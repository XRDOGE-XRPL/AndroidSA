package com.xrdoge.xrpl.androidsa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals("demo.sa-mp.local:7777", overview.serverAddress)
        assertEquals("Guest", overview.playerName)
        assertEquals(0, overview.latencyMs)
        assertEquals(0, overview.packetsSent)
        assertEquals(0, overview.packetsReceived)
        assertEquals(0, overview.connectionAttempts)
        assertEquals("startup", overview.lastCommand)
    }

    @Test
    fun parseNativeOverviewUsesFallbacksForBlankSegments() {
        val overview = parseNativeOverview("| | | | | | | | | | ")

        assertEquals("AndroidSA", overview.clientName)
        assertEquals("unavailable", overview.transport)
        assertEquals("offline", overview.connectionState)
        assertEquals("No diagnostics available", overview.diagnostics)
        assertEquals("demo.sa-mp.local:7777", overview.serverAddress)
        assertEquals("Guest", overview.playerName)
        assertEquals(0, overview.latencyMs)
        assertEquals(0, overview.packetsSent)
        assertEquals(0, overview.packetsReceived)
        assertEquals(0, overview.connectionAttempts)
        assertEquals("startup", overview.lastCommand)
    }

    @Test
    fun parseNativeOverviewPreservesLegacyPipesInsideDiagnostics() {
        val overview = parseNativeOverview("AndroidSA|udp|ready|pipe|inside|diagnostics")

        assertEquals("AndroidSA", overview.clientName)
        assertEquals("udp", overview.transport)
        assertEquals("ready", overview.connectionState)
        assertEquals("pipe|inside|diagnostics", overview.diagnostics)
        assertEquals("demo.sa-mp.local:7777", overview.serverAddress)
    }

    @Test
    fun parseNativeOverviewTrimsWhitespaceAndReadsExtendedFields() {
        val overview = parseNativeOverview(
            " AndroidSA | udp | ready | diagnostics | example.org:7777 | Ryder | 42 | 7 | 9 | 3 | connect:example.org:7777 "
        )

        assertEquals("AndroidSA", overview.clientName)
        assertEquals("udp", overview.transport)
        assertEquals("ready", overview.connectionState)
        assertEquals("diagnostics", overview.diagnostics)
        assertEquals("example.org:7777", overview.serverAddress)
        assertEquals("Ryder", overview.playerName)
        assertEquals(42, overview.latencyMs)
        assertEquals(7, overview.packetsSent)
        assertEquals(9, overview.packetsReceived)
        assertEquals(3, overview.connectionAttempts)
        assertEquals("connect:example.org:7777", overview.lastCommand)
    }

    @Test
    fun parseNativeOverviewPreservesDiagnosticsWithEmbeddedPipesInFullSummary() {
        val overview = parseNativeOverview("AndroidSA|udp|ready|diag|with|pipe|server.org:7777|Ryder|42|7|9|3|status")

        assertEquals("AndroidSA", overview.clientName)
        assertEquals("udp", overview.transport)
        assertEquals("ready", overview.connectionState)
        assertEquals("diag|with|pipe", overview.diagnostics)
        assertEquals("server.org:7777", overview.serverAddress)
        assertEquals("Ryder", overview.playerName)
        assertEquals(42, overview.latencyMs)
        assertEquals(7, overview.packetsSent)
        assertEquals(9, overview.packetsReceived)
        assertEquals(3, overview.connectionAttempts)
        assertEquals("status", overview.lastCommand)
    }

    @Test
    fun parseNativeOverviewFallsBackForInvalidNumericFields() {
        val overview = parseNativeOverview("AndroidSA|udp|ready|diag|server|Guest|oops|nope|nah|bad|status")

        assertEquals(0, overview.latencyMs)
        assertEquals(0, overview.packetsSent)
        assertEquals(0, overview.packetsReceived)
        assertEquals(0, overview.connectionAttempts)
    }

    @Test
    fun parseNativeEventLogSplitsNonBlankLines() {
        val events = parseNativeEventLog("one\n\n two \nthree")

        assertEquals(listOf("one", "two", "three"), events)
    }

    @Test
    fun parseNativeEventLogFallsBackWhenEmpty() {
        val events = parseNativeEventLog("   \n  ")

        assertEquals(listOf("No recent events"), events)
    }

    @Test
    fun parseNativeEventLogCapsToMostRecentEntries() {
        val raw = (1..60).joinToString(separator = "\n") { "event-$it" }

        val events = parseNativeEventLog(raw)

        assertEquals(48, events.size)
        assertEquals("event-13", events.first())
        assertEquals("event-60", events.last())
    }

    @Test
    fun normalizeNativeSnapshotPromotesFailureDiagnosticsToErrorState() {
        val snapshot = NativeClientSnapshot(
            overview = NativeOverview(
                clientName = "AndroidSA",
                transport = "udp",
                connectionState = "connected",
                diagnostics = "UDP bind failed: address in use",
                serverAddress = "demo.sa-mp.local:7777",
                playerName = "Guest",
                latencyMs = 0,
                packetsSent = 0,
                packetsReceived = 0,
                connectionAttempts = 1,
                lastCommand = "connect",
            ),
            recentEvents = listOf("UDP bind failed: address in use"),
        )

        val normalized = normalizeNativeSnapshot(snapshot)

        assertEquals("error", normalized.overview.connectionState)
        assertTrue(normalized.overview.diagnostics.contains("failed"))
    }

    @Test
    fun requireValidNativeCommandTrimsInput() {
        assertEquals("ping", requireValidNativeCommand("  ping  "))
    }

    @Test
    fun requireValidNativeCommandAcceptsStatusAndResetStyleCommands() {
        assertEquals("status", requireValidNativeCommand("status"))
        assertEquals("reset", requireValidNativeCommand("reset"))
        assertEquals("disconnect", requireValidNativeCommand("disconnect"))
    }

    @Test
    fun requireValidNativeCommandRejectsWhitespaceBeforeSeparator() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("transport :udp")
        }
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
    fun requireValidNativeCommandRejectsControlCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("ping\n")
        }
    }

    @Test
    fun requireValidNativeCommandRejectsSummaryDelimiterCharacter() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("transport:udp|tcp")
        }
    }

    @Test
    fun requireValidNativeCommandAcceptsTransportCommand() {
        assertEquals("transport:udp", requireValidNativeCommand("transport:udp"))
        assertEquals("transport:udp", requireValidNativeCommand("Transport:udp"))
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
    fun requireValidNativeCommandAcceptsDiagnosticsCommand() {
        assertEquals("diagnostics:ok", requireValidNativeCommand("diagnostics:ok"))
    }

    @Test
    fun requireValidNativeCommandAcceptsConnectWithServer() {
        assertEquals("connect:demo.sa-mp.local:7777", requireValidNativeCommand("connect:demo.sa-mp.local:7777"))
    }

    @Test
    fun requireValidNativeCommandRejectsBlankConnectValue() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("connect:   ")
        }
    }

    @Test
    fun requireValidNativeCommandAcceptsPlayerCommand() {
        assertEquals("player:CJ", requireValidNativeCommand("player:CJ"))
    }

    @Test
    fun requireValidNativeCommandRejectsBlankPlayerValue() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("player:   ")
        }
    }

    @Test
    fun requireValidNativeCommandRejectsMissingPlayerSeparator() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("player")
        }
    }

    @Test
    fun requireValidNativeCommandAcceptsLatencyCommand() {
        assertEquals("latency:42", requireValidNativeCommand("latency:42"))
    }

    @Test
    fun requireValidNativeCommandRejectsInvalidLatencyValue() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("latency:fast")
        }
    }

    @Test
    fun requireValidNativeCommandRejectsMissingLatencySeparator() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("latency")
        }
    }

    @Test
    fun requireValidNativeCommandAcceptsSimulateCommand() {
        assertEquals("simulate:rx", requireValidNativeCommand("simulate:rx"))
    }

    @Test
    fun requireValidNativeCommandRejectsUnsupportedSimulateDirection() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("simulate:loopback")
        }
    }

    @Test
    fun requireValidNativeCommandAcceptsFailCommand() {
        assertEquals("fail:timeout", requireValidNativeCommand("fail:timeout"))
    }

    @Test
    fun classifyEventCategoryRecognizesKeySignals() {
        assertEquals(EventCategory.HANDSHAKE, classifyEventCategory("RX RakNet connected ping"))
        assertEquals(EventCategory.REPLY, classifyEventCategory("Open connection reply received"))
        assertEquals(EventCategory.PAYLOAD, classifyEventCategory("RX Open:MP/SA:MP RPC wrapper"))
        assertEquals(EventCategory.WARNING, classifyEventCategory("UDP timeout while waiting for reply"))
        assertEquals(EventCategory.DIAGNOSTIC, classifyEventCategory("Session reset to initial state"))
    }

    @Test
    fun filterRecentEventsFiltersExpectedCategory() {
        val events = listOf(
            "RX RakNet connected ping",
            "RX Open:MP/SA:MP RPC wrapper",
            "Timeout while waiting for reply",
            "Session reset to initial state",
        )

        assertEquals(listOf("RX RakNet connected ping"), filterRecentEvents(events, EventCategory.HANDSHAKE))
        assertEquals(listOf("Timeout while waiting for reply"), filterRecentEvents(events, EventCategory.WARNING))
        assertEquals(listOf("Session reset to initial state"), filterRecentEvents(events, EventCategory.DIAGNOSTIC))
    }

    @Test
    fun summarizeEventCategoriesCountsTotalAndCategoryBuckets() {
        val events = listOf(
            "RX RakNet connected ping",
            "RX Open:MP/SA:MP RPC wrapper",
            "Timeout while waiting for reply",
            "Session reset to initial state",
        )

        val totals = summarizeEventCategories(events)

        assertEquals(4, totals[EventCategory.ALL])
        assertEquals(1, totals[EventCategory.HANDSHAKE])
        assertEquals(1, totals[EventCategory.PAYLOAD])
        assertEquals(1, totals[EventCategory.WARNING])
        assertEquals(1, totals[EventCategory.DIAGNOSTIC])
    }

    @Test
    fun requireValidNativeCommandRejectsMissingFailSeparator() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidNativeCommand("fail")
        }
    }
}
