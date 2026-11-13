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

import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.util.rethrowIfCancellation
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.ferlagod.mispodcast.util.LocalizedException
import com.ferlagod.mispodcast.util.UiText
import java.io.IOException
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
                e.rethrowIfCancellation()
                // Continuar si el checkpoint devuelve un estado transitorio
            }

            val dbFile = context.getDatabasePath(DB_NAME)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            if (!dbFile.exists()) {
                return@withContext Result.failure(LocalizedException(R.string.error_db_not_found))
            }

            val filesToZip = listOfNotNull(
                dbFile.takeIf { it.exists() },
                walFile.takeIf { it.exists() },
                shmFile.takeIf { it.exists() }
            )

            val outputStream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext Result.failure(LocalizedException(R.string.error_open_destination))

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
            e.rethrowIfCancellation()
            Result.failure(e)
        }
    }

    /**
     * Resultado de una importación correcta.
     *
     * @property requiresRestart `true` si se ha sustituido la base de datos de la app: la instancia
     *   abierta queda cerrada y hay que reiniciar el proceso para cargar la restaurada.
     */
    data class ImportOutcome(val message: UiText, val requiresRestart: Boolean)

    /**
     * Importa y restaura la base de datos desde un archivo (compatible con ZIP nativo de MisPodcast o .db / .zip de AntennaPod).
     */
    suspend fun importDatabase(
        context: Context,
        sourceUri: Uri,
        database: PodcastDatabase
    ): Result<ImportOutcome> = withContext(Dispatchers.IO) {
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
            } ?: return@withContext Result.failure(LocalizedException(R.string.error_open_file))

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
                    val incomingDb = File(tempDir, DB_NAME)
                    // Validar ANTES de tocar la base de datos actual: una copia dañada o de una versión
                    // más nueva de la app (que Room trataría como downgrade y vaciaría) se rechaza
                    validateMisPodcastBackup(incomingDb)

                    val targetDir = context.getDatabasePath(DB_NAME).parentFile
                        ?: return@withContext Result.failure(LocalizedException(R.string.error_db_not_found))

                    try {
                        database.close()
                    } catch (e: Exception) { e.rethrowIfCancellation() }

                    replaceDatabaseFiles(targetDir, incomingDb)
                    return@withContext Result.success(ImportOutcome(UiText.Res(R.string.msg_db_restored), requiresRestart = true))
                }

                // Caso B: El ZIP contiene una base de datos de AntennaPod (.db o similar)
                val candidateAntennaPodDb = tempDir.listFiles()?.firstOrNull { file ->
                    file.isFile && file.name != "incoming_backup.tmp" && AntennaPodDatabaseImporter.isAntennaPodDatabase(file)
                }

                if (candidateAntennaPodDb != null) {
                    return@withContext AntennaPodDatabaseImporter.importFromSqliteFile(context, candidateAntennaPodDb, database)
                        .map { ImportOutcome(it, requiresRestart = false) }
                }

                return@withContext Result.failure(LocalizedException(R.string.error_backup_invalid_zip))
            } else {
                // Caso C: Archivo directo .db (sin comprimir)
                if (AntennaPodDatabaseImporter.isAntennaPodDatabase(tempFile)) {
                    return@withContext AntennaPodDatabaseImporter.importFromSqliteFile(context, tempFile, database)
                        .map { ImportOutcome(it, requiresRestart = false) }
                }

                return@withContext Result.failure(LocalizedException(R.string.error_backup_invalid_file))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Comprueba que [file] es una base de datos de MisPodcast íntegra y restaurable con esta versión.
     * Además vuelca su WAL al archivo principal, de modo que basta con copiar ese único archivo.
     */
    private fun validateMisPodcastBackup(file: File) {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) {
            throw LocalizedException(UiText.Res(R.string.error_backup_corrupt), "No se puede abrir la copia", e)
        }
        db.use {
            val version = it.version
            if (version > PodcastDatabase.DB_VERSION) {
                throw LocalizedException(R.string.error_backup_newer_version)
            }
            if (version <= 0) throw LocalizedException(R.string.error_backup_corrupt)

            val intact = it.rawQuery("PRAGMA integrity_check", null).use { c ->
                c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
            }
            val hasCoreTables = it.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('podcasts', 'episodes')",
                null
            ).use { c -> c.moveToFirst() && c.getInt(0) == 2 }
            if (!intact || !hasCoreTables) throw LocalizedException(R.string.error_backup_corrupt)

            it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() }
        }
    }

    /**
     * Sustituye la base de datos de la app por [incomingDb] conservando la actual como `.bak`
     * hasta que la copia termina: si algo falla a medias, se recupera la original intacta.
     */
    private fun replaceDatabaseFiles(targetDir: File, incomingDb: File) {
        val current = DB_SUFFIXES.map { File(targetDir, DB_NAME + it) }
        val saved = current.map { File(it.path + ".bak") }
        saved.forEach { it.delete() }
        try {
            current.forEachIndexed { i, file ->
                if (file.exists() && !file.renameTo(saved[i])) throw IOException("No se pudo apartar ${file.name}")
            }
            incomingDb.copyTo(File(targetDir, DB_NAME), overwrite = true)
        } catch (e: Exception) {
            current.forEach { it.delete() }
            saved.forEachIndexed { i, backup -> if (backup.exists()) backup.renameTo(current[i]) }
            throw LocalizedException(UiText.Res(R.string.error_backup_restore_failed), e.message, e)
        }
        saved.forEach { it.delete() }
    }

    private val DB_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

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
