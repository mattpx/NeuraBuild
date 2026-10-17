package com.neurabuild.ide.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurabuild.ide.ai.AgentTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileItem(val name: String, val isDir: Boolean, val rel: String)

@Composable
fun FilesScreen(tools: AgentTools) {
    var cwd by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var editing by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var createIsDir by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FileItem?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            val r = withContext(Dispatchers.IO) { tools.listDir(cwd) }
            items = if (r.ok) r.output.lines().filter { it.length > 2 }.mapNotNull { line ->
                val isDir = line.startsWith("d ")
                val name = line.drop(2)
                if (name.isBlank()) null else FileItem(name, isDir, if (cwd.isEmpty()) name else "$cwd/$name")
            } else emptyList()
        }
    }
    remember(cwd) { scope.launch { refresh() } }

    editing?.let { rel ->
        EditorScreen(path = rel, tools = tools, onBack = { editing = null; refresh() })
        return
    }

    Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { cwd = "" }) { Text("/workspace") }
            if (cwd.isNotEmpty()) Text("/$cwd", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { createIsDir = false; newName = ""; showCreate = true }) { Icon(Icons.Filled.NoteAdd, "new file") }
            IconButton(onClick = { createIsDir = true; newName = ""; showCreate = true }) { Icon(Icons.Filled.CreateNewFolder, "new folder") }
        }
        if (cwd.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().clickable {
                cwd = cwd.substringBeforeLast("/", "")
            }) { Text(".. (up)", Modifier.padding(12.dp)) }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items) { item ->
                Card(Modifier.fillMaxWidth().clickable {
                    if (item.isDir) cwd = item.rel else editing = item.rel
                }) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (item.isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile, null)
                            Text(item.name)
                        }
                        Row {
                            IconButton(onClick = { renameTarget = item; newName = item.name }) { Icon(Icons.Filled.DriveFileRenameOutline, "rename") }
                            IconButton(onClick = { deleteTarget = item }) { Icon(Icons.Filled.Delete, "delete") }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(if (createIsDir) "New folder" else "New file") },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text("name") }) },
            confirmButton = { TextButton(onClick = {
                val name = newName.trim()
                if (name.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val f = tools.resolveGuestPath(if (cwd.isEmpty()) name else "$cwd/$name")
                            if (createIsDir) f.mkdirs() else { f.parentFile?.mkdirs(); f.createNewFile() }
                        } catch (_: Exception) {}
                        withContext(kotlinx.coroutines.Dispatchers.Main) { refresh() }
                    }
                }
                showCreate = false
            }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text("new name") }) },
            confirmButton = { TextButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val src = tools.resolveGuestPath(target.rel)
                        val dst = File(src.parentFile, newName.trim())
                        src.renameTo(dst)
                    } catch (_: Exception) {}
                    withContext(kotlinx.coroutines.Dispatchers.Main) { refresh() }
                }
                renameTarget = null
            }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val f = tools.resolveGuestPath(target.rel)
                        if (f.isDirectory) f.deleteRecursively() else f.delete()
                    } catch (_: Exception) {}
                    withContext(kotlinx.coroutines.Dispatchers.Main) { refresh() }
                }
                deleteTarget = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
