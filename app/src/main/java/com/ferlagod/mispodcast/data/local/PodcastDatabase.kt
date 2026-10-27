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
        com.ferlagod.mispodcast.data.local.entities.BookmarkEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class PodcastDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao

    companion object {
        private const val TAG = "PodcastDatabase"
        const val DATABASE_NAME = "mis_podcast_database"

        @Volatile
        private var INSTANCE: PodcastDatabase? = null

        /**
         * Migraciones paso a paso y directas.
         * Todas delegan en [ensureAllTablesAndColumns] para garantizar de forma acumulativa
         * que cualquier usuario con cualquier versión previa conserve intactos todos sus datos.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_1_10 = object : Migration(1, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_1_9 = object : Migration(1, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_6_8 = object : Migration(6, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_1_8 = object : Migration(1, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_2_7 = object : Migration(2, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_3_7 = object : Migration(3, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_4_7 = object : Migration(4, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllTablesAndColumns(db)
            }
        }

        fun getInstance(context: Context): PodcastDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PodcastDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        MIGRATION_9_10,
                        MIGRATION_1_10,
                        MIGRATION_8_9,
                        MIGRATION_1_9,
                        MIGRATION_7_8,
                        MIGRATION_6_8,
                        MIGRATION_1_8,
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_1_7,
                        MIGRATION_2_7,
                        MIGRATION_3_7,
                        MIGRATION_4_7,
                        MIGRATION_5_7,
                        MIGRATION_1_6,
                        MIGRATION_2_6,
                        MIGRATION_3_6,
                        MIGRATION_4_6,
                        MIGRATION_1_5,
                        MIGRATION_2_5,
                        MIGRATION_3_5,
                        MIGRATION_1_4,
                        MIGRATION_2_4
                    )
                    // NUNCA borrar datos de usuarios en actualizaciones normales.
                    // Solo en caso de downgrade (instalar un APK con versión inferior de BD).
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                // Asegurar que los podcasts suscritos previamente ajusten su subscribedAt
                                // para no mostrar episodios antiguos en la sección de novedades
                                db.execSQL("""
                                    UPDATE podcasts 
                                    SET subscribedAt = (
                                        SELECT MAX(e.pubDate) 
                                        FROM episodes e 
                                        WHERE e.podcastId = podcasts.id
                                    )
                                    WHERE isSubscribed = 1 
                                      AND (SELECT MAX(e.pubDate) FROM episodes e WHERE e.podcastId = podcasts.id) > subscribedAt
                                """)

                                // Corregir episodios en curso que tuvieran erróneamente marcado isPlayed = 1
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
                            } catch (_: Exception) {}
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Verifica y crea de forma idempotente todas las columnas, índices y tablas necesarias
         * en Room sin tocar ni alterar las filas existentes de los usuarios.
         */
        private fun ensureAllTablesAndColumns(db: SupportSQLiteDatabase) {
            android.util.Log.d(TAG, "Ejecutando migración segura a Room v6...")

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
