package com.xrdoge.xrpl.androidsa

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import java.util.Locale
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

private const val MaxUiRecentEvents = 12
private const val ServerProfilesPreferencesKey = "androidsa_server_profiles"

private data class ServerProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val lastLatencyMs: Int? = null,
    val lastState: String = "unknown",
    val probeHistory: List<String> = emptyList(),
)

private enum class ServerHealthStatus(val label: String) {
    HEALTHY("healthy"),
    SLOW("slow"),
    UNREACHABLE("unreachable"),
    UNKNOWN("unknown"),
}

internal enum class EventCategory(val label: String) {
    ALL("all"),
    HANDSHAKE("handshake"),
    REPLY("reply"),
    PAYLOAD("payload"),
    WARNING("warning"),
    DIAGNOSTIC("diagnostic"),
}

internal fun classifyEventCategory(event: String): EventCategory {
    val normalized = event.lowercase()
    return when {
        normalized.contains("timeout") || normalized.contains("failed") || normalized.contains("error") -> EventCategory.WARNING
        normalized.contains("rpc wrapper") || normalized.contains("payload") || normalized.contains("0x7d") -> EventCategory.PAYLOAD
        normalized.contains("open connection reply") || normalized.contains("reply") || normalized.contains("0x1d") -> EventCategory.REPLY
        normalized.contains("connected ping") || normalized.contains("open connection request") || normalized.contains("handshake") || normalized.contains("0x00") || normalized.contains("0x1c") -> EventCategory.HANDSHAKE
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
        normalizedState.contains("timeout") || normalizedState.contains("error") || normalizedState.contains("failed") || normalizedState.contains("disconnected") -> ServerHealthStatus.UNREACHABLE
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
    if (normalizedState.contains("timeout") || normalizedState.contains("error") || normalizedState.contains("failed")) {
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
    if (normalizedState.contains("timeout") || normalizedState.contains("error") || normalizedState.contains("failed")) {
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
    val commandMutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
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
                                ?: "Examples: ping, connect, connect:demo.sa-mp.local:7777, player:Guest, transport:udp, latency:42, diagnostics:ok, simulate:rx"
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
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = true,
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
