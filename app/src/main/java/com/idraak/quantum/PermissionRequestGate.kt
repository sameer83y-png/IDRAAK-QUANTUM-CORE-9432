package com.idraak.quantum

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Remembers runtime permission prompts locally so Idraak does not repeatedly ask. */
object PermissionRequestGate {
    private const val PREFS = "idraak_permission_request_history"

    @Synchronized
    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(
            context.applicationContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun tryMarkRequested(context: Context, permission: String): Boolean {
        if (isGranted(context, permission)) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "requested_" + permission
        if (prefs.getBoolean(key, false)) return false
        return prefs.edit().putBoolean(key, true).commit()
    }

    @Synchronized
    fun markResult(context: Context, permission: String, granted: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("requested_" + permission, true)
            .putBoolean("granted_" + permission, granted)
            .commit()
    }

    @Synchronized
    fun tryMarkSpecialSettingsOpened(context: Context, key: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val settingKey = "special_settings_opened_" + key
        if (prefs.getBoolean(settingKey, false)) return false
        return prefs.edit().putBoolean(settingKey, true).commit()
    }
}
