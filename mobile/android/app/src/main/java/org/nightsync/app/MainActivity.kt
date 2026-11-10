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
    var details by remember { mutableStateOf("Add an HTTPS server address to test a real endpoint.") }
    var busy by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isAdminPreview by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("NightSync") }) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Your private cloud. Your storage. Your control.",
                    style = MaterialTheme.typography.bodyMedium)
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    listOf("Overview", "Connect", "Storage", "Files", "Photos", "Logs").forEachIndexed { i, label ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(label) })
                    }
                }
                when (selectedTab) {
                    0 -> {
                        Text("Server overview", style = MaterialTheme.typography.headlineSmall)
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("DEMO MONITORING DATA", style = MaterialTheme.typography.labelLarge)
                                Text("Illustrative values only — not readings from a connected server.")
                            }
                        }
                        MetricCard("Server status", "Online (demo)")
                        MetricCard("CPU usage", "18% (demo)")
                        MetricCard("Memory usage", "62% (demo)")
                        MetricCard("Storage used", "1.2 TB / 4 TB (demo)")
                        MetricCard("Connected devices", "3 (demo)")
                        MetricCard("Uptime", "12d 04h (demo)")
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Role preview:")
                            Spacer(Modifier.width(8.dp))
                            FilterChip(selected = !isAdminPreview, onClick = { isAdminPreview = false }, label = { Text("Member") })
                            Spacer(Modifier.width(6.dp))
                            FilterChip(selected = isAdminPreview, onClick = { isAdminPreview = true }, label = { Text("Admin") })
                        }
                        if (isAdminPreview) {
                            Button(onClick = {
                                status = "Admin demo action"
                                details = "Diagnostics preview completed. No server setting was changed."
                            }) { Text("Run diagnostics (demo)") }
                            Text("Admin controls are previews only. Real permissions must be enforced by the server.")
                        } else {
                            Text("Member view is read-only. Management controls are hidden.")
                        }
                        OutlinedButton(onClick = {
                            status = "Demo dashboard refreshed"
                            details = "These sample metrics are illustrative, not live server readings."
                        }) { Text("Refresh demo dashboard") }
                    }
                    1 -> {
                        Text("Connect to server", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            value = address, onValueChange = { address = it },
                            label = { Text("Server address (HTTPS)") },
                            placeholder = { Text("https://cloud.example.com") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val normalized = address.trim().trimEnd('/')
                                if (normalized.startsWith("https://") && normalized.length > 8) {
                                    address = normalized
                                    onSave(normalized)
                                    status = "Saved"
                                    details = "Server address saved on this device."
                                } else {
                                    status = "HTTPS address required"
                                    details = "Enter a valid HTTPS address."
                                }
                            }) { Text("Save") }
                            OutlinedButton(enabled = !busy, onClick = {
                                scope.launch {
                                    val normalized = address.trim().trimEnd('/')
                                    if (!normalized.startsWith("https://")) {
                                        status = "HTTPS address required"
                                        details = "Enter a valid HTTPS address before testing."
                                    } else {
                                        busy = true
                                        status = "Testing…"
                                        details = "Checking /health."
                                        val result = withContext(Dispatchers.IO) { checkServer(normalized) }
                                        status = result.first
                                        details = result.second
                                        busy = false
                                    }
                                }
                            }) { Text(if (busy) "Testing…" else "Test connection") }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("CONNECTION STATUS", style = MaterialTheme.typography.labelLarge)
                                Text(status, style = MaterialTheme.typography.titleLarge)
                                Text(details)
                            }
                        }
                    }
                    2 -> PlaceholderPage("Storage", "Drive capacity, volume health, pools and backup destinations will appear when connected to a real server.")
                    3 -> PlaceholderPage("Files", "File browsing, upload, download, sync and recycle bin are planned for upcoming milestones.")
                    4 -> PlaceholderPage("Photos", "Photo gallery and automatic camera backup are planned for upcoming milestones.")
                    else -> PlaceholderPage("Server logs", "Server logs will be visible to authorised administrators when the separate NightSync Server is implemented.")
                }
                Spacer(Modifier.height(4.dp))
                Text("NightSync 0.3.0 · Monitoring dashboard preview",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PlaceholderPage(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) { Text(description, Modifier.padding(16.dp)) }
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
        if (code in 200..299) "Reachable" to "Server returned HTTP $code. This confirms endpoint reachability only."
        else "Server responded" to "HTTP $code — endpoint reachable, health check unsuccessful."
    } catch (e: Exception) {
        "Connection failed" to "Could not reach server. Check address, network and HTTPS certificate."
    } finally {
        connection?.disconnect()
    }
}
