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

import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import com.ferlagod.mispodcast.util.ShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas de la lógica de compartir: elección de enlace, minuto y composición del texto. */
class ShareTests {

    @Test
    fun episodePageIsPreferredOverAudio() {
        val link = ShareHelper.resolveEpisodeLink(
            episodeLink = "https://example.com/ep/42",
            audioUrl = "https://cdn.example.com/42.mp3",
            podcastLink = "https://example.com"
        )
        assertEquals(ShareHelper.EpisodeLink("https://example.com/ep/42", isWebPage = true), link)
    }

    @Test
    fun genericPodcastPageFallsBackToCleanAudio() {
        // Muchos feeds repiten en cada episodio el <link> del canal
        val link = ShareHelper.resolveEpisodeLink(
            episodeLink = "https://www.example.com/",
            audioUrl = "https://dts.podtrac.com/redirect.mp3/cdn.example.com/a.mp3?utm_source=x",
            podcastLink = "https://example.com"
        )
        assertEquals(ShareHelper.EpisodeLink("https://cdn.example.com/a.mp3", isWebPage = false), link)
    }

    @Test
    fun audioLinkCarriesMediaFragmentTimestamp() {
        val link = ShareHelper.resolveEpisodeLink(
            episodeLink = null,
            audioUrl = "https://cdn.example.com/a.mp3?id=1",
            positionMs = 754_900L
        )
        assertEquals("https://cdn.example.com/a.mp3?id=1#t=754", link?.url)
    }

    @Test
    fun localAudioIsNeverShared() {
        assertNull(ShareHelper.resolveEpisodeLink(null, "content://com.android.externalstorage/doc/a.mp3"))
        assertNull(ShareHelper.resolveEpisodeLink("", "file:///sdcard/a.mp3"))
        // Si solo hay página genérica, se usa antes que nada
        assertEquals(
            "https://example.com",
            ShareHelper.resolveEpisodeLink("https://example.com", "content://x/a.mp3", "https://example.com")?.url
        )
    }

    @Test
    fun composeTextOrdersLinesAndSkipsBlanks() {
        val text = ShareHelper.composeText(
            headline = "Episodio — Podcast",
            fromTimeLine = "Escúchalo desde el 12:34",
            note = "  qué bueno  ",
            link = "https://e.com/1",
            extraLines = listOf("", "Feed RSS: https://e.com/feed"),
            signature = "Compartido desde Mis Podcast"
        )
        assertEquals(
            "Episodio — Podcast\nEscúchalo desde el 12:34\n\"qué bueno\"\nhttps://e.com/1\nFeed RSS: https://e.com/feed\n\nCompartido desde Mis Podcast",
            text
        )
        assertEquals("Solo título", ShareHelper.composeText("Solo título", fromTimeLine = "", note = null, link = null, signature = null))
    }

    @Test
    fun timestampFormat() {
        assertEquals("0:05", ShareHelper.formatTimestamp(5_000L))
        assertEquals("12:34", ShareHelper.formatTimestamp(754_000L))
        assertEquals("1:02:03", ShareHelper.formatTimestamp(3_723_000L))
    }

    @Test
    fun parserReadsEpisodeLinkFromRssAndAtom() {
        val rss = """
            <rss><channel><title>Demo</title><link>https://example.com</link>
              <item>
                <title>Uno</title><link>https://example.com/uno</link>
                <enclosure url="https://cdn.example.com/1.mp3" type="audio/mpeg" length="1"/>
              </item>
              <item>
                <title>Sin enlace</title>
                <enclosure url="https://cdn.example.com/2.mp3" type="audio/mpeg" length="1"/>
              </item>
            </channel></rss>
        """.trimIndent()
        val rssFeed = RssFeedParser().parseFeed(rss.byteInputStream(), "https://example.com/feed")
        assertEquals("https://example.com/uno", rssFeed.episodes[0].link)
        assertNull(rssFeed.episodes[1].link)
        assertEquals("https://example.com", rssFeed.link)

        val atom = """
            <feed xmlns="http://www.w3.org/2005/Atom"><title>Atom</title>
              <entry>
                <title>Atom 1</title>
                <link rel="enclosure" href="https://cdn.example.com/a.mp3" type="audio/mpeg"/>
                <link rel="alternate" href="https://example.com/atom-1"/>
                <enclosure url="https://cdn.example.com/a.mp3" type="audio/mpeg" length="1"/>
              </entry>
            </feed>
        """.trimIndent()
        val atomFeed = RssFeedParser().parseFeed(atom.byteInputStream(), "https://example.com/atom")
        assertEquals("https://example.com/atom-1", atomFeed.episodes.single().link)
    }
}
