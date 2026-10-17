package com.neurabuild.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurabuild.ide.ai.Policy
import com.neurabuild.ide.ai.Verdict
import com.neurabuild.ide.core.MemoryPressure
import com.neurabuild.ide.runtime.AlpineManager
import com.neurabuild.ide.runtime.ProotRunner
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(alpine: AlpineManager) {
    var cmd by remember { mutableStateOf("cat /etc/alpine-release && uname -m") }
    var output by remember { mutableStateOf("$ short-lived PRoot sessions: each Run spawns, executes, kills.\n") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MemoryPressure.registerReleasable { output = "" }

    Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.spacedBy(8.dp)) {
        Card(Modifier.weight(1f).fillMaxWidth()) {
            SelectionContainer {
                Text(
                    output.takeLast(20000),
                    Modifier.padding(8.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(cmd, { cmd = it }, Modifier.weight(1f), label = { Text("$") }, enabled = !busy, maxLines = 3, singleLine = false)
            Button(enabled = !busy, onClick = {
                val c = cmd.trim()
                if (c.isEmpty()) return@Button
                val verdict = Policy.checkShell(c)
                if (verdict is Verdict.Deny) {
                    output += "\n$ $c\nDENIED: ${verdict.reason}\n"
                    return@Button
                }
                busy = true
                output += "\n$ $c\n"
                scope.launch {
                    val r = ProotRunner(alpine).run(c)
                    output += r.stdout.take(20000)
                    if (r.stderr.isNotBlank()) output += "\n[stderr]\n" + r.stderr.take(5000)
                    output += "\n[exit=${r.exitCode}]\n"
                    busy = false
                }
            }) { Text("Run") }
        }
    }
}
