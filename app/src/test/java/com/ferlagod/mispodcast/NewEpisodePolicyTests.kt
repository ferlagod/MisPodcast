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

import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.repository.NewEpisodePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodePolicyTests {

    private val hour = 3600_000L
    private val now = 1_800_000_000_000L

    private fun podcast(lastUpdated: Long, subscribedAt: Long = now - 30 * 24 * hour) = PodcastEntity(
        id = "pod",
        title = "Podcast",
        author = "Autor",
        description = "",
        feedUrl = "https://example.com/feed.xml",
        artworkUrl = "",
        subscribedAt = subscribedAt,
        lastUpdated = lastUpdated
    )

    private fun episode(id: String, pubDate: Long) = EpisodeEntity(
        id = id,
        podcastId = "pod",
        title = id,
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        imageUrl = "",
        pubDate = pubDate,
        duration = 0L
    )

    @Test
    fun episodioConFechaAnteriorALaUltimaComprobacionSigueSiendoNovedad() {
        // Fecha declarada 10:00, comprobación previa 10:05, aparece en el feed a las 10:20
        val lastCheck = now - 15 * 60_000L
        val late = episode("tardio", pubDate = lastCheck - 5 * 60_000L)

        val result = NewEpisodePolicy.selectGenuinelyNew(podcast(lastCheck), listOf(late), now)

        assertEquals(listOf(late), result)
    }

    @Test
    fun primeraSincronizacionNoProduceNovedades() {
        val result = NewEpisodePolicy.selectGenuinelyNew(
            podcast(lastUpdated = 0L),
            listOf(episode("a", now - hour)),
            now
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun episodiosAntiguosResubidosYAnterioresALaSuscripcionSeDescartan() {
        val p = podcast(lastUpdated = now - hour, subscribedAt = now - 24 * hour)
        val reuploaded = episode("viejo", now - 10 * 24 * hour)
        val beforeSubscription = episode("previo", now - 30 * hour)
        val fresh = episode("nuevo", now - 2 * hour)

        val result = NewEpisodePolicy.selectGenuinelyNew(p, listOf(reuploaded, beforeSubscription, fresh), now)

        assertEquals(listOf(fresh), result)
    }

    @Test
    fun laVentanaSeAmpliaSiHaceDiasQueNoSeComprobaba() {
        val p = podcast(lastUpdated = now - 5 * 24 * hour)
        val fourDaysOld = episode("cuatro", now - 4 * 24 * hour)

        assertEquals(listOf(fourDaysOld), NewEpisodePolicy.selectGenuinelyNew(p, listOf(fourDaysOld), now))
    }

    @Test
    fun reglaDeEncoladoUsaLaGlobalSoloConDefault() {
        assertEquals("TOP", NewEpisodePolicy.effectiveEnqueueRule(podcast(1L).copy(autoEnqueueRule = "DEFAULT"), "TOP"))
        assertEquals("BOTTOM", NewEpisodePolicy.effectiveEnqueueRule(podcast(1L).copy(autoEnqueueRule = "BOTTOM"), "TOP"))
    }
}
