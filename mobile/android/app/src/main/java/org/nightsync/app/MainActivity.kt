package org.nightsync.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val NightInk = Color(0xFF0B0E14)
private val NightSurface = Color(0xFF171D27)
private val NightGold = Color(0xFFD9B76E)
private val NightText = Color(0xFFF4F1E9)
private val NightMuted = Color(0xFFA2ADBC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nightsync", Context.MODE_PRIVATE)
        val savedTimes = (0..2).map { i ->
            prefs.getString("sync_time_$i", listOf("08:00", "14:00", "20:00")[i]) ?: "08:00"
        }
        setContent {
            NightSyncAuthApp(
                initialAddress = prefs.getString("server_address", "http://127.0.0.1:8787") ?: "http://127.0.0.1:8787",
                onSaveAddress = { prefs.edit().putString("server_address", it).apply() },
                initialSyncCount = prefs.getInt("sync_count", 1),
                initialSyncTimes = savedTimes,
                onSaveSchedule = { count, times ->
                    prefs.edit().putInt("sync_count", count).apply()
                    times.forEachIndexed { i, time -> prefs.edit().putString("sync_time_$i", time).apply() }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightSyncAuthApp(
    initialAddress: String,
    onSaveAddress: (String) -> Unit,
    initialSyncCount: Int,
    initialSyncTimes: List<String>,
    onSaveSchedule: (Int, List<String>) -> Unit
) {
    var address by remember { mutableStateOf(initialAddress) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf("") }
    var mustChange by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Sign in to your NightSync server") }
    var busy by remember { mutableStateOf(false) }
    var syncCount by remember { mutableIntStateOf(initialSyncCount.coerceIn(1, 3)) }
    var syncTimes by remember { mutableStateOf((0..2).map { i -> initialSyncTimes.getOrNull(i) ?: listOf("08:00", "14:00", "20:00")[i] }) }
    var editingTimeIndex by remember { mutableIntStateOf(-1) }
    var scheduleSaved by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun runRequest(path: String, method: String, payload: JSONObject?, authToken: String? = null, done: (Int, JSONObject?) -> Unit) {
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                try {
                    val c = URL(address.trim().trimEnd('/') + path).openConnection() as HttpURLConnection
                    c.requestMethod = method
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.setRequestProperty("Content-Type", "application/json")
                    if (authToken != null) c.setRequestProperty("Authorization", "Bearer $authToken")
                    if (payload != null) {
                        c.doOutput = true
                        c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    }
                    val code = c.responseCode
                    val stream = if (code in 200..299) c.inputStream else c.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                    c.disconnect()
                    code to JSONObject(text)
                } catch (e: Exception) {
                    0 to JSONObject().put("error", e.message ?: "Connection failed")
                }
            }
            busy = false
            done(result.first, result.second)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = NightGold, onPrimary = Color(0xFF211A0D), secondary = Color(0xFFC3A56A),
        background = NightInk, onBackground = NightText, surface = NightSurface,
        onSurface = NightText, onSurfaceVariant = NightMuted, outline = Color(0xFF394556)
    )) {
        Scaffold(
            containerColor = NightInk,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("NIGHTSYNC", style = MaterialTheme.typography.labelMedium, color = NightGold, fontWeight = FontWeight.Bold)
                            Text("Your private cloud", style = MaterialTheme.typography.titleMedium, color = NightText)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NightInk)
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Private storage. Simple control.", color = NightMuted, style = MaterialTheme.typography.bodyMedium)
                if (token == null) {
                    PremiumCard {
                        Text("Welcome back", style = MaterialTheme.typography.headlineSmall, color = NightText, fontWeight = FontWeight.SemiBold)
                        Text("Sign in to your NightSync space.", color = NightMuted)
                        PremiumField(address, { address = it }, "Server address")
                        PremiumField(username, { username = it }, "User ID")
                        PremiumField(password, { password = it }, "Password", secret = true)
                        Button(
                            enabled = !busy,
                            onClick = {
                                val a = address.trim().trimEnd('/')
                                if (!(a.startsWith("https://") || a.startsWith("http://127.0.0.1") || a.startsWith("http://localhost"))) {
                                    status = "Use HTTPS except for local testing."
                                } else {
                                    onSaveAddress(a)
                                    runRequest("/api/v1/login", "POST", JSONObject().put("username", username).put("password", password)) { code, body ->
                                        if (code == 200 && body != null) {
                                            token = body.optString("token")
                                            role = body.optString("role")
                                            mustChange = body.optBoolean("must_change_password")
                                            status = "Signed in as $username"
                                        } else status = body?.optString("error") ?: "Sign-in failed"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (busy) "Signing in…" else "Sign in") }
                        Text("First time? Set up the first administrator on the Windows host.", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (mustChange) {
                    PremiumCard {
                        Text("Set a new password", style = MaterialTheme.typography.headlineSmall, color = NightText)
                        Text("Your temporary password must be changed before continuing.", color = NightMuted)
                        PremiumField(password, { password = it }, "Temporary password", secret = true)
                        PremiumField(newPassword, { newPassword = it }, "New password (12+ characters)", secret = true)
                        Button(
                            enabled = !busy,
                            onClick = {
                                runRequest("/api/v1/change-password", "POST",
                                    JSONObject().put("current_password", password).put("new_password", newPassword), token) { code, body ->
                                    if (code == 200) {
                                        token = null; password = ""; newPassword = ""; mustChange = false
                                        status = "Password changed. Please sign in again."
                                    } else status = body?.optString("error") ?: "Password change failed"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        ) { Text("Update password") }
                    }
                } else {
                    PremiumCard {
                        Text("Welcome, $username", style = MaterialTheme.typography.headlineSmall, color = NightText, fontWeight = FontWeight.SemiBold)
                        Text("Signed in · $role", color = NightMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    PremiumCard {
                        Text("Sync schedule", style = MaterialTheme.typography.titleLarge, color = NightText, fontWeight = FontWeight.SemiBold)
                        Text("Choose how often this phone should sync when automatic transfers are available.", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Text("SYNC TIMES PER DAY", color = NightGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            (1..3).forEach { count ->
                                FilterChip(
                                    selected = syncCount == count,
                                    onClick = { syncCount = count; scheduleSaved = false },
                                    label = { Text(count.toString() + if (count == 1) " time" else " times") },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NightGold,
                                        selectedLabelColor = Color(0xFF211A0D),
                                        labelColor = NightText
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        (0 until syncCount).forEach { i ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Sync " + (i + 1), color = NightText, fontWeight = FontWeight.Medium)
                                    Text("Every day", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(
                                    onClick = { editingTimeIndex = i },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NightGold)
                                ) { Text(formatClock(syncTimes[i])) }
                            }
                        }
                        Button(
                            onClick = {
                                onSaveSchedule(syncCount, syncTimes)
                                scheduleSaved = true
                                status = "Your sync schedule is saved on this phone."
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Save schedule") }
                        Text(
                            if (scheduleSaved) "Schedule saved on this phone." else "You have unsaved changes.",
                            color = if (scheduleSaved) NightGold else NightMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Scheduled file transfers are not active yet. This release saves your preferred times; automatic syncing will be enabled when the server transfer feature is ready.",
                            color = NightMuted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(
                        onClick = { token = null; password = ""; status = "Signed out" },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NightText)
                    ) { Text("Sign out") }
                }
                Text(status, color = NightGold, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (editingTimeIndex in 0..2) {
        val parts = syncTimes[editingTimeIndex].split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { editingTimeIndex = -1 },
            title = { Text("Choose sync time", color = NightText) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val updated = syncTimes.toMutableList()
                    updated[editingTimeIndex] = "%02d:%02d".format(timeState.hour, timeState.minute)
                    syncTimes = updated
                    scheduleSaved = false
                    editingTimeIndex = -1
                }) { Text("Done", color = NightGold) }
            },
            dismissButton = { TextButton(onClick = { editingTimeIndex = -1 }) { Text("Cancel", color = NightMuted) } },
            containerColor = NightSurface
        )
    }
}

@Composable
private fun PremiumCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NightSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun PremiumField(value: String, onValueChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NightGold, focusedLabelColor = NightGold,
            unfocusedBorderColor = Color(0xFF394556), focusedTextColor = NightText,
            unfocusedTextColor = NightText, cursorColor = NightGold
        )
    )
}

private fun formatClock(value: String): String {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val suffix = if (hour < 12) "AM" else "PM"
    val displayHour = when (hour % 12) { 0 -> 12; else -> hour % 12 }
    return "%02d:%02d %s".format(displayHour, minute, suffix)
}