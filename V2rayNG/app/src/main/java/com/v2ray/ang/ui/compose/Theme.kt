package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val DefaultTypography = Typography()

private val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, weight = FontWeight.Normal),
    Font(R.font.vazirmatn_bold, weight = FontWeight.Bold),
)

private val VazirmatnTypography = DefaultTypography.run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = VazirmatnFontFamily),
        displayMedium = displayMedium.copy(fontFamily = VazirmatnFontFamily),
        displaySmall = displaySmall.copy(fontFamily = VazirmatnFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = VazirmatnFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = VazirmatnFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = VazirmatnFontFamily),
        titleLarge = titleLarge.copy(fontFamily = VazirmatnFontFamily),
        titleMedium = titleMedium.copy(fontFamily = VazirmatnFontFamily),
        titleSmall = titleSmall.copy(fontFamily = VazirmatnFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = VazirmatnFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = VazirmatnFontFamily),
        bodySmall = bodySmall.copy(fontFamily = VazirmatnFontFamily),
        labelLarge = labelLarge.copy(fontFamily = VazirmatnFontFamily),
        labelMedium = labelMedium.copy(fontFamily = VazirmatnFontFamily),
        labelSmall = labelSmall.copy(fontFamily = VazirmatnFontFamily),
    )
}

// PattNG palette (the fallback when dynamic color is off, which is the default): firouzeh turquoise-green from the
// green of the app icon, lapis-teal as the second color and saffron as the accent, on soft green-tinted surfaces.
private val LightColor = lightColorScheme(
    primary = Color(0xFF006B55), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8FE3C4), onPrimaryContainer = Color(0xFF00382B),
    secondary = Color(0xFF2F6B7A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBFE6F0), onSecondaryContainer = Color(0xFF001F27),
    tertiary = Color(0xFF8A5A00), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDA3), onTertiaryContainer = Color(0xFF2C1A00),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FBF8), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF7FBF8), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF), onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975), outlineVariant = Color(0xFFBFC9C4),
    inverseSurface = Color(0xFF2C322F), inverseOnSurface = Color(0xFFEDF2EE),
    inversePrimary = Color(0xFF6DDBB8), scrim = Color(0xFF000000), surfaceTint = Color(0xFF006B55),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F6F3),
    surfaceContainer = Color(0xFFEBF0ED), surfaceContainerHigh = Color(0xFFE5EAE7),
    surfaceContainerHighest = Color(0xFFDFE4E1),
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF6DDBB8), onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF0B5A47), onPrimaryContainer = Color(0xFFA8F0D8),
    secondary = Color(0xFF8FCFE0), onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF164F5C), onSecondaryContainer = Color(0xFFB8EAF6),
    tertiary = Color(0xFFFFC857), onTertiary = Color(0xFF432C00),
    tertiaryContainer = Color(0xFF5F4100), onTertiaryContainer = Color(0xFFFFDDA3),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1512), onBackground = Color(0xFFDDE4E0),
    surface = Color(0xFF0E1512), onSurface = Color(0xFFDDE4E0),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF89938F), outlineVariant = Color(0xFF3F4945),
    inverseSurface = Color(0xFFDDE4E0), inverseOnSurface = Color(0xFF2C322F),
    inversePrimary = Color(0xFF006B55), scrim = Color(0xFF000000), surfaceTint = Color(0xFF6DDBB8),
    surfaceContainerLowest = Color(0xFF090F0D), surfaceContainerLow = Color(0xFF161D1A),
    surfaceContainer = Color(0xFF1A211E), surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF2F3633),
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
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
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
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
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
    val usePersianFont = LocalConfiguration.current.locales[0].language == "fa"
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()
    // Read again whenever the screen resumes, so that the switch of the settings applies on return.
    var reduceMotion by remember { mutableStateOf(MotionPolicy.isReduced(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reduceMotion = MotionPolicy.isReduced(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        LocalAppSnackbar provides snackbarController,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (usePersianFont) VazirmatnTypography else DefaultTypography,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
