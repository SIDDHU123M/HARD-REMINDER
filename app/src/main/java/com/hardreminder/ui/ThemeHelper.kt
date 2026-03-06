package com.hardreminder.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.hardreminder.R
import com.hardreminder.data.AppSettings
import com.hardreminder.data.AppSettings.themeMode
import com.hardreminder.data.AppSettings.useAmoledMode

object ThemeHelper {

    fun applyGlobalTheme(context: Context) {
        when (context.themeMode) {
            AppSettings.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            AppSettings.THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            AppSettings.THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun applyActivityTheme(activity: Activity) {
        val mode = activity.themeMode
        val isDark = when (mode) {
            AppSettings.THEME_DARK -> true
            AppSettings.THEME_SYSTEM -> {
                val nightFlag = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightFlag == Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }
        val isAmoled = isDark && activity.useAmoledMode

        val themeRes = when {
            isAmoled -> R.style.Theme_HardReminder_Amoled
            isDark -> R.style.Theme_HardReminder_Dark
            else -> R.style.Theme_HardReminder
        }
        activity.setTheme(themeRes)
    }
}
