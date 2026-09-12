package com.xrdoge.xrpl.androidsa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val InitialOverview = NativeOverview(
    clientName = "AndroidSA",
    transport = "loading",
    connectionState = "initializing",
    diagnostics = "Loading native state",
)

private fun nativeErrorOverview(message: String) = InitialOverview.copy(
    transport = "unavailable",
    connectionState = "error",
    diagnostics = message,
)

private fun loadOverviewSafely(action: () -> Result<NativeOverview>): NativeOverview =
    action().getOrElse { error ->
        nativeErrorOverview(error.message ?: "Failed to load native state")
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
    var overview by remember { mutableStateOf(InitialOverview) }
    var commandText by remember { mutableStateOf("ping") }
    var isLoading by remember { mutableStateOf(false) }
    var commandJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val isCommandDispatchInProgress = commandJob?.isActive == true
    val commandError = remember(commandText) {
        runCatching {
            requireValidNativeCommand(commandText)
        }.exceptionOrNull()?.message
    }
    val dispatchCommand: (String) -> Unit = dispatch@{ commandToDispatch ->
        if (isLoading || isCommandDispatchInProgress) {
            return@dispatch
        }

        val sanitizedCommand = runCatching {
            requireValidNativeCommand(commandToDispatch)
        }.getOrElse { validationError ->
            overview = overview.copy(
                connectionState = "error",
                diagnostics = validationError.message ?: "Native command validation failed",
            )
            return@dispatch
        }

        isLoading = true
        val launchedJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    NativeBridge.refresh(sanitizedCommand)
                }
                overview = result.getOrElse {
                    overview.copy(
                        connectionState = "error",
                        diagnostics = it.message ?: "Native command failed",
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    throw error
                }
                overview = overview.copy(
                    connectionState = "error",
                    diagnostics = error.message ?: "Native command failed",
                )
            } finally {
                val finishingJob = coroutineContext[Job]
                if (commandJob === finishingJob) {
                    isLoading = false
                    commandJob = null
                }
            }
        }
        commandJob = launchedJob
    }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            overview = withContext(Dispatchers.IO) {
                loadOverviewSafely { NativeBridge.overview() }
            }
        } finally {
            isLoading = false
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = overview.clientName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "JNI/Kotlin bridge for SA:MP / Open:MP on Android.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OverviewCard(title = "Transport", value = overview.transport)
            OverviewCard(title = "Connection", value = overview.connectionState)
            OverviewCard(title = "Diagnostics", value = overview.diagnostics)
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                label = { Text("Native command") },
                supportingText = {
                    Text(commandError ?: "Examples: ping, connect, disconnect, reset, status, transport:udp, diagnostics:ok")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                isError = commandError != null,
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickCommandButton(
                    label = "Ping",
                    command = "ping",
                    enabled = !isLoading && !isCommandDispatchInProgress,
                ) { command ->
                    commandText = command
                    dispatchCommand(command)
                }
                QuickCommandButton(
                    label = "Connect",
                    command = "connect",
                    enabled = !isLoading && !isCommandDispatchInProgress,
                ) { command ->
                    commandText = command
                    dispatchCommand(command)
                }
                QuickCommandButton(
                    label = "Status",
                    command = "status",
                    enabled = !isLoading && !isCommandDispatchInProgress,
                ) { command ->
                    commandText = command
                    dispatchCommand(command)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickCommandButton(
                    label = "Disconnect",
                    command = "disconnect",
                    enabled = !isLoading && !isCommandDispatchInProgress,
                ) { command ->
                    commandText = command
                    dispatchCommand(command)
                }
                QuickCommandButton(
                    label = "Reset",
                    command = "reset",
                    enabled = !isLoading && !isCommandDispatchInProgress,
                ) { command ->
                    commandText = command
                    dispatchCommand(command)
                }
                QuickCommandButton(
                    label = "Diag OK",
                    command = "diagnostics:ok",
                    enabled = !isLoading && !isCommandDispatchInProgress,
                ) { command ->
                    commandText = command
                    dispatchCommand(command)
                }
            }
            Button(
                enabled = !isLoading && !isCommandDispatchInProgress && commandError == null,
                onClick = {
                    dispatchCommand(commandText)
                },
            ) {
                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Send native command")
                    }
                } else {
                    Text("Send native command")
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuickCommandButton(
    label: String,
    command: String,
    enabled: Boolean,
    onCommand: (String) -> Unit,
) {
    Button(
        modifier = Modifier.weight(1f),
        enabled = enabled,
        onClick = { onCommand(command) },
    ) {
        Text(label)
    }
}

@Composable
private fun OverviewCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
