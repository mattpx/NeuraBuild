package com.neurabuild.ide.runtime

import java.io.File
import java.util.concurrent.TimeUnit

data class CmdResult(val exitCode: Int, val stdout: String, val stderr: String)

/**
 * Short-lived PRoot sessions: spawn on demand, kill after command completes.
 * Do NOT keep background PTYs alive (memory + OS kills anyway).
 */
class ProotRunner(private val alpine: AlpineManager) {

    private val prootBin: File get() = File(alpine.binDir, "proot")
    private val loaderBin: File get() = File(alpine.binDir, "libproot-loader.so")

    fun commandLine(shellCmd: String): List<String> {
        // Explicit -r and -b so baked-in Termux default prefix is never used.
        return listOf(
            prootBin.absolutePath,
            "-r", alpine.alpineDir.absolutePath,
            "-b", "${alpine.workspaceDir.absolutePath}:/workspace",
            "-b", "/proc:/proc",
            "-b", "/sys:/sys",
            "-b", "/dev:/dev",
            "-w", "/workspace",
            "/bin/sh", "-c", shellCmd
        )
    }

    fun env(): Map<String, String> = buildMap {
        put("PROOT_TMP_DIR", alpine.prootTmpDir.absolutePath)
        if (loaderBin.exists()) put("PROOT_LOADER", loaderBin.absolutePath)
        put("HOME", "/root")
        put("TERM", "xterm-256color")
        put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/bin:/bin:/sbin")
        // NOTE: never set PROOT_NO_SECCOMP — breaks getcwd under musl.
    }

    suspend fun run(shellCmd: String, timeoutMs: Long = 60_000): CmdResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runBlocking(shellCmd, timeoutMs)
        }

    fun runBlocking(shellCmd: String, timeoutMs: Long = 60_000): CmdResult {
        if (!prootBin.exists()) {
            return CmdResult(127, "", "proot binary missing at ${prootBin.absolutePath}. See docs/PROOT_PACKAGING.md")
        }
        return try {
            val pb = ProcessBuilder(commandLine(shellCmd))
            pb.environment().putAll(env())
            pb.redirectErrorStream(false)
            val proc = pb.start()
            val outReader = proc.inputStream.bufferedReader()
            val errReader = proc.errorStream.bufferedReader()
            val out = StringBuilder()
            val err = StringBuilder()
            val t1 = Thread { try { outReader.forEachLine { l -> if (out.length < 200_000) out.appendLine(l) } } catch (_: Exception) {} }
            val t2 = Thread { try { errReader.forEachLine { l -> if (err.length < 200_000) err.appendLine(l) } } catch (_: Exception) {} }
            t1.start(); t2.start()
            val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                t1.join(2000); t2.join(2000)
                return CmdResult(124, out.toString(), err.toString() + "\n[timed out after ${timeoutMs}ms, killed]")
            }
            t1.join(5000); t2.join(5000)
            CmdResult(proc.exitValue(), out.toString(), err.toString())
        } catch (e: Exception) {
            CmdResult(127, "", "proot launch failed: ${e.message}")
        }
    }
}
