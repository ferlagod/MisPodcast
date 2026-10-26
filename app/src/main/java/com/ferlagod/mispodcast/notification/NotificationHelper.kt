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

package com.ferlagod.mispodcast.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ferlagod.mispodcast.MainActivity
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationHelper {

    const val CHANNEL_NEW_EPISODES_ID = "channel_new_episodes"
    const val CHANNEL_NEW_EPISODES_NAME = "Nuevos episodios"
    const val CHANNEL_DOWNLOADS_ID = "channel_downloads"
    const val CHANNEL_DOWNLOADS_NAME = "Descargas de episodios"
    private const val NOTIFICATION_GROUP_KEY = "com.ferlagod.mispodcast.NEW_EPISODES_GROUP"
    private const val SUMMARY_NOTIFICATION_ID = 9999

    /**
     * Crea los canales de notificaciones (nuevos episodios y descargas) en Android 8.0+.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelEpisodes = NotificationChannel(
                CHANNEL_NEW_EPISODES_ID,
                CHANNEL_NEW_EPISODES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones automáticas al detectar nuevos capítulos de tus podcasts suscritos"
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channelEpisodes)

            val channelDownloads = NotificationChannel(
                CHANNEL_DOWNLOADS_ID,
                CHANNEL_DOWNLOADS_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de descarga en segundo plano para escucha sin conexión"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channelDownloads)
        }
    }

    /**
     * Muestra una notificación rica para un nuevo episodio o lote de episodios de un podcast.
     */
    suspend fun notifyNewEpisodes(
        context: Context,
        podcast: PodcastEntity,
        newEpisodes: List<EpisodeEntity>
    ) = withContext(Dispatchers.IO) {
        if (newEpisodes.isEmpty()) return@withContext

        createNotificationChannels(context)

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return@withContext
        }

        // Cargar portada con Coil
        val artworkUrl = newEpisodes.firstOrNull()?.imageUrl?.ifBlank { podcast.artworkUrl } ?: podcast.artworkUrl
        val artworkBitmap = loadArtworkBitmap(context, artworkUrl)

        if (newEpisodes.size == 1) {
            val episode = newEpisodes.first()
            val notificationId = episode.id.hashCode()

            // PendingIntent para abrir la pantalla del podcast y reproducir/ver el episodio
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "podcast_detail")
                putExtra("podcastId", podcast.id)
                putExtra("feedUrl", podcast.feedUrl)
                putExtra("episodeId", episode.id)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cleanDescription = episode.description
                .replace(Regex("<.*?>"), "")
                .trim()
                .take(180)

            val builder = NotificationCompat.Builder(context, CHANNEL_NEW_EPISODES_ID)
                .setSmallIcon(R.drawable.app_logo)
                .setContentTitle(podcast.title)
                .setContentText(episode.title)
                .setSubText("Nuevo episodio")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(podcast.title)
                        .setSummaryText("Nuevo episodio publicado")
                        .bigText("${episode.title}\n\n$cleanDescription")
                )

            if (artworkBitmap != null) {
                builder.setLargeIcon(artworkBitmap)
            }

            try {
                notificationManager.notify(notificationId, builder.build())
            } catch (_: SecurityException) {
            }
        } else {
            // Múltiples episodios nuevos del mismo podcast
            val notificationId = podcast.id.hashCode()

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "podcast_detail")
                putExtra("podcastId", podcast.id)
                putExtra("feedUrl", podcast.feedUrl)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("${podcast.title} (${newEpisodes.size} nuevos)")
                .setSummaryText("Nuevos episodios disponibles")

            newEpisodes.take(5).forEach { ep ->
                inboxStyle.addLine("• ${ep.title}")
            }

            if (newEpisodes.size > 5) {
                inboxStyle.addLine("y ${newEpisodes.size - 5} más...")
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_NEW_EPISODES_ID)
                .setSmallIcon(R.drawable.app_logo)
                .setContentTitle(podcast.title)
                .setContentText("${newEpisodes.size} nuevos episodios disponibles")
                .setSubText("Novedades")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setStyle(inboxStyle)

            if (artworkBitmap != null) {
                builder.setLargeIcon(artworkBitmap)
            }

            try {
                notificationManager.notify(notificationId, builder.build())
            } catch (_: SecurityException) {
            }
        }
    }

    /**
     * Descarga de forma segura un Bitmap desde una URL para usarlo en la notificación de Android.
     */
    private suspend fun loadArtworkBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val imageLoader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // Requisito obligatorio para notificaciones en Android
                .build()
            val result = (imageLoader.execute(request) as? SuccessResult)?.drawable
            (result as? BitmapDrawable)?.bitmap
        } catch (_: Exception) {
            null
        }
    }
}
