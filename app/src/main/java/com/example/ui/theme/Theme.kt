package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun parseColorOrFallback(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun GceNoticeBoardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColorHex: String = "#0B3C5D",
    content: @Composable () -> Unit,
) {
    val primaryAccent = parseColorOrFallback(accentColorHex, GceBluePrimary)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryAccent,
            onPrimary = Color.White,
            primaryContainer = primaryAccent.copy(alpha = 0.35f),
            onPrimaryContainer = Color.White,
            secondary = GceGoldAccent,
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF4A3E00),
            onSecondaryContainer = GceGoldLight,
            background = SlateBackgroundDark,
            onBackground = Color(0xFFECEFF1),
            surface = SlateSurfaceDark,
            onSurface = Color(0xFFECEFF1),
            surfaceVariant = SlateSurfaceVariantDark,
            onSurfaceVariant = Color(0xFFCFD8DC),
            outline = SlateOutlineDark
        )
    } else {
        lightColorScheme(
            primary = primaryAccent,
            onPrimary = Color.White,
            primaryContainer = GceBlueLight,
            onPrimaryContainer = GceBlueDark,
            secondary = GceGoldAccent,
            onSecondary = Color.Black,
            secondaryContainer = GceGoldLight,
            onSecondaryContainer = Color(0xFF3E2723),
            background = SlateBackgroundLight,
            onBackground = Color(0xFF1E293B),
            surface = SlateSurfaceLight,
            onSurface = Color(0xFF1E293B),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            outline = SlateOutlineLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
