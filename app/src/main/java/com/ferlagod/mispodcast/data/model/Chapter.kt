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

package com.ferlagod.mispodcast.data.model

import kotlinx.serialization.Serializable

/**
 * Representa un capítulo de un episodio de podcast, obtenido vía Podcasting 2.0 (JSON)
 * o embebido como metadatos ID3 (frame CHAP) en el archivo de audio MP3.
 *
 * @property title Título o nombre descriptivo del capítulo.
 * @property startTimeMs Tiempo de inicio del capítulo en milisegundos.
 * @property endTimeMs Tiempo de finalización opcional en milisegundos.
 * @property imageUrl URL opcional de la imagen o carátula específica del capítulo.
 * @property linkUrl Enlace web externo opcional con notas o referencias del capítulo.
 */
@Serializable
data class Chapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val imageUrl: String? = null,
    val linkUrl: String? = null
)
