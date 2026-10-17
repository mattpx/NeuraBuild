package com.neurabuild.ide.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "neurabuild_secret",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var baseUrl: String
        get() = prefs.getString("base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        set(v) = prefs.edit().putString("base_url", v.trimEnd('/')).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()

    var model: String
        get() = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(v) = prefs.edit().putString("model", v).apply()

    fun hasKey(): Boolean = apiKey.isNotBlank()
}
