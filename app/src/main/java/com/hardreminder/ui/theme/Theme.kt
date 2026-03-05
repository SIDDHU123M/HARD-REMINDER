package com.hardreminder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Define fallback colors (e.g. from the Ruby Red default palette)
private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFCE0A33),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDADA),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF40000A),
    secondary = androidx.compose.ui.graphics.Color(0xFF0058CB),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    background = androidx.compose.ui.graphics.Color(0xFFFFFBFF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF201A1B),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFBFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF201A1B),
)

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFFB3B4),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF680016),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF930022),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD5),
    secondary = androidx.compose.ui.graphics.Color(0xFFAEC6FF),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF002E69),
    background = androidx.compose.ui.graphics.Color(0xFF201A1A),
    onBackground = androidx.compose.ui.graphics.Color(0xFFECE0E0),
    surface = androidx.compose.ui.graphics.Color(0xFF2E2424),
    onSurface = androidx.compose.ui.graphics.Color(0xFFECE0E0),
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background = androidx.compose.ui.graphics.Color(0xFF000000),
    surface = androidx.compose.ui.graphics.Color(0xFF141414),
    onBackground = androidx.compose.ui.graphics.Color(0xFFECE0E0),
    onSurface = androidx.compose.ui.graphics.Color(0xFFECE0E0),
)

@Composable
fun HardReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                if (amoled) {
                    // Start with dynamic dark and just override the surface/background values 
                    dynamicDarkColorScheme(context).copy(
                         background = androidx.compose.ui.graphics.Color(0xFF000000),
                         surface = androidx.compose.ui.graphics.Color(0xFF141414)
                    )
                } else {
                    dynamicDarkColorScheme(context)
                }
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> {
            if (amoled) AmoledColorScheme else DarkColorScheme
        }
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
