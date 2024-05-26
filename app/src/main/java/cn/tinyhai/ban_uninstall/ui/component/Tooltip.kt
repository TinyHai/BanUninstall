package cn.tinyhai.ban_uninstall.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

@Composable
private fun rememberTooltipPositionProvider(): PopupPositionProvider {
    val spacing = with(LocalDensity.current) { 4.dp.toPx().roundToInt() }
    return remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = anchorBounds.left + ((anchorBounds.width - popupContentSize.width) / 2)
                val y = anchorBounds.bottom + spacing
                return IntOffset(x, y)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipBoxWrapper(
    tooltipText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(),
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = ShapeDefaults.ExtraSmall,
            ) {
                Text(
                    text = tooltipText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(4.dp)
                )
            }
        },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        content()
    }
}
