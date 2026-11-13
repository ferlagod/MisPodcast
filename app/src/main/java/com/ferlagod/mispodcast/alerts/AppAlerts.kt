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

package com.ferlagod.mispodcast.alerts

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Origen de un aviso; determina su icono y con qué otros avisos se agrupa. */
enum class AlertKind { DOWNLOAD, SYNC, FEEDS }

/** Acción que acompaña a un aviso. La resuelve la interfaz, que tiene acceso a los ViewModels y la navegación. */
sealed interface AlertAction {
    data class RetryDownloads(val episodeIds: List<String>) : AlertAction
    data object RetrySync : AlertAction
    data object OpenSyncSettings : AlertAction
    data object RetryFeeds : AlertAction
}

data class AppAlert(
    val id: Long,
    val kind: AlertKind,
    val message: UiText,
    val action: AlertAction?
)

/** Motivo por el que una descarga se ha dado por perdida. */
enum class DownloadFailureReason { NETWORK, NO_SPACE, UNAVAILABLE }

/** Motivo por el que ha fallado una sincronización con Nextcloud. */
enum class SyncFailureReason {
    /** Sin red o servidor inaccesible: suele resolverse solo. */
    NETWORK,
    /** El servidor respondió con un error o hubo conflictos repetidos: puede ser pasajero. */
    SERVER,
    /** Credenciales rechazadas (HTTP 401/403): no se arregla sin intervención del usuario. */
    AUTH,
    /** El archivo remoto no se puede interpretar: no se arregla sin intervención del usuario. */
    CORRUPT,
    /** URL sin HTTPS: no se arregla sin intervención del usuario. */
    INSECURE;

    val needsUserAction: Boolean get() = this == AUTH || this == CORRUPT || this == INSECURE
}

/**
 * Centro de avisos de la aplicación para fallos que ocurren lejos de la pantalla que los provoca
 * (descargas en WorkManager, sincronización automática, actualización de feeds…).
 *
 * Está pensado para informar sin molestar:
 * - Los avisos se muestran de uno en uno en el Snackbar global y nunca bloquean la interfaz.
 * - Un aviso nuevo del mismo tipo sustituye al pendiente en vez de apilarse (tres descargas
 *   fallidas producen un único «No se pudieron descargar 3 episodios»).
 * - La sincronización automática solo avisa de problemas que el usuario debe resolver, o de fallos
 *   pasajeros cuando se repiten; nunca por estar sin conexión.
 * - Con la app en primer plano, las descargas fallidas se avisan aquí en lugar de con una
 *   notificación del sistema, para no duplicar el mensaje.
 */
object AppAlerts {

    private val nextId = AtomicLong(1)

    /** Avisos pendientes, en orden de llegada. Solo se modifica bajo el bloqueo de este objeto. */
    private var pending: List<AppAlert> = emptyList()

    private val _current = MutableStateFlow<AppAlert?>(null)

    /** Aviso que debe mostrarse ahora (el más antiguo pendiente), o `null` si no hay ninguno. */
    val current: StateFlow<AppAlert?> = _current.asStateFlow()

    /** Último problema de sincronización sin resolver, para mostrarlo discretamente en Ajustes. */
    private val _syncIssue = MutableStateFlow<SyncFailureReason?>(null)
    val syncIssue: StateFlow<SyncFailureReason?> = _syncIssue.asStateFlow()

    private lateinit var appContext: Context
    private var startedActivities = 0

    /** `true` mientras alguna Activity de la app está visible. */
    @Volatile
    var isAppVisible: Boolean = false
        private set

    fun init(application: Application) {
        appContext = application.applicationContext
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                isAppVisible = true
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                isAppVisible = startedActivities > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** La interfaz llama a esto cuando el aviso deja de mostrarse (descartado, caducado o usada su acción). */
    @Synchronized
    fun dismiss(alertId: Long) {
        val removed = pending.firstOrNull { it.id == alertId } ?: return
        if (removed.kind == AlertKind.DOWNLOAD) downloadFailures.clear()
        updatePending { list -> list.filterNot { it.id == alertId } }
    }

    // ==========================================
    // DESCARGAS
    // ==========================================

    private data class DownloadFailure(val episodeId: String, val title: String, val reason: DownloadFailureReason)

    private val downloadFailures = LinkedHashMap<String, DownloadFailure>()

    /**
     * Registra una descarga fallida definitivamente. Devuelve `true` si se mostrará dentro de la app,
     * en cuyo caso quien llama no debe emitir además una notificación del sistema.
     */
    @Synchronized
    fun reportDownloadFailure(episodeId: String, title: String, reason: DownloadFailureReason): Boolean {
        if (!isAppVisible) return false
        downloadFailures[episodeId] = DownloadFailure(episodeId, title, reason)
        val failures = downloadFailures.values.toList()

        val retryable = failures.filter { it.reason != DownloadFailureReason.UNAVAILABLE }.map { it.episodeId }
        val message = if (failures.size == 1) {
            val failure = failures.single()
            when (failure.reason) {
                DownloadFailureReason.NETWORK -> UiText.Res(R.string.alert_download_failed_network, failure.title)
                DownloadFailureReason.NO_SPACE -> UiText.Res(R.string.alert_download_failed_space, failure.title)
                DownloadFailureReason.UNAVAILABLE -> UiText.Res(R.string.alert_download_failed_unavailable, failure.title)
            }
        } else if (failures.all { it.reason == DownloadFailureReason.NO_SPACE }) {
            UiText.Plural(R.plurals.alert_downloads_failed_space, failures.size, failures.size)
        } else {
            UiText.Plural(R.plurals.alert_downloads_failed, failures.size, failures.size)
        }
        // Reintentar sin espacio libre volvería a fallar: no se ofrece
        val canRetry = retryable.isNotEmpty() && failures.any { it.reason == DownloadFailureReason.NETWORK }
        post(AlertKind.DOWNLOAD, message, if (canRetry) AlertAction.RetryDownloads(retryable) else null)
        return true
    }

    // ==========================================
    // SINCRONIZACIÓN NEXTCLOUD
    // ==========================================

    private var consecutiveSyncFailures = 0
    private var alertedSyncReason: SyncFailureReason? = null

    /** Número de fallos pasajeros seguidos de la sincronización automática antes de avisar. */
    private const val TRANSIENT_SYNC_FAILURES_BEFORE_ALERT = 3

    @Synchronized
    fun onSyncSucceeded() {
        consecutiveSyncFailures = 0
        alertedSyncReason = null
        _syncIssue.value = null
        updatePending { list -> list.filterNot { it.kind == AlertKind.SYNC } }
    }

    /**
     * Registra un fallo de sincronización.
     *
     * @param automatic `false` si lo pidió el usuario desde Ajustes, donde ya ve el resultado:
     *   entonces solo se actualiza el estado, sin Snackbar.
     */
    @Synchronized
    fun onSyncFailed(reason: SyncFailureReason, automatic: Boolean) {
        // Sin conexión no hay nada que contar: se sincronizará cuando vuelva la red
        if (reason == SyncFailureReason.NETWORK && !isOnline()) return

        consecutiveSyncFailures++
        _syncIssue.value = reason
        if (!automatic || alertedSyncReason == reason) return

        val shouldAlert = reason.needsUserAction ||
            consecutiveSyncFailures >= TRANSIENT_SYNC_FAILURES_BEFORE_ALERT
        if (!shouldAlert) return

        alertedSyncReason = reason
        val message = UiText.Res(
            when (reason) {
                SyncFailureReason.AUTH -> R.string.alert_sync_auth
                SyncFailureReason.CORRUPT -> R.string.alert_sync_corrupt
                SyncFailureReason.INSECURE -> R.string.alert_sync_insecure
                SyncFailureReason.NETWORK, SyncFailureReason.SERVER -> R.string.alert_sync_failing
            }
        )
        val action = if (reason.needsUserAction) AlertAction.OpenSyncSettings else AlertAction.RetrySync
        post(AlertKind.SYNC, message, action)
    }

    // ==========================================
    // ACTUALIZACIÓN DE FEEDS
    // ==========================================

    private var lastFeedFailureKey: Set<String> = emptySet()
    private var lastFeedFailureAt = 0L

    /** No se repite el mismo aviso de feeds caídos en cada actualización manual durante este tiempo. */
    private const val FEED_ALERT_COOLDOWN_MS = 6L * 60 * 60 * 1000

    /**
     * Resume el resultado de una actualización manual de feeds.
     *
     * @param failedTitles títulos de los podcasts cuyo feed no se pudo actualizar.
     * @param attempted número de feeds remotos que se intentaron actualizar.
     */
    @Synchronized
    fun onFeedRefreshFinished(failedTitles: List<String>, attempted: Int) {
        if (failedTitles.isEmpty()) {
            lastFeedFailureKey = emptySet()
            updatePending { list -> list.filterNot { it.kind == AlertKind.FEEDS } }
            return
        }

        val offline = !isOnline()
        val allFailed = failedTitles.size >= attempted
        // Un feed caído que sigue caído no merece un aviso en cada «tirar para actualizar»
        if (!offline && !allFailed) {
            val key = failedTitles.toSet()
            val now = SystemClock.elapsedRealtime()
            if (key == lastFeedFailureKey && now - lastFeedFailureAt < FEED_ALERT_COOLDOWN_MS) return
            lastFeedFailureKey = key
            lastFeedFailureAt = now
        }

        val message = when {
            offline -> UiText.Res(R.string.alert_feeds_offline)
            allFailed && attempted > 1 -> UiText.Res(R.string.alert_feeds_all_failed)
            failedTitles.size == 1 -> UiText.Res(R.string.alert_feed_failed, failedTitles.single())
            else -> UiText.Plural(R.plurals.alert_feeds_failed, failedTitles.size, failedTitles.size)
        }
        post(AlertKind.FEEDS, message, AlertAction.RetryFeeds)
    }

    // ==========================================
    // INTERNO
    // ==========================================

    /** Debe llamarse con el bloqueo de este objeto tomado. */
    private fun updatePending(transform: (List<AppAlert>) -> List<AppAlert>) {
        pending = transform(pending)
        _current.value = pending.firstOrNull()
    }

    /** Añade un aviso o sustituye, en su misma posición, al pendiente del mismo tipo. */
    private fun post(kind: AlertKind, message: UiText, action: AlertAction?) {
        val alert = AppAlert(nextId.getAndIncrement(), kind, message, action)
        updatePending { list ->
            val index = list.indexOfFirst { it.kind == kind }
            if (index >= 0) list.toMutableList().apply { this[index] = alert } else list + alert
        }
    }

    private fun isOnline(): Boolean {
        if (!::appContext.isInitialized) return true
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
