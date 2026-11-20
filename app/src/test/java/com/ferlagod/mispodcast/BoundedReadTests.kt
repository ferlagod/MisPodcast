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

package com.ferlagod.mispodcast

import com.ferlagod.mispodcast.util.LimitedInputStream
import com.ferlagod.mispodcast.util.SizeLimitExceededException
import com.ferlagod.mispodcast.util.stringWithLimit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedReadTests {

    @Test
    fun leeHastaElLimiteExacto() {
        val data = ByteArray(100) { 1 }
        assertEquals(100, LimitedInputStream(data.inputStream(), 100).readBytes().size)
    }

    @Test(expected = SizeLimitExceededException::class)
    fun fallaAlSuperarElLimite() {
        LimitedInputStream(ByteArray(101).inputStream(), 100).readBytes()
    }

    @Test(expected = SizeLimitExceededException::class)
    fun cuerpoDeRespuestaDemasiadoGrandeFalla() {
        "x".repeat(2000).toResponseBody("application/json".toMediaType()).stringWithLimit(1000)
    }

    @Test
    fun cuerpoDeRespuestaPequenoSeLeeConSuCharset() {
        val body = "café".toByteArray(Charsets.ISO_8859_1)
            .toResponseBody("text/plain; charset=ISO-8859-1".toMediaType())
        assertEquals("café", body.stringWithLimit(1000))
    }
}
