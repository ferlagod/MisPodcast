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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Xml
import com.ferlagod.mispodcast.data.backup.OpmlExporter
import com.ferlagod.mispodcast.data.backup.OpmlImporter
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.mapper.PodcastLanguageMapper
import com.ferlagod.mispodcast.data.mapper.toEpisodeEntity
import com.ferlagod.mispodcast.data.mapper.toPodcastEntity
import com.ferlagod.mispodcast.data.remote.parser.PodcastPerson
import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import kotlinx.serialization.json.Json
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Batería de pruebas unitarias locales para validar el parseo de feeds RSS, extensiones Podcasting 2.0,
 * exportación e importación OPML y cálculo de modelos de datos.
 */
class UnitTests {

    @Before
    fun setUp() {
        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers { org.kxml2.io.KXmlParser() }

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val urlString = firstArg<String>()
            val uriMock = mockk<Uri>()
            every { uriMock.toString() } returns urlString
            uriMock
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testRssFeedParserStandardAndPodcast20Tags() {
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" 
                xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
                xmlns:podcast="https://podcastindex.org/namespace/1.0"
                xmlns:content="http://purl.org/rss/1.0/modules/content/">
                <channel>
                    <title>Tecnología y Futuro</title>
                    <link>https://ejemplo.com</link>
                    <description>Un podcast sobre innovación</description>
                    <itunes:author>María García</itunes:author>
                    <itunes:image href="https://ejemplo.com/cover.jpg" />
                    <itunes:category text="Technology" />
                    <podcast:funding url="https://ejemplo.com/donar">Apoya a nuestro podcast</podcast:funding>
                    <podcast:value type="lightning" method="keysend" />
                    
                    <item>
                        <title>Episodio 1: Inteligencia Artificial</title>
                        <description>Todo sobre los nuevos modelos de lenguaje</description>
                        <guid>ep-101-ai</guid>
                        <pubDate>Mon, 15 Jan 2026 10:00:00 +0000</pubDate>
                        <itunes:duration>01:15:30</itunes:duration>
                        <itunes:image href="https://ejemplo.com/ep1.jpg" />
                        <enclosure url="https://ejemplo.com/audio/ep1.mp3" type="audio/mpeg" length="54321000" />
                        <podcast:chapters url="https://ejemplo.com/chapters/ep1.json" type="application/json+chapters" />
                        <podcast:transcript url="https://ejemplo.com/transcripts/ep1.vtt" type="text/vtt" />
                        <podcast:funding url="https://ejemplo.com/donate-ep1" />
                        <podcast:value type="lightning" method="keysend" />
                        <podcast:person role="host" group="cast" href="https://ejemplo.com/host" img="https://ejemplo.com/host.jpg">
                            María García
                        </podcast:person>
                        <podcast:person role="guest" group="cast" href="https://ejemplo.com/guest" img="https://ejemplo.com/guest.jpg">
                            Carlos Ingeniero
                        </podcast:person>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val parser = RssFeedParser()
        val parsedFeed = parser.parseFeed(xmlContent.byteInputStream(Charsets.UTF_8), "https://ejemplo.com/feed.xml")

        assertEquals("Tecnología y Futuro", parsedFeed.title)
        assertEquals("María García", parsedFeed.author)
        assertEquals("https://ejemplo.com/donar", parsedFeed.fundingUrl)
        assertEquals("Apoya a nuestro podcast", parsedFeed.fundingText)
        assertEquals("type=lightning;method=keysend", parsedFeed.valueForValueInfo)
        assertEquals(1, parsedFeed.episodes.size)

        val podcastEntity = parsedFeed.toPodcastEntity()
        assertEquals("Tecnología y Futuro", podcastEntity.title)
        assertEquals("María García", podcastEntity.author)
        assertEquals("https://ejemplo.com/feed.xml", podcastEntity.feedUrl)
        assertEquals("https://ejemplo.com/cover.jpg", podcastEntity.artworkUrl)
        assertEquals("https://ejemplo.com/donar", podcastEntity.fundingUrl)
        assertEquals("Apoya a nuestro podcast", podcastEntity.fundingText)
        assertEquals("type=lightning;method=keysend", podcastEntity.valueForValueInfo)

        val parsedEp = parsedFeed.episodes[0]
        val episodeEntity = parsedEp.toEpisodeEntity(podcastId = podcastEntity.id)

        assertEquals("ep-101-ai", episodeEntity.id)
        assertEquals(podcastEntity.id, episodeEntity.podcastId)
        assertEquals("Episodio 1: Inteligencia Artificial", episodeEntity.title)
        assertEquals("https://ejemplo.com/audio/ep1.mp3", episodeEntity.audioUrl)
        assertEquals("https://ejemplo.com/ep1.jpg", episodeEntity.imageUrl)
        assertEquals("https://ejemplo.com/chapters/ep1.json", episodeEntity.chaptersUrl)
        assertEquals("https://ejemplo.com/transcripts/ep1.vtt", episodeEntity.transcriptUrl)
        assertEquals("https://ejemplo.com/donate-ep1", episodeEntity.fundingUrl)
        assertEquals("type=lightning;method=keysend", episodeEntity.valueForValueInfo)
        assertEquals(4530000L, episodeEntity.duration)

        assertNotNull(episodeEntity.persons)
        val personsList: List<PodcastPerson> = Json.decodeFromString(episodeEntity.persons!!)
        assertEquals(2, personsList.size)
        assertEquals("María García", personsList[0].name)
        assertEquals("host", personsList[0].role)
        assertEquals("Carlos Ingeniero", personsList[1].name)
        assertEquals("guest", personsList[1].role)
    }

    @Test
    fun testPodcastLanguageMapperSupportedLanguagesAndFallback() {
        val supportedCodes = listOf("es", "en", "fr", "de", "it", "pt")
        val expectedCountries = listOf("ES", "US", "FR", "DE", "IT", "PT")
        val expectedTechTerms = listOf("tecnologia", "technology", "technologie", "technologie", "tecnologia", "tecnologia")
        val expectedParams = listOf("es_es", "en_us", "fr_fr", "de_de", "it_it", "pt_pt")

        for (i in supportedCodes.indices) {
            val code = supportedCodes[i]
            assertEquals("El país para $code debe ser ${expectedCountries[i]}", expectedCountries[i], PodcastLanguageMapper.getCountryCode(code))
            assertEquals("El término traducido para $code debe ser ${expectedTechTerms[i]}", expectedTechTerms[i], PodcastLanguageMapper.getCategoryTerm(1318, code))
            assertEquals("El parámetro de idioma para $code debe ser ${expectedParams[i]}", expectedParams[i], PodcastLanguageMapper.getLanguageParam(code))
        }

        val unsupportedLanguages = listOf("ru", "ja", "zh", "ar", "ko")
        for (unsupported in unsupportedLanguages) {
            assertEquals("Idioma no soportado $unsupported debe hacer fallback a 'en'", "en", PodcastLanguageMapper.resolveLanguageCode(unsupported))
            assertEquals("País de fallback para $unsupported debe ser 'US'", "US", PodcastLanguageMapper.getCountryCode(unsupported))
            assertEquals("Término de fallback para $unsupported debe ser 'technology'", "technology", PodcastLanguageMapper.getCategoryTerm(1318, unsupported))
            assertEquals("Parámetro de fallback para $unsupported debe ser 'en_us'", "en_us", PodcastLanguageMapper.getLanguageParam(unsupported))
        }
    }

    @Test
    fun testOpmlExportAndImportPreservesAllUrls() = runBlocking {
        val originalPodcasts = listOf(
            PodcastEntity(
                id = "https://podcast1.com/rss",
                title = "Noticias Globales",
                author = "Redacción Global",
                description = "Noticias del mundo",
                feedUrl = "https://podcast1.com/rss",
                artworkUrl = "https://podcast1.com/cover.png",
                link = "https://podcast1.com",
                category = "Noticias"
            ),
            PodcastEntity(
                id = "https://podcast2.com/feed?id=42&sort=desc",
                title = "Ciencia & Más <Especial>",
                author = "Dr. Carl Sagan",
                description = "Cosmología y física",
                feedUrl = "https://podcast2.com/feed?id=42&sort=desc",
                artworkUrl = "https://podcast2.com/cover.png",
                link = "https://podcast2.com/home?ref=app",
                category = "Ciencia"
            )
        )

        val outputStream = ByteArrayOutputStream()
        val mockContext = mockk<Context>()
        val mockContentResolver = mockk<ContentResolver>()
        val mockUri = mockk<Uri>()

        every { mockContext.contentResolver } returns mockContentResolver
        every { mockContentResolver.openOutputStream(mockUri) } answers { outputStream }
        every { mockContentResolver.openInputStream(mockUri) } answers {
            ByteArrayInputStream(outputStream.toByteArray())
        }

        val exportResult = OpmlExporter.exportOpml(mockContext, mockUri, originalPodcasts)
        assertTrue(exportResult.isSuccess)
        assertEquals(2, exportResult.getOrNull())

        val generatedXml = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(generatedXml.contains("<opml version=\"2.0\">"))
        assertTrue(generatedXml.contains("xmlUrl=\"https://podcast1.com/rss\""))
        assertTrue(generatedXml.contains("xmlUrl=\"https://podcast2.com/feed?id=42&amp;sort=desc\""))

        val importResult = OpmlImporter.importOpml(mockContext, mockUri)
        assertTrue(importResult.isSuccess)

        val importedFeeds = importResult.getOrNull()
        assertNotNull(importedFeeds)
        assertEquals(2, importedFeeds!!.size)

        assertEquals("https://podcast1.com/rss", importedFeeds[0].xmlUrl)
        assertEquals("Noticias Globales", importedFeeds[0].title)

        assertEquals("https://podcast2.com/feed?id=42&sort=desc", importedFeeds[1].xmlUrl)
        assertEquals("Ciencia & Más <Especial>", importedFeeds[1].title)
    }
}
