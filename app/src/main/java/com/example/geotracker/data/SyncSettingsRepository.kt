package com.example.geotracker.data

import android.content.Context
import android.content.SharedPreferences

object SyncSettingsRepository {
    private const val PREFS_NAME = "sync_settings"
    private const val KEY_SYNC_URL = "sync_url"

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSyncUrl(): String {
        return prefs?.getString(KEY_SYNC_URL, "").orEmpty()
    }

    fun saveSyncUrl(url: String) {
        prefs?.edit()
            ?.putString(KEY_SYNC_URL, url.trim())
            ?.apply()
    }
}
