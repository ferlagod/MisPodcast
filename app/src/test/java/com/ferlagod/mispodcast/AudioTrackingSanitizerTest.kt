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

import com.ferlagod.mispodcast.util.AudioTrackingSanitizer.cleanAudioUrl
import com.ferlagod.mispodcast.util.AudioTrackingSanitizer.stableIdKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** URLs reales de feeds: cada caso rompía la reproducción con UnknownHostException antes de la corrección. */
class AudioTrackingSanitizerTest {

    @Test
    fun `cadena de cinco rastreadores con identificadores intermedios llega a megaphone`() {
        assertEquals(
            "https://traffic.megaphone.fm/EOM9648367273.mp3",
            cleanAudioUrl(
                "https://www.podtrac.com/pts/redirect.mp3/pdst.fm/e/chrt.fm/track/B1992E/" +
                    "pscrb.fm/rss/p/arttrk.com/p/GSDCV/traffic.megaphone.fm/EOM9648367273.mp3"
            )
        )
    }

    @Test
    fun `omny es el servidor del audio y no se toca salvo los utm`() {
        assertEquals(
            "https://traffic.omny.fm/d/clips/2446592a/9afe58f0/5ab96321/audio.mp3?in_playlist=fd25",
            cleanAudioUrl("https://traffic.omny.fm/d/clips/2446592a/9afe58f0/5ab96321/audio.mp3?utm_source=Podcast&in_playlist=fd25")
        )
    }

    @Test
    fun `op3 con url completa incrustada`() {
        assertEquals(
            "https://prestocast.com/play/ce0e5567.m4a",
            cleanAudioUrl("https://op3.dev/e/https://prestocast.com/play/ce0e5567.m4a")
        )
        assertEquals(
            "https://cdn.host.com/a.mp3",
            cleanAudioUrl("https://op3.dev/e,pg=1234-abcd/cdn.host.com/a.mp3")
        )
    }

    @Test
    fun `chartable con identificador y host sin esquema`() {
        assertEquals(
            "https://traffic.libsyn.com/show/ep1.mp3",
            cleanAudioUrl("https://chtbl.com/track/AB12CD/traffic.libsyn.com/show/ep1.mp3")
        )
    }

    @Test
    fun `esquema colapsado y http se respetan`() {
        assertEquals("http://media.example.org/ep.mp3", cleanAudioUrl("https://pdst.fm/e/http:/media.example.org/ep.mp3"))
    }

    @Test
    fun `si no hay un host claro tras el rastreador la url queda intacta`() {
        val raw = "https://dts.podtrac.com/redirect.mp3/abc123/episode.mp3"
        assertEquals(raw, cleanAudioUrl(raw))
        val onlyQuery = "https://pdst.fm?u=https://evil.example.com/x.mp3"
        assertEquals(onlyQuery, cleanAudioUrl(onlyQuery))
    }

    @Test
    fun `nunca produce un host sin punto`() {
        listOf(
            "https://chrt.fm/track/B1992E/pscrb.fm/rss/p/x.mp3",
            "https://www.podtrac.com/pts/redirect.mp3/pdst.fm/e/chrt.fm/track/ZZZ/",
            "https://arttrk.com/p/GSDCV/"
        ).forEach { raw ->
            val host = cleanAudioUrl(raw).substringAfter("://").substringBefore('/')
            assert('.' in host) { "host inválido «$host» para $raw" }
        }
    }

    @Test
    fun `la clave de id de episodios sin guid no cambia`() {
        // Valores calculados con la implementación original: si cambiaran, se duplicarían episodios
        assertEquals("https://cdn.example.com/a.mp3", stableIdKey("https://dts.podtrac.com/redirect.mp3/cdn.example.com/a.mp3"))
        assertEquals(
            "https://B1992E/pscrb.fm/rss/p/arttrk.com/p/GSDCV/traffic.megaphone.fm/EOM9648367273.mp3",
            stableIdKey(
                "https://www.podtrac.com/pts/redirect.mp3/pdst.fm/e/chrt.fm/track/B1992E/" +
                    "pscrb.fm/rss/p/arttrk.com/p/GSDCV/traffic.megaphone.fm/EOM9648367273.mp3"
            )
        )
    }
}
