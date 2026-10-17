package com.neurabuild.ide.runtime

import java.io.File
import java.io.InputStream

/** Minimal POSIX tar (ustar) extractor. No external deps, low memory, streaming. */
object TarExtractor {
    private const val BLOCK = 512

    fun extract(input: InputStream, destDir: File, onFrac: (Float) -> Unit = {}) {
        destDir.mkdirs()
        val header = ByteArray(BLOCK)
        var entries = 0
        while (true) {
            if (!readFull(input, header, BLOCK)) break
            if (header.all { it == 0.toByte() }) break // end-of-archive (2 zero blocks)
            val name = readString(header, 0, 100)
            val prefix = readString(header, 345, 155)
            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name
            val size = readOctal(header, 124, 12)
            val type = header[156].toInt().toChar()
            val target = File(destDir, fullName)
            // Zip-slip guard
            if (!target.canonicalPath.startsWith(destDir.canonicalPath + File.separator) && target.canonicalPath != destDir.canonicalPath) {
                skip(input, size)
                continue
            }
            when (type) {
                '5' -> target.mkdirs()
                '2' -> { /* symlink */ 
                    val linkTarget = readString(header, 157, 100)
                    try {
                        target.parentFile?.mkdirs()
                        target.delete()
                        android.system.Os.symlink(linkTarget, target.absolutePath)
                    } catch (_: Exception) { }
                }
                '1' -> { /* hardlink: copy */ 
                    val linkName = readString(header, 157, 100)
                    try {
                        File(destDir, linkName).copyTo(target, overwrite = true)
                    } catch (_: Exception) { }
                }
                else -> {
                    target.parentFile?.mkdirs()
                    writeFile(input, target, size)
                    // mode
                    try {
                        val mode = readOctal(header, 100, 8).toInt() and 511
                        if (mode != 0) android.system.Os.chmod(target.absolutePath, mode)
                    } catch (_: Exception) { }
                }
            }
            // skip padding to 512 boundary (writeFile already consumed exact size; skip pad)
            val pad = ((size + BLOCK - 1) / BLOCK * BLOCK - size)
            if (type == '5' || type == '2' || type == '1') {
                // no data consumed yet
                skip(input, 0)
            } else {
                skip(input, pad)
            }
            entries++
            if (entries % 200 == 0) onFrac((entries % 10000) / 10000f)
        }
        onFrac(1.0f)
    }

    private fun writeFile(input: InputStream, out: File, size: Long) {
        out.outputStream().use { o ->
            var remaining = size
            val buf = ByteArray(32 * 1024)
            while (remaining > 0) {
                val want = minOf(buf.size.toLong(), remaining).toInt()
                val n = input.read(buf, 0, want)
                if (n <= 0) break
                o.write(buf, 0, n)
                remaining -= n
            }
        }
    }

    private fun skip(input: InputStream, n: Long) {
        var remaining = n
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (r <= 0) break
            remaining -= r
        }
    }

    private fun readFull(input: InputStream, buf: ByteArray, len: Int): Boolean {
        var off = 0
        while (off < len) {
            val r = input.read(buf, off, len - off)
            if (r <= 0) return off != 0
            off += r
        }
        return true
    }

    private fun readString(buf: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && buf[end] != 0.toByte()) end++
        return String(buf, off, end - off, Charsets.UTF_8)
    }

    private fun readOctal(buf: ByteArray, off: Int, len: Int): Long {
        var s = String(buf, off, len, Charsets.US_ASCII).trim().trimEnd(0.toChar()).trim()
        if (s.isEmpty()) return 0
        // strip trailing space/null
        s = s.split(" ").first()
        return try { s.toLong(8) } catch (_: Exception) { 0 }
    }
}
