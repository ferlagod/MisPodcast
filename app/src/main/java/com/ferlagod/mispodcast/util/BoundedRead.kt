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

import okhttp3.ResponseBody
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Lectura de datos remotos con un tope de tamaño, para que una respuesta enorme no agote la memoria. */
object BoundedRead {
    /** Feeds RSS: los más grandes conocidos rondan las decenas de MB. */
    const val MAX_FEED_BYTES = 64L * 1024 * 1024

    /** JSON de capítulos, transcripciones y respuestas de búsqueda. */
    const val MAX_TEXT_BYTES = 16L * 1024 * 1024

    /** Estado de sincronización de Nextcloud. */
    const val MAX_SYNC_BYTES = 64L * 1024 * 1024
}

/** Se lanza cuando una lectura supera su tope. */
class SizeLimitExceededException(limit: Long) : IOException("Respuesta mayor de $limit bytes")

/** [InputStream] que falla con [SizeLimitExceededException] al leer más de [limit] bytes. */
class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    private fun account(n: Long) {
        if (n > 0) {
            count += n
            if (count > limit) throw SizeLimitExceededException(limit)
        }
    }

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) account(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        account(n.toLong())
        return n
    }

    override fun skip(n: Long): Long = super.skip(n).also { account(it) }

    override fun markSupported(): Boolean = false
}

/** Lee el cuerpo como texto sin superar [limit] bytes (respeta el charset de la respuesta). */
fun ResponseBody.stringWithLimit(limit: Long): String {
    if (contentLength() > limit) throw SizeLimitExceededException(limit)
    val charset = contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    return LimitedInputStream(byteStream(), limit).use { it.readBytes().toString(charset) }
}
