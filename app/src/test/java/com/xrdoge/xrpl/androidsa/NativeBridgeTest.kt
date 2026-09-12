package com.xrdoge.xrpl.androidsa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun parseNativeOverviewTrimsWhitespace() {
        val overview = parseNativeOverview(" AndroidSA | udp | ready | diagnostics ")

        assertEquals("AndroidSA", overview.clientName)
        assertEquals("udp", overview.transport)
        assertEquals("ready", overview.connectionState)
        assertEquals("diagnostics", overview.diagnostics)
    }

    @Test
    fun requireValidNativeCommandTrimsInput() {
        assertEquals("ping", requireValidNativeCommand("  ping  "))
    }

    @Test
    fun requireValidNativeCommandRejectsBlankInput() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("   ")
        }
    }

    @Test
    fun requireValidNativeCommandRejectsTooLongInput() {
        val tooLong = "x".repeat(MaxNativeCommandLength + 1)

        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand(tooLong)
        }
    }

    @Test
    fun requireValidNativeCommandAcceptsTransportCommand() {
        assertEquals("transport:udp", requireValidNativeCommand("transport:udp"))
    }

    @Test
    fun requireValidNativeCommandAcceptsMixedCaseTransportCommand() {
        assertEquals("Transport:udp", requireValidNativeCommand("Transport:udp"))
    }

    @Test
    fun requireValidNativeCommandRejectsMissingTransportSeparator() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("transportudp")
        }
    }

    @Test
    fun requireValidNativeCommandRejectsTransportKeywordTail() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("transport foo:bar")
        }
    }

    @Test
    fun requireValidNativeCommandRejectsWhitespaceBeforeTransportSeparator() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("transport :udp")
        }
    }
}
