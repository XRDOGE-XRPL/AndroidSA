package com.xrdoge.xrpl.androidsa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        overview = withContext(Dispatchers.IO) {
            loadOverviewSafely { NativeBridge.overview() }
        }
        isLoading = false
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
                supportingText = { Text("Examples: ping, connect, disconnect, reset, transport:udp") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
            )
            Button(
                enabled = !isLoading,
                onClick = {
                    val currentOverview = overview
                    val commandToDispatch = commandText
                    scope.launch {
                        isLoading = true
                        overview = withContext(Dispatchers.IO) {
                            loadOverviewSafely {
                                NativeBridge.refresh(commandToDispatch).recover {
                                    currentOverview.copy(
                                        connectionState = "error",
                                        diagnostics = it.message ?: "Native command failed",
                                    )
                                }
                            }
                        }
                        isLoading = false
                    }
                },
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("Send native command")
                }
            }
        }
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
