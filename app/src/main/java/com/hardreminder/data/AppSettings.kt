package com.hardreminder.data

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    private const val PREFS_NAME = "hard_reminder_prefs"

    // Keys
    private const val KEY_DEFAULT_SOUND = "default_sound"
    private const val KEY_DEFAULT_VIBRATION = "default_vibration"
    private const val KEY_USE_24H_FORMAT = "use_24h_format"
    private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    private const val KEY_AUTO_DELETE_FIRED = "auto_delete_fired"
    private const val KEY_DEFAULT_PRIOR_MINUTES = "default_prior_minutes"
    private const val KEY_START_ON_BOOT = "start_on_boot"
    private const val KEY_SHOW_ONGOING_NOTIF = "show_ongoing_notification"
    private const val KEY_FLASH_SCREEN = "flash_screen_on_alarm"
    private const val KEY_DISMISS_ON_UNLOCK = "dismiss_on_unlock"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_USE_AMOLED_MODE = "use_amoled_mode"
    private const val KEY_USE_MATERIAL_YOU = "use_material_you"

    // Theme modes
    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_SYSTEM = 3

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Default sound for new reminders
    var Context.defaultSound: Boolean
        get() = prefs(this).getBoolean(KEY_DEFAULT_SOUND, true)
        set(value) = prefs(this).edit().putBoolean(KEY_DEFAULT_SOUND, value).apply()

    // Default vibration for new reminders
    var Context.defaultVibration: Boolean
        get() = prefs(this).getBoolean(KEY_DEFAULT_VIBRATION, true)
        set(value) = prefs(this).edit().putBoolean(KEY_DEFAULT_VIBRATION, value).apply()

    // 24-hour time format
    var Context.use24HourFormat: Boolean
        get() = prefs(this).getBoolean(KEY_USE_24H_FORMAT, false)
        set(value) = prefs(this).edit().putBoolean(KEY_USE_24H_FORMAT, value).apply()

    // Snooze duration in minutes
    var Context.snoozeMinutes: Int
        get() = prefs(this).getInt(KEY_SNOOZE_MINUTES, 5)
        set(value) = prefs(this).edit().putInt(KEY_SNOOZE_MINUTES, value).apply()

    // Auto-delete one-time reminders after they fire
    var Context.autoDeleteFired: Boolean
        get() = prefs(this).getBoolean(KEY_AUTO_DELETE_FIRED, false)
        set(value) = prefs(this).edit().putBoolean(KEY_AUTO_DELETE_FIRED, value).apply()

    // Default prior notification time in minutes (0 = disabled)
    var Context.defaultPriorMinutes: Int
        get() = prefs(this).getInt(KEY_DEFAULT_PRIOR_MINUTES, 0)
        set(value) = prefs(this).edit().putInt(KEY_DEFAULT_PRIOR_MINUTES, value).apply()

    // Start foreground service on boot
    var Context.startOnBoot: Boolean
        get() = prefs(this).getBoolean(KEY_START_ON_BOOT, true)
        set(value) = prefs(this).edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    // Show ongoing notification (foreground service)
    var Context.showOngoingNotification: Boolean
        get() = prefs(this).getBoolean(KEY_SHOW_ONGOING_NOTIF, true)
        set(value) = prefs(this).edit().putBoolean(KEY_SHOW_ONGOING_NOTIF, value).apply()

    // Flash/turn on screen when alarm fires
    var Context.flashScreenOnAlarm: Boolean
        get() = prefs(this).getBoolean(KEY_FLASH_SCREEN, true)
        set(value) = prefs(this).edit().putBoolean(KEY_FLASH_SCREEN, value).apply()

    // App theme mode (0=Light, 1=Dark, 3=System)
    var Context.themeMode: Int
        get() = prefs(this).getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) = prefs(this).edit().putInt(KEY_THEME_MODE, value).apply()

    // Use AMOLED mode when dark theme is active
    var Context.useAmoledMode: Boolean
        get() = prefs(this).getBoolean(KEY_USE_AMOLED_MODE, false)
        set(value) = prefs(this).edit().putBoolean(KEY_USE_AMOLED_MODE, value).apply()

    // Use Material You dynamic colors
    var Context.useMaterialYou: Boolean
        get() = prefs(this).getBoolean(KEY_USE_MATERIAL_YOU, true)
        set(value) = prefs(this).edit().putBoolean(KEY_USE_MATERIAL_YOU, value).apply()

    // Helper for composing UI based on theme mode
    val Context.isDarkAppTheme: Boolean
        get() = when (themeMode) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

    val THEME_OPTIONS = listOf(
        THEME_LIGHT to "Light",
        THEME_DARK to "Dark",
        THEME_SYSTEM to "Follow System"
    )

    // Prior notification time options in minutes
    val PRIOR_OPTIONS = listOf(
        0 to "No prior notification",
        5 to "5 minutes before",
        10 to "10 minutes before",
        15 to "15 minutes before",
        30 to "30 minutes before",
        60 to "1 hour before",
        120 to "2 hours before",
        360 to "6 hours before",
        720 to "12 hours before",
        1440 to "1 day before"
    )

    val SNOOZE_OPTIONS = listOf(
        1 to "1 minute",
        2 to "2 minutes",
        5 to "5 minutes",
        10 to "10 minutes",
        15 to "15 minutes",
        30 to "30 minutes",
        60 to "1 hour"
    )

    fun getPriorLabel(minutes: Int): String {
        return PRIOR_OPTIONS.firstOrNull { it.first == minutes }?.second
            ?: if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m before"
            else "$minutes min before"
    }
}
