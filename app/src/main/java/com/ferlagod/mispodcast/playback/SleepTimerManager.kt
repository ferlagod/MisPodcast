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

package com.ferlagod.mispodcast.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

/**
 * Modos de funcionamiento disponibles para el temporizador de apagado.
 */
sealed class SleepTimerMode {
    /** Temporizador inactivo. */
    object Off : SleepTimerMode()

    /** Temporizador por duración fija en minutos (ej. 15, 30, 45, 60 min). */
    data class Minutes(val totalMinutes: Int) : SleepTimerMode()

    /** Temporizador que detiene la reproducción exactamente cuando finaliza el episodio en curso. */
    object EndOfEpisode : SleepTimerMode()
}

/**
 * Estado inmutable del temporizador de apagado.
 *
 * @property isActive Indica si el temporizador está actualmente en marcha.
 * @property mode Modo activo del temporizador ([SleepTimerMode.Off], [SleepTimerMode.Minutes] o [SleepTimerMode.EndOfEpisode]).
 * @property remainingMillis Milisegundos restantes antes del apagado automático.
 * @property totalDurationMillis Duración total original configurada en milisegundos.
 * @property shakeToExtendEnabled Si está activo el detector de acelerómetro para prolongar agitando el dispositivo.
 * @property lastShakeExtendedAt Marca temporal en la que se prolongó por última vez mediante agitación.
 */
data class SleepTimerState(
    val isActive: Boolean = false,
    val mode: SleepTimerMode = SleepTimerMode.Off,
    val remainingMillis: Long = 0L,
    val totalDurationMillis: Long = 0L,
    val shakeToExtendEnabled: Boolean = true,
    val lastShakeExtendedAt: Long = 0L
) {
    /**
     * Retorna una representación legible del tiempo restante (MM:SS) o el estado "Al final del episodio".
     */
    val formattedRemaining: String
        get() {
            if (!isActive) return ""
            if (mode is SleepTimerMode.EndOfEpisode) return "Al final del episodio"
            val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
}

/**
 * Motor central del temporizador de apagado con soporte para:
 * - Intervalos fijos (15, 30, 45, 60 min o valor personalizado).
 * - Apagado automático al terminar el capítulo en reproducción.
 * - Detección por sensor de acelerómetro ("Agitar para prolongar") con confirmación háptica.
 * - Conteo regresivo en segundo plano protegido con [SupervisorJob].
 */
class SleepTimerManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : SensorEventListener {

    private var countdownJob: Job? = null

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var onPauseAction: (() -> Unit)? = null

    private val sensorManager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val accelerometer: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private var lastShakeTimestamp: Long = 0L
    private var isSensorRegistered: Boolean = false

    companion object {
        private const val TAG = "SleepTimerManager"
        private const val SHAKE_THRESHOLD_G = 2.4f // Umbral de aceleración G para detectar agitación intencional
        private const val SHAKE_DEBOUNCE_MS = 1500L // Intervalo mínimo entre agitaciones consecutivas
        const val DEFAULT_EXTEND_MINUTES = 15

        @Volatile
        private var INSTANCE: SleepTimerManager? = null

        fun getInstance(context: Context): SleepTimerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SleepTimerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Registra la acción encargada de pausar el reproductor cuando expira el temporizador.
     */
    fun registerPauseAction(action: () -> Unit) {
        this.onPauseAction = action
    }

    /**
     * Inicia un temporizador con duración fija en minutos.
     */
    fun startMinutesTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelTimer()
            return
        }
        val durationMillis = minutes * 60 * 1000L
        _state.update {
            it.copy(
                isActive = true,
                mode = SleepTimerMode.Minutes(minutes),
                remainingMillis = durationMillis,
                totalDurationMillis = durationMillis
            )
        }
        startCountdownTicker()
        updateSensorState()
        Log.i(TAG, "Temporizador iniciado para $minutes minutos.")
    }

    /**
     * Inicia el modo "Al final del episodio": pausará la reproducción tan pronto como concluya el episodio actual.
     */
    fun startEndOfEpisodeTimer() {
        countdownJob?.cancel()
        _state.update {
            it.copy(
                isActive = true,
                mode = SleepTimerMode.EndOfEpisode,
                remainingMillis = 0L,
                totalDurationMillis = 0L
            )
        }
        updateSensorState()
        Log.i(TAG, "Temporizador 'Al final del episodio' activado.")
    }

    /**
     * Añade minutos adicionales al temporizador activo o prolonga su duración.
     */
    fun extendTimer(additionalMinutes: Int = DEFAULT_EXTEND_MINUTES) {
        val additionalMillis = additionalMinutes * 60 * 1000L
        val currentState = _state.value

        if (!currentState.isActive) {
            startMinutesTimer(additionalMinutes)
            return
        }

        when (currentState.mode) {
            is SleepTimerMode.Minutes -> {
                val newRemaining = currentState.remainingMillis + additionalMillis
                val newTotal = currentState.totalDurationMillis + additionalMillis
                val newMinutes = (newTotal / (60 * 1000L)).toInt()

                _state.update {
                    it.copy(
                        remainingMillis = newRemaining,
                        totalDurationMillis = newTotal,
                        mode = SleepTimerMode.Minutes(newMinutes),
                        lastShakeExtendedAt = System.currentTimeMillis()
                    )
                }
                if (countdownJob?.isActive != true) {
                    startCountdownTicker()
                }
                Log.i(TAG, "Temporizador prolongado +$additionalMinutes min (quedan ${newRemaining / 1000}s).")
            }
            is SleepTimerMode.EndOfEpisode, SleepTimerMode.Off -> {
                startMinutesTimer(additionalMinutes)
            }
        }
    }

    /**
     * Cancela y desactiva el temporizador de apagado, liberando sensores.
     */
    fun cancelTimer() {
        countdownJob?.cancel()
        countdownJob = null
        _state.update {
            it.copy(
                isActive = false,
                mode = SleepTimerMode.Off,
                remainingMillis = 0L,
                totalDurationMillis = 0L
            )
        }
        unregisterSensor()
        Log.i(TAG, "Temporizador cancelado.")
    }

    /**
     * Habilita o deshabilita la prolongación mediante agitación del dispositivo.
     */
    fun setShakeToExtend(enabled: Boolean) {
        _state.update { it.copy(shakeToExtendEnabled = enabled) }
        updateSensorState()
    }

    /**
     * Notificación invocada desde el listener del reproductor cuando un episodio finaliza naturalmente.
     */
    fun onEpisodeEnded() {
        val currentState = _state.value
        if (currentState.isActive && currentState.mode is SleepTimerMode.EndOfEpisode) {
            Log.i(TAG, "Episodio finalizado: apagando reproducción según el temporizador.")
            triggerPauseAndFinish()
        }
    }

    private fun startCountdownTicker() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive && _state.value.isActive && _state.value.mode is SleepTimerMode.Minutes) {
                delay(1000L)
                val currentRemaining = _state.value.remainingMillis - 1000L
                if (currentRemaining <= 0L) {
                    _state.update { it.copy(remainingMillis = 0L) }
                    Log.i(TAG, "Temporizador de minutos agotado: pausando reproducción.")
                    triggerPauseAndFinish()
                    break
                } else {
                    _state.update { it.copy(remainingMillis = currentRemaining) }
                }
            }
        }
    }

    private fun triggerPauseAndFinish() {
        try {
            onPauseAction?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error al pausar la reproducción por temporizador", e)
        }
        cancelTimer()
    }

    private fun updateSensorState() {
        if (_state.value.isActive && _state.value.shakeToExtendEnabled) {
            registerSensor()
        } else {
            unregisterSensor()
        }
    }

    private fun registerSensor() {
        if (!isSensorRegistered && accelerometer != null && sensorManager != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isSensorRegistered = true
            Log.d(TAG, "Sensor de acelerómetro registrado para agitar y prolongar.")
        }
    }

    private fun unregisterSensor() {
        if (isSensorRegistered && sensorManager != null) {
            sensorManager?.unregisterListener(this)
            isSensorRegistered = false
            Log.d(TAG, "Sensor de acelerómetro desregistrado.")
        }
    }

    // ==========================================
    // SensorEventListener (Detección de agitación)
    // ==========================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_state.value.isActive || !_state.value.shakeToExtendEnabled) return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_G) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTimestamp >= SHAKE_DEBOUNCE_MS) {
                lastShakeTimestamp = now
                Log.i(TAG, "¡Agitación detectada (fuerza $gForce G)! Prolongando temporizador +$DEFAULT_EXTEND_MINUTES min.")
                extendTimer(DEFAULT_EXTEND_MINUTES)
                vibrateConfirmation()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Sin acción necesaria
    }

    /**
     * Emite una vibración háptica agradable de confirmación al prolongar mediante agitación.
     */
    private fun vibrateConfirmation() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrationEffect = VibrationEffect.createWaveform(
                    longArrayOf(0, 80, 60, 100),
                    intArrayOf(0, 180, 0, 255),
                    -1
                )
                vibratorManager?.defaultVibrator?.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vibrationEffect = VibrationEffect.createWaveform(
                        longArrayOf(0, 80, 60, 100),
                        -1
                    )
                    vibrator?.vibrate(vibrationEffect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(150L)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo emitir vibración háptica: ${e.message}")
        }
    }
}
