package org.swiftify.app.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object SwiftifyShapes {
    val ExtraSmall = RoundedCornerShape(8.dp) // List rows
    val Small = RoundedCornerShape(12.dp)     // Covers, cards
    val Medium = RoundedCornerShape(17.dp)    // Input fields, panels, container chips
    val Large = RoundedCornerShape(23.dp)     // Hero cards, bottom sheets
    val ExtraLarge = RoundedCornerShape(30.dp) // Large burst backgrounds
    val PlayerBar = RoundedCornerShape(20.dp) // Player bar distinct radius
    val Artwork = RoundedCornerShape(12.dp)
    val SmallArtwork = RoundedCornerShape(7.dp)
    val Full = CircleShape

    val MaterialShapes = Shapes(
        extraSmall = ExtraSmall,
        small = Small,
        medium = Medium,
        large = Large,
        extraLarge = ExtraLarge
    )
}
