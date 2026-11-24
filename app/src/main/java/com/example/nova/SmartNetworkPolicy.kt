package com.example.nova

internal object SmartNetworkPolicy {
    enum class Mode {
        AUTO, RESTRICTED, HIGH_SPEED, CUSTOM;

        companion object {
            fun parse(value: String?): Mode = entries.firstOrNull { it.name == value } ?: AUTO
        }
    }

    enum class Network { STANDARD, METERED, UNVALIDATED, UNKNOWN }

    data class Success(val transport: String, val atMs: Long)
    data class Attempt(val transport: String, val budgetMs: Long)

    private const val HISTORY_TTL_MS = 24L * 60L * 60L * 1_000L
    private val fast = listOf("ru", "masque", "opera", "nova", "proton", "tor", "dns")
    private val restricted = listOf("nova", "opera", "masque", "ru", "proton", "tor", "dns")

    fun describe(hasNetwork: Boolean, validated: Boolean, metered: Boolean): Network = when {
        !hasNetwork -> Network.UNKNOWN
        !validated -> Network.UNVALIDATED
        metered -> Network.METERED
        else -> Network.STANDARD
    }

    fun plan(
        mode: Mode,
        network: Network,
        supported: List<String>,
        lastSuccess: Success?,
        nowMs: Long,
    ): List<Attempt> {
        val preference = when (mode) {
            Mode.RESTRICTED -> restricted
            Mode.HIGH_SPEED -> fast
            Mode.CUSTOM -> supported
            Mode.AUTO -> when (network) {
                Network.STANDARD -> fast
                Network.UNVALIDATED -> restricted
                Network.METERED, Network.UNKNOWN -> supported
            }
        }
        val ordered = (preference + supported).filter { it in supported }.distinct()
        val remembered = lastSuccess?.takeIf {
            mode in setOf(Mode.AUTO, Mode.RESTRICTED) && network != Network.UNKNOWN &&
                it.atMs in (nowMs - HISTORY_TTL_MS)..nowMs && it.transport in ordered &&
                (network != Network.STANDARD || it.transport !in setOf("tor", "dns"))
        }
        val candidates = if (remembered == null) ordered else {
            listOf(remembered.transport) + ordered.filter { it != remembered.transport }
        }
        return candidates.map { transport ->
            val budget = when {
                mode == Mode.CUSTOM -> 120_000L
                transport in setOf("tor", "dns") -> 120_000L
                mode == Mode.RESTRICTED -> 85_000L
                mode == Mode.HIGH_SPEED || network == Network.STANDARD -> 45_000L
                else -> 85_000L
            }
            Attempt(transport, budget)
        }
    }
}
