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

package com.ferlagod.mispodcast.data.backup

import android.content.Context
import android.net.Uri
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exportador de suscripciones en formato estándar OPML 2.0 (XML).
 *
 * Permite generar un archivo compatible con cualquier otro reproductor de podcasts (AntennaPod, etc.)
 * conteniendo los títulos, feeds RSS y páginas oficiales de los programas suscritos.
 */
object OpmlExporter {

    suspend fun exportOpml(
        context: Context,
        targetUri: Uri,
        podcasts: List<PodcastEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el destino para escribir"))

            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            val currentDate = dateFormat.format(Date())

            BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<opml version=\"2.0\">\n")
                writer.write("  <head>\n")
                writer.write("    <title>Mis Podcast - Suscripciones</title>\n")
                writer.write("    <dateCreated>$currentDate</dateCreated>\n")
                writer.write("    <docs>http://opml.org/spec2.opml</docs>\n")
                writer.write("  </head>\n")
                writer.write("  <body>\n")
                writer.write("    <outline text=\"feeds\" title=\"feeds\">\n")

                for (podcast in podcasts) {
                    val safeTitle = escapeXml(podcast.title)
                    val safeXmlUrl = escapeXml(podcast.feedUrl)
                    val safeHtmlUrl = escapeXml(podcast.link)
                    writer.write("      <outline type=\"rss\" text=\"$safeTitle\" title=\"$safeTitle\" xmlUrl=\"$safeXmlUrl\" htmlUrl=\"$safeHtmlUrl\" />\n")
                }

                writer.write("    </outline>\n")
                writer.write("  </body>\n")
                writer.write("</opml>\n")
                writer.flush()
            }

            Result.success(podcasts.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
