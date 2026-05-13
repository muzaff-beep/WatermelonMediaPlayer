// app/src/main/kotlin/com/watermelon/player/ui/theme/WatermelonPlayerTheme.kt
// Compose Material3 theme with Persian/RTL support. Manifesto Handover §4.

package com.watermelon.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

// Persian fonts loaded from assets/fonts/ per Handover §4
val VazirmatnFamily = FontFamily(
    Font("fonts/vazir.ttf", FontWeight.Normal),
    Font("fonts/vazir.ttf", FontWeight.Bold)
)
val YekanFamily = FontFamily(
    Font("fonts/yekan.ttf", FontWeight.Normal),
    Font("fonts/yekan.ttf", FontWeight.Bold)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF81C784),
    tertiary = Color(0xFFFFC107),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFCF6679),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF388E3C),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF66BB6A),
    tertiary = Color(0xFFFFA000),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFB00020),
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121)
)

/**
 * Top-level theme composable. Applies RTL layout for Persian, Material3 theming,
 * and sets the status bar color to match the background.
 */
@Composable
fun WatermelonPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isPersian: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val layoutDirection = if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr
    val context = LocalContext.current

    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = colorScheme.background.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
        window.decorView.layoutDirection = if (isPersian) android.view.View.LAYOUT_DIRECTION_RTL
        else android.view.View.LAYOUT_DIRECTION_LTR
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}