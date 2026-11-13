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

import android.util.Xml
import com.ferlagod.mispodcast.data.remote.parser.PodcastSoundbite
import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import com.ferlagod.mispodcast.ui.screens.EpisodeFilter
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.util.AudioTrackingSanitizer
import com.ferlagod.mispodcast.util.TimestampParser
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas de las funciones añadidas: marcas de tiempo, soundbites, alternateEnclosure,
 * URLs de audio originales con ID estable y filtros de episodios.
 */
class NewFeaturesTests {

    @Before
    fun setUp() {
        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers { org.kxml2.io.KXmlParser() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun timestampsAreDetectedAndInvalidOnesIgnored() {
        val text = "Intro 0:45, tema 12:34, cierre 1:02:03. Fecha 2024-10-31, hora 25:99 y ratio 3:2"
        val found = TimestampParser.findTimestamps(text)
        assertEquals(listOf(45_000L, 754_000L, 3_723_000L), found.map { it.positionMs })
        assertEquals("12:34", text.substring(found[1].start, found[1].end))
    }

    @Test
    fun timestampsBeyondDurationAreIgnored() {
        val found = TimestampParser.findTimestamps("Ver 05:00 y 45:00", maxDurationMs = 10 * 60_000L)
        assertEquals(listOf(300_000L), found.map { it.positionMs })
        assertEquals("1:02:03", TimestampParser.format(3_723_000L))
        assertEquals("5:07", TimestampParser.format(307_000L))
    }

    @Test
    fun parserReadsSoundbitesAlternateEnclosureAndKeepsRawUrlWithStableId() {
        val xml = """
            <rss xmlns:podcast="https://podcastindex.org/namespace/1.0">
              <channel>
                <title>Demo</title>
                <item>
                  <title>Con tracker</title>
                  <enclosure url="https://dts.podtrac.com/redirect.mp3/cdn.example.com/a.mp3" type="audio/mpeg" length="1"/>
                  <podcast:soundbite startTime="73.5" duration="60.0">Lo mejor</podcast:soundbite>
                </item>
                <item>
                  <guid>alt-1</guid>
                  <title>Solo alternativo</title>
                  <podcast:alternateEnclosure type="video/mp4"><podcast:source uri="https://cdn.example.com/v.mp4"/></podcast:alternateEnclosure>
                  <podcast:alternateEnclosure type="audio/opus"><podcast:source uri="https://cdn.example.com/b.opus"/></podcast:alternateEnclosure>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val feed = RssFeedParser().parseFeed(xml.byteInputStream(), "https://example.com/feed")
        assertEquals(2, feed.episodes.size)

        val tracked = feed.episodes[0]
        assertEquals("https://dts.podtrac.com/redirect.mp3/cdn.example.com/a.mp3", tracked.audioUrl)
        val idKey = AudioTrackingSanitizer.stableIdKey(tracked.audioUrl)
        assertEquals("https://example.com/feed_${idKey.hashCode()}", tracked.id)
        assertEquals(listOf(PodcastSoundbite(73_500L, 60_000L, "Lo mejor")), tracked.soundbites)

        val alt = feed.episodes[1]
        assertEquals("https://cdn.example.com/b.opus", alt.audioUrl)
        assertEquals("audio/opus", alt.audioType)
    }

    @Test
    fun soundbitesRoundTripThroughJson() {
        val list = listOf(PodcastSoundbite(1_000L, 30_000L, "A"), PodcastSoundbite(5_000L, 10_000L))
        assertEquals(list, PodcastSoundbite.decode(PodcastSoundbite.encode(list)))
        assertTrue(PodcastSoundbite.decode("no-json").isEmpty())
        assertEquals(null, PodcastSoundbite.encode(emptyList()))
    }

    @Test
    fun episodeFiltersMatchExpectedEpisodes() {
        fun ep(played: Boolean = false, pos: Long = 0L, dur: Long = 3_600_000L, fav: Boolean = false, dl: Boolean = false) =
            EpisodeEntity(id = "e", podcastId = "p", title = "t", description = "", audioUrl = "u", imageUrl = "",
                pubDate = 0L, duration = dur, currentPositionMs = pos, isPlayed = played, isFavorite = fav, isDownloaded = dl)

        assertTrue(EpisodeFilter.UNPLAYED.matches(ep(), null))
        assertFalse(EpisodeFilter.UNPLAYED.matches(ep(pos = 5_000L), null))
        assertTrue(EpisodeFilter.IN_PROGRESS.matches(ep(pos = 5_000L), null))
        assertFalse(EpisodeFilter.IN_PROGRESS.matches(ep(played = true, pos = 5_000L), null))
        assertTrue(EpisodeFilter.DOWNLOADED.matches(ep(dl = true), null))
        assertTrue(EpisodeFilter.FAVORITES.matches(ep(fav = true), null))
        assertTrue(EpisodeFilter.SHORT.matches(ep(dur = 20 * 60_000L), null))
        assertFalse(EpisodeFilter.SHORT.matches(ep(dur = 0L), null))
    }
}
