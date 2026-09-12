package com.xrdoge.xrpl.androidsa

data class NativeOverview(
    val clientName: String,
    val transport: String,
    val connectionState: String,
    val diagnostics: String,
    val serverAddress: String,
    val playerName: String,
    val latencyMs: Int,
    val packetsSent: Int,
    val packetsReceived: Int,
    val connectionAttempts: Int,
    val lastCommand: String,
)

data class NativeClientSnapshot(
    val overview: NativeOverview,
    val recentEvents: List<String>,
)

internal const val MaxNativeCommandLength = 64
private const val NativeSummaryDelimiter = '|'
private const val NativeSummaryFieldCount = 11

private fun requireExactValueCommand(sanitized: String, normalized: String, keyword: String, label: String): String {
    val separatorIndex = normalized.indexOf(':')
    require(separatorIndex != -1) { "$label command must include ':' separator" }
    val keywordTail = normalized.substring(keyword.length, separatorIndex)
    require(keywordTail.isEmpty()) { "$label command keyword is invalid" }
    val value = sanitized.substring(separatorIndex + 1).trim()
    require(value.isNotEmpty()) { "$label command value must not be blank" }
    return value
}

internal fun requireValidNativeCommand(command: String): String {
    require(command.none { it.code < 0x20 || it.code == 0x7F }) {
        "Command must not contain control characters"
    }
    require(!command.contains(NativeSummaryDelimiter)) {
        "Command must not contain '$NativeSummaryDelimiter'"
    }

    val sanitized = command.trim()
    require(sanitized.isNotEmpty()) { "Command must not be blank" }
    require(sanitized.length <= MaxNativeCommandLength) {
        "Command must be at most $MaxNativeCommandLength characters"
    }

    val normalized = sanitized.lowercase()
    when {
        normalized.startsWith("transport") -> {
            requireExactValueCommand(sanitized, normalized, "transport", "Transport")
        }
        normalized == "diagnostics" || normalized.startsWith("diagnostics:") -> {
            requireExactValueCommand(sanitized, normalized, "diagnostics", "Diagnostics")
        }
        normalized == "connect" -> Unit
        normalized.startsWith("connect:") -> {
            requireExactValueCommand(sanitized, normalized, "connect", "Connect")
        }
        normalized.startsWith("player") -> {
            requireExactValueCommand(sanitized, normalized, "player", "Player")
        }
        normalized.startsWith("latency") -> {
            val latencyValue = requireExactValueCommand(sanitized, normalized, "latency", "Latency")
            val latencyMs = latencyValue.toIntOrNull()
            require(latencyMs != null && latencyMs >= 0) {
                "Latency command value must be a non-negative integer"
            }
        }
        normalized.startsWith("fail") -> {
            requireExactValueCommand(sanitized, normalized, "fail", "Fail")
        }
        normalized.startsWith("simulate") -> {
            val simulateValue = requireExactValueCommand(sanitized, normalized, "simulate", "Simulate")
            require(simulateValue.lowercase() in setOf("rx", "tx")) {
                "Simulate command value must be rx or tx"
            }
        }
    }
    return sanitized
}

internal fun parseNativeOverview(summary: String): NativeOverview {
    val rawSections = summary.split(NativeSummaryDelimiter)
    val sections = if (rawSections.size >= NativeSummaryFieldCount) {
        rawSections.take(NativeSummaryFieldCount)
    } else {
        listOf(
            rawSections.getOrElse(0) { "" },
            rawSections.getOrElse(1) { "" },
            rawSections.getOrElse(2) { "" },
            rawSections.drop(3).joinToString(NativeSummaryDelimiter.toString()),
        )
    }.map(String::trim)
    return NativeOverview(
        clientName = sections.getOrElse(0) { "AndroidSA" }.ifBlank { "AndroidSA" },
        transport = sections.getOrElse(1) { "unavailable" }.ifBlank { "unavailable" },
        connectionState = sections.getOrElse(2) { "offline" }.ifBlank { "offline" },
        diagnostics = sections.getOrElse(3) { "No diagnostics available" }.ifBlank { "No diagnostics available" },
        serverAddress = sections.getOrElse(4) { "demo.sa-mp.local:7777" }.ifBlank { "demo.sa-mp.local:7777" },
        playerName = sections.getOrElse(5) { "Guest" }.ifBlank { "Guest" },
        latencyMs = sections.getOrElse(6) { "0" }.toIntOrNull() ?: 0,
        packetsSent = sections.getOrElse(7) { "0" }.toIntOrNull() ?: 0,
        packetsReceived = sections.getOrElse(8) { "0" }.toIntOrNull() ?: 0,
        connectionAttempts = sections.getOrElse(9) { "0" }.toIntOrNull() ?: 0,
        lastCommand = sections.getOrElse(10) { "startup" }.ifBlank { "startup" },
    )
}

internal fun parseNativeEventLog(rawEvents: String): List<String> {
    return rawEvents
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .toList()
        .ifEmpty { listOf("No recent events") }
}

object NativeBridge {
    @Volatile
    private var libraryLoaded = false

    private external fun nativeGetClientSummary(): String
    private external fun nativeGetRecentEvents(): String
    private external fun nativeDispatchCommand(command: String): Boolean

    @Synchronized
    private fun ensureLibraryLoaded() {
        if (!libraryLoaded) {
            System.loadLibrary("androidsa")
            libraryLoaded = true
        }
    }

    fun snapshot(): Result<NativeClientSnapshot> = runCatching {
        ensureLibraryLoaded()
        NativeClientSnapshot(
            overview = parseNativeOverview(nativeGetClientSummary()),
            recentEvents = parseNativeEventLog(nativeGetRecentEvents()),
        )
    }

    fun overview(): Result<NativeOverview> = snapshot().map { it.overview }

    fun refresh(command: String = "ping"): Result<NativeClientSnapshot> = runCatching {
        ensureLibraryLoaded()
        val sanitizedCommand = requireValidNativeCommand(command)
        if (nativeDispatchCommand(sanitizedCommand)) {
            snapshot().getOrThrow()
        } else {
            error("Native command was rejected")
        }
    }
}
