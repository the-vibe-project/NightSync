package org.nightsync.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                initialAddress = prefs.getString("server_address", "") ?: "",
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
    var qrMessage by remember { mutableStateOf("") }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) {
            qrMessage = "QR scan cancelled."
        } else {
            try {
                val payload = JSONObject(raw)
                val scannedUrl = payload.optString("url").trim().trimEnd('/')
                val validType = payload.optString("type") == "nightsync-connect"
                val validVersion = payload.optInt("version") == 1
                val parsed = URL(scannedUrl)
                if (!validType || !validVersion || parsed.protocol != "https" || parsed.host.isBlank()) {
                    throw IllegalArgumentException("This is not a valid secure NightSync connection QR code.")
                }
                address = scannedUrl
                onSaveAddress(scannedUrl)
                qrMessage = "Secure server address added. Sign in with your NightSync account."
            } catch (e: Exception) {
                qrMessage = e.message ?: "Could not read this NightSync QR code."
            }
        }
    }
    var address by remember { mutableStateOf(initialAddress) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var isDemoMode by remember { mutableStateOf(false) }
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
    var backupPhotos by remember { mutableStateOf(true) }
    var backupVideos by remember { mutableStateOf(true) }
    var backupDocuments by remember { mutableStateOf(true) }
    var backupDownloads by remember { mutableStateOf(false) }
    var backupAudio by remember { mutableStateOf(false) }
    var backupNetwork by remember { mutableStateOf("Wi-Fi only") }
    var backupFrequency by remember { mutableStateOf("Daily") }
    var backupChargingOnly by remember { mutableStateOf(true) }
    var demoBackupCount by remember { mutableIntStateOf(0) }
    var backupMessage by remember { mutableStateOf("Your backup settings are ready to preview.") }

    var currentFolder by remember { mutableStateOf("") }
    var cloudItems by remember { mutableStateOf<List<CloudItem>>(emptyList()) }
    var filesLoading by remember { mutableStateOf(false) }
    var filesMessage by remember { mutableStateOf("Your private files will appear here.") }
    var newFolderName by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<CloudItem?>(null) }
    var fileToDownload by remember { mutableStateOf<CloudItem?>(null) }

    fun refreshFiles() {
        if (isDemoMode) {
            cloudItems = when (currentFolder) {
                "Documents" -> listOf(CloudItem("Meeting-notes.docx", "Documents/Meeting-notes.docx", false, 82944, "Today"), CloudItem("Research.pdf", "Documents/Research.pdf", false, 3145728, "Nov 10"))
                "Photos" -> listOf(CloudItem("Weekend.jpg", "Photos/Weekend.jpg", false, 4718592, "Yesterday"), CloudItem("Workspace.png", "Photos/Workspace.png", false, 2097152, "Nov 09"))
                "Projects" -> listOf(CloudItem("NightSync", "Projects/NightSync", true, 0, "Today"), CloudItem("readme.md", "Projects/readme.md", false, 8192, "Today"))
                "Projects/NightSync" -> listOf(CloudItem("app", "Projects/NightSync/app", true, 0, "Today"), CloudItem("README.md", "Projects/NightSync/README.md", false, 12288, "Today"))
                else -> listOf(CloudItem("Documents", "Documents", true, 0, "Today"), CloudItem("Photos", "Photos", true, 0, "Yesterday"), CloudItem("Projects", "Projects", true, 0, "Yesterday"), CloudItem("Welcome.pdf", "Welcome.pdf", false, 1842176, "Today"), CloudItem("budget-2026.xlsx", "budget-2026.xlsx", false, 245760, "Yesterday"), CloudItem("notes.txt", "notes.txt", false, 4096, "Nov 12"))
            }
            filesMessage = "Demo workspace · sample data only. No server is connected."
            return
        }
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
        if (isDemoMode) { filesMessage = "Folder creation is disabled in the demo."; return }
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
                                connection.setChunkedStreamingMode(64 * 1024)
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
        if (isDemoMode) { itemToDelete = null; filesMessage = "File actions are disabled in the demo."; return }
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = NightCyan, modifier = Modifier.size(19.dp))
                                Text("NIGHTSYNC", style = MaterialTheme.typography.labelMedium, color = NightCyan, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                            }
                            Text(if (token == null) "Your private cloud" else "Welcome, $username", style = MaterialTheme.typography.titleMedium, color = NightText)
                            Text("BY THE VIBE PROJECT", style = MaterialTheme.typography.labelSmall, color = NightMuted)
                        }
                    },
                    actions = {
                        if (token != null) {
                            if (isDemoMode) Surface(color = NightRaised, shape = RoundedCornerShape(50)) { Text("DEMO", color = NightCyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
                            TextButton(onClick = {
                                val sessionToken = token
                                val wasDemo = isDemoMode
                                isDemoMode = false
                                token = null
                                password = ""
                                activeTab = "files"
                                currentFolder = ""
                                cloudItems = emptyList()
                                status = "Signed out."
                                if (sessionToken != null && !wasDemo) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            try { requestJson(address, "/api/v1/logout", "POST", token = sessionToken) } catch (_: Exception) { }
                                        }
                                    }
                                }
                            }) {
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
                            selected = activeTab == "overview",
                            onClick = { activeTab = "overview" },
                            icon = { Icon(Icons.Outlined.CloudQueue, contentDescription = null) },
                            label = { Text("Overview") }
                        )
                        NavigationBarItem(
                            selected = activeTab == "files",
                            onClick = { activeTab = "files"; refreshFiles() },
                            icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                            label = { Text("Files") }
                        )
                        NavigationBarItem(
                            selected = activeTab == "backup",
                            onClick = { activeTab = "backup" },
                            icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            label = { Text("Backup") }
                        )
                        NavigationBarItem(
                            selected = activeTab == "schedule",
                            onClick = { activeTab = "schedule" },
                            icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
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
                            Text("YOUR FILES, YOUR SPACE", color = NightCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Your cloud.\nYour control.", style = MaterialTheme.typography.headlineLarge, color = NightText, fontWeight = FontWeight.SemiBold)
                            Text("A private space for everything that matters.", color = NightMuted, style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider(color = Color(0xFF24364B))
                            Text("Connect to your server", style = MaterialTheme.typography.titleMedium, color = NightText, fontWeight = FontWeight.SemiBold)
                            PremiumField(address, { address = it }, "Server address")
                            OutlinedButton(
                                onClick = {
                                    qrScanner.launch(
                                        ScanOptions()
                                            .setPrompt("Scan the QR code shown in your NightSync Windows dashboard")
                                            .setBeepEnabled(false)
                                            .setOrientationLocked(false)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Scan server QR code") }
                            if (qrMessage.isNotBlank()) {
                                Text(qrMessage, color = NightCyan, style = MaterialTheme.typography.bodySmall)
                            }
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
                            Text("Secure connections use HTTPS. Local emulator addresses are for development only.", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            isDemoMode = true
                            token = "nightsync-demo-session"
                            username = "Guest"
                            mustChange = false
                            currentFolder = ""
                            activeTab = "overview"
                            status = "Exploring the sample workspace. No account or server required."
                            refreshFiles()
                        }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NightCyan)) {
                            Text("Explore demo workspace")
                        }
                        Text("Try NightSync with sample files. Demo mode never connects to a server or changes real files.", color = NightMuted, style = MaterialTheme.typography.bodySmall)
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
                } else if (activeTab == "overview") {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (isDemoMode) "Your server at a glance" else "Your workspace", color = NightText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (isDemoMode) {
                            PremiumCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(50), color = Color(0xFF123C3A)) {
                                        Text("ONLINE · DEMO", color = Color(0xFF7DE8C2), modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text("SIMULATED", color = NightViolet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                Text("NightSync Home", color = NightText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("Demo server · 192.168.1.20 · Version 0.1.0-preview", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider(color = Color(0xFF24364B))
                                Text("Server status", color = NightMuted, style = MaterialTheme.typography.labelSmall)
                                Text("Healthy · Last checked just now · 18 ms response", color = NightText)
                                Text("Uptime 12 days, 4 hours · 2 devices connected", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            PremiumCard {
                                Text("STORAGE OVERVIEW", color = NightCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("1.2 TB", color = NightText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                                    Text(" used of 2 TB", color = NightMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 5.dp))
                                }
                                LinearProgressIndicator(progress = { 0.60f }, modifier = Modifier.fillMaxWidth(), color = NightCyan, trackColor = NightRaised)
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("60% used", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                                    Text("800 GB free", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            PremiumCard {
                                Text("BACKUP SNAPSHOT", color = NightCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(if (demoBackupCount == 0) "No demo backup run yet" else "Demo backup #$demoBackupCount completed", color = NightText, style = MaterialTheme.typography.titleMedium)
                                Text(if (demoBackupCount == 0) "Explore backup settings and run a safe simulation." else "Last simulated run: just now · 248 files · 1.8 GB", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { activeTab = "backup" }, modifier = Modifier.fillMaxWidth()) { Text("Configure backup") }
                            }
                            PremiumCard {
                                Text("QUICK ACCESS", color = NightCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { activeTab = "files"; currentFolder = ""; refreshFiles() }, modifier = Modifier.fillMaxWidth()) { Text("Browse sample files") }
                                OutlinedButton(onClick = { activeTab = "schedule" }, modifier = Modifier.fillMaxWidth()) { Text("View sync schedule") }
                            }
                        } else {
                            PremiumCard {
                                Text("Server status is available when connected to a NightSync server.", color = NightText)
                                Text("This prototype currently exposes file browsing and local schedule preferences. Live health metrics and backup jobs are not yet implemented in the Android client.", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (activeTab == "backup") {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Backup settings", color = NightText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                Text("Choose what NightSync should protect", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Surface(color = NightRaised, shape = RoundedCornerShape(50)) {
                                Text(if (isDemoMode) "PREVIEW" else "PLANNED", color = NightViolet, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (isDemoMode) {
                            PremiumCard {
                                Text("WHAT TO BACK UP", color = NightCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("Choose the content categories", color = NightText, style = MaterialTheme.typography.titleMedium)
                                CheckboxRow("Photos", "Camera and gallery images", backupPhotos) { backupPhotos = it }
                                CheckboxRow("Videos", "Recorded and downloaded videos", backupVideos) { backupVideos = it }
                                CheckboxRow("Documents", "PDFs, office files and text", backupDocuments) { backupDocuments = it }
                                CheckboxRow("Downloads", "Files saved in Downloads", backupDownloads) { backupDownloads = it }
                                CheckboxRow("Audio", "Music, recordings and audio files", backupAudio) { backupAudio = it }
                            }
                            PremiumCard {
                                Text("BACKUP NETWORK", color = NightCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("When may backups use mobile data?", color = NightText, style = MaterialTheme.typography.titleMedium)
                                listOf("Wi-Fi only", "Wi-Fi + mobile data", "Mobile data only").forEach { option ->
                                    Row(Modifier.fillMaxWidth().clickable { backupNetwork = option }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = backupNetwork == option, onClick = { backupNetwork = option }, colors = RadioButtonDefaults.colors(selectedColor = NightCyan))
                                        Column(Modifier.padding(start = 8.dp)) {
                                            Text(option, color = NightText)
                                            Text(when(option) {
                                                "Wi-Fi only" -> "Recommended · avoids cellular usage"
                                                "Wi-Fi + mobile data" -> "Use cellular when Wi-Fi is unavailable"
                                                else -> "Back up over cellular networks"
                                            }, color = NightMuted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color(0xFF24364B))
                                CheckboxRow("Only while charging", "Pause backup while running on battery", backupChargingOnly) { backupChargingOnly = it }
                            }
                            PremiumCard {
                                Text("BACKUP FREQUENCY", color = NightCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                listOf("Daily", "Weekly", "Manual").forEach { option ->
                                    Row(Modifier.fillMaxWidth().clickable { backupFrequency = option }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = backupFrequency == option, onClick = { backupFrequency = option }, colors = RadioButtonDefaults.colors(selectedColor = NightCyan))
                                        Text(option, color = NightText, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                                Text("Estimated selection: 2.4 GB · Destination: NightSync Home · Retention: 30 days", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = {
                                val selected = listOf(backupPhotos, backupVideos, backupDocuments, backupDownloads, backupAudio).count { it }
                                if (selected > 0) {
                                    demoBackupCount += 1
                                    backupMessage = "Simulation complete · $selected categories · $backupNetwork · $backupFrequency. No files were read or transferred."
                                } else backupMessage = "Choose at least one category before previewing a backup."
                            }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Simulate backup") }
                            Text(backupMessage, color = NightCyan, style = MaterialTheme.typography.bodySmall)
                            Text("DEMO ONLY: these controls are illustrative. No device content is accessed, no network is used, and no backup is created.", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                        } else {
                            PremiumCard {
                                Text("Backup is not available yet", color = NightText, style = MaterialTheme.typography.titleMedium)
                                Text("The current NightSync repository does not yet implement backup or restore jobs. These controls are shown in demo mode as a product preview only; this signed-in screen does not start a simulated or real backup.", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (activeTab == "files") {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (isDemoMode) "Explore your cloud" else "My files", style = MaterialTheme.typography.headlineSmall, color = NightText, fontWeight = FontWeight.SemiBold)
                                Text(if (isDemoMode) "A preview of the NightSync experience" else "Your private space", color = NightMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Surface(shape = RoundedCornerShape(18.dp), color = NightRaised) {
                                Text(if (isDemoMode) "SAMPLE DATA" else "PRIVATE", color = NightCyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
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
                                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = NightCyan) }
                                Text(
                                    if (currentFolder.isBlank()) "My files" else currentFolder.replace("/", "  /  "),
                                    color = NightText, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { refreshFiles() }, enabled = !filesLoading) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = NightCyan) }
                            }
                        }
                        if (isDemoMode) {
                            Surface(color = NightRaised, shape = RoundedCornerShape(14.dp)) {
                                Text("Sample content only. Connect a server to manage your own files.", color = NightMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(14.dp))
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { uploadLauncher.launch(arrayOf("*/*")) }, enabled = !filesLoading, modifier = Modifier.weight(1f)) { Text("Upload files") }
                                OutlinedButton(onClick = { refreshFiles() }, enabled = !filesLoading, modifier = Modifier.weight(1f)) { Text("Refresh") }
                            }
                        }
                        if (!isDemoMode) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    Icon(if (item.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                                        contentDescription = if (item.isDirectory) "Folder" else "File",
                                        tint = if (item.isDirectory) NightCyan else NightViolet, modifier = Modifier.size(23.dp))
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(item.name, color = NightText, fontWeight = FontWeight.Medium, maxLines = 1)
                                        Text(if (item.isDirectory) "Folder" else item.sizeLabel, color = NightMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (!item.isDirectory && !isDemoMode) {
                                        TextButton(onClick = {
                                            fileToDownload = item
                                            downloadLauncher.launch(item.name)
                                        }) { Text("Save", color = NightCyan) }
                                    }
                                    if (!isDemoMode) TextButton(onClick = { itemToDelete = item }) { Text("More", color = NightMuted) }
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
private fun CheckboxRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = NightCyan))
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, color = NightText)
            Text(description, color = NightMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PremiumCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = NightSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun PremiumField(value: String, onValueChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
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
