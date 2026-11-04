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

import com.ferlagod.mispodcast.util.rethrowIfCancellation
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
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.allowHardware
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.ferlagod.mispodcast.MainActivity
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationHelper {

    const val CHANNEL_NEW_EPISODES_ID = "channel_new_episodes"
    const val CHANNEL_DOWNLOADS_ID = "channel_downloads"
    private const val NOTIFICATION_GROUP_KEY = "com.ferlagod.mispodcast.NEW_EPISODES_GROUP"
    private const val SUMMARY_NOTIFICATION_ID = 9999
    private const val BATCH_SUMMARY_NOTIFICATION_ID = 8888

    /**
     * Limpia todas las notificaciones pendientes de la aplicación.
     */
    fun cancelAllNotifications(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancelAll()
        } catch (e: Exception) {
            android.util.Log.w("NotificationHelper", "No se pudieron cancelar las notificaciones: ${e.message}")
        }
    }

    /**
     * Crea los canales de notificaciones (nuevos episodios y descargas) en Android 8.0+.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelEpisodes = NotificationChannel(
                CHANNEL_NEW_EPISODES_ID,
                context.getString(R.string.notif_channel_episodes),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_episodes_desc)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channelEpisodes)

            val channelDownloads = NotificationChannel(
                CHANNEL_DOWNLOADS_ID,
                context.getString(R.string.notif_channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_downloads_desc)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channelDownloads)
        }
    }

    /**
     * Notificación consolidada para comprobaciones del worker con múltiples podcasts.
     * Si solo 1 podcast tiene novedades (y <= 3 episodios), emite la notificación individual de ese podcast.
     * Si varios podcasts tienen novedades o son muchos episodios, emite UNA SOLA notificación resumen (InboxStyle)
     * para evitar saturar la barra de estado y el sonido del usuario.
     */
    suspend fun notifyBatchNewEpisodes(
        context: Context,
        podcastsWithEpisodes: Map<PodcastEntity, List<EpisodeEntity>>
    ) = withContext(Dispatchers.IO) {
        if (podcastsWithEpisodes.isEmpty()) return@withContext

        val validEntries = podcastsWithEpisodes.filter { it.value.isNotEmpty() }
        if (validEntries.isEmpty()) return@withContext

        createNotificationChannels(context)

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return@withContext
        }

        val totalEpisodes = validEntries.values.sumOf { it.size }
        val totalPodcasts = validEntries.size

        // Si solo 1 podcast tiene novedades y <= 3 episodios, mostramos su detalle
        if (totalPodcasts == 1 && totalEpisodes <= 3) {
            val single = validEntries.entries.first()
            notifyNewEpisodes(context, single.key, single.value)
            return@withContext
        }

        // Si son múltiples podcasts o más de 3 episodios, emitimos UNA ÚNICA NOTIFICACIÓN RESUMEN
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "new_releases")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            BATCH_SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(context.getString(R.string.notif_new_episodes_total, totalEpisodes))
            .setSummaryText(context.getString(R.string.notif_programs_with_news, totalPodcasts))

        val sampleLines = validEntries.flatMap { (podcast, eps) ->
            eps.map { ep -> "${podcast.title}: ${ep.title}" }
        }

        sampleLines.take(5).forEach { line ->
            inboxStyle.addLine("• $line")
        }
        if (sampleLines.size > 5) {
            inboxStyle.addLine(context.getString(R.string.notif_more_episodes, sampleLines.size - 5))
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_NEW_EPISODES_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_summary_text, totalEpisodes, totalPodcasts))
            .setSubText(context.getString(R.string.notif_subtext_new_releases))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(inboxStyle)

        try {
            notificationManager.notify(BATCH_SUMMARY_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
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
                .setSubText(context.getString(R.string.notif_subtext_new_episode))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(podcast.title)
                        .setSummaryText(context.getString(R.string.notif_new_episode_published))
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
                .setBigContentTitle(context.getString(R.string.notif_podcast_new_count, podcast.title, newEpisodes.size))
                .setSummaryText(context.getString(R.string.notif_new_episodes_available))

            newEpisodes.take(5).forEach { ep ->
                inboxStyle.addLine("• ${ep.title}")
            }

            if (newEpisodes.size > 5) {
                inboxStyle.addLine(context.getString(R.string.notif_more_short, newEpisodes.size - 5))
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_NEW_EPISODES_ID)
                .setSmallIcon(R.drawable.app_logo)
                .setContentTitle(podcast.title)
                .setContentText(context.getString(R.string.notif_new_episodes_total, newEpisodes.size))
                .setSubText(context.getString(R.string.notif_subtext_new_releases))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // Requisito obligatorio para notificaciones en Android
                .build()
            val result = (context.imageLoader.execute(request) as? SuccessResult)?.image?.asDrawable(context.resources)
            (result as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            null
        }
    }
}
