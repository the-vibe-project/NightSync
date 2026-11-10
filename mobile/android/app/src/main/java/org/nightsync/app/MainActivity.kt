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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nightsync", Context.MODE_PRIVATE)
        setContent {
            NightSyncAuthApp(
                initialAddress = prefs.getString("server_address", "http://127.0.0.1:8787") ?: "http://127.0.0.1:8787",
                onSaveAddress = { prefs.edit().putString("server_address", it).apply() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightSyncAuthApp(initialAddress: String, onSaveAddress: (String) -> Unit) {
    var address by remember { mutableStateOf(initialAddress) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf("") }
    var mustChange by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Sign in to your NightSync server") }
    var busy by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun runRequest(path: String, method: String, payload: JSONObject?, authToken: String? = null, done: (Int, JSONObject?) -> Unit) {
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = URL(address.trim().trimEnd('/') + path)
                    val c = (url.openConnection() as HttpURLConnection)
                    c.requestMethod = method
                    c.connectTimeout = 8000; c.readTimeout = 8000
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
                } catch (e: Exception) { 0 to JSONObject().put("error", e.message ?: "Connection failed") }
            }
            busy = false
            done(result.first, result.second)
        }
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("NightSync") }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your private cloud. Your storage. Your control.", style = MaterialTheme.typography.bodyMedium)
                if (token == null) {
                    Text("Sign in", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(address, { address = it }, label = { Text("Server address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(username, { username = it }, label = { Text("User ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(enabled = !busy, onClick = {
                        val a = address.trim().trimEnd('/')
                        if (!(a.startsWith("https://") || a.startsWith("http://127.0.0.1") || a.startsWith("http://localhost"))) {
                            status = "Use HTTPS except for localhost development."
                        } else {
                            onSaveAddress(a)
                            runRequest("/api/v1/login", "POST", JSONObject().put("username", username).put("password", password)) { code, body ->
                                if (code == 200 && body != null) {
                                    token = body.optString("token")
                                    role = body.optString("role")
                                    mustChange = body.optBoolean("must_change_password")
                                    status = "Signed in as $username ($role)"
                                } else status = body?.optString("error") ?: "Sign-in failed"
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Signing in…" else "Sign in") }
                    Text("First time? Create the first administrator on the Windows host using the Windows app setup page.", style = MaterialTheme.typography.bodySmall)
                } else if (mustChange) {
                    Text("Set your new password", style = MaterialTheme.typography.headlineMedium)
                    Text("Your admin-issued temporary password must be changed before you continue.")
                    OutlinedTextField(password, { password = it }, label = { Text("Temporary/current password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newPassword, { newPassword = it }, label = { Text("New password (12+ characters)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy, onClick = {
                        runRequest("/api/v1/change-password", "POST", JSONObject().put("current_password", password).put("new_password", newPassword), token) { code, body ->
                            if (code == 200) { token = null; password = ""; newPassword = ""; mustChange = false; status = "Password changed. Please sign in again." }
                            else status = body?.optString("error") ?: "Password change failed"
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Change password") }
                } else {
                    Text("Welcome, $username", style = MaterialTheme.typography.headlineMedium)
                    Text("Role: $role")
                    Text("You are signed in. Live dashboard and file screens will be connected to authenticated endpoints in the next client milestone.")
                    Button(onClick = { token = null; password = ""; status = "Signed out" }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
                }
                Text(status, color = MaterialTheme.colorScheme.primary)
                Text("NightSync 0.6.0 · Local development authentication", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
