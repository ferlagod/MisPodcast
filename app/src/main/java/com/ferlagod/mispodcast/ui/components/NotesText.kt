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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spanned
import android.text.style.URLSpan
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.util.NoteLinks
import com.ferlagod.mispodcast.util.TimestampParser

/** Texto visible de unas notas en HTML y sus enlaces (los `<a href>` y las URLs escritas como texto). */
internal data class ParsedNotes(val text: String, val links: List<NoteLinks.Link>)

internal fun parseNotes(html: String): ParsedNotes {
    if (html.isBlank()) return ParsedNotes("", emptyList())
    return try {
        val spanned: Spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        val full = spanned.toString()
        val leading = full.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) full.length else it }
        val text = full.trim()
        val markupLinks = spanned.getSpans(0, spanned.length, URLSpan::class.java).mapNotNull { span ->
            val start = (spanned.getSpanStart(span) - leading).coerceIn(0, text.length)
            val end = (spanned.getSpanEnd(span) - leading).coerceIn(0, text.length)
            if (end > start) NoteLinks.Link(start, end, span.url) else null
        }
        ParsedNotes(text, NoteLinks.merge(text, markupLinks))
    } catch (_: Exception) {
        val text = html.trim()
        ParsedNotes(text, NoteLinks.findBareUrls(text))
    }
}

/**
 * Notas de un episodio (o descripción de un podcast) con los enlaces pulsables y, si se indica
 * [onSeekToPosition], también las marcas de tiempo («12:34») para saltar a ese punto.
 *
 * @param fallback texto a mostrar si [html] está vacío.
 */
@Composable
fun rememberNotesText(
    html: String,
    linkColor: Color,
    fallback: String = "",
    durationMs: Long = 0L,
    onSeekToPosition: ((Long) -> Unit)? = null
): AnnotatedString {
    val context = LocalContext.current
    val noAppMessage = stringResource(R.string.link_no_app)
    val seek by rememberUpdatedState(onSeekToPosition)
    val parsed = remember(html) { parseNotes(html) }
    val hasSeek = onSeekToPosition != null

    return remember(parsed, fallback, linkColor, durationMs, hasSeek, noAppMessage) {
        if (parsed.text.isEmpty()) return@remember AnnotatedString(fallback)
        val linkStyle = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        val timestampStyle = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold))
        buildAnnotatedString {
            append(parsed.text)
            parsed.links.forEach { link ->
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "url",
                        styles = linkStyle,
                        linkInteractionListener = { openLink(context, link.url, noAppMessage) }
                    ),
                    link.start,
                    link.end
                )
            }
            if (hasSeek) {
                TimestampParser.findTimestamps(parsed.text, durationMs)
                    .filter { ts -> parsed.links.none { it.start < ts.end && ts.start < it.end } }
                    .forEach { ts ->
                        addLink(
                            LinkAnnotation.Clickable(
                                tag = "ts_${ts.positionMs}",
                                styles = timestampStyle,
                                linkInteractionListener = { seek?.invoke(ts.positionMs) }
                            ),
                            ts.start,
                            ts.end
                        )
                    }
            }
        }
    }
}

/** Abre [url] fuera de la app; sin una app capaz de abrirlo, avisa en lugar de cerrarse. */
private fun openLink(context: Context, url: String, noAppMessage: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
    }
}
