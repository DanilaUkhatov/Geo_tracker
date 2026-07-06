package com.example.geotracker.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest

object AuthRepository {
    private const val TAG = "AuthRepository"
    private const val PREFS_NAME = "auth_repository"
    private const val KEY_ANDROID_ID_HASH = "android_id_hash"
    private const val KEY_IS_DEVICE_AUTHORIZED = "is_device_authorized"

    // Production mode: fill this set with corporate device ANDROID_ID values.
    // Empty set means dev mode: the current device is allowed automatically.
    private val allowedAndroidIds = emptySet<String>()

    private var prefs: SharedPreferences? = null
    private var androidId: String = ""
    private var isDeviceAuthorized: Boolean = false

    fun initialize(context: Context) {
        if (prefs != null) return

        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        androidId = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()

        isDeviceAuthorized = isAllowedDevice(androidId)
        prefs?.edit()
            ?.putString(KEY_ANDROID_ID_HASH, sha256(androidId))
            ?.putBoolean(KEY_IS_DEVICE_AUTHORIZED, isDeviceAuthorized)
            ?.apply()

        Log.d(
            TAG,
            "Device auth checked: androidIdHash=${sha256(androidId)}, authorized=$isDeviceAuthorized"
        )
    }

    fun isDeviceAuthorized(): Boolean = isDeviceAuthorized

    fun getAndroidId(): String = androidId

    fun getAndroidIdHash(): String = sha256(androidId)

    private fun isAllowedDevice(androidId: String): Boolean {
        if (androidId.isBlank()) return false
        return allowedAndroidIds.isEmpty() || androidId in allowedAndroidIds
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
