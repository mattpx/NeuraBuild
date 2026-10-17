package com.neurabuild.ide.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.room.Room
import com.neurabuild.ide.ai.AgentTools
import com.neurabuild.ide.data.ChatDb
import com.neurabuild.ide.runtime.AlpineManager
import com.neurabuild.ide.security.SecurePrefs
import com.neurabuild.ide.util.AppPrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alpine = AlpineManager(this)
        val secure = SecurePrefs(this)
        val prefs = AppPrefs(this)
        val db = Room.databaseBuilder(this, ChatDb::class.java, "chat.db").build()
        val tools = AgentTools(alpine)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    RootNav(alpine, secure, prefs, db, tools)
                }
            }
        }
    }
}

@Composable
fun RootNav(
    alpine: AlpineManager,
    secure: SecurePrefs,
    prefs: AppPrefs,
    db: ChatDb,
    tools: AgentTools
) = AppRoot(alpine, secure, prefs, db, tools)
