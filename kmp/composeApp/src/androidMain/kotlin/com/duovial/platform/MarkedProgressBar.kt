package com.duovial.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duovial.theme.DuoVialNeonGreen

@Composable
fun MarkedProgressBar(
    progress: Float,
    boundaries: List<Float>,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val trackHeightDp = 4.dp
    val thumbRadiusDp = 9.dp
    val markerHeightDp = 14.dp
    val markerWidthDp = 2.5.dp
    val touchHeightDp = 40.dp

    val trackHeightPx = with(density) { trackHeightDp.toPx() }
    val thumbRadiusPx = with(density) { thumbRadiusDp.toPx() }
    val markerHalfHeightPx = with(density) { markerHeightDp.toPx() / 2f }
    val markerWidthPx = with(density) { markerWidthDp.toPx() }
    val cornerRadiusPx = trackHeightPx / 2f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeightDp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(newProgress)
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val delta = dragAmount / size.width
                        val newProgress = (progress + delta).coerceIn(0f, 1f)
                        onSeek(newProgress)
                    }
                )
            }
    ) {
        val trackY = size.height / 2f
        val trackTop = trackY - trackHeightPx / 2f
        val progressX = size.width * progress.coerceIn(0f, 1f)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.2f),
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeightPx),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )

        drawRoundRect(
            color = DuoVialNeonGreen,
            topLeft = Offset(0f, trackTop),
            size = Size(progressX, trackHeightPx),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )

        for (boundary in boundaries) {
            val bx = size.width * boundary.coerceIn(0f, 1f)
            if (bx > 0f && bx < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(bx, trackY - markerHalfHeightPx),
                    end = Offset(bx, trackY + markerHalfHeightPx),
                    strokeWidth = markerWidthPx
                )
            }
        }

        drawCircle(
            color = Color.White,
            radius = thumbRadiusPx,
            center = Offset(progressX, trackY)
        )
    }
}
