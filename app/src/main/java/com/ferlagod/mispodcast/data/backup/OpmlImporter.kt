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
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * Importador y procesador de archivos en formato estándar OPML (XML).
 *
 * Analiza etiquetas `<outline>` con URLs de feeds RSS (`xmlUrl`) para permitir la
 * suscripción masiva a programas desde copias de seguridad de otras aplicaciones.
 */
object OpmlImporter {

    suspend fun importOpml(
        context: Context,
        sourceUri: Uri
    ): Result<List<OpmlPodcastFeed>> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el archivo OPML seleccionado"))

            val feedList = mutableListOf<OpmlPodcastFeed>()

            inputStream.use { stream ->
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, "UTF-8")

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                            ?: parser.getAttributeValue(null, "url")

                        if (!xmlUrl.isNullOrBlank()) {
                            val title = parser.getAttributeValue(null, "text")
                                ?: parser.getAttributeValue(null, "title")
                                ?: "Podcast sin título"
                            val htmlUrl = parser.getAttributeValue(null, "htmlUrl")

                            feedList.add(
                                OpmlPodcastFeed(
                                    title = title.trim(),
                                    xmlUrl = xmlUrl.trim(),
                                    htmlUrl = htmlUrl?.trim()
                                )
                            )
                        }
                    }
                    eventType = parser.next()
                }
            }

            Result.success(feedList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
