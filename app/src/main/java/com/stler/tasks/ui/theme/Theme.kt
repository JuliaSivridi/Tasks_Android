package com.stler.tasks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerGrayLight,
    onPrimaryContainer = OnSecondaryContainerGray,
    secondary = Secondary,
    onSecondary = Foreground,
    secondaryContainer = SecondaryContainerGray,   // neutral gray — no more pale yellow tint
    onSecondaryContainer = OnSecondaryContainerGray,
    background = Background,
    onBackground = Foreground,
    surface = Surface,
    onSurface = Foreground,
    surfaceVariant = Secondary,
    onSurfaceVariant = MutedForeground,
    outline = Border,
    outlineVariant = Border,
    error = Destructive,
    onError = OnDestructive,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Foreground,
    inverseOnSurface = Background,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerGrayDark,
    onPrimaryContainer = OnPrimaryContainerGrayDark,
    secondary = SecondaryDark,
    onSecondary = ForegroundDark,
    secondaryContainer = PrimaryContainerGrayDark,   // #515151 — visible on dark surfaces
    onSecondaryContainer = OnPrimaryContainerGrayDark, // #f2f2f2
    background = BackgroundDark,
    onBackground = ForegroundDark,
    surface = SurfaceDark,
    onSurface = ForegroundDark,
    surfaceVariant = SecondaryDark,
    onSurfaceVariant = MutedForegroundDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = DestructiveDark,
    onError = OnDestructive,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = ForegroundDark,
    inverseOnSurface = BackgroundDark,
)

@Composable
fun TasksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
