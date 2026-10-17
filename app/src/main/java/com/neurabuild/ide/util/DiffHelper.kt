package com.neurabuild.ide.util

import com.github.difflib.DiffUtils

data class DiffRow(val kind: Kind, val oldLine: String?, val newLine: String?) {
    enum class Kind { SAME, ADD, DEL }
}

object DiffHelper {
    fun compute(oldText: String, newText: String, maxRows: Int = 2000): List<DiffRow> {
        val a = oldText.lines()
        val b = newText.lines()
        val patch = DiffUtils.diff(a, b)
        val rows = ArrayList<DiffRow>()
        var aPos = 0
        for (delta in patch.deltas) {
            val src = delta.source
            val tgt = delta.target
            while (aPos < src.position && rows.size < maxRows) {
                rows += DiffRow(DiffRow.Kind.SAME, a.getOrNull(aPos), a.getOrNull(aPos))
                aPos++
            }
            when (delta.type) {
                com.github.difflib.patch.DeltaType.CHANGE -> {
                    val n = maxOf(src.lines.size, tgt.lines.size)
                    for (i in 0 until n) {
                        if (rows.size >= maxRows) break
                        rows += DiffRow(DiffRow.Kind.DEL, src.lines.getOrNull(i), null)
                    }
                    for (i in 0 until n) {
                        if (rows.size >= maxRows) break
                        rows += DiffRow(DiffRow.Kind.ADD, null, tgt.lines.getOrNull(i))
                    }
                }
                com.github.difflib.patch.DeltaType.DELETE ->
                    for (l in src.lines) { if (rows.size >= maxRows) break; rows += DiffRow(DiffRow.Kind.DEL, l, null) }
                com.github.difflib.patch.DeltaType.INSERT ->
                    for (l in tgt.lines) { if (rows.size >= maxRows) break; rows += DiffRow(DiffRow.Kind.ADD, null, l) }
                else -> {}
            }
            aPos = src.position + src.lines.size
        }
        while (aPos < a.size && rows.size < maxRows) {
            rows += DiffRow(DiffRow.Kind.SAME, a[aPos], a[aPos]); aPos++
        }
        return rows
    }
}
