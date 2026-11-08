package org.swiftify.app.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.swiftify.app.R

val GoogleSansFlexFamily = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.Normal),
    Font(R.font.google_sans_flex, FontWeight.Medium),
    Font(R.font.google_sans_flex, FontWeight.SemiBold),
    Font(R.font.google_sans_flex, FontWeight.Bold),
    Font(R.font.google_sans_flex, FontWeight.ExtraBold),
    Font(R.font.google_sans_flex, FontWeight.Black)
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
