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

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Procesador de audio Media3 para conversión de canales estéreo a mono en tiempo real.
 *
 * Permite escuchar podcasts con un solo auricular sin perder pistas o interlocutores paneados
 * a la izquierda o derecha en grabaciones estéreo o entrevistas multipistas.
 *
 * Al activarse ([isEnabled] = true), mezcla los canales promediando las muestras PCM de 16 bits:
 * `mono = (L + R) / 2` y replica la señal resultante en ambos canales de salida.
 *
 * Si se encuentra desactivado ([isEnabled] = false), realiza un paso directo transparente
 * de los búferes de audio sin sobrecarga de procesamiento ni necesidad de reinicializar la pista.
 */
@OptIn(UnstableApi::class)
class MonoAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var isEnabled: Boolean = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // Conservamos la misma frecuencia de muestreo, codificación y número de canales (estéreo dual-mono)
        // para garantizar que la transición entre mono y estéreo sea instantánea y libre de cortes.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val channels = inputAudioFormat.channelCount
        val bytesPerFrame = channels * 2 // 2 bytes por muestra (16 bits)

        if (!isEnabled || channels <= 1 || remaining % bytesPerFrame != 0) {
            // Paso directo si está deshabilitado o si la pista ya es mono nativa
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        // Procesamiento mono: mezclamos las muestras de todos los canales
        val frames = remaining / bytesPerFrame
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())

        if (channels == 2) {
            // Optimización rápida para el caso más común: estéreo de 2 canales
            for (i in 0 until frames) {
                val left = inputBuffer.short
                val right = inputBuffer.short
                val mono = ((left.toInt() + right.toInt()) / 2).toShort()
                outputBuffer.putShort(mono)
                outputBuffer.putShort(mono)
            }
        } else {
            // Soporte genérico para pistas multicanal (p. ej. 5.1 envolvente)
            for (i in 0 until frames) {
                var sum = 0
                for (ch in 0 until channels) {
                    sum += inputBuffer.short.toInt()
                }
                val mono = (sum / channels).toShort()
                for (ch in 0 until channels) {
                    outputBuffer.putShort(mono)
                }
            }
        }

        outputBuffer.flip()
    }
}
