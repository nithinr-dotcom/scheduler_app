package com.wac.worktrack.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.wac.worktrack.core.Prefs

val Good = Color(0xFF2E9E5B)
val Warn = Color(0xFFE08A00)
val Bad = Color(0xFFD64545)

fun scoreColor(score: Int?): Color = when {
    score == null -> Color.Gray
    score >= 80 -> Good
    score >= 50 -> Warn
    else -> Bad
}

/** Current epoch day; refreshed when the app resumes so screens roll over at midnight. */
val LocalToday = compositionLocalOf { 0L }

/** Bumped on every resume so read-only Google data is re-fetched. */
val LocalResumeTick = compositionLocalOf { 0 }

/** Accent colours offered when wallpaper colours are off (or before Android 12). */
val Accents = listOf(
    "Indigo" to Color(0xFF4F5BD5),
    "Teal" to Color(0xFF00897B),
    "Green" to Color(0xFF2E9E5B),
    "Orange" to Color(0xFFE5732E),
    "Rose" to Color(0xFFD6457A),
    "Purple" to Color(0xFF7E57C2),
)

/** Theme choices, observable so Settings changes apply instantly. */
object ThemeState {
    var mode by mutableStateOf("system")
    var dynamic by mutableStateOf(true)
    var accent by mutableIntStateOf(0)

    fun load(context: Context) {
        val p = Prefs(context)
        mode = p.themeMode
        dynamic = p.dynamicColor
        accent = p.accent
    }

    fun save(context: Context) {
        val p = Prefs(context)
        p.themeMode = mode
        p.dynamicColor = dynamic
        p.accent = accent
    }
}

@Composable
fun WorkTrackTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeState.mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val accent = Accents[ThemeState.accent.coerceIn(Accents.indices)].second
    val scheme = when {
        ThemeState.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = accent.lighten(), secondary = accent.lighten(), tertiary = accent.lighten())
        else -> lightColorScheme(primary = accent, secondary = accent, tertiary = accent)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Lighter shade so the accent stays readable on dark backgrounds. */
private fun Color.lighten() = Color(
    red = red + (1 - red) * 0.35f,
    green = green + (1 - green) * 0.35f,
    blue = blue + (1 - blue) * 0.35f,
)
