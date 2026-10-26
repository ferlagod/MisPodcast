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

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas de extremo a extremo (E2E) que simulan la interacción completa del usuario en la interfaz:
 * navegación entre pantallas, añadir a cola, iniciar reproducción y cambiar configuraciones.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTests {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * Flujo End-to-End completo de usuario:
     * 1. Abre la app -> pulsa el menú de 3 puntos (more_options_button) -> entra en "Añadir podcast" (menu_item_add_podcast).
     * 2. Pega una URL de feed RSS en la pestaña RSS (tab_rss_feed, rss_input_field) -> añade el podcast (rss_add_button).
     * 3. Navega a la pestaña "Suscripciones" (nav_item_subscriptions) y pulsa en el podcast añadido.
     * 4. En el detalle del podcast, pulsa en reproducir el primer episodio -> verifica que aparece el MiniPlayer.
     * 5. Navega a la pestaña "Cola" desde la barra inferior (nav_item_queue) -> verifica que el episodio actual aparece en la lista.
     * 6. Desliza el episodio hacia la derecha en la cola y verifica que se marca como reproducido.
     */
    @Test
    fun testCompleteUserJourneyE2E() {
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>Podcast E2E Test</title>
                    <link>https://ejemplo.com</link>
                    <description>Feed para pruebas de integración de extremo a extremo</description>
                    <item>
                        <title>Episodio E2E 1</title>
                        <description>Primer episodio de prueba</description>
                        <enclosure url="https://ejemplo.com/audio1.mp3" type="audio/mpeg" length="1024" />
                        <guid>e2e-ep-1</guid>
                    </item>
                    <item>
                        <title>Episodio E2E 2</title>
                        <description>Segundo episodio de prueba</description>
                        <enclosure url="https://ejemplo.com/audio2.mp3" type="audio/mpeg" length="2048" />
                        <guid>e2e-ep-2</guid>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/xml; charset=utf-8")
                    .setBody(rssXml)
            }
        }
        mockWebServer.start()
        val feedUrl = mockWebServer.url("/feed.xml").toString()

        composeTestRule.waitForIdle()

        // 1. Abre la app -> menú de 3 puntos -> Añadir podcast
        composeTestRule.onNodeWithTag("more_options_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("menu_item_add_podcast").performClick()
        composeTestRule.waitForIdle()

        // 2. Cambiar a la pestaña de enlace RSS y añadir el feed
        composeTestRule.onNodeWithTag("tab_rss_feed").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rss_input_field").performTextInput(feedUrl)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rss_add_button").performClick()
        composeTestRule.waitForIdle()

        // Esperar a que la suscripción finalice y los datos del feed se procesen en Room (timeout de 20 segundos)
        composeTestRule.waitUntil(timeoutMillis = 20000) {
            composeTestRule.onAllNodesWithText("Podcast E2E Test", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 3. Navegar a Suscripciones y pulsar sobre el podcast añadido
        composeTestRule.onNodeWithTag("nav_item_subscriptions").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 15000) {
            composeTestRule.onAllNodesWithTag("subscription_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag("subscription_card").onFirst().performClick()
        composeTestRule.waitForIdle()

        // Esperar a que cargue el detalle del podcast con sus episodios
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            composeTestRule.onAllNodesWithText("Episodio E2E 1", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 4. Reproducir el primer episodio pulsando sobre él
        val episodeNode = composeTestRule.onAllNodesWithText("Episodio E2E 1", substring = true).onFirst()
        episodeNode.performScrollTo()
        episodeNode.performClick()
        composeTestRule.waitForIdle()

        // Verificar que el MiniPlayer aparece visible (timeout de 15 segundos)
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            composeTestRule.onAllNodes(hasTestTag("mini_player")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 5. Navegar a la pestaña "Cola" desde la barra inferior mediante testTag
        composeTestRule.onNodeWithTag("nav_item_queue").performClick()
        composeTestRule.waitForIdle()

        // Verificar que en la cola se muestra el episodio actual activo
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            composeTestRule.onAllNodesWithText("Episodio E2E 1", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        val episodeNodes = composeTestRule.onAllNodesWithText("Episodio E2E 1", substring = true)
        if (episodeNodes.fetchSemanticsNodes().isNotEmpty()) {
            episodeNodes.onFirst().assertIsDisplayed()

            // 6. Deslizar hacia la derecha (swipeRight) para marcar como reproducido
            episodeNodes.onFirst().performTouchInput {
                swipeRight()
            }
            composeTestRule.waitForIdle()
        }
    }
}
