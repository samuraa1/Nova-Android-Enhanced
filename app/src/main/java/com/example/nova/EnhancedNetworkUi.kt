package com.example.nova

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.net.Inet4Address
import java.net.Inet6Address

internal object EnhancedNetworkUi {
    fun bind(activity: SettingsActivity) {
        val store = SmartNetworkStore(activity)
        val policy = activity.findViewById<Button>(R.id.btn_network_policy)
        val report = activity.findViewById<Button>(R.id.btn_network_report)
        val ru = NovaLanguage.current(activity) == NovaLanguage.Language.RU
        val modes = SmartNetworkPolicy.Mode.entries
        val labels = if (ru) arrayOf("Авто", "Ограниченная сеть", "Высокая скорость", "Пользовательский")
            else arrayOf("Auto", "Restricted network", "High speed", "Custom")
        val policyTitle = if (ru) "Стратегия подключения" else "Connection strategy"
        val reportTitle = if (ru) "Диагностика сети" else "Network diagnostics"
        fun refresh() {
            policy.text = "$policyTitle · ${labels[store.mode().ordinal]}"
        }
        refresh()
        report.text = reportTitle
        NovaDialogs.styleButton(policy, primary = false)
        NovaDialogs.styleButton(report, primary = false)
        policy.setOnClickListener {
            var selected = store.mode().ordinal
            val information = if (ru) "Порядок и время ожидания для встроенного AUTO. В пользовательском режиме используется исходный порядок Nova. Явный выбор транспорта и импорт действуют отдельно."
                else "Order and time limits for built-in AUTO. Custom uses Nova’s original order. Explicit transports and imported profiles are configured separately."
            AlertDialog.Builder(activity)
                .setTitle(policyTitle)
                .setSingleChoiceItems(labels, selected) { _, index -> selected = index }
                .setNeutralButton(if (ru) "Подробнее" else "Details") { _, _ ->
                    AlertDialog.Builder(activity).setTitle(policyTitle).setMessage(information)
                        .setPositiveButton(android.R.string.ok, null).show().also(NovaDialogs::style)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (store.setMode(modes[selected])) refresh()
                    else Toast.makeText(activity, if (ru) "Не удалось сохранить стратегию" else "Could not save strategy", Toast.LENGTH_SHORT).show()
                }
                .show().also(NovaDialogs::style)
        }
        report.setOnClickListener {
            val message = snapshot(activity, labels[store.mode().ordinal], ru)
            AlertDialog.Builder(activity).setTitle(reportTitle).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(if (ru) "Поделиться" else "Share") { _, _ ->
                    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                    activity.startActivity(Intent.createChooser(share, reportTitle))
                }.show().also(NovaDialogs::style)
        }
    }

    private fun snapshot(activity: SettingsActivity, mode: String, ru: Boolean): String {
        val data = ClientData(activity)
        val manager = activity.getSystemService(ConnectivityManager::class.java)
        val network = UnderlyingNetwork.select(activity)
        val caps = network?.let { manager?.getNetworkCapabilities(it) }
        val link = network?.let { manager?.getLinkProperties(it) }
        val vpn = manager?.activeNetwork?.let { manager?.getNetworkCapabilities(it) }
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> if (ru) "сотовая" else "cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "—"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val v4 = link?.linkAddresses?.any { it.address is Inet4Address } == true
        val v6 = link?.linkAddresses?.any { it.address is Inet6Address && !it.address.isLinkLocalAddress } == true
        val privateDns = AndroidCompat.isPrivateDnsActive(link)
        val state = data.getServiceState()
        val tunnel = data.getServiceTransport().takeIf { vpn && state == NovaVpnService.STATE_CONNECTED }.orEmpty().ifBlank { "—" }
        val yes = if (ru) "да" else "yes"
        val no = if (ru) "нет" else "no"
        fun flag(value: Boolean) = if (link == null) "—" else if (value) yes else no
        return buildList {
            add("Nova Android Enhanced ${BuildConfig.VERSION_NAME}")
            add("${if (ru) "Стратегия" else "Strategy"} · $mode")
            add("${if (ru) "Исходная сеть" else "Underlying network"} · $transport")
            add("${if (ru) "Интернет подтверждён Android" else "Android internet validation"} · ${if (caps == null) "—" else if (validated) yes else no}")
            add("IPv4 · ${flag(v4)}    IPv6 · ${flag(v6)}")
            add("${if (ru) "Системный Private DNS" else "Android Private DNS"} · ${flag(privateDns)}")
            add("${if (ru) "Активный транспорт" else "Active transport"} · $tunnel")
            add(if (ru) "IPv4/IPv6 показывают назначенные адреса. Активные пробы TCP и UDP здесь не выполняются." else "IPv4/IPv6 reflect assigned addresses. This report does not send active TCP or UDP probes.")
        }.joinToString("\n")
    }
}
