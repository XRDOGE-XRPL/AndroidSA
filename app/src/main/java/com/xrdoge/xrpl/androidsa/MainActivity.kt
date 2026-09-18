package com.xrdoge.xrpl.androidsa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

private const val MaxUiRecentEvents = 12
private const val ServerProfilesPreferencesKey = "androidsa_server_profiles"
private const val GtaRuntimePackageOverrideKey = GtaPackageDetector.PACKAGE_OVERRIDE_KEY
private val DefaultGtaRuntimePackages = GtaPackageDetector.defaultPackages

private fun configuredGtaRuntimePackages(context: Context): List<String> =
    GtaPackageDetector.configuredPackages(context)

private fun detectGtaRuntime(context: Context): GtaRuntimeStatus =
    GtaPackageDetector.detect(context)

private fun launchGtaRuntime(context: Context): Boolean =
    GtaPackageDetector.launch(context)

private data class ServerProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val lastLatencyMs: Int? = null,
    val lastState: String = "unknown",
    val probeHistory: List<String> = emptyList(),
)

private data class GtaRuntimePathEntry(
    val label: String,
    val value: String,
)

private data class GtaConnectionRouteStep(
    val label: String,
    val status: String,
    val detail: String,
    val ready: Boolean,
)

private fun runtimeHealthState(gtaStatus: GtaRuntimeStatus): String = when {
    !gtaStatus.installed -> "missing-host"
    !gtaStatus.launchable -> "blocked"
    else -> "runtime-ready"
}

private fun runtimeDashboardMessage(gtaStatus: GtaRuntimeStatus): String = when {
    !gtaStatus.installed -> "Host missing: GTA SA Mobile is not installed on this device."
    !gtaStatus.launchable -> "Host detected but launch intent is unavailable."
    else -> "Host runtime is ready and launchable on this device."
}

private fun buildGtaConnectionRoute(
    gtaStatus: GtaRuntimeStatus,
    streamState: String,
    streamSurfaceReady: Boolean,
): List<GtaConnectionRouteStep> {
    val hostReady = gtaStatus.installed && gtaStatus.launchable
    val streamReady = streamState in listOf("live", "paused", "starting") || streamSurfaceReady
    return listOf(
        GtaConnectionRouteStep(
            label = "Local device",
            status = when {
                !gtaStatus.installed -> "missing-host"
                !gtaStatus.launchable -> "blocked"
                else -> "runtime-ready"
            },
            detail = "Android runtime host visible on this device",
            ready = hostReady,
        ),
        GtaConnectionRouteStep(
            label = "GTA SA Mobile host",
            status = runtimeHealthState(gtaStatus),
            detail = gtaStatus.summary,
            ready = gtaStatus.installed,
        ),
        GtaConnectionRouteStep(
            label = "Launch probe",
            status = if (gtaStatus.launchable) "runtime-ready" else "blocked",
            detail = if (gtaStatus.launchable) "Launch intent available" else "No launch intent or runtime missing",
            ready = gtaStatus.launchable,
        ),
        GtaConnectionRouteStep(
            label = "Stream diagnostics",
            status = streamState.ifBlank { "idle" },
            detail = if (streamReady) "Local capture/surface state is active" else "Waiting for local stream state",
            ready = streamReady,
        ),
    )
}

private fun runtimeRouteSummary(
    gtaStatus: GtaRuntimeStatus,
    streamState: String,
    streamSurfaceReady: Boolean,
): String {
    val runtimeState = runtimeHealthState(gtaStatus)
    val streamReady = streamState in listOf("live", "paused", "starting") || streamSurfaceReady
    return when {
        runtimeState == "missing-host" -> "Host missing: GTA SA Mobile is not installed on this device."
        runtimeState == "blocked" -> "Host detected but launch intent is unavailable."
        !streamReady -> "Host is runtime-ready; waiting for the local diagnostics stream to become active."
        else -> "Route ready: local host detected, launchable, and diagnostics stream active."
    }
}

private fun safeRuntimePath(value: String?, fallback: String = "unavailable"): String =
    value?.takeIf { it.isNotBlank() } ?: fallback

private fun runtimePathEntries(context: Context, packageName: String): List<GtaRuntimePathEntry> {
    return try {
        val pm = context.packageManager
        val pkg = pm.getPackageInfo(packageName, 0)
        val appInfo = pkg.applicationInfo ?: return emptyList()
        val dataDir = safeRuntimePath(appInfo.dataDir)
        val cacheDir = safeRuntimePath(context.cacheDir?.absolutePath)
        val obbDir = safeRuntimePath(context.obbDir?.absolutePath)
        val externalDir = runCatching { context.getExternalFilesDir(null)?.absolutePath }.getOrNull()?.let { safeRuntimePath(it) } ?: "unavailable"
        val nativeDir = safeRuntimePath(appInfo.nativeLibraryDir)
        listOf(
            GtaRuntimePathEntry("Package", packageName),
            GtaRuntimePathEntry("Source", safeRuntimePath(appInfo.sourceDir)),
            GtaRuntimePathEntry("Data", dataDir),
            GtaRuntimePathEntry("Native libs", nativeDir),
            GtaRuntimePathEntry("Cache", cacheDir),
            GtaRuntimePathEntry("OBB", obbDir),
            GtaRuntimePathEntry("External files", externalDir),
        )
    } catch (_: Exception) {
        listOf(
            GtaRuntimePathEntry("Package", packageName),
            GtaRuntimePathEntry("Path finder", "Runtime not installed or package access unavailable"),
        )
    }
}

private enum class ServerHealthStatus(val label: String) {
    HEALTHY("healthy"),
    SLOW("slow"),
    UNREACHABLE("unreachable"),
    UNKNOWN("unknown"),
}

internal enum class EventCategory(val label: String) {
    ALL("all"),
    STREAM("stream"),
    TRANSPORT("transport"),
    QUERY("query"),
    PARSE("parse"),
    ERROR("error"),
    USER("user"),
    HANDSHAKE("handshake"),
    REPLY("reply"),
    PAYLOAD("payload"),
    WARNING("warning"),
    DIAGNOSTIC("diagnostic"),
}

internal fun classifyEventCategory(event: String): EventCategory {
    val normalized = event.lowercase()
    return when {
        normalized.contains("timeout") || normalized.contains("timed out") -> EventCategory.WARNING
        normalized.contains("failed") || normalized.contains("error") ||
            normalized.contains("rejected") || normalized.contains("disconnected") ||
            normalized.contains("unreachable") -> EventCategory.ERROR
        normalized.contains("connected ping") || normalized.contains("connection request") ||
            normalized.contains("open connection request") || normalized.contains("handshake") ||
            normalized.contains("0x00") || normalized.contains("0x10") || normalized.contains("0x1c") -> EventCategory.HANDSHAKE
        normalized.contains("open connection reply") || normalized.contains("connection accepted") ||
            normalized.contains("new incoming connection") || normalized.contains("reply") ||
            normalized.contains("0x1d") || normalized.contains("0x13") || normalized.contains("0x15") -> EventCategory.REPLY
        normalized.contains("rpc wrapper") || normalized.contains("payload") ||
            normalized.contains("0x7d") || normalized.contains("rpc packet") -> EventCategory.PAYLOAD
        normalized.contains("stream") || normalized.contains("capture") || normalized.contains("projection") -> EventCategory.STREAM
        normalized.contains("transport") || normalized.contains("udp") || normalized.contains("socket") -> EventCategory.TRANSPORT
        normalized.contains("ping") || normalized.contains("query") || normalized.contains("probe") || normalized.contains("server list") -> EventCategory.QUERY
        normalized.contains("parse") || normalized.contains("packet") -> EventCategory.PARSE
        normalized.contains("user") || normalized.contains("manual") || normalized.contains("player") || normalized.contains("launch") -> EventCategory.USER
        else -> EventCategory.DIAGNOSTIC
    }
}

internal fun filterRecentEvents(events: List<String>, category: EventCategory): List<String> {
    if (category == EventCategory.ALL) {
        return events
    }
    val filtered = events.filter { classifyEventCategory(it) == category }
    return filtered.ifEmpty { listOf("No ${category.label} events") }
}

internal fun summarizeEventCategories(events: List<String>): Map<EventCategory, Int> {
    val totals = EventCategory.entries.associateWith { 0 }.toMutableMap()
    totals[EventCategory.ALL] = events.size
    events.forEach { event ->
        val category = classifyEventCategory(event)
        totals[category] = (totals[category] ?: 0) + 1
    }
    return totals
}

private enum class ProbeResult(val label: String, val description: String, val color: Color) {
    IDLE("idle", "No probe evidence yet", Color(0xFF616161)),
    HANDSHAKE("handshake", "Probe reached the server and started a RakNet-style exchange", Color(0xFF1976D2)),
    REPLY("reply", "Server replied with a valid connection/negotiation signal", Color(0xFF2E7D32)),
    PAYLOAD("payload", "RPC wrapper or payload inspection succeeded", Color(0xFF7B1FA2)),
    TIMEOUT("timeout", "Probe failed or timed out before a healthy response", Color(0xFFD32F2F)),
}

private fun serverEndpoint(profile: ServerProfile): String = "${profile.host}:${profile.port}"

private fun loadStoredServerProfiles(context: Context): List<ServerProfile> {
    val prefs = context.getSharedPreferences("androidsa_server_health", Context.MODE_PRIVATE)
    val storedValue = prefs.getString(ServerProfilesPreferencesKey, null) ?: return DefaultServerProfiles
    return storedValue.split(";\n").filter { it.isNotBlank() }.mapNotNull { entry ->
        val parts = entry.split("|")
        if (parts.size < 3) return@mapNotNull null
        val label = parts[0].trim()
        val host = parts[1].trim()
        val port = parts[2].trim().toIntOrNull() ?: return@mapNotNull null
        if (label.isEmpty() || host.isEmpty()) return@mapNotNull null
        val lastState = if (parts.size >= 4) parts[3].trim() else "unknown"
        val probeHistory = if (parts.size >= 5) {
            parts[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        ServerProfile(
            id = "$host:$port",
            label = label,
            host = host,
            port = port,
            lastState = lastState,
            probeHistory = probeHistory,
        )
    }.ifEmpty { DefaultServerProfiles }
}

private fun persistServerProfiles(context: Context, profiles: List<ServerProfile>) {
    val prefs = context.getSharedPreferences("androidsa_server_health", Context.MODE_PRIVATE)
    val payload = profiles.joinToString(";\n") { profile ->
        val history = profile.probeHistory.joinToString(",")
        "${profile.label}|${profile.host}|${profile.port}|${profile.lastState}|$history"
    }
    prefs.edit().putString(ServerProfilesPreferencesKey, payload).apply()
}

private fun classifyServerHealth(profile: ServerProfile): ServerHealthStatus {
    val normalizedState = profile.lastState.lowercase()
    return when {
        normalizedState.contains("connected") && (profile.lastLatencyMs == null || profile.lastLatencyMs <= 150) -> ServerHealthStatus.HEALTHY
        normalizedState.contains("connected") && profile.lastLatencyMs != null && profile.lastLatencyMs <= 500 -> ServerHealthStatus.SLOW
        normalizedState.contains("timeout") || normalizedState.contains("timed out") || normalizedState.contains("error") || normalizedState.contains("failed") || normalizedState.contains("disconnected") -> ServerHealthStatus.UNREACHABLE
        else -> ServerHealthStatus.UNKNOWN
    }
}

private fun serverHealthColor(status: ServerHealthStatus): Color = when (status) {
    ServerHealthStatus.HEALTHY -> Color(0xFF2E7D32)
    ServerHealthStatus.SLOW -> Color(0xFFF9A825)
    ServerHealthStatus.UNREACHABLE -> Color(0xFFD32F2F)
    ServerHealthStatus.UNKNOWN -> Color(0xFF616161)
}

private fun classifyProbeResult(profile: ServerProfile, recentEvents: List<String>): ProbeResult {
    val normalizedState = profile.lastState.lowercase()
    if (normalizedState.contains("timeout") || normalizedState.contains("timed out") || normalizedState.contains("error") || normalizedState.contains("failed")) {
        return ProbeResult.TIMEOUT
    }

    val eventText = recentEvents.joinToString("\n").lowercase()
    return when {
        eventText.contains("0x7d") || eventText.contains("rpc wrapper") || eventText.contains("payload") -> ProbeResult.PAYLOAD
        eventText.contains("0x1d") || eventText.contains("open connection reply") || eventText.contains("reply") -> ProbeResult.REPLY
        eventText.contains("0x1c") || eventText.contains("open connection request") || eventText.contains("connected ping") || eventText.contains("handshake") -> ProbeResult.HANDSHAKE
        normalizedState.contains("connected") -> ProbeResult.REPLY
        else -> ProbeResult.IDLE
    }
}

private fun buildProbeTimeline(profile: ServerProfile, recentEvents: List<String>): List<String> {
    val normalizedState = profile.lastState.lowercase()
    val sequence = mutableListOf<String>()
    if (normalizedState.contains("timeout") || normalizedState.contains("timed out") || normalizedState.contains("error") || normalizedState.contains("failed")) {
        sequence += "timeout after handshake attempt"
        return sequence
    }

    val eventText = recentEvents.joinToString("\n").lowercase()
    if (eventText.contains("0x1c") || eventText.contains("open connection request") || eventText.contains("handshake")) {
        sequence += "handshake started"
    }
    if (eventText.contains("0x1d") || eventText.contains("open connection reply") || eventText.contains("reply")) {
        sequence += "server reply observed"
    }
    if (eventText.contains("0x7d") || eventText.contains("rpc wrapper") || eventText.contains("payload")) {
        sequence += "rpc payload wrapper received"
    }
    if (sequence.isEmpty()) {
        sequence += if (normalizedState.contains("connected")) {
            "connected state without payload trace"
        } else {
            "no signal seen yet"
        }
    }
    return sequence
}

private data class RakNetSignal(
    val code: String,
    val label: String,
    val description: String,
)

private enum class RakNetSignalState(val label: String, val color: Color) {
    IDLE("idle", Color(0xFF616161)),
    HANDSHAKE("handshake", Color(0xFF1976D2)),
    REPLY("reply", Color(0xFF2E7D32)),
    PAYLOAD("payload", Color(0xFF7B1FA2)),
    TIMEOUT("timeout", Color(0xFFD32F2F)),
}

private fun classifyRakNetSignalState(signal: RakNetSignal, recentEvents: List<String>): RakNetSignalState {
    val matchingEvent = recentEvents.firstOrNull { event ->
        event.contains(signal.label, ignoreCase = true) ||
            event.contains(signal.code, ignoreCase = true)
    } ?: return RakNetSignalState.IDLE

    return when {
        matchingEvent.contains("timeout", ignoreCase = true) ||
            matchingEvent.contains("timed out", ignoreCase = true) ||
            matchingEvent.contains("malformed", ignoreCase = true) -> RakNetSignalState.TIMEOUT
        matchingEvent.contains("open connection reply", ignoreCase = true) ||
            matchingEvent.contains("reply", ignoreCase = true) -> RakNetSignalState.REPLY
        matchingEvent.contains("rpc wrapper", ignoreCase = true) ||
            matchingEvent.contains("payload", ignoreCase = true) -> RakNetSignalState.PAYLOAD
        matchingEvent.contains("connected ping", ignoreCase = true) ||
            matchingEvent.contains("open connection request", ignoreCase = true) ||
            matchingEvent.contains("handshake", ignoreCase = true) -> RakNetSignalState.HANDSHAKE
        else -> RakNetSignalState.IDLE
    }
}

private val RakNetSignals = listOf(
    RakNetSignal("0x00", "RakNet connected ping", "Connected-ping handshake signal"),
    RakNetSignal("0x1c", "RakNet open connection request", "Connection-start probe"),
    RakNetSignal("0x1d", "RakNet open connection reply", "Server reply / negotiation"),
    RakNetSignal("0x7d", "Open:MP / SA:MP RPC wrapper", "RPC payload wrapper for protocol inspection"),
)

private data class ProtocolInsight(
    val title: String,
    val value: String,
    val description: String,
    val ready: Boolean,
)

internal data class RakNetProtocolState(
    val observedSignals: List<String>,
    val handshakeState: String,
    val replyState: String,
    val payloadState: String,
    val captureState: String,
) {
    val packetFingerprint: String
        get() = if (observedSignals.isEmpty()) "idle" else observedSignals.joinToString(" / ")
}

internal data class QuerySignalAnalysisRow(
    val phase: String,
    val marker: String,
    val evidence: String,
    val detected: Boolean,
    val confidence: String,
)

internal fun buildQuerySignalAnalysis(recentEvents: List<String>): List<QuerySignalAnalysisRow> {
    val eventText = recentEvents.joinToString("\n").lowercase(Locale.US)
    val signalRows = listOf(
        QuerySignalAnalysisRow(
            phase = "handshake",
            marker = "0x00 / 0x1c",
            evidence = "connected ping and connection request",
            detected = eventText.contains("0x00") || eventText.contains("connected ping") ||
                eventText.contains("0x1c") || eventText.contains("open connection request"),
            confidence = if (eventText.contains("0x00") || eventText.contains("connected ping") ||
                eventText.contains("0x1c") || eventText.contains("open connection request")) "high" else "low",
        ),
        QuerySignalAnalysisRow(
            phase = "reply",
            marker = "0x1d",
            evidence = "server reply / connection acceptance",
            detected = eventText.contains("0x1d") || eventText.contains("open connection reply") ||
                eventText.contains("connection accepted") || eventText.contains("reply"),
            confidence = if (eventText.contains("0x1d") || eventText.contains("open connection reply") ||
                eventText.contains("connection accepted") || eventText.contains("reply")) "high" else "low",
        ),
        QuerySignalAnalysisRow(
            phase = "payload",
            marker = "0x7d",
            evidence = "RPC wrapper / payload inspection",
            detected = eventText.contains("0x7d") || eventText.contains("rpc wrapper") ||
                eventText.contains("payload") || eventText.contains("rpc packet"),
            confidence = if (eventText.contains("0x7d") || eventText.contains("rpc wrapper") ||
                eventText.contains("payload") || eventText.contains("rpc packet")) "high" else "low",
        ),
    )
    return signalRows.map { row ->
        val statusText = if (row.detected) "detected" else "waiting"
        row.copy(confidence = if (row.detected) row.confidence else "idle")
    }
}

internal fun buildRakNetProtocolState(recentEvents: List<String>): RakNetProtocolState {
    val eventText = recentEvents.joinToString("\n").lowercase(Locale.US)
    val normalizedEventText = eventText.replace(" / ", "/")
    val knownSignals = listOf(
        "0x00" to "RakNet connected ping",
        "0x1c" to "RakNet open connection request",
        "0x1d" to "RakNet open connection reply",
        "0x7d" to "Open:MP / SA:MP RPC wrapper",
    )
    val observedSignals = knownSignals.mapNotNull { (code, label) ->
        val normalizedLabel = label.lowercase(Locale.US).replace(" / ", "/")
        val replyVariantLabel = if (code == "0x1d") "connection reply" else normalizedLabel
        if (
            normalizedEventText.contains(code.lowercase(Locale.US)) ||
            normalizedEventText.contains(normalizedLabel) ||
            (code == "0x1d" && normalizedEventText.contains(replyVariantLabel))
        ) {
            code
        } else {
            null
        }
    }
    val handshakeState = if (
        eventText.contains("connected ping") ||
        eventText.contains("open connection request") ||
        eventText.contains("handshake") ||
        observedSignals.contains("0x00") ||
        observedSignals.contains("0x1c")
    ) {
        "in progress"
    } else {
        "waiting"
    }
    val replyState = if (
        eventText.contains("open connection reply") ||
        eventText.contains("reply") ||
        eventText.contains("0x1d")
    ) {
        "replied"
    } else {
        "waiting"
    }
    val payloadState = if (
        eventText.contains("rpc wrapper") ||
        eventText.contains("payload") ||
        eventText.contains("0x7d")
    ) {
        "wrapped"
    } else {
        "quiet"
    }
    val captureState = if (
        eventText.contains("stream") ||
        eventText.contains("capture") ||
        eventText.contains("screen") ||
        eventText.contains("projection")
    ) {
        "active"
    } else {
        "idle"
    }

    return RakNetProtocolState(
        observedSignals = observedSignals,
        handshakeState = handshakeState,
        replyState = replyState,
        payloadState = payloadState,
        captureState = captureState,
    )
}

private fun buildProtocolInsights(recentEvents: List<String>): List<ProtocolInsight> {
    val protocolState = buildRakNetProtocolState(recentEvents)
    return listOf(
        ProtocolInsight(
            title = "Packet fingerprint",
            value = protocolState.packetFingerprint,
            description = "Observed RakNet/Open:MP markers in the local host diagnostics stream.",
            ready = protocolState.observedSignals.isNotEmpty(),
        ),
        ProtocolInsight(
            title = "Handshake state",
            value = protocolState.handshakeState,
            description = "RakNet handshake detection is emphasizing connection start and ping activity.",
            ready = protocolState.handshakeState == "in progress",
        ),
        ProtocolInsight(
            title = "Reply state",
            value = protocolState.replyState,
            description = "Server response monitoring is watching for connection accept and negotiation replies.",
            ready = protocolState.replyState == "replied",
        ),
        ProtocolInsight(
            title = "Payload / RPC state",
            value = protocolState.payloadState,
            description = "Open:MP / SA:MP wrapper inspection is active as a diagnostic signal layer.",
            ready = protocolState.payloadState == "wrapped",
        ),
        ProtocolInsight(
            title = "Local capture state",
            value = protocolState.captureState,
            description = "Host-side capture and runtime state feedback stay inside the local diagnostics model.",
            ready = protocolState.captureState == "active",
        ),
    )
}

private val InitialOverview = NativeOverview(
    clientName = "AndroidSA",
    transport = "loading",
    connectionState = "initializing",
    diagnostics = "Loading native state",
    serverAddress = "demo.sa-mp.local:7777",
    playerName = "Guest",
    latencyMs = 0,
    packetsSent = 0,
    packetsReceived = 0,
    connectionAttempts = 0,
    lastCommand = "startup",
)

private val InitialSnapshot = NativeClientSnapshot(
    overview = InitialOverview,
    recentEvents = listOf("Loading native event log"),
)

private val DefaultServerProfiles = listOf(
    ServerProfile(id = "default-demo", label = "Demo EU", host = "demo.sa-mp.local", port = 7777),
    ServerProfile(id = "default-dev", label = "Local Dev", host = "127.0.0.1", port = 7777),
)

private fun nativeErrorSnapshot(message: String) = NativeClientSnapshot(
    overview = InitialOverview.copy(
        transport = "unavailable",
        connectionState = "error",
        diagnostics = message,
    ),
    recentEvents = listOf(message),
)

private fun loadSnapshotSafely(action: () -> Result<NativeClientSnapshot>): NativeClientSnapshot =
    action().getOrElse { error ->
        nativeErrorSnapshot(error.message ?: "Failed to load native state")
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AndroidSAApp()
            }
        }
    }
}

@Composable
private fun AndroidSAApp() {
    var snapshot by remember { mutableStateOf(InitialSnapshot) }
    var commandText by remember { mutableStateOf("ping") }
    var serverAddressText by remember { mutableStateOf(InitialOverview.serverAddress) }
    var playerNameText by remember { mutableStateOf(InitialOverview.playerName) }
    var transportText by remember { mutableStateOf("RakNet-compatible UDP") }
    var diagnosticsText by remember { mutableStateOf("Ready for manual diagnostics") }
    var latencyText by remember { mutableStateOf("48") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var serverProfiles by remember { mutableStateOf(loadStoredServerProfiles(context)) }
    var selectedServerProfileId by remember { mutableStateOf(serverProfiles.firstOrNull()?.id ?: DefaultServerProfiles.first().id) }
    var newServerLabel by remember { mutableStateOf("Custom") }
    var newServerHost by remember { mutableStateOf("127.0.0.1") }
    var newServerPort by remember { mutableStateOf("7777") }
    var isLoading by remember { mutableStateOf(false) }
    var commandJob by remember { mutableStateOf<Job?>(null) }
    var commandInFlight by remember { mutableStateOf<String?>(null) }
    var eventFilter by remember { mutableStateOf(EventCategory.ALL) }
    var streamCaptureState by remember { mutableStateOf(StreamCaptureState()) }
    var streamSurfaceReady by remember { mutableStateOf(false) }
    var gtaRuntimeStatus by remember(context) { mutableStateOf(detectGtaRuntime(context)) }
    val gtaRuntimePathEntries = remember(context, gtaRuntimeStatus.packageName) {
        runtimePathEntries(context, gtaRuntimeStatus.packageName)
    }
    val commandMutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
    val capturePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val resultCode = result.resultCode
        val data = result.data
        if (resultCode == Activity.RESULT_OK && data != null) {
            streamCaptureState = StreamCaptureState(state = "starting", errorReason = null)
            StreamCaptureService.startWithProjection(context, resultCode, data)
            streamCaptureState = StreamCaptureService.currentState()
        } else {
            streamCaptureState = StreamCaptureState(
                state = "error",
                errorReason = "Screen capture permission was denied",
            )
        }
    }
    LaunchedEffect(streamCaptureState.state) {
        if (streamCaptureState.state !in listOf("starting", "live", "paused", "need_permission")) {
            return@LaunchedEffect
        }
        while (true) {
            delay(1000)
            val liveState = StreamCaptureService.currentState()
            streamCaptureState = liveState
            if (liveState.state !in listOf("starting", "live", "paused", "need_permission")) {
                break
            }
        }
    }
    LaunchedEffect(serverProfiles) {
        persistServerProfiles(context, serverProfiles)
    }
    val overview = snapshot.overview
    val eventCounts = summarizeEventCategories(snapshot.recentEvents)
    val filteredRecentEvents = remember(snapshot.recentEvents, eventFilter) {
        filterRecentEvents(snapshot.recentEvents, eventFilter)
    }
    val txRxRatio = when {
        overview.packetsSent == 0 && overview.packetsReceived == 0 -> "0.00"
        overview.packetsReceived == 0 -> "∞"
        else -> String.format(Locale.US, "%.2f", overview.packetsSent.toDouble() / overview.packetsReceived.toDouble())
    }
    val isBusy = isLoading || commandJob?.isActive == true || commandInFlight != null
    val commandError = remember(commandText) {
        runCatching {
            requireValidNativeCommand(commandText)
        }.exceptionOrNull()?.message
    }
    val newServerPortValue = newServerPort.trim().toIntOrNull()
    val canAddServerProfile = newServerHost.isNotBlank() &&
        newServerLabel.isNotBlank() &&
        newServerPortValue != null &&
        newServerPortValue in 1..65535

    fun applySnapshot(newSnapshot: NativeClientSnapshot, syncInputs: Boolean = true) {
        snapshot = newSnapshot
        if (syncInputs) {
            serverAddressText = newSnapshot.overview.serverAddress
            playerNameText = newSnapshot.overview.playerName
            transportText = newSnapshot.overview.transport
            diagnosticsText = newSnapshot.overview.diagnostics
            latencyText = newSnapshot.overview.latencyMs.toString()
        }
        val state = runtimeHealthState(gtaRuntimeStatus)
        val dashboardMessage = runtimeDashboardMessage(gtaRuntimeStatus)
        when {
            state == "missing-host" -> {
                diagnosticsText = dashboardMessage
            }
            state == "blocked" -> {
                diagnosticsText = dashboardMessage
            }
            state == "runtime-ready" -> {
                diagnosticsText = if (newSnapshot.overview.diagnostics.isNotBlank()) {
                    newSnapshot.overview.diagnostics
                } else {
                    dashboardMessage
                }
            }
        }
    }

    fun applyLocalError(message: String) {
        val baseSnapshot = snapshot
        applySnapshot(
            baseSnapshot.copy(
                overview = baseSnapshot.overview.copy(
                    connectionState = "error",
                    diagnostics = message,
                ),
                recentEvents = (listOf(message) + baseSnapshot.recentEvents).take(MaxUiRecentEvents),
            ),
            syncInputs = false,
        )
    }

    fun trackServerMetricFromSnapshot(snapshotToTrack: NativeClientSnapshot) {
        val activeAddress = snapshotToTrack.overview.serverAddress
        val activeIndex = serverProfiles.indexOfFirst { serverEndpoint(it) == activeAddress }
        if (activeIndex == -1) {
            return
        }
        val current = serverProfiles[activeIndex]
        val nextProbeHistory = buildProbeTimeline(current, snapshotToTrack.recentEvents)
        val updated = current.copy(
            lastLatencyMs = snapshotToTrack.overview.latencyMs,
            lastState = snapshotToTrack.overview.connectionState,
            probeHistory = nextProbeHistory,
        )
        if (updated != current) {
            serverProfiles = serverProfiles.toMutableList().also { it[activeIndex] = updated }
        }
    }

    val dispatchCommand: (String) -> Unit = dispatch@{ commandToDispatch ->
        if (isBusy || !commandMutex.tryLock()) {
            return@dispatch
        }

        val sanitizedCommand = runCatching {
            requireValidNativeCommand(commandToDispatch)
        }.getOrElse { validationError ->
            commandMutex.unlock()
            applyLocalError(validationError.message ?: "Native command validation failed")
            return@dispatch
        }

        isLoading = true
        commandInFlight = sanitizedCommand
        val launchedJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    NativeBridge.refresh(sanitizedCommand)
                }
                val refreshedSnapshot = result.getOrElse {
                    applyLocalError(it.message ?: "Native command failed")
                    return@getOrElse snapshot
                }
                applySnapshot(refreshedSnapshot, syncInputs = true)
                trackServerMetricFromSnapshot(refreshedSnapshot)
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }
                applyLocalError(error.message ?: "Native command failed")
            } finally {
                val finishingJob = coroutineContext[Job]
                if (commandJob === finishingJob) {
                    isLoading = false
                    commandInFlight = null
                    commandJob = null
                }
                if (commandMutex.isLocked) {
                    commandMutex.unlock()
                }
            }
        }
        commandJob = launchedJob
    }

    fun dispatchPreset(command: String) {
        commandText = command
        dispatchCommand(command)
    }

    fun dispatchServerProfile(profile: ServerProfile, pingOnly: Boolean) {
        selectedServerProfileId = profile.id
        serverAddressText = serverEndpoint(profile)
        if (pingOnly) {
            dispatchPreset("ping")
            return
        }
        dispatchPreset("connect:${serverEndpoint(profile)}")
    }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            val initialSnapshot = withContext(Dispatchers.IO) {
                loadSnapshotSafely { NativeBridge.snapshot() }
            }
            applySnapshot(initialSnapshot)
            trackServerMetricFromSnapshot(initialSnapshot)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(gtaRuntimeStatus.state) {
        val state = gtaRuntimeStatus.state
        val message = runtimeDashboardMessage(gtaRuntimeStatus)
        val runtimeStatusSnapshot = snapshot.copy(
            overview = snapshot.overview.copy(
                connectionState = state,
                diagnostics = if (snapshot.overview.diagnostics.isBlank()) message else snapshot.overview.diagnostics,
            ),
            recentEvents = listOf(message) + snapshot.recentEvents,
        )
        applySnapshot(runtimeStatusSnapshot, syncInputs = false)
    }

    val shouldAutoRefreshMetrics = overview.connectionState.equals("connected", ignoreCase = true)
    LaunchedEffect(shouldAutoRefreshMetrics) {
        if (!shouldAutoRefreshMetrics) {
            return@LaunchedEffect
        }
        while (true) {
            delay(1000)
            if (isBusy) {
                continue
            }
            val autoRefreshSnapshot = withContext(Dispatchers.IO) {
                loadSnapshotSafely { NativeBridge.snapshot() }
            }
            applySnapshot(autoRefreshSnapshot, syncInputs = false)
            trackServerMetricFromSnapshot(autoRefreshSnapshot)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = overview.clientName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "AndroidSA Server Health Dashboard — SA:MP / Open:MP server diagnostics and launcher status.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SectionCard(title = "Session overview") {
                OverviewValueRow(title = "Transport", value = overview.transport)
                OverviewValueRow(title = "Connection", value = overview.connectionState)
                OverviewValueRow(title = "Diagnostics", value = overview.diagnostics)
                OverviewValueRow(title = "Server", value = overview.serverAddress)
                OverviewValueRow(title = "Player", value = overview.playerName)
            }
            SectionCard(title = "Runtime stats") {
                OverviewValueRow(title = "Latency", value = "${overview.latencyMs} ms")
                OverviewValueRow(title = "Packets sent", value = overview.packetsSent.toString())
                OverviewValueRow(title = "Packets received", value = overview.packetsReceived.toString())
                OverviewValueRow(title = "TX/RX ratio", value = txRxRatio)
                OverviewValueRow(title = "Reconnect attempts", value = overview.connectionAttempts.toString())
                OverviewValueRow(title = "Last command", value = overview.lastCommand)
                OverviewValueRow(title = "Active operation", value = commandInFlight ?: "idle")
            }
            SectionCard(title = "Server health dashboard") {
                serverProfiles.forEach { profile ->
                    val status = classifyServerHealth(profile)
                    val statusColor = serverHealthColor(status)
                    val probeResult = classifyProbeResult(profile, snapshot.recentEvents)
                    val statusDescription = when (status) {
                        ServerHealthStatus.HEALTHY -> "Responsive server"
                        ServerHealthStatus.SLOW -> "Slow response"
                        ServerHealthStatus.UNREACHABLE -> "No healthy response"
                        ServerHealthStatus.UNKNOWN -> "Awaiting probe"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${profile.label} · ${serverEndpoint(profile)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "$statusDescription · last latency: ${profile.lastLatencyMs ?: 0} ms",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "Probe result: ${probeResult.label} · ${probeResult.description}",
                                color = probeResult.color,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = status.label,
                            color = statusColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            SectionCard(title = "Probe detail view") {
                serverProfiles.forEach { profile ->
                    val probeResult = classifyProbeResult(profile, snapshot.recentEvents)
                    val timeline = profile.probeHistory.ifEmpty { buildProbeTimeline(profile, snapshot.recentEvents) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${profile.label} · ${serverEndpoint(profile)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Current result: ${probeResult.label}",
                            color = probeResult.color,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        timeline.forEach { step ->
                            Text(
                                text = "• $step",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            SectionCard(title = "RakNet / Open:MP signal diagnostics") {
                RakNetSignals.forEach { signal ->
                    val signalState = classifyRakNetSignalState(signal, snapshot.recentEvents)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${signal.code} · ${signal.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = signal.description,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = signalState.label,
                            color = signalState.color,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            SectionCard(title = "RakNet / Open:MP protocol intelligence") {
                val protocolInsights = buildProtocolInsights(snapshot.recentEvents)
                protocolInsights.forEach { insight ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (insight.ready) Color(0xFF2E7D32) else Color(0xFF616161)),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = insight.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = insight.value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = insight.description,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            SectionCard(title = "Query / signal analysis") {
                val querySignals = buildQuerySignalAnalysis(snapshot.recentEvents)
                querySignals.forEach { signal ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${signal.phase} · ${signal.marker}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = signal.evidence,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (signal.detected) "detected" else "waiting",
                                color = if (signal.detected) Color(0xFF2E7D32) else Color(0xFF616161),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = signal.confidence,
                                color = if (signal.detected) Color(0xFF90CAF9) else Color(0xFFB0BEC5),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            SectionCard(title = "GTA connection & path finder") {
                val routeSteps = buildGtaConnectionRoute(
                    gtaStatus = gtaRuntimeStatus,
                    streamState = streamCaptureState.state,
                    streamSurfaceReady = streamSurfaceReady,
                )
                val routeStatusText = runtimeRouteSummary(
                    gtaStatus = gtaRuntimeStatus,
                    streamState = streamCaptureState.state,
                    streamSurfaceReady = streamSurfaceReady,
                )

                Text(
                    text = "Connection route: local device -> GTA SA Mobile host -> launch probe -> diagnostics stream",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1565C0),
                )
                Text(
                    text = routeStatusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (gtaRuntimeStatus.installed && gtaRuntimeStatus.launchable) Color(0xFF2E7D32) else Color(0xFFE0E0E0),
                )
                Text(
                    text = "This layer only inspects the local GTA runtime, package paths, and runtime metadata; it does not become a gameplay client.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(label = "Detect host", enabled = !isBusy) {
                        val detected = detectGtaRuntime(context)
                        gtaRuntimeStatus = detected
                        val hostMessage = runtimeDashboardMessage(detected)
                        applySnapshot(
                            snapshot.copy(
                                overview = snapshot.overview.copy(
                                    connectionState = detected.state,
                                    diagnostics = hostMessage,
                                ),
                                recentEvents = listOf(hostMessage) + snapshot.recentEvents,
                            ),
                            syncInputs = false,
                        )
                    }
                    ActionButton(label = "Open runtime", enabled = !isBusy && gtaRuntimeStatus.launchable) {
                        val launched = launchGtaRuntime(context)
                        gtaRuntimeStatus = detectGtaRuntime(context)
                        val hostMessage = runtimeDashboardMessage(gtaRuntimeStatus)
                        if (!launched) {
                            applyLocalError("GTA SA Mobile launch intent is unavailable on this device")
                        } else {
                            applySnapshot(
                                snapshot.copy(
                                    overview = snapshot.overview.copy(
                                        connectionState = gtaRuntimeStatus.state,
                                        diagnostics = hostMessage,
                                    ),
                                    recentEvents = listOf(hostMessage) + snapshot.recentEvents,
                                ),
                                syncInputs = false,
                            )
                        }
                    }
                }

                routeSteps.forEach { step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (step.ready) Color(0xFF2E7D32) else Color(0xFF616161),
                                ),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = step.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${step.status} · ${step.detail}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                gtaRuntimePathEntries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = entry.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE0E0E0),
                        )
                    }
                }
            }
            SectionCard(title = "Naht B: GTA SA Mobile host") {
                val streamState = streamCaptureState.state
                val streamFps = "${streamCaptureState.captureFps} fps"
                val captureLatencyMs = "${streamCaptureState.captureLatencyMs} ms"
                val streamGeometry = "${streamCaptureState.frameWidth}x${streamCaptureState.frameHeight}"
                val streamSurfaceState = if (streamSurfaceReady) "ready" else "waiting"

                Text(
                    text = "Scope: local host detection + launch only; no join/sync, no GTA V, no Play Core, no gameplay-client integration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1565C0),
                )
                Text(
                    text = "Phase 2 stays local: capture/surface status and runtime info only, never a multiplayer client.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                )

                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                    val captureSurface = Surface(surface)
                                    StreamCaptureService.attachSurface(captureSurface)
                                    streamSurfaceReady = true
                                    if (streamCaptureState.state == "starting") {
                                        streamCaptureState = StreamCaptureService.currentState()
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit
                                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                    streamSurfaceReady = false
                                    StreamCaptureService.clearSurface()
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )

                Text("Host package: ${gtaRuntimeStatus.packageName}")
                Text("Version: ${gtaRuntimeStatus.versionName}")
                Text("State: ${gtaRuntimeStatus.state}")
                Text("Launch intent: ${if (gtaRuntimeStatus.launchable) "available" else "unavailable"}")
                Text(gtaRuntimeStatus.summary)
                Text("Capture status: ${streamCaptureState.description()}")
                Text("Capture geometry: $streamGeometry · Surface: $streamSurfaceState")
                Text("Capture metrics: FPS ${streamCaptureState.captureFps}, latency ${streamCaptureState.captureLatencyMs} ms, dropped ${streamCaptureState.droppedFrames} frames")
                if (streamCaptureState.errorReason != null) {
                    Text("Stream error: ${streamCaptureState.errorReason}", color = Color(0xFFD32F2F))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        label = if (gtaRuntimeStatus.installed) "Launch host" else "Install host",
                        enabled = !isBusy,
                    ) {
                        val launched = launchGtaRuntime(context)
                        gtaRuntimeStatus = detectGtaRuntime(context)
                        if (launched) {
                            dispatchPreset("stream:start")
                        } else if (!gtaRuntimeStatus.installed) {
                            applyLocalError("GTA SA Mobile runtime missing: install the GTA SA Mobile package first")
                        } else {
                            applyLocalError("GTA SA Mobile launch intent is unavailable on this device")
                        }
                    }
                    ActionButton(
                        label = if (streamCaptureState.state == "live") "Stream running" else if (streamCaptureState.state == "paused") "Resume stream" else "Stream start",
                        enabled = !isBusy && streamSurfaceReady,
                    ) {
                        if (streamCaptureState.state == "live") {
                            StreamCaptureService.requestStop(context)
                            streamCaptureState = StreamCaptureState(state = "stopped")
                            dispatchPreset("stream:stop")
                        } else if (streamCaptureState.state == "paused") {
                            streamCaptureState = StreamCaptureState(state = "starting", errorReason = null)
                            StreamCaptureService.startWithProjection(context, Activity.RESULT_OK, Intent())
                            dispatchPreset("stream:start")
                        } else {
                            val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
                            val captureIntent = projectionManager.createScreenCaptureIntent()
                            streamCaptureState = StreamCaptureState(state = "need_permission", errorReason = null)
                            capturePermissionLauncher.launch(captureIntent)
                            dispatchPreset("stream:start")
                        }
                    }
                    ActionButton(label = "Stream stop", enabled = !isBusy) {
                        StreamCaptureService.requestStop(context)
                        streamCaptureState = StreamCaptureState(state = "stopped")
                        dispatchPreset("stream:stop")
                    }
                    ActionButton(label = "Stream pause", enabled = !isBusy) {
                        StreamCaptureService.requestPause(context)
                        streamCaptureState = StreamCaptureState(state = "paused")
                        dispatchPreset("stream:pause")
                    }
                    ActionButton(label = "Stream info", enabled = !isBusy) {
                        val infoState = StreamCaptureService.currentState()
                        streamCaptureState = infoState
                        dispatchPreset("stream:info")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PACKAGE: ${gtaRuntimeStatus.packageName}")
                    Text("VERSION: ${gtaRuntimeStatus.versionName}")
                    Text("STREAM_STATE: ${streamState}")
                    Text("FPS: $streamFps")
                    Text("FRAME: ${streamCaptureState.frameWidth}x${streamCaptureState.frameHeight}")
                    Text("DROPPED: ${streamCaptureState.droppedFrames}")
                    Text("CAPTURE_MS: $captureLatencyMs")
                }

                var gtaPackageOverrideText by remember(context, gtaRuntimeStatus.packageName) {
                    mutableStateOf(configuredGtaRuntimePackages(context).joinToString(","))
                }
                OutlinedTextField(
                    value = gtaPackageOverrideText,
                    onValueChange = { gtaPackageOverrideText = it },
                    label = { Text("Package override") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    val packages = gtaPackageOverrideText.split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    val prefs = context.getSharedPreferences("androidsa_runtime", Context.MODE_PRIVATE)
                    if (packages.isEmpty()) {
                        prefs.edit().remove(GtaRuntimePackageOverrideKey).apply()
                    } else {
                        prefs.edit().putString(GtaRuntimePackageOverrideKey, packages.joinToString(",")).apply()
                    }
                    gtaRuntimeStatus = detectGtaRuntime(context)
                    dispatchPreset("stream:source:${gtaRuntimeStatus.packageName}")
                }) {
                    Text("Apply package override")
                }
            }
            SectionCard(title = "Guided controls") {
                SectionCard(title = "Server browser") {
                    OutlinedTextField(
                        value = newServerLabel,
                        onValueChange = { newServerLabel = it },
                        label = { Text("Profile label") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        singleLine = true,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newServerHost,
                            onValueChange = { newServerHost = it },
                            label = { Text("Host/IP") },
                            modifier = Modifier.weight(2f),
                            enabled = !isBusy,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = newServerPort,
                            onValueChange = { newServerPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f),
                            enabled = !isBusy,
                            singleLine = true,
                        )
                    }
                    Button(
                        enabled = !isBusy && canAddServerProfile,
                        onClick = {
                            val parsedPort = newServerPortValue ?: return@Button
                            val normalizedHost = newServerHost.trim()
                            val normalizedLabel = newServerLabel.trim()
                            val newProfile = ServerProfile(
                                id = "${normalizedHost}:${parsedPort}",
                                label = normalizedLabel,
                                host = normalizedHost,
                                port = parsedPort,
                            )
                            if (serverProfiles.none { it.id == newProfile.id }) {
                                serverProfiles = serverProfiles + newProfile
                            }
                            selectedServerProfileId = newProfile.id
                            serverAddressText = serverEndpoint(newProfile)
                            persistServerProfiles(context, serverProfiles)
                        },
                    ) {
                        Text("Add server profile")
                    }
                    serverProfiles.forEach { profile ->
                        val isSelected = profile.id == selectedServerProfileId
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "${profile.label} (${serverEndpoint(profile)})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                                val profileStatus = classifyServerHealth(profile)
                                val profileStatusColor = serverHealthColor(profileStatus)
                                Text(
                                    text = "Health: ${profileStatus.label} · Last state: ${profile.lastState} · Last latency: ${profile.lastLatencyMs ?: 0} ms",
                                    color = profileStatusColor,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ActionButton(label = "Select", enabled = !isBusy) {
                                        selectedServerProfileId = profile.id
                                        serverAddressText = serverEndpoint(profile)
                                    }
                                    ActionButton(label = "Connect", enabled = !isBusy) {
                                        dispatchServerProfile(profile, pingOnly = false)
                                    }
                                    ActionButton(label = "Ping", enabled = !isBusy) {
                                        dispatchServerProfile(profile, pingOnly = true)
                                    }
                                    ActionButton(
                                        label = "Remove",
                                        enabled = !isBusy && !profile.id.startsWith("default-"),
                                    ) {
                                        val remaining = serverProfiles.filterNot { it.id == profile.id }
                                        serverProfiles = remaining
                                        if (selectedServerProfileId == profile.id && remaining.isNotEmpty()) {
                                            selectedServerProfileId = remaining.first().id
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = serverAddressText,
                    onValueChange = { serverAddressText = it },
                    label = { Text("Server address") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    singleLine = true,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(label = "Connect server", enabled = !isBusy && serverAddressText.isNotBlank()) {
                        dispatchPreset("connect:${serverAddressText.trim()}")
                    }
                    ActionButton(label = "Reconnect", enabled = !isBusy) {
                        dispatchPreset("reconnect")
                    }
                    ActionButton(label = "Disconnect", enabled = !isBusy) {
                        dispatchPreset("disconnect")
                    }
                    ActionButton(label = "Ping", enabled = !isBusy) {
                        dispatchPreset("ping")
                    }
                }

                OutlinedTextField(
                    value = playerNameText,
                    onValueChange = { playerNameText = it },
                    label = { Text("Player name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    singleLine = true,
                )
                Button(
                    enabled = !isBusy && playerNameText.isNotBlank(),
                    onClick = { dispatchPreset("player:${playerNameText.trim()}") },
                ) {
                    Text("Apply player")
                }

                OutlinedTextField(
                    value = transportText,
                    onValueChange = { transportText = it },
                    label = { Text("Transport profile") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    singleLine = true,
                )
                Button(
                    enabled = !isBusy && transportText.isNotBlank(),
                    onClick = { dispatchPreset("transport:${transportText.trim()}") },
                ) {
                    Text("Apply transport")
                }

                OutlinedTextField(
                    value = latencyText,
                    onValueChange = { latencyText = it },
                    label = { Text("Latency override (ms)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    singleLine = true,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        label = "Apply latency",
                        enabled = !isBusy && latencyText.trim().toIntOrNull()?.let { it >= 0 } == true,
                    ) {
                        dispatchPreset("latency:${latencyText.trim()}")
                    }
                    ActionButton(label = "Sim RX", enabled = !isBusy) {
                        dispatchPreset("simulate:rx")
                    }
                    ActionButton(label = "Sim TX", enabled = !isBusy) {
                        dispatchPreset("simulate:tx")
                    }
                }

                OutlinedTextField(
                    value = diagnosticsText,
                    onValueChange = { diagnosticsText = it },
                    label = { Text("Diagnostics message") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    singleLine = true,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(label = "Status", enabled = !isBusy) {
                        dispatchPreset("status")
                    }
                    ActionButton(
                        label = "Apply diag",
                        enabled = !isBusy && diagnosticsText.isNotBlank(),
                    ) {
                        dispatchPreset("diagnostics:${diagnosticsText.trim()}")
                    }
                    ActionButton(label = "Fail demo", enabled = !isBusy) {
                        dispatchPreset("fail:simulated timeout")
                    }
                }
                Button(
                    enabled = !isBusy,
                    onClick = { dispatchPreset("reset") },
                ) {
                    Text("Reset session")
                }
            }
            SectionCard(title = "Manual command") {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    label = { Text("Native command") },
                    supportingText = {
                        Text(
                            commandError
                                ?: "Examples: ping, connect, connect:demo.sa-mp.local:7777, player:Guest, transport:udp, latency:42, diagnostics:ok, protocol:handshake, protocol:status, simulate:rx, stream:start, stream:stop, stream:info"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    isError = commandError != null,
                    singleLine = true,
                )
                Button(
                    enabled = !isBusy && commandError == null,
                    onClick = {
                        dispatchCommand(commandText)
                    },
                ) {
                    if (isBusy) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                            )
                            Text("Dispatching command")
                        }
                    } else {
                        Text("Dispatch manual command")
                    }
                }
            }
            SectionCard(title = "Recent events") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EventCategory.entries.forEach { category ->
                        val isSelected = eventFilter == category
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = true,
                            colors = if (isSelected) {
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1976D2),
                                    contentColor = Color(0xFFFFFFFF),
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2C2C2C),
                                    contentColor = Color(0xFFEAEAEA),
                                )
                            },
                            onClick = { eventFilter = category },
                        ) {
                            Text("${category.label} (${eventCounts[category] ?: 0})")
                        }
                    }
                }
                filteredRecentEvents.forEach { event ->
                    Text(text = "• $event", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun RowScope.ActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        modifier = Modifier.weight(1f),
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(label)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                content()
            },
        )
    }
}

@Composable
private fun OverviewValueRow(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
