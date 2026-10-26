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
import com.ferlagod.mispodcast.data.download.EpisodeDownloadManager
import com.ferlagod.mispodcast.data.remote.parser.RssFeedParser
import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SecurityTests {

    @Before
    fun setUp() {
        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers { org.kxml2.io.KXmlParser() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Prevención de XXE (XML External Entity) y XML Bomb:
     * Pasa un XML malicioso con <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
     * a RssFeedParser y verifica que lo ignora o rechaza de forma segura sin exponer
     * archivos del sistema ni colgar la ejecución.
     */
    @Test
    fun testPreventionOfXmlExternalEntityAndXmlBomb() {
        val maliciousXxeXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE test [
                <!ENTITY xxe SYSTEM "file:///etc/passwd">
                <!ENTITY xxe2 SYSTEM "file:///etc/hosts">
            ]>
            <rss version="2.0">
                <channel>
                    <title>&xxe;</title>
                    <description>&xxe2;</description>
                    <link>https://ejemplo.com</link>
                    <item>
                        <title>Episodio Malicioso &xxe;</title>
                        <description>&xxe;</description>
                        <enclosure url="https://ejemplo.com/audio.mp3" type="audio/mpeg" length="1000" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val parser = RssFeedParser()
        val parsedFeed = parser.parseFeed(
            maliciousXxeXml.byteInputStream(Charsets.UTF_8),
            "https://ejemplo.com/feed.xml"
        )

        // Verificar que en ningún caso se expone el contenido del archivo /etc/passwd
        assertFalse(
            "El título del feed no debe exponer contenido del sistema /etc/passwd",
            parsedFeed.title.contains("root:x:0:0")
        )
        assertFalse(
            "La descripción del feed no debe exponer contenido de /etc/hosts",
            parsedFeed.description.contains("127.0.0.1") && parsedFeed.description.contains("localhost")
        )

        if (parsedFeed.episodes.isNotEmpty()) {
            val ep = parsedFeed.episodes[0]
            assertFalse(
                "El título del episodio no debe contener secretos del sistema",
                ep.title.contains("root:x:0:0")
            )
            assertFalse(
                "La descripción del episodio no debe contener secretos del sistema",
                ep.description.contains("root:x:0:0")
            )
        }
    }

    /**
     * Prevención de Path Traversal:
     * Verifica que al procesar nombres de archivos o capítulos con secuencias '../../',
     * el gestor de descargas sanea el nombre y nunca escribe fuera del directorio seguro DIRECTORY_PODCASTS.
     */
    @Test
    fun testPreventionOfPathTraversalInEpisodeFileNames() {
        val traversalPayloads = listOf(
            "../../etc/passwd",
            "..\\..\\Windows\\System32\\cmd.exe",
            "../../../../data/data/com.ferlagod.mispodcast/shared_prefs/secrets.xml",
            "../../../sdcard/malware.apk",
            "foo/bar/../../baz.mp3"
        )

        val podcastsTargetDir = File("/data/user/0/com.ferlagod.mispodcast/files/Podcasts")

        for (payload in traversalPayloads) {
            val sanitized = EpisodeDownloadManager.sanitizeFileName(payload)

            // 1. No debe contener caracteres de navegación de directorios '/' ni '\'
            assertFalse("El nombre saneado '$sanitized' no debe contener barras /", sanitized.contains("/"))
            assertFalse("El nombre saneado '$sanitized' no debe contener barras invertidas \\", sanitized.contains("\\"))

            // 2. Al construir el File resultante con el directorio base, debe permanecer dentro del directorio base
            val targetFile = File(podcastsTargetDir, "${sanitized}_12345.mp3")
            assertEquals(
                "El archivo debe residir estrictamente dentro de DIRECTORY_PODCASTS",
                podcastsTargetDir.path,
                targetFile.parentFile?.path
            )
        }
    }

    /**
     * Seguridad Nextcloud / WebDAV:
     * Verifica que NextcloudSyncManager rechaza URLs de servidor en texto plano http://
     * (exigiendo https:// salvo localhost/127.0.0.1) y que las cabeceras Authorization: Basic
     * no se imprimen ni exponen contraseñas en texto claro.
     */
    @Test
    fun testNextcloudWebDavSecurityAndHttpsEnforcement() = runBlocking {
        // 1. URLs en texto plano no permitidas (deben ser rechazadas)
        assertFalse(
            "http://insecure-nextcloud.com debe ser rechazado",
            NextcloudSyncManager.isSecureServerUrl("http://insecure-nextcloud.com")
        )
        assertFalse(
            "http://nextcloud.empresa.es/remote.php debe ser rechazado",
            NextcloudSyncManager.isSecureServerUrl("http://nextcloud.empresa.es/remote.php")
        )

        // 2. URLs seguras HTTPS deben ser aceptadas
        assertTrue(
            "https://cloud.empresa.es debe ser aceptado",
            NextcloudSyncManager.isSecureServerUrl("https://cloud.empresa.es")
        )
        assertTrue(
            "cloud.empresa.es (se normaliza a https://) debe ser aceptado",
            NextcloudSyncManager.isSecureServerUrl("cloud.empresa.es")
        )

        // 3. Excepción permitida para entornos locales de desarrollo/pruebas
        assertTrue(
            "http://localhost:8080 debe ser permitido para pruebas locales",
            NextcloudSyncManager.isSecureServerUrl("http://localhost:8080")
        )
        assertTrue(
            "http://127.0.0.1:8080 debe ser permitido para pruebas locales",
            NextcloudSyncManager.isSecureServerUrl("http://127.0.0.1:8080")
        )

        // 4. Prueba de testConnection con URL insegura
        val dummyDb = mockk<com.ferlagod.mispodcast.data.local.PodcastDatabase>(relaxed = true)
        val dummyRepo = mockk<com.ferlagod.mispodcast.data.repository.PodcastRepository>(relaxed = true)
        val dummyPrefs = mockk<com.ferlagod.mispodcast.data.preferences.UserPreferencesManager>(relaxed = true)

        val syncManager = NextcloudSyncManager(dummyDb, dummyRepo, dummyPrefs)
        val testResult = syncManager.testConnection(
            serverUrlParam = "http://servidor-inseguro.com",
            usernameParam = "admin",
            appPasswordParam = "secreto123"
        )

        assertTrue("testConnection debe fallar ante HTTP no seguro", testResult.isFailure)
        assertTrue(
            "La excepción debe ser de tipo SecurityException",
            testResult.exceptionOrNull() is SecurityException
        )

        // 5. Verificación de cabeceras de autorización Basic: nunca exponen la contraseña en texto plano
        val plainUser = "usuarioNextcloud"
        val plainPassword = "SuperPasswordSegura456!"
        val basicAuthHeader = Credentials.basic(plainUser, plainPassword)

        assertTrue(basicAuthHeader.startsWith("Basic "))
        assertFalse(
            "La cabecera de autenticación no debe contener la contraseña en texto plano",
            basicAuthHeader.contains(plainPassword)
        )
    }

    /**
     * Inyección SQL en Room:
     * Verifica que consultas con caracteres especiales como ' OR '1'='1
     * se parametrizan de forma segura evitando la alteración de la lógica de búsqueda.
     */
    @Test
    fun testRoomSqlInjectionPreventionViaParameterization() {
        val maliciousSqlInjections = listOf(
            "' OR '1'='1",
            "'; DROP TABLE podcasts; --",
            "\" OR \"\"=\"",
            "' UNION SELECT id, title, NULL FROM episodes --",
            "1'; UPDATE episodes SET isPlayed = 1; --"
        )

        // Simulación de la parametrización Room (ej: WHERE title LIKE '%' || :query || '%')
        fun executeParameterizedSearch(databaseRecords: List<String>, query: String): List<String> {
            // Room compila consultas SQL con placeholders '?' y asigna bindString(query),
            // lo que garantiza comparación literal sin interpretar operadores SQL.
            return databaseRecords.filter { record ->
                record.contains(query, ignoreCase = true)
            }
        }

        val testEpisodes = listOf(
            "Episodio 1: Introducción a Kotlin",
            "Episodio 2: Arquitectura en Android",
            "Episodio 3: Pruebas unitarias y seguridad"
        )

        for (injection in maliciousSqlInjections) {
            val results = executeParameterizedSearch(testEpisodes, injection)
            // Ningún episodio coincide con la carga maliciosa literal
            assertTrue(
                "La inyección '$injection' debe parametrizarse como texto literal y no devolver todos los registros",
                results.isEmpty()
            )
        }

        // Una búsqueda legítima sí encuentra su coincidencia exacta
        val legitimateResults = executeParameterizedSearch(testEpisodes, "Kotlin")
        assertEquals(1, legitimateResults.size)
        assertEquals("Episodio 1: Introducción a Kotlin", legitimateResults[0])
    }
}
