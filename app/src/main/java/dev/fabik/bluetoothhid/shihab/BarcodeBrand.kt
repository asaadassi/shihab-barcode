package dev.fabik.bluetoothhid.shihab

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.shihab.shared.ui.ShihabColor
import app.shihab.shared.ui.ShihabPalette
import dev.fabik.bluetoothhid.R

/**
 * Barcode's look from the Shihab shared library: the colour tokens (ShihabPalette, from the brand colours in
 * res/values/colors.xml) mapped onto Material 3, and Almarai (the corrected Regular and Bold the library copies
 * from Shihab.Common) for every text style.
 */
object BarcodeBrand {
    fun palette(ctx: Context, dark: Boolean): ShihabPalette = ShihabPalette.forContext(
        ctx,
        ShihabPalette.Brand(
            primary = ctx.getColor(R.color.brand_primary),
            onPrimary = ctx.getColor(R.color.brand_on_primary),
            accent = ctx.getColor(R.color.brand_accent),
            background = ctx.getColor(R.color.brand_background),
            text = ctx.getColor(R.color.brand_text),
        ),
        dark,
    )

    fun colorScheme(p: ShihabPalette): ColorScheme {
        val primary = Color(p.primary)
        val onPrimary = Color(p.onPrimary)
        val bg = Color(p.bg)
        val surface = Color(p.surface)
        val surfaceAlt = Color(p.surfaceAlt)
        val text = Color(p.text)
        val muted = Color(p.muted)
        val stroke = Color(p.stroke)
        val accent = Color(p.accent)
        val container = Color(ShihabColor.mix(p.bg, p.primary, if (p.isDark) 0.22 else 0.14))
        val danger = Color(p.danger)
        val onDanger = Color(if (p.isDark) p.bg else 0xFFFFFFFF.toInt())
        return if (p.isDark) darkColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = text,
            secondary = accent, onSecondary = onPrimary, secondaryContainer = surfaceAlt, onSecondaryContainer = text,
            tertiary = accent, onTertiary = onPrimary, tertiaryContainer = surfaceAlt, onTertiaryContainer = text,
            background = bg, onBackground = text, surface = bg, onSurface = text,
            surfaceVariant = surfaceAlt, onSurfaceVariant = muted, surfaceTint = primary,
            inverseSurface = text, inverseOnSurface = bg, inversePrimary = primary,
            error = danger, onError = onDanger, outline = stroke, outlineVariant = stroke,
            surfaceBright = surfaceAlt, surfaceDim = bg, surfaceContainerLowest = bg, surfaceContainerLow = surface,
            surfaceContainer = surface, surfaceContainerHigh = surfaceAlt, surfaceContainerHighest = surfaceAlt,
        ) else lightColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = text,
            secondary = accent, onSecondary = onPrimary, secondaryContainer = surfaceAlt, onSecondaryContainer = text,
            tertiary = accent, onTertiary = onPrimary, tertiaryContainer = surfaceAlt, onTertiaryContainer = text,
            background = bg, onBackground = text, surface = bg, onSurface = text,
            surfaceVariant = surfaceAlt, onSurfaceVariant = muted, surfaceTint = primary,
            inverseSurface = text, inverseOnSurface = bg, inversePrimary = primary,
            error = danger, onError = onDanger, outline = stroke, outlineVariant = stroke,
            surfaceBright = bg, surfaceDim = surfaceAlt, surfaceContainerLowest = bg, surfaceContainerLow = surface,
            surfaceContainer = surface, surfaceContainerHigh = surfaceAlt, surfaceContainerHighest = surfaceAlt,
        )
    }

    /** Material's text styles in Almarai; no letter spacing, which would break the joining of Arabic letters. */
    fun typography(ctx: Context): Typography {
        val assets = ctx.applicationContext.assets
        val almarai = FontFamily(
            Font("shihab/fonts/Almarai-Regular.ttf", assets, FontWeight.Normal),
            Font("shihab/fonts/Almarai-Regular.ttf", assets, FontWeight.Medium),
            Font("shihab/fonts/Almarai-Bold.ttf", assets, FontWeight.SemiBold),
            Font("shihab/fonts/Almarai-Bold.ttf", assets, FontWeight.Bold),
        )
        fun TextStyle.almarai() = copy(fontFamily = almarai, letterSpacing = 0.sp)
        return Typography().run {
            Typography(
                displayLarge = displayLarge.almarai(), displayMedium = displayMedium.almarai(),
                displaySmall = displaySmall.almarai(), headlineLarge = headlineLarge.almarai(),
                headlineMedium = headlineMedium.almarai(), headlineSmall = headlineSmall.almarai(),
                titleLarge = titleLarge.almarai(), titleMedium = titleMedium.almarai(),
                titleSmall = titleSmall.almarai(), bodyLarge = bodyLarge.almarai(),
                bodyMedium = bodyMedium.almarai(), bodySmall = bodySmall.almarai(),
                labelLarge = labelLarge.almarai(), labelMedium = labelMedium.almarai(),
                labelSmall = labelSmall.almarai(),
            )
        }
    }
}
