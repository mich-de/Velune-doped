package com.nikhil.yt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.nikhil.yt.LocalPlayerConnection
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VuMeter(
    modifier: Modifier = Modifier,
    isPlayerExpanded: Boolean = true,
    cornerRadius: Float = 16f,
    isWide: Boolean = false,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val service = playerConnection.service
    val isPlaying by playerConnection.isPlaying.collectAsState()

    var leftLevel by remember { mutableStateOf(0f) }
    var rightLevel by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlaying, isPlayerExpanded) {
        if (isPlaying && isPlayerExpanded) {
            while (true) {
                val rawL = service.amplitudeProcessor.latestAmplitudeL
                val rawR = service.amplitudeProcessor.latestAmplitudeR
                val currentVol = service.player.volume
                val jitterL = if (rawL > 0.05f) (Math.random().toFloat() - 0.5f) * 0.02f else 0f
                val jitterR = if (rawR > 0.05f) (Math.random().toFloat() - 0.5f) * 0.02f else 0f

                leftLevel = (rawL * currentVol + jitterL).coerceIn(0f, 1f)
                rightLevel = (rawR * currentVol + jitterR).coerceIn(0f, 1f)

                delay(16)
            }
        } else {
            while (leftLevel > 0f || rightLevel > 0f) {
                leftLevel = (leftLevel * 0.8f - 0.02f).coerceIn(0f, 1f)
                rightLevel = (rightLevel * 0.8f - 0.02f).coerceIn(0f, 1f)
                delay(16)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * (if (isWide) 0.95f else 0.58f)
            val needleLen = h * (if (isWide) 0.78f else 0.44f)

            // Real-time transient beat-synchronized ambery radial light glow
            val timeSinceBeat = System.currentTimeMillis() - service.amplitudeProcessor.lastBeatTime
            val lightIntensity = (1f - (timeSinceBeat.toFloat() / 300f)).coerceIn(0.2f, 1.0f)
            val glowColor = Color(0xFFFF9800).copy(alpha = 0.55f * lightIntensity)
            val glowRadius = size.height * (if (isWide) 0.8f else 0.5f)
            val centerGlow = Offset(cx, cy)

            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = centerGlow,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = centerGlow
            )

            // Draw the programmatic transparent meter scale markings
            drawMeterScale(cx, cy, needleLen, isWide)

            // Draw the single combined needle
            drawVintageNeedles(
                leftLevel = leftLevel,
                rightLevel = rightLevel,
                isActive = isPlayerExpanded && isPlaying,
                isWide = isWide
            )
        }
    }
}

private fun DrawScope.drawMeterScale(
    cx: Float,
    cy: Float,
    needleLen: Float,
    isWide: Boolean
) {
    val startAngle = -142f
    val sweepAngle = 104f
    
    val arcRadius = needleLen * 0.95f
    val trackColor = Color.White.copy(alpha = 0.35f)
    
    val tickValues = listOf(
        0.0f to "-20",
        0.2f to "-10",
        0.4f to "-5",
        0.6f to "-3",
        0.73f to "-1",
        0.8f to "0",
        0.9f to "+2",
        1.0f to "+3"
    )
    
    val paintText = android.graphics.Paint().apply {
        textSize = needleLen * 0.08f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    // Draw arc track
    drawArc(
        color = trackColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(cx - arcRadius, cy - arcRadius),
        size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
    )

    for ((value, label) in tickValues) {
        val angleDeg = startAngle + value * sweepAngle
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val cosVal = cos(angleRad).toFloat()
        val sinVal = sin(angleRad).toFloat()
        
        val isRedZone = value >= 0.8f
        val tickColor = if (isRedZone) Color(0xFFFF3D00) else Color.White.copy(alpha = 0.7f)
        
        val tickStartLen = arcRadius
        val tickEndLen = arcRadius + (needleLen * 0.06f)
        
        drawLine(
            color = tickColor,
            start = Offset(cx + tickStartLen * cosVal, cy + tickStartLen * sinVal),
            end = Offset(cx + tickEndLen * cosVal, cy + tickEndLen * sinVal),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        
        val textRadius = arcRadius - (needleLen * 0.12f)
        val textX = cx + textRadius * cosVal
        val textY = cy + textRadius * sinVal + (paintText.textSize / 3f)
        
        paintText.color = if (isRedZone) android.graphics.Color.RED else android.graphics.Color.argb(200, 255, 255, 255)
        
        drawContext.canvas.nativeCanvas.drawText(
            label,
            textX,
            textY,
            paintText
        )
    }
}

private fun DrawScope.drawVintageNeedles(
    leftLevel: Float,
    rightLevel: Float,
    isActive: Boolean,
    isWide: Boolean,
) {
    val w = size.width
    val h = size.height
    
    val cx = w / 2f
    val cy = h * (if (isWide) 0.95f else 0.58f)
    val needleLen = h * (if (isWide) 0.78f else 0.44f)
    
    val leftNeedleColor = Color(0xFFFFB300)
    
    val startAngle = -142f
    val sweepAngle = 104f
    
    fun drawNeedle(level: Float, color: Color) {
        val clamped = level.coerceIn(0f, 1f)
        val angleDeg = startAngle + clamped * sweepAngle
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val tipX = cx + (needleLen * cos(angleRad)).toFloat()
        val tipY = cy + (needleLen * sin(angleRad)).toFloat()
        
        drawLine(
            color = Color.Black.copy(alpha = 0.4f),
            start = Offset(cx + 4f, cy + 4f),
            end = Offset(tipX + 4f, tipY + 4f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(tipX, tipY),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
    }
    
    val combinedLevel = maxOf(leftLevel, rightLevel)
    drawNeedle(combinedLevel, leftNeedleColor)
    
    drawCircle(
        color = Color(0xFFFFB300).copy(alpha = 0.25f),
        radius = w * 0.04f,
        center = Offset(cx, cy)
    )
}
