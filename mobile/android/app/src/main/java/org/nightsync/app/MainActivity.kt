package org.nightsync.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.File
import java.text.DateFormat
import java.util.Date

private val NightInk = Color(0xFF070B14)
private val NightSurface = Color(0xFF101827)
private val NightRaised = Color(0xFF152238)
private val NightCyan = Color(0xFF69E5FF)
private val NightViolet = Color(0xFFA48CFF)
private val NightText = Color(0xFFECF7FF)
private val NightMuted = Color(0xFF91A7C1)
private val NightDanger = Color(0xFFFF9EB5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nightsync", Context.MODE_PRIVATE)
        val savedTimes = (0..2).map { i ->
            prefs.getString("sync_time_$i", listOf("08:00", "14:00", "20:00")[i]) ?: "08:00"
        }
        setContent {
            NightSyncAuthApp(
                initialAddress = prefs.getString("server_address", "http://10.0.2.2:8787") ?: "http://10.0.2.2:8787",
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

private data class CloudItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modified: String
) {
    val kind: String get() = if (isDirectory) "Folder" else "File"
    val sizeLabel: String get() = if (isDirectory) "—" else formatBytes(sizeBytes)
}

private data class ApiResult(val code: Int, val body: JSONObject)

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private fun requestJson(
    address: String,
    path: String,
    method: String,
    payload: JSONObject? = null,
    token: String? = null
): ApiResult {
    val connection = URL(address.trim().trimEnd('/') + path).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        if (payload != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        return ApiResult(code, JSONObject(text))
    } finally {
        connection.disconnect()
    }
}

private fun formatBytes(value: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var amount = value.coerceAtLeast(0).toDouble()
    var index = 0
    while (amount >= 1024 && index < units.lastIndex) {
        amount /= 1024
        index++
    }
    return "%.1f %s".format(amount, units[index])
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(initialAddress) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf("") }
    var mustChange by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connect to your NightSync server") }
    var busy by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("files") }
    var syncCount by remember { mutableIntStateOf(initialSyncCount.coerceIn(1, 3)) }
    var syncTimes by remember {
        mutableStateOf((0..2).map { i -> initialSyncTimes.getOrNull(i) ?: listOf("08:00", "14:00", "20:00")[i] })
    }
    var editingTimeIndex by remember { mutableIntStateOf(-1) }
    var scheduleSaved by remember { mutableStateOf(true) }

    var currentFolder by remember { mutableStateOf("") }
    var cloudItems by remember { mutableStateOf<List<CloudItem>>(emptyList()) }
    var filesLoading by remember { mutableStateOf(false) }
    var filesMessage by remember { mutableStateOf("Your private files will appear here.") }
    var newFolderName by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<CloudItem?>(null) }
    var fileToDownload by remember { mutableStateOf<CloudItem?>(null) }

    fun refreshFiles() {
        val currentToken = token ?: return
        scope.launch {
            filesLoading = true
            val result = withContext(Dispatchers.IO) {
                try {
                    val api = requestJson(
                        address, "/api/v1/files?path=" + encode(currentFolder), "GET", token = currentToken
                    )
                    if (api.code !in 200..299) throw IllegalStateException(api.body.optString("error", "Could not load files"))
                    val array = api.body.optJSONArray("items") ?: org.json.JSONArray()
                    val parsed = buildList {
                        for (index in 0 until array.length()) {
                            val item = array.getJSONObject(index)
                            add(
                                CloudItem(
                                    name = item.optString("name"),
                                    path = item.optString("path"),
                                    isDirectory = item.optBoolean("is_dir"),
                                    sizeBytes = item.optLong("size_bytes"),
                                    modified = item.optString("modified")
                                )
                            )
                        }
                    }.sortedWith(compareByDescending<CloudItem> { it.isDirectory }.thenBy { it.name.lowercase() })
                    parsed to "Loaded ${parsed.size} item(s)."
                } catch (e: Exception) {
                    null to (e.message ?: "Could not connect to your server.")
                }
            }
            filesLoading = false
            if (result.first != null) {
                cloudItems = result.first!!
                filesMessage = result.second
            } else {
                filesMessage = result.second
            }
        }
    }

    fun createFolder() {
        val cleanName = newFolderName.trim()
        if (cleanName.isBlank() || cleanName == "." || cleanName == ".." || cleanName.contains("/") || cleanName.contains("\\")) {
            filesMessage = "Enter a valid folder name."
            return
        }
        val path = if (currentFolder.isBlank()) cleanName else "$currentFolder/$cleanName"
        val currentToken = token ?: return
        scope.launch {
            filesLoading = true
            val message = withContext(Dispatchers.IO) {
                try {
                    val response = requestJson(
                        address, "/api/v1/folders", "POST",
                        JSONObject().put("path", path), currentToken
                    )
                    when (response.code) {
                        201 -> "Folder created."
                        409 -> "A file or folder with that name already exists."
                        else -> response.body.optString("error", "Could not create folder.")
                    }
                } catch (e: Exception) {
                    e.message ?: "Could not create folder."
                }
            }
            filesLoading = false
            filesMessage = message
            if (message == "Folder created.") {
                newFolderName = ""
                refreshFiles()
            }
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val currentToken = token
            if (currentToken != null) {
                scope.launch {
                    filesLoading = true
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                                ?: "upload-${System.currentTimeMillis()}"
                            val relativePath = if (currentFolder.isBlank()) displayName else "$currentFolder/$displayName"
                            val connection = URL(address.trim().trimEnd('/') + "/api/v1/files?path=" + encode(relativePath))
                                .openConnection() as HttpURLConnection
                            try {
                                connection.requestMethod = "PUT"
                                connection.connectTimeout = 10000
                                connection.readTimeout = 60000
                                connection.setRequestProperty("Authorization", "Bearer $currentToken")
                                connection.setRequestProperty("Content-Type", context.contentResolver.getType(uri) ?: "application/octet-stream")
                                connection.doOutput = true
                                val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Could not open selected file.")
                                input.use { source -> connection.outputStream.use { target -> source.copyTo(target) } }
                                val code = connection.responseCode
                                if (code == 201) "Uploaded $displayName."
                                else {
                                    val stream = connection.errorStream
                                    stream?.bufferedReader()?.use { it.readText() }?.let { JSONObject(it).optString("error") }
                                        ?.takeIf { it.isNotBlank() } ?: "Upload failed (HTTP $code)."
                                }
                            } finally {
                                connection.disconnect()
                            }
                        } catch (e: Exception) {
                            "Upload failed: ${e.message ?: "connection error"}"
                        }
                    }
                    filesLoading = false
                    filesMessage = result
                    refreshFiles()
                }
            }
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val selected = fileToDownload
        fileToDownload = null
        if (uri != null && selected != null) {
            val currentToken = token
            if (currentToken != null) {
                scope.launch {
                    filesLoading = true
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val connection = URL(address.trim().trimEnd('/') + "/api/v1/file?path=" + encode(selected.path))
                                .openConnection() as HttpURLConnection
                            try {
                                connection.requestMethod = "GET"
                                connection.connectTimeout = 10000
                                connection.readTimeout = 60000
                                connection.setRequestProperty("Authorization", "Bearer $currentToken")
                                if (connection.responseCode !in 200..299) throw IllegalStateException("Download failed (HTTP ${connection.responseCode}).")
                                val output = context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Could not open destination.")
                                connection.inputStream.use { source -> output.use { target -> source.copyTo(target) } }
                                "Downloaded ${selected.name}."
                            } finally {
                                connection.disconnect()
                            }
                        } catch (e: Exception) {
                            "Download failed: ${e.message ?: "connection error"}"
                        }
                    }
                    filesLoading = false
                    filesMessage = result
                }
            }
        }
    }

    fun deleteSelected() {
        val selected = itemToDelete ?: return
        itemToDelete = null
        val currentToken = token ?: return
        scope.launch {
            filesLoading = true
            val result = withContext(Dispatchers.IO) {
                try {
                    val path = encode(selected.path)
                    val endpoint = if (selected.isDirectory) "/api/v1/folders?path=$path" else "/api/v1/file?path=$path"
                    val response = requestJson(address, endpoint, "DELETE", token = currentToken)
                    when (response.code) {
                        200 -> if (selected.isDirectory) "Folder deleted." else "File deleted."
                        409 -> "Folder is not empty. Remove its contents first."
                        else -> response.body.optString("error", "Delete failed.")
                    }
                } catch (e: Exception) {
                    e.message ?: "Delete failed."
                }
            }
            filesLoading = false
            filesMessage = result
            refreshFiles()
        }
    }

    val colors = darkColorScheme(
        primary = NightCyan, onPrimary = Color(0xFF06111C), secondary = NightViolet,
        background = NightInk, onBackground = NightText, surface = NightSurface,
        onSurface = NightText, surfaceVariant = NightRaised, onSurfaceVariant = NightMuted,
        outline = Color(0xFF29435F), error = NightDanger
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = NightInk,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("NIGHTSYNC", style = MaterialTheme.typography.labelMedium, color = NightCyan, fontWeight = FontWeight.Bold)
                            Text(if (token == null) "Your private cloud" else "Welcome, $username", style = MaterialTheme.typography.titleMedium, color = NightText)
                        }
                    },
                    actions = {
                        if (token != null) {
                            TextButton(onClick = { token = null; password = ""; activeTab = "files"; status = "Signed out." }) {
                                Text("Sign out", color = NightMuted)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NightInk)
                )
            },
            bottomBar = {
                if (token != null && !mustChange) {
                    NavigationBar(containerColor = NightSurface) {
                        NavigationBarItem(
                            selected = activeTab == "files",
                            onClick = { activeTab = "files"; refreshFiles() },
                            icon = { Text("▤") },
                            label = { Text("My files") }
                        )
                        NavigationBarItem(
                            selected = activeTab == "schedule",
                            onClick = { activeTab = "schedule" },
                            icon = { Text("◷") },
                            label = { Text("Schedule") }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (token == null) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        Text("Private storage. Simple control.", color = NightMuted, style = MaterialTheme.typography.bodyMedium)
                        PremiumCard {
                            Text("Welcome back", style = MaterialTheme.typography.headlineSmall, color = NightText, fontWeight = FontWeight.SemiBold)
                            Text("Connect to your NightSync server.", color = NightMuted)
                            PremiumField(address, { address = it }, "Server address")
                            PremiumField(username, { username = it }, "User ID")
                            PremiumField(password, { password = it }, "Password", secret = true)
                            Button(
                                enabled = !busy,
                                onClick = {
                                    val a = address.trim().trimEnd('/')
                                    if (!(a.startsWith("https://") || a.startsWith("http://127.0.0.1") ||
                                                a.startsWith("http://localhost") || a.startsWith("http://10.0.2.2"))) {
                                        status = "Use HTTPS except for local emulator testing."
                                    } else {
                                        onSaveAddress(a)
                                        scope.launch {
                                            busy = true
                                            val result = withContext(Dispatchers.IO) {
                                                try {
                                                    requestJson(a, "/api/v1/login", "POST",
                                                        JSONObject().put("username", username).put("password", password))
                                                } catch (e: Exception) {
                                                    ApiResult(0, JSONObject().put("error", e.message ?: "Connection failed"))
                                                }
                                            }
                                            busy = false
                                            if (result.code == 200) {
                                                token = result.body.optString("token")
                                                role = result.body.optString("role")
                                                mustChange = result.body.optBoolean("must_change_password")
                                                status = "Connected to your private cloud."
                                                if (!mustChange) refreshFiles()
                                            } else {
                                                status = result.body.optString("error", "Sign-in failed.")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(13.dp)
                            ) { Text(if (busy) "Connecting…" else "Connect") }
                            Text("Use a secure HTTPS address for remote connections. The local emulator address is for development testing only.",
                                color = NightMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(status, color = NightCyan, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (mustChange) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PremiumCard {
                            Text("Set a new password", style = MaterialTheme.typography.headlineSmall, color = NightText)
                            Text("Your temporary password must be changed before continuing.", color = NightMuted)
                            PremiumField(password, { password = it }, "Temporary password", secret = true)
                            PremiumField(newPassword, { newPassword = it }, "New password (12+ characters)", secret = true)
                            Button(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        val result = withContext(Dispatchers.IO) {
                                            try {
                                                requestJson(address, "/api/v1/change-password", "POST",
                                                    JSONObject().put("current_password", password).put("new_password", newPassword), token)
                                            } catch (e: Exception) {
                                                ApiResult(0, JSONObject().put("error", e.message ?: "Connection failed"))
                                            }
                                        }
                                        busy = false
                                        if (result.code == 200) {
                                            token = null; password = ""; newPassword = ""; mustChange = false
                                            status = "Password changed. Sign in again."
                                        } else status = result.body.optString("error", "Password change failed.")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                            ) { Text("Update password") }
                        }
                    }
                } else if (activeTab == "files") {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("My files", style = MaterialTheme.typography.headlineSmall, color = NightText, fontWeight = FontWeight.SemiBold)
                                Text("Your private space", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Surface(shape = RoundedCornerShape(18.dp), color = NightRaised) {
                                Text("PRIVATE", color = NightCyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                            }
                        }
                        Surface(color = NightRaised, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    enabled = currentFolder.isNotBlank(),
                                    onClick = {
                                        val parts = currentFolder.split("/").filter { it.isNotBlank() }
                                        currentFolder = if (parts.size <= 1) "" else parts.dropLast(1).joinToString("/")
                                        refreshFiles()
                                    }
                                ) { Text("‹ Back", color = NightCyan) }
                                Text(
                                    if (currentFolder.isBlank()) "My files" else currentFolder.replace("/", "  /  "),
                                    color = NightText, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { refreshFiles() }, enabled = !filesLoading) { Text("↻", color = NightCyan) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }, enabled = !filesLoading, modifier = Modifier.weight(1f)) { Text("↑ Upload") }
                            Button(onClick = { fileToDownload?.let { downloadLauncher.launch(it.name) } }, enabled = false, modifier = Modifier.weight(1f)) { Text("↓ Download") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newFolderName, onValueChange = { newFolderName = it },
                                modifier = Modifier.weight(1f), singleLine = true,
                                label = { Text("New folder name") }, shape = RoundedCornerShape(12.dp)
                            )
                            Button(onClick = { createFolder() }, enabled = !filesLoading && newFolderName.isNotBlank()) { Text("Create") }
                        }
                        if (filesLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = NightCyan)
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            if (cloudItems.isEmpty() && !filesLoading) {
                                item {
                                    PremiumCard {
                                        Text(if (currentFolder.isBlank()) "Nothing here yet" else "This folder is empty",
                                            color = NightText, fontWeight = FontWeight.SemiBold)
                                        Text("Upload a file or create a folder to get started.", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            items(cloudItems, key = { it.path }) { item ->
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = item.isDirectory) {
                                        currentFolder = item.path
                                        refreshFiles()
                                    }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (item.isDirectory) "▰" else "▧", color = if (item.isDirectory) NightCyan else NightViolet,
                                        style = MaterialTheme.typography.titleLarge)
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(item.name, color = NightText, fontWeight = FontWeight.Medium, maxLines = 1)
                                        Text(if (item.isDirectory) "Folder" else item.sizeLabel, color = NightMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (!item.isDirectory) {
                                        TextButton(onClick = {
                                            fileToDownload = item
                                            downloadLauncher.launch(item.name)
                                        }) { Text("Save", color = NightCyan) }
                                    }
                                    TextButton(onClick = { itemToDelete = item }) { Text("⋯", color = NightMuted) }
                                }
                                HorizontalDivider(color = Color(0xFF1D2C40))
                            }
                        }
                        Text(filesMessage, color = NightMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumCard {
                            Text("Sync schedule", style = MaterialTheme.typography.headlineSmall, color = NightText, fontWeight = FontWeight.SemiBold)
                            Text("Choose when this phone should sync with your server.", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                            Text("TIMES PER DAY", color = NightCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                (1..3).forEach { count ->
                                    FilterChip(
                                        selected = syncCount == count,
                                        onClick = { syncCount = count; scheduleSaved = false },
                                        label = { Text(count.toString() + if (count == 1) " time" else " times") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            (0 until syncCount).forEach { index ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Sync ${index + 1}", color = NightText)
                                    OutlinedButton(onClick = { editingTimeIndex = index }) { Text(formatClock(syncTimes[index]), color = NightCyan) }
                                }
                            }
                            Button(
                                onClick = {
                                    onSaveSchedule(syncCount, syncTimes)
                                    scheduleSaved = true
                                    status = "Your preferred sync times are saved on this phone."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save schedule") }
                            Text(if (scheduleSaved) "Schedule saved on this phone." else "You have unsaved changes.",
                                color = NightMuted, style = MaterialTheme.typography.bodySmall)
                            Text("Scheduled transfers are not active until secure remote connectivity and the sync engine are enabled.",
                                color = NightMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
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
                }) { Text("Done", color = NightCyan) }
            },
            dismissButton = { TextButton(onClick = { editingTimeIndex = -1 }) { Text("Cancel", color = NightMuted) } },
            containerColor = NightSurface
        )
    }

    if (itemToDelete != null) {
        val selected = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(if (selected.isDirectory) "Delete folder?" else "Delete file?") },
            text = { Text(if (selected.isDirectory) "Only empty folders can be deleted." else "This permanently deletes the file from your private storage.") },
            confirmButton = {
                TextButton(onClick = { deleteSelected() }) { Text("Delete", color = NightDanger) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel", color = NightMuted) }
            },
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
            focusedBorderColor = NightCyan, focusedLabelColor = NightCyan,
            unfocusedBorderColor = Color(0xFF29435F), focusedTextColor = NightText,
            unfocusedTextColor = NightText, cursorColor = NightCyan
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
