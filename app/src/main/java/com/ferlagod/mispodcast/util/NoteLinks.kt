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
 * Enlaces de las notas de un episodio o de la descripción de un podcast.
 *
 * Kotlin puro (sin APIs de Android) para poder probarlo en tests de JVM.
 */
object NoteLinks {

    /** Enlace sobre el rango `[start, end)` del texto visible. */
    data class Link(val start: Int, val end: Int, val url: String)

    private val BARE_URL = Regex("""(?i)(?<![\w@/.-])(?:https?://|www\.)[^\s<>"'«»“”]+""")
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}…'\""
    private val ALLOWED_SCHEMES = setOf("http", "https", "mailto")

    /**
     * URLs escritas como texto (`https://…`, `www.…`), sin la puntuación que suele seguirlas
     * («visita https://ejemplo.com.»). Un paréntesis de cierre se conserva si forma parte de la URL,
     * como en las de Wikipedia: `…/Mercurio_(planeta)`.
     */
    fun findBareUrls(text: String): List<Link> = BARE_URL.findAll(text).mapNotNull { match ->
        val start = match.range.first
        var end = match.range.last + 1
        while (end > start && text[end - 1] in TRAILING_PUNCTUATION) {
            val candidate = text.substring(start, end)
            if (text[end - 1] == ')' && candidate.count { it == '(' } >= candidate.count { it == ')' }) break
            end--
        }
        val raw = text.substring(start, end)
        val url = normalize(raw) ?: return@mapNotNull null
        // «www.» o «https://» solos no son un enlace
        if (url.substringAfter("://").length < 4 || '.' !in url.substringAfter("://")) return@mapNotNull null
        Link(start, end, url)
    }.toList()

    /** URL lista para abrir, o `null` si su esquema no es seguro (p. ej. `javascript:` o `intent:`). */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("www.", ignoreCase = true)) "https://$trimmed" else trimmed
        val scheme = withScheme.substringBefore(':', "").lowercase()
        return if (scheme in ALLOWED_SCHEMES) withScheme else null
    }

    /**
     * Une los enlaces del HTML ([fromMarkup]) con las URLs escritas como texto, descartando las que
     * se solapan con un enlace del HTML (p. ej. `<a href="…">https://…</a>`).
     */
    fun merge(text: String, fromMarkup: List<Link>): List<Link> {
        val markup = fromMarkup.mapNotNull { link -> normalize(link.url)?.let { link.copy(url = it) } }
        val bare = findBareUrls(text).filter { b -> markup.none { it.start < b.end && b.start < it.end } }
        return (markup + bare).sortedBy { it.start }
    }
}
