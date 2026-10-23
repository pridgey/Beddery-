package dev.pridgey.beddery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(
    primary = Color(0xFFBDC2FF),
    onPrimary = Color(0xFF1F2578),
    primaryContainer = Color(0xFF363C8F),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFC5C5DD),
    tertiary = Color(0xFFF4E7C5),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
)

private val Light = lightColorScheme(
    primary = Color(0xFF4E54A9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF060A64),
    secondary = Color(0xFF5C5D72),
    tertiary = Color(0xFF7A5A1F),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
)

@Composable
fun BedderyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
