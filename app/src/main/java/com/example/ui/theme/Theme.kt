package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.AppThemeMode

private val DarkNavyColorScheme = darkColorScheme(
    primary = EnergyEmeraldBright,
    onPrimary = NavyVoid,
    primaryContainer = EnergyMintDark,
    onPrimaryContainer = EnergyMintSoft,
    secondary = SkyWeather,
    onSecondary = NavyVoid,
    secondaryContainer = SkyWeatherBgDark,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = ChargingAmber,
    onTertiary = NavyVoid,
    tertiaryContainer = ChargingAmberBgDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = NavyCanvas,
    onBackground = NavyTextPrimary,
    surface = NavySurface,
    onSurface = NavyTextPrimary,
    surfaceVariant = NavySurfaceElevated,
    onSurfaceVariant = NavyTextSecondary,
    outline = NavyBorder,
    outlineVariant = Color(0xFF1F2D4E),
    error = Color(0xFFF87171),
    onError = NavyVoid,
    errorContainer = AlertCoralBgDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val CleanWhiteColorScheme = lightColorScheme(
    primary = EnergyEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = EnergyMintSoft,
    onPrimaryContainer = EnergyMintDark,
    secondary = SkyWeather,
    onSecondary = Color.White,
    secondaryContainer = SkyWeatherBgLight,
    onSecondaryContainer = SkyWeatherBgDark,
    tertiary = ChargingAmber,
    onTertiary = Color.White,
    tertiaryContainer = ChargingAmberBgLight,
    onTertiaryContainer = ChargingAmberBgDark,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFCBD5E1),
    error = AlertCoral,
    onError = Color.White,
    errorContainer = AlertCoralBgLight,
    onErrorContainer = AlertCoralBgDark
)

val ElectricityShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ElectricityMonitorTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK_NAVY -> true
    }

    val colorScheme = if (isDark) DarkNavyColorScheme else CleanWhiteColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ElectricityTypography,
        shapes = ElectricityShapes,
        content = content
    )
}
