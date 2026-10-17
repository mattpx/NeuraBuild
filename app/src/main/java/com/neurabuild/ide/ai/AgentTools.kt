package com.neurabuild.ide.ai

import com.neurabuild.ide.runtime.AlpineManager
import com.neurabuild.ide.runtime.ProotRunner
import java.io.File

data class ToolResult(val ok: Boolean, val output: String)

class AgentTools(private val alpine: AlpineManager) {
    private val runner get() = ProotRunner(alpine)

    fun resolveGuestPath(path: String): File {
        val clean = path.trim().removePrefix("/workspace").trimStart('/')
        val f = File(alpine.workspaceDir, clean)
        val canon = f.canonicalPath
        val root = alpine.workspaceDir.canonicalPath
        require(canon == root || canon.startsWith("$root/")) { "path escapes workspace" }
        return f
    }

    suspend fun readFile(path: String, maxBytes: Long = 100_000): ToolResult {
        return try {
            val f = resolveGuestPath(path)
            if (!f.exists()) return ToolResult(false, "not found: $path")
            if (f.isDirectory) return ToolResult(false, "is directory: $path")
            if (f.length() > maxBytes) return ToolResult(false, "file too large (${f.length()} bytes), max $maxBytes")
            ToolResult(true, f.readText())
        } catch (e: Exception) {
            ToolResult(false, "readFile error: ${e.message}")
        }
    }

    suspend fun runShell(cmd: String, timeoutMs: Long = 60_000): ToolResult {
        return try {
            val r = runner.run(cmd, timeoutMs)
            val out = buildString {
                append("[exit=${r.exitCode}]\n")
                if (r.stdout.isNotBlank()) append(r.stdout.take(50_000))
                if (r.stderr.isNotBlank()) { append("\n--- stderr ---\n"); append(r.stderr.take(20_000)) }
            }
            ToolResult(r.exitCode == 0, out)
        } catch (e: Exception) {
            ToolResult(false, "runShell error: ${e.message}")
        }
    }

    fun writeFileDirect(path: String, content: String): ToolResult {
        return try {
            val f = resolveGuestPath(path)
            f.parentFile?.mkdirs()
            f.writeText(content)
            ToolResult(true, "wrote ${content.length} chars to $path")
        } catch (e: Exception) {
            ToolResult(false, "writeFile error: ${e.message}")
        }
    }

    fun listDir(path: String): ToolResult {
        return try {
            val f = if (path.isBlank() || path == "/" || path == "/workspace") alpine.workspaceDir else resolveGuestPath(path)
            if (!f.exists()) return ToolResult(false, "not found: $path")
            if (!f.isDirectory) return ToolResult(false, "not a directory: $path")
            val items = f.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                ?.joinToString("\n") { (if (it.isDirectory) "d " else "f ") + it.name } ?: ""
            ToolResult(true, items.ifEmpty { "(empty)" })
        } catch (e: Exception) {
            ToolResult(false, "listDir error: ${e.message}")
        }
    }
}
