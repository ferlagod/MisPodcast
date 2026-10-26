/*
 * Mis Podcast - Gestor y reproductor libre de podcasts para Android
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: puedes redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General de GNU tal como fue
 * publicada por la Free Software Foundation, ya sea la versión 3 de la
 * Licencia, o (a tu elección) cualquier versión posterior.
 *
 * Este programa se distribuye con la esperanza de que sea útil,
 * pero SIN NINGUNA GARANTÍA; ni siquiera la garantía implícita de
 * COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR.
 * Consulta la Licencia Pública General de GNU para más detalles.
 *
 * Deberías haber recibido una copia de la Licencia Pública General de GNU
 * junto con este programa. Si no es así, consulta <https://www.gnu.org/licenses/>.
 */

package com.ferlagod.mispodcast.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ferlagod.mispodcast.data.model.Chapter
import kotlin.math.PI
import kotlin.math.sin

/**
 * Barra de progreso ondulada interactiva estilo Material 3 Expressive (WavySlider).
 *
 * Muestra:
 * - Una onda sinodal continua y animada para la porción de audio reproducida cuando [isPlaying] es true.
 * - Una línea recta inactiva con marcas verticales discretas para los límites de cada [Chapter].
 * - Un pulgar táctil suave y soporte para scrub/arrastre táctil o toque directo para buscar posición.
 */
@Composable
fun ExpressiveWavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    chapters: List<Chapter> = emptyList(),
    totalDurationMs: Long = 0L,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val maxVal = if (valueRange.endInclusive > 0f) valueRange.endInclusive else 1f
    val fraction = (value / maxVal).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fraction) }

    val effectiveFraction = if (isDragging) dragFraction else fraction

    // Animación de fase para la onda mientras se reproduce
    val infiniteTransition = rememberInfiniteTransition(label = "WavyProgressPhaseTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying && !isDragging) (2f * PI.toFloat()) else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(maxVal) {
                detectTapGestures { offset ->
                    val newFrac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChange(newFrac * maxVal)
                }
            }
            .pointerInput(maxVal) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(dragFraction * maxVal)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChange(dragFraction * maxVal)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(dragFraction * maxVal)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val centerY = size.height / 2f
            val activeWidth = width * effectiveFraction

            // 1. Pista inactiva (fondo)
            drawLine(
                color = trackColor,
                start = Offset(activeWidth, centerY),
                end = Offset(width, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 2. Marcas de división de capítulos sobre la pista inactiva
            if (chapters.isNotEmpty() && totalDurationMs > 0L) {
                chapters.forEach { ch ->
                    val chRatio = (ch.startTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    val markX = width * chRatio
                    if (markX > activeWidth && markX < width - 4.dp.toPx()) {
                        drawLine(
                            color = waveColor.copy(alpha = 0.55f),
                            start = Offset(markX, centerY - 6.dp.toPx()),
                            end = Offset(markX, centerY + 6.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // 3. Pista activa (onda sinusoidal suave cuando reproduce, recta cuando está en pausa o arrastrando)
            if (activeWidth > 0f) {
                if (isPlaying && !isDragging && activeWidth > 20.dp.toPx()) {
                    val wavePath = Path()
                    val waveLength = 32.dp.toPx()
                    val amplitude = 3.8.dp.toPx()

                    wavePath.moveTo(0f, centerY)
                    var x = 0f
                    val step = 3f // Resolución de trazado suave
                    while (x <= activeWidth) {
                        val angle = (x / waveLength) * (2f * PI.toFloat()) - wavePhase
                        val y = centerY + sin(angle) * amplitude
                        wavePath.lineTo(x, y)
                        x += step
                    }

                    drawPath(
                        path = wavePath,
                        color = waveColor,
                        style = Stroke(
                            width = 4.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    drawLine(
                        color = waveColor,
                        start = Offset(0f, centerY),
                        end = Offset(activeWidth, centerY),
                        strokeWidth = 4.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 4. Pulgar (Thumb) Material 3 Expressive
                val thumbRadius = if (isDragging) 8.dp.toPx() else 6.5.dp.toPx()
                drawCircle(
                    color = waveColor,
                    radius = thumbRadius,
                    center = Offset(activeWidth, centerY)
                )
                // Aro exterior blanco para destacar en fondos oscuros
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = thumbRadius * 0.45f,
                    center = Offset(activeWidth, centerY)
                )
            }
        }
    }
}
