package org.nightsync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore(name = "nightsync_settings")
private val SERVER_URL = stringPreferencesKey("server_url")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NightSyncApp() }
    }
}

private enum class Tab { HOME, FILES, ACTIVITY, SETTINGS }

@Composable
private fun NightSyncApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.HOME) }
    var serverUrl by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        serverUrl = context.dataStore.data.first()[SERVER_URL] ?: ""
        loaded = true
    }
    MaterialTheme {
        Scaffold(bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == Tab.HOME, { tab = Tab.HOME }, icon = { Icon(Icons.Default.Cloud, null) }, label = { Text("Home") })
                NavigationBarItem(tab == Tab.FILES, { tab = Tab.FILES }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Files") })
                NavigationBarItem(tab == Tab.ACTIVITY, { tab = Tab.ACTIVITY }, icon = { Icon(Icons.Default.Sync, null) }, label = { Text("Activity") })
                NavigationBarItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }) { padding ->
            when (tab) {
                Tab.HOME -> HomeScreen(serverUrl, Modifier.padding(padding))
                Tab.FILES -> SimpleScreen("Your files", "File browsing and transfers will be added after server pairing.", Modifier.padding(padding))
                Tab.ACTIVITY -> SimpleScreen("Sync activity", "Uploads, downloads, retries and progress will appear here.", Modifier.padding(padding))
                Tab.SETTINGS -> SettingsScreen(serverUrl, { serverUrl = it }, loaded, {
                    scope.launch { context.dataStore.edit { prefs -> prefs[SERVER_URL] = serverUrl.trim().trimEnd('/') } }
                }, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun HomeScreen(serverUrl: String, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("NightSync", style = MaterialTheme.typography.headlineLarge); Text("Your private cloud, across your devices.") }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            Text(if (serverUrl.isBlank()) "Not configured" else "Address saved on this device")
            if (serverUrl.isNotBlank()) Text(serverUrl, style = MaterialTheme.typography.bodySmall)
            Text("Live connection testing is not implemented yet.", style = MaterialTheme.typography.bodySmall)
        } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("Storage used", "—", Modifier.weight(1f)); Metric("Devices", "—", Modifier.weight(1f)) } }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Camera backup", style = MaterialTheme.typography.titleMedium); Text("Not configured"); Text("Original-quality backup will be configured after secure server pairing.") } } }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Recent activity", style = MaterialTheme.typography.titleMedium); Text("No sync activity yet.") } } }
        item { Text("Development build • No files are uploaded in this version.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun SettingsScreen(url: String, onUrl: (String) -> Unit, loaded: Boolean, onSave: () -> Unit, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    val validHttps = url.trim().startsWith("https://") && url.trim().length > 10
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Text("Server connection", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = url, onValueChange = { onUrl(it); message = "" }, label = { Text("Server address") }, placeholder = { Text("https://your-server.example") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Text("Enter an HTTPS address. Do not enter passwords or secret keys here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Button(onClick = { onSave(); message = "Server address saved on this device." }, enabled = loaded && validHttps, modifier = Modifier.fillMaxWidth()) { Text("Save server address") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text("Live connection test, account sign-in and device pairing are the next development milestone.")
    }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title); Text(value, style = MaterialTheme.typography.headlineMedium) } }
}

@Composable
private fun SimpleScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description, style = MaterialTheme.typography.bodyLarge)
        Text("This section is not connected to a backend yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}
