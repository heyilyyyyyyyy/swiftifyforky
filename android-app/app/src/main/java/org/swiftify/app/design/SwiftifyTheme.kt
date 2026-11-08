package org.swiftify.app.design

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle

@Composable
fun SwiftifyTheme(
    themeMode: SwiftifyThemeMode = SwiftifyThemeMode.SPOTIFY,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SwiftifyColors.colorSchemeFor(themeMode),
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
