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

package com.ferlagod.mispodcast.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ferlagod.mispodcast.data.local.dao.PodcastDao
import com.ferlagod.mispodcast.data.local.entities.EpisodeEntity
import com.ferlagod.mispodcast.data.local.entities.ListeningHistoryEntity
import com.ferlagod.mispodcast.data.local.entities.PlaybackStatsEntity
import com.ferlagod.mispodcast.data.local.entities.PodcastEntity
import com.ferlagod.mispodcast.data.local.entities.QueueItemEntity

/**
 * Base de datos principal de la aplicación construida con Room.
 *
 * Administra las tablas de podcasts, episodios, cola de reproducción, historial y estadísticas.
 * Integra migraciones acumulativas seguras para garantizar que las actualizaciones de esquema
 * preserven todos los datos locales del usuario sin pérdida alguna.
 */
@Database(
    entities = [
        PodcastEntity::class,
        EpisodeEntity::class,
        QueueItemEntity::class,
        ListeningHistoryEntity::class,
        PlaybackStatsEntity::class,
        com.ferlagod.mispodcast.data.local.entities.BookmarkEntity::class,
        com.ferlagod.mispodcast.data.local.entities.SyncStateEntity::class,
        com.ferlagod.mispodcast.data.local.entities.SyncStatsContributionEntity::class
    ],
    version = PodcastDatabase.DB_VERSION,
    exportSchema = true
)
abstract class PodcastDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao

    companion object {
        private const val TAG = "PodcastDatabase"
        const val DATABASE_NAME = "mis_podcast_database"

        @Volatile
        private var INSTANCE: PodcastDatabase? = null

        /** Versión actual del esquema (usada también en la anotación [Database]). */
        const val DB_VERSION = 14

        /**
         * Migración directa desde cualquier versión anterior a [DB_VERSION].
         *
         * [ensureAllTablesAndColumns] es idempotente y deja el esquema en su estado final sea cual sea
         * la versión de origen, por lo que basta una única migración por versión de partida.
         * Las correcciones de datos heredadas ([applyLegacyDataFixes]) se ejecutan una sola vez, al
         * actualizar, en lugar de en cada apertura de la base de datos.
         */
        private fun directMigrationFrom(fromVersion: Int) = object : Migration(fromVersion, DB_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
                applyLegacyDataFixes(db)
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = (1 until DB_VERSION).map { directMigrationFrom(it) }.toTypedArray()

        fun getInstance(context: Context): PodcastDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PodcastDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // NUNCA borrar datos de usuarios en actualizaciones normales.
                    // Solo en caso de downgrade (instalar un APK con versión inferior de BD).
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Corrige datos guardados por versiones antiguas de la app. Se ejecuta una vez al migrar.
         */
        internal fun applyLegacyDataFixes(db: SupportSQLiteDatabase) {
            // Episodios en curso que tuvieran erróneamente marcado isPlayed = 1,
            // para garantizar su reanudación exacta desde la posición guardada
            db.execSQL("""
                UPDATE episodes
                SET isPlayed = 0
                WHERE currentPositionMs > 0
                  AND (duration <= 0 OR currentPositionMs < duration - 5000)
            """)

            // Reconocer podcasts de carpetas locales importados desde AntennaPod
            db.execSQL("""
                UPDATE podcasts
                SET isLocalFolder = 1,
                    localFolderPath = REPLACE(feedUrl, 'antennapod_local:', ''),
                    category = 'Archivos Locales'
                WHERE feedUrl LIKE 'antennapod_local:%'
            """)

            // Restaurar podcasts RSS que tenían erróneamente isLocalFolder = 1 por URLs de caché de AntennaPod
            db.execSQL("""
                UPDATE podcasts
                SET isLocalFolder = 0,
                    localFolderPath = NULL
                WHERE isLocalFolder = 1
                  AND (feedUrl LIKE 'http://%' OR feedUrl LIKE 'https://%')
            """)
        }

        /**
         * Verifica y crea de forma idempotente todas las columnas, índices y tablas necesarias
         * en Room sin tocar ni alterar las filas existentes de los usuarios.
         */
        private fun ensureAllTablesAndColumns(db: SupportSQLiteDatabase) {
            android.util.Log.d(TAG, "Ejecutando migración segura a Room v$DB_VERSION...")

            // 1. Columnas adicionales de podcasts (Carpetas locales, lápidas, cabeceras HTTP de caché, Podcasting 2.0 y ajustes por podcast)
            addColumnIfNotExists(db, "podcasts", "fundingUrl", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "podcasts", "fundingText", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "podcasts", "valueForValueInfo", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "podcasts", "isLocalFolder", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "podcasts", "localFolderPath", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "podcasts", "unsubscribedAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "podcasts", "httpEtag", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "podcasts", "httpLastModified", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "podcasts", "customSpeed", "REAL DEFAULT NULL")
            addColumnIfNotExists(db, "podcasts", "skipIntroSeconds", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "podcasts", "skipEndingSeconds", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "podcasts", "autoDownloadEnabled", "INTEGER DEFAULT NULL")
            addColumnIfNotExists(db, "podcasts", "episodeSortOrder", "TEXT NOT NULL DEFAULT 'NEWEST_FIRST'")
            addColumnIfNotExists(db, "podcasts", "autoEnqueueRule", "TEXT NOT NULL DEFAULT 'DISABLED'")
            addColumnIfNotExists(db, "podcasts", "newEpisodeNotifications", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfNotExists(db, "podcasts", "autoDeleteRule", "TEXT NOT NULL DEFAULT 'DEFAULT'")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_podcasts_feedUrl` ON `podcasts` (`feedUrl`)")

            // 2. Columnas e índices de episodios (Archivos locales, tamaños, marca temporal de reproducción y Podcasting 2.0)
            addColumnIfNotExists(db, "episodes", "localFilePath", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "episodes", "fileSizeBytes", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "episodes", "chaptersUrl", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "episodes", "transcriptUrl", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "episodes", "fundingUrl", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "episodes", "valueForValueInfo", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "episodes", "persons", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "episodes", "positionUpdatedAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "episodes", "isDismissedFromNewReleases", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "episodes", "soundbites", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "episodes", "link", "TEXT DEFAULT NULL")

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_podcastId` ON `episodes` (`podcastId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_pubDate` ON `episodes` (`pubDate`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_isFavorite` ON `episodes` (`isFavorite`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_isPlayed` ON `episodes` (`isPlayed`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_isDismissedFromNewReleases` ON `episodes` (`isDismissedFromNewReleases`)")

            // 3. Tabla queue_items (Cola persistente)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `queue_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `episodeId` TEXT NOT NULL,
                    `positionIndex` INTEGER NOT NULL,
                    `isCurrentlyPlaying` INTEGER NOT NULL DEFAULT 0,
                    `addedAt` INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_queue_items_episodeId` ON `queue_items` (`episodeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_items_positionIndex` ON `queue_items` (`positionIndex`)")

            // Si existía la tabla anterior playback_queue, migrar sus elementos a queue_items
            try {
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='playback_queue'").use { cursor ->
                    if (cursor.count > 0) {
                        db.execSQL("""
                            INSERT OR IGNORE INTO `queue_items` (`id`, `episodeId`, `positionIndex`, `isCurrentlyPlaying`, `addedAt`)
                            SELECT `id`, `episodeId`, `positionIndex`, `isCurrentlyPlaying`, `addedAt` FROM `playback_queue`
                        """)
                        db.execSQL("DROP TABLE IF EXISTS `playback_queue`")
                        android.util.Log.d(TAG, "Migrados datos de playback_queue a queue_items con éxito.")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Aviso migrando playback_queue: ${e.message}")
            }

            // 4. Tabla listening_history (Historial de reproducciones)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `listening_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `episodeId` TEXT NOT NULL,
                    `podcastId` TEXT NOT NULL,
                    `listenedAt` INTEGER NOT NULL DEFAULT 0,
                    `durationListenedMs` INTEGER NOT NULL DEFAULT 0,
                    `completed` INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_history_episodeId` ON `listening_history` (`episodeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_history_listenedAt` ON `listening_history` (`listenedAt`)")
            // Identificador estable de cada sesión para la sincronización (v12): se rellena en las filas existentes
            addColumnIfNotExists(db, "listening_history", "syncId", "TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `listening_history` SET `syncId` = lower(hex(randomblob(16))) WHERE `syncId` = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_listening_history_syncId` ON `listening_history` (`syncId`)")

            // 5. Tabla playback_stats (Estadísticas)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `playback_stats` (
                    `podcastId` TEXT NOT NULL PRIMARY KEY,
                    `podcastTitle` TEXT NOT NULL,
                    `totalTimeListenedMs` INTEGER NOT NULL DEFAULT 0,
                    `episodesCompletedCount` INTEGER NOT NULL DEFAULT 0,
                    `lastListenedAt` INTEGER NOT NULL DEFAULT 0
                )
            """)

            // 6. Tabla bookmarks (Marcadores y clips)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `bookmarks` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `episodeId` TEXT NOT NULL,
                    `podcastId` TEXT NOT NULL DEFAULT '',
                    `podcastTitle` TEXT NOT NULL DEFAULT '',
                    `episodeTitle` TEXT NOT NULL DEFAULT '',
                    `positionMs` INTEGER NOT NULL,
                    `note` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_episodeId` ON `bookmarks` (`episodeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_podcastId` ON `bookmarks` (`podcastId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_createdAt` ON `bookmarks` (`createdAt`)")

            // 7. Estado interno de la sincronización Nextcloud (v12)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `sync_state` (
                    `stateKey` TEXT NOT NULL,
                    `stateValue` TEXT NOT NULL,
                    PRIMARY KEY(`stateKey`)
                )
            """)

            // 8. Aportación de cada dispositivo a las estadísticas (v12)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `sync_stats_contributions` (
                    `deviceId` TEXT NOT NULL,
                    `podcastId` TEXT NOT NULL,
                    `podcastTitle` TEXT NOT NULL,
                    `totalTimeListenedMs` INTEGER NOT NULL,
                    `episodesCompletedCount` INTEGER NOT NULL,
                    `lastListenedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`deviceId`, `podcastId`)
                )
            """)

            android.util.Log.d(TAG, "Migración segura completada con éxito.")
        }

        /**
         * Comprueba en SQLite si una columna ya existe antes de añadirla para evitar excepciones
         * de tipo "duplicate column name".
         */
        private fun addColumnIfNotExists(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDef: String
        ) {
            try {
                var exists = false
                db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1) {
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                                exists = true
                                break
                            }
                        }
                    }
                }
                if (!exists) {
                    db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDef")
                    android.util.Log.d(TAG, "Añadida columna $columnName a tabla $tableName")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error añadiendo columna $columnName a $tableName", e)
            }
        }
    }
}
