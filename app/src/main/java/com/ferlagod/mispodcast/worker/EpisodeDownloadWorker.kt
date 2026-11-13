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

package com.ferlagod.mispodcast.worker

import com.ferlagod.mispodcast.util.rethrowIfCancellation
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.alerts.AppAlerts
import com.ferlagod.mispodcast.alerts.DownloadFailureReason
import com.ferlagod.mispodcast.data.download.DownloadCleaner
import com.ferlagod.mispodcast.data.download.DownloadStatus
import com.ferlagod.mispodcast.data.download.EpisodeDownloadManager
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.notification.NotificationHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Worker de WorkManager encargado de descargar episodios de podcasts en segundo plano de manera robusta.
 *
 * Características clave:
 * - Se ejecuta con [ForegroundInfo] para evitar que el sistema operativo mate el proceso.
 * - Soporta reanudación mediante cabecera HTTP `Range`, evitando reiniciar descargas desde cero.
 * - Respeta restricciones de red (solo Wi-Fi) y carga energética configuradas por el usuario.
 * - Determina dinámicamente la extensión del archivo según el Content-Type o la URL (mp3, m4a, opus, etc.).
 * - Limpia archivos temporales en fallos definitivos.
 * - No introduce efectos secundarios en la cola de reproducción.
 */
class EpisodeDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val okHttpClient: OkHttpClient by lazy {
        (applicationContext as com.ferlagod.mispodcast.MisPodcastApplication).container.okHttpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return Result.failure()
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return Result.failure()
        val episodeTitle = inputData.getString(KEY_EPISODE_TITLE) ?: "Episodio"

        val container = (applicationContext as com.ferlagod.mispodcast.MisPodcastApplication).container
        val database = container.database
        val dao = database.podcastDao()
        val downloadManager = EpisodeDownloadManager.getInstance(applicationContext, dao)

        NotificationHelper.createNotificationChannels(applicationContext)

        val targetDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            ?: applicationContext.filesDir
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val sanitizedTitle = EpisodeDownloadManager.sanitizeFileName(episodeTitle)
        val tempFile = File(targetDir, "${sanitizedTitle}_${episodeId.hashCode()}.tmp")
        val stripTrackers = container.preferencesManager
            .userSettingsFlow.first().stripAudioTrackers
        val cleanAudioUrl = if (stripTrackers) com.ferlagod.mispodcast.util.AudioTrackingSanitizer.cleanAudioUrl(audioUrl) else audioUrl

        try {
            downloadManager.reportProgress(episodeId, DownloadStatus.DOWNLOADING, 0f)
            trySetForeground(episodeTitle, 0)

            var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

            var request = buildDownloadRequest(cleanAudioUrl, existingBytes)
            var response = okHttpClient.newCall(request).execute()

            // Si el servidor responde 416 Range Not Satisfiable (el temporal es mayor o inválido), reiniciar desde 0
            if (response.code == 416 && existingBytes > 0L) {
                response.close()
                tempFile.delete()
                existingBytes = 0L
                request = buildDownloadRequest(cleanAudioUrl, 0L)
                response = okHttpClient.newCall(request).execute()
            }

            if (!response.isSuccessful && response.code != 206) {
                val code = response.code
                response.close()
                // Episodio retirado o inaccesible: reintentar no lo arreglaría
                if (code in PERMANENT_HTTP_ERRORS) {
                    throw PermanentDownloadException("HTTP $code al descargar $audioUrl")
                }
                throw IOException("Error HTTP $code al descargar $audioUrl")
            }

            val isPartialContent = response.code == 206
            val responseBody = response.body ?: throw IOException("Cuerpo de respuesta vacío para $audioUrl")
            val contentType = response.header("Content-Type")

            val totalBytes = if (isPartialContent) {
                existingBytes + responseBody.contentLength()
            } else {
                existingBytes = 0L
                responseBody.contentLength()
            }

            val appendMode = isPartialContent && existingBytes > 0L
            var bytesCopied = existingBytes
            var lastUpdateNotificationTime = System.currentTimeMillis()

            responseBody.byteStream().use { input: InputStream ->
                FileOutputStream(tempFile, appendMode).use { output: FileOutputStream ->
                    val buffer = ByteArray(16384)
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        if (isStopped) {
                            output.flush()
                            Log.w(TAG, "Descarga interrumpida/detenida por el sistema para: $episodeTitle. Se guardan $bytesCopied bytes.")
                            return Result.retry()
                        }

                        output.write(buffer, 0, read)
                        bytesCopied += read

                        val now = System.currentTimeMillis()
                        if (totalBytes > 0 && now - lastUpdateNotificationTime > 400) {
                            val progressFloat = (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                            val progressInt = (progressFloat * 100).toInt()

                            downloadManager.reportProgress(episodeId, DownloadStatus.DOWNLOADING, progressFloat)
                            setProgress(workDataOf(KEY_PROGRESS to progressInt))
                            trySetForeground(episodeTitle, progressInt)

                            lastUpdateNotificationTime = now
                        }
                    }
                    output.flush()
                }
            }

            // Una conexión cortada sin excepción deja un archivo incompleto: reintentar con Range
            if (totalBytes > 0L && bytesCopied < totalBytes) {
                throw IOException("Descarga incompleta ($bytesCopied de $totalBytes bytes) para $audioUrl")
            }

            // Detección dinámica de extensión (no solo mp3)
            val extension = determineExtension(audioUrl, contentType)
            val targetFile = File(targetDir, "${sanitizedTitle}_${episodeId.hashCode()}.$extension")

            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("No se pudo mover ${tempFile.name} a ${targetFile.name}")
                }
            }

            // Actualizar Room con isDownloaded = true, la ruta y el tamaño en bytes
            dao.updateDownloadStatus(
                episodeId = episodeId,
                isDownloaded = true,
                localPath = targetFile.absolutePath,
                fileSizeBytes = targetFile.length()
            )

            // Actualizar estado en memoria
            downloadManager.reportProgress(episodeId, DownloadStatus.DOWNLOADED, 1f)

            // Aplicar límite de almacenamiento configurado por el usuario
            DownloadCleaner.enforceCacheLimit(applicationContext, dao, protectedEpisodeId = episodeId)

            Log.i(TAG, "Descarga completada con éxito: $episodeTitle -> ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            return Result.success()

        } catch (ioe: IOException) {
            Log.w(TAG, "Error de red/IO durante la descarga de $episodeTitle: ${ioe.message}. Se reintentará con Range.")
            if (isStopped) {
                return Result.retry()
            }
            val outOfSpace = targetDir.usableSpace < MIN_FREE_SPACE_BYTES
            if (runAttemptCount < 3 && !outOfSpace) {
                return Result.retry()
            } else {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                downloadManager.reportProgress(episodeId, DownloadStatus.NOT_DOWNLOADED, 0f)
                notifyFailure(
                    episodeId,
                    episodeTitle,
                    if (outOfSpace) DownloadFailureReason.NO_SPACE else DownloadFailureReason.NETWORK
                )
                return Result.failure()
            }
        } catch (pe: PermanentDownloadException) {
            if (tempFile.exists()) tempFile.delete()
            downloadManager.reportProgress(episodeId, DownloadStatus.NOT_DOWNLOADED, 0f)
            Log.w(TAG, "Descarga descartada para $episodeTitle: ${pe.message}")
            notifyFailure(episodeId, episodeTitle, DownloadFailureReason.UNAVAILABLE)
            return Result.failure()
        } catch (e: Exception) {
            // La limpieza también se aplica si el usuario cancela la descarga
            if (tempFile.exists()) {
                tempFile.delete()
            }
            downloadManager.reportProgress(episodeId, DownloadStatus.NOT_DOWNLOADED, 0f)
            e.rethrowIfCancellation()
            Log.e(TAG, "Fallo irrecuperable al descargar $episodeTitle: ${e.message}", e)
            return Result.failure()
        }
    }

    /**
     * Avisa del fallo dentro de la app si está a la vista; si no, con una notificación del sistema.
     * Así el usuario nunca recibe el mismo aviso dos veces.
     */
    private fun notifyFailure(episodeId: String, episodeTitle: String, reason: DownloadFailureReason) {
        if (AppAlerts.reportDownloadFailure(episodeId, episodeTitle, reason)) return
        val reasonText = applicationContext.getString(
            when (reason) {
                DownloadFailureReason.NETWORK -> R.string.download_error_network
                DownloadFailureReason.NO_SPACE -> R.string.download_error_no_space
                DownloadFailureReason.UNAVAILABLE -> R.string.download_error_unavailable
            }
        )
        NotificationHelper.notifyDownloadFailed(applicationContext, episodeId, episodeTitle, reasonText)
    }

    /** `false` cuando Android no permite el servicio en primer plano (app en segundo plano, Android 12+). */
    private var foregroundAllowed = true

    /**
     * Pasa la descarga a primer plano si el sistema lo permite. Con la app en segundo plano
     * (p. ej. al descargar automáticamente desde [PodcastCheckWorker]) Android 12+ lanza
     * `ForegroundServiceStartNotAllowedException`; la descarga continúa igualmente sin notificación.
     */
    private suspend fun trySetForeground(title: String, progress: Int) {
        if (!foregroundAllowed) return
        try {
            setForeground(createForegroundInfo(title, progress))
        } catch (e: Exception) {
            // WorkManager puede entregar la excepción tal cual o envuelta: se trata cualquiera
            e.rethrowIfCancellation()
            foregroundAllowed = false
            Log.w(TAG, "Descarga sin primer plano para $title: ${e.message}")
        }
    }

    private fun buildDownloadRequest(url: String, existingBytes: Long): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", com.ferlagod.mispodcast.util.AudioTrackingSanitizer.GENERIC_PRIVACY_USER_AGENT)

        if (existingBytes > 0L) {
            builder.header("Range", "bytes=$existingBytes-")
        }
        return builder.build()
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val notificationId = (KEY_DOWNLOAD_NOTIFICATION_BASE + id.hashCode()) and 0x7FFFFFFF

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_DOWNLOADS_ID)
            .setContentTitle(applicationContext.getString(R.string.notif_downloading_episode))
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress <= 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    /** Error que no se resuelve reintentando (p. ej. el episodio ya no existe en el servidor). */
    private class PermanentDownloadException(message: String) : Exception(message)

    companion object {
        private const val TAG = "EpisodeDownloadWorker"
        private val PERMANENT_HTTP_ERRORS = setOf(401, 403, 404, 410, 451)
        private const val MIN_FREE_SPACE_BYTES = 20L * 1024 * 1024
        private const val KEY_DOWNLOAD_NOTIFICATION_BASE = 8000

        const val KEY_EPISODE_ID = "key_episode_id"
        const val KEY_AUDIO_URL = "key_audio_url"
        const val KEY_EPISODE_TITLE = "key_episode_title"
        const val KEY_PODCAST_ID = "key_podcast_id"
        const val KEY_IMAGE_URL = "key_image_url"
        const val KEY_DURATION = "key_duration"
        const val KEY_PUB_DATE = "key_pub_date"
        const val KEY_PROGRESS = "key_progress"
        const val TAG_DOWNLOAD = "podcast_download"

        /**
         * Determina dinámicamente la extensión adecuada para el archivo según la cabecera Content-Type o la URL.
         */
        fun determineExtension(url: String, contentType: String?): String {
            val cleanType = contentType?.substringBefore(';')?.trim()?.lowercase()
            when (cleanType) {
                "audio/mpeg", "audio/mp3" -> return "mp3"
                "audio/mp4", "audio/m4a", "audio/x-m4a" -> return "m4a"
                "audio/ogg", "audio/opus" -> return "opus"
                "audio/aac", "audio/aacp" -> return "aac"
                "audio/wav", "audio/x-wav" -> return "wav"
                "audio/flac" -> return "flac"
            }

            val path = url.substringBefore('?').substringBefore('#')
            val lastDot = path.lastIndexOf('.')
            if (lastDot != -1) {
                val candidate = path.substring(lastDot + 1).lowercase()
                if (candidate in listOf("mp3", "m4a", "mp4", "opus", "ogg", "aac", "wav", "flac")) {
                    return candidate
                }
            }
            return "mp3"
        }
    }
}
