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

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ferlagod.mispodcast.data.preferences.AppThemeMode
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.data.preferences.UserSettings
import com.ferlagod.mispodcast.notification.NotificationHelper
import com.ferlagod.mispodcast.ui.components.MainScaffold
import com.ferlagod.mispodcast.ui.theme.MisPodcastTheme
import com.ferlagod.mispodcast.worker.PodcastCheckWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Actividad principal y punto de entrada de la aplicación Mis Podcast.
 *
 * Responsabilidades:
 * - Configurar la pantalla de bienvenida nativa (Splash Screen).
 * - Habilitar el diseño de borde a borde (Edge-to-Edge).
 * - Observar el tema configurado por el usuario (Claro, Oscuro o Automático según el sistema).
 * - Montar el andamiaje principal de Jetpack Compose mediante [MainScaffold].
 * - Procesar intents externos y notificaciones para navegar a episodios o programas concretos.
 * - Solicitar permisos de notificación en tiempo de ejecución (Android 13+) y activar comprobaciones en segundo plano.
 */
class MainActivity : AppCompatActivity() {

    private var targetPodcastId by mutableStateOf<String?>(null)
    private var targetFeedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleNotificationIntent(intent)

        setContent {
            val prefManager = remember { UserPreferencesManager.getInstance(applicationContext) }
            val settings by prefManager.userSettingsFlow.collectAsState(initial = UserSettings())

            val isAmoled = settings.themeMode == AppThemeMode.AMOLED
            val isDark = when (settings.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK, AppThemeMode.AMOLED -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MisPodcastTheme(
                darkTheme = isDark,
                isAmoled = isAmoled,
                dynamicColor = settings.useDynamicColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScaffold(
                        intentPodcastId = targetPodcastId,
                        intentFeedUrl = targetFeedUrl,
                        onIntentHandled = {
                            targetPodcastId = null
                            targetFeedUrl = null
                        }
                    )
                }
            }
        }

        // 1. Crear canal de notificaciones locales de alta importancia
        NotificationHelper.createNotificationChannels(this)

        // 2. Solicitar permiso de notificaciones en Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isTesting = try {
                Class.forName("androidx.test.platform.app.InstrumentationRegistry")
                true
            } catch (_: Throwable) {
                false
            }
            if (!isTesting && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // 3. Programar comprobación periódica en segundo plano según preferencias del usuario
        lifecycleScope.launch {
            val prefManager = UserPreferencesManager.getInstance(applicationContext)
            val settings = prefManager.userSettingsFlow.first()
            PodcastCheckWorker.schedulePeriodicCheck(applicationContext, settings.episodeCheckFrequency)
            // Comprobación inmediata al iniciar la app
            PodcastCheckWorker.scheduleImmediateCheck(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val podcastId = intent?.getStringExtra("podcastId")
        val feedUrl = intent?.getStringExtra("feedUrl")
        if (!podcastId.isNullOrBlank() || !feedUrl.isNullOrBlank()) {
            targetPodcastId = podcastId
            targetFeedUrl = feedUrl
        }
    }
}