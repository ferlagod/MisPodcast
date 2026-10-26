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
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.ferlagod.mispodcast.playback.PodcastPlaybackService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTests {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Comprueba en menos de 10 segundos que la aplicación arranca sin crashear,
     * que la barra inferior muestra los 5 accesos (Inicio, Cola, Novedades, Suscripciones y Más),
     * y que al interactuar con cada pestaña se navega fluidamente sin excepciones.
     */
    @Test
    fun testAppLaunchesWithoutCrashAndBottomBarDisplaysAllFiveIcons() {
        composeTestRule.waitForIdle()

        // 1. Verificar la visibilidad de los 5 elementos de la barra inferior mediante testTag independiente de idioma
        composeTestRule.onNodeWithTag("nav_item_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_queue").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_new_releases").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_item_subscriptions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_options_button").assertIsDisplayed()

        // 2. Navegación secuencial por cada pestaña
        composeTestRule.onNodeWithTag("nav_item_queue").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_item_new_releases").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_item_subscriptions").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_item_home").performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * Verifica que en AndroidManifest.xml el servicio PodcastPlaybackService
     * tiene declarado el permiso FOREGROUND_SERVICE_MEDIA_PLAYBACK para cumplir
     * con los requisitos de Android 14 y 15+.
     */
    @Test
    fun testManifestDeclaresForegroundServiceMediaPlaybackPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val packageName = context.packageName

        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of((PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES).toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        }

        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        assertTrue(
            "AndroidManifest.xml debe declarar el permiso android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            requestedPermissions.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
        )

        val serviceInfo = packageInfo.services?.firstOrNull { it.name == PodcastPlaybackService::class.java.name }
        assertNotNull(
            "PodcastPlaybackService debe estar declarado como servicio en el AndroidManifest",
            serviceInfo
        )
    }
}
