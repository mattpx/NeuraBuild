package com.neurabuild.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurabuild.ide.ai.AgentTools
import com.neurabuild.ide.core.MemoryPressure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(path: String, tools: AgentTools, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    MemoryPressure.registerReleasable { if (text.length > 50_000) text = "" }

    LaunchedEffect(path) {
        val r = withContext(Dispatchers.IO) { tools.readFile(path) }
        if (r.ok) { text = r.output; loaded = true } else error = r.output
    }

    Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("< Files") }
            Text(path, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        // Plain text editor by design: no CodeMirror/CodeView (memory).
        OutlinedTextField(
            text, { text = it; saved = "" },
            Modifier.weight(1f).fillMaxWidth(),
            enabled = loaded,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = loaded, onClick = {
                scope.launch(Dispatchers.IO) {
                    val r = tools.writeFileDirect(path, text)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        saved = if (r.ok) "Saved." else r.output
                    }
                }
            }) { Text("Save") }
            if (saved.isNotEmpty()) Text(saved, Modifier.padding(top = 12.dp))
        }
    }
}
