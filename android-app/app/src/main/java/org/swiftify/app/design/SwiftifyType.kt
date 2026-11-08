@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package org.swiftify.app.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.swiftify.app.R

/**
 * Google Sans Flex is a variable font. Its default instance is the plain face,
 * which is why simply bundling the file never produced the rounded look.
 *
 * Desktop Swiftify rounds only headings (`TitleText`), using ROND=100 and
 * wdth=110 with a heavy weight, while body copy stays on the plain face. Android
 * does the same by mapping the rounded axes only to the bold faces of this
 * family: body weights resolve to the default instance, headings pick these up.
 */
private fun roundedFont(weight: FontWeight): Font = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        weight,
        FontStyle.Normal,
        FontVariation.Setting("ROND", 100f),
        FontVariation.Setting("wdth", 110f)
    )
)

val GoogleSansFlexFamily = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.Normal),
    Font(R.font.google_sans_flex, FontWeight.Medium),
    Font(R.font.google_sans_flex, FontWeight.SemiBold),
    roundedFont(FontWeight.Bold),
    roundedFont(FontWeight.ExtraBold),
    roundedFont(FontWeight.Black)
)

object SwiftifyType {
    val Typography = Typography(
        displayLarge = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        ),
        displayMedium = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        ),
        displaySmall = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        ),
        titleLarge = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        ),
        titleMedium = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        ),
        titleSmall = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp
        ),
        bodySmall = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp
        ),
        labelLarge = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        ),
        labelMedium = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        ),
        labelSmall = TextStyle(
            fontFamily = GoogleSansFlexFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp
        )
    )
}
