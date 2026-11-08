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

import com.ferlagod.mispodcast.data.remote.parser.FeedDateParser
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedDateParserTest {

    // 3 nov 2025 10:00:00 UTC
    private val expected = 1762164000000L

    private fun assertParses(input: String, millis: Long = expected) =
        assertEquals(input, millis, FeedDateParser.parseToMillis(input))

    @Test
    fun rfc822Variants() {
        assertParses("Mon, 03 Nov 2025 10:00:00 GMT")
        assertParses("Mon, 3 Nov 2025 10:00:00 +0000")
        assertParses("Mon, 3 Nov 2025 11:00:00 +0100")
        assertParses("Mon, 03 Nov 2025 05:00:00 EST")
        assertParses("Mon, 03 Nov 2025 10:00:00 UT")
        assertParses("Mon, 03 Nov 2025 10:00:00 Z")
        assertParses("Mon, 03 Nov 2025 10:00 GMT")
        assertParses("03 Nov 2025 10:00:00 GMT")
        assertParses("  Mon,  03   Nov 2025 10:00:00 GMT ")
    }

    @Test
    fun nonStandardOrWrongWeekdayIsIgnored() {
        assertParses("Tues, 03 Nov 2025 10:00:00 GMT")
        assertParses("Thurs, 03 Nov 2025 10:00:00 GMT")
        assertParses("Fri, 03 Nov 2025 10:00:00 GMT") // día de la semana incorrecto
    }

    @Test
    fun iso8601Variants() {
        assertParses("2025-11-03T10:00:00Z")
        assertParses("2025-11-03T10:00:00.000Z")
        assertParses("2025-11-03T11:00:00+01:00")
        assertParses("2025-11-03T11:00:00.123+01:00", expected + 123)
        assertParses("2025-11-03T10:00Z")
        assertParses("2025-11-03T10:00:00")
        assertParses("2025-11-03 10:00:00")
    }

    @Test
    fun dateOnly() {
        assertParses("2025-11-03", 1762128000000L)
        assertParses("Mon, 03 Nov 2025", 1762128000000L)
    }

    @Test
    fun unparseableReturnsZero() {
        assertEquals(0L, FeedDateParser.parseToMillis(""))
        assertEquals(0L, FeedDateParser.parseToMillis(null))
        assertEquals(0L, FeedDateParser.parseToMillis("pronto"))
    }
}
