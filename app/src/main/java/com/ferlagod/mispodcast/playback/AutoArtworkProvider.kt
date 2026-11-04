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

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ferlagod.mispodcast.MisPodcastApplication
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Expone las portadas remotas de podcasts y episodios como URIs `content://` para Android Auto.
 *
 * Android Auto no descarga portadas `http(s)` de los elementos del catálogo: necesita URIs
 * `content://` o `android.resource://`. Este proveedor descarga la imagen la primera vez que se
 * solicita, la guarda en la caché de la app y la sirve desde disco en las siguientes peticiones.
 *
 * Por seguridad solo se sirven URLs registradas previamente por la propia app mediante [toContentUri]
 * (o ya presentes en la caché), de modo que otras apps no pueden usarlo para descargar URLs arbitrarias.
 */
class AutoArtworkProvider : ContentProvider() {

    companion object {
        private const val TAG = "AutoArtworkProvider"
        private const val AUTHORITY_SUFFIX = ".artwork"
        private const val CACHE_DIR = "auto_artwork"
        private const val MAX_ARTWORK_BYTES = 8L * 1024 * 1024

        /** Clave (hash SHA-256 de la URL) → URL remota original. */
        private val registry = ConcurrentHashMap<String, String>()

        /**
         * Convierte una portada remota en una URI `content://` servida por este proveedor.
         * Las URIs que no sean `http(s)` se devuelven sin cambios.
         */
        fun toContentUri(context: Context, remoteUri: Uri?): Uri? {
            if (remoteUri == null) return null
            val scheme = remoteUri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return remoteUri
            val url = remoteUri.toString()
            val key = sha256(url)
            registry[key] = url
            return Uri.Builder()
                .scheme("content")
                .authority(context.packageName + AUTHORITY_SUFFIX)
                .appendPath(key)
                .build()
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("Sin contexto")
        val key = uri.lastPathSegment?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            ?: throw FileNotFoundException("URI no válida: $uri")

        val dir = File(ctx.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, key)

        if (!file.exists() || file.length() == 0L) {
            val remoteUrl = registry[key] ?: throw FileNotFoundException("Portada no registrada: $key")
            download(ctx, remoteUrl, file)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun download(ctx: Context, url: String, target: File) {
        val client = (ctx.applicationContext as MisPodcastApplication).container.okHttpClient
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful) {
                    throw FileNotFoundException("HTTP ${response.code} al descargar la portada")
                }
                if (body.contentLength() > MAX_ARTWORK_BYTES) {
                    throw FileNotFoundException("Portada demasiado grande")
                }
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_ARTWORK_BYTES) throw FileNotFoundException("Portada demasiado grande")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) throw FileNotFoundException("No se pudo guardar la portada")
        } catch (e: FileNotFoundException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            Log.e(TAG, "Error al descargar la portada para Android Auto", e)
            throw FileNotFoundException(e.message ?: "Error al descargar la portada")
        }
    }

    override fun getType(uri: Uri): String = "image/*"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
