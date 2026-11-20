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

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SkipCommandsListenerTests {

    // Player.Commands necesita el framework de Android para inicializarse: la ampliación de
    // comandos se comprueba en dispositivo; aquí se valida la delegación del Proxy.

    @Test
    fun elRestoDeCallbacksSeDelegan() {
        val playing = mutableListOf<Boolean>()
        val listener = skipCommandsListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing += isPlaying
            }
        })

        listener.onIsPlayingChanged(true)

        assertEquals(listOf(true), playing)
    }

    @Test
    fun igualdadPorIdentidadParaPoderQuitarElListener() {
        val inner = object : Player.Listener {}
        val a = skipCommandsListener(inner)
        val b = skipCommandsListener(inner)
        assertEquals(a, a)
        assertNotEquals(a, b)
    }
}
