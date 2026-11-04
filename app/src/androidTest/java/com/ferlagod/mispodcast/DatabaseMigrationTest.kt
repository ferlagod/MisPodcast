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

package com.ferlagod.mispodcast

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas automatizadas de esquema y migración de base de datos con [MigrationTestHelper].
 * Valida que el esquema exportado coincida con las entidades de Room y que las migraciones
 * mantengan la integridad de los datos.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PodcastDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun testDatabaseSchemaVersion7Creation() {
        // Crear base de datos en versión 7 y verificar que el esquema es válido
        val db = helper.createDatabase(TEST_DB, 7)
        assertNotNull(db)
        db.close()
    }

    @Test
    fun testAllMigrationsArrayPreservesIntegrity() {
        // Migrar desde la versión anterior y validar contra el esquema exportado actual
        val db = helper.createDatabase(TEST_DB, PodcastDatabase.DB_VERSION - 1)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            PodcastDatabase.DB_VERSION,
            true,
            *PodcastDatabase.ALL_MIGRATIONS
        )
        assertNotNull(migratedDb)
        migratedDb.close()
    }

    @Test
    fun testMigrationFixesLegacyPlayedState() {
        val db = helper.createDatabase(TEST_DB, PodcastDatabase.DB_VERSION - 1)
        db.execSQL("INSERT INTO podcasts (id, title, author, description, feedUrl, artworkUrl, isSubscribed, subscribedAt, unsubscribedAt, lastUpdated, category, link, isLocalFolder, skipIntroSeconds, skipEndingSeconds, episodeSortOrder, autoEnqueueRule, newEpisodeNotifications, autoDeleteRule) VALUES ('p1', '', '', '', 'https://x/feed', '', 0, 0, 0, 0, '', '', 0, 0, 0, '', '', 0, '')")
        db.execSQL("INSERT INTO episodes (id, podcastId, title, description, audioUrl, imageUrl, pubDate, duration, currentPositionMs, positionUpdatedAt, isPlayed, isFavorite, isDownloaded, fileSizeBytes, isDismissedFromNewReleases) VALUES ('e1', 'p1', '', '', 'https://x/a.mp3', '', 0, 600000, 60000, 0, 1, 0, 0, 0, 0)")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            PodcastDatabase.DB_VERSION,
            true,
            *PodcastDatabase.ALL_MIGRATIONS
        )
        migratedDb.query("SELECT isPlayed FROM episodes WHERE id = 'e1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.close()
    }
}
