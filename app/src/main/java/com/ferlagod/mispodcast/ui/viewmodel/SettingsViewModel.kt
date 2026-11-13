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

package com.ferlagod.mispodcast.ui.viewmodel

import com.ferlagod.mispodcast.util.asUiText
import com.ferlagod.mispodcast.R
import android.app.Application
import android.net.Uri
import com.ferlagod.mispodcast.util.UiText
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ferlagod.mispodcast.data.backup.DatabaseBackupManager
import com.ferlagod.mispodcast.data.backup.OpmlExporter
import com.ferlagod.mispodcast.data.backup.OpmlImporter
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.preferences.AppThemeMode
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.data.preferences.UserSettings
import com.ferlagod.mispodcast.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel para la pantalla de configuración y ajustes de la aplicación.
 *
 * Gestiona el cambio de tema visual, idioma de la interfaz, frecuencias de sincronización en segundo plano,
 * credenciales de Nextcloud WebDAV y copias de seguridad de la base de datos y archivos OPML.
 */
class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val preferencesManager: UserPreferencesManager = (application as com.ferlagod.mispodcast.MisPodcastApplication).container.preferencesManager,
    private val database: PodcastDatabase = (application as com.ferlagod.mispodcast.MisPodcastApplication).container.database,
    private val repository: PodcastRepository = (application as com.ferlagod.mispodcast.MisPodcastApplication).container.podcastRepository,
    private val nextcloudSyncManager: com.ferlagod.mispodcast.sync.NextcloudSyncManager = (application as com.ferlagod.mispodcast.MisPodcastApplication).container.nextcloudSyncManager
) : AndroidViewModel(application) {

    val userSettings: StateFlow<UserSettings> = preferencesManager.userSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    private val _isProcessingBackup = MutableStateFlow(false)
    val isProcessingBackup: StateFlow<Boolean> = _isProcessingBackup.asStateFlow()

    private val _backupMessage = MutableStateFlow<UiText?>(null)
    val backupMessage: StateFlow<UiText?> = _backupMessage.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<UiText?>(null)
    val connectionTestResult: StateFlow<UiText?> = _connectionTestResult.asStateFlow()

    private val _isConnectionSuccess = MutableStateFlow<Boolean?>(null)
    val isConnectionSuccess: StateFlow<Boolean?> = _isConnectionSuccess.asStateFlow()

    private val _isSyncingNextcloud = MutableStateFlow(false)
    val isSyncingNextcloud: StateFlow<Boolean> = _isSyncingNextcloud.asStateFlow()

    private val _nextcloudSyncMessage = MutableStateFlow<UiText?>(null)
    val nextcloudSyncMessage: StateFlow<UiText?> = _nextcloudSyncMessage.asStateFlow()

    fun setNextcloudConfig(serverUrl: String, username: String, appPassword: String) {
        viewModelScope.launch {
            preferencesManager.setNextcloudConfig(serverUrl, username, appPassword)
            _connectionTestResult.value = null
            _isConnectionSuccess.value = null
        }
    }

    fun testNextcloudConnection(serverUrl: String, username: String, appPassword: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = UiText.Res(R.string.msg_nextcloud_testing)
            _isConnectionSuccess.value = null

            val result = nextcloudSyncManager.testConnection(serverUrl, username, appPassword)
            result.onSuccess {
                _connectionTestResult.value = UiText.Res(R.string.msg_nextcloud_connected, username.trim())
                _isConnectionSuccess.value = true
                preferencesManager.setNextcloudConfig(serverUrl, username, appPassword)
            }.onFailure { err ->
                _connectionTestResult.value = err.asUiText(R.string.msg_nextcloud_connect_error)
                _isConnectionSuccess.value = false
            }
            _isTestingConnection.value = false
        }
    }

    fun syncNextcloudNow() {
        viewModelScope.launch {
            _isSyncingNextcloud.value = true
            _nextcloudSyncMessage.value = UiText.Res(R.string.msg_nextcloud_syncing)
            val result = nextcloudSyncManager.syncNow()
            result.onSuccess { msg ->
                _nextcloudSyncMessage.value = when (msg) {
                    com.ferlagod.mispodcast.sync.NextcloudSyncManager.MSG_ALREADY_RUNNING -> UiText.Res(R.string.msg_nextcloud_sync_running)
                    com.ferlagod.mispodcast.sync.NextcloudSyncManager.MSG_NOT_CONFIGURED -> UiText.Res(R.string.msg_nextcloud_not_configured)
                    else -> UiText.Res(R.string.msg_nextcloud_synced)
                }
            }.onFailure { err ->
                _nextcloudSyncMessage.value = err.asUiText(R.string.msg_nextcloud_sync_error)
            }
            _isSyncingNextcloud.value = false
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
        _isConnectionSuccess.value = null
    }

    fun setLanguageCode(languageCode: String) {
        viewModelScope.launch {
            preferencesManager.setLanguageCode(languageCode)
            com.ferlagod.mispodcast.util.LanguageManager.applyLanguage(languageCode)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setUseDynamicColor(enabled) }
    }

    fun setSkipForwardSeconds(seconds: Int) {
        viewModelScope.launch { preferencesManager.setSkipForwardSeconds(seconds) }
    }

    fun setSkipBackwardSeconds(seconds: Int) {
        viewModelScope.launch { preferencesManager.setSkipBackwardSeconds(seconds) }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch { preferencesManager.setDefaultPlaybackSpeed(speed) }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSkipSilence(enabled) }
    }

    fun setVoiceBoostEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setVoiceBoostEnabled(enabled) }
    }

    fun setVoiceBoostGainMb(gainMb: Int) {
        viewModelScope.launch { preferencesManager.setVoiceBoostGainMb(gainMb) }
    }

    fun setMonoAudioEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setMonoAudioEnabled(enabled) }
    }

    fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoDownloadNewEpisodes(enabled) }
    }

    fun setDefaultAutoEnqueueRule(rule: String) {
        viewModelScope.launch { preferencesManager.setDefaultAutoEnqueueRule(rule) }
    }

    fun setDownloadOnlyOnWifi(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDownloadOnlyOnWifi(enabled) }
    }

    fun setDownloadOnlyWhenCharging(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDownloadOnlyWhenCharging(enabled) }
    }

    fun setCachedEpisodesLimit(limit: Int) {
        viewModelScope.launch {
            preferencesManager.setCachedEpisodesLimit(limit)
            val database = com.ferlagod.mispodcast.data.local.PodcastDatabase.getInstance(getApplication())
            com.ferlagod.mispodcast.data.download.DownloadCleaner.enforceCacheLimit(getApplication(), database.podcastDao())
        }
    }

    fun setDeleteAfterPlayed(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDeleteAfterPlayed(enabled) }
    }

    fun setStripAudioTrackers(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setStripAudioTrackers(enabled) }
    }

    fun setShareSignature(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setShareSignature(enabled) }
    }

    fun setNewEpisodeNotifications(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setNewEpisodeNotifications(enabled) }
    }

    fun setEpisodeCheckFrequency(frequency: com.ferlagod.mispodcast.data.preferences.EpisodeCheckFrequency) {
        viewModelScope.launch {
            preferencesManager.setEpisodeCheckFrequency(frequency)
            com.ferlagod.mispodcast.worker.PodcastCheckWorker.schedulePeriodicCheck(getApplication(), frequency)
        }
    }

    fun triggerManualCheckNow() {
        com.ferlagod.mispodcast.worker.PodcastCheckWorker.scheduleImmediateCheck(getApplication())
    }

    /** Vuelve a mostrar los consejos contextuales de gestos (ver [com.ferlagod.mispodcast.ui.components.OneTimeHint]). */
    fun resetHints() {
        viewModelScope.launch { preferencesManager.resetHints() }
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    // ==========================================
    // EXPORTACIÓN E IMPORTACIÓN OPML
    // ==========================================

    fun exportOpml(targetUri: Uri) {
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupMessage.value = UiText.Res(R.string.msg_opml_generating)
            val podcasts = database.podcastDao().getSubscribedPodcastsFlow().firstOrNull() ?: emptyList()
            val result = OpmlExporter.exportOpml(getApplication(), targetUri, podcasts)
            result.onSuccess { count ->
                _backupMessage.value = UiText.Res(R.string.msg_opml_exported, count)
            }.onFailure { err ->
                _backupMessage.value = UiText.Res(R.string.msg_opml_export_error, err.asUiText())
            }
            _isProcessingBackup.value = false
        }
    }

    fun importOpml(sourceUri: Uri) {
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupMessage.value = UiText.Res(R.string.msg_opml_reading)
            val parseResult = OpmlImporter.importOpml(getApplication(), sourceUri)
            parseResult.onSuccess { feeds ->
                if (feeds.isEmpty()) {
                    _backupMessage.value = UiText.Res(R.string.msg_opml_empty)
                    _isProcessingBackup.value = false
                    return@launch
                }

                _backupMessage.value = UiText.Res(R.string.msg_opml_importing, feeds.size)
                var importedCount = 0
                for (feed in feeds) {
                    val isLocalFeed = feed.xmlUrl.startsWith("antennapod_local:") ||
                            feed.xmlUrl.startsWith("local:") ||
                            feed.xmlUrl.startsWith("local://") ||
                            feed.xmlUrl.startsWith("content:") ||
                            feed.xmlUrl.startsWith("file:") ||
                            feed.xmlUrl.startsWith("/")
                    if (isLocalFeed) {
                        val cleanPath = feed.xmlUrl
                            .removePrefix("antennapod_local:")
                            .removePrefix("local://")
                            .removePrefix("local:")
                        val localEntity = PodcastEntity(
                            id = feed.xmlUrl,
                            title = feed.title.ifBlank { getApplication<Application>().getString(R.string.local_podcast_default_title) },
                            author = "Carpeta Local",
                            description = "Podcast de carpeta local importado desde OPML",
                            feedUrl = feed.xmlUrl,
                            artworkUrl = "",
                            isSubscribed = true,
                            subscribedAt = System.currentTimeMillis(),
                            lastUpdated = System.currentTimeMillis(),
                            category = "Archivos Locales",
                            link = feed.htmlUrl.orEmpty(),
                            isLocalFolder = true,
                            localFolderPath = cleanPath
                        )
                        database.podcastDao().insertPodcast(localEntity)
                        importedCount++
                    } else {
                        val subResult = repository.subscribeToFeed(feed.xmlUrl)
                        if (subResult.isSuccess) {
                            importedCount++
                        }
                    }
                }
                _backupMessage.value = UiText.Res(R.string.msg_opml_imported, importedCount, feeds.size)
                if (importedCount > 0) {
                    nextcloudSyncManager.triggerSyncIfConfigured(debounceMs = 2500L)
                }
            }.onFailure { err ->
                _backupMessage.value = UiText.Res(R.string.msg_opml_read_error, err.asUiText())
            }
            _isProcessingBackup.value = false
        }
    }

    // ==========================================
    // EXPORTACIÓN E IMPORTACIÓN DE BASE DE DATOS
    // ==========================================

    fun exportDatabaseZip(targetUri: Uri) {
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupMessage.value = UiText.Res(R.string.msg_db_exporting)
            val result = DatabaseBackupManager.exportDatabaseZip(getApplication(), targetUri, database)
            result.onSuccess {
                _backupMessage.value = UiText.Res(R.string.msg_db_exported)
            }.onFailure { err ->
                _backupMessage.value = UiText.Res(R.string.msg_db_export_error, err.asUiText())
            }
            _isProcessingBackup.value = false
        }
    }

    fun importDatabaseZip(sourceUri: Uri) {
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupMessage.value = UiText.Res(R.string.msg_db_importing)
            val result = DatabaseBackupManager.importDatabase(getApplication(), sourceUri, database)
            result.onSuccess { outcome ->
                com.ferlagod.mispodcast.notification.NotificationHelper.cancelAllNotifications(getApplication())
                _backupMessage.value = outcome.message
                if (outcome.requiresRestart) {
                    // La base de datos abierta se cerró para sustituirla: reiniciar para cargar la restaurada
                    // (la sincronización se lanzará sola al arrancar de nuevo)
                    kotlinx.coroutines.delay(RESTART_DELAY_MS)
                    com.ferlagod.mispodcast.util.AppRestarter.restart(getApplication())
                    return@launch
                }
                nextcloudSyncManager.triggerSyncIfConfigured(debounceMs = 2500L)
            }.onFailure { err ->
                _backupMessage.value = UiText.Res(R.string.msg_db_import_error, err.asUiText())
            }
            _isProcessingBackup.value = false
        }
    }

    private companion object {
        /** Tiempo para leer «Copia restaurada» antes de que la app se reinicie. */
        const val RESTART_DELAY_MS = 1_800L
    }
}
