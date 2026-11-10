package org.nightsync.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nightsync", Context.MODE_PRIVATE)
        setContent {
            NightSyncScreen(
                initialAddress = prefs.getString("server_address", "") ?: "",
                onSave = { address -> prefs.edit().putString("server_address", address).apply() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightSyncScreen(initialAddress: String, onSave: (String) -> Unit) {
    var address by remember { mutableStateOf(initialAddress) }
    var status by remember { mutableStateOf("Not connected") }
    var details by remember { mutableStateOf("Add your NightSync server's HTTPS address.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("NightSync") }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Your private cloud. Your storage. Your control.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Connect to a server", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Server address (HTTPS)") },
                    placeholder = { Text("https://cloud.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        val normalized = address.trim().trimEnd('/')
                        if (normalized.startsWith("https://") && normalized.length > 8) {
                            address = normalized
                            onSave(normalized)
                            status = "Saved"
                            details = "Server address saved on this device."
                        } else {
                            status = "Address required"
                            details = "Enter a valid HTTPS address."
                        }
                    }) { Text("Save") }

                    OutlinedButton(enabled = !busy, onClick = {
                        scope.launch {
                            val normalized = address.trim().trimEnd('/')
                            if (!normalized.startsWith("https://")) {
                                status = "Address required"
                                details = "Enter a valid HTTPS address before testing."
                            } else {
                                busy = true
                                status = "Testing…"
                                details = "Checking the /health endpoint."
                                val result = withContext(Dispatchers.IO) { checkServer(normalized) }
                                status = result.first
                                details = result.second
                                busy = false
                            }
                        }
                    }) { Text(if (busy) "Testing…" else "Connection test") }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("CONNECTION STATUS", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(status, style = MaterialTheme.typography.titleLarge)
                        Text(details, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("FILES", style = MaterialTheme.typography.labelLarge)
                ListItem(headlineContent = { Text("File browsing") },
                    supportingContent = { Text("Available after secure server API integration.") })
                Text("PHOTOS", style = MaterialTheme.typography.labelLarge)
                ListItem(headlineContent = { Text("Camera backup") },
                    supportingContent = { Text("Automatic backup will be added in a later milestone.") })
                Text("NightSync 0.2.0 · Development build",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


private fun checkServer(baseAddress: String): Pair<String, String> {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL("$baseAddress/health").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }
        val code = connection.responseCode
        if (code in 200..299) {
            "Reachable" to "Server returned HTTP $code. Full NightSync API compatibility will be checked in a later milestone."
        } else {
            "Server responded" to "HTTP $code — address reachable, health check unsuccessful."
        }
    } catch (e: Exception) {
        "Connection failed" to "Could not reach the server. Check the address, network and HTTPS certificate."
    } finally {
        connection?.disconnect()
    }
}
