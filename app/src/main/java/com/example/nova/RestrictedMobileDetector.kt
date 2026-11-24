package com.example.nova

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress

object RestrictedMobileDetector {
    private val ipv4Targets = listOf("1.1.1.1", "8.8.8.8")
    private val ipv6Targets = listOf("2606:4700:4700::1111", "2001:4860:4860::8888")

    fun detect(connectivityManager: ConnectivityManager, network: Network?): Boolean? =
        detect(connectivityManager, network, requireCellular = true)

    /**
     * @param requireCellular исходное поведение: отвечать только про сотовую сеть.
     *        Режим «белого списка» бывает и на Wi-Fi — в гостевых, корпоративных и
     *        гостиничных сетях, — и для выбора SNI это ровно тот же случай, поэтому
     *        оттуда вызывается с `false`. Прочие потребители ответа (маскировка
     *        WARP, политика MASQUE) настроены на сотовую сеть, и расширять их
     *        поведение заодно нельзя.
     */
    fun detect(
        connectivityManager: ConnectivityManager,
        network: Network?,
        requireCellular: Boolean,
    ): Boolean? {
        if (network == null) return null
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (requireCellular && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return null
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) return null
        val link = connectivityManager.getLinkProperties(network) ?: return null
        val hasIpv4 = link.routes.any { it.isDefaultRoute && it.destination.address is Inet4Address }
        val hasIpv6 = link.routes.any { it.isDefaultRoute && it.destination.address is Inet6Address }
        val targets = when {
            hasIpv4 && hasIpv6 -> listOf(ipv4Targets[0], ipv6Targets[1], ipv4Targets[1])
            hasIpv4 -> ipv4Targets
            hasIpv6 -> ipv6Targets
            else -> return null
        }
        // 700 мс, а не 350: сотовое радио после простоя просыпается 100–500 мс (RRC),
        // и на этом времени первая проба не успевала соединиться на совершенно
        // обычной сети. Ответ «ограничена» из-за такого промаха дороже лишней
        // секунды ожидания: он переводит маскировку в режим белого списка и убирает
        // зарубежные имена из ротации.
        val reachable = targets.any { tcpProbe(network, it, 443, 700) }
        return !reachable
    }

    fun buildNetworkId(network: Network?): String? {
        return network?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun tcpProbe(
        network: Network,
        host: String,
        port: Int,
        timeoutMs: Int,
    ): Boolean {
        return try {
            network.socketFactory.createSocket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.isConnected
            }
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
