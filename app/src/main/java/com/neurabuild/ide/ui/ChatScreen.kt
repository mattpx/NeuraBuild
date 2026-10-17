package com.neurabuild.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurabuild.ide.ai.AgentLoop
import com.neurabuild.ide.ai.AgentTools
import com.neurabuild.ide.ai.ChatMsg
import com.neurabuild.ide.ai.OpenAiClient
import com.neurabuild.ide.ai.PendingShell
import com.neurabuild.ide.ai.PendingWrite
import com.neurabuild.ide.ai.Verdict
import com.neurabuild.ide.core.MemoryPressure
import com.neurabuild.ide.data.ChatDb
import com.neurabuild.ide.data.MsgEntity
import com.neurabuild.ide.security.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun ChatScreen(secure: SecurePrefs, db: ChatDb, tools: AgentTools) {
    val msgs by db.msgs().all().collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingWrite by remember { mutableStateOf<PendingWrite?>(null) }
    var pendingShell by remember { mutableStateOf<Pair<PendingShell, Verdict>?>(null) }
    var writeResume by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var shellResume by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    MemoryPressure.registerReleasable { input = "" }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        if (!secure.hasKey()) {
            Card(Modifier.fillMaxWidth().padding(4.dp)) {
                Text("No API key set. Go to Settings > AI provider.", Modifier.padding(12.dp))
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(msgs) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text(m.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(m.content.take(4000))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), label = { Text(if (busy) "working..." else "Ask AI...") }, enabled = !busy, maxLines = 4)
            Button(enabled = !busy && input.isNotBlank(), onClick = {
                val text = input.trim()
                input = ""
                scope.launch {
                    busy = true
                    try {
                        db.msgs().insert(MsgEntity(role = "user", content = text))
                        val history = db.msgs().allOnce().map { ChatMsg(it.role, it.content) }.toMutableList()
                        history.add(0, ChatMsg("system", "You are a coding assistant inside an Android IDE. Workspace is /workspace (Alpine Linux via PRoot). Use tools to read/run/write. Keep replies short. For writes, prefer small diffs."))
                        val client = withContext(Dispatchers.IO) { OpenAiClient(secure.baseUrl, secure.apiKey, secure.model) }
                        val loop = AgentLoop(client, tools)
                        loop.step(
                            history,
                            onAssistantText = { t -> scope.launch { db.msgs().insert(MsgEntity(role = "assistant", content = t.take(8000))) } },
                            onNeedWriteApproval = { pw ->
                                suspendCoroutine { cont ->
                                    pendingWrite = pw
                                    writeResume = { ok -> cont.resume(ok) }
                                }
                            },
                            onNeedShellApproval = { ps, verdict ->
                                suspendCoroutine { cont ->
                                    pendingShell = ps to verdict
                                    shellResume = { ok -> cont.resume(ok) }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        db.msgs().insert(MsgEntity(role = "assistant", content = "Error: ${e.message}"))
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Send") }
        }
    }

    pendingWrite?.let { pw ->
        DiffDialog(path = pw.path, oldText = pw.oldContent ?: "", newText = pw.newContent,
            onApprove = { pendingWrite = null; writeResume?.invoke(true); writeResume = null },
            onReject = { pendingWrite = null; writeResume?.invoke(false); writeResume = null })
    }
    pendingShell?.let { (ps, verdict) ->
        val reason = (verdict as? Verdict.Confirm)?.reason ?: "needs approval"
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Run in Alpine?") },
            text = { Text("$reason\n\n$ ${ps.cmd.take(1000)}") },
            confirmButton = { TextButton(onClick = { pendingShell = null; shellResume?.invoke(true); shellResume = null }) { Text("Run") } },
            dismissButton = { TextButton(onClick = { pendingShell = null; shellResume?.invoke(false); shellResume = null }) { Text("Reject") } }
        )
    }
}
