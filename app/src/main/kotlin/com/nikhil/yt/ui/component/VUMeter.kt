package com.nikhil.yt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
                // Poll the amplitude values from our custom audio processor
                val rawL = service.amplitudeProcessor.latestAmplitudeL
                val rawR = service.amplitudeProcessor.latestAmplitudeR

                // Scale it by the current player volume (which is 0f to 1f)
                val currentVol = service.player.volume
                
                // Add a small random jitter to make it feel organic and warm
                val jitterL = if (rawL > 0.05f) (Math.random().toFloat() - 0.5f) * 0.02f else 0f
                val jitterR = if (rawR > 0.05f) (Math.random().toFloat() - 0.5f) * 0.02f else 0f

                leftLevel = (rawL * currentVol + jitterL).coerceIn(0f, 1f)
                rightLevel = (rawR * currentVol + jitterR).coerceIn(0f, 1f)

                delay(16) // ~60 FPS polling rate
            }
        } else {
            // Decay to zero when paused
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
        Image(
            painter = painterResource(
                if (isWide) com.nikhil.yt.R.drawable.vu_meter_wide_bg
                else com.nikhil.yt.R.drawable.vu_meter_bg
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Real-time transient beat-synchronized ambery radial light glow
            val timeSinceBeat = System.currentTimeMillis() - service.amplitudeProcessor.lastBeatTime
            val lightIntensity = (1f - (timeSinceBeat.toFloat() / 300f)).coerceIn(0.2f, 1.0f)
            val glowColor = Color(0xFFFF9800).copy(alpha = 0.55f * lightIntensity)
            val glowRadius = size.height * (if (isWide) 0.8f else 0.5f)
            val centerGlow = Offset(size.width / 2f, size.height * (if (isWide) 0.95f else 0.58f))

            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = centerGlow,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = centerGlow
            )

            drawVintageNeedles(
                leftLevel = leftLevel,
                rightLevel = rightLevel,
                isActive = isPlayerExpanded && isPlaying,
                isWide = isWide
            )
        }
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
    
    // Pivot at bottom center of the black dial face (95% for wide, 58% for square)
    val cx = w / 2f
    val cy = h * (if (isWide) 0.95f else 0.58f)
    val needleLen = h * (if (isWide) 0.78f else 0.44f)
    
    // Glowing amber-yellow (Left) and orange-red (Right) needles matching the TEAC dial
    val leftNeedleColor = Color(0xFFFFB300)
    val rightNeedleColor = Color(0xFFFF3D00)
    
    // Sweep from left (-142 deg) to right (-38 deg)
    val startAngle = -142f
    val sweepAngle = 104f
    
    fun drawNeedle(level: Float, color: Color) {
        val clamped = level.coerceIn(0f, 1f)
        val angleDeg = startAngle + clamped * sweepAngle
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val tipX = cx + (needleLen * cos(angleRad)).toFloat()
        val tipY = cy + (needleLen * sin(angleRad)).toFloat()
        
        // Shadow for depth
        drawLine(
            color = Color.Black.copy(alpha = 0.4f),
            start = Offset(cx + 4f, cy + 4f),
            end = Offset(tipX + 4f, tipY + 4f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        
        // Main needle line
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
    
    // Glowing light bulb center
    drawCircle(
        color = Color(0xFFFFB300).copy(alpha = 0.25f),
        radius = w * 0.04f,
        center = Offset(cx, cy)
    )
}
