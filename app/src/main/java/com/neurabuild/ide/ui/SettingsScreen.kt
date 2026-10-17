package com.neurabuild.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurabuild.ide.ai.OpenAiClient
import com.neurabuild.ide.data.ChatDb
import com.neurabuild.ide.security.SecurePrefs
import com.neurabuild.ide.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(secure: SecurePrefs, prefs: AppPrefs, db: ChatDb) {
    var baseUrl by remember { mutableStateOf(secure.baseUrl) }
    var apiKey by remember { mutableStateOf(secure.apiKey) }
    var model by remember { mutableStateOf(secure.model) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                Text("AI provider (OpenAI-compatible)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                Text("Stored in EncryptedSharedPreferences (AES-256 via Keystore). Backup disabled.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        secure.baseUrl = baseUrl; secure.apiKey = apiKey; secure.model = model
                        msg = "Saved."
                    }) { Text("Save") }
                    Button(onClick = {
                        scope.launch {
                            msg = "Testing..."
                            try {
                                val r = withContext(Dispatchers.IO) { OpenAiClient(baseUrl, apiKey, model).test() }
                                msg = "OK: $r"
                            } catch (e: Exception) { msg = "Failed: ${e.message}" }
                        }
                    }) { Text("Test") }
                }
                if (msg.isNotEmpty()) Text(msg)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                Text("Chat history", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { scope.launch { db.msgs().clear(); msg = "History cleared." } }) { Text("Clear history") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("NeuraBuild 0.1.0 — AI chat is the main interface. Alpine + PRoot runtime, short-lived sessions, no root.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
