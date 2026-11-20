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

import com.ferlagod.mispodcast.sync.NextcloudSyncManager
import org.junit.Assert.assertEquals
import org.junit.Test

class NextcloudUrlTests {

    @Test
    fun usuarioSimple() {
        assertEquals(
            "https://nube.ejemplo.com/remote.php/dav/files/ana",
            NextcloudSyncManager.webDavBaseUrl("https://nube.ejemplo.com", " ana ")
        )
    }

    @Test
    fun servidorEnSubcarpeta() {
        assertEquals(
            "https://ejemplo.com/nextcloud/remote.php/dav/files/ana",
            NextcloudSyncManager.webDavBaseUrl("https://ejemplo.com/nextcloud", "ana")
        )
    }

    @Test
    fun usuarioConEspaciosYBarraSeCodificaComoUnSoloSegmento() {
        assertEquals(
            "https://nube.ejemplo.com/remote.php/dav/files/Ana%20L%C3%B3pez%2F..",
            NextcloudSyncManager.webDavBaseUrl("https://nube.ejemplo.com", "Ana López/..")
        )
    }
}
