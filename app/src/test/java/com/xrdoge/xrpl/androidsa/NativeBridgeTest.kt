package com.xrdoge.xrpl.androidsa

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeBridgeTest {
    @Test
    fun parseNativeOverviewUsesFallbacksForMissingSegments() {
        val overview = parseNativeOverview("AndroidSA|udp")

        assertEquals("AndroidSA", overview.clientName)
        assertEquals("udp", overview.transport)
        assertEquals("offline", overview.connectionState)
        assertEquals("No diagnostics available", overview.diagnostics)
    }
}
