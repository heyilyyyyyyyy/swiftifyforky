package org.swiftify.app.design

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle

@Composable
fun SwiftifyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SwiftifyColors.DarkColorScheme,
        typography = SwiftifyType.Typography,
        shapes = SwiftifyShapes.MaterialShapes,
        content = {
            CompositionLocalProvider(
                LocalTextStyle provides TextStyle(fontFamily = GoogleSansFlexFamily)
            ) {
                content()
            }
        }
    )
}
