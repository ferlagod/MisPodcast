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

import com.ferlagod.mispodcast.data.download.DownloadCleaner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPathTests {

    private val files = "/data/user/0/com.ferlagod.mispodcast/files"
    private val roots = listOf("/storage/emulated/0/Android/data/com.ferlagod.mispodcast/files/Podcasts", "$files/podcasts")

    @Test
    fun descargasEnSusCarpetasSonGestionadas() {
        assertTrue(DownloadCleaner.isManagedPath("${roots[0]}/ep_1.mp3", roots, files))
        assertTrue(DownloadCleaner.isManagedPath("$files/podcasts/ep_1.mp3", roots, files))
        // Descargas antiguas guardadas directamente en filesDir
        assertTrue(DownloadCleaner.isManagedPath("$files/ep_1.mp3", roots, files))
    }

    @Test
    fun datosDeLaAppNoSeConsideranDescargas() {
        assertFalse(DownloadCleaner.isManagedPath("$files/datastore/user_prefs.preferences_pb", roots, files))
        assertFalse(DownloadCleaner.isManagedPath("/data/user/0/com.ferlagod.mispodcast/databases/mispodcast.db", roots, files))
        assertFalse(DownloadCleaner.isManagedPath(roots[0], roots, files))
    }
}
