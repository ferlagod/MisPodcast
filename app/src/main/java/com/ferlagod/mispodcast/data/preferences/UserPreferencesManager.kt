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

package com.ferlagod.mispodcast.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import androidx.annotation.StringRes
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.local.model.SubscriptionSortOption

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * Modos de tema visual soportados por la aplicación.
 */
enum class AppThemeMode(@get:StringRes val titleRes: Int) {
    SYSTEM(R.string.settings_theme_system),
    LIGHT(R.string.settings_theme_light),
    DARK(R.string.settings_theme_dark),
    AMOLED(R.string.settings_theme_amoled)
}

/**
 * Opciones de idioma disponibles para la interfaz de la aplicación.
 */
enum class AppLanguageOption(
    val code: String,
    val displayName: String,
    val country: String,
    @get:StringRes val titleRes: Int
) {
    SYSTEM("system", "Predeterminado del sistema", "", R.string.settings_lang_system),
    SPANISH("es", "Español", "ES", R.string.settings_lang_spanish),
    ENGLISH("en", "English", "US", R.string.settings_lang_english),
    FRENCH("fr", "Français", "FR", R.string.settings_lang_french),
    GERMAN("de", "Deutsch", "DE", R.string.settings_lang_german),
    ITALIAN("it", "Italiano", "IT", R.string.settings_lang_italian),
    PORTUGUESE("pt", "Português", "PT", R.string.settings_lang_portuguese);

    companion object {
        fun fromCode(code: String): AppLanguageOption {
            val normalized = code.trim().lowercase()
            return entries.firstOrNull { it.code == normalized }
                ?: if (normalized == "auto") SYSTEM else SYSTEM
        }
    }
}

/**
 * Frecuencia de comprobación periódica en segundo plano para nuevos episodios.
 */
enum class EpisodeCheckFrequency(
    val label: String,
    val intervalHours: Long,
    @get:StringRes val titleRes: Int
) {
    MANUAL("Manualmente (Solo al abrir la app)", 0L, R.string.freq_manual),
    EVERY_1_HOUR("Cada hora", 1L, R.string.freq_1_hour),
    EVERY_2_HOURS("Cada 2 horas", 2L, R.string.freq_2_hours),
    EVERY_6_HOURS("Cada 6 horas", 6L, R.string.freq_6_hours),
    EVERY_12_HOURS("Cada 12 horas", 12L, R.string.freq_12_hours),
    EVERY_24_HOURS("Cada 24 horas", 24L, R.string.freq_24_hours)
}

/**
 * Modelo inmutable que agrupa la totalidad de ajustes configurados por el usuario.
 */
data class UserSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val languageCode: String = "system",
    val skipForwardSeconds: Int = 30,
    val skipBackwardSeconds: Int = 10,
    val defaultPlaybackSpeed: Float = 1.0f,
    val skipSilence: Boolean = false,
    val autoDownloadNewEpisodes: Boolean = false,
    val downloadOnlyOnWifi: Boolean = true,
    val downloadOnlyWhenCharging: Boolean = false,
    val cachedEpisodesLimit: Int = 10,
    val deleteAfterPlayed: Boolean = true,
    val newEpisodeNotifications: Boolean = true,
    val episodeCheckFrequency: EpisodeCheckFrequency = EpisodeCheckFrequency.EVERY_24_HOURS,
    val stripAudioTrackers: Boolean = true,
    val nextcloudServerUrl: String = "",
    val nextcloudUsername: String = "",
    val nextcloudAppPassword: String = "",
    val voiceBoostEnabled: Boolean = false,
    val voiceBoostGainMb: Int = 800,
    val monoAudioEnabled: Boolean = false,
    val defaultAutoEnqueueRule: String = "DISABLED",
    val useDynamicColor: Boolean = false,
    val subscriptionSortOption: SubscriptionSortOption = SubscriptionSortOption.RECENTLY_SUBSCRIBED,
    val lastSyncTimestamp: Long = 0L
)

/**
 * Gestor singleton de preferencias de usuario basado en AndroidX Jetpack DataStore.
 *
 * Expone un flujo reactivo [userSettingsFlow] con los valores persistidos y métodos
 * suspendidos para actualizar cada preferencia de forma atómica y segura.
 */
class UserPreferencesManager(private val context: Context) {

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        private val KEY_LANGUAGE_CODE = stringPreferencesKey("app_language_code")
        private val KEY_SKIP_FORWARD = intPreferencesKey("skip_forward_seconds")
        private val KEY_SKIP_BACKWARD = intPreferencesKey("skip_backward_seconds")
        private val KEY_DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
        private val KEY_SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        private val KEY_AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_new")
        private val KEY_DOWNLOAD_ONLY_WIFI = booleanPreferencesKey("download_only_wifi")
        private val KEY_DOWNLOAD_ONLY_CHARGING = booleanPreferencesKey("download_only_charging")
        private val KEY_CACHE_LIMIT = intPreferencesKey("cached_episodes_limit")
        private val KEY_DELETE_AFTER_PLAYED = booleanPreferencesKey("delete_after_played")
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("new_episode_notifications")
        private val KEY_CHECK_FREQUENCY = stringPreferencesKey("episode_check_frequency")
        private val KEY_STRIP_AUDIO_TRACKERS = booleanPreferencesKey("strip_audio_trackers")
        private val KEY_NEXTCLOUD_SERVER_URL = stringPreferencesKey("nextcloud_server_url")
        private val KEY_NEXTCLOUD_USERNAME = stringPreferencesKey("nextcloud_username")
        private val KEY_NEXTCLOUD_APP_PASSWORD = stringPreferencesKey("nextcloud_app_password")
        private val KEY_VOICE_BOOST_ENABLED = booleanPreferencesKey("voice_boost_enabled")
        private val KEY_VOICE_BOOST_GAIN_MB = intPreferencesKey("voice_boost_gain_mb")
        private val KEY_MONO_AUDIO_ENABLED = booleanPreferencesKey("mono_audio_enabled")
        private val KEY_DEFAULT_AUTO_ENQUEUE_RULE = stringPreferencesKey("default_auto_enqueue_rule")
        private val KEY_SUBSCRIPTION_SORT_OPTION = stringPreferencesKey("subscription_sort_option")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_timestamp")

        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        fun getInstance(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferencesManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val themeModeStr = preferences[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val themeMode = try {
            AppThemeMode.valueOf(themeModeStr)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }

        val checkFrequencyStr = preferences[KEY_CHECK_FREQUENCY] ?: EpisodeCheckFrequency.EVERY_24_HOURS.name
        val checkFrequency = try {
            EpisodeCheckFrequency.valueOf(checkFrequencyStr)
        } catch (e: Exception) {
            EpisodeCheckFrequency.EVERY_24_HOURS
        }

        val rawStoredPassword = preferences[KEY_NEXTCLOUD_APP_PASSWORD] ?: ""
        val decryptedPassword = com.ferlagod.mispodcast.util.KeyStoreCryptoHelper.decrypt(rawStoredPassword)

        UserSettings(
            themeMode = themeMode,
            languageCode = preferences[KEY_LANGUAGE_CODE] ?: "",
            skipForwardSeconds = preferences[KEY_SKIP_FORWARD] ?: 30,
            skipBackwardSeconds = preferences[KEY_SKIP_BACKWARD] ?: 10,
            defaultPlaybackSpeed = preferences[KEY_DEFAULT_SPEED] ?: 1.0f,
            skipSilence = preferences[KEY_SKIP_SILENCE] ?: false,
            autoDownloadNewEpisodes = preferences[KEY_AUTO_DOWNLOAD] ?: false,
            downloadOnlyOnWifi = preferences[KEY_DOWNLOAD_ONLY_WIFI] ?: true,
            downloadOnlyWhenCharging = preferences[KEY_DOWNLOAD_ONLY_CHARGING] ?: false,
            cachedEpisodesLimit = preferences[KEY_CACHE_LIMIT] ?: 10,
            deleteAfterPlayed = preferences[KEY_DELETE_AFTER_PLAYED] ?: true,
            newEpisodeNotifications = preferences[KEY_NOTIFICATIONS] ?: true,
            episodeCheckFrequency = checkFrequency,
            stripAudioTrackers = preferences[KEY_STRIP_AUDIO_TRACKERS] ?: true,
            nextcloudServerUrl = preferences[KEY_NEXTCLOUD_SERVER_URL] ?: "",
            nextcloudUsername = preferences[KEY_NEXTCLOUD_USERNAME] ?: "",
            nextcloudAppPassword = decryptedPassword,
            voiceBoostEnabled = preferences[KEY_VOICE_BOOST_ENABLED] ?: false,
            voiceBoostGainMb = preferences[KEY_VOICE_BOOST_GAIN_MB] ?: 800,
            monoAudioEnabled = preferences[KEY_MONO_AUDIO_ENABLED] ?: false,
            defaultAutoEnqueueRule = preferences[KEY_DEFAULT_AUTO_ENQUEUE_RULE] ?: "DISABLED",
            useDynamicColor = preferences[KEY_USE_DYNAMIC_COLOR] ?: false,
            subscriptionSortOption = SubscriptionSortOption.fromName(preferences[KEY_SUBSCRIPTION_SORT_OPTION]),
            lastSyncTimestamp = preferences[KEY_LAST_SYNC] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setLanguageCode(languageCode: String) {
        context.dataStore.edit { it[KEY_LANGUAGE_CODE] = languageCode }
    }

    suspend fun setSkipForwardSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_SKIP_FORWARD] = seconds }
    }

    suspend fun setSkipBackwardSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_SKIP_BACKWARD] = seconds }
    }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_DEFAULT_SPEED] = speed }
    }

    suspend fun setSkipSilence(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SKIP_SILENCE] = enabled }
    }

    suspend fun setAutoDownloadNewEpisodes(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_DOWNLOAD] = enabled }
    }

    suspend fun setDownloadOnlyOnWifi(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DOWNLOAD_ONLY_WIFI] = enabled }
    }

    suspend fun setDownloadOnlyWhenCharging(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DOWNLOAD_ONLY_CHARGING] = enabled }
    }

    suspend fun setCachedEpisodesLimit(limit: Int) {
        context.dataStore.edit { it[KEY_CACHE_LIMIT] = limit }
    }

    suspend fun setDeleteAfterPlayed(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DELETE_AFTER_PLAYED] = enabled }
    }

    suspend fun setNewEpisodeNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun setEpisodeCheckFrequency(frequency: EpisodeCheckFrequency) {
        context.dataStore.edit { it[KEY_CHECK_FREQUENCY] = frequency.name }
    }

    suspend fun setStripAudioTrackers(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STRIP_AUDIO_TRACKERS] = enabled }
    }

    suspend fun setVoiceBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VOICE_BOOST_ENABLED] = enabled }
    }

    suspend fun setVoiceBoostGainMb(gainMb: Int) {
        context.dataStore.edit { it[KEY_VOICE_BOOST_GAIN_MB] = gainMb }
    }

    suspend fun setMonoAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MONO_AUDIO_ENABLED] = enabled }
    }

    suspend fun setDefaultAutoEnqueueRule(rule: String) {
        context.dataStore.edit { it[KEY_DEFAULT_AUTO_ENQUEUE_RULE] = rule }
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC] = timestamp }
    }

    suspend fun setSubscriptionSortOption(option: SubscriptionSortOption) {
        context.dataStore.edit { it[KEY_SUBSCRIPTION_SORT_OPTION] = option.name }
    }

    suspend fun setNextcloudConfig(serverUrl: String, username: String, appPassword: String) {
        val encryptedPassword = com.ferlagod.mispodcast.util.KeyStoreCryptoHelper.encrypt(appPassword.trim())
        context.dataStore.edit {
            it[KEY_NEXTCLOUD_SERVER_URL] = serverUrl.trim().trimEnd('/')
            it[KEY_NEXTCLOUD_USERNAME] = username.trim()
            it[KEY_NEXTCLOUD_APP_PASSWORD] = encryptedPassword
        }
    }
}
