package app.wird.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.wird.data.ColorSource
import app.wird.data.DarkMode
import app.wird.data.UserSettings

@Composable
fun WirdTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    // Keep system-bar icon contrast in sync with the effective theme.
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(dark, activity) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
            navigationBarStyle = if (dark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
        )
    }

    val context = LocalContext.current
    val base = when {
        settings.colorSource == ColorSource.SYSTEM_DYNAMIC &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> WirdDarkScheme
        else -> WirdLightScheme
    }
    val scheme = if (dark && settings.amoledBlack) base.toAmoledBlack() else base

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = remember { wirdTypography() },
        content = content,
    )
}
