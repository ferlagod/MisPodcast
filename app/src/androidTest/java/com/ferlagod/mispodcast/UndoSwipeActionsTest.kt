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
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.dao.PodcastDao
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deshacer las acciones rápidas de deslizar debe devolver el episodio exactamente
 * al estado previo: reproducido/posición, su posición en la cola e historial.
 */
@RunWith(AndroidJUnit4::class)
class UndoSwipeActionsTest {

    private lateinit var database: PodcastDatabase
    private lateinit var dao: PodcastDao

    @Before
    fun setUp() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PodcastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.podcastDao()

        val now = System.currentTimeMillis()
        dao.insertPodcast(
            PodcastEntity(
                id = "pod-undo",
                title = "Podcast Deshacer",
                author = "Mis Podcast",
                description = "",
                feedUrl = "https://mispodcast.test/undo.xml",
                artworkUrl = "",
                isSubscribed = true,
                subscribedAt = now - 100000L,
                lastUpdated = now,
                category = "Test"
            )
        )
        dao.insertEpisodes((1..4).map { i ->
            EpisodeEntity(
                id = "ep-$i",
                podcastId = "pod-undo",
                title = "Episodio $i",
                description = "",
                audioUrl = "https://mispodcast.test/$i.mp3",
                imageUrl = "",
                pubDate = now - i * 1000L,
                duration = 60_000L,
                fileSizeBytes = 0L,
                isPlayed = false
            )
        })
        (1..4).forEach { i ->
            dao.insertQueueItem(QueueItemEntity(episodeId = "ep-$i", positionIndex = i - 1, isCurrentlyPlaying = i == 1))
        }
        dao.updatePlaybackPosition("ep-3", 12_345L)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun queueOrder() = dao.getQueueItemsSync().map { it.episodeId }

    @Test
    fun undoMarkPlayedAndRemoveRestoresQueuePositionProgressAndHistory() = runBlocking {
        val snapshot = dao.captureEpisodeUndoSnapshot("ep-3")!!

        dao.markAsPlayedAndRemoveFromQueue("ep-3")
        assertEquals(listOf("ep-1", "ep-2", "ep-4"), queueOrder())
        assertTrue(dao.getEpisodeById("ep-3")!!.isPlayed)
        assertNotNull(dao.getLatestListeningHistoryEntry("ep-3"))

        dao.restoreEpisodeUndoSnapshot(snapshot)

        assertEquals(listOf("ep-1", "ep-2", "ep-3", "ep-4"), queueOrder())
        assertEquals(listOf(0, 1, 2, 3), dao.getQueueItemsSync().map { it.positionIndex })
        val episode = dao.getEpisodeById("ep-3")!!
        assertFalse(episode.isPlayed)
        assertEquals(12_345L, episode.currentPositionMs)
        assertNull(dao.getLatestListeningHistoryEntry("ep-3"))
        assertEquals("ep-1", dao.getQueueItemsSync().single { it.isCurrentlyPlaying }.episodeId)
    }

    @Test
    fun undoRemoveFromQueueKeepsOtherChanges() = runBlocking {
        val snapshot = dao.captureEpisodeUndoSnapshot("ep-2")!!
        dao.removeFromQueue("ep-2")
        dao.reindexQueue()
        // Otro cambio independiente mientras se muestra el Snackbar
        dao.removeFromQueue("ep-4")
        dao.reindexQueue()

        dao.restoreEpisodeUndoSnapshot(snapshot)

        assertEquals(listOf("ep-1", "ep-2", "ep-3"), queueOrder())
    }

    @Test
    fun undoAddToQueueRemovesTheEpisodeAgain() = runBlocking {
        dao.removeFromQueue("ep-4")
        dao.reindexQueue()
        val snapshot = dao.captureEpisodeUndoSnapshot("ep-4")!!
        dao.addEpisodeToQueueEnd("ep-4")
        assertEquals(listOf("ep-1", "ep-2", "ep-3", "ep-4"), queueOrder())

        dao.restoreEpisodeUndoSnapshot(snapshot)

        assertEquals(listOf("ep-1", "ep-2", "ep-3"), queueOrder())
    }

    @Test
    fun undoTogglePlayedRestoresPreviousProgress() = runBlocking {
        val snapshot = dao.captureEpisodeUndoSnapshot("ep-3")!!
        dao.markEpisodeAsPlayed("ep-3", true)

        dao.restoreEpisodeUndoSnapshot(snapshot)

        val episode = dao.getEpisodeById("ep-3")!!
        assertFalse(episode.isPlayed)
        assertEquals(12_345L, episode.currentPositionMs)
        assertEquals(listOf("ep-1", "ep-2", "ep-3", "ep-4"), queueOrder())
    }
}
