package com.neurabuild.ide.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatMsg(val role: String, val content: String, val toolCallId: String? = null, val name: String? = null)
data class ToolCall(val id: String, val name: String, val argsJson: String)
data class AiReply(val text: String?, val toolCalls: List<ToolCall>)

class OpenAiClient(baseUrl: String, private val apiKey: String, private val model: String) {
    private val url = baseUrl.trimEnd('/') + "/chat/completions"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        fun toolsSpec(): JsonArray = Json.parseToJsonElement("""
        [
          {"type":"function","function":{"name":"readFile","description":"Read a text file from /workspace","parameters":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}}},
          {"type":"function","function":{"name":"listDir","description":"List a directory under /workspace","parameters":{"type":"object","properties":{"path":{"type":"string"}}}}},
          {"type":"function","function":{"name":"writeFile","description":"Write a file (needs user Apply)","parameters":{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}}},
          {"type":"function","function":{"name":"runShell","description":"Run shell inside Alpine","parameters":{"type":"object","properties":{"cmd":{"type":"string"}},"required":["cmd"]}}}
        ]
        """.trimIndent()) as JsonArray
    }

    fun chat(history: List<ChatMsg>, toolResults: List<ChatMsg> = emptyList()): AiReply {
        val messages = buildJsonObject {
            putJsonArray("messages") {
                for (m in history + toolResults) {
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                        if (m.toolCallId != null) put("tool_call_id", m.toolCallId)
                        if (m.name != null) put("name", m.name)
                    })
                }
            }
        }
        val body = buildJsonObject {
            put("model", model)
            put("messages", messages["messages"]!!)
            put("tools", toolsSpec())
            put("tool_choice", "auto")
            put("temperature", 0.2)
        }
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IllegalStateException("API ${resp.code}: ${text.take(500)}")
            val root = json.parseToJsonElement(text).jsonObject
            val choice = root["choices"]!!.jsonArray.first().jsonObject
            val msg = choice["message"]!!.jsonObject
            val content = msg["content"]?.jsonPrimitive?.content
            val calls = mutableListOf<ToolCall>()
            msg["tool_calls"]?.let { tc ->
                for (el in tc.jsonArray) {
                    val o = el.jsonObject
                    val fn = o["function"]!!.jsonObject
                    calls += ToolCall(
                        o["id"]!!.jsonPrimitive.content,
                        fn["name"]!!.jsonPrimitive.content,
                        fn["arguments"]!!.jsonPrimitive.content
                    )
                }
            }
            val finish = choice["finish_reason"]?.jsonPrimitive?.content
            if (calls.isEmpty() && content.isNullOrBlank() && finish == "tool_calls") {
                throw IllegalStateException("empty tool_calls response")
            }
            return AiReply(content?.takeIf { it.isNotBlank() }, calls)
        }
    }

    fun test(): String {
        val r = chat(listOf(ChatMsg("user", "Reply with exactly: OK")))
        return r.text ?: "(empty reply, tools=${r.toolCalls.size})"
    }
}
