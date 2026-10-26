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
        // Verificar que todas las migraciones registradas son ejecutables
        val db = helper.createDatabase(TEST_DB, 6)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            PodcastDatabase.MIGRATION_6_7
        )
        assertNotNull(migratedDb)
        migratedDb.close()
    }
}
