package app.wird.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.wird.R

/**
 * Arabic sacred text: Noto Sans Arabic (bundled variable font, OFL).
 * Weight rows are instantiated via FontVariation so the variable font serves
 * regular and medium cuts.
 */
val NotoArabic: FontFamily = FontFamily(
    Font(
        R.font.noto_sans_arabic,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.noto_sans_arabic,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.noto_sans_arabic,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/**
 * Baloo Bhaijaan 2 (bundled variable font, OFL) — the rounded Arabic display
 * face used for the "القرآن" masthead. Variable wght axis is 400..800.
 */
val BalooBhaijaan2: FontFamily = FontFamily(
    Font(
        R.font.baloo_bhaijaan_2,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.baloo_bhaijaan_2,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * Latin/UI type: Google Sans on Pixel via device-font lookup, silently falling
 * back to the platform default elsewhere (same chain Waqt ships).
 */
val WirdFontFamily: FontFamily = FontFamily(
    listOf("google-sans-flex", "google-sans-text", "google-sans").flatMap { family ->
        listOf(400, 500, 600, 700).map { weight ->
            Font(DeviceFontFamilyName(family), weight = FontWeight(weight))
        }
    },
)

private fun TextStyle.branded(): TextStyle = copy(fontFamily = WirdFontFamily)

fun wirdTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.branded(),
        displayMedium = base.displayMedium.branded(),
        displaySmall = base.displaySmall.branded(),
        headlineLarge = base.headlineLarge.branded(),
        headlineMedium = base.headlineMedium.branded(),
        headlineSmall = base.headlineSmall.branded(),
        titleLarge = base.titleLarge.branded(),
        titleMedium = base.titleMedium.branded(),
        titleSmall = base.titleSmall.branded(),
        bodyLarge = base.bodyLarge.branded(),
        bodyMedium = base.bodyMedium.branded(),
        bodySmall = base.bodySmall.branded(),
        labelLarge = base.labelLarge.branded(),
        labelMedium = base.labelMedium.branded(),
        labelSmall = base.labelSmall.branded(),
        displayLargeEmphasized = base.displayLargeEmphasized.branded(),
        displayMediumEmphasized = base.displayMediumEmphasized.branded(),
        displaySmallEmphasized = base.displaySmallEmphasized.branded(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.branded(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.branded(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.branded(),
        titleLargeEmphasized = base.titleLargeEmphasized.branded(),
        titleMediumEmphasized = base.titleMediumEmphasized.branded(),
        titleSmallEmphasized = base.titleSmallEmphasized.branded(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.branded(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.branded(),
        bodySmallEmphasized = base.bodySmallEmphasized.branded(),
        labelLargeEmphasized = base.labelLargeEmphasized.branded(),
        labelMediumEmphasized = base.labelMediumEmphasized.branded(),
        labelSmallEmphasized = base.labelSmallEmphasized.branded(),
    )
}
