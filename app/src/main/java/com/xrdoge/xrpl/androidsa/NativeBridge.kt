package com.xrdoge.xrpl.androidsa

data class NativeOverview(
    val clientName: String,
    val transport: String,
    val connectionState: String,
    val diagnostics: String,
)

internal fun parseNativeOverview(summary: String): NativeOverview {
    val sections = summary.split('|', limit = 4)
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
        if (nativeDispatchCommand(command)) {
            overview().getOrThrow()
        } else {
            error("Native command was rejected")
        }
    }
}
