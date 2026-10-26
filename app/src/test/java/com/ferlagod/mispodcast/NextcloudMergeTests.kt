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

import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.dao.PodcastDao
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import com.ferlagod.mispodcast.sync.NextcloudEpisodeProgress
import com.ferlagod.mispodcast.sync.NextcloudSubscription
import com.ferlagod.mispodcast.sync.NextcloudSyncData
import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Batería de pruebas unitarias exhaustivas para el algoritmo de fusión (Merge) de Nextcloud (Punto 1.2).
 *
 * Valida la resolución de conflictos sin pérdida de datos:
 * - Prevención de resurrección de podcasts cancelados localmente mediante marcas de tiempo de baja (tombstones).
 * - Propagación de re-suscripciones remotas si son más recientes que la baja local.
 * - Sincronización de desuscripciones remotas a local.
 * - Soporte para rebobinados y desmarcado de episodios reproducidos mediante `positionUpdatedAt`.
 * - Rechazo de servidores HTTP inseguros.
 */
class NextcloudMergeTests {

    private lateinit var mockDatabase: PodcastDatabase
    private lateinit var mockDao: PodcastDao
    private lateinit var mockRepository: PodcastRepository
    private lateinit var mockPreferencesManager: UserPreferencesManager
    private lateinit var syncManager: NextcloudSyncManager

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0

        mockDatabase = mockk()
        mockDao = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)

        every { mockDatabase.podcastDao() } returns mockDao

        syncManager = NextcloudSyncManager(
            database = mockDatabase,
            repository = mockRepository,
            preferencesManager = mockPreferencesManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test 1: No resucitar podcasts desuscritos localmente.
     * Si el usuario se desuscribió localmente (`unsubscribedAt` = 10.000 ms),
     * y el estado remoto trae una suscripción vieja (`subscribedAt` = 5.000 ms),
     * el podcast NO debe volver a marcarse como suscrito.
     */
    @Test
    fun testDoNotResurrectLocallyUnsubscribedPodcast() = runBlocking {
        val feedUrl = "https://ejemplo.com/podcast.xml"
        val localPodcast = PodcastEntity(
            id = "pod-1",
            title = "Podcast Cancelado",
            author = "Autor",
            description = "Desc",
            feedUrl = feedUrl,
            artworkUrl = "",
            isSubscribed = false,
            subscribedAt = 5000L,
            unsubscribedAt = 10000L // Cancelado posteriormente a las 10s
        )

        coEvery { mockDao.getPodcastByIdOrFeedUrl(feedUrl, feedUrl) } returns localPodcast

        val remoteData = NextcloudSyncData(
            subscriptions = listOf(
                NextcloudSubscription(
                    feedUrl = feedUrl,
                    title = "Podcast Cancelado",
                    isSubscribed = true,
                    subscribedAt = 5000L // Remoto viejo anterior a la baja local
                )
            )
        )

        syncManager.mergeRemoteStateIntoLocal(remoteData)

        // NO se debe actualizar a suscrito ni volver a encolar suscripción
        coVerify(exactly = 0) {
            mockDao.updateSubscriptionWithTimestamps(
                podcastId = "pod-1",
                isSubscribed = true,
                subscribedAt = any(),
                unsubscribedAt = any()
            )
        }
        coVerify(exactly = 0) { mockRepository.subscribeToFeed(any()) }
    }

    /**
     * Test 2: Re-suscripción remota válida.
     * Si el usuario se desuscribió localmente a los 10.000 ms pero luego
     * se volvió a suscribir en otro dispositivo a los 15.000 ms,
     * el merge debe reactivar la suscripción local.
     */
    @Test
    fun testValidRemoteResubscriptionAfterLocalUnsubscribe() = runBlocking {
        val feedUrl = "https://ejemplo.com/podcast.xml"
        val localPodcast = PodcastEntity(
            id = "pod-1",
            title = "Podcast Re-suscrito",
            author = "Autor",
            description = "Desc",
            feedUrl = feedUrl,
            artworkUrl = "",
            isSubscribed = false,
            subscribedAt = 5000L,
            unsubscribedAt = 10000L
        )

        coEvery { mockDao.getPodcastByIdOrFeedUrl(feedUrl, feedUrl) } returns localPodcast

        val remoteData = NextcloudSyncData(
            subscriptions = listOf(
                NextcloudSubscription(
                    feedUrl = feedUrl,
                    title = "Podcast Re-suscrito",
                    isSubscribed = true,
                    subscribedAt = 15000L // Remoto posterior a la lápida local
                )
            )
        )

        syncManager.mergeRemoteStateIntoLocal(remoteData)

        coVerify(exactly = 1) {
            mockDao.updateSubscriptionWithTimestamps(
                podcastId = "pod-1",
                isSubscribed = true,
                subscribedAt = 15000L,
                unsubscribedAt = 0L
            )
        }
    }

    /**
     * Test 3: Sincronización de lápida remota (Baja en otro dispositivo).
     * Si localmente estaba suscrito (a los 5.000 ms) y el servidor envía
     * una lápida con `isSubscribed = false` a los 12.000 ms,
     * debe darse de baja localmente.
     */
    @Test
    fun testRemoteTombstonePropagatesUnsubscribeToLocal() = runBlocking {
        val feedUrl = "https://ejemplo.com/podcast.xml"
        val localPodcast = PodcastEntity(
            id = "pod-1",
            title = "Podcast a Cancelar",
            author = "Autor",
            description = "Desc",
            feedUrl = feedUrl,
            artworkUrl = "",
            isSubscribed = true,
            subscribedAt = 5000L,
            unsubscribedAt = 0L
        )

        coEvery { mockDao.getPodcastByIdOrFeedUrl(feedUrl, feedUrl) } returns localPodcast

        val remoteData = NextcloudSyncData(
            subscriptions = listOf(
                NextcloudSubscription(
                    feedUrl = feedUrl,
                    title = "Podcast a Cancelar",
                    isSubscribed = false,
                    unsubscribedAt = 12000L // Lápida remota
                )
            )
        )

        syncManager.mergeRemoteStateIntoLocal(remoteData)

        coVerify(exactly = 1) {
            mockDao.updateSubscriptionWithTimestamps(
                podcastId = "pod-1",
                isSubscribed = false,
                subscribedAt = 5000L,
                unsubscribedAt = 12000L
            )
        }
    }

    /**
     * Test 4: Lápida remota obsoleta no cancela suscripción local reciente.
     * Si el usuario se volvió a suscribir localmente a los 20.000 ms y llega
     * una lápida remota antigua (12.000 ms), debe mantenerse suscrito.
     */
    @Test
    fun testObsoleteRemoteTombstoneDoesNotCancelRecentLocalSubscription() = runBlocking {
        val feedUrl = "https://ejemplo.com/podcast.xml"
        val localPodcast = PodcastEntity(
            id = "pod-1",
            title = "Podcast Activo",
            author = "Autor",
            description = "Desc",
            feedUrl = feedUrl,
            artworkUrl = "",
            isSubscribed = true,
            subscribedAt = 20000L, // Suscripción local más reciente
            unsubscribedAt = 0L
        )

        coEvery { mockDao.getPodcastByIdOrFeedUrl(feedUrl, feedUrl) } returns localPodcast

        val remoteData = NextcloudSyncData(
            subscriptions = listOf(
                NextcloudSubscription(
                    feedUrl = feedUrl,
                    title = "Podcast Activo",
                    isSubscribed = false,
                    unsubscribedAt = 12000L // Lápida vieja
                )
            )
        )

        syncManager.mergeRemoteStateIntoLocal(remoteData)

        // No se debe cambiar a isSubscribed = false
        coVerify(exactly = 0) {
            mockDao.updateSubscriptionWithTimestamps(
                podcastId = "pod-1",
                isSubscribed = false,
                subscribedAt = any(),
                unsubscribedAt = any()
            )
        }
    }

    /**
     * Test 5: Resolución de posición de escucha más reciente y rebobinado.
     * Si localmente el episodio estaba en el segundo 600 (positionUpdatedAt = 1000),
     * y el usuario rebobinó en otro dispositivo al segundo 120 (lastUpdatedTimestamp = 2000),
     * el merge debe aceptar los 120 segundos porque la marca de tiempo remota es más nueva.
     */
    @Test
    fun testEpisodeRewindPreservedWhenRemoteIsMoreRecent() = runBlocking {
        val episodeId = "ep-42"
        val localEpisode = EpisodeEntity(
            id = episodeId,
            podcastId = "pod-1",
            title = "Episodio Rebobinado",
            description = "Desc",
            audioUrl = "https://audio.com/ep42.mp3",
            imageUrl = "",
            pubDate = 1000L,
            duration = 1800000L,
            currentPositionMs = 600000L, // 10 minutos
            positionUpdatedAt = 1000L,
            isPlayed = false
        )

        coEvery { mockDao.getEpisodeById(episodeId) } returns localEpisode

        val remoteData = NextcloudSyncData(
            episodesProgress = listOf(
                NextcloudEpisodeProgress(
                    episodeId = episodeId,
                    currentPositionMs = 120000L, // Rebobinado a 2 minutos
                    isPlayed = false,
                    lastUpdatedTimestamp = 2000L // Más reciente que el local (2000 > 1000)
                )
            )
        )

        syncManager.mergeRemoteStateIntoLocal(remoteData)

        coVerify(exactly = 1) {
            mockDao.updateEpisodePlaybackState(
                episodeId = episodeId,
                isPlayed = false,
                positionMs = 120000L,
                now = 2000L
            )
        }
    }

    /**
     * Test 6: Posición local más reciente prevalece sobre remota antigua.
     */
    @Test
    fun testLocalProgressPreservedWhenLocalIsMoreRecentThanRemote() = runBlocking {
        val episodeId = "ep-43"
        val localEpisode = EpisodeEntity(
            id = episodeId,
            podcastId = "pod-1",
            title = "Episodio en curso",
            description = "Desc",
            audioUrl = "https://audio.com/ep43.mp3",
            imageUrl = "",
            pubDate = 1000L,
            duration = 1800000L,
            currentPositionMs = 900000L, // 15 minutos
            positionUpdatedAt = 3000L, // Local posterior (3000 > 2000)
            isPlayed = false
        )

        coEvery { mockDao.getEpisodeById(episodeId) } returns localEpisode

        val remoteData = NextcloudSyncData(
            episodesProgress = listOf(
                NextcloudEpisodeProgress(
                    episodeId = episodeId,
                    currentPositionMs = 300000L, // 5 minutos viejo
                    isPlayed = false,
                    lastUpdatedTimestamp = 2000L
                )
            )
        )

        syncManager.mergeRemoteStateIntoLocal(remoteData)

        // No se debe llamar a updateEpisodePlaybackState porque local >= remote y no hay cambios
        coVerify(exactly = 0) {
            mockDao.updateEpisodePlaybackState(
                episodeId = episodeId,
                isPlayed = any(),
                positionMs = 300000L,
                now = any()
            )
        }
    }

    /**
     * Test 7: Desmarcado de episodio completado (isPlayed = false) respetado por marca de tiempo.
     */
    @Test
    fun testUnmarkEpisodeAsPlayedPropagatesCorrectly() = runBlocking {
        val episodeId = "ep-44"
        val localEpisode = EpisodeEntity(
            id = episodeId,
            podcastId = "pod-1",
            title = "Episodio Escuchado",
            description = "Desc",
            audioUrl = "https://audio.com/ep44.mp3",
            imageUrl = "",
            pubDate = 1000L,
            duration = 1800000L,
            currentPositionMs = 1800000L,
            positionUpdatedAt = 1000L,
            isPlayed = true
        )

        coEvery { mockDao.getEpisodeById(episodeId) } returns localEpisode

        val remoteData = NextcloudSyncData(
            episodesProgress = listOf(
                NextcloudEpisodeProgress(
                    episodeId = episodeId,
                    currentPositionMs = 0L,
                    isPlayed = false, // Desmarcado en otro dispositivo
                    lastUpdatedTimestamp = 2500L // Más nuevo
                )
            )
        )

        syncManager.mergeRemoteStateIntoLocal(remoteData)

        coVerify(exactly = 1) {
            mockDao.updateEpisodePlaybackState(
                episodeId = episodeId,
                isPlayed = false,
                positionMs = 0L,
                now = 2500L
            )
        }
    }

    /**
     * Test 8: Validación de seguridad en Nextcloud: HTTPS estricto o localhost.
     */
    @Test
    fun testNextcloudSecurityServerUrlValidation() {
        assertTrue(NextcloudSyncManager.isSecureServerUrl("https://nube.midominio.com"))
        assertTrue(NextcloudSyncManager.isSecureServerUrl("https://192.168.1.100:8443"))
        assertTrue(NextcloudSyncManager.isSecureServerUrl("http://localhost:8080"))
        assertTrue(NextcloudSyncManager.isSecureServerUrl("http://127.0.0.1:8080"))
        assertFalse(NextcloudSyncManager.isSecureServerUrl("http://servidor-remoto-inseguro.org"))
    }
}
