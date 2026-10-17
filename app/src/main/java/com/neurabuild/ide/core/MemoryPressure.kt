package com.neurabuild.ide.core

import android.content.Context
import android.webkit.WebView
import java.util.concurrent.CopyOnWriteArrayList

object MemoryPressure {
    private val releasables = CopyOnWriteArrayList<() -> Unit>()
    private var webView: WebView? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun trackWebView(view: WebView?) {
        webView?.let { old ->
            try {
                old.stopLoading()
                old.loadUrl("about:blank")
                old.clearHistory()
                old.clearCache(true)
                old.removeAllViews()
                old.destroy()
            } catch (_: Exception) {
            }
        }
        webView = view
    }

    fun registerReleasable(release: () -> Unit) {
        releasables.add(release)
    }

    fun onUiHidden() {
        try {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.clearCache(false)
        } catch (_: Exception) {
        }
        for (release in releasables) {
            try {
                release()
            } catch (_: Exception) {
            }
        }
        releasables.clear()
        System.gc()
    }

    fun onLowMemory() {
        onUiHidden()
        try {
            webView?.let {
                it.removeAllViews()
                it.destroy()
            }
        } catch (_: Exception) {
        }
        webView = null
        System.gc()
    }
}
