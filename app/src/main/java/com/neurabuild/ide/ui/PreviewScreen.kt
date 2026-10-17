package com.neurabuild.ide.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.neurabuild.ide.core.MemoryPressure
import com.neurabuild.ide.preview.PreviewProxy
import com.neurabuild.ide.runtime.AlpineManager
import com.neurabuild.ide.util.AppPrefs

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(alpine: AlpineManager, prefs: AppPrefs) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(prefs.previewEnabled) }
    var showWarn by remember { mutableStateOf(false) }
    var targetPort by remember { mutableStateOf(prefs.targetPort.toString()) }
    var proxy by remember { mutableStateOf<PreviewProxy?>(null) }
    var status by remember { mutableStateOf("Preview is OFF.") }
    var reloadKey by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            try { proxy?.stop() } catch (_: Exception) {}
            MemoryPressure.trackWebView(null)
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                Text("Web preview (off by default)", style = MaterialTheme.typography.titleMedium)
                Text("Runs your Node server (:${targetPort.ifBlank { "3000" }}) through an in-app proxy (:8080).", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(targetPort, { targetPort = it.filter { c -> c.isDigit() }.take(5) }, label = { Text("Node port") }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (!enabled) showWarn = true
                        else {
                            try { proxy?.stop() } catch (_: Exception) {}
                            proxy = null
                            enabled = false
                            prefs.previewEnabled = false
                            status = "Preview is OFF."
                            MemoryPressure.trackWebView(null)
                        }
                    }) { Text(if (enabled) "Stop" else "Enable") }
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (enabled) {
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        MemoryPressure.trackWebView(this)
                        loadUrl("http://127.0.0.1:8080/")
                    }
                },
                update = { wv ->
                    if (reloadKey > 0) wv.reload()
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }

    if (showWarn) {
        AlertDialog(
            onDismissRequest = { showWarn = false },
            title = { Text("Enable preview?") },
            text = { Text("May cause lag on low-memory devices. Prefer bundled/single-file Node apps: PRoot syscall overhead ~2x (2841-file project = 20s cold start, esbuild-bundled single file = 6.4s).") },
            confirmButton = { TextButton(onClick = {
                showWarn = false
                val port = targetPort.toIntOrNull() ?: 3000
                prefs.targetPort = port
                try {
                    proxy?.stop()
                    val p = PreviewProxy(targetPort = port, listenPort = 8080)
                    p.start()
                    proxy = p
                    enabled = true
                    prefs.previewEnabled = true
                    status = "Proxy live: WebView :8080 -> Node :$port. Start your server in Term tab, e.g. `node server.js`."
                    reloadKey++
                } catch (e: Exception) {
                    status = "Proxy failed: ${e.message}"
                }
            }) { Text("Enable anyway") } },
            dismissButton = { TextButton(onClick = { showWarn = false }) { Text("Cancel") } }
        )
    }
}
