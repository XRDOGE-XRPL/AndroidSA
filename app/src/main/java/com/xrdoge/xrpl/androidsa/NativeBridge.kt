package com.xrdoge.xrpl.androidsa

data class NativeOverview(
    val clientName: String,
    val transport: String,
    val connectionState: String,
    val diagnostics: String,
)

internal const val MaxNativeCommandLength = 64
private const val NativeSummaryDelimiter = '|'

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
    if (normalized.startsWith("transport")) {
        val separatorIndex = normalized.indexOf(':')
        require(separatorIndex != -1) { "Transport command must include ':' separator" }
        val keywordTail = normalized.substring("transport".length, separatorIndex)
        require(keywordTail.isEmpty()) { "Transport command keyword is invalid" }
        val transportValue = sanitized.substring(separatorIndex + 1).trim()
        require(transportValue.isNotEmpty()) { "Transport command value must not be blank" }
    }

    val isDiagnosticsCommand = normalized == "diagnostics" || normalized.startsWith("diagnostics:")
    if (isDiagnosticsCommand) {
        val separatorIndex = normalized.indexOf(':')
        require(separatorIndex != -1) { "Diagnostics command must include ':' separator" }
        val keywordTail = normalized.substring("diagnostics".length, separatorIndex)
        require(keywordTail.isEmpty()) { "Diagnostics command keyword is invalid" }
        val diagnosticsValue = sanitized.substring(separatorIndex + 1).trim()
        require(diagnosticsValue.isNotEmpty()) { "Diagnostics command value must not be blank" }
    }
    return sanitized
}

internal fun parseNativeOverview(summary: String): NativeOverview {
    val sections = summary.split('|', limit = 4).map(String::trim)
    return NativeOverview(
        clientName = sections.getOrElse(0) { "AndroidSA" }.ifBlank { "AndroidSA" },
        transport = sections.getOrElse(1) { "unavailable" }.ifBlank { "unavailable" },
        connectionState = sections.getOrElse(2) { "offline" }.ifBlank { "offline" },
        diagnostics = sections.getOrElse(3) { "No diagnostics available" }.ifBlank { "No diagnostics available" },
    )
}

object NativeBridge {
    @Volatile
    private var libraryLoaded = false

    private external fun nativeGetClientSummary(): String
    private external fun nativeDispatchCommand(command: String): Boolean

    @Synchronized
    private fun ensureLibraryLoaded() {
        if (!libraryLoaded) {
            System.loadLibrary("androidsa")
            libraryLoaded = true
        }
    }

    fun overview(): Result<NativeOverview> = runCatching {
        ensureLibraryLoaded()
        parseNativeOverview(nativeGetClientSummary())
    }

    fun refresh(command: String = "ping"): Result<NativeOverview> = runCatching {
        ensureLibraryLoaded()
        val sanitizedCommand = requireValidNativeCommand(command)
        if (nativeDispatchCommand(sanitizedCommand)) {
            overview().getOrThrow()
        } else {
            error("Native command was rejected")
        }
    }
}
