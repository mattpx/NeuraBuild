package com.neurabuild.ide.util

import android.content.Context

class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("neurabuild", Context.MODE_PRIVATE)
    var setupDone: Boolean
        get() = p.getBoolean("setup_done", false)
        set(v) = p.edit().putBoolean("setup_done", v).apply()
    var previewEnabled: Boolean
        get() = p.getBoolean("preview_enabled", false)
        set(v) = p.edit().putBoolean("preview_enabled", v).apply()
    var targetPort: Int
        get() = p.getInt("target_port", 3000)
        set(v) = p.edit().putInt("target_port", v).apply()
}
