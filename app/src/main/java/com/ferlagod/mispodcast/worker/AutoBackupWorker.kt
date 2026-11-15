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

package com.ferlagod.mispodcast.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ferlagod.mispodcast.data.backup.DatabaseBackupManager
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import com.ferlagod.mispodcast.data.preferences.UserPreferencesManager
import com.ferlagod.mispodcast.util.rethrowIfCancellation
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Copia de seguridad automática de la base de datos cada 24 h en la carpeta elegida por el usuario
 * (árbol SAF con permiso persistente). Conserva solo las [MAX_BACKUPS] copias más recientes:
 * la más antigua se elimina después de escribir correctamente la nueva.
 */
class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        const val PERIODIC_WORK_NAME = "mis_podcast_periodic_auto_backup"
        const val MAX_BACKUPS = 3
        const val FILE_PREFIX = "mis_podcast_autobackup_"
        const val FILE_SUFFIX = ".zip"

        /** Programa la copia diaria. La primera se ejecuta en cuanto se cumplen las restricciones. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun isAutoBackupFile(name: String?): Boolean =
            name != null && name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)

        /**
         * Devuelve los nombres de copia automática que sobran para quedarse con las [keep] más recientes.
         * El nombre lleva la fecha en formato `yyyyMMdd_HHmmss`, así que el orden alfabético es cronológico.
         */
        fun selectBackupsToDelete(names: List<String>, keep: Int = MAX_BACKUPS): List<String> =
            names.filter { isAutoBackupFile(it) }.sortedDescending().drop(keep)
    }

    override suspend fun doWork(): Result {
        val prefs = UserPreferencesManager.getInstance(applicationContext)
        val settings = prefs.userSettingsFlow.first()
        if (!settings.autoBackupEnabled || settings.autoBackupFolderUri.isBlank()) {
            cancel(applicationContext)
            return Result.success()
        }

        return try {
            val folder = DocumentFile.fromTreeUri(applicationContext, Uri.parse(settings.autoBackupFolderUri))
            if (folder == null || !folder.canWrite()) {
                Log.w(TAG, "Carpeta de copias no accesible: ${settings.autoBackupFolderUri}")
                return Result.success()
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val target = folder.createFile("application/zip", "$FILE_PREFIX$stamp")
                ?: return Result.retry()

            val database = PodcastDatabase.getInstance(applicationContext)
            val result = DatabaseBackupManager.exportDatabaseZip(applicationContext, target.uri, database)
            if (result.isFailure) {
                Log.w(TAG, "Fallo al crear la copia automática: ${result.exceptionOrNull()?.message}")
                target.delete()
                return Result.retry()
            }

            // Solo con la nueva copia ya escrita se eliminan las antiguas que sobren
            val existing = folder.listFiles().filter { it.isFile && isAutoBackupFile(it.name) }
            val toDelete = selectBackupsToDelete(existing.mapNotNull { it.name }).toSet()
            existing.filter { it.name in toDelete }.forEach { old ->
                if (!old.delete()) Log.w(TAG, "No se pudo borrar la copia antigua ${old.name}")
            }

            prefs.setLastAutoBackupTimestamp(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Log.e(TAG, "Excepción en la copia automática: ${e.message}", e)
            Result.retry()
        }
    }
}
