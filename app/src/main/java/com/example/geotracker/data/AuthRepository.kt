package com.example.geotracker.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AuthRepository {
    private const val TAG = "AuthRepository"
    private const val PREFS_NAME = "auth_repository"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_PASSWORD_SALT = "password_salt"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val DEFAULT_USERNAME = "admin"
    private const val DEFAULT_PASSWORD = "admin123"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val HASH_BITS = 256

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs != null) return

        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!hasStoredCredentials()) {
            saveCredentials(DEFAULT_USERNAME, DEFAULT_PASSWORD)
            Log.d(TAG, "Default local user created: username=$DEFAULT_USERNAME")
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs?.getBoolean(KEY_IS_LOGGED_IN, false) == true
    }

    fun login(username: String, password: String): Boolean {
        val savedUsername = prefs?.getString(KEY_USERNAME, null) ?: return false
        val savedHash = prefs?.getString(KEY_PASSWORD_HASH, null) ?: return false
        val savedSalt = prefs?.getString(KEY_PASSWORD_SALT, null) ?: return false

        if (username != savedUsername) {
            Log.d(TAG, "Login failed: wrong username")
            return false
        }

        val saltBytes = Base64.decode(savedSalt, Base64.NO_WRAP)
        val passwordHash = hashPassword(password, saltBytes)
        val success = MessageDigest.isEqual(
            passwordHash.toByteArray(Charsets.UTF_8),
            savedHash.toByteArray(Charsets.UTF_8)
        )

        if (success) {
            prefs?.edit()?.putBoolean(KEY_IS_LOGGED_IN, true)?.apply()
            Log.d(TAG, "Login success: username=$username")
        } else {
            Log.d(TAG, "Login failed: wrong password")
        }

        return success
    }

    fun logout() {
        prefs?.edit()?.putBoolean(KEY_IS_LOGGED_IN, false)?.apply()
        Log.d(TAG, "User logged out")
    }

    private fun hasStoredCredentials(): Boolean {
        return prefs?.contains(KEY_USERNAME) == true &&
            prefs?.contains(KEY_PASSWORD_HASH) == true &&
            prefs?.contains(KEY_PASSWORD_SALT) == true
    }

    private fun saveCredentials(username: String, password: String) {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)

        prefs?.edit()
            ?.putString(KEY_USERNAME, username)
            ?.putString(KEY_PASSWORD_SALT, Base64.encodeToString(saltBytes, Base64.NO_WRAP))
            ?.putString(KEY_PASSWORD_HASH, hashPassword(password, saltBytes))
            ?.putBoolean(KEY_IS_LOGGED_IN, false)
            ?.apply()
    }

    private fun hashPassword(password: String, saltBytes: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, PBKDF2_ITERATIONS, HASH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }
}
