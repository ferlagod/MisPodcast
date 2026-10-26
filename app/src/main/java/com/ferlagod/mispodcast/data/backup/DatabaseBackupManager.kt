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

package com.ferlagod.mispodcast.data.backup

import android.content.Context
import android.net.Uri
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Administrador de copias de seguridad integrales de la base de datos Room SQLite.
 *
 * Facilita la exportación e importación directa de la base de datos en formato ZIP o archivo `.db`
 * realizando un volcado seguro del registro WAL (`wal_checkpoint`) para preservar la integridad de datos.
 */
object DatabaseBackupManager {

    private const val DB_NAME = PodcastDatabase.DATABASE_NAME

    /**
     * Exporta la base de datos Room (.db, .db-wal y .db-shm) a un archivo ZIP.
     */
    suspend fun exportDatabaseZip(
        context: Context,
        targetUri: Uri,
        database: PodcastDatabase
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Asegurar que el log WAL esté volcado en el archivo principal .db
            try {
                val cursor = database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
                cursor.moveToFirst()
                cursor.close()
            } catch (e: Exception) {
                // Continuar si el checkpoint devuelve un estado transitorio
            }

            val dbFile = context.getDatabasePath(DB_NAME)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            if (!dbFile.exists()) {
                return@withContext Result.failure(IllegalStateException("No se encontró el archivo de base de datos local"))
            }

            val filesToZip = listOfNotNull(
                dbFile.takeIf { it.exists() },
                walFile.takeIf { it.exists() },
                shmFile.takeIf { it.exists() }
            )

            val outputStream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el destino de exportación"))

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                val buffer = ByteArray(8192)
                for (file in filesToZip) {
                    val entry = ZipEntry(file.name)
                    zipOut.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            zipOut.write(buffer, 0, bytesRead)
                        }
                    }
                    zipOut.closeEntry()
                }
                zipOut.flush()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Importa y restaura la base de datos desde un archivo (compatible con ZIP nativo de MisPodcast o .db / .zip de AntennaPod).
     */
    suspend fun importDatabase(
        context: Context,
        sourceUri: Uri,
        database: PodcastDatabase
    ): Result<String> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "db_restore_temp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        try {
            val tempFile = File(tempDir, "incoming_backup.tmp")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el archivo seleccionado"))

            val header = ByteArray(16)
            FileInputStream(tempFile).use { it.read(header) }
            val isZip = header.size >= 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()

            if (isZip) {
                // Descomprimir el contenido en tempDir
                val buffer = ByteArray(8192)
                var hasMainMisPodcastDb = false

                ZipInputStream(BufferedInputStream(FileInputStream(tempFile))).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val entryName = File(entry.name).name
                        if (!entry.isDirectory && entryName.isNotBlank()) {
                            if (entryName == DB_NAME) {
                                hasMainMisPodcastDb = true
                            }
                            val destFile = File(tempDir, entryName)
                            FileOutputStream(destFile).use { fos ->
                                var bytesRead: Int
                                while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                                    fos.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                // Caso A: Es una copia nativa de Room de MisPodcast
                if (hasMainMisPodcastDb) {
                    try {
                        database.close()
                    } catch (ignored: Exception) {}

                    val targetDir = context.getDatabasePath(DB_NAME).parentFile
                        ?: return@withContext Result.failure(IllegalStateException("No se encontró el directorio de bases de datos"))

                    File(targetDir, DB_NAME).delete()
                    File(targetDir, "$DB_NAME-wal").delete()
                    File(targetDir, "$DB_NAME-shm").delete()

                    tempDir.listFiles()?.filter { it.name.startsWith(DB_NAME) }?.forEach { file ->
                        file.copyTo(File(targetDir, file.name), overwrite = true)
                    }

                    return@withContext Result.success(
                        "¡Base de datos de MisPodcast restaurada con éxito! Reinicia la app para recargar los cambios."
                    )
                }

                // Caso B: El ZIP contiene una base de datos de AntennaPod (.db o similar)
                val candidateAntennaPodDb = tempDir.listFiles()?.firstOrNull { file ->
                    file.isFile && file.name != "incoming_backup.tmp" && AntennaPodDatabaseImporter.isAntennaPodDatabase(file)
                }

                if (candidateAntennaPodDb != null) {
                    return@withContext AntennaPodDatabaseImporter.importFromSqliteFile(context, candidateAntennaPodDb, database)
                }

                return@withContext Result.failure(
                    IllegalArgumentException("El archivo ZIP no contiene una base de datos válida de MisPodcast ni de AntennaPod")
                )
            } else {
                // Caso C: Archivo directo .db (sin comprimir)
                if (AntennaPodDatabaseImporter.isAntennaPodDatabase(tempFile)) {
                    return@withContext AntennaPodDatabaseImporter.importFromSqliteFile(context, tempFile, database)
                }

                return@withContext Result.failure(
                    IllegalArgumentException("El archivo seleccionado no es una copia de seguridad de MisPodcast (.zip) ni una base de datos de AntennaPod (.db)")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Mantiene compatibilidad hacia atrás delegando a importDatabase.
     */
    suspend fun importDatabaseZip(
        context: Context,
        sourceUri: Uri,
        database: PodcastDatabase
    ): Result<Unit> = withContext(Dispatchers.IO) {
        importDatabase(context, sourceUri, database).map { Unit }
    }
}
