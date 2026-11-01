package kr.personal.sealion.one.launcher.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kr.personal.sealion.one.launcher.data.LauncherSettings

@Composable
fun LauncherTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val accent = Color(settings.accent.argb)
    val scheme = if (settings.darkMode) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            secondary = accent.copy(alpha = 0.7f),
            background = Color(0xFF07090D),
            onBackground = Color(0xFFE9EEF5),
            surface = Color(0xFF12161D),
            onSurface = Color(0xFFE9EEF5),
            surfaceVariant = Color(0xFF1B212B),
            onSurfaceVariant = Color(0xFF9AA6B5),
            outline = Color(0xFF2B3340),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = accent.copy(alpha = 0.7f),
            background = Color(0xFFF1F3F6),
            onBackground = Color(0xFF10141A),
            surface = Color.White,
            onSurface = Color(0xFF10141A),
            surfaceVariant = Color(0xFFE6EAF0),
            onSurfaceVariant = Color(0xFF55606E),
            outline = Color(0xFFCCD3DC),
        )
    }
    MaterialTheme(colorScheme = scheme) {
        // No Surface wraps the launcher, so set the default text/icon colour explicitly.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
