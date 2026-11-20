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

package com.ferlagod.mispodcast.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAccessPolicyTests {

    @Test
    fun appCualquieraNoExploraLaBiblioteca() {
        assertFalse(LibraryAccessPolicy.canBrowseLibrary(false, false, "com.ejemplo.espia", true))
    }

    @Test
    fun appQueSeHacePasarPorAndroidAutoSinVerificarNoExplora() {
        assertFalse(
            LibraryAccessPolicy.canBrowseLibrary(false, false, "com.google.android.projection.gearhead", false)
        )
    }

    @Test
    fun controladoresLegitimosExploran() {
        assertTrue(LibraryAccessPolicy.canBrowseLibrary(true, false, "com.ferlagod.mispodcast", true))
        assertTrue(LibraryAccessPolicy.canBrowseLibrary(false, true, "android.car", false))
        assertTrue(
            LibraryAccessPolicy.canBrowseLibrary(false, false, "com.google.android.googlequicksearchbox", true)
        )
    }
}
