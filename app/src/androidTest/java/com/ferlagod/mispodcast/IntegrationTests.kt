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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import com.ferlagod.mispodcast.sync.NextcloudEpisodeProgress
import com.ferlagod.mispodcast.sync.NextcloudSubscription
import com.ferlagod.mispodcast.sync.NextcloudSyncData
import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import com.ferlagod.mispodcast.worker.PodcastCheckWorker
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas de integración sobre emulador o dispositivo Android para comprobar la persistencia en Room,
 * preferencias de usuario y operaciones concurrentes.
 */
@RunWith(AndroidJUnit4::class)
class IntegrationTests {

    private lateinit var context: Context
    private lateinit var database: PodcastDatabase
    private var mockWebServer: MockWebServer? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PodcastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        mockWebServer?.shutdown()
    }

    /**
     * Prueba de integración Room + DAO real en memoria:
     * - Persistencia y reordenación de 5 episodios en la cola.
     * - Actualización de estado de reproducción (isPlayed = true).
     * - Limpieza de Novedades marcando todos los episodios pendientes como reproducidos.
     */
    @Test
    fun testRoomDatabaseQueuePersistenceReorderAndClearNewReleases() = runBlocking {
        val dao = database.podcastDao()
        val now = System.currentTimeMillis()

        val podcast = PodcastEntity(
            id = "pod-integration-1",
            title = "Podcast de Integración",
            author = "Equipo Mis Podcast",
            description = "Descripción del podcast de prueba",
            feedUrl = "https://mispodcast.test/feed.xml",
            artworkUrl = "https://mispodcast.test/cover.jpg",
            isSubscribed = true,
            subscribedAt = now - 100000L,
            lastUpdated = now,
            category = "Tecnología"
        )
        dao.insertPodcast(podcast)

        val episodes = (1..5).map { index ->
            EpisodeEntity(
                id = "ep-test-$index",
                podcastId = podcast.id,
                title = "Episodio $index",
                description = "Descripción del episodio $index",
                audioUrl = "https://mispodcast.test/audio$index.mp3",
                imageUrl = "https://mispodcast.test/img$index.jpg",
                pubDate = now - (index * 10000L),
                duration = index * 60000L,
                fileSizeBytes = index * 1024L * 1024L,
                isPlayed = false
            )
        }
        dao.insertEpisodes(episodes)

        // 1. Insertar los 5 episodios en la cola con índices iniciales consecutivos
        episodes.forEachIndexed { index, ep ->
            dao.insertQueueItem(
                QueueItemEntity(
                    episodeId = ep.id,
                    positionIndex = index,
                    isCurrentlyPlaying = (index == 0),
                    addedAt = now + index
                )
            )
        }

        var queueItems = dao.getQueueFlow().first()
        assertEquals(5, queueItems.size)
        assertEquals("ep-test-1", queueItems[0].episode.id)
        assertEquals(0, queueItems[0].positionIndex)

        // 2. Reordenar la cola por duración descendente
        val sortedByDurationDesc = queueItems.sortedByDescending { it.episode.duration }
        sortedByDurationDesc.forEachIndexed { newIndex, item ->
            dao.updateQueuePosition(item.episode.id, newIndex)
        }

        queueItems = dao.getQueueFlow().first()
        assertEquals("ep-test-5", queueItems[0].episode.id)
        assertEquals(0, queueItems[0].positionIndex)
        assertEquals(300000L, queueItems[0].episode.duration)
        assertEquals("ep-test-1", queueItems[4].episode.id)
        assertEquals(4, queueItems[4].positionIndex)

        // 3. Marcar uno de los episodios como reproducido
        dao.markEpisodeAsPlayed("ep-test-5", isPlayed = true)
        val updatedEp5 = dao.getEpisodeById("ep-test-5")
        assertNotNull(updatedEp5)
        assertTrue(updatedEp5!!.isPlayed)
        assertEquals(300000L, updatedEp5.currentPositionMs)

        // 4. Quitar dos episodios de la cola y limpiar Novedades
        dao.removeFromQueue("ep-test-3")
        dao.removeFromQueue("ep-test-4")
        val queueBeforeClear = dao.getQueueFlow().first().map { it.episode.id }
        dao.clearAllNewReleasesDirect()

        // Los que NO estaban en la cola se marcan como reproducidos (isPlayed = 1)
        assertTrue(dao.getEpisodeById("ep-test-3")!!.isPlayed)
        assertTrue(dao.getEpisodeById("ep-test-4")!!.isPlayed)

        // 5. La cola queda exactamente igual y sus episodios NO se marcan como reproducidos
        val queueAfterClear = dao.getQueueFlow().first()
        assertEquals(queueBeforeClear, queueAfterClear.map { it.episode.id })
        assertFalse(dao.getEpisodeById("ep-test-1")!!.isPlayed)
        assertFalse(dao.getEpisodeById("ep-test-2")!!.isPlayed)

        // 6. La pestaña de novedades queda completamente vacía (descartados de novedades)
        val newReleasesAfterClear = dao.getNewReleasesList(100)
        assertTrue("La pestaña de novedades debe quedar completamente vacía", newReleasesAfterClear.isEmpty())
    }

    /**
     * Prueba de integración Nextcloud WebDAV + MockWebServer:
     * - Levanta un MockWebServer simulando las respuestas WebDAV (PROPFIND 207, GET 200, PUT 200).
     * - Verifica que NextcloudSyncManager sube el JSON de estado (PUT) y descarga/fusiona suscripciones y progreso (GET).
     */
    @Test
    fun testNextcloudWebDavSyncWithMockWebServer() = runBlocking {
        val server = MockWebServer()
        mockWebServer = server

        val remoteSyncData = NextcloudSyncData(
            version = 2,
            app = "MisPodcast",
            subscriptions = listOf(
                NextcloudSubscription(
                    feedUrl = "https://servidor-remoto.org/podcast/feed.xml",
                    title = "Podcast Sincronizado",
                    subscribedAt = 1700000000000L
                )
            ),
            episodesProgress = listOf(
                NextcloudEpisodeProgress(
                    episodeId = "ep-remote-123",
                    currentPositionMs = 85000L,
                    isPlayed = false
                )
            )
        )
        val remoteJson = Json.encodeToString(remoteSyncData)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                val method = request.method ?: ""

                return when {
                    method == "PROPFIND" -> MockResponse().setResponseCode(207).setBody("<multistatus/>")
                    method == "GET" && path.contains("sync_state.json") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody(remoteJson)
                    method == "PUT" && path.contains("sync_state.json") -> MockResponse().setResponseCode(200)
                    else -> MockResponse().setResponseCode(200)
                }
            }
        }
        server.start()

        val baseUrl = server.url("/").toString().removeSuffix("/")

        val prefManager = UserPreferencesManager.getInstance(context)
        prefManager.setNextcloudConfig(baseUrl, "usuarioPrueba", "passwordAppPrueba")

        val repository = PodcastRepository(database.podcastDao())
        val syncManager = NextcloudSyncManager(
            database = database,
            repository = repository,
            preferencesManager = prefManager
        )

        val syncResult = syncManager.syncNow()
        assertTrue("La sincronización con MockWebServer debe ser exitosa", syncResult.isSuccess)

        // Verificar peticiones registradas por MockWebServer
        var foundPut = false
        var foundGet = false

        while (server.requestCount > 0) {
            val req = try {
                server.takeRequest(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                null
            } ?: break

            if (req.method == "GET" && req.path?.contains("sync_state.json") == true) {
                foundGet = true
            }
            if (req.method == "PUT" && req.path?.contains("sync_state.json") == true) {
                foundPut = true
                val requestBody = req.body.readUtf8()
                assertTrue("El cuerpo del PUT debe ser un JSON válido", requestBody.contains("\"app\":\"MisPodcast\""))
            }
        }

        assertTrue("Debe haberse ejecutado la petición GET para descargar el estado remoto", foundGet)
        assertTrue("Debe haberse ejecutado la petición PUT para subir el estado local", foundPut)
    }

    /**
     * Prueba de integración WorkManager:
     * - Usa TestListenableWorkerBuilder para comprobar que PodcastCheckWorker
     *   se instancia y ejecuta correctamente devolviendo Result.success().
     */
    @Test
    fun testPodcastCheckWorkerExecutionSuccess() = runBlocking {
        val worker = TestListenableWorkerBuilder<PodcastCheckWorker>(context).build()
        val result = worker.doWork()

        assertEquals(
            "PodcastCheckWorker debe retornar Result.success() tras comprobar los episodios",
            ListenableWorker.Result.success(),
            result
        )
    }
}
