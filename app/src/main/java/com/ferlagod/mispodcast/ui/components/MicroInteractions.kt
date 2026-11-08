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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.ui.theme.AccentGreen
import com.ferlagod.mispodcast.ui.theme.RoyalBlueLight
import com.ferlagod.mispodcast.ui.theme.readableContentColor

/**
 * Indicador animado de ecualizador con 3 barras que oscilan armónicamente
 * cuando el episodio se encuentra en reproducción activa.
 */
@Composable
fun EqualizerIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    isPlaying: Boolean = true,
    maxHeight: Dp = 14.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizerTransition")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 310, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val h1 = if (isPlaying) maxHeight * bar1Height else maxHeight * 0.35f
        val h2 = if (isPlaying) maxHeight * bar2Height else maxHeight * 0.65f
        val h3 = if (isPlaying) maxHeight * bar3Height else maxHeight * 0.40f

        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h1)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h2)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h3)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
    }
}

/**
 * Botón de reproducción con anillo de progreso circular para episodios a medias.
 */
@Composable
fun PlayProgressButton(
    isPlaying: Boolean,
    progress: Float, // 0.0f a 1.0f
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(size)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Anillo de progreso exterior si el episodio ha comenzado pero no terminado
        if (progress > 0.01f && progress < 0.99f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 2.5.dp,
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.2f)
            )
        }

        Box(
            modifier = Modifier
                .size(size - 6.dp)
                .background(
                    if (isPlaying) accentColor else accentColor.copy(alpha = 0.14f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                tint = if (isPlaying) accentColor.readableContentColor() else accentColor,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

/**
 * Botón animado de suscripción con rebote elástico e intercambio armónico de icono (+ ↔ ✓).
 */
@Composable
fun AnimatedSubscribeButton(
    isSubscribed: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dominantColor: Color = MaterialTheme.colorScheme.primary
) {
    val haptic = LocalHapticFeedback.current

    AnimatedContent(
        targetState = isSubscribed,
        transitionSpec = {
            (scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(150)))
                .togetherWith(scaleOut(tween(120)) + fadeOut(tween(120)))
        },
        label = "subscribeAnim"
    ) { subscribed ->
        if (subscribed) {
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                modifier = modifier,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AccentGreen.copy(alpha = 0.15f),
                    contentColor = AccentGreen
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.subscribed),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                modifier = modifier,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = dominantColor,
                    contentColor = dominantColor.readableContentColor()
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.subscribe),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Fracción del ancho de la fila que hay que recorrer (y soltar) para ejecutar una acción de deslizar. */
private const val SWIPE_ACTION_THRESHOLD = 0.6f

/**
 * Contenedor de deslizar con «resorte»: la acción solo se ejecuta si el dedo se suelta tras
 * recorrer al menos [SWIPE_ACTION_THRESHOLD] del ancho. Si no, la fila vuelve a su sitio sin hacer
 * nada, también ante un gesto rápido y corto. Al cruzar el umbral vibra para avisar de que,
 * al soltar, se aplicará la acción.
 *
 * @param dismissOnAction true si la fila desaparece tras la acción (p. ej. quitar de la cola);
 *   false si debe volver a su sitio tras ejecutarla.
 * @param backgroundContent fondo según la dirección del gesto y si ya se ha cruzado el umbral.
 */
@Composable
fun DeliberateSwipeBox(
    onSwipeStartToEnd: () -> Unit,
    onSwipeEndToStart: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnAction: Boolean = false,
    backgroundContent: @Composable (direction: SwipeToDismissBoxValue, armed: Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentStartToEnd by rememberUpdatedState(onSwipeStartToEnd)
    val currentEndToStart by rememberUpdatedState(onSwipeEndToStart)

    // Estado no restaurable: una fila que vuelve a la lista (p. ej. tras «Deshacer») empieza en reposo
    val state = remember {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled) { total -> total * SWIPE_ACTION_THRESHOLD }
    }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var fingerDown by remember { mutableStateOf(false) }
    var releasedArmed by remember { mutableStateOf(false) }
    val armed by remember {
        derivedStateOf {
            // requireOffset() falla mientras la fila aún no se ha medido
            val offset = runCatching { state.requireOffset() }.getOrDefault(0f)
            widthPx > 0f && !offset.isNaN() && abs(offset) >= widthPx * SWIPE_ACTION_THRESHOLD
        }
    }

    LaunchedEffect(armed) {
        if (armed && fingerDown) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember(state) {
        { direction ->
            if (releasedArmed) {
                releasedArmed = false
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> currentStartToEnd()
                    SwipeToDismissBoxValue.EndToStart -> currentEndToStart()
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                if (!dismissOnAction) scope.launch { state.reset() }
            } else {
                // Gesto rápido sin recorrido suficiente: vuelve a su sitio sin aplicar nada
                scope.launch { state.reset() }
            }
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                // Observa (sin consumir) dónde se suelta el dedo para decidir si la acción procede
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    fingerDown = true
                    releasedArmed = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    releasedArmed = armed
                    fingerDown = false
                }
            },
        onDismiss = onDismiss,
        backgroundContent = { backgroundContent(state.dismissDirection, armed) }
    ) {
        content()
    }
}

/**
 * Envoltorio deslizable (Swipe-to-action) para filas de episodios:
 * - Deslizar a la derecha (StartToEnd): Añadir a la cola.
 * - Deslizar a la izquierda (EndToStart): Marcar como completado/escuchado.
 * La fila vuelve a su sitio tras la acción; ver [DeliberateSwipeBox].
 */
@Composable
fun SwipeableEpisodeRow(
    onSwipeToQueue: () -> Unit,
    onSwipeToComplete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    DeliberateSwipeBox(
        onSwipeStartToEnd = onSwipeToQueue,
        onSwipeEndToStart = onSwipeToComplete,
        modifier = modifier,
        backgroundContent = { direction, armed ->
            val (bgColor, icon, label, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Quadruple(
                    RoyalBlueLight,
                    Icons.AutoMirrored.Filled.QueueMusic,
                    stringResource(R.string.add_to_queue),
                    Alignment.CenterStart
                )
                SwipeToDismissBoxValue.EndToStart -> Quadruple(
                    AccentGreen,
                    Icons.Filled.CheckCircle,
                    stringResource(R.string.mark_played_short),
                    Alignment.CenterEnd
                )
                else -> Quadruple(Color.Transparent, Icons.Filled.Add, "", Alignment.Center)
            }
            SwipeActionBackground(
                color = bgColor,
                armed = armed,
                alignment = alignment,
                verticalPadding = 6.dp
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(icon, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(icon, contentDescription = null, tint = Color.White)
                }
            }
        },
        content = content
    )
}

/**
 * Fondo común de las acciones de deslizar: tenue mientras no se alcanza el umbral y con el color
 * pleno (y el icono algo más grande) cuando al soltar se aplicará la acción.
 */
@Composable
fun SwipeActionBackground(
    color: Color,
    armed: Boolean,
    alignment: Alignment,
    verticalPadding: Dp,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(if (armed) 1f else 0.45f, label = "swipeBgAlpha")
    val scale by animateFloatAsState(
        targetValue = if (armed) 1.12f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipeContentScale"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = verticalPadding)
            .clip(RoundedCornerShape(16.dp))
            .background(if (color == Color.Transparent) color else color.copy(alpha = alpha))
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        ) {
            content()
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
