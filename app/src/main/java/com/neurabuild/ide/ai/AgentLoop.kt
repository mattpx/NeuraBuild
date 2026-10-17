package com.neurabuild.ide.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PendingWrite(val path: String, val newContent: String, val oldContent: String?)
data class PendingShell(val cmd: String)

class AgentLoop(
    private val client: OpenAiClient,
    private val tools: AgentTools,
    private val maxSteps: Int = 8
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun step(
        history: MutableList<ChatMsg>,
        onAssistantText: (String) -> Unit,
        onNeedWriteApproval: suspend (PendingWrite) -> Boolean,
        onNeedShellApproval: suspend (PendingShell, Verdict) -> Boolean,
    ) {
        var steps = 0
        while (steps < maxSteps) {
            steps++
            val reply = try {
                client.chat(history)
            } catch (e: Exception) {
                onAssistantText("API error: ${e.message}")
                return
            }
            if (reply.text != null) {
                history += ChatMsg("assistant", reply.text)
                onAssistantText(reply.text)
            }
            if (reply.toolCalls.isEmpty()) return
            for (tc in reply.toolCalls) {
                val args = try { json.parseToJsonElement(tc.argsJson.ifBlank { "{}" }).jsonObject } catch (_: Exception) { null }
                when (tc.name) {
                    "readFile" -> {
                        val path = args?.get("path")?.jsonPrimitive?.content ?: ""
                        val r = tools.readFile(path)
                        history += ChatMsg("assistant", "", null, null)
                        history += ChatMsg("tool", (if (r.ok) "" else "ERROR: ") + r.output.take(8000), tc.id, tc.name)
                    }
                    "listDir" -> {
                        val path = args?.get("path")?.jsonPrimitive?.content ?: ""
                        val r = tools.listDir(path)
                        history += ChatMsg("tool", (if (r.ok) "" else "ERROR: ") + r.output.take(8000), tc.id, tc.name)
                    }
                    "runShell" -> {
                        val cmd = args?.get("cmd")?.jsonPrimitive?.content ?: ""
                        val verdict = Policy.checkShell(cmd)
                        if (verdict is Verdict.Deny) {
                            history += ChatMsg("tool", "DENIED: ${verdict.reason}", tc.id, tc.name)
                            onAssistantText("Denied `$cmd`: ${verdict.reason}")
                            continue
                        }
                        val approved = if (verdict is Verdict.AutoAllow) true else onNeedShellApproval(PendingShell(cmd), verdict)
                        if (!approved) {
                            history += ChatMsg("tool", "User rejected shell command.", tc.id, tc.name)
                            onAssistantText("Rejected: `$cmd`")
                            continue
                        }
                        val r = tools.runShell(cmd)
                        history += ChatMsg("tool", r.output.take(8000), tc.id, tc.name)
                    }
                    "writeFile" -> {
                        val path = args?.get("path")?.jsonPrimitive?.content ?: ""
                        val content = args?.get("content")?.jsonPrimitive?.content ?: ""
                        val chk = Policy.checkWrite(path)
                        if (chk is Verdict.Deny) {
                            history += ChatMsg("tool", "DENIED: ${chk.reason}", tc.id, tc.name)
                            onAssistantText("Denied write $path: ${chk.reason}")
                            continue
                        }
                        val old = tools.readFile(path).output.takeIf { tools.readFile(path).ok }
                        val approved = onNeedWriteApproval(PendingWrite(path, content, old))
                        if (!approved) {
                            history += ChatMsg("tool", "User rejected write (no changes made).", tc.id, tc.name)
                            onAssistantText("Rejected write to $path")
                            continue
                        }
                        val r = tools.writeFileDirect(path, content)
                        history += ChatMsg("tool", r.output, tc.id, tc.name)
                    }
                    else -> history += ChatMsg("tool", "unknown tool ${tc.name}", tc.id, tc.name)
                }
            }
        }
        onAssistantText("(stopped after $maxSteps tool steps)")
    }
}
