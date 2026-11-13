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

import com.ferlagod.mispodcast.util.HtmlText
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {

    @Test
    fun `decodifica entidades con nombre, decimales y hexadecimales`() {
        assertEquals("Rock & Metal «ya» — é é", HtmlText.decodeEntities("Rock &amp; Metal &laquo;ya&raquo; &mdash; &#233; &#xE9;"))
    }

    @Test
    fun `deja intactas las entidades desconocidas y los ampersands sueltos`() {
        assertEquals("A & B &foo; &#xZZ;", HtmlText.decodeEntities("A & B &foo; &#xZZ;"))
    }

    @Test
    fun `plainLine resuelve títulos codificados dos veces`() {
        assertEquals("MAXIMETAL, Hard Rock & Heavy Metal", HtmlText.plainLine("MAXIMETAL, Hard Rock &amp;amp; Heavy Metal"))
        assertEquals("Hard Rock & Heavy", HtmlText.plainLine("Hard Rock &amp; Heavy"))
    }

    @Test
    fun `plainLine no decodifica más de dos niveles`() {
        assertEquals("&amp;", HtmlText.plainLine("&amp;amp;amp;"))
    }

    @Test
    fun `toPlainText quita etiquetas y convierte nbsp en espacio`() {
        assertEquals(
            "Conoce el Grado: https://landing.udima.es/grado",
            HtmlText.toPlainText("<p>Conoce el Grado:&nbsp;<a href=\"x\">https://landing.udima.es/grado</a></p>")
        )
    }

    @Test
    fun `ignora puntos de código no válidos`() {
        assertEquals("&#55296;", HtmlText.decodeEntities("&#55296;"))
    }
}
