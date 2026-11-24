package com.notsonata.comixium.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.notsonata.comixium.R

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jetbrains_mono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

// Sizes mirror the site's --text-* scale (base 14px).
val ComixTypography = Typography().let { t ->
    Typography(
        titleLarge = t.titleLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = t.titleSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 15.sp),
        bodyLarge = t.bodyLarge.copy(fontFamily = Poppins, fontSize = 15.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = Poppins, fontSize = 14.sp),
        bodySmall = t.bodySmall.copy(fontFamily = Poppins, fontSize = 13.sp),
        labelLarge = t.labelLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
        labelSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.6.sp),
    )
}
