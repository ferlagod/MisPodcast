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

package com.ferlagod.mispodcast.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.preferences.AppLanguageOption
import com.ferlagod.mispodcast.data.preferences.AppThemeMode
import com.ferlagod.mispodcast.data.preferences.EpisodeCheckFrequency
import com.ferlagod.mispodcast.ui.viewmodel.PodcastViewModel
import com.ferlagod.mispodcast.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de configuración y ajustes generales de Mis Podcast.
 *
 * Permite configurar el tema, idioma, sincronización con servidores Nextcloud WebDAV,
 * frecuencia de búsqueda de episodios y gestión de copias de seguridad de datos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    podcastViewModel: PodcastViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit = {}
) {
    val settings by settingsViewModel.userSettings.collectAsState()
    val isProcessingBackup by settingsViewModel.isProcessingBackup.collectAsState()
    val backupMessage by settingsViewModel.backupMessage.collectAsState()

    val isSyncing by podcastViewModel.isSyncing.collectAsState()
    val syncMessage by podcastViewModel.syncMessage.collectAsState()

    var serverUrlInput by remember(settings.nextcloudServerUrl) { mutableStateOf(settings.nextcloudServerUrl) }
    var usernameInput by remember(settings.nextcloudUsername) { mutableStateOf(settings.nextcloudUsername) }
    var appPasswordInput by remember(settings.nextcloudAppPassword) { mutableStateOf(settings.nextcloudAppPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

    val isTestingConnection by settingsViewModel.isTestingConnection.collectAsState()
    val connectionTestResult by settingsViewModel.connectionTestResult.collectAsState()
    val isConnectionSuccess by settingsViewModel.isConnectionSuccess.collectAsState()
    val isSyncingNextcloud by settingsViewModel.isSyncingNextcloud.collectAsState()
    val nextcloudSyncMessage by settingsViewModel.nextcloudSyncMessage.collectAsState()

    // Activity Result Launchers para SAF (Storage Access Framework)
    val exportOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri: Uri? ->
        uri?.let { settingsViewModel.exportOpml(it) }
    }

    val importOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { settingsViewModel.importOpml(it) }
    }

    val exportDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { settingsViewModel.exportDatabaseZip(it) }
    }

    val importDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { settingsViewModel.importDatabaseZip(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // MENSAJE DE ESTADO DE BACKUP O SINCRONIZACIÓN
            item {
                AnimatedVisibility(visible = backupMessage != null || isProcessingBackup) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isProcessingBackup) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = backupMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // SECCIÓN 1: TEMA VISUAL E IDIOMA
            item {
                SettingsSection(title = stringResource(R.string.settings_theme)) {
                    Column {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.settings_theme),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeOptionButton(
                                    label = stringResource(R.string.settings_theme_system),
                                    selected = settings.themeMode == AppThemeMode.SYSTEM,
                                    modifier = Modifier.weight(1f),
                                    onClick = { settingsViewModel.setThemeMode(AppThemeMode.SYSTEM) }
                                )
                                ThemeOptionButton(
                                    label = stringResource(R.string.settings_theme_light),
                                    selected = settings.themeMode == AppThemeMode.LIGHT,
                                    modifier = Modifier.weight(1f),
                                    onClick = { settingsViewModel.setThemeMode(AppThemeMode.LIGHT) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeOptionButton(
                                    label = stringResource(R.string.settings_theme_dark),
                                    selected = settings.themeMode == AppThemeMode.DARK,
                                    modifier = Modifier.weight(1f),
                                    onClick = { settingsViewModel.setThemeMode(AppThemeMode.DARK) }
                                )
                                ThemeOptionButton(
                                    label = stringResource(R.string.settings_theme_amoled),
                                    selected = settings.themeMode == AppThemeMode.AMOLED,
                                    modifier = Modifier.weight(1f),
                                    onClick = { settingsViewModel.setThemeMode(AppThemeMode.AMOLED) }
                                )
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            SettingsSwitchRow(
                                icon = Icons.Filled.AutoAwesome,
                                title = stringResource(R.string.settings_dynamic_color_title),
                                subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                                checked = settings.useDynamicColor,
                                onCheckedChange = { settingsViewModel.setUseDynamicColor(it) }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        SettingsLanguageSelectorRow(
                            currentLanguageCode = settings.languageCode,
                            onLanguageSelected = { selected ->
                                settingsViewModel.setLanguageCode(selected.code)
                            }
                        )
                    }
                }
            }

            // SECCIÓN 2: CONTROLES DE REPRODUCCIÓN
            item {
                SettingsSection(title = stringResource(R.string.settings_playback_controls)) {
                    Column {
                        // Salto adelante
                        SettingsDropdownRow(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.settings_skip_forward),
                            currentValue = "${settings.skipForwardSeconds}s",
                            options = listOf(10, 15, 30, 45, 60),
                            onSelect = { settingsViewModel.setSkipForwardSeconds(it) },
                            formatOption = { "${it}s" }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // Salto atrás
                        SettingsDropdownRow(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.settings_skip_backward),
                            currentValue = "${settings.skipBackwardSeconds}s",
                            options = listOf(5, 10, 15, 30),
                            onSelect = { settingsViewModel.setSkipBackwardSeconds(it) },
                            formatOption = { "${it}s" }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // Velocidad por defecto (fina: 0.5x a 3.0x)
                        SettingsDropdownRow(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.settings_default_speed),
                            currentValue = String.format(java.util.Locale.US, "%.1fx", settings.defaultPlaybackSpeed),
                            options = (5..30).map { it / 10f },
                            onSelect = { settingsViewModel.setDefaultPlaybackSpeed(it) },
                            formatOption = { String.format(java.util.Locale.US, "%.1fx", it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // Salto de silencios
                        SettingsSwitchRow(
                            icon = Icons.Filled.GraphicEq,
                            title = stringResource(R.string.settings_skip_silence),
                            subtitle = "Acelera o recorta las pausas silenciosas al reproducir",
                            checked = settings.skipSilence,
                            onCheckedChange = { settingsViewModel.setSkipSilence(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // Mejora de voz y volumen (LoudnessEnhancer)
                        SettingsSwitchRow(
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "Mejora de voz y volumen",
                            subtitle = "Amplifica el volumen y resalta voces con compresión dinámica",
                            checked = settings.voiceBoostEnabled,
                            onCheckedChange = { settingsViewModel.setVoiceBoostEnabled(it) }
                        )

                        if (settings.voiceBoostEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                            val gainLabels = mapOf(
                                com.ferlagod.mispodcast.playback.AudioEffectsManager.GAIN_SOFT_MB to "Suave (+4 dB)",
                                com.ferlagod.mispodcast.playback.AudioEffectsManager.GAIN_BALANCED_MB to "Medio (+8 dB, recomendado)",
                                com.ferlagod.mispodcast.playback.AudioEffectsManager.GAIN_STRONG_MB to "Fuerte (+12 dB)"
                            )
                            SettingsDropdownRow(
                                icon = Icons.Filled.GraphicEq,
                                title = "Nivel de amplificación de voz",
                                currentValue = gainLabels[settings.voiceBoostGainMb] ?: "Medio (+8 dB)",
                                options = listOf(
                                    com.ferlagod.mispodcast.playback.AudioEffectsManager.GAIN_SOFT_MB,
                                    com.ferlagod.mispodcast.playback.AudioEffectsManager.GAIN_BALANCED_MB,
                                    com.ferlagod.mispodcast.playback.AudioEffectsManager.GAIN_STRONG_MB
                                ),
                                onSelect = { settingsViewModel.setVoiceBoostGainMb(it) },
                                formatOption = { gainLabels[it] ?: "+$it mB" }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // Modo Mono (un solo auricular)
                        SettingsSwitchRow(
                            icon = Icons.Filled.Headphones,
                            title = "Modo mono (un solo auricular)",
                            subtitle = "Mezcla canales estéreo para no perder detalles con un solo auricular",
                            checked = settings.monoAudioEnabled,
                            onCheckedChange = { settingsViewModel.setMonoAudioEnabled(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        val autoEnqueueLabels = mapOf(
                            "DISABLED" to "Desactivado (manual)",
                            "TOP" to "Al principio de la cola (Siguiente / Up Next)",
                            "BOTTOM" to "Al final de la cola"
                        )
                        SettingsDropdownRow(
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            title = "Auto-encolado de nuevos episodios",
                            currentValue = autoEnqueueLabels[settings.defaultAutoEnqueueRule] ?: "Desactivado (manual)",
                            options = listOf("DISABLED", "TOP", "BOTTOM"),
                            onSelect = { settingsViewModel.setDefaultAutoEnqueueRule(it) },
                            formatOption = { autoEnqueueLabels[it] ?: it }
                        )
                    }
                }
            }

            // SECCIÓN 3: DESCARGAS Y ALMACENAMIENTO
            item {
                SettingsSection(title = stringResource(R.string.settings_downloads)) {
                    Column {
                        SettingsSwitchRow(
                            icon = Icons.Filled.Download,
                            title = stringResource(R.string.settings_auto_download),
                            subtitle = "Descargar automáticamente nuevos episodios detectados",
                            checked = settings.autoDownloadNewEpisodes,
                            onCheckedChange = { settingsViewModel.setAutoDownloadNewEpisodes(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        SettingsSwitchRow(
                            icon = Icons.Filled.Wifi,
                            title = "Solo con conexión Wi-Fi",
                            subtitle = "Evita el consumo de tarifa de datos móviles al descargar",
                            checked = settings.downloadOnlyOnWifi,
                            onCheckedChange = { settingsViewModel.setDownloadOnlyOnWifi(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        SettingsSwitchRow(
                            icon = Icons.Filled.BatteryChargingFull,
                            title = "Solo mientras se carga",
                            subtitle = "Pospone las descargas automáticas hasta conectar el cargador",
                            checked = settings.downloadOnlyWhenCharging,
                            onCheckedChange = { settingsViewModel.setDownloadOnlyWhenCharging(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        val episodesStr = stringResource(R.string.episodes)
                        SettingsDropdownRow(
                            icon = Icons.Filled.Storage,
                            title = stringResource(R.string.settings_cache_limit),
                            currentValue = if (settings.cachedEpisodesLimit >= 100) "Ilimitado" else "${settings.cachedEpisodesLimit} $episodesStr",
                            options = listOf(5, 10, 20, 50, 100),
                            onSelect = { settingsViewModel.setCachedEpisodesLimit(it) },
                            formatOption = { if (it >= 100) "Ilimitado" else "$it $episodesStr" }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        SettingsSwitchRow(
                            icon = Icons.Filled.Storage,
                            title = stringResource(R.string.settings_delete_after_played),
                            subtitle = "Borra el archivo descargado de la memoria tras completarlo",
                            checked = settings.deleteAfterPlayed,
                            onCheckedChange = { settingsViewModel.setDeleteAfterPlayed(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        SettingsSwitchRow(
                            icon = Icons.Filled.Security,
                            title = "Protección de privacidad (antirrastreo)",
                            subtitle = "Elimina redirecciones publicitarias y trackers de terceros (Podtrac, Chartable, etc.) en los audios",
                            checked = settings.stripAudioTrackers,
                            onCheckedChange = { settingsViewModel.setStripAudioTrackers(it) }
                        )
                    }
                }
            }

            // SECCIÓN 4: SINCRONIZACIÓN EN LA NUBE (NEXTCLOUD / WEBDAV)
            item {
                SettingsSection(title = stringResource(R.string.settings_nextcloud_sync)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Nextcloud / WebDAV",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                val lastSyncText = if (settings.lastSyncTimestamp > 0L) {
                                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                    "Última vez: ${sdf.format(Date(settings.lastSyncTimestamp))}"
                                } else {
                                    "Sin sincronización previa"
                                }
                                Text(
                                    text = lastSyncText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Sincroniza tus podcasts suscritos, cola de reproducción y progreso de episodios mediante WebDAV o GPodder Sync en tu nube privada Nextcloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // URL del servidor
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = {
                                serverUrlInput = it
                                settingsViewModel.setNextcloudConfig(it, usernameInput, appPasswordInput)
                            },
                            label = { Text(stringResource(R.string.settings_server_url)) },
                            placeholder = { Text("https://mi-nextcloud.com") },
                            leadingIcon = {
                                Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Usuario
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = {
                                usernameInput = it
                                settingsViewModel.setNextcloudConfig(serverUrlInput, it, appPasswordInput)
                            },
                            label = { Text(stringResource(R.string.settings_username)) },
                            placeholder = { Text("mi_usuario") },
                            leadingIcon = {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Contraseña de aplicación
                        OutlinedTextField(
                            value = appPasswordInput,
                            onValueChange = {
                                appPasswordInput = it
                                settingsViewModel.setNextcloudConfig(serverUrlInput, usernameInput, it)
                            },
                            label = { Text(stringResource(R.string.settings_app_password)) },
                            placeholder = { Text("Generada en Seguridad > Dispositivos") },
                            leadingIcon = {
                                Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Mensaje de resultado de prueba de conexión o sincronización
                        if (connectionTestResult != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isConnectionSuccess == true) {
                                    Color(0xFFE8F5E9)
                                } else if (isConnectionSuccess == false) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isConnectionSuccess == true) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Text(
                                        text = connectionTestResult.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isConnectionSuccess == true) {
                                            Color(0xFF1B5E20)
                                        } else if (isConnectionSuccess == false) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }

                        if (nextcloudSyncMessage != null && connectionTestResult == null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = nextcloudSyncMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botones de acción: "Probar conexión" y "Sincronizar ahora"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    settingsViewModel.testNextcloudConnection(
                                        serverUrlInput,
                                        usernameInput,
                                        appPasswordInput
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isTestingConnection && !isSyncingNextcloud && serverUrlInput.isNotBlank() && usernameInput.isNotBlank()
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Probando...")
                                } else {
                                    Text(stringResource(R.string.settings_test_connection), fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Button(
                                onClick = { settingsViewModel.syncNextcloudNow() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSyncingNextcloud && !isTestingConnection && serverUrlInput.isNotBlank() && usernameInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isSyncingNextcloud) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sincronizando...")
                                } else {
                                    Icon(Icons.Filled.CloudSync, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.settings_sync_now), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // SECCIÓN 5: COPIAS DE SEGURIDAD (ESTILO ANTENNAPOD)
            item {
                SettingsSection(title = stringResource(R.string.settings_backup)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Exporta tus suscripciones y el estado completo de reproducción para conservarlos o transferirlos a otro dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Respaldo OPML
                        Text(
                            text = "Suscripciones (Formato estándar OPML)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportOpmlLauncher.launch("mis_podcast_subs_$dateStr.opml")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.FileUpload, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_export_opml), style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    importOpmlLauncher.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "*/*"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_import_opml), style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Respaldo Base de Datos Completa
                        Text(
                            text = "Base de datos completa (.ZIP / .DB AntennaPod)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Exporta tu base de datos completa o importa copias de seguridad de MisPodcast (.zip) o bases de datos de AntennaPod (.db o .zip con suscripciones, episodios escuchados, favoritos, cola y estadísticas).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportDbLauncher.launch("mis_podcast_database_$dateStr.zip")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_export_db), style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    importDbLauncher.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/x-sqlite3",
                                            "application/vnd.sqlite3",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Filled.Storage, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_import_db), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // SECCIÓN 6: COMPROBAR NUEVOS EPISODIOS
            item {
                SettingsSection(title = stringResource(R.string.settings_check_frequency)) {
                    Column {
                        SettingsFrequencySelectorRow(
                            currentFrequency = settings.episodeCheckFrequency,
                            onFrequencySelected = { settingsViewModel.setEpisodeCheckFrequency(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Comprobar ahora",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Buscar episodios nuevos inmediatamente en segundo plano",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { settingsViewModel.triggerManualCheckNow() },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Comprobar")
                            }
                        }
                    }
                }
            }

            // SECCIÓN 7: NOTIFICACIONES
            item {
                SettingsSection(title = stringResource(R.string.settings_notifications)) {
                    SettingsSwitchRow(
                        icon = Icons.Filled.Notifications,
                        title = "Nuevos episodios",
                        subtitle = "Mostrar avisos cuando se publiquen episodios de tus suscripciones",
                        checked = settings.newEpisodeNotifications,
                        onCheckedChange = { settingsViewModel.setNewEpisodeNotifications(it) }
                    )
                }
            }

            // SECCIÓN 8: INFORMACIÓN Y ACERCA DE
            item {
                SettingsSection(title = stringResource(R.string.settings_about)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAbout() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Acerca de Mis Podcast",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Versión 1.0.0 • Licencia GNU GPL v3 • ferlagod",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = stringResource(R.string.menu_about),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun <T> SettingsDropdownRow(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<T>,
    onSelect: (T) -> Unit,
    formatOption: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(formatOption(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsFrequencySelectorRow(
    currentFrequency: EpisodeCheckFrequency,
    onFrequencySelected: (EpisodeCheckFrequency) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CloudSync,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_check_frequency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(currentFrequency.titleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_check_frequency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    EpisodeCheckFrequency.entries.forEach { frequency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onFrequencySelected(frequency)
                                    showDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = (frequency == currentFrequency),
                                onClick = {
                                    onFrequencySelected(frequency)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(frequency.titleRes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (frequency == currentFrequency) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun SettingsLanguageSelectorRow(
    currentLanguageCode: String,
    onLanguageSelected: (AppLanguageOption) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentOption = AppLanguageOption.fromCode(currentLanguageCode)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            val currentOptionName = stringResource(currentOption.titleRes)
            val subtitleText = if (currentOption == AppLanguageOption.SYSTEM) {
                val effective = com.ferlagod.mispodcast.util.LanguageManager.getEffectiveLanguageCode(currentLanguageCode)
                "$currentOptionName (${effective.uppercase()})"
            } else {
                "$currentOptionName (${currentOption.code.uppercase()})"
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_language_app),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    AppLanguageOption.entries.forEach { option ->
                        val isSelected = (option == currentOption)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLanguageSelected(option)
                                    showDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onLanguageSelected(option)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(option.titleRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (option != AppLanguageOption.SYSTEM && option.country.isNotBlank()) {
                                    Text(
                                        text = "Catálogo de podcasts: ${option.country}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}


