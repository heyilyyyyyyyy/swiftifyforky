package org.swiftify.app.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun SwiftifyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SwiftifyColors.DarkColorScheme,
        typography = SwiftifyType.Typography,
        shapes = SwiftifyShapes.MaterialShapes,
        content = content
    )
}
