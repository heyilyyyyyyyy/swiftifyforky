package org.swiftify.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStorage {
    private const val TAG = "SecureStorage"
    private const val SECURE_PREFS_NAME = "swiftify_secure_prefs"
    private const val STANDARD_PREFS_NAME = "swiftify_prefs"

    private const val KEY_LOGIN_BLOB = "login_blob"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"

    @Volatile
    private var securePrefs: SharedPreferences? = null

    fun getSecurePrefs(context: Context): SharedPreferences {
        return securePrefs ?: synchronized(this) {
            securePrefs ?: initSecurePrefs(context).also { securePrefs = it }
        }
    }

    private fun initSecurePrefs(context: Context): SharedPreferences {
        val standardPrefs = context.getSharedPreferences(STANDARD_PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encrypted = EncryptedSharedPreferences.create(
                context.applicationContext,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // Migrate credentials from unencrypted prefs if present
            migrateIfNecessary(standardPrefs, encrypted)
            Log.d(TAG, "EncryptedSharedPreferences initialized and active ✓")
            return encrypted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, fallback to standard", e)
            return standardPrefs
        }
    }

    private fun migrateIfNecessary(standard: SharedPreferences, secure: SharedPreferences) {
        val oldBlob = standard.getString(KEY_LOGIN_BLOB, null)
        val oldAccess = standard.getString(KEY_ACCESS_TOKEN, null)
        val oldRefresh = standard.getString(KEY_REFRESH_TOKEN, null)
        val oldExpires = standard.getLong(KEY_TOKEN_EXPIRES_AT, 0L)

        if (oldBlob != null || oldAccess != null || oldRefresh != null) {
            val editor = secure.edit()
            if (oldBlob != null) editor.putString(KEY_LOGIN_BLOB, oldBlob)
            if (oldAccess != null) editor.putString(KEY_ACCESS_TOKEN, oldAccess)
            if (oldRefresh != null) editor.putString(KEY_REFRESH_TOKEN, oldRefresh)
            if (oldExpires > 0) editor.putLong(KEY_TOKEN_EXPIRES_AT, oldExpires)
            editor.apply()

            // Remove sensitive secrets from standard unencrypted file
            standard.edit()
                .remove(KEY_LOGIN_BLOB)
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRES_AT)
                .apply()
            Log.d(TAG, "Successfully migrated credentials to EncryptedSharedPreferences")
        }
    }

    fun getLoginBlob(context: Context): String? =
        getSecurePrefs(context).getString(KEY_LOGIN_BLOB, null)

    fun setLoginBlob(context: Context, blob: String) {
        getSecurePrefs(context).edit().putString(KEY_LOGIN_BLOB, blob).apply()
    }

    fun getAccessToken(context: Context): String? =
        getSecurePrefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(context: Context): String? =
        getSecurePrefs(context).getString(KEY_REFRESH_TOKEN, null)

    fun getTokenExpiresAt(context: Context): Long =
        getSecurePrefs(context).getLong(KEY_TOKEN_EXPIRES_AT, 0L)

    fun saveTokens(context: Context, accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        val editor = getSecurePrefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_TOKEN_EXPIRES_AT, System.currentTimeMillis() + (expiresInSeconds - 60) * 1000)
        if (!refreshToken.isNullOrEmpty()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        editor.apply()
    }

    fun clearAuth(context: Context) {
        getSecurePrefs(context).edit()
            .remove(KEY_LOGIN_BLOB)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .apply()
        context.getSharedPreferences(STANDARD_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_LOGIN_BLOB)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .apply()
    }
}
