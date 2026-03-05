package com.hardreminder.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.hardreminder.R
import com.hardreminder.data.AppSettings
import com.hardreminder.data.AppSettings.colorPalette
import com.hardreminder.data.AppSettings.themeMode

object ThemeHelper {

    fun applyGlobalTheme(context: Context) {
        when (context.themeMode) {
            AppSettings.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            AppSettings.THEME_DARK, AppSettings.THEME_AMOLED -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            AppSettings.THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun applyActivityTheme(activity: Activity) {
        val mode = activity.themeMode
        val palette = activity.colorPalette
        val isDark = when (mode) {
            AppSettings.THEME_DARK, AppSettings.THEME_AMOLED -> true
            AppSettings.THEME_SYSTEM -> {
                val nightFlag = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightFlag == Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }
        val isAmoled = mode == AppSettings.THEME_AMOLED

        val themeRes = resolveTheme(palette, isDark, isAmoled)
        activity.setTheme(themeRes)
    }

    private fun resolveTheme(palette: Int, isDark: Boolean, isAmoled: Boolean): Int {
        return when (palette) {
            AppSettings.PALETTE_TONAL -> when {
                isAmoled -> R.style.Theme_HardReminder_Tonal_Amoled
                isDark -> R.style.Theme_HardReminder_Tonal_Dark
                else -> R.style.Theme_HardReminder_Tonal
            }
            AppSettings.PALETTE_VIBRANT -> when {
                isAmoled -> R.style.Theme_HardReminder_Vibrant_Amoled
                isDark -> R.style.Theme_HardReminder_Vibrant_Dark
                else -> R.style.Theme_HardReminder_Vibrant
            }
            AppSettings.PALETTE_EXPRESSIVE -> when {
                isAmoled -> R.style.Theme_HardReminder_Expressive_Amoled
                isDark -> R.style.Theme_HardReminder_Expressive_Dark
                else -> R.style.Theme_HardReminder_Expressive
            }
            AppSettings.PALETTE_MONO -> when {
                isAmoled -> R.style.Theme_HardReminder_Mono_Amoled
                isDark -> R.style.Theme_HardReminder_Mono_Dark
                else -> R.style.Theme_HardReminder_Mono
            }
            else -> when {
                isAmoled -> R.style.Theme_HardReminder_Amoled
                isDark -> R.style.Theme_HardReminder_Dark
                else -> R.style.Theme_HardReminder
            }
        }
    }
}
