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

package com.ferlagod.mispodcast.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Trabajador en segundo plano de AndroidX WorkManager para sincronización periódica.
 *
 * Ejecuta el re-escaneo de carpetas locales de audio y la sincronización bidireccional
 * de suscripciones, puntos de escucha y cola con servidores Nextcloud mediante WebDAV.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        const val PERIODIC_WORK_NAME = "mis_podcast_periodic_sync"
        const val ONE_TIME_WORK_NAME = "mis_podcast_immediate_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun scheduleImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // 1. Re-escanear carpetas locales de audio para detectar nuevos episodios
            try {
                val db = com.ferlagod.mispodcast.data.local.PodcastDatabase.getInstance(applicationContext)
                com.ferlagod.mispodcast.data.local.LocalFolderPodcastImporter.rescanAllLocalPodcasts(
                    applicationContext,
                    db.podcastDao()
                )
            } catch (folderEx: Exception) {
                Log.w(TAG, "Aviso al re-escanear carpetas locales: ${folderEx.message}")
            }

            // 2. Ejecutar sincronización Nextcloud mediante WebDAV
            val syncManager = NextcloudSyncManager.getInstance(applicationContext)
            val result = syncManager.syncNow()

            if (result.isSuccess) {
                Log.d(TAG, "Sincronización Nextcloud ejecutada: ${result.getOrNull()}")
            } else {
                Log.w(TAG, "Aviso en sincronización Nextcloud: ${result.exceptionOrNull()?.message}")
            }

            // Siempre terminar limpiamente con Result.success() para evitar bucles de retry
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en SyncWorker: ${e.message}", e)
            Result.success()
        }
    }
}
