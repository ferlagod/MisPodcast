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

import com.ferlagod.mispodcast.worker.AutoBackupWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupRotationTest {

    private fun name(stamp: String) = "mis_podcast_autobackup_$stamp.zip"

    @Test
    fun `con tres copias o menos no se borra nada`() {
        val names = listOf(name("20261101_030000"), name("20261102_030000"), name("20261103_030000"))
        assertTrue(AutoBackupWorker.selectBackupsToDelete(names).isEmpty())
    }

    @Test
    fun `con cuatro copias se borra solo la mas antigua`() {
        val names = listOf(
            name("20261103_030000"), name("20261101_030000"),
            name("20261104_030000"), name("20261102_030000")
        )
        assertEquals(listOf(name("20261101_030000")), AutoBackupWorker.selectBackupsToDelete(names))
    }

    @Test
    fun `los archivos ajenos a la copia automatica no se tocan`() {
        val names = listOf(
            "mis_podcast_database_20250101_0000.zip", "notas.txt",
            name("20261101_030000"), name("20261102_030000"), name("20261103_030000"), name("20261104_030000")
        )
        assertEquals(listOf(name("20261101_030000")), AutoBackupWorker.selectBackupsToDelete(names))
    }
}
