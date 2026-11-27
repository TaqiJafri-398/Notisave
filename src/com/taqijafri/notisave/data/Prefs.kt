package com.taqijafri.notisave.data

import android.content.Context

/** Lightweight preferences: theme, retention, first-run flags. */
class Prefs(context: Context) {

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val RETENTION_30 = 30
        const val RETENTION_60 = 60
        const val RETENTION_90 = 90
        const val RETENTION_UNLIMITED = -1
    }

    private val sp = context.applicationContext
        .getSharedPreferences("notisave_prefs", Context.MODE_PRIVATE)

    var themeMode: Int
        get() = sp.getInt("theme_mode", THEME_SYSTEM)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    /** Days to keep history; -1 = unlimited. Default 90 days. */
    var retentionDays: Int
        get() = sp.getInt("retention_days", RETENTION_90)
        set(v) = sp.edit().putInt("retention_days", v).apply()

    var privacyShown: Boolean
        get() = sp.getBoolean("privacy_shown", false)
        set(v) = sp.edit().putBoolean("privacy_shown", v).apply()

    var capturePaused: Boolean
        get() = sp.getBoolean("capture_paused", false)
        set(v) = sp.edit().putBoolean("capture_paused", v).apply()

    var redactPreviews: Boolean
        get() = sp.getBoolean("redact_previews", false)
        set(v) = sp.edit().putBoolean("redact_previews", v).apply()

    var appLockEnabled: Boolean
        get() = sp.getBoolean("app_lock_enabled", false)
        set(v) = sp.edit().putBoolean("app_lock_enabled", v).apply()

    var lastUnlockAt: Long
        get() = sp.getLong("last_unlock_at", 0L)
        set(v) = sp.edit().putLong("last_unlock_at", v).apply()

    var lastUnlockBootCount: Int
        get() = sp.getInt("last_unlock_boot_count", -1)
        set(v) = sp.edit().putInt("last_unlock_boot_count", v).apply()

    /** Persisted SAF tree Uri of the WhatsApp folder chosen for status saving. */
    var statusFolderUri: String?
        get() = sp.getString("status_folder_uri", null)
        set(v) = sp.edit().putString("status_folder_uri", v).apply()
}
