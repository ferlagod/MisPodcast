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

import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * Gestor de efectos de audio para mejora de voz y normalización de volumen en Android.
 *
 * Utiliza [LoudnessEnhancer] vinculado a la sesión de audio activa de ExoPlayer para amplificar
 * el nivel sonoro percibido y comprimir picos dinámicos, resultando en voces más nítidas y audibles
 * en podcasts grabados con baja ganancia o masterización deficiente.
 */
class AudioEffectsManager {

    companion object {
        private const val TAG = "AudioEffectsManager"

        /** Ganancia suave: +4.0 dB (400 mB). */
        const val GAIN_SOFT_MB = 400

        /** Ganancia equilibrada recomendada: +8.0 dB (800 mB). */
        const val GAIN_BALANCED_MB = 800

        /** Ganancia potente para podcasts con volumen extremadamente bajo: +12.0 dB (1200 mB). */
        const val GAIN_STRONG_MB = 1200
    }

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    var isVoiceBoostEnabled: Boolean = false
        private set

    var targetGainMb: Int = GAIN_BALANCED_MB
        private set

    /**
     * Asocia los efectos de audio a la sesión de ExoPlayer especificada por [sessionId].
     */
    @Synchronized
    fun attachAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (currentSessionId == sessionId && loudnessEnhancer != null) return

        releaseEnhancer()
        currentSessionId = sessionId

        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(targetGainMb)
                enabled = isVoiceBoostEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo instanciar LoudnessEnhancer en audioSessionId=$sessionId: ${e.message}")
            loudnessEnhancer = null
        }
    }

    /**
     * Habilita o deshabilita la amplificación y normalización de voz.
     */
    @Synchronized
    fun setVoiceBoostEnabled(enabled: Boolean) {
        isVoiceBoostEnabled = enabled
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al alternar estado de LoudnessEnhancer: ${e.message}")
        }
    }

    /**
     * Configura la ganancia objetivo en milibelios (mB).
     * Por ejemplo, 800 mB equivale a un realce de +8 dB.
     */
    @Synchronized
    fun setTargetGainMb(gainMb: Int) {
        targetGainMb = gainMb.coerceIn(0, 3000)
        try {
            loudnessEnhancer?.setTargetGain(targetGainMb)
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al fijar ganancia en LoudnessEnhancer: ${e.message}")
        }
    }

    /**
     * Libera los recursos de hardware y efectos de audio.
     */
    @Synchronized
    fun release() {
        releaseEnhancer()
        currentSessionId = 0
    }

    private fun releaseEnhancer() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error al liberar LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
        }
    }
}
