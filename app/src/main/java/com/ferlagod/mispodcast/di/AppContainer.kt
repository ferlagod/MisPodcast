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

package com.ferlagod.mispodcast.di

import android.content.Context
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.data.remote.api.ApplePodcastSearchService
import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Contenedor de Inyección de Dependencias a nivel de aplicación (AppContainer).
 *
 * Centraliza y expone las instancias compartidas (singletons) fundamentales del sistema:
 * - Un único cliente HTTP [OkHttpClient] con caché en disco de 20 MB y pool de conexiones compartido.
 * - Una única instancia de base de datos Room [PodcastDatabase].
 * - Una única instancia del repositorio de datos [PodcastRepository].
 * - Servicios de red y sincronización reutilizables ([ApplePodcastSearchService], [RssFeedParser], [NextcloudSyncManager]).
 */
interface AppContainer {
    val database: PodcastDatabase
    val okHttpClient: OkHttpClient
    val applePodcastSearchService: ApplePodcastSearchService
    val rssFeedParser: RssFeedParser
    val preferencesManager: UserPreferencesManager
    val podcastRepository: PodcastRepository
    val nextcloudSyncManager: NextcloudSyncManager
    val sleepTimerManager: com.ferlagod.mispodcast.playback.SleepTimerManager
    val chapterRepository: com.ferlagod.mispodcast.data.repository.ChapterRepository
    val transcriptRepository: com.ferlagod.mispodcast.data.repository.TranscriptRepository
}

/**
 * Implementación predeterminada y perezosa (lazy) de [AppContainer].
 *
 * Inicializa cada componente bajo demanda garantizando un único punto de asignación
 * de recursos (hilos, sockets y memoria caché).
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: PodcastDatabase by lazy {
        PodcastDatabase.getInstance(context)
    }

    override val okHttpClient: OkHttpClient by lazy {
        val cacheDirectory = File(context.cacheDir, "http_cache")
        val cacheSize = 20L * 1024L * 1024L // 20 MB en disco para respuestas HTTP reutilizables

        OkHttpClient.Builder()
            .cache(Cache(cacheDirectory, cacheSize))
            .connectionPool(ConnectionPool(maxIdleConnections = 8, keepAliveDuration = 5, timeUnit = TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override val applePodcastSearchService: ApplePodcastSearchService by lazy {
        ApplePodcastSearchService.create(baseClient = okHttpClient)
    }

    override val rssFeedParser: RssFeedParser by lazy {
        val clientForFeeds = okHttpClient.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        RssFeedParser(okHttpClient = clientForFeeds)
    }

    override val preferencesManager: UserPreferencesManager by lazy {
        UserPreferencesManager.getInstance(context)
    }

    override val podcastRepository: PodcastRepository by lazy {
        PodcastRepository(
            podcastDao = database.podcastDao(),
            searchService = applePodcastSearchService,
            feedParser = rssFeedParser,
            preferencesManager = preferencesManager
        )
    }

    override val nextcloudSyncManager: NextcloudSyncManager by lazy {
        NextcloudSyncManager.getInstance(
            context = context,
            customRepository = podcastRepository,
            customClient = okHttpClient
        )
    }

    override val sleepTimerManager: com.ferlagod.mispodcast.playback.SleepTimerManager by lazy {
        com.ferlagod.mispodcast.playback.SleepTimerManager.getInstance(context)
    }

    override val chapterRepository: com.ferlagod.mispodcast.data.repository.ChapterRepository by lazy {
        com.ferlagod.mispodcast.data.repository.ChapterRepository(okHttpClient = okHttpClient)
    }

    override val transcriptRepository: com.ferlagod.mispodcast.data.repository.TranscriptRepository by lazy {
        com.ferlagod.mispodcast.data.repository.TranscriptRepository(okHttpClient = okHttpClient)
    }
}
