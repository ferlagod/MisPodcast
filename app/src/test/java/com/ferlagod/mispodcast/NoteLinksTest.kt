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

import com.ferlagod.mispodcast.util.NoteLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLinksTest {

    private fun urls(text: String) = NoteLinks.findBareUrls(text).map { text.substring(it.start, it.end) to it.url }

    @Test
    fun `detecta urls sin la puntuacion final`() {
        assertEquals(
            listOf("https://landing.udima.es/grado" to "https://landing.udima.es/grado"),
            urls("Conoce el Grado: https://landing.udima.es/grado.")
        )
        assertEquals(listOf("https://a.com/x" to "https://a.com/x"), urls("(más en https://a.com/x)"))
    }

    @Test
    fun `www sin esquema se abre con https`() {
        assertEquals(listOf("www.ivoox.com/podcast" to "https://www.ivoox.com/podcast"), urls("Escúchanos en www.ivoox.com/podcast, gracias"))
    }

    @Test
    fun `conserva parentesis que forman parte de la url`() {
        assertEquals(
            listOf("https://es.wikipedia.org/wiki/Mercurio_(planeta)" to "https://es.wikipedia.org/wiki/Mercurio_(planeta)"),
            urls("Ver https://es.wikipedia.org/wiki/Mercurio_(planeta).")
        )
    }

    @Test
    fun `varias urls y comillas tipograficas`() {
        val found = urls("Web: https://uno.es y «https://dos.es/a?b=1&c=2» o “www.tres.org”.")
        assertEquals(listOf("https://uno.es", "https://dos.es/a?b=1&c=2", "www.tres.org"), found.map { it.first })
    }

    @Test
    fun `no confunde correos ni texto suelto`() {
        assertTrue(urls("Escríbenos a hola@www.podcast.es o visita www. pronto").isEmpty())
        assertTrue(urls("Versión 2.0 del episodio, 12:34 y más.").isEmpty())
    }

    @Test
    fun `solo esquemas seguros`() {
        assertNull(NoteLinks.normalize("javascript:alert(1)"))
        assertNull(NoteLinks.normalize("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals("mailto:hola@podcast.es", NoteLinks.normalize("mailto:hola@podcast.es"))
    }

    @Test
    fun `los enlaces del html prevalecen sobre las urls de su propio texto`() {
        val text = "Mi web https://ejemplo.com y otra https://otra.com"
        val markup = listOf(NoteLinks.Link(0, 26, "https://ejemplo.com/?ref=feed"))
        val merged = NoteLinks.merge(text, markup)
        assertEquals(listOf("https://ejemplo.com/?ref=feed", "https://otra.com"), merged.map { it.url })
    }

    @Test
    fun `descarta enlaces del html con esquema peligroso`() {
        val merged = NoteLinks.merge("Pulsa aquí", listOf(NoteLinks.Link(0, 10, "javascript:void(0)")))
        assertTrue(merged.isEmpty())
    }
}
