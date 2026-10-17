package com.neurabuild.ide.core

import android.app.Application
import android.content.ComponentCallbacks2
import android.webkit.WebView

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MemoryPressure.init(this)
    }

    // NON-NEGOTIABLE: release heavy objects immediately when UI hidden.
    // Do not attempt background persistence of terminals — OS will kill us anyway.
    // Chat history survives via Room; terminal sessions are rebuilt on demand.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            MemoryPressure.onUiHidden()
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            MemoryPressure.onLowMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MemoryPressure.onLowMemory()
    }
}
