package com.example.intune.ui

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CoachBackground(content: @Composable () -> Unit) {
    val vignette = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(vignette, Color.Transparent),
                        center = Offset.Zero,
                        radius = size.maxDimension * 0.8f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(vignette, Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.maxDimension * 0.8f,
                    ),
                )
            },
    ) {
        content()
    }
}

@Composable
fun IntuneLogoMark(modifier: Modifier = Modifier) {
    val coral = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        val stroke = size.minDimension * 0.10f
        drawArc(
            color = coral,
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(size.width * 0.14f, size.height * 0.26f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.50f),
            style = Stroke(stroke),
        )
        drawCircle(coral, size.minDimension * 0.13f, Offset(size.width / 2, size.height * 0.18f))
    }
}

@Composable
fun GrowthMark(level: Int, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val layers = (1 + level / 3).coerceAtMost(4)
        repeat(layers) { layer ->
            val count = 3 + layer * 2
            repeat(count) { index ->
                rotate(index * 360f / count, center) {
                    drawLeaf(center, size.minDimension * (0.16f + layer * 0.05f), primary.copy(alpha = 0.72f - layer * 0.1f), 0f)
                }
            }
        }
        drawCircle(accent, size.minDimension * 0.08f, center)
    }
}

@Composable
fun CelebrationBurst(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val colors = listOf(
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary,
    )
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(650)) }
    Canvas(modifier) {
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
    val context = LocalContext.current
    val sounds = remember(context) { CoachSounds(context) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    return sounds
}
