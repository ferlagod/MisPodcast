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

package com.ferlagod.mispodcast.util

/**
 * Utilidades de texto para contenido que llega de los feeds con HTML o entidades.
 *
 * Son Kotlin puro (sin `android.text.Html`) para poder usarse en el parser y en los tests de JVM.
 */
object HtmlText {

    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});")
    private val TAG = Regex("<[^>]*>")
    private val SPACES = Regex("[ \\t\\u00A0]+")

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»", "bull" to "•", "middot" to "·",
        "copy" to "©", "reg" to "®", "trade" to "™", "euro" to "€", "deg" to "°",
        "iexcl" to "¡", "iquest" to "¿", "ordf" to "ª", "ordm" to "º",
        "aacute" to "á", "eacute" to "é", "iacute" to "í", "oacute" to "ó", "uacute" to "ú",
        "Aacute" to "Á", "Eacute" to "É", "Iacute" to "Í", "Oacute" to "Ó", "Uacute" to "Ú",
        "agrave" to "à", "egrave" to "è", "igrave" to "ì", "ograve" to "ò", "ugrave" to "ù",
        "Agrave" to "À", "Egrave" to "È", "Igrave" to "Ì", "Ograve" to "Ò", "Ugrave" to "Ù",
        "acirc" to "â", "ecirc" to "ê", "icirc" to "î", "ocirc" to "ô", "ucirc" to "û",
        "atilde" to "ã", "otilde" to "õ", "Atilde" to "Ã", "Otilde" to "Õ",
        "auml" to "ä", "euml" to "ë", "iuml" to "ï", "ouml" to "ö", "uuml" to "ü",
        "Auml" to "Ä", "Euml" to "Ë", "Iuml" to "Ï", "Ouml" to "Ö", "Uuml" to "Ü",
        "ntilde" to "ñ", "Ntilde" to "Ñ", "ccedil" to "ç", "Ccedil" to "Ç", "szlig" to "ß"
    )

    /**
     * Sustituye las entidades HTML (`&amp;`, `&nbsp;`, `&#233;`, `&#xE9;`…) por sus caracteres.
     * Las entidades desconocidas o mal formadas se dejan tal cual.
     */
    fun decodeEntities(text: String): String {
        if (text.indexOf('&') < 0) return text
        return ENTITY.replace(text) { match ->
            val body = match.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.substring(2).toIntOrNull(16)?.toCodePointString()
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.toCodePointString()
                else -> NAMED[body]
            } ?: match.value
        }
    }

    /**
     * Texto de una línea a partir de un valor que no debería llevar HTML (títulos, autores…):
     * decodifica entidades, incluso las codificadas dos veces (`&amp;amp;`), y une los espacios.
     */
    fun plainLine(text: String): String {
        var result = text
        // Hasta dos pasadas: cubre los feeds que escapan el texto dos veces sin arriesgarse a bucles
        repeat(2) {
            val decoded = decodeEntities(result)
            if (decoded == result) return@repeat
            result = decoded
        }
        return result.replace(SPACES, " ").trim()
    }

    /** Texto plano a partir de HTML: quita etiquetas, decodifica entidades y une los espacios. */
    fun toPlainText(html: String): String =
        decodeEntities(html.replace(TAG, " ")).replace(SPACES, " ").trim()

    private fun Int.toCodePointString(): String? =
        if (this in 1..0x10FFFF && this !in 0xD800..0xDFFF) String(Character.toChars(this)) else null
}
