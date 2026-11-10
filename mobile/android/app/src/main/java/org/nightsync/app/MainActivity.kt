package org.nightsync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NightSyncScreen() }
    }
}

@Composable
fun NightSyncScreen() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text("NightSync", fontSize = 32.sp, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text("Your private cloud. Your storage. Your control.")
                Spacer(Modifier.height(28.dp))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Welcome", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("This is the fresh Android application scaffold.")
                        Text("Server connection and automatic photo backup are coming in later milestones.")
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("NightSync 0.1.0 · Development build", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
