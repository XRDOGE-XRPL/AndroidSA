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

    @Test
    fun parseNativeOverviewPreservesPipesInsideDiagnostics() {
        val overview = parseNativeOverview("AndroidSA|udp|ready|pipe|inside|diagnostics")

        assertEquals("AndroidSA", overview.clientName)
        assertEquals("udp", overview.transport)
        assertEquals("ready", overview.connectionState)
        assertEquals("pipe|inside|diagnostics", overview.diagnostics)
    }

    @Test
    fun parseNativeOverviewUsesFallbacksForBlankSegments() {
        val overview = parseNativeOverview("| | | ")

        assertEquals("AndroidSA", overview.clientName)
        assertEquals("unavailable", overview.transport)
        assertEquals("offline", overview.connectionState)
        assertEquals("No diagnostics available", overview.diagnostics)
    }
}
