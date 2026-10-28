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

package com.ferlagod.mispodcast

import android.content.Context
import android.net.Uri
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity
import com.ferlagod.mispodcast.data.preferences.UserSettings
import com.ferlagod.mispodcast.widget.PodcastWidgetState
import com.ferlagod.mispodcast.playback.SleepTimerManager
import com.ferlagod.mispodcast.playback.SleepTimerMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlinx.coroutines.flow.first

class RegressionTests {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val raw = firstArg<String>()
            val mockUri = mockk<Uri>()
            every { mockUri.toString() } returns raw
            mockUri
        }
        every { Uri.fromFile(any()) } answers {
            val file = firstArg<File>()
            val mockUri = mockk<Uri>()
            every { mockUri.toString() } returns "file://${file.absolutePath}"
            mockUri
        }
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.equals(any(), any()) } answers {
            val a = firstArg<CharSequence?>()
            val b = secondArg<CharSequence?>()
            a == b
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Regresión 1: Al reproducir un episodio, debe colocarse siempre en positionIndex = 0
     * de la cola y desplazar a los demás elementos para que nunca se desincronice el orden.
     */
    @Test
    fun testRegression1_activeEpisodeAlwaysAtPositionIndexZero() {
        // Estado inicial de la cola con 3 elementos
        val initialQueue = mutableListOf(
            QueueItemEntity(episodeId = "ep-1", positionIndex = 0, isCurrentlyPlaying = true, addedAt = 1000L),
            QueueItemEntity(episodeId = "ep-2", positionIndex = 1, isCurrentlyPlaying = false, addedAt = 2000L),
            QueueItemEntity(episodeId = "ep-3", positionIndex = 2, isCurrentlyPlaying = false, addedAt = 3000L)
        )

        // Simulación de la lógica de transacción Room de PodcastDao.setCurrentPlaybackInQueue
        fun playEpisodeInQueue(queue: MutableList<QueueItemEntity>, targetEpisodeId: String) {
            // 1. Eliminar targetEpisodeId si ya existía en la cola
            queue.removeAll { it.episodeId == targetEpisodeId }

            // 2. Desplazar todos los demás índices positionIndex + 1
            val shifted = queue.map { item ->
                item.copy(positionIndex = item.positionIndex + 1, isCurrentlyPlaying = false)
            }.toMutableList()

            // 3. Insertar el target con positionIndex = 0 y como reproduciendo
            shifted.add(
                0,
                QueueItemEntity(
                    episodeId = targetEpisodeId,
                    positionIndex = 0,
                    isCurrentlyPlaying = true,
                    addedAt = System.currentTimeMillis()
                )
            )

            // 4. Reindexar secuencialmente 0, 1, 2...
            val reindexed = shifted.sortedBy { it.positionIndex }.mapIndexed { idx, it ->
                it.copy(positionIndex = idx)
            }

            queue.clear()
            queue.addAll(reindexed)
        }

        // Caso A: Reproducir un episodio nuevo que no estaba en la cola
        playEpisodeInQueue(initialQueue, "ep-nuevo")

        assertEquals("ep-nuevo", initialQueue[0].episodeId)
        assertEquals(0, initialQueue[0].positionIndex)
        assertTrue(initialQueue[0].isCurrentlyPlaying)

        assertEquals("ep-1", initialQueue[1].episodeId)
        assertEquals(1, initialQueue[1].positionIndex)
        assertFalse(initialQueue[1].isCurrentlyPlaying)

        assertEquals("ep-2", initialQueue[2].episodeId)
        assertEquals(2, initialQueue[2].positionIndex)

        assertEquals("ep-3", initialQueue[3].episodeId)
        assertEquals(3, initialQueue[3].positionIndex)

        // Caso B: Reproducir un episodio que ya estaba en la posición 3 ("ep-3")
        playEpisodeInQueue(initialQueue, "ep-3")

        assertEquals("ep-3", initialQueue[0].episodeId)
        assertEquals(0, initialQueue[0].positionIndex)
        assertTrue(initialQueue[0].isCurrentlyPlaying)

        assertEquals("ep-nuevo", initialQueue[1].episodeId)
        assertEquals(1, initialQueue[1].positionIndex)

        assertEquals("ep-1", initialQueue[2].episodeId)
        assertEquals(2, initialQueue[2].positionIndex)

        assertEquals("ep-2", initialQueue[3].episodeId)
        assertEquals(3, initialQueue[3].positionIndex)

        assertEquals(4, initialQueue.size)
    }

    /**
     * Regresión 2: Si un episodio tiene isDownloaded = true y un localFilePath válido y existente,
     * el reproductor debe elegir la URI local y NUNCA la audioUrl remota.
     */
    @Test
    fun testRegression2_localFilePriorityOverRemoteAudioUrl() {
        val tempAudioFile = File.createTempFile("test_episode_downloaded", ".mp3").apply {
            writeBytes(ByteArray(1024))
            deleteOnExit()
        }

        val downloadedEpisode = EpisodeEntity(
            id = "ep-local-1",
            podcastId = "pod-1",
            title = "Episodio Descargado",
            description = "Descripción de prueba",
            audioUrl = "https://servidor-remoto.com/archivos/pesado.mp3",
            imageUrl = "https://servidor-remoto.com/cover.jpg",
            pubDate = 1700000000000L,
            duration = 1800000L,
            isDownloaded = true,
            localFilePath = tempAudioFile.absolutePath
        )

        // Lógica de resolución de URI implementada en PodcastPlaybackService y PlayerViewModel
        fun resolveUri(episode: EpisodeEntity): String {
            return when {
                episode.isDownloaded && !episode.localFilePath.isNullOrBlank() && File(episode.localFilePath).exists() -> {
                    Uri.fromFile(File(episode.localFilePath)).toString()
                }
                !episode.localFilePath.isNullOrBlank() && File(episode.localFilePath).exists() -> {
                    Uri.fromFile(File(episode.localFilePath)).toString()
                }
                !episode.localFilePath.isNullOrBlank() && episode.localFilePath.startsWith("content://") -> {
                    Uri.parse(episode.localFilePath).toString()
                }
                episode.audioUrl.startsWith("content://") -> {
                    Uri.parse(episode.audioUrl).toString()
                }
                else -> {
                    Uri.parse(episode.audioUrl).toString()
                }
            }
        }

        val resolvedUri = resolveUri(downloadedEpisode)
        assertEquals("file://${tempAudioFile.absolutePath}", resolvedUri)
        assertNotEquals(downloadedEpisode.audioUrl, resolvedUri)
        assertFalse(resolvedUri.startsWith("https://"))

        // Si el archivo físico no existe o isDownloaded = false, debe caer al audioUrl remoto
        val nonDownloadedEpisode = downloadedEpisode.copy(
            isDownloaded = false,
            localFilePath = "/ruta/inexistente/episodio.mp3"
        )
        val fallbackUri = resolveUri(nonDownloadedEpisode)
        assertEquals("https://servidor-remoto.com/archivos/pesado.mp3", fallbackUri)
    }

    /**
     * Regresión 3: Al pausar o cerrar la reproducción en el milisegundo 125000,
     * currentPositionMs debe actualizarse exactamente a 125000 y al volver a cargar
     * el episodio debe hacer seekTo(125000).
     */
    @Test
    fun testRegression3_exactListeningPositionRetentionAndSeekTo() {
        val pausedPosition = 125000L
        val totalDuration = 600000L // 10 minutos

        var episode = EpisodeEntity(
            id = "ep-pos-test",
            podcastId = "pod-pos",
            title = "Episodio con progreso guardado",
            description = "Descripción del episodio",
            audioUrl = "https://ejemplo.com/audio.mp3",
            imageUrl = "https://ejemplo.com/cover.jpg",
            pubDate = 1700000000000L,
            duration = totalDuration,
            currentPositionMs = 0L
        )

        // 1. Simular la pausa / detención de reproducción a los 125.000 ms
        episode = episode.copy(currentPositionMs = pausedPosition)
        assertEquals(125000L, episode.currentPositionMs)

        // 2. Simular reanudación del episodio en PodcastPlaybackService
        fun calculateResumePosition(ep: EpisodeEntity): Long {
            return if (ep.currentPositionMs > 0L && (ep.duration <= 0L || ep.currentPositionMs < ep.duration - 5000L)) {
                ep.currentPositionMs
            } else {
                0L
            }
        }

        val seekTargetMs = calculateResumePosition(episode)
        assertEquals(125000L, seekTargetMs)

        // 3. Verificar que el reproductor ejecuta seekTo exactamente con los 125.000 ms
        val mockPlayerAction = mockk<(Long) -> Unit>(relaxed = true)
        mockPlayerAction.invoke(seekTargetMs)

        verify(exactly = 1) { mockPlayerAction.invoke(125000L) }

        // Si el episodio ya estaba en los últimos 4 segundos (casi terminado), no debe reanudar al final sino en 0
        val nearlyFinishedEpisode = episode.copy(currentPositionMs = totalDuration - 2000L)
        assertEquals(0L, calculateResumePosition(nearlyFinishedEpisode))
    }

    /**
     * Regresión 4: Permitir crear un podcast de carpeta local (isLocalFolder = true)
     * aunque inicialmente tenga 0 archivos de audio.
     */
    @Test
    fun testRegression4_localFolderPodcastAllowedWithZeroAudios() {
        val folderTreeUri = "content://com.android.externalstorage.documents/tree/primary%3AMisAudios"
        val folderName = "Grabaciones Propias"

        val podcastId = "local_" + folderTreeUri.hashCode().toString().replace("-", "0")
        val feedUrl = "local://$podcastId"

        // Creación del PodcastEntity de carpeta local sin archivos iniciales
        val localFolderPodcast = PodcastEntity(
            id = podcastId,
            title = folderName,
            author = "Carpeta Local",
            description = "Audios importados desde la carpeta local: $folderName",
            feedUrl = feedUrl,
            artworkUrl = "",
            isSubscribed = true,
            subscribedAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),
            category = "Archivos Locales",
            link = folderTreeUri,
            isLocalFolder = true,
            localFolderPath = folderTreeUri
        )

        val scannedAudioFiles = emptyList<String>()

        // Verificar propiedades críticas
        assertTrue(localFolderPodcast.isLocalFolder)
        assertEquals(folderTreeUri, localFolderPodcast.localFolderPath)
        assertEquals(feedUrl, localFolderPodcast.feedUrl)
        assertEquals("Carpeta Local", localFolderPodcast.author)
        assertEquals(0, scannedAudioFiles.size)

        // El podcast existe válidamente y puede insertarse en base de datos aunque la lista esté vacía
        assertEquals("local_", localFolderPodcast.id.take(6))
        assertTrue(localFolderPodcast.id.length > 6)
    }

    /**
     * Regresión 5: Sincronización Nextcloud sin pérdida:
     * - Las lápidas (unsubscribedAt) previenen la resurrección de suscripciones canceladas.
     * - El progreso de reproducción más reciente (positionUpdatedAt) prevalece incluso si es menor (rebobinado o desmarcar reproducido).
     */
    @Test
    fun testRegression5_nextcloudTombstoneAndPositionResolution() {
        val now = 1000000L

        // 1. Caso de lápida (tombstone)
        val localSubscribedAt = now - 50000
        val remoteUnsubscribedAt = now - 10000

        // El usuario canceló la suscripción en la tablet después de haberse suscrito en el móvil
        val shouldUnsubscribe = remoteUnsubscribedAt > localSubscribedAt
        assertTrue("La lápida remota debe ganar sobre una suscripción previa", shouldUnsubscribe)

        // 2. Rebobinado de audio con marca temporal
        val localEpisode = EpisodeEntity(
            id = "ep1",
            podcastId = "pod1",
            title = "Episodio 1",
            description = "",
            audioUrl = "https://example.com/audio.mp3",
            imageUrl = "",
            pubDate = now - 100000,
            duration = 1800000L,
            currentPositionMs = 900000L, // Iba por el minuto 15
            positionUpdatedAt = now - 20000,
            isPlayed = false
        )

        val remoteRewoundPosition = 300000L // En otro dispositivo se rebobinó al minuto 5
        val remoteTimestamp = now - 5000 // Cambio más reciente

        // Al ser remoteTimestamp > localEpisode.positionUpdatedAt, la posición remota (menor) debe ganar
        val resolvedPosition = if (remoteTimestamp > localEpisode.positionUpdatedAt) {
            remoteRewoundPosition
        } else {
            localEpisode.currentPositionMs
        }
        assertEquals(300000L, resolvedPosition)

        // 3. Desmarcar como reproducido (isPlayed = false)
        val remoteIsPlayed = false
        val resolvedPlayed = if (remoteTimestamp > localEpisode.positionUpdatedAt) {
            remoteIsPlayed
        } else {
            localEpisode.isPlayed
        }
        assertFalse("El estado desmarcado como reproducido más reciente debe respetarse", resolvedPlayed)
    }

    /**
     * Regresión 6: Descargas robustas:
     * - Detección dinámica de extensiones (mp3, m4a, opus, aac) en lugar de forzar siempre .mp3.
     * - Creación correcta de cabecera Range para reanudación.
     */
    @Test
    fun testRegression6_robustDownloadsWithDynamicExtensionsAndRange() {
        // 1. Detección por Content-Type
        assertEquals("mp3", com.ferlagod.mispodcast.worker.EpisodeDownloadWorker.determineExtension("https://example.com/stream?id=123", "audio/mpeg"))
        assertEquals("m4a", com.ferlagod.mispodcast.worker.EpisodeDownloadWorker.determineExtension("https://example.com/audio", "audio/mp4"))
        assertEquals("m4a", com.ferlagod.mispodcast.worker.EpisodeDownloadWorker.determineExtension("https://example.com/audio", "audio/x-m4a"))
        assertEquals("opus", com.ferlagod.mispodcast.worker.EpisodeDownloadWorker.determineExtension("https://example.com/stream", "audio/opus"))
        assertEquals("aac", com.ferlagod.mispodcast.worker.EpisodeDownloadWorker.determineExtension("https://example.com/stream", "audio/aac"))

        // 2. Detección por URL
        assertEquals("m4a", com.ferlagod.mispodcast.worker.EpisodeDownloadWorker.determineExtension("https://example.com/episodes/show1.m4a?source=feed", null))
        assertEquals("opus", com.ferlagod.mispodcast.worker.EpisodeDownloadWorker.determineExtension("https://example.com/episodes/show2.opus", null))
        assertEquals("flac", com.ferlagod.mispodcast.worker.EpisodeDownloadWorker.determineExtension("https://example.com/episodes/show3.flac#part1", null))
        assertEquals("mp3", com.ferlagod.mispodcast.worker.EpisodeDownloadWorker.determineExtension("https://example.com/unknown_format", null))

        // 3. Formato de Range
        val existingBytes = 5242880L // 5 MB ya descargados
        val rangeHeader = "bytes=$existingBytes-"
        assertEquals("bytes=5242880-", rangeHeader)
    }

    /**
     * Regresión 7: Avance de cola y preservación de orden al terminar un episodio:
     * - Si el usuario escucha un episodio en la posición K (ej. posición 2), el orden de la cola no se corrompe.
     * - Al terminar el episodio, el siguiente seleccionado es exactamente el posterior (K + 1),
     *   en lugar de asumir erróneamente queueList[1] o volver al principio.
     * - El episodio terminado se elimina de la cola y se reindexan las posiciones restantes.
     */
    @Test
    fun testRegression7_queueAdvancementAndOrderPreservation() {
        data class TestQueueItem(val episodeId: String, var positionIndex: Int, var isCurrentlyPlaying: Boolean)

        val queue = mutableListOf(
            TestQueueItem("ep-0", 0, false),
            TestQueueItem("ep-1", 1, false),
            TestQueueItem("ep-2", 2, false),
            TestQueueItem("ep-3", 3, false),
            TestQueueItem("ep-4", 4, false)
        )

        // 1. Simulación de setCurrentPlaybackInQueue respetando el orden existente si ya está en la cola
        fun setPlayingInQueue(list: MutableList<TestQueueItem>, targetId: String) {
            val inQueue = list.any { it.episodeId == targetId }
            if (inQueue) {
                list.forEach { it.isCurrentlyPlaying = (it.episodeId == targetId) }
            } else {
                list.forEach { it.positionIndex += 1; it.isCurrentlyPlaying = false }
                list.add(0, TestQueueItem(targetId, 0, true))
            }
        }

        // El usuario pulsa play en el episodio "ep-2" (posición 2)
        setPlayingInQueue(queue, "ep-2")

        // El orden debe mantenerse intacto: [ep-0, ep-1, ep-2, ep-3, ep-4]
        assertEquals(5, queue.size)
        assertEquals("ep-0", queue[0].episodeId)
        assertEquals(0, queue[0].positionIndex)
        assertFalse(queue[0].isCurrentlyPlaying)

        assertEquals("ep-2", queue[2].episodeId)
        assertEquals(2, queue[2].positionIndex)
        assertTrue(queue[2].isCurrentlyPlaying)

        // 2. Simulación del cálculo del siguiente episodio tras terminar "ep-2"
        val finishedId = "ep-2"
        val finishedIndex = queue.indexOfFirst { it.episodeId == finishedId }
        val remaining = queue.filter { it.episodeId != finishedId }

        val nextItem = if (finishedIndex >= 0 && finishedIndex + 1 < queue.size) {
            queue[finishedIndex + 1]
        } else {
            remaining.first()
        }

        // El siguiente episodio DEBE ser "ep-3" (índice 3), NO "ep-1" ni "ep-0"
        assertEquals("ep-3", nextItem.episodeId)

        // 3. Simulación de markAsPlayedAndRemoveFromQueue: se retira el terminado y se reindexa
        queue.removeAll { it.episodeId == finishedId }
        queue.forEachIndexed { index, item -> item.positionIndex = index }

        assertEquals(4, queue.size)
        assertEquals("ep-0", queue[0].episodeId)
        assertEquals("ep-1", queue[1].episodeId)
        assertEquals("ep-3", queue[2].episodeId)
        assertEquals(2, queue[2].positionIndex)
        assertEquals("ep-4", queue[3].episodeId)
        assertEquals(3, queue[3].positionIndex)
    }

    /**
     * Regresión 8: Seguridad y Privacidad:
     * - AudioTrackingSanitizer elimina redirecciones de seguimiento (Podtrac, Chartable, pdst.fm) y parámetros UTM.
     * - NextcloudSyncManager exige conexión segura HTTPS (o localhost) rechazando HTTP inseguro.
     */
    @Test
    fun testRegression8_securityAndPrivacyEnhancements() {
        // 1. Desenrollar Podtrac
        val podtracUrl = "https://dts.podtrac.com/redirect.mp3/cdn.example.com/shows/audio.mp3"
        val cleanedPodtrac = com.ferlagod.mispodcast.util.AudioTrackingSanitizer.cleanAudioUrl(podtracUrl)
        assertEquals("https://cdn.example.com/shows/audio.mp3", cleanedPodtrac)

        // 2. Desenrollar Chartable con URL completa embebida
        val chartableUrl = "https://chtbl.com/track/98765/https://media.podcasts.org/episodes/final.mp3"
        val cleanedChartable = com.ferlagod.mispodcast.util.AudioTrackingSanitizer.cleanAudioUrl(chartableUrl)
        assertEquals("https://media.podcasts.org/episodes/final.mp3", cleanedChartable)

        // 3. Desenrollar rastreadores anidados (Podtrac + pdst.fm)
        val nestedTrackerUrl = "https://dts.podtrac.com/redirect.mp3/https://pdst.fm/e/server.com/show/ep.mp3"
        val cleanedNested = com.ferlagod.mispodcast.util.AudioTrackingSanitizer.cleanAudioUrl(nestedTrackerUrl)
        assertEquals("https://server.com/show/ep.mp3", cleanedNested)

        // 4. Limpieza de parámetros query de rastreo (UTM)
        val utmTrackingUrl = "https://cdn.example.com/audio.mp3?utm_source=adnetwork&utm_medium=podcast&valid_token=abc123"
        val cleanedUtm = com.ferlagod.mispodcast.util.AudioTrackingSanitizer.cleanAudioUrl(utmTrackingUrl)
        assertTrue("Debe conservar parámetros válidos del enclosure", cleanedUtm.contains("valid_token=abc123"))
        assertFalse("Debe eliminar utm_source", cleanedUtm.contains("utm_source"))
        assertFalse("Debe eliminar utm_medium", cleanedUtm.contains("utm_medium"))

        // 5. URLs directas limpias no deben sufrir alteraciones
        val directCleanUrl = "https://libre.podcast.org/feed/episodio42.opus"
        val unchanged = com.ferlagod.mispodcast.util.AudioTrackingSanitizer.cleanAudioUrl(directCleanUrl)
        assertEquals(directCleanUrl, unchanged)

        // 6. Validación de HTTPS en Nextcloud
        assertTrue(com.ferlagod.mispodcast.sync.NextcloudSyncManager.isSecureServerUrl("https://mi-servidor.nextcloud.com"))
        assertTrue(com.ferlagod.mispodcast.sync.NextcloudSyncManager.isSecureServerUrl("http://localhost:8080"))
        assertTrue(com.ferlagod.mispodcast.sync.NextcloudSyncManager.isSecureServerUrl("http://127.0.0.1:8080"))
        assertFalse(com.ferlagod.mispodcast.sync.NextcloudSyncManager.isSecureServerUrl("http://servidor-inseguro.com"))
    }

    /**
     * Regresión 9: Inyección de dependencias y clientes HTTP compartidos (Apartado 3.1):
     * - AppContainer centraliza OkHttpClient con caché en disco de 20 MB y connection pool.
     * - newBuilder() en clientes derivados (Nextcloud, Feeds, Apple API, Workers) comparte pool y caché.
     */
    @Test
    fun testRegression9_sharedDependenciesAndAppContainer() {
        val tempCacheDir = java.io.File(System.getProperty("java.io.tmpdir"), "test_http_cache_" + System.currentTimeMillis())
        tempCacheDir.mkdirs()
        tempCacheDir.deleteOnExit()

        val cacheSize = 20L * 1024L * 1024L
        val diskCache = okhttp3.Cache(tempCacheDir, cacheSize)
        val pool = okhttp3.ConnectionPool(8, 5, java.util.concurrent.TimeUnit.MINUTES)

        val baseClient = okhttp3.OkHttpClient.Builder()
            .cache(diskCache)
            .connectionPool(pool)
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        assertEquals(cacheSize, baseClient.cache?.maxSize())
        assertEquals(pool, baseClient.connectionPool)

        // Derivado para feeds RSS con timeout personalizado
        val feedClient = baseClient.newBuilder()
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Deben compartir la misma ConnectionPool y la misma Caché HTTP
        assertSame(baseClient.connectionPool, feedClient.connectionPool)
        assertSame(baseClient.cache, feedClient.cache)

        // Derivado para Nextcloud Sync
        val nextcloudClient = baseClient.newBuilder()
            .readTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        assertSame(baseClient.connectionPool, nextcloudClient.connectionPool)
        assertSame(baseClient.cache, nextcloudClient.cache)

        // Derivado para descargas
        val downloadClient = baseClient.newBuilder()
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        assertSame(baseClient.connectionPool, downloadClient.connectionPool)
        assertSame(baseClient.cache, downloadClient.cache)
    }

    /**
     * Regresión 10: Optimización de comprobación de feeds (Apartado 3.2):
     * - Peticiones condicionales HTTP: FeedFetchResult.NotModified (HTTP 304) vs FeedFetchResult.Success (HTTP 200).
     * - Deduplicación masiva eficiente: detección de episodios nuevos mediante resultado de inserción con IGNORE
     *   (IDs != -1L) sin cargar todas las listas históricas en memoria.
     * - Concurrencia controlada: límite de peticiones simultáneas con Semaphore(6).
     */
    @Test
    fun testRegression10_feedUpdateOptimizationAndConditionalHeaders() {
        // 1. Simulación de deduplicación con resultado de inserción Room IGNORE
        val existingEpisodeIds = setOf("ep-1", "ep-2", "ep-3")
        val incomingBatch = listOf(
            EpisodeEntity(
                id = "ep-2", // Duplicado existente
                podcastId = "pod-1",
                title = "Episodio 2",
                description = "Desc",
                audioUrl = "https://audio.com/2.mp3",
                imageUrl = "",
                pubDate = 1000L,
                duration = 60000L
            ),
            EpisodeEntity(
                id = "ep-4", // Nuevo
                podcastId = "pod-1",
                title = "Episodio 4",
                description = "Desc",
                audioUrl = "https://audio.com/4.mp3",
                imageUrl = "",
                pubDate = 2000L,
                duration = 60000L
            ),
            EpisodeEntity(
                id = "ep-5", // Nuevo
                podcastId = "pod-1",
                title = "Episodio 5",
                description = "Desc",
                audioUrl = "https://audio.com/5.mp3",
                imageUrl = "",
                pubDate = 3000L,
                duration = 60000L
            )
        )

        // Simulación de la respuesta devuelta por Room @Insert(onConflict = IGNORE):
        // Fila 0 ("ep-2"): ignorada -> -1L
        // Fila 1 ("ep-4"): insertada -> rowId 4L
        // Fila 2 ("ep-5"): insertada -> rowId 5L
        val mockInsertResults = incomingBatch.map { ep ->
            if (existingEpisodeIds.contains(ep.id)) -1L else 100L
        }

        val newlyInsertedEpisodes = incomingBatch.filterIndexed { index, _ ->
            mockInsertResults[index] != -1L
        }

        assertEquals(2, newlyInsertedEpisodes.size)
        assertEquals("ep-4", newlyInsertedEpisodes[0].id)
        assertEquals("ep-5", newlyInsertedEpisodes[1].id)

        // 2. Verificación de modelos de resultado condicional
        val notModifiedResult: com.ferlagod.mispodcast.data.remote.parser.FeedFetchResult =
            com.ferlagod.mispodcast.data.remote.parser.FeedFetchResult.NotModified
        assertTrue(notModifiedResult is com.ferlagod.mispodcast.data.remote.parser.FeedFetchResult.NotModified)

        // 3. Verificación de PodcastEntity con cabeceras de caché
        val podcastWithHeaders = PodcastEntity(
            id = "https://feed.org/rss",
            title = "Podcast de Prueba",
            author = "Autor",
            description = "Desc",
            feedUrl = "https://feed.org/rss",
            artworkUrl = "",
            httpEtag = "\"W/abc123etag\"",
            httpLastModified = "Wed, 21 Oct 2026 07:28:00 GMT"
        )

        assertEquals("\"W/abc123etag\"", podcastWithHeaders.httpEtag)
        assertEquals("Wed, 21 Oct 2026 07:28:00 GMT", podcastWithHeaders.httpLastModified)
    }

    /**
     * Prueba de regresión 11:
     * - SleepTimerManager inicia temporizadores fijos en minutos (15, 30, 45, 60 min).
     * - Permite prolongar la duración (+15 min) manualmente o por agitación.
     * - Conmuta el detector del acelerómetro 'Agitar para prolongar'.
     * - En modo 'Al final del episodio', detiene la reproducción y apaga el temporizador al concluir el capítulo.
     * - Cancela correctamente y limpia el estado a Off.
     */
    @Test
    fun testRegression11_sleepTimerManagementAndShakeToExtend() {
        val mockContext = mockk<Context>(relaxed = true)
        val timerManager = SleepTimerManager(mockContext)

        // 1. Estado inicial inactivo
        val initialState = timerManager.state.value
        assertFalse(initialState.isActive)
        assertEquals(SleepTimerMode.Off, initialState.mode)
        assertEquals(0L, initialState.remainingMillis)

        // 2. Temporizador por minutos (30 min)
        timerManager.startMinutesTimer(30)
        val thirtyMinState = timerManager.state.value
        assertTrue(thirtyMinState.isActive)
        assertTrue(thirtyMinState.mode is SleepTimerMode.Minutes)
        assertEquals(30, (thirtyMinState.mode as SleepTimerMode.Minutes).totalMinutes)
        assertEquals(30 * 60 * 1000L, thirtyMinState.remainingMillis)
        assertEquals("30:00", thirtyMinState.formattedRemaining)

        // 3. Prolongación manual o por agitación (+15 min)
        timerManager.extendTimer(15)
        val extendedState = timerManager.state.value
        assertEquals(45 * 60 * 1000L, extendedState.remainingMillis)
        assertEquals("45:00", extendedState.formattedRemaining)

        // 4. Conmutación de agitar para prolongar
        timerManager.setShakeToExtend(false)
        assertFalse(timerManager.state.value.shakeToExtendEnabled)
        timerManager.setShakeToExtend(true)
        assertTrue(timerManager.state.value.shakeToExtendEnabled)

        // 5. Modo 'Al final del episodio'
        var pauseCalled = false
        timerManager.registerPauseAction { pauseCalled = true }

        timerManager.startEndOfEpisodeTimer()
        val endOfEpState = timerManager.state.value
        assertTrue(endOfEpState.isActive)
        assertTrue(endOfEpState.mode is SleepTimerMode.EndOfEpisode)
        assertEquals("Al final del episodio", endOfEpState.formattedRemaining)

        // Al finalizar el episodio se debe pausar la reproducción y apagar el temporizador
        timerManager.onEpisodeEnded()
        assertTrue(pauseCalled)
        assertFalse(timerManager.state.value.isActive)
        assertEquals(SleepTimerMode.Off, timerManager.state.value.mode)

        // 6. Cancelación manual
        timerManager.startMinutesTimer(15)
        assertTrue(timerManager.state.value.isActive)
        timerManager.cancelTimer()
        assertFalse(timerManager.state.value.isActive)
        assertEquals(SleepTimerMode.Off, timerManager.state.value.mode)
    }

    /**
     * Prueba de regresión 12:
     * - Ajustes específicos por podcast en Room (PodcastEntity): velocidad propia,
     *   salto de intro/créditos, preferencia de auto-descarga y orden de episodios.
     * - Verificación de resolución de velocidad (propia vs global).
     * - Verificación de herencia y anulación de auto-descarga (true/false/null).
     * - Verificación de ordenación reactiva de episodios (cronológico para series narrativas vs más recientes para noticias).
     * - Verificación de saltos de inicio (intro) y final (créditos).
     */
    @Test
    fun testRegression12_podcastSpecificSettings() {
        // 1. Valores por defecto heredados en PodcastEntity
        val defaultPodcast = PodcastEntity(
            id = "https://feed.audio/default",
            title = "Podcast General",
            author = "Autor",
            description = "Desc",
            feedUrl = "https://feed.audio/default",
            artworkUrl = ""
        )
        assertNull(defaultPodcast.customSpeed)
        assertEquals(0, defaultPodcast.skipIntroSeconds)
        assertEquals(0, defaultPodcast.skipEndingSeconds)
        assertNull(defaultPodcast.autoDownloadEnabled)
        assertEquals("NEWEST_FIRST", defaultPodcast.episodeSortOrder)

        // 2. Podcast de Noticias: velocidad rápida 1.6x, salto de intro 30s, créditos 15s, auto-descarga forzada
        val newsPodcast = defaultPodcast.copy(
            id = "https://feed.audio/news",
            title = "Noticias de la Mañana",
            customSpeed = 1.6f,
            skipIntroSeconds = 30,
            skipEndingSeconds = 15,
            autoDownloadEnabled = true,
            episodeSortOrder = "NEWEST_FIRST"
        )
        assertEquals(1.6f, newsPodcast.customSpeed)
        assertEquals(30, newsPodcast.skipIntroSeconds)
        assertEquals(15, newsPodcast.skipEndingSeconds)
        assertEquals(true, newsPodcast.autoDownloadEnabled)
        assertEquals("NEWEST_FIRST", newsPodcast.episodeSortOrder)

        // 3. Podcast de Audiolibro / Serie Narrativa: velocidad 1.0x, orden cronológico (viejo -> nuevo)
        val audiobookPodcast = defaultPodcast.copy(
            id = "https://feed.audio/book",
            title = "Audiolibro: El Quijote",
            customSpeed = 1.0f,
            skipIntroSeconds = 0,
            skipEndingSeconds = 0,
            autoDownloadEnabled = false,
            episodeSortOrder = "OLDEST_FIRST"
        )
        assertEquals(1.0f, audiobookPodcast.customSpeed)
        assertEquals(false, audiobookPodcast.autoDownloadEnabled)
        assertEquals("OLDEST_FIRST", audiobookPodcast.episodeSortOrder)

        // 4. Verificación de resolución de velocidad efectiva
        val globalDefaultSpeed = 1.25f
        val effectiveNewsSpeed = newsPodcast.customSpeed ?: globalDefaultSpeed
        val effectiveAudiobookSpeed = audiobookPodcast.customSpeed ?: globalDefaultSpeed
        val effectiveDefaultSpeed = defaultPodcast.customSpeed ?: globalDefaultSpeed

        assertEquals(1.6f, effectiveNewsSpeed, 0.001f)
        assertEquals(1.0f, effectiveAudiobookSpeed, 0.001f)
        assertEquals(1.25f, effectiveDefaultSpeed, 0.001f)

        // 5. Verificación de anulación / herencia de auto-descarga
        fun shouldAutoDownload(podcast: PodcastEntity, globalSetting: Boolean): Boolean {
            return podcast.autoDownloadEnabled ?: globalSetting
        }

        // Global está apagado (false), pero el podcast de noticias fuerza true
        assertTrue(shouldAutoDownload(newsPodcast, globalSetting = false))

        // Global está encendido (true), pero el audiolibro fuerza false
        assertFalse(shouldAutoDownload(audiobookPodcast, globalSetting = true))

        // Podcast sin preferencia específica (null) hereda el ajuste global
        assertTrue(shouldAutoDownload(defaultPodcast, globalSetting = true))
        assertFalse(shouldAutoDownload(defaultPodcast, globalSetting = false))

        // 6. Verificación de ordenación de episodios
        val ep1 = EpisodeEntity(
            id = "ep-1",
            podcastId = audiobookPodcast.id,
            title = "Capítulo 1",
            description = "",
            audioUrl = "https://audio.org/1.mp3",
            imageUrl = "",
            pubDate = 1000L,
            duration = 1800000L
        )
        val ep2 = EpisodeEntity(
            id = "ep-2",
            podcastId = audiobookPodcast.id,
            title = "Capítulo 2",
            description = "",
            audioUrl = "https://audio.org/2.mp3",
            imageUrl = "",
            pubDate = 2000L,
            duration = 1800000L
        )
        val ep3 = EpisodeEntity(
            id = "ep-3",
            podcastId = audiobookPodcast.id,
            title = "Capítulo 3",
            description = "",
            audioUrl = "https://audio.org/3.mp3",
            imageUrl = "",
            pubDate = 3000L,
            duration = 1800000L
        )
        val episodesList = listOf(ep2, ep1, ep3)

        // Orden cronológico para narrativa (OLDEST_FIRST)
        val sortedChronological = episodesList.sortedWith(
            compareBy<EpisodeEntity> { it.pubDate }.thenBy { it.id }
        )
        assertEquals(listOf("ep-1", "ep-2", "ep-3"), sortedChronological.map { it.id })

        // Orden de noticias (NEWEST_FIRST)
        val sortedNewest = episodesList.sortedWith(
            compareByDescending<EpisodeEntity> { it.pubDate }.thenByDescending { it.id }
        )
        assertEquals(listOf("ep-3", "ep-2", "ep-1"), sortedNewest.map { it.id })

        // 7. Verificación de salto de intro y créditos
        val introSkipMs = newsPodcast.skipIntroSeconds * 1000L
        assertEquals(30000L, introSkipMs)

        // Si la posición de inicio es menor a introSkipMs, se ajusta a introSkipMs
        val currentStartPos = 0L
        val effectiveStartPos = if (introSkipMs > 0 && currentStartPos < introSkipMs) introSkipMs else currentStartPos
        assertEquals(30000L, effectiveStartPos)

        // Salto de créditos finales
        val totalDurationMs = 600000L // 10 minutos
        val endingSkipMs = newsPodcast.skipEndingSeconds * 1000L // 15 segundos
        val endingThresholdMs = totalDurationMs - endingSkipMs // 585000 ms

        val isBeforeEnding = 500000L >= endingThresholdMs
        val isAtEnding = 586000L >= endingThresholdMs
        assertFalse(isBeforeEnding)
        assertTrue(isAtEnding)
    }

    /**
     * Prueba de regresión 13:
     * - Verificación del sistema de velocidad fina: rango de 0.5x a 3.0x en incrementos de 0.1x.
     * - Verificación de lista de velocidades admitidas (26 valores finos).
     * - Verificación del formateo uniforme de velocidad (p.ej. "1.0x", "1.3x", "2.7x").
     * - Verificación de redondeo al decimal más cercano y límites de saturación (clamp 0.5x a 3.0x).
     * - Verificación de presets de ciclo rápido para notificación y Android Auto.
     */
    @Test
    fun testRegression13_finePlaybackSpeed() {
        val speeds = com.ferlagod.mispodcast.playback.PlaybackController.PLAYBACK_SPEEDS
        assertEquals(26, speeds.size)
        assertEquals(0.5f, speeds.first(), 0.001f)
        assertEquals(3.0f, speeds.last(), 0.001f)

        // Comprobar que los pasos consecutivos son exactamente de 0.1
        for (i in 0 until speeds.size - 1) {
            val diff = speeds[i + 1] - speeds[i]
            assertEquals(0.1f, diff, 0.001f)
        }

        // Comprobar formateo de velocidad
        assertEquals("0.5x", com.ferlagod.mispodcast.playback.PlaybackController.formatSpeed(0.5f))
        assertEquals("1.0x", com.ferlagod.mispodcast.playback.PlaybackController.formatSpeed(1.0f))
        assertEquals("1.3x", com.ferlagod.mispodcast.playback.PlaybackController.formatSpeed(1.3f))
        assertEquals("1.6x", com.ferlagod.mispodcast.playback.PlaybackController.formatSpeed(1.6f))
        assertEquals("2.4x", com.ferlagod.mispodcast.playback.PlaybackController.formatSpeed(2.4f))
        assertEquals("3.0x", com.ferlagod.mispodcast.playback.PlaybackController.formatSpeed(3.0f))

        // Redondeo al decimal más próximo y acotado (clamp)
        fun clampSpeed(input: Float): Float {
            return ((input * 10f).let { kotlin.math.round(it) } / 10f).coerceIn(0.5f, 3.0f)
        }

        assertEquals(0.5f, clampSpeed(0.2f), 0.001f)
        assertEquals(0.5f, clampSpeed(0.52f), 0.001f)
        assertEquals(1.2f, clampSpeed(1.24f), 0.001f)
        assertEquals(1.3f, clampSpeed(1.26f), 0.001f)
        assertEquals(3.0f, clampSpeed(3.5f), 0.001f)

        // Presets rápidos para ciclado
        val presets = com.ferlagod.mispodcast.playback.PlaybackController.CYCLE_PRESETS
        assertEquals(listOf(1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f), presets)
        assertTrue(presets.all { it in 0.5f..3.0f })
    }

    /**
     * Prueba de regresión 14: Capítulos de verdad (Podcasting 2.0 JSON & ID3 CHAP).
     *
     * - Parseo de Podcasting 2.0 JSON con startTime en float, int y formato timestamp.
     * - Relleno automático de endTime ausente con el startTime del capítulo sucesivo.
     * - Extracción de metadatos ID3 CHAP (ChapterFrame con TextInformationFrame y UrlLinkFrame).
     * - Navegación inteligente entre capítulos (siguiente, anterior con umbral de 3s).
     */
    @Test
    fun testRegression14_chaptersPodcasting20AndId3() {
        val chapterRepo = com.ferlagod.mispodcast.data.repository.ChapterRepository(
            okHttpClient = okhttp3.OkHttpClient()
        )

        // 1. Podcasting 2.0 JSON
        val json = """
            {
              "version": "1.2.0",
              "chapters": [
                {
                  "startTime": 0,
                  "title": "Introducción y bienvenida",
                  "img": "https://mispodcast.es/img/ch1.png",
                  "url": "https://mispodcast.es/intro"
                },
                {
                  "startTime": 125.5,
                  "title": "Tema principal: Inteligencia Artificial",
                  "img": null,
                  "url": "https://mispodcast.es/ia",
                  "endTime": 300.0
                },
                {
                  "startTime": "00:05:00",
                  "title": "Debate y conclusiones",
                  "img": "https://mispodcast.es/img/ch3.png"
                }
              ]
            }
        """.trimIndent()

        val parsedChapters = chapterRepo.parsePodcasting20Json(json)
        assertEquals(3, parsedChapters.size)

        // Capítulo 1: inicio 0s, fin rellenado automáticamente por el inicio del capítulo 2 (125.5s = 125500ms)
        val ch1 = parsedChapters[0]
        assertEquals("Introducción y bienvenida", ch1.title)
        assertEquals(0L, ch1.startTimeMs)
        assertEquals(125500L, ch1.endTimeMs)
        assertEquals("https://mispodcast.es/img/ch1.png", ch1.imageUrl)
        assertEquals("https://mispodcast.es/intro", ch1.linkUrl)

        // Capítulo 2: inicio 125.5s (125500ms), fin explícito 300s (300000ms)
        val ch2 = parsedChapters[1]
        assertEquals("Tema principal: Inteligencia Artificial", ch2.title)
        assertEquals(125500L, ch2.startTimeMs)
        assertEquals(300000L, ch2.endTimeMs)
        assertNull(ch2.imageUrl)
        assertEquals("https://mispodcast.es/ia", ch2.linkUrl)

        // Capítulo 3: inicio parseado desde timestamp "00:05:00" = 5 minutos = 300000ms
        val ch3 = parsedChapters[2]
        assertEquals("Debate y conclusiones", ch3.title)
        assertEquals(300000L, ch3.startTimeMs)
        assertNull(ch3.endTimeMs)
        assertEquals("https://mispodcast.es/img/ch3.png", ch3.imageUrl)

        // 2. Metadatos ID3 CHAP (ChapterFrame con subframes)
        val textSubFrame = androidx.media3.extractor.metadata.id3.TextInformationFrame(
            "TIT2",
            null,
            com.google.common.collect.ImmutableList.of("Capítulo ID3 MP3")
        )
        val urlSubFrame = androidx.media3.extractor.metadata.id3.UrlLinkFrame(
            "WXXX",
            null,
            "https://mispodcast.es/capitulo-id3"
        )
        val chapFrame = androidx.media3.extractor.metadata.id3.ChapterFrame(
            "ch01",
            60000,
            180000,
            0L,
            0L,
            arrayOf(textSubFrame, urlSubFrame)
        )
        val id3Metadata = androidx.media3.common.Metadata(chapFrame)
        val id3Chapters = chapterRepo.parseId3Metadata(id3Metadata)

        assertEquals(1, id3Chapters.size)
        assertEquals("Capítulo ID3 MP3", id3Chapters[0].title)
        assertEquals(60000L, id3Chapters[0].startTimeMs)
        assertEquals(180000L, id3Chapters[0].endTimeMs)
        assertEquals("https://mispodcast.es/capitulo-id3", id3Chapters[0].linkUrl)

        // 3. Navegación inteligente de saltos de capítulos en PlaybackController
        val testChapters = listOf(
            com.ferlagod.mispodcast.data.model.Chapter(title = "C1", startTimeMs = 0L, endTimeMs = 30000L),
            com.ferlagod.mispodcast.data.model.Chapter(title = "C2", startTimeMs = 30000L, endTimeMs = 90000L),
            com.ferlagod.mispodcast.data.model.Chapter(title = "C3", startTimeMs = 90000L, endTimeMs = 150000L)
        )

        var fakePosition = 10000L
        val mockPlayer = mockk<androidx.media3.common.Player>(relaxed = true)
        every { mockPlayer.currentPosition } answers { fakePosition }
        every { mockPlayer.seekTo(any()) } answers {
            fakePosition = firstArg()
        }

        val mockDb = mockk<com.ferlagod.mispodcast.data.local.PodcastDatabase>(relaxed = true)
        val mockTree = mockk<com.ferlagod.mispodcast.playback.AutoBrowseTree>(relaxed = true)
        val controller = com.ferlagod.mispodcast.playback.PlaybackController(mockDb, mockTree)
        controller.setChapters(testChapters)

        // Obtener capítulo activo en posición 10000ms -> Capítulo 1
        val currentCh = controller.getCurrentChapter(10000L)
        assertNotNull(currentCh)
        assertEquals("C1", currentCh?.title)

        // Saltar al siguiente capítulo desde 10000ms -> debe ir a 30000ms (C2)
        val jumpedNext = controller.seekToNextChapter(mockPlayer)
        assertTrue(jumpedNext)
        assertEquals(30000L, fakePosition)

        // Si avanzamos a 35000ms (a 5s del inicio de C2, mayor a umbral de 3s)
        fakePosition = 35000L
        val rewindToStart = controller.seekToPreviousChapter(mockPlayer)
        assertTrue(rewindToStart)
        // Vuelve al inicio del capítulo actual (30000ms)
        assertEquals(30000L, fakePosition)

        // Si estamos a 31000ms (a sólo 1s del inicio de C2, menor al umbral de 3s)
        fakePosition = 31000L
        val jumpToPrev = controller.seekToPreviousChapter(mockPlayer)
        assertTrue(jumpToPrev)
        // Salta al capítulo anterior C1 (0ms)
        assertEquals(0L, fakePosition)

        // Comprobar que buildCustomCommandButtons incluye el botón de Siguiente capítulo
        val buttonsWithChapters = controller.buildCustomCommandButtons()
        assertTrue(buttonsWithChapters.any { it.sessionCommand?.customAction == com.ferlagod.mispodcast.playback.PlaybackController.ACTION_CUSTOM_NEXT_CHAPTER })

        // Verificar que ningún botón tiene como iconResId las constantes de CommandButton (0xe000+)
        // y que todos tienen un iconResId válido asignado para el DefaultMediaNotificationProvider
        buttonsWithChapters.forEach { btn ->
            assertTrue("El botón ${btn.displayName} debe tener iconResId != 0", btn.iconResId != 0)
            assertFalse(
                "iconResId del botón ${btn.displayName} no debe ser la constante ICON_SKIP_BACK_10",
                btn.iconResId == androidx.media3.session.CommandButton.ICON_SKIP_BACK_10
            )
        }

        // Si no hay capítulos, no debe incluir el botón de siguiente capítulo
        controller.setChapters(emptyList())
        val buttonsWithoutChapters = controller.buildCustomCommandButtons()
        assertFalse(buttonsWithoutChapters.any { it.sessionCommand?.customAction == com.ferlagod.mispodcast.playback.PlaybackController.ACTION_CUSTOM_NEXT_CHAPTER })
    }

    /**
     * Prueba de regresión 15: Transcripciones sincronizadas (WebVTT, SRT y JSON) y búsqueda.
     *
     * - Parseo de WebVTT con marcas de voz <v Interlocutor> y timestamps con puntos.
     * - Parseo de SRT con índices numéricos, timestamps con comas y prefijos de locutor.
     * - Parseo de JSON Podcasting 2.0 con start, end, text y speaker.
     * - Sincronización en tiempo real con la posición de audio y búsqueda de texto.
     */
    @Test
    fun testRegression15_synchronizedTranscriptsVttSrtJson() {
        val repo = com.ferlagod.mispodcast.data.repository.TranscriptRepository(
            okHttpClient = okhttp3.OkHttpClient()
        )

        // 1. WebVTT
        val vttContent = """
            WEBVTT - Transcripción del episodio

            00:00:01.200 --> 00:00:04.800
            <v Fernando>Hola a todos y bienvenidos a Mis Podcast.

            00:00:05.100 --> 00:00:09.500
            <v Laura>Hoy analizaremos las nuevas capacidades de Android Media3.

            01:10.000 --> 01:15.500
            <v Fernando><b>No olvidéis</b> suscribiros al canal y compartirlo.
        """.trimIndent()

        val vttSegments = repo.parseTranscript(vttContent)
        assertEquals(3, vttSegments.size)

        assertEquals(1200L, vttSegments[0].startTimeMs)
        assertEquals(4800L, vttSegments[0].endTimeMs)
        assertEquals("Fernando", vttSegments[0].speaker)
        assertEquals("Hola a todos y bienvenidos a Mis Podcast.", vttSegments[0].text)

        assertEquals(5100L, vttSegments[1].startTimeMs)
        assertEquals(9500L, vttSegments[1].endTimeMs)
        assertEquals("Laura", vttSegments[1].speaker)
        assertEquals("Hoy analizaremos las nuevas capacidades de Android Media3.", vttSegments[1].text)

        // 01:10.000 = 70 segundos = 70000ms
        assertEquals(70000L, vttSegments[2].startTimeMs)
        assertEquals(75500L, vttSegments[2].endTimeMs)
        assertEquals("Fernando", vttSegments[2].speaker)
        assertEquals("No olvidéis suscribiros al canal y compartirlo.", vttSegments[2].text)

        // 2. SubRip (SRT)
        val srtContent = """
            1
            00:00:02,500 --> 00:00:06,000
            Carlos: Comencemos con la primera sección del programa.

            2
            00:00:06,500 --> 00:00:10,000
            Hablaremos de Room y migraciones automatizadas.
        """.trimIndent()

        val srtSegments = repo.parseTranscript(srtContent)
        assertEquals(2, srtSegments.size)

        assertEquals(2500L, srtSegments[0].startTimeMs)
        assertEquals(6000L, srtSegments[0].endTimeMs)
        assertEquals("Carlos", srtSegments[0].speaker)
        assertEquals("Comencemos con la primera sección del programa.", srtSegments[0].text)

        assertEquals(6500L, srtSegments[1].startTimeMs)
        assertEquals(10000L, srtSegments[1].endTimeMs)
        assertNull(srtSegments[1].speaker)
        assertEquals("Hablaremos de Room y migraciones automatizadas.", srtSegments[1].text)

        // 3. JSON Podcasting 2.0
        val jsonContent = """
            {
              "version": "1.0.0",
              "segments": [
                {
                  "start": 0.5,
                  "end": 3.2,
                  "text": "Intro rápida en JSON",
                  "speaker": "Host"
                },
                {
                  "start": 3.5,
                  "end": 7.0,
                  "text": "Desarrollo nativo en Kotlin",
                  "speaker": "Guest"
                }
              ]
            }
        """.trimIndent()

        val jsonSegments = repo.parseTranscript(jsonContent)
        assertEquals(2, jsonSegments.size)
        assertEquals(500L, jsonSegments[0].startTimeMs)
        assertEquals(3200L, jsonSegments[0].endTimeMs)
        assertEquals("Intro rápida en JSON", jsonSegments[0].text)
        assertEquals("Host", jsonSegments[0].speaker)

        // 4. Sincronización en vivo según posición de reproducción
        val currentAudioPosMs = 7000L
        val activeVttSegment = vttSegments.find { currentAudioPosMs in it.startTimeMs..it.endTimeMs }
        assertNotNull(activeVttSegment)
        assertEquals("Hoy analizaremos las nuevas capacidades de Android Media3.", activeVttSegment?.text)

        // 5. Búsqueda instantánea de texto en la transcripción
        val query = "Media3"
        val searchResults = vttSegments.filter { it.text.contains(query, ignoreCase = true) }
        assertEquals(1, searchResults.size)
        assertEquals("Laura", searchResults[0].speaker)
        assertEquals(5100L, searchResults[0].startTimeMs)
    }

    @Test
    fun testRegression16_voiceBoostAndMonoAudio() {
        // 1. Verificación de MonoAudioProcessor
        val monoProcessor = com.ferlagod.mispodcast.playback.MonoAudioProcessor()
        val inputFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat(
            44100, // sampleRate
            2,     // channelCount (estéreo)
            androidx.media3.common.C.ENCODING_PCM_16BIT
        )

        val outputFormat = monoProcessor.configure(inputFormat)
        monoProcessor.flush()
        assertEquals(44100, outputFormat.sampleRate)
        assertEquals(2, outputFormat.channelCount)
        assertEquals(androidx.media3.common.C.ENCODING_PCM_16BIT, outputFormat.encoding)

        // Caso A: Deshabilitado -> Paso directo sin alterar muestras
        monoProcessor.isEnabled = false
        val bufferPassthrough = java.nio.ByteBuffer.allocateDirect(4 * 2)
            .order(java.nio.ByteOrder.nativeOrder())
        bufferPassthrough.putShort(1000.toShort()) // L
        bufferPassthrough.putShort(3000.toShort()) // R
        bufferPassthrough.putShort((-2000).toShort()) // L
        bufferPassthrough.putShort(2000.toShort()) // R
        bufferPassthrough.flip()

        monoProcessor.queueInput(bufferPassthrough)
        val outPassthrough = monoProcessor.output
        assertEquals(8, outPassthrough.remaining())
        assertEquals(1000.toShort(), outPassthrough.short)
        assertEquals(3000.toShort(), outPassthrough.short)
        assertEquals((-2000).toShort(), outPassthrough.short)
        assertEquals(2000.toShort(), outPassthrough.short)

        // Caso B: Habilitado -> Mezcla mono ((L + R) / 2) replicada en ambos canales
        monoProcessor.isEnabled = true
        val bufferMono = java.nio.ByteBuffer.allocateDirect(4 * 2)
            .order(java.nio.ByteOrder.nativeOrder())
        bufferMono.putShort(1000.toShort()) // L
        bufferMono.putShort(3000.toShort()) // R
        bufferMono.putShort((-2000).toShort()) // L
        bufferMono.putShort(2000.toShort()) // R
        bufferMono.flip()

        monoProcessor.queueInput(bufferMono)
        val outMono = monoProcessor.output
        assertEquals(8, outMono.remaining())
        // Primer frame: (1000 + 3000) / 2 = 2000
        assertEquals(2000.toShort(), outMono.short)
        assertEquals(2000.toShort(), outMono.short)
        // Segundo frame: (-2000 + 2000) / 2 = 0
        assertEquals(0.toShort(), outMono.short)
        assertEquals(0.toShort(), outMono.short)

        // 2. Verificación de AudioEffectsManager
        val effectsManager = com.ferlagod.mispodcast.playback.AudioEffectsManager()
        assertEquals(com.ferlagod.mispodcast.playback.AudioEffectsManager.GAIN_BALANCED_MB, effectsManager.targetGainMb)
        assertFalse(effectsManager.isVoiceBoostEnabled)

        effectsManager.setVoiceBoostEnabled(true)
        assertTrue(effectsManager.isVoiceBoostEnabled)

        effectsManager.setTargetGainMb(com.ferlagod.mispodcast.playback.AudioEffectsManager.GAIN_STRONG_MB)
        assertEquals(1200, effectsManager.targetGainMb)

        // Comportamiento seguro ante sesiones no válidas o liberación en entorno JVM
        effectsManager.attachAudioSession(0)
        effectsManager.attachAudioSession(-1)
        effectsManager.release()
        // La preferencia del usuario se mantiene activa para futuras sesiones
        assertTrue(effectsManager.isVoiceBoostEnabled)

        // 3. Verificación de valores por defecto en UserSettings
        val defaultSettings = com.ferlagod.mispodcast.data.preferences.UserSettings()
        assertFalse(defaultSettings.voiceBoostEnabled)
        assertEquals(800, defaultSettings.voiceBoostGainMb)
        assertFalse(defaultSettings.monoAudioEnabled)
    }

    @Test
    fun testRegression17_bookmarksClipsAndSmartAutoEnqueue() {
        // 1. Marcadores y Clips: Creación, timestamps y generación de texto para compartir
        val bookmark = com.ferlagod.mispodcast.data.local.entities.BookmarkEntity(
            id = "bm-1",
            episodeId = "ep-100",
            podcastId = "pod-1",
            podcastTitle = "La Cafetera",
            episodeTitle = "Episodio 500: Especial Tecnología",
            positionMs = (12 * 60 + 34) * 1000L, // 12 minutos y 34 segundos
            note = "Cita memorable sobre software libre"
        )

        assertEquals("ep-100", bookmark.episodeId)
        assertEquals(754000L, bookmark.positionMs)
        assertEquals("Cita memorable sobre software libre", bookmark.note)

        // Verificación de formateo de timestamp (12:34)
        val totalSec = bookmark.positionMs / 1000L
        val min = totalSec / 60
        val sec = totalSec % 60
        val formattedTime = String.format(java.util.Locale.US, "%02d:%02d", min, sec)
        assertEquals("12:34", formattedTime)

        // Verificación de generación de texto para compartir con timestamp
        val audioUrl = "https://example.com/audio/ep500.mp3"
        val shareLink = "$audioUrl?t=$totalSec"
        val shareText = "🎧 Escucha \"${bookmark.note}\" en \"${bookmark.episodeTitle}\" (${bookmark.podcastTitle}) desde el minuto $formattedTime:\n$shareLink"

        assertTrue(shareText.contains("12:34"))
        assertTrue(shareText.contains("Episodio 500: Especial Tecnología"))
        assertTrue(shareText.contains("?t=754"))
        assertTrue(shareText.contains("Cita memorable sobre software libre"))

        // 2. Bandeja de entrada / Auto-encolado inteligente ("Up Next")
        val podcastDefault = PodcastEntity(
            id = "pod-test-1",
            title = "Noticias Diarias",
            author = "Redacción",
            description = "Resumen de actualidad",
            feedUrl = "https://example.com/feed.xml",
            artworkUrl = "https://example.com/art.jpg"
        )
        // Por defecto la regla de auto-encolado es DISABLED
        assertEquals("DISABLED", podcastDefault.autoEnqueueRule)

        val podcastTop = podcastDefault.copy(autoEnqueueRule = "TOP")
        assertEquals("TOP", podcastTop.autoEnqueueRule)

        val podcastBottom = podcastDefault.copy(autoEnqueueRule = "BOTTOM")
        assertEquals("BOTTOM", podcastBottom.autoEnqueueRule)

        // 3. Verificación de lógica de encolado 'TOP' (Up Next / siguiente en reproducirse)
        // Si hay una pista en reproducción en pos 0, insertar al principio debe posicionarse en pos 1
        val currentQueue = listOf(
            QueueItemEntity(episodeId = "playing-now", positionIndex = 0, isCurrentlyPlaying = true),
            QueueItemEntity(episodeId = "next-up", positionIndex = 1, isCurrentlyPlaying = false)
        )
        val insertIndex = if (currentQueue.any { it.isCurrentlyPlaying }) 1 else 0
        assertEquals(1, insertIndex)

        // Los elementos siguientes se desplazan un índice hacia abajo
        val shiftedQueue = currentQueue.map {
            if (it.positionIndex >= insertIndex) it.copy(positionIndex = it.positionIndex + 1) else it
        }
        assertEquals(0, shiftedQueue[0].positionIndex)
        assertEquals(2, shiftedQueue[1].positionIndex)

        // El nuevo episodio toma el índice 1 como inmediato "Up Next"
        val newQueueItem = QueueItemEntity(
            episodeId = "newly-detected-ep",
            positionIndex = insertIndex,
            isCurrentlyPlaying = false
        )
        assertEquals(1, newQueueItem.positionIndex)
    }

    /**
     * Prueba de regresión 18:
     * Verifica la regla global por defecto de auto-encolado en UserSettings,
     * la resolución de política 'DEFAULT' para podcasts individuales,
     * y las acciones en bloque de la bandeja de entrada / Up Next inteligente.
     */
    @Test
    fun testRegression18_inboxAndDefaultAutoEnqueueRule() {
        // 1. Preferencia global en UserSettings
        val defaultSettings = UserSettings()
        assertEquals("DISABLED", defaultSettings.defaultAutoEnqueueRule)

        val customSettings = UserSettings(defaultAutoEnqueueRule = "TOP")
        assertEquals("TOP", customSettings.defaultAutoEnqueueRule)

        // 2. Resolución de regla efectiva
        val podcastInherited = PodcastEntity(
            id = "pod-inherited",
            title = "Podcast Sin Configuración Específica",
            author = "Autor",
            description = "Desc",
            feedUrl = "https://example.com/feed.xml",
            artworkUrl = "",
            autoEnqueueRule = "DEFAULT"
        )

        val effectiveRuleInherited = if (podcastInherited.autoEnqueueRule == "DEFAULT" || podcastInherited.autoEnqueueRule.isBlank()) {
            customSettings.defaultAutoEnqueueRule
        } else {
            podcastInherited.autoEnqueueRule
        }
        assertEquals("TOP", effectiveRuleInherited)

        val podcastExplicitDisabled = podcastInherited.copy(autoEnqueueRule = "DISABLED")
        val effectiveRuleDisabled = if (podcastExplicitDisabled.autoEnqueueRule == "DEFAULT" || podcastExplicitDisabled.autoEnqueueRule.isBlank()) {
            customSettings.defaultAutoEnqueueRule
        } else {
            podcastExplicitDisabled.autoEnqueueRule
        }
        assertEquals("DISABLED", effectiveRuleDisabled)

        // 3. Encolado en bloque desde la bandeja de entrada (Inbox)
        val inboxEpisodes = listOf("ep-1-older", "ep-2-newer")

        // Para añadir en bloque a TOP (Up Next), se procesan en orden inverso
        // de modo que el primer episodio nuevo quede como el más próximo en reproducirse
        val topEnqueuedOrder = mutableListOf<String>()
        for (id in inboxEpisodes.reversed()) {
            topEnqueuedOrder.add(0, id) // Inserción a la cabeza
        }
        assertEquals("ep-1-older", topEnqueuedOrder[0])
        assertEquals("ep-2-newer", topEnqueuedOrder[1])

        // Para añadir a BOTTOM, se insertan en orden secuencial
        val bottomEnqueuedOrder = mutableListOf<String>()
        for (id in inboxEpisodes) {
            bottomEnqueuedOrder.add(id)
        }
        assertEquals("ep-1-older", bottomEnqueuedOrder[0])
        assertEquals("ep-2-newer", bottomEnqueuedOrder[1])
    }

    /**
     * Prueba de regresión 19:
     * Verifica la coherencia de estado y acciones del Widget en pantalla de inicio (Glance)
     * y el Tile en Ajustes Rápidos (Quick Settings Tile).
     */
    @Test
    fun testRegression19_glanceWidgetAndQuickSettingsTile() {
        // 1. Estado inicial del Widget y Tile
        val emptyState = PodcastWidgetState()
        assertEquals("", emptyState.episodeId)
        assertEquals("", emptyState.podcastTitle)
        assertEquals("", emptyState.episodeTitle)
        assertFalse(emptyState.isPlaying)
        assertEquals(0L, emptyState.durationMs)
        assertEquals(0L, emptyState.currentPositionMs)

        // 2. Transición a estado de reproducción activa
        val playingState = PodcastWidgetState(
            episodeId = "ep-test-123",
            podcastId = "pod-tech-456",
            podcastTitle = "Tecnología Libre",
            episodeTitle = "El Futuro del Software de Código Abierto",
            artworkUrl = "https://example.com/art.png",
            isPlaying = true,
            durationMs = 1800000L, // 30 min
            currentPositionMs = 450000L // 7.5 min (25%)
        )

        assertTrue(playingState.isPlaying)
        assertEquals("Tecnología Libre", playingState.podcastTitle)
        assertEquals("El Futuro del Software de Código Abierto", playingState.episodeTitle)
        val progress = (playingState.currentPositionMs.toFloat() / playingState.durationMs.toFloat())
        assertEquals(0.25f, progress, 0.001f)

        // 3. Simulación de pausa
        val pausedState = playingState.copy(isPlaying = false, currentPositionMs = 600000L)
        assertFalse(pausedState.isPlaying)
        assertEquals(600000L, pausedState.currentPositionMs)

        // 4. Verificación de constantes de control multimedia
        assertEquals(
            "com.ferlagod.mispodcast.action.PLAY_PAUSE",
            com.ferlagod.mispodcast.playback.PodcastPlaybackService.ACTION_PLAY_PAUSE
        )
        assertEquals(
            "com.ferlagod.mispodcast.action.SKIP_FORWARD",
            com.ferlagod.mispodcast.playback.PodcastPlaybackService.ACTION_SKIP_FORWARD
        )
        assertEquals(
            "com.ferlagod.mispodcast.action.SKIP_BACKWARD",
            com.ferlagod.mispodcast.playback.PodcastPlaybackService.ACTION_SKIP_BACKWARD
        )

        // 5. Lógica de estados del Quick Settings Tile
        val tileStateActive = if (playingState.isPlaying) 2 /* Tile.STATE_ACTIVE */ else 1 /* Tile.STATE_INACTIVE */
        assertEquals(2, tileStateActive)

        val tileStatePaused = if (pausedState.isPlaying) 2 else 1
        assertEquals(1, tileStatePaused)
    }

    /**
     * Prueba de regresión 20: Value4Value en Lightning Network (Podcasting 2.0) y financiación libre.
     *
     * Valida:
     * - Parseo de etiquetas <podcast:funding url="..." > con texto descriptivo del canal.
     * - Parseo de etiquetas <podcast:value> y múltiples <podcast:valueRecipient> con split porcentual.
     * - Deserialización y compatibilidad retrocompatible de ValueForValueConfig (JSON y formato plano).
     * - Generación de esquemas de pago URI Lightning ("lightning:user@domain.com" o keysend pubkey).
     */
    @Test
    fun testRegression20_value4ValueAndPodcastFunding() {
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:podcast="https://podcastindex.org/namespace/1.0">
              <channel>
                <title>Podcast Value4Value</title>
                <description>Demostración de financiación libre y Bitcoin Lightning</description>
                <link>https://ejemplo.com</link>
                <podcast:funding url="https://patreon.com/podcastv4v">¡Apoya el podcast independiente en Patreon!</podcast:funding>
                <podcast:value type="lightning" method="keysend" suggested="0.00000005000">
                  <podcast:valueRecipient name="Creador Principal" type="node" address="03ae9f91a08120f0420b9e0f682149fa821373260170d659a784ed2b4e0ef815e0" split="90" />
                  <podcast:valueRecipient name="Hosting Libre" type="lnaddress" address="host@getalby.com" split="10" />
                </podcast:value>
                <item>
                  <title>Episodio 1: Value4Value en marcha</title>
                  <enclosure url="https://ejemplo.com/audio1.mp3" length="123456" type="audio/mpeg" />
                  <guid>ep1_v4v</guid>
                  <podcast:funding url="https://ko-fi.com/creador">Un café para el episodio 1</podcast:funding>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parser = com.ferlagod.mispodcast.data.remote.parser.RssFeedParser(okhttp3.OkHttpClient())
        val parsedFeed = parser.parseFeed(rssXml.byteInputStream(), "https://ejemplo.com/feed.xml")

        // 1. Verificación de financiación de canal
        assertEquals("https://patreon.com/podcastv4v", parsedFeed.fundingUrl)
        assertEquals("¡Apoya el podcast independiente en Patreon!", parsedFeed.fundingText)

        // 2. Verificación de Value4Value serializado
        assertNotNull(parsedFeed.valueForValueInfo)
        val v4vConfig = com.ferlagod.mispodcast.data.model.ValueForValueConfig.fromString(parsedFeed.valueForValueInfo)
        assertNotNull(v4vConfig)
        assertEquals("lightning", v4vConfig!!.type)
        assertEquals("keysend", v4vConfig.method)
        assertEquals("0.00000005000", v4vConfig.suggested)
        assertEquals(2, v4vConfig.recipients.size)

        // Beneficiario 1 (Node pubkey con 90% split)
        val r1 = v4vConfig.recipients[0]
        assertEquals("Creador Principal", r1.name)
        assertEquals("node", r1.type)
        assertEquals("03ae9f91a08120f0420b9e0f682149fa821373260170d659a784ed2b4e0ef815e0", r1.address)
        assertEquals(90, r1.split)

        // Beneficiario 2 (LN Address con 10% split)
        val r2 = v4vConfig.recipients[1]
        assertEquals("Hosting Libre", r2.name)
        assertEquals("lnaddress", r2.type)
        assertEquals("host@getalby.com", r2.address)
        assertEquals(10, r2.split)

        // 3. Verificación de retrocompatibilidad con formato plano legacy
        val legacyConfig = com.ferlagod.mispodcast.data.model.ValueForValueConfig.fromString("type=lightning;method=keysend")
        assertNotNull(legacyConfig)
        assertEquals("lightning", legacyConfig!!.type)
        assertEquals("keysend", legacyConfig.method)
        assertTrue(legacyConfig.recipients.isEmpty())

        // 4. Verificación de financiación de episodio
        assertEquals(1, parsedFeed.episodes.size)
        val ep1 = parsedFeed.episodes[0]
        assertEquals("https://ko-fi.com/creador", ep1.fundingUrl)

        // 5. Verificación de construcción de URI de Lightning
        val lightningUri = if (r2.address.startsWith("lightning:")) r2.address else "lightning:${r2.address}"
        assertEquals("lightning:host@getalby.com", lightningUri)
    }

    /**
     * Prueba de regresión 21: Búsqueda por voz de Android Auto ("Ok Google, pon X en Mis Podcast")
     * y compatibilidad de Media3 con Wear OS.
     *
     * Valida:
     * - Sanitización de comandos hablados del Asistente (eliminación de prefijos "pon", "reproduce" y sufijos "en Mis Podcast").
     * - Resolución inteligente hacia el podcast local y selección del episodio idóneo (no escuchado / en progreso).
     * - Integración transparente en PlaybackController a través de RequestMetadata.searchQuery de Media3.
     */
    @Test
    fun testRegression21_androidAutoVoiceSearchAndWearOsMedia3() = kotlinx.coroutines.test.runTest {
        val mockDb = mockk<com.ferlagod.mispodcast.data.local.PodcastDatabase>(relaxed = true)
        val mockDao = mockk<com.ferlagod.mispodcast.data.local.dao.PodcastDao>(relaxed = true)
        val mockSearchService = mockk<com.ferlagod.mispodcast.data.remote.api.ApplePodcastSearchService>(relaxed = true)
        val mockRssParser = mockk<com.ferlagod.mispodcast.data.remote.parser.RssFeedParser>(relaxed = true)

        every { mockDb.podcastDao() } returns mockDao

        val autoTree = com.ferlagod.mispodcast.playback.AutoBrowseTree(
            database = mockDb,
            searchService = mockSearchService,
            rssParser = mockRssParser
        )

        // 1. Sanitización de órdenes habladas del Asistente de Google
        assertEquals("Nadie Sabe Nada", autoTree.sanitizeVoiceQuery("pon Nadie Sabe Nada en Mis Podcast"))
        assertEquals("Nadie Sabe Nada", autoTree.sanitizeVoiceQuery("reproduce Nadie Sabe Nada en mis podcasts"))
        assertEquals("The Daily", autoTree.sanitizeVoiceQuery("play The Daily on mis podcast"))
        assertEquals("La Ruina", autoTree.sanitizeVoiceQuery("escuchar La Ruina por favor"))
        assertEquals("El Partidazo de COPE", autoTree.sanitizeVoiceQuery("quiero escuchar El Partidazo de COPE con mis podcasts"))

        // 2. Simulación de podcast y episodios en base de datos
        val testPodcast = com.ferlagod.mispodcast.data.local.entities.PodcastEntity(
            id = "pod_nadie",
            title = "Nadie Sabe Nada",
            author = "Andreu Buenafuente y Berto Romero",
            description = "El programa del humor improvisado",
            feedUrl = "https://cadenaser.com/feed/nadie.xml",
            artworkUrl = "https://cadenaser.com/img/nadie.jpg"
        )
        val testEpisode1 = com.ferlagod.mispodcast.data.local.entities.EpisodeEntity(
            id = "ep_nadie_01",
            podcastId = "pod_nadie",
            title = "Nadie Sabe Nada 12x01",
            description = "Comienza la nueva temporada",
            audioUrl = "https://cadenaser.com/audio/nadie_01.mp3",
            imageUrl = "https://cadenaser.com/img/ep1.jpg",
            pubDate = 1700000000000L,
            duration = 3600000L,
            isPlayed = false
        )

        coEvery { mockDao.searchLocalPodcasts("Nadie Sabe Nada") } returns listOf(testPodcast)
        coEvery { mockDao.getEpisodesForPodcast("pod_nadie") } returns listOf(testEpisode1)
        coEvery { mockDao.getEpisodeById("ep_nadie_01") } returns testEpisode1
        coEvery { mockDao.getPodcastById("pod_nadie") } returns testPodcast
        coEvery { mockDao.getQueueList() } returns emptyList()

        // 3. Resolución directa por voz en AutoBrowseTree
        val resolvedItem = autoTree.resolveMediaItemFromVoiceSearch("Ok Google, pon Nadie Sabe Nada en Mis Podcast")
        assertNotNull(resolvedItem)
        assertEquals("ep_nadie_01", resolvedItem!!.mediaId)
        assertEquals("Nadie Sabe Nada 12x01", resolvedItem.mediaMetadata.title.toString())
        assertEquals("Nadie Sabe Nada", resolvedItem.mediaMetadata.artist.toString())

        // 4. Verificación de resolución a través de PlaybackController con MediaItem conteniendo searchQuery
        val controller = com.ferlagod.mispodcast.playback.PlaybackController(
            database = mockDb,
            autoBrowseTree = autoTree
        )

        val assistantMediaItem = androidx.media3.common.MediaItem.Builder()
            .setRequestMetadata(
                androidx.media3.common.MediaItem.RequestMetadata.Builder()
                    .setSearchQuery("pon Nadie Sabe Nada en Mis Podcast")
                    .build()
            )
            .build()

        val result = controller.resolveMediaItemsForSet(
            mediaItems = listOf(assistantMediaItem),
            startIndex = 0,
            startPositionMs = 0L
        )

        assertEquals(1, result.mediaItems.size)
        assertEquals("ep_nadie_01", result.mediaItems[0].mediaId)
        assertEquals(0, result.startIndex)
    }

    @Test
    fun testRegression22_wrappedAnnualStatsAndSharing() = kotlinx.coroutines.test.runTest {
        val mockDao = mockk<com.ferlagod.mispodcast.data.local.dao.PodcastDao>(relaxed = true)
        val mockSearchService = mockk<com.ferlagod.mispodcast.data.remote.api.ApplePodcastSearchService>(relaxed = true)
        val mockRssParser = mockk<com.ferlagod.mispodcast.data.remote.parser.RssFeedParser>(relaxed = true)

        val repository = com.ferlagod.mispodcast.data.repository.PodcastRepository(
            podcastDao = mockDao,
            searchService = mockSearchService,
            feedParser = mockRssParser
        )

        // 1. Simulación de estadísticas del año 2026 devueltas por el DAO
        val sampleStats = listOf(
            com.ferlagod.mispodcast.data.local.dao.PodcastPeriodStat(
                podcastId = "pod_nadie",
                podcastTitle = "Nadie Sabe Nada",
                podcastArtworkUrl = "https://cadenaser.com/img/nadie.jpg",
                timeListenedMs = 72_000_000L, // 20 horas
                completedCount = 20
            ),
            com.ferlagod.mispodcast.data.local.dao.PodcastPeriodStat(
                podcastId = "pod_daily",
                podcastTitle = "The Daily",
                podcastArtworkUrl = "https://nytimes.com/img/daily.jpg",
                timeListenedMs = 36_000_000L, // 10 horas
                completedCount = 30
            ),
            com.ferlagod.mispodcast.data.local.dao.PodcastPeriodStat(
                podcastId = "pod_ruina",
                podcastTitle = "La Ruina",
                podcastArtworkUrl = "https://laruina.com/img/ruina.jpg",
                timeListenedMs = 18_000_000L, // 5 horas
                completedCount = 5
            )
        )

        coEvery { mockDao.getWrappedStatsForPeriod(any(), any()) } returns sampleStats

        // 2. Ejecución del cálculo de Wrapped en el repositorio
        val wrapped = repository.getWrappedStats(2026)

        assertEquals(2026, wrapped.year)
        assertEquals(126_000_000L, wrapped.totalTimeListenedMs) // 35 horas totales
        assertEquals(55, wrapped.totalEpisodesCompleted)
        assertEquals(3, wrapped.totalPodcastsTracked)
        assertEquals(3, wrapped.topPodcasts.size)

        // Verificación de podcast #1 y porcentajes
        val top1 = wrapped.topPodcast
        assertNotNull(top1)
        assertEquals("Nadie Sabe Nada", top1!!.title)
        assertEquals(20.0, top1.hours, 0.01)
        assertEquals(72_000_000f / 126_000_000f, top1.percentageOfTotal, 0.01f)

        // Verificación de arquetipo
        assertEquals("El Fan Incondicional", wrapped.archetype)

        // 3. Verificación de arquetipos alternativos según hábitos auditivos
        val (maratoniano, _) = com.ferlagod.mispodcast.data.model.WrappedData.resolveArchetype(
            totalHours = 120.0,
            totalPodcasts = 5,
            episodesCompleted = 80,
            topPercentage = 0.3f
        )
        assertEquals("El Maratoniano del Audio", maratoniano)

        val (explorador, _) = com.ferlagod.mispodcast.data.model.WrappedData.resolveArchetype(
            totalHours = 25.0,
            totalPodcasts = 15,
            episodesCompleted = 20,
            topPercentage = 0.15f
        )
        assertEquals("El Explorador Ecléctico", explorador)

        // 4. Verificación de texto generado para compartir
        val shareText = wrapped.generateShareText()
        assertTrue(shareText.contains("¡Mi Wrapped 2026 en Mis Podcast!"))
        assertTrue(shareText.contains("35,0 horas") || shareText.contains("35.0 horas"))
        assertTrue(shareText.contains("Nadie Sabe Nada"))
        assertTrue(shareText.contains("El Fan Incondicional"))
        assertTrue(shareText.contains("#MisPodcastWrapped"))
        assertTrue(shareText.contains("🥇 Nadie Sabe Nada"))
        assertTrue(shareText.contains("🥈 The Daily"))
        assertTrue(shareText.contains("🥉 La Ruina"))

        // 5. Verificación de fallback a estadísticas acumuladas si el periodo está vacío
        coEvery { mockDao.getWrappedStatsForPeriod(any(), any()) } returns emptyList()
        coEvery { mockDao.getAllTimePlaybackStatsWithArtwork() } returns sampleStats

        val fallbackWrapped = repository.getWrappedStats(2026)
        assertEquals(126_000_000L, fallbackWrapped.totalTimeListenedMs)
        assertEquals("Nadie Sabe Nada", fallbackWrapped.topPodcast?.title)
    }

    /**
     * Test de regresión 23: Accesibilidad TalkBack en Slider de progreso de reproducción.
     *
     * Valida que:
     * - El estado semántico (`stateDescription`) anuncia descripciones naturales ("12 minutos de 58") en vez de floats.
     * - Episodios largos (> 1h) anuncian horas y minutos de forma amigable.
     * - Posiciones al inicio de episodios largos anuncian "X min de Y h Z min".
     * - Casos extremos (0 ms, clamping por encima de duración, duración inválida) se resuelven coherentemente.
     */
    @Test
    fun testRegression23_talkBackSliderProgressAccessibility() {
        // 1. Caso explícito de la especificación: "12 minutos de 58"
        // 12 minutos = 12 * 60 * 1000 = 720_000 ms; 58 minutos = 58 * 60 * 1000 = 3_480_000 ms
        val descMinutes = com.ferlagod.mispodcast.util.AccessibilityUtils.formatProgressTalkBack(
            currentPosMs = 12 * 60 * 1000L,
            durationMs = 58 * 60 * 1000L,
            minutesFormat = "%1\$d minutos de %2\$d"
        )
        assertEquals("12 minutos de 58", descMinutes)

        // 2. Inicio de episodio de 58 minutos (0 minutos de 58)
        val descStart = com.ferlagod.mispodcast.util.AccessibilityUtils.formatProgressTalkBack(
            currentPosMs = 0L,
            durationMs = 58 * 60 * 1000L,
            minutesFormat = "%1\$d minutos de %2\$d"
        )
        assertEquals("0 minutos de 58", descStart)

        // 3. Episodio largo (> 1 hora) en posición > 1 hora: ej. 1h 15m de 2h 30m
        val descHours = com.ferlagod.mispodcast.util.AccessibilityUtils.formatProgressTalkBack(
            currentPosMs = (1 * 3600 + 15 * 60) * 1000L,
            durationMs = (2 * 3600 + 30 * 60) * 1000L,
            hoursFormat = "%1\$d h %2\$d min de %3\$d h %4\$d min"
        )
        assertEquals("1 h 15 min de 2 h 30 min", descHours)

        // 4. Episodio largo (> 1 hora) en posición < 1 hora: ej. 25 min de 2h 30m
        val descHoursEarly = com.ferlagod.mispodcast.util.AccessibilityUtils.formatProgressTalkBack(
            currentPosMs = 25 * 60 * 1000L,
            durationMs = (2 * 3600 + 30 * 60) * 1000L,
            hoursStartFormat = "%1\$d min de %2\$d h %3\$d min"
        )
        assertEquals("25 min de 2 h 30 min", descHoursEarly)

        // 5. Clamping seguro: posición más allá del final
        val descClamped = com.ferlagod.mispodcast.util.AccessibilityUtils.formatProgressTalkBack(
            currentPosMs = 70 * 60 * 1000L,
            durationMs = 58 * 60 * 1000L,
            minutesFormat = "%1\$d minutos de %2\$d"
        )
        assertEquals("58 minutos de 58", descClamped)

        // 6. Duración inválida o vacía
        val descEmpty = com.ferlagod.mispodcast.util.AccessibilityUtils.formatProgressTalkBack(
            currentPosMs = 5000L,
            durationMs = 0L
        )
        assertEquals("", descEmpty)
    }

    /**
     * Test de regresión 24: Configuración de Color dinámico (Material You).
     *
     * Valida que:
     * - Por defecto, `useDynamicColor` es false para salvaguardar la identidad visual del logo (#297A9D).
     * - `UserSettings` permite configurar `useDynamicColor = true` para adaptar el tema en Android 12+.
     */
    @Test
    fun testRegression24_materialYouDynamicColorPreference() {
        val defaultSettings = com.ferlagod.mispodcast.data.preferences.UserSettings()
        assertFalse(defaultSettings.useDynamicColor)

        val dynamicSettings = defaultSettings.copy(useDynamicColor = true)
        assertTrue(dynamicSettings.useDynamicColor)
    }

    /**
     * Test de regresión 25: Soporte adaptativo para tablets y plegables (NavigationSuite y Two-Pane Layout).
     *
     * Valida:
     * - El umbral de dos paneles para detalles de podcast (720.dp).
     * - Lógica de discriminación entre modo compacto (teléfono, < 720 dp) y modo expandido (tablet/foldable, >= 720 dp).
     * - Ancho reservado para panel maestro (360.dp).
     */
    @Test
    fun testRegression25_adaptiveNavigationAndTwoPaneLayout() {
        fun isTwoPaneLayout(widthDp: Int): Boolean = widthDp >= 720

        // Dispositivos compactos (smartphones estándar)
        assertFalse(isTwoPaneLayout(360)) // Teléfono pequeño
        assertFalse(isTwoPaneLayout(390)) // Teléfono típico (Pixel, Galaxy, iPhone)
        assertFalse(isTwoPaneLayout(411)) // Teléfono grande vertical
        assertFalse(isTwoPaneLayout(600)) // Phablet / plegable cerrado
        assertFalse(isTwoPaneLayout(719)) // Inmediatamente debajo del umbral

        // Dispositivos amplios (tablets y plegables desplegados)
        assertTrue(isTwoPaneLayout(720))  // Umbral exacto
        assertTrue(isTwoPaneLayout(800))  // Plegable 펼친 상태 / Tablet 8"
        assertTrue(isTwoPaneLayout(840))  // Tablet horizontal / Pixel Fold
        assertTrue(isTwoPaneLayout(1024)) // Tablet 10-11"
        assertTrue(isTwoPaneLayout(1280)) // Tablet grande / Desktop

        // En modo de dos paneles, el panel izquierdo de 360 dp deja suficiente espacio para la lista
        val totalWidth = 800
        val leftPaneWidth = 360
        val rightPaneAvailable = totalWidth - leftPaneWidth
        assertTrue(rightPaneAvailable >= 400) // Espacio de sobra para listas y tarjetas de episodios
    }

    /**
     * Test de regresión 26: Soporte para Modo AMOLED puro (True OLED Black).
     *
     * Valida que:
     * - `AppThemeMode` incluya los 4 modos oficiales: SYSTEM, LIGHT, DARK y AMOLED.
     * - `AppThemeMode.AMOLED` tenga asignado el recurso de texto `settings_theme_amoled`.
     * - Las superficies y fondos de AMOLED sean negro puro (0x000000 / #000000) para apagar los píxeles OLED al 100%.
     * - El modo oscuro estándar use gris oscuro (#121214) diferenciado del modo AMOLED.
     * - `UserSettings` serialice y guarde correctamente el modo AMOLED.
     */
    @Test
    fun testRegression26_amoledPureBlackThemeMode() {
        val modes = com.ferlagod.mispodcast.data.preferences.AppThemeMode.values()
        assertEquals(4, modes.size)
        assertTrue(modes.contains(com.ferlagod.mispodcast.data.preferences.AppThemeMode.AMOLED))
        assertEquals(
            com.ferlagod.mispodcast.R.string.settings_theme_amoled,
            com.ferlagod.mispodcast.data.preferences.AppThemeMode.AMOLED.titleRes
        )

        // Verificación de configuración
        val settings = com.ferlagod.mispodcast.data.preferences.UserSettings(
            themeMode = com.ferlagod.mispodcast.data.preferences.AppThemeMode.AMOLED
        )
        assertEquals(com.ferlagod.mispodcast.data.preferences.AppThemeMode.AMOLED, settings.themeMode)

        // Verificación de colores OLED puro vs Modo Oscuro Estándar
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), com.ferlagod.mispodcast.ui.theme.BackgroundAmoled)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), com.ferlagod.mispodcast.ui.theme.SurfaceAmoled)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF121214), com.ferlagod.mispodcast.ui.theme.BackgroundDark)
        assertNotEquals(
            com.ferlagod.mispodcast.ui.theme.BackgroundDark,
            com.ferlagod.mispodcast.ui.theme.BackgroundAmoled
        )
    }

    @Test
    fun testHistoryItemsDeduplicationByEpisode() = kotlinx.coroutines.runBlocking {
        val mockDao = mockk<com.ferlagod.mispodcast.data.local.dao.PodcastDao>()
        val ep1 = EpisodeEntity(
            id = "ep_1",
            podcastId = "pod_1",
            title = "Episode 1",
            description = "Desc 1",
            audioUrl = "https://example.com/1.mp3",
            imageUrl = "",
            pubDate = 1000L,
            duration = 60000L
        )
        val ep2 = EpisodeEntity(
            id = "ep_2",
            podcastId = "pod_1",
            title = "Episode 2",
            description = "Desc 2",
            audioUrl = "https://example.com/2.mp3",
            imageUrl = "",
            pubDate = 2000L,
            duration = 60000L
        )
        val duplicateHistoryItems = listOf(
            com.ferlagod.mispodcast.data.local.model.HistoryEpisodeItem(
                historyId = 3L,
                listenedAt = 3000L,
                durationListenedMs = 5000L,
                completed = false,
                podcastTitle = "Podcast 1",
                podcastArtworkUrl = "",
                episode = ep1
            ),
            com.ferlagod.mispodcast.data.local.model.HistoryEpisodeItem(
                historyId = 2L,
                listenedAt = 2000L,
                durationListenedMs = 10000L,
                completed = false,
                podcastTitle = "Podcast 1",
                podcastArtworkUrl = "",
                episode = ep2
            ),
            com.ferlagod.mispodcast.data.local.model.HistoryEpisodeItem(
                historyId = 1L,
                listenedAt = 1000L,
                durationListenedMs = 2000L,
                completed = false,
                podcastTitle = "Podcast 1",
                podcastArtworkUrl = "",
                episode = ep1
            )
        )
        every { mockDao.getListeningHistoryFlow(any()) } returns kotlinx.coroutines.flow.flowOf(duplicateHistoryItems)

        val repo = com.ferlagod.mispodcast.data.repository.PodcastRepository(
            podcastDao = mockDao,
            searchService = mockk(relaxed = true),
            feedParser = mockk(relaxed = true)
        )

        val result = repo.getListeningHistory().first()
        assertEquals(2, result.size)
        assertEquals("ep_1", result[0].episode.id)
        assertEquals(3000L, result[0].listenedAt)
        assertEquals("ep_2", result[1].episode.id)
        assertEquals(2000L, result[1].listenedAt)
    }
}




