package local.antenna.calculator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import local.antenna.calculator.state.AppTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF14645E), onPrimary = Color.White,
    secondary = Color(0xFF123C54), onSecondary = Color.White,
    primaryContainer = Color(0xFFD5ECE7), onPrimaryContainer = Color(0xFF073D38),
    secondaryContainer = Color(0xFFD8E7ED), onSecondaryContainer = Color(0xFF123C54),
    background = Color(0xFFF4F7FA), onBackground = Color(0xFF152B36),
    surface = Color(0xFFF4F7FA), onSurface = Color(0xFF152B36),
    surfaceVariant = Color(0xFFE3ECEF), onSurfaceVariant = Color(0xFF40555F),
    surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFEAF0F4),
    outline = Color(0xFF657B84), outlineVariant = Color(0xFFC4D4DB),
    error = Color(0xFFAB2427),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD3C6), onPrimary = Color(0xFF003731),
    secondary = Color(0xFFA7CEE4), onSecondary = Color(0xFF0A3348),
    primaryContainer = Color(0xFF164E48), onPrimaryContainer = Color(0xFFBCF0E5),
    secondaryContainer = Color(0xFF24495D), onSecondaryContainer = Color(0xFFD4ECFA),
    background = Color(0xFF101D26), onBackground = Color(0xFFDFEAF0),
    surface = Color(0xFF101D26), onSurface = Color(0xFFDFEAF0),
    surfaceVariant = Color(0xFF2B3E48), onSurfaceVariant = Color(0xFFBDCDD6),
    surfaceContainerLow = Color(0xFF172932), surfaceContainer = Color(0xFF1B303C),
    outline = Color(0xFF8BA2AC), outlineVariant = Color(0xFF3F535D),
)

@Composable
fun CalculatorTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val dark = when (theme) { AppTheme.SYSTEM -> isSystemInDarkTheme(); AppTheme.LIGHT -> false; AppTheme.DARK -> true }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
