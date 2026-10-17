package com.neurabuild.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neurabuild.ide.ai.OpenAiClient
import com.neurabuild.ide.runtime.AlpineManager
import com.neurabuild.ide.security.SecurePrefs
import com.neurabuild.ide.util.AppPrefs
import com.neurabuild.ide.util.DeviceCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SetupFlow(alpine: AlpineManager, secure: SecurePrefs, prefs: AppPrefs, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val report = remember { DeviceCheck.report(ctx) }
    var withNode by remember { mutableStateOf(true) }
    var withPython by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(secure.baseUrl) }
    var apiKey by remember { mutableStateOf(secure.apiKey) }
    var model by remember { mutableStateOf(secure.model) }
    var testMsg by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressMsg by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), Arrangement.spacedBy(12.dp)) {
        Text("NeuraBuild setup (${step + 1}/4)", style = MaterialTheme.typography.titleLarge)
        when (step) {
            0 -> {
                Text(if (report.abiOk) "ABI: ${report.abis} (OK)" else "BLOCKED: ${report.abis} — arm64-v8a required")
                Text("Storage free: ${report.freeMb}MB" + if (report.storageOk) " (OK)" else " (need >= 200MB)")
                Text("RAM: ${report.totalRamMb}MB" + if (report.lowRam) " (warning: < 4GB, may lag)" else " (OK)")
                if (!report.abiOk || !report.storageOk) {
                    Text("Fix the above to continue.", color = MaterialTheme.colorScheme.error)
                } else Button(onClick = { step = 1 }) { Text("Next") }
            }
            1 -> {
                Text("Toolchain", style = MaterialTheme.typography.titleMedium)
                Row { Checkbox(withNode, { withNode = it }); Text("Alpine + Node.js (~60MB download)") }
                Row { Checkbox(withPython, { withPython = it }); Text("Python3 + pip (~50MB download)") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { step = 0 }) { Text("Back") }
                    Button(onClick = { step = 2 }) { Text("Next") }
                }
            }
            2 -> {
                Text("AI provider (OpenAI-compatible)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            testMsg = "Testing..."
                            try {
                                val r = withContext(Dispatchers.IO) { OpenAiClient(baseUrl, apiKey, model).test() }
                                testMsg = "OK: $r"
                            } catch (e: Exception) { testMsg = "Failed: ${e.message}" }
                        }
                    }) { Text("Test") }
                }
                if (testMsg.isNotEmpty()) Text(testMsg)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { step = 1 }) { Text("Back") }
                    Button(onClick = {
                        secure.baseUrl = baseUrl; secure.apiKey = apiKey; secure.model = model
                        step = 3
                    }) { Text("Next") }
                }
            }
            3 -> {
                Text("Extract & ready", style = MaterialTheme.typography.titleMedium)
                if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                if (progressMsg.isNotEmpty()) Text(progressMsg)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    error = ""
                    scope.launch {
                        val r = alpine.stagedInstall(withNode, withPython) { msg, frac ->
                            progressMsg = msg; progress = frac
                        }
                        if (r.isSuccess) onDone() else error = r.exceptionOrNull()?.message ?: "install failed"
                    }
                }) { Text("Install & Start") }
            }
        }
    }
}
