package com.neurabuild.ide.ai

sealed interface Verdict {
    data object AutoAllow : Verdict
    data class Confirm(val reason: String) : Verdict
    data class Deny(val reason: String) : Verdict
}

object Policy {
    private val autoCmds = setOf("ls", "cat", "pwd", "echo", "git")
    private val denyPatterns = listOf(
        Regex("""rm\s+(-[rf]*\s+)*/\s*(;|&&|\||$)"""),
        Regex("""rm\s+-rf?\s+/\*"""),
        Regex("""/system\b"""),
        Regex("""mkfs|dd\s+of=/dev"""),
        Regex(""":\(\)\s*\{\s*:\|\:&\s*\}""")
    )
    private val confirmCmds = setOf("rm", "mv", "cp", "chmod", "npm", "node", "apk", "pip", "python3", "git")

    fun checkShell(cmd: String): Verdict {
        val t = cmd.trim()
        if (t.isEmpty()) return Verdict.Deny("empty command")
        for (p in denyPatterns) if (p.containsMatchIn(t)) return Verdict.Deny("matches deny pattern: ${p.pattern}")
        if (t.contains("/proc/") && (t.contains(">") || t.contains("rm") || t.contains("mv"))) {
            return Verdict.Deny("writes under /proc are denied")
        }
        val first = t.split(Regex("""\s+|;|&&|\|\|""")).firstOrNull()?.trim() ?: ""
        val base = first.substringAfterLast('/')
        if (base in autoCmds) {
            if (base == "git" && !t.contains("status") && !t.contains("log") && !t.contains("diff")) {
                return Verdict.Confirm("git write op needs approval")
            }
            if (t.contains(">") || t.contains("rm ") || t.contains("mv ")) {
                return Verdict.Confirm("read-like command with write redirect")
            }
            return Verdict.AutoAllow
        }
        if (base in confirmCmds || t.contains("apk add") || t.contains("npm install")) {
            return Verdict.Confirm("potentially mutating: $base")
        }
        return Verdict.Confirm("default confirm: $base")
    }

    fun checkWrite(path: String): Verdict {
        val p = path.trim()
        if (p.isEmpty()) return Verdict.Deny("empty path")
        if (p.startsWith("/system") || p.startsWith("/proc") || p == "/" || p == "/*") {
            return Verdict.Deny("path outside workspace: $p")
        }
        return Verdict.Confirm("file write needs diff preview + Apply")
    }
}
