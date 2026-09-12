package com.xrdoge.xrpl.androidsa

data class NativeOverview(
    val clientName: String,
    val transport: String,
    val connectionState: String,
    val diagnostics: String,
)

internal fun parseNativeOverview(summary: String): NativeOverview {
    val sections = summary.split('|')
    return NativeOverview(
        clientName = sections.getOrElse(0) { "AndroidSA" },
        transport = sections.getOrElse(1) { "unavailable" },
        connectionState = sections.getOrElse(2) { "offline" },
        diagnostics = sections.getOrElse(3) { "No diagnostics available" },
    )
}

object NativeBridge {
    init {
        System.loadLibrary("androidsa")
    }

    private external fun nativeGetClientSummary(): String
    private external fun nativeDispatchCommand(command: String): Boolean

    fun overview(): NativeOverview = parseNativeOverview(nativeGetClientSummary())

    fun refresh(command: String = "ping"): NativeOverview {
        nativeDispatchCommand(command)
        return overview()
    }
}
