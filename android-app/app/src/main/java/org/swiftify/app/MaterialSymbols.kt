package org.swiftify.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val MaterialSymbolsFont = FontFamily(Font(R.font.material_symbols_rounded))

object MaterialSymbols {
    const val Shuffle = "\ue043"
    const val Repeat = "\ue040"
    const val RepeatOne = "\ue041"
    const val SkipPrevious = "\ue045"
    const val SkipNext = "\ue044"
    const val PlayArrow = "\ue037"
    const val Pause = "\ue034"
    const val ArrowBack = "\ue5c4"
    const val KeyboardArrowDown = "\ue313"
    const val MoreVert = "\ue5d4"
    const val Favorite = "\ue87d"
    const val FavoriteBorder = "\ue87e"
    const val Bedtime = "\uef3e"
    const val VolumeUp = "\ue050"
    const val QueueMusic = "\ue03d"
    const val PlaylistAdd = "\ue03b"
}

@Composable
fun MaterialSymbol(
    symbol: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    val density = LocalDensity.current
    val fontSizeSp = with(density) { size.toSp() }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        this.role = Role.Image
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontFamily = MaterialSymbolsFont,
            fontSize = fontSizeSp,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeight = fontSizeSp
            ),
            color = tint,
            textAlign = TextAlign.Center
        )
    }
}
