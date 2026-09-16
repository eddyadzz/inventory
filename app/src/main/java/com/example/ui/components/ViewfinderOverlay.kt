package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ScannerOrange

@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val boxWidth = minOf(canvasWidth * 0.82f, 340.dp.toPx())
        val boxHeight = minOf(canvasHeight * 0.32f, 220.dp.toPx())

        val left = (canvasWidth - boxWidth) / 2f
        val top = (canvasHeight - boxHeight) / 2f - 40.dp.toPx()
        val right = left + boxWidth
        val bottom = top + boxHeight

        // Dim outer area
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = Color.Black.copy(alpha = 0.55f)
                style = PaintingStyle.Fill
            }
            // Draw 4 rectangles around viewfinder
            // Top
            canvas.drawRect(0f, 0f, canvasWidth, top, paint)
            // Bottom
            canvas.drawRect(0f, bottom, canvasWidth, canvasHeight, paint)
            // Left
            canvas.drawRect(0f, top, left, bottom, paint)
            // Right
            canvas.drawRect(right, top, canvasWidth, bottom, paint)
        }

        // Viewfinder rounded border
        drawRoundRect(
            color = Color.White.copy(alpha = 0.3f),
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        // Corner accents (industrial scanner reticle)
        val cornerLength = 28.dp.toPx()
        val cornerStroke = 4.dp.toPx()
        val cornerColor = ScannerOrange

        // Top-Left
        drawLine(cornerColor, Offset(left - 2, top + cornerLength), Offset(left - 2, top), cornerStroke)
        drawLine(cornerColor, Offset(left - 2, top), Offset(left + cornerLength, top), cornerStroke)

        // Top-Right
        drawLine(cornerColor, Offset(right + 2, top + cornerLength), Offset(right + 2, top), cornerStroke)
        drawLine(cornerColor, Offset(right + 2, top), Offset(right - cornerLength, top), cornerStroke)

        // Bottom-Left
        drawLine(cornerColor, Offset(left - 2, bottom - cornerLength), Offset(left - 2, bottom), cornerStroke)
        drawLine(cornerColor, Offset(left - 2, bottom), Offset(left + cornerLength, bottom), cornerStroke)

        // Bottom-Right
        drawLine(cornerColor, Offset(right + 2, bottom - cornerLength), Offset(right + 2, bottom), cornerStroke)
        drawLine(cornerColor, Offset(right + 2, bottom), Offset(right - cornerLength, bottom), cornerStroke)

        // Animated red/orange laser scan line
        val currentLaserY = top + 12.dp.toPx() + (boxHeight - 24.dp.toPx()) * laserPosition
        val laserBrush = Brush.horizontalGradient(
            colors = listOf(
                ScannerOrange.copy(alpha = 0.05f),
                ScannerOrange,
                Color.White,
                ScannerOrange,
                ScannerOrange.copy(alpha = 0.05f)
            ),
            startX = left,
            endX = right
        )

        drawLine(
            brush = laserBrush,
            start = Offset(left + 8.dp.toPx(), currentLaserY),
            end = Offset(right - 8.dp.toPx(), currentLaserY),
            strokeWidth = 3.dp.toPx()
        )
    }
}
