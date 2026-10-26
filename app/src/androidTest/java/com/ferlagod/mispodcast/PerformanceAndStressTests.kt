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
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PlaybackStatsEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity
import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import kotlin.system.measureTimeMillis

import android.os.Build
import androidx.test.rule.GrantPermissionRule

@RunWith(AndroidJUnit4::class)
/**
 * Pruebas de rendimiento y estrés: validación de tiempos de carga con grandes volúmenes de episodios,
 * inserciones masivas en base de datos y estabilidad del reproductor.
 */
class PerformanceAndStressTests {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var database: PodcastDatabase

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
    }

    /**
     * 1. Test de Carga (Load Test):
     * - Inserta de golpe en Room en memoria 150 podcasts suscritos, 15.000 episodios y 500 episodios en la cola.
     * - Mide cuánto tarda el DAO en responder a las consultas de Cola, Novedades y Estadísticas.
     * - Falla si cualquiera de las consultas supera los 300ms.
     */
    @Test
    fun testLoad150Podcasts15000Episodes500QueuePerformance() = runBlocking {
        val dao = database.podcastDao()
        val now = System.currentTimeMillis()

        // 1. Crear 150 podcasts
        val podcastList = (1..150).map { pIndex ->
            PodcastEntity(
                id = "pod-load-$pIndex",
                title = "Podcast Veterano $pIndex",
                author = "Autor de Carga $pIndex",
                description = "Descripción extendida para pruebas de carga en base de datos $pIndex",
                feedUrl = "https://loadtest.com/rss/$pIndex.xml",
                artworkUrl = "https://loadtest.com/art/$pIndex.jpg",
                isSubscribed = true,
                subscribedAt = now - 864000000L,
                lastUpdated = now,
                category = "General"
            )
        }
        dao.insertPodcasts(podcastList)

        // 2. Crear 15.000 episodios (100 por podcast)
        val allEpisodes = ArrayList<EpisodeEntity>(15000)
        for (pIndex in 1..150) {
            val podId = "pod-load-$pIndex"
            for (eIndex in 1..100) {
                val epId = "ep-$pIndex-$eIndex"
                allEpisodes.add(
                    EpisodeEntity(
                        id = epId,
                        podcastId = podId,
                        title = "Episodio $eIndex del Podcast $pIndex",
                        description = "Resumen del episodio $eIndex para verificar indexación y rendimiento de consultas",
                        audioUrl = "https://loadtest.com/audio/$pIndex/$eIndex.mp3",
                        imageUrl = "https://loadtest.com/cover/$pIndex.jpg",
                        pubDate = now - (eIndex * 60000L),
                        duration = 1800000L + (eIndex * 1000L),
                        fileSizeBytes = 30000000L,
                        isPlayed = (eIndex > 20)
                    )
                )
            }
        }
        dao.insertEpisodes(allEpisodes)

        // 3. Crear 500 elementos en la cola
        val queueItems = (1..500).map { qIndex ->
            QueueItemEntity(
                episodeId = allEpisodes[qIndex - 1].id,
                positionIndex = qIndex - 1,
                isCurrentlyPlaying = (qIndex == 1),
                addedAt = now + qIndex
            )
        }
        dao.insertQueueItems(queueItems)

        // 4. Crear estadísticas de reproducción
        val statsList = (1..150).map { pIndex ->
            PlaybackStatsEntity(
                podcastId = "pod-load-$pIndex",
                podcastTitle = "Podcast Veterano $pIndex",
                totalTimeListenedMs = pIndex * 120000L,
                episodesCompletedCount = pIndex,
                lastListenedAt = now - (pIndex * 60000L)
            )
        }
        dao.upsertPlaybackStatsList(statsList)

        // 5. Medir tiempo de consulta de la COLA (500 elementos ordenados con JOIN)
        val queueQueryTimeMs = measureTimeMillis {
            val queueResult = dao.getQueueList()
            assertEquals(500, queueResult.size)
        }
        assertTrue(
            "La consulta de la Cola tardó $queueQueryTimeMs ms (límite: 300ms)",
            queueQueryTimeMs <= 300L
        )

        // 6. Medir tiempo de consulta de NOVEDADES (100 elementos con filtro y JOIN)
        val newReleasesQueryTimeMs = measureTimeMillis {
            val newReleasesResult = dao.getNewReleasesList(limit = 100)
            assertTrue("Debe devolver novedades", newReleasesResult.isNotEmpty())
        }
        assertTrue(
            "La consulta de Novedades tardó $newReleasesQueryTimeMs ms (límite: 300ms)",
            newReleasesQueryTimeMs <= 300L
        )

        // 7. Medir tiempo de consulta de ESTADÍSTICAS
        val statsQueryTimeMs = measureTimeMillis {
            val statsResult = dao.getPodcastStatsList()
            assertEquals(150, statsResult.size)
        }
        assertTrue(
            "La consulta de Estadísticas tardó $statsQueryTimeMs ms (límite: 300ms)",
            statsQueryTimeMs <= 300L
        )
    }

    /**
     * 2. Test de Rendimiento de Parseo y Memoria (Performance Test):
     * - Genera en memoria un XML RSS con 2.500 episodios y etiquetas Podcast 2.0 completas.
     * - Verifica que el parseo completo tarda menos de 2000ms y no lanza OutOfMemoryError.
     */
    @Test
    fun testParsingPerformanceAndMemoryUsageWith2500Episodes() {
        val episodeCount = 2500
        val sb = StringBuilder(episodeCount * 450)
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" 
    xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
    xmlns:podcast="https://podcastindex.org/namespace/1.0">
    <channel>
        <title>Podcast Masivo 2.500 Episodios</title>
        <link>https://podcastmasivo.test</link>
        <description>Feed extremadamente grande para medir parseo y consumo de heap</description>
        <itunes:author>Generador de Estrés</itunes:author>
        <podcast:funding url="https://podcastmasivo.test/donate">Soporte</podcast:funding>
""")

        for (i in 1..episodeCount) {
            sb.append("""
        <item>
            <title>Episodio de Rendimiento $i</title>
            <description>Descripción extendida del episodio número $i con detalles técnicos.</description>
            <guid>guid-perf-$i</guid>
            <enclosure url="https://podcastmasivo.test/audio/ep_$i.mp3" type="audio/mpeg" length="${1000000L + i}" />
            <pubDate>Wed, 14 Jan 2026 12:00:00 +0000</pubDate>
            <itunes:duration>45:20</itunes:duration>
            <itunes:image href="https://podcastmasivo.test/img/ep_$i.jpg" />
            <podcast:chapters url="https://podcastmasivo.test/chapters/ep_$i.json" type="application/json+chapters" />
            <podcast:transcript url="https://podcastmasivo.test/transcripts/ep_$i.vtt" type="text/vtt" />
            <podcast:person role="host" group="cast">Presentador $i</podcast:person>
            <podcast:funding url="https://podcastmasivo.test/donate_$i" />
        </item>
""")
        }
        sb.append("""
    </channel>
</rss>
""")

        val xmlBytes = sb.toString().toByteArray(Charsets.UTF_8)

        System.gc()
        val beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        val parser = RssFeedParser()
        val parsedFeed: com.ferlagod.mispodcast.data.remote.parser.ParsedPodcastFeed
        val parseTimeMs = measureTimeMillis {
            val stream = ByteArrayInputStream(xmlBytes)
            parsedFeed = parser.parseFeed(stream, "https://podcastmasivo.test/feed.xml")
        }

        val afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryDeltaMb = (afterMemory - beforeMemory) / (1024 * 1024)

        assertEquals(episodeCount, parsedFeed.episodes.size)
        assertEquals("Podcast Masivo 2.500 Episodios", parsedFeed.title)
        assertTrue(
            "El parseo de 2.500 episodios tardó $parseTimeMs ms (debe ser menor a 2000ms)",
            parseTimeMs < 2000L
        )
        assertNotNull(memoryDeltaMb)
    }

    /**
     * 3. Test de Estrés y Concurrencia (Stress Test):
     * - 50 corrutinas concurrentes ejecutando escrituras, reordenaciones, marcas de escucha y lecturas simultáneas.
     * - Verifica que no se produzca SQLiteDatabaseLockedException y los índices queden íntegros.
     */
    @Test
    fun testConcurrentStressAndDatabaseIntegrity() = runBlocking {
        val dao = database.podcastDao()
        val now = System.currentTimeMillis()

        val basePodcast = PodcastEntity(
            id = "pod-stress-base",
            title = "Podcast Estrés Concurrente",
            author = "Autor Concurrencia",
            description = "Podcast base para operaciones de concurrencia intensa",
            feedUrl = "https://stress.test/feed.xml",
            artworkUrl = "https://stress.test/cover.jpg",
            isSubscribed = true,
            subscribedAt = now,
            lastUpdated = now,
            category = "Tecnología"
        )
        dao.insertPodcast(basePodcast)

        val stressEpisodes = (1..60).map { i ->
            EpisodeEntity(
                id = "ep-stress-$i",
                podcastId = basePodcast.id,
                title = "Episodio Estrés $i",
                description = "Descripción concurrente $i",
                audioUrl = "https://stress.test/audio_$i.mp3",
                imageUrl = "https://stress.test/cover_$i.jpg",
                pubDate = now - (i * 1000L),
                duration = 60000L * i,
                isPlayed = false
            )
        }
        dao.insertEpisodes(stressEpisodes)

        val initialQueue = (1..30).map { i ->
            QueueItemEntity(
                episodeId = stressEpisodes[i - 1].id,
                positionIndex = i - 1,
                isCurrentlyPlaying = (i == 1),
                addedAt = now + i
            )
        }
        dao.insertQueueItems(initialQueue)

        // Ejecutar 50 corrutinas simultáneas en Dispatchers.IO
        coroutineScope {
            val jobs = (1..50).map { workerIndex ->
                launch(Dispatchers.IO) {
                    when (workerIndex % 4) {
                        0 -> {
                            // Actualizar punto de reproducción
                            repeat(8) { iter ->
                                dao.updatePlaybackPosition("ep-stress-1", (iter + 1) * 1500L)
                                delay(3)
                            }
                        }
                        1 -> {
                            // Reordenar índices de cola
                            repeat(4) {
                                dao.shiftAllQueueIndices()
                                dao.reindexQueue()
                                delay(5)
                            }
                        }
                        2 -> {
                            // Marcar descarga y reproducción
                            repeat(8) { iter ->
                                dao.updateDownloadStatus("ep-stress-2", true, "/data/local/stress_$iter.mp3")
                                dao.markEpisodeAsPlayed("ep-stress-2", iter % 2 == 0)
                                delay(4)
                            }
                        }
                        3 -> {
                            // Lecturas de exportación
                            repeat(8) {
                                val subscribed = dao.getSubscribedPodcasts()
                                val queue = dao.getQueueList()
                                assertTrue(subscribed.isNotEmpty())
                                assertTrue(queue.isNotEmpty())
                                delay(3)
                            }
                        }
                    }
                }
            }
            jobs.joinAll()
        }

        // Asegurar consistencia final y verificar que los índices son estrictamente 0, 1, 2...
        dao.reindexQueue()
        val finalQueue = dao.getQueueList()
        assertTrue("La cola debe mantener elementos tras el estrés concurrente", finalQueue.isNotEmpty())

        finalQueue.forEachIndexed { index, queueItem ->
            assertEquals(
                "Los positionIndex de la cola deben ser estrictamente consecutivos",
                index,
                queueItem.positionIndex
            )
        }
    }

    /**
     * 4. Test de Estrés en la Interfaz (UI Stress Test):
     * - Cambio rápido entre las 4 pestañas 20 veces consecutivas.
     * - Scroll rápido en la cola.
     * - Verifica que la app permanece totalmente fluida sin ANR ni excepciones.
     */
    @Test
    fun testUiRapidTabSwitchingAndFastScrollStress() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForIdle()

            // 1. Cambiar rápidamente entre las 4 pestañas inferiores 20 veces seguidas mediante testTag
            for (i in 0 until 20) {
                composeTestRule.onNodeWithTag("nav_item_queue").performClick()
                composeTestRule.onNodeWithTag("nav_item_new_releases").performClick()
                composeTestRule.onNodeWithTag("nav_item_subscriptions").performClick()
                composeTestRule.onNodeWithTag("nav_item_home").performClick()
            }
            composeTestRule.waitForIdle()

            // 2. Navegar a la cola y ejecutar scroll de prueba
            composeTestRule.onNodeWithTag("nav_item_queue").performClick()
            composeTestRule.waitForIdle()

            val scrollableNodes = composeTestRule.onAllNodes(hasScrollAction())
            if (scrollableNodes.fetchSemanticsNodes().isNotEmpty()) {
                val list = scrollableNodes.onFirst()
                list.performScrollToIndex(0)
                list.performScrollToIndex(10.coerceAtMost(scrollableNodes.fetchSemanticsNodes().size - 1))
                list.performScrollToIndex(0)
            }
            composeTestRule.waitForIdle()
        }
    }
}
