package com.xrdoge.xrpl.androidsa

import android.os.Bundle
import androidx.activity.ComponentActivity
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

private data class ServerProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val lastLatencyMs: Int? = null,
    val lastState: String = "unknown",
)

private fun serverEndpoint(profile: ServerProfile): String = "${profile.host}:${profile.port}"

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
    var serverProfiles by remember { mutableStateOf(DefaultServerProfiles) }
    var selectedServerProfileId by remember { mutableStateOf(DefaultServerProfiles.first().id) }
    var newServerLabel by remember { mutableStateOf("Custom") }
    var newServerHost by remember { mutableStateOf("127.0.0.1") }
    var newServerPort by remember { mutableStateOf("7777") }
    var isLoading by remember { mutableStateOf(false) }
    var commandJob by remember { mutableStateOf<Job?>(null) }
    var commandInFlight by remember { mutableStateOf<String?>(null) }
    val commandMutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
    val overview = snapshot.overview
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
        applySnapshot(
            snapshot.copy(
                overview = snapshot.overview.copy(
                    connectionState = "error",
                    diagnostics = message,
                ),
                recentEvents = (listOf(message) + snapshot.recentEvents).take(MaxUiRecentEvents),
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
        val updated = current.copy(
            lastLatencyMs = snapshotToTrack.overview.latencyMs,
            lastState = snapshotToTrack.overview.connectionState,
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
                applySnapshot(
                    result.getOrElse {
                        applyLocalError(it.message ?: "Native command failed")
                        return@getOrElse snapshot
                    },
                    syncInputs = true,
                )
                trackServerMetricFromSnapshot(snapshot)
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
            dispatchPreset("connect:${serverEndpoint(profile)}")
            return
        }
        dispatchPreset("connect:${serverEndpoint(profile)}")
    }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            applySnapshot(
                withContext(Dispatchers.IO) {
                    loadSnapshotSafely { NativeBridge.snapshot() }
                },
            )
            trackServerMetricFromSnapshot(snapshot)
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
            applySnapshot(
                withContext(Dispatchers.IO) {
                    loadSnapshotSafely { NativeBridge.snapshot() }
                },
                syncInputs = false,
            )
            trackServerMetricFromSnapshot(snapshot)
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
                text = "Expanded Android control surface for SA:MP / Open:MP runtime state, guided commands, and diagnostics.",
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
                OverviewValueRow(title = "Reconnect attempts", value = overview.connectionAttempts.toString())
                OverviewValueRow(title = "Last command", value = overview.lastCommand)
                OverviewValueRow(title = "Active operation", value = commandInFlight ?: "idle")
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
                                Text(
                                    text = "Last state: ${profile.lastState} · Last latency: ${profile.lastLatencyMs ?: 0} ms",
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
                snapshot.recentEvents.forEach { event ->
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
