import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

/**
 * Диагностическая сборка: `./gradlew :app:assembleGithubRelease -PnovaDiagnostics`.
 *
 * Включает [com.example.nova.DiagnosticsActivity] (отдельный вход в лаунчере и
 * перенаправление с главного экрана) и ранний перехват падений. Нужна там, где
 * приложение показывает белый экран, а подключить ADB нельзя: отчёт выводится на
 * экран крупным текстом и QR-кодом, человек фотографирует.
 *
 * В обычных сборках и в сборке F-Droid флага нет: компоненты выключены в
 * манифесте, `BuildConfig.DIAGNOSTICS` равен `false`, zxing не подключается.
 *
 * Версию диагностической сборки задают правкой литералов ниже на время сборки
 * (так уехала отладочная `1.29.debug-2`): менять их выражением нельзя —
 * `fdroid checkupdates` читает версию регулярным выражением. По той же
 * причине в комментариях этого файла не должно быть слова versionCode
 * рядом с числом: их checkupdates нашёл такую пару в тексте выше и решил,
 * что вышла версия из комментария, а не настоящая.
 */
val novaDiagnostics = providers.gradleProperty("novaDiagnostics").isPresent

/**
 * Быстрая сборка для проверок: `./gradlew :app:assembleGithubRelease -PnovaFastBuild`.
 *
 * Замер профиля на правке одного файла: вся сборка 31 с, из них
 * `lintVitalAnalyzeGithubRelease` 26,3 с и `minifyGithubReleaseWithR8` 23,0 с
 * (идут параллельно), а компиляция Kotlin — 0,7 с. То есть время съедают ровно
 * два релизных шага, и в быстром режиме они выключаются.
 *
 * Флаг ничего не меняет без явной передачи: релизы и сборки F-Droid идут с R8 и
 * lint как раньше. APK быстрого режима **нельзя публиковать** — он не обфусцирован
 * и не прошёл проверку lint.
 */
val novaFastBuild = providers.gradleProperty("novaFastBuild").isPresent
if (novaFastBuild) {
    logger.lifecycle("novaFastBuild: R8 и lint выключены — сборка только для проверок")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists().also { exists ->
    if (exists) {
        keystorePropertiesFile.inputStream().use(keystoreProperties::load)
    }
}

// Public Enhanced builds use provider APIs without the upstream author's private relay.
val tgCfWsSecret = ""
val relayKeyId = ""
val operaRelayPassword = ""

android {
    namespace = "com.example.nova"
    compileSdk = 35

    // Версия закреплена намеренно: этой же сборкой собирается Go-ядро
    // (tools/build_nova_core_aar.sh), и расхождение NDK между JNI-слоем и ядром
    // — источник несовместимостей, которые проявляются только на устройстве.
    ndkVersion = "27.2.12479018"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.brent.nova.enhanced"
        minSdk = 24 // Android 7.0; более новые API вызываются под проверкой SDK_INT
        targetSdk = 34
        buildConfigField("boolean", "DIAGNOSTICS", novaDiagnostics.toString())
        manifestPlaceholders["novaDiagnosticsEnabled"] = novaDiagnostics.toString()
        // Главный экран выключается только в диагностической сборке — чтобы в
        // лаунчере остался один значок и человек не открыл падающий экран.
        manifestPlaceholders["novaMainEnabled"] = (!novaDiagnostics).toString()
        versionCode = 170
        versionName = "1.36.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Собираем ровно те архитектуры, для которых в репозитории есть
        // нативные зависимости. Без этого CMake пошёл бы собирать x86/x86_64 и
        // упал бы на импортируемом libtun2proxy.so, которого для них нет.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    /**
     * JNI-слой к tun2proxy собирается из исходников (`src/main/cpp`), а не лежит
     * готовым файлом.
     *
     * Раньше в репозитории хранились два бинарника одного и того же слоя под
     * разными именами — `libnative-lib.so` для arm64 и `libtun2proxy_jni.so`
     * для armeabi-v7a, — хотя `native-lib.cpp` лежал рядом и просто не был
     * подключён к сборке. Правила F-Droid запрещают готовые бинарники, а здесь
     * они к тому же были лишними.
     */
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    /**
     * Откуда приложение получает обновления.
     *
     * `github` — обычная сборка, которую владелец выкладывает в релизы: встроенный
     * механизм обновления работает как работал. `fdroid` — сборка для каталога
     * F-Droid, где обновления выдаёт сам каталог, а собственный загрузчик обязан
     * молчать: их правило запрещает приложению самому тянуть исполняемые файлы.
     *
     * Различие ровно одно — флаг [BuildConfig.UPDATER_ENABLED]. Кода апдейтера
     * это не удаляет и на сборку для GitHub не влияет: варианты собираются из
     * одних и тех же исходников.
     */
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "UPDATER_ENABLED", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATER_ENABLED", "false")
        }
    }

    applicationVariants.all {
        // Имя файла не зависит от варианта: у владельца в релизах лежит
        // `Nova_<версия>.apk`, и менять это из-за появления flavor нельзя.
        outputs.map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output -> output.outputFileName = "Nova_$versionName.apk" }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    /**
     * Проверка релизной сборки после lint не запускается в быстром режиме: по
     * замерам профиля `lintVitalAnalyze` — самая долгая задача сборки (26 с из 31).
     */
    lint {
        checkReleaseBuilds = !novaFastBuild
    }

    buildTypes {
        release {
            // AGP кладёт в APK `META-INF/version-control-info.textproto` — снимок
            // состояния git той машины, где шла сборка. Для воспроизводимой
            // сборки это тупик: у F-Droid свой клон, и файл заведомо другой
            // (поймано сравнением с их сборкой). Приложению он не нужен.
            vcsInfo {
                include = false
            }
            // В быстром режиме R8 и сжатие ресурсов выключены: вторая по времени
            // задача (23 с). Итог — APK крупнее и без обфускации, годится только
            // для проверок, не для публикации.
            isMinifyEnabled = !novaFastBuild
            isShrinkResources = !novaFastBuild
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "TG_CF_WS_SECRET", "\"$tgCfWsSecret\"")
            buildConfigField("String", "OPERA_RELAY_PASSWORD", "\"" + operaRelayPassword + "\"")
            buildConfigField("String", "RELAY_KEY_ID", "\"" + relayKeyId + "\"")
        }
        debug {
            // Для отладочных сборок пустой секрет допустим: подпись просто не
            // добавляется, и клиент работает по публичным доменам Cloudflare.
            buildConfigField("String", "TG_CF_WS_SECRET", "\"$tgCfWsSecret\"")
            buildConfigField("String", "OPERA_RELAY_PASSWORD", "\"" + operaRelayPassword + "\"")
            buildConfigField("String", "RELAY_KEY_ID", "\"" + relayKeyId + "\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            // Мёртвый вес из BouncyCastle, который R8 не трогает: ресурсы — не код.
            //
            // `picnic/lowmc*.bin.properties` — таблицы постквантовой подписи Picnic:
            // 1 221 059 байт, и они почти несжимаемы (высокая энтропия), то есть
            // занимают в APK ровно столько же. Picnic в проекте не упоминается
            // нигде: `bcprov` здесь ради Proton и WARP (`ProtonCrypto`,
            // `WarpClient`), а PQC-провайдер не инстанцируется ни разу.
            //
            // `CertPathReviewerMessages_de` — немецкие тексты ошибок проверки
            // цепочки сертификатов. Английский остаётся.
            //
            // Если однажды что-то позовёт Picnic, оно упадёт в рантайме, а не на
            // сборке, — поэтому исключение узкое и названо поимённо, а не
            // `org/bouncycastle/pqc/**`.
            excludes += setOf(
                "org/bouncycastle/pqc/crypto/picnic/lowmcL1.bin.properties",
                "org/bouncycastle/pqc/crypto/picnic/lowmcL3.bin.properties",
                "org/bouncycastle/pqc/crypto/picnic/lowmcL5.bin.properties",
                "org/bouncycastle/x509/CertPathReviewerMessages_de.properties",
            )
        }
    }
}

dependencies {
    // QR нужен только экрану самодиагностики. В обычной сборке библиотека не
    // подключается вовсе, а код зовёт её через рефлексию и молча обходится без
    // неё — размер APK и состав зависимостей F-Droid при этом не меняются.
    if (novaDiagnostics) {
        implementation("com.google.zxing:core:3.5.3")
    }
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    // Local .aar library from Go will be added here later
    implementation(files("libs/nova-core-api24-stripped.aar"))
    // Настоящий tor (BSD-3), запускаемый через JNI в нашем же процессе, а не
    // отдельным исполняемым файлом: сокеты чужого процесса нечем пометить
    // `VpnService.protect()`, и мост завернулся бы в собственный туннель (G154).
    // Версия 0.4.8.x, а не 0.4.9.x: та требует compileSdk 37, у нас 35.
    implementation("info.guardianproject:tor-android:0.4.8.22")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
