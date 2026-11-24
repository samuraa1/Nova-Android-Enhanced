package com.example.nova

import android.content.Context
import android.net.NetworkCapabilities
import android.util.AtomicFile
import java.io.File
import java.io.IOException

internal class SmartNetworkStore(context: Context) {
    private val directory = context.applicationContext.filesDir

    fun mode(): SmartNetworkPolicy.Mode = SmartNetworkPolicy.Mode.parse(read("network_policy"))

    fun setMode(mode: SmartNetworkPolicy.Mode): Boolean = write("network_policy", mode.name)

    fun lastSuccess(scope: String?): SmartNetworkPolicy.Success? {
        if (scope == null) return null
        val fields = read("network_success_$scope")?.split('|', limit = 2) ?: return null
        val time = fields.getOrNull(1)?.toLongOrNull() ?: return null
        return SmartNetworkPolicy.Success(fields[0], time)
    }

    fun recordSuccess(scope: String?, transport: String, nowMs: Long) {
        if (scope != null) write("network_success_$scope", "$transport|$nowMs")
    }

    private fun read(name: String): String? = try {
        AtomicFile(File(directory, name)).readFully().decodeToString()
    } catch (_: IOException) {
        null
    }

    private fun write(name: String, value: String): Boolean {
        val file = AtomicFile(File(directory, name))
        val stream = try {
            file.startWrite()
        } catch (_: IOException) {
            return false
        }
        return try {
            stream.write(value.encodeToByteArray())
            file.finishWrite(stream)
            true
        } catch (_: IOException) {
            file.failWrite(stream)
            false
        }
    }

    companion object {
        fun classify(caps: NetworkCapabilities?): SmartNetworkPolicy.Network = SmartNetworkPolicy.describe(
            hasNetwork = caps != null,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true,
        )

        fun scope(networkClass: String?, condition: SmartNetworkPolicy.Network): String? =
            networkClass?.takeIf { condition != SmartNetworkPolicy.Network.UNKNOWN }?.let { "$it-${condition.name}" }
    }
}
