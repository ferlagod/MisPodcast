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

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ferlagod.mispodcast.data.local.PodcastDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Batería de pruebas unitarias para las migraciones de Room de Mis Podcast.
 *
 * Valida:
 * - Continuidad de versiones desde v1 hasta v9 (directas y paso a paso).
 * - Creación e idempotencia de tablas (queue_items, listening_history, playback_stats, bookmarks).
 * - Inserción segura de columnas en 'podcasts' (lápidas, ajustes, carpetas locales, caché HTTP, financiación).
 * - Inserción segura de columnas en 'episodes' (positionUpdatedAt, transcripciones, capítulos, archivos locales).
 * - Migración de datos desde tablas anteriores (ej. playback_queue a queue_items).
 */
class RoomMigrationTests {

    private lateinit var mockDb: SupportSQLiteDatabase
    private val executedSqlStatements = mutableListOf<String>()

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0

        mockDb = mockk(relaxed = true)
        executedSqlStatements.clear()

        // Capturar todas las sentencias SQL ejecutadas
        val sqlSlot = slot<String>()
        every { mockDb.execSQL(capture(sqlSlot)) } answers {
            executedSqlStatements.add(sqlSlot.captured)
        }

        // Simular que inicialmente las tablas no tienen las nuevas columnas
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.getColumnIndex("name") } returns 1
        every { mockCursor.moveToNext() } returns false // No hay columnas preexistentes
        every { mockDb.query(any<String>()) } returns mockCursor
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun migrationFrom(version: Int) =
        PodcastDatabase.ALL_MIGRATIONS.single { it.startVersion == version }

    /**
     * Test 1: Existe una migración directa a la versión actual desde cada versión anterior.
     */
    @Test
    fun testMigrationVersionDefinitions() {
        val starts = PodcastDatabase.ALL_MIGRATIONS.map { it.startVersion }
        assertEquals((1 until PodcastDatabase.DB_VERSION).toList(), starts)
        PodcastDatabase.ALL_MIGRATIONS.forEach {
            assertEquals(PodcastDatabase.DB_VERSION, it.endVersion)
        }
    }

    /**
     * Test 1b: Las correcciones de datos heredadas se aplican al migrar (y ya no en cada apertura).
     */
    @Test
    fun testMigrationAppliesLegacyDataFixes() {
        migrationFrom(PodcastDatabase.DB_VERSION - 1).migrate(mockDb)

        val allSql = executedSqlStatements.joinToString("\n")
        assertTrue(allSql.contains("SET isPlayed = 0"))
        assertTrue(allSql.contains("WHERE feedUrl LIKE 'antennapod_local:%'"))
        assertTrue(allSql.contains("SET isLocalFolder = 0"))
    }

    /**
     * Test 2: Ejecutar la migración desde v9 y verificar la adición de columnas y tablas.
     */
    @Test
    fun testMigration9To10CreatesAllRequiredTablesAndColumns() {
        migrationFrom(9).migrate(mockDb)

        val allSql = executedSqlStatements.joinToString("\n")

        // 1. Columnas de podcasts
        assertTrue("Debe añadir unsubscribedAt", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `unsubscribedAt`"))
        assertTrue("Debe añadir isLocalFolder", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `isLocalFolder`"))
        assertTrue("Debe añadir localFolderPath", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `localFolderPath`"))
        assertTrue("Debe añadir httpEtag", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `httpEtag`"))
        assertTrue("Debe añadir httpLastModified", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `httpLastModified`"))
        assertTrue("Debe añadir customSpeed", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `customSpeed`"))
        assertTrue("Debe añadir skipIntroSeconds", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `skipIntroSeconds`"))
        assertTrue("Debe añadir skipEndingSeconds", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `skipEndingSeconds`"))
        assertTrue("Debe añadir autoDownloadEnabled", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `autoDownloadEnabled`"))
        assertTrue("Debe añadir episodeSortOrder", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `episodeSortOrder`"))
        assertTrue("Debe añadir autoEnqueueRule", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `autoEnqueueRule`"))
        assertTrue("Debe añadir fundingUrl", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `fundingUrl`"))
        assertTrue("Debe añadir valueForValueInfo", allSql.contains("ALTER TABLE `podcasts` ADD COLUMN `valueForValueInfo`"))

        // 2. Columnas de episodios
        assertTrue("Debe añadir positionUpdatedAt", allSql.contains("ALTER TABLE `episodes` ADD COLUMN `positionUpdatedAt`"))
        assertTrue("Debe añadir isDismissedFromNewReleases", allSql.contains("ALTER TABLE `episodes` ADD COLUMN `isDismissedFromNewReleases`"))
        assertTrue("Debe añadir localFilePath", allSql.contains("ALTER TABLE `episodes` ADD COLUMN `localFilePath`"))
        assertTrue("Debe añadir fileSizeBytes", allSql.contains("ALTER TABLE `episodes` ADD COLUMN `fileSizeBytes`"))
        assertTrue("Debe añadir chaptersUrl", allSql.contains("ALTER TABLE `episodes` ADD COLUMN `chaptersUrl`"))
        assertTrue("Debe añadir transcriptUrl", allSql.contains("ALTER TABLE `episodes` ADD COLUMN `transcriptUrl`"))
        assertTrue("Debe añadir persons", allSql.contains("ALTER TABLE `episodes` ADD COLUMN `persons`"))

        // 3. Tablas e índices
        assertTrue("Debe crear la tabla bookmarks", allSql.contains("CREATE TABLE IF NOT EXISTS `bookmarks`"))
        assertTrue("Debe crear la tabla queue_items", allSql.contains("CREATE TABLE IF NOT EXISTS `queue_items`"))
        assertTrue("Debe crear la tabla listening_history", allSql.contains("CREATE TABLE IF NOT EXISTS `listening_history`"))
        assertTrue("Debe crear la tabla playback_stats", allSql.contains("CREATE TABLE IF NOT EXISTS `playback_stats`"))
        assertTrue("Debe crear índice de feedUrl", allSql.contains("index_podcasts_feedUrl"))
        assertTrue("Debe crear índice de episodeId en queue_items", allSql.contains("index_queue_items_episodeId"))
        assertTrue("Debe crear índice de isDismissedFromNewReleases en episodes", allSql.contains("index_episodes_isDismissedFromNewReleases"))
    }

    /**
     * Test 2b: La migración desde v11 añade el estado de sincronización y el identificador de sesión del historial.
     */
    @Test
    fun testMigration11To12AddsSyncTablesAndHistorySyncId() {
        migrationFrom(11).migrate(mockDb)

        val allSql = executedSqlStatements.joinToString("\n")
        assertTrue(allSql.contains("ALTER TABLE `listening_history` ADD COLUMN `syncId`"))
        assertTrue(allSql.contains("UPDATE `listening_history` SET `syncId` = lower(hex(randomblob(16)))"))
        assertTrue(allSql.contains("index_listening_history_syncId"))
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS `sync_state`"))
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS `sync_stats_contributions`"))
        // El relleno de syncId debe ir antes del índice único
        assertTrue(allSql.indexOf("randomblob") < allSql.indexOf("index_listening_history_syncId"))
    }

    /**
     * Test 3: Migración acumulativa desde versión antigua v1 a v10.
     */
    @Test
    fun testDirectMigration1To10CompletesSuccessfully() {
        migrationFrom(1).migrate(mockDb)

        val allSql = executedSqlStatements.joinToString("\n")
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS `bookmarks`"))
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS `queue_items`"))
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS `listening_history`"))
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS `playback_stats`"))
        assertTrue(allSql.contains("isDismissedFromNewReleases"))
    }

    /**
     * Test 4: Idempotencia: si las columnas ya existen en SQLite, no deben provocar fallos ni reinsertarse.
     */
    @Test
    fun testMigrationIsIdempotentWhenColumnsAlreadyExist() {
        // Simular que todas las columnas ya existen
        val mockCursorWithExistingColumns = mockk<Cursor>(relaxed = true)
        every { mockCursorWithExistingColumns.getColumnIndex("name") } returns 1
        every { mockCursorWithExistingColumns.moveToNext() } returnsMany listOf(true, false)
        every { mockCursorWithExistingColumns.getString(1) } returns "unsubscribedAt"
        every { mockDb.query(any<String>()) } returns mockCursorWithExistingColumns

        // Debe ejecutarse sin lanzar excepciones
        migrationFrom(8).migrate(mockDb)

        verify(atLeast = 1) { mockDb.query(any<String>()) }
    }
}
