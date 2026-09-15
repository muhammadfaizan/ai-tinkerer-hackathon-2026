package com.example.intune.ui

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CoachBackground(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "coach-background")
    val start by transition.animateColor(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant, infiniteRepeatable(tween(12_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "background-start")
    val end by transition.animateColor(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background, infiniteRepeatable(tween(15_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "background-end")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(start, end)))) {
        Canvas(Modifier.fillMaxSize().alpha(0.06f)) {
            for (x in 0..size.width.toInt() step 120) for (y in 0..size.height.toInt() step 150) {
                drawLeaf(Offset(x.toFloat(), y.toFloat()), 28f, Color(0xFF256B55), (x + y) % 45f)
            }
        }
        content()
    }
}

@Composable
fun GrowthMark(level: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val layers = (1 + level / 3).coerceAtMost(4)
        repeat(layers) { layer ->
            val count = 3 + layer * 2
            repeat(count) { index ->
                rotate(index * 360f / count, size.center) {
                    drawLeaf(size.center, size.minDimension * (0.16f + layer * 0.05f), Color(0xFF256B55).copy(alpha = 0.72f - layer * 0.1f), 0f)
                }
            }
        }
        drawCircle(Color(0xFF345B9B), size.minDimension * 0.08f, size.center)
    }
}

@Composable
fun CelebrationBurst(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(650)) }
    Canvas(modifier) {
        val colors = listOf(Color(0xFF345B9B), Color(0xFF256B55), Color(0xFF236A78))
        repeat(14) { index ->
            val angle = index * 2 * Math.PI / 14
            val distance = size.minDimension * 0.42f * progress.value
            val center = Offset(size.width / 2 + cos(angle).toFloat() * distance, size.height / 2 + sin(angle).toFloat() * distance)
            drawCircle(colors[index % colors.size].copy(alpha = 1f - progress.value * 0.7f), 7f * (1f - progress.value * 0.35f), center)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLeaf(center: Offset, radius: Float, color: Color, rotation: Float) {
    rotate(rotation, center) {
        val path = Path().apply {
            moveTo(center.x, center.y - radius)
            cubicTo(center.x + radius, center.y - radius, center.x + radius, center.y + radius * 0.45f, center.x, center.y + radius)
            cubicTo(center.x - radius, center.y + radius * 0.45f, center.x - radius, center.y - radius, center.x, center.y - radius)
        }
        drawPath(path, color)
    }
}

class CoachSounds(context: Context) {
    private val appContext = context.applicationContext
    private val tones = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35)

    fun accept(enabled: Boolean) = play(enabled, ToneGenerator.TONE_PROP_ACK, 180)
    fun dismiss(enabled: Boolean) = play(enabled, ToneGenerator.TONE_PROP_BEEP2, 55)
    fun navigate(enabled: Boolean) = play(enabled, ToneGenerator.TONE_PROP_BEEP, 35)

    private fun play(enabled: Boolean, tone: Int, duration: Int) {
        val audio = appContext.getSystemService(AudioManager::class.java)
        val notifications = appContext.getSystemService(NotificationManager::class.java)
        val dnd = notifications.currentInterruptionFilter
        if (enabled && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            dnd != NotificationManager.INTERRUPTION_FILTER_NONE && dnd != NotificationManager.INTERRUPTION_FILTER_ALARMS
        ) tones.startTone(tone, duration)
    }

    fun release() = tones.release()
}

@Composable
fun rememberCoachSounds(): CoachSounds {
    val sounds = remember { CoachSounds(LocalContext.current) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    return sounds
}
