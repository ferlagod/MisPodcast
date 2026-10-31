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

package com.ferlagod.mispodcast.data.remote.parser

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fragmento destacado de un episodio según Podcasting 2.0 (`<podcast:soundbite>`).
 */
@Serializable
data class PodcastSoundbite(
    val startTimeMs: Long,
    val durationMs: Long,
    val title: String? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(list: List<PodcastSoundbite>): String? =
            if (list.isEmpty()) null else runCatching { json.encodeToString(list) }.getOrNull()

        fun decode(raw: String?): List<PodcastSoundbite> =
            if (raw.isNullOrBlank()) emptyList()
            else runCatching { json.decodeFromString<List<PodcastSoundbite>>(raw) }.getOrDefault(emptyList())
    }
}
