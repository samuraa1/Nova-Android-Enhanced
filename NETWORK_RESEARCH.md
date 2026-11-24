# Сетевые ограничения и доступность соединения

Актуализация 24 ноября 2026 года. Результаты публичных измерений помогают сформировать гипотезы. Работоспособность Enhanced в конкретной сети подтверждается замером из этой сети.

## Сигналы и проверки

- **DNS.** Сравните ответы A и AAAA, код ответа и задержку системного резолвера и доверенного DoH с использованием известных контрольных имён. Геобалансировку CDN учитывайте отдельно. Сохранение TLS-проверки при тестах подключения к известному IP позволяет исследовать DNS без подмены доверия.
- **IP, TCP и SNI.** Проверяйте маршрут до разрешённого вам адреса, TCP connect и отдельно TLS с корректным SNI. Расхождение между TCP и TLS с одинаковым адресом указывает на проблему выше транспортного уровня. Таймаут TLS также возможен из-за потерь, MTU или сервера. Публичные измерения фиксировали неоднородность между сетями и вмешательство после ClientHello. [Censored Planet, IMC 2022](https://censoredplanet.org/assets/tspu-imc22.pdf) · [OONI, исследование России](https://ooni.org/post/2024-russia-report/)
- **UDP и QUIC.** UDP connect без ответа ничего не подтверждает. Используйте ответ на протокольное рукопожатие и отдельно полезный трафик по туннелю. Тест TCP- и UDP-транспортов на одном контролируемом сервере помогает разделить протокольные и адресные ограничения. [RFC 9000](https://www.rfc-editor.org/rfc/rfc9000.html)
- **Белые списки.** Доступность меняется по оператору, региону и времени. Публичная карта фиксирует наблюдения в нескольких сетях, а между точками замера остаётся неопределённость. [Amnezia Pulse](https://amnezia.org/blog/amnezia-pulse-release)

## Почему upstream мог открывать YouTube

Отчёт о просмотре видео подтверждает доступность в конкретной сессии. Правдоподобная гипотеза — один из разрешённых маршрутов доставил трафик туннеля на внешнюю сторону фильтрации, а выходная сеть открыла цепочку YouTube и CDN. Для проверки нужны активный транспорт, тип сети, успешная проба через VPN и сравнение без VPN в то же время. В upstream есть измерение рабочего MASQUE data-plane после обновления ключа и пример отдельного отказа регистрации на стадии TLS. Эти наблюдения не привязаны к сеансам YouTube. [MASQUE upstream](https://github.com/confeden/Nova-Android/blob/main/docs/masque-connect-ip.md) · [Регистрация WARP](https://github.com/confeden/Nova-Android/blob/main/docs/warp-api-sni-block.md)

## Сравнение вариантов

- **WireGuard и AmneziaWG.** Семейство на UDP, эффективно по числу обменов. Обфускация AWG меняет вид пакетов и требует согласованных параметров. При полной недоступности UDP нужен иной транспорт. [WireGuard](https://www.wireguard.com/protocol/) · [AmneziaWG](https://docs.amnezia.org/documentation/amnezia-wg/)
- **WARP и MASQUE.** WARP — сервис, WireGuard и MASQUE — протоколы. CONNECT-IP и CONNECT-UDP требуют сервера с соответствующей поддержкой. В upstream CONNECT-IP использует QUIC и включает дополнительный HTTP/2 путь в Go-ядре. Доступность коммерческого ingress и регистрационного API проверяются отдельно. [WARP](https://developers.cloudflare.com/warp-client/warp-modes/) · [RFC 9484](https://www.rfc-editor.org/rfc/rfc9484.html) · [RFC 9298](https://www.rfc-editor.org/rfc/rfc9298.html)
- **Xray / REALITY.** Пользовательские профили для TCP-путей могут пережить выключение UDP при доступном узле, совместимом транспорте и корректной аутентификации. [Project X](https://xtls.github.io/en/)
- **Hysteria2 и TUIC.** Оба используют QUIC. Это кандидаты для сценариев с потерями и доступным UDP. Для сценария UDP deny они добавляют ещё один зависимый от UDP вариант. sing-box реализует ряд таких протоколов и управляет DNS и маршрутизацией. Добавление нового ядра требует совместимости лицензий, конфигурации и анализа расхода ресурсов. [Hysteria2](https://v2.hysteria.network/docs/developers/Protocol/) · [TUIC](https://github.com/tuic-protocol/tuic) · [sing-box](https://sing-box.sagernet.org/configuration/)
- **DoH, DoT, DoQ и ECH.** DoH использует HTTPS, DoT — TLS на выделенном порту, DoQ — QUIC. ECH шифрует внутренний ClientHello у совместимых сторон. Эти технологии не меняют видимость IP назначения и сами по себе не восстанавливают внешний маршрут. [DoH](https://www.rfc-editor.org/rfc/rfc8484.html) · [DoT](https://www.rfc-editor.org/rfc/rfc7858.html) · [DoQ](https://www.rfc-editor.org/info/rfc9250/) · [ECH](https://www.rfc-editor.org/rfc/rfc9849.html)

## Инфраструктура

Бесплатная поставка не требует VPS для самого приложения. Доступность готовых вариантов зависит от провайдера и публичных источников конфигураций upstream. Proton документирует импорт официально выпущенных конфигураций WireGuard в сторонние клиенты. Документация Opera описывает бесплатный VPN в составе браузера, а руководство WARP — официальный клиент. Наличие исходного кода адаптеров не подтверждает разрешение на стороннюю автоматическую регистрацию. Новые провайдеры, учётные данные или серверы в Enhanced не добавлены. [Proton WireGuard](https://protonvpn.com/support/wireguard-configurations) · [Opera VPN](https://www.opera.com/features/free-vpn) · [WARP для потребителей](https://developers.cloudflare.com/warp-client/get-started/)
