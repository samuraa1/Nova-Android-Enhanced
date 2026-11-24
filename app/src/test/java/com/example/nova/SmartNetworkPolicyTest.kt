package com.example.nova

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartNetworkPolicyTest {
    private val transports = listOf("nova", "ru", "masque", "opera", "proton", "tor", "dns")
    private val now = 100_000_000L

    @Test
    fun validatedUnmeteredNetworkPrefersFastTransportAndKeepsFallbacks() {
        val plan = SmartNetworkPolicy.plan(
            SmartNetworkPolicy.Mode.AUTO, SmartNetworkPolicy.Network.STANDARD, transports, null, now,
        )
        assertEquals("ru", plan.first().transport)
        assertEquals(45_000L, plan.first().budgetMs)
        assertEquals(transports.toSet(), plan.map { it.transport }.toSet())
        assertEquals(120_000L, plan.last().budgetMs)
    }

    @Test
    fun restrictedPrefersNovaAndOperaAheadOfWarp() {
        val plan = SmartNetworkPolicy.plan(
            SmartNetworkPolicy.Mode.RESTRICTED, SmartNetworkPolicy.Network.STANDARD, transports, null, now,
        )
        assertEquals(listOf("nova", "opera"), plan.take(2).map { it.transport })
        assertEquals(85_000L, plan.first().budgetMs)
    }

    @Test
    fun historyOnlyPromotesRecentUsableCandidates() {
        fun first(success: SmartNetworkPolicy.Success) = SmartNetworkPolicy.plan(
            SmartNetworkPolicy.Mode.AUTO, SmartNetworkPolicy.Network.STANDARD, transports, success, now,
        ).first().transport
        assertEquals("masque", first(SmartNetworkPolicy.Success("masque", now - 1)))
        assertEquals("ru", first(SmartNetworkPolicy.Success("masque", 1)))
        assertEquals("ru", first(SmartNetworkPolicy.Success("masque", now + 1)))
        assertEquals("ru", first(SmartNetworkPolicy.Success("tor", now)))
        assertEquals("ru", first(SmartNetworkPolicy.Success("missing", now)))
    }

    @Test
    fun customPreservesOriginalOrderAndDoesNotPromoteHistory() {
        val plan = SmartNetworkPolicy.plan(
            SmartNetworkPolicy.Mode.CUSTOM, SmartNetworkPolicy.Network.UNVALIDATED, transports,
            SmartNetworkPolicy.Success("opera", now), now,
        )
        assertEquals(transports, plan.map { it.transport })
        assertTrue(plan.all { it.budgetMs == 120_000L })
    }

    @Test
    fun androidValidationAndMeteredSignalsStayDistinct() {
        assertEquals(SmartNetworkPolicy.Network.UNKNOWN, SmartNetworkPolicy.describe(false, false, false))
        assertEquals(SmartNetworkPolicy.Network.UNVALIDATED, SmartNetworkPolicy.describe(true, false, false))
        assertEquals(SmartNetworkPolicy.Network.METERED, SmartNetworkPolicy.describe(true, true, true))
        assertEquals(SmartNetworkPolicy.Network.STANDARD, SmartNetworkPolicy.describe(true, true, false))
    }
}
