package app.wird.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Wird's palette: a deep mosque-green seed (#106B50), gold tertiary reserved
 * for bookmarks/downloads-complete semantics, and a warm sepia reading surface.
 */

val WirdLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF106B50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA9F2D3),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4C6359),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DB),
    onSecondaryContainer = Color(0xFF082017),
    tertiary = Color(0xFF7C5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF271900),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF7),
    onBackground = Color(0xFF161D19),
    surface = Color(0xFFF5FBF7),
    onSurface = Color(0xFF161D19),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF3F4944),
    outline = Color(0xFF6F7973),
    outlineVariant = Color(0xFFBEC9C2),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2B322E),
    inverseOnSurface = Color(0xFFECF2ED),
    inversePrimary = Color(0xFF8DD6B7),
    surfaceDim = Color(0xFFD5DBD6),
    surfaceBright = Color(0xFFF5FBF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F0),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE3EAE4),
    surfaceContainerHighest = Color(0xFFDDE4DE),
)

val WirdDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8DD6B7),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA9F2D3),
    secondary = Color(0xFFB2CCBF),
    onSecondary = Color(0xFF1E352C),
    secondaryContainer = Color(0xFF344C42),
    onSecondaryContainer = Color(0xFFCEE9DB),
    tertiary = Color(0xFFE8C26C),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0A120E),
    onBackground = Color(0xFFDDE4DE),
    surface = Color(0xFF0A120E),
    onSurface = Color(0xFFDDE4DE),
    surfaceVariant = Color(0xFF3F4944),
    onSurfaceVariant = Color(0xFFBEC9C2),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF3F4944),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDDE4DE),
    inverseOnSurface = Color(0xFF2B322E),
    inversePrimary = Color(0xFF106B50),
    surfaceDim = Color(0xFF0A120E),
    surfaceBright = Color(0xFF303733),
    surfaceContainerLowest = Color(0xFF050B08),
    surfaceContainerLow = Color(0xFF161D19),
    surfaceContainer = Color(0xFF1A211D),
    surfaceContainerHigh = Color(0xFF252C28),
    surfaceContainerHighest = Color(0xFF303733),
)

/** True-black variant for night reading. */
fun ColorScheme.toAmoledBlack(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C1210),
    surfaceContainer = Color(0xFF121816),
    surfaceContainerHigh = Color(0xFF1B211E),
    surfaceContainerHighest = Color(0xFF242A27),
)

/** The reader's warm paper surface (light themes only). */
object SepiaSurface {
    val background = Color(0xFFF7F1E2)
    val onBackground = Color(0xFF2C2517)
    val muted = Color(0xFF77694E)
    val container = Color(0xFFEFE7D2)
    val selection = Color(0xFFE3EFDD)
}

/**
 * In AMOLED night the Arabic renders slightly warm, not pure white, to cut
 * halation at large sizes.
 */
val NightArabic = Color(0xFFE8E2D9)
