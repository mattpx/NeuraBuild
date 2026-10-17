package com.neurabuild.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurabuild.ide.util.DiffHelper
import com.neurabuild.ide.util.DiffRow

@Composable
fun DiffDialog(path: String, oldText: String, newText: String, onApprove: () -> Unit, onReject: () -> Unit) {
    val rows = remember(oldText, newText) { DiffHelper.compute(oldText, newText) }
    val adds = rows.count { it.kind == DiffRow.Kind.ADD }
    val dels = rows.count { it.kind == DiffRow.Kind.DEL }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Apply write to $path?") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp), Arrangement.spacedBy(4.dp)) {
                Text("+$adds -$dels lines", style = MaterialTheme.typography.labelSmall)
                LazyColumn(Modifier.weight(1f)) {
                    items(rows) { r ->
                        val bg = when (r.kind) {
                            DiffRow.Kind.ADD -> Color(0xFF1B3A23)
                            DiffRow.Kind.DEL -> Color(0xFF3D1A1A)
                            DiffRow.Kind.SAME -> Color.Transparent
                        }
                        val prefix = when (r.kind) {
                            DiffRow.Kind.ADD -> "+ "
                            DiffRow.Kind.DEL -> "- "
                            DiffRow.Kind.SAME -> "  "
                        }
                        Text(
                            prefix + (if (r.kind == DiffRow.Kind.ADD) r.newLine else r.oldLine).orEmpty().take(300),
                            Modifier.fillMaxWidth().background(bg).padding(2.dp),
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = when (r.kind) {
                                DiffRow.Kind.ADD -> Color(0xFF7EE787)
                                DiffRow.Kind.DEL -> Color(0xFFFF7B72)
                                DiffRow.Kind.SAME -> Color(0xFF9E9E9E)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onApprove) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Reject") } }
    )
}
