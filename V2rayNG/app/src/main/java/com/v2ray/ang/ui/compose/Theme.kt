package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Fallback palette (Android < 12, or dynamic color turned off): violet primary container on a
// warm near-black / lavender-white surface with a lime accent. Hand-picked from the visual
// direction of the ZedSecure screenshots; no code or assets are copied from that project.
private val LightColor = lightColorScheme(
    primary = Color(0xFF4F3FD8), // Violet
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFF8779F8), // Periwinkle (connection card)
    onPrimaryContainer = Color(0xFF14055E), // Deep Indigo
    secondary = Color(0xFF4C6700), // Olive Lime
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFC7F24E), // Lime
    onSecondaryContainer = Color(0xFF161E00), // Near Black Olive
    tertiary = Color(0xFF9C3A62), // Raspberry
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFFFD9E2), // Pale Pink
    onTertiaryContainer = Color(0xFF3E001D), // Dark Wine
    error = Color(0xFFBA1A1A), // Red
    errorContainer = Color(0xFFFFDAD6), // Light Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF410002), // Dark Red
    background = Color(0xFFFDFBFF), // Lavender White
    onBackground = Color(0xFF1B1B20), // Near Black
    surface = Color(0xFFFDFBFF), // Lavender White
    onSurface = Color(0xFF1B1B20), // Near Black
    surfaceVariant = Color(0xFFE5E0EE), // Light Lavender Gray
    onSurfaceVariant = Color(0xFF47464F), // Dark Gray
    outline = Color(0xFF787680), // Medium Gray
    outlineVariant = Color(0xFFC9C5D0), // Light Gray
    inverseSurface = Color(0xFF303034), // Dark Gray
    inverseOnSurface = Color(0xFFF2EFF5), // Very Light Gray
    inversePrimary = Color(0xFFC9BFFF), // Lavender
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF4F3FD8), // Violet
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF8F5FC), // Very Light Lavender
    surfaceContainer = Color(0xFFF2EFF7), // Light Lavender
    surfaceContainerHigh = Color(0xFFECE9F1), // Lavender Gray
    surfaceContainerHighest = Color(0xFFE6E3EB), // Lavender Gray
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFFC9BFFF), // Lavender
    onPrimary = Color(0xFF2A1C8A), // Deep Indigo
    primaryContainer = Color(0xFF4636C8), // Indigo (connection card)
    onPrimaryContainer = Color(0xFFE6DFFF), // Pale Lavender
    secondary = Color(0xFFC7F24E), // Lime
    onSecondary = Color(0xFF263500), // Dark Olive
    secondaryContainer = Color(0xFF3A4D00), // Olive
    onSecondaryContainer = Color(0xFFDDFF86), // Pale Lime
    tertiary = Color(0xFFFFB0C8), // Pink
    onTertiary = Color(0xFF5E1133), // Dark Wine
    tertiaryContainer = Color(0xFF7B2949), // Wine
    onTertiaryContainer = Color(0xFFFFD9E2), // Pale Pink
    error = Color(0xFFFFB4AB), // Light Red
    errorContainer = Color(0xFF93000A), // Dark Red
    onError = Color(0xFF690005), // Deep Red
    onErrorContainer = Color(0xFFFFDAD6), // Light Red
    background = Color(0xFF11130B), // Olive Black
    onBackground = Color(0xFFE4E3D6), // Warm Light Gray
    surface = Color(0xFF11130B), // Olive Black
    onSurface = Color(0xFFE4E3D6), // Warm Light Gray
    surfaceVariant = Color(0xFF45483A), // Olive Gray
    onSurfaceVariant = Color(0xFFC6C8B6), // Warm Light Gray
    outline = Color(0xFF909285), // Olive Medium Gray
    outlineVariant = Color(0xFF45483A), // Olive Gray
    inverseSurface = Color(0xFFE4E3D6), // Warm Light Gray
    inverseOnSurface = Color(0xFF2F3126), // Dark Olive Gray
    inversePrimary = Color(0xFF4F3FD8), // Violet
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFFC9BFFF), // Lavender
    surfaceContainerLowest = Color(0xFF0C0E06), // Near Black
    surfaceContainerLow = Color(0xFF191B12), // Dark Olive
    surfaceContainer = Color(0xFF1D1F16), // Dark Olive
    surfaceContainerHigh = Color(0xFF282A20), // Dark Olive
    surfaceContainerHighest = Color(0xFF33352B), // Dark Olive
)

// Semantic Colors
val colorPing = Color(0xFF009966) // Green
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = Color(0xFFf97910) // Orange
val colorFabActive = Color(0xFFf97910) // Orange
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
