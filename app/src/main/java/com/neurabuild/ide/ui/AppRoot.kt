package com.neurabuild.ide.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.neurabuild.ide.ai.AgentTools
import com.neurabuild.ide.data.ChatDb
import com.neurabuild.ide.runtime.AlpineManager
import com.neurabuild.ide.security.SecurePrefs
import com.neurabuild.ide.util.AppPrefs

@Composable
fun AppRoot(
    alpine: AlpineManager,
    secure: SecurePrefs,
    prefs: AppPrefs,
    db: ChatDb,
    tools: AgentTools
) {
    var setupDone by remember { mutableStateOf(prefs.setupDone && alpine.isInstalled()) }
    if (!setupDone) {
        SetupFlow(alpine, secure, prefs, onDone = {
            prefs.setupDone = true
            setupDone = true
        })
        return
    }
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf("Chat" to Icons.Filled.Forum, "Term" to Icons.Filled.Terminal, "Files" to Icons.Filled.Folder, "Preview" to Icons.Filled.Public, "Settings" to Icons.Filled.Settings)
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> ChatScreen(secure, db, tools)
                1 -> TerminalScreen(alpine)
                2 -> FilesScreen(tools)
                3 -> PreviewScreen(alpine, prefs)
                4 -> SettingsScreen(secure, prefs, db)
            }
        }
    }
}
