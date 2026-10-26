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
 * Representa una sección o intervención de texto dentro de una transcripción temporizada
 * (conforme a los estándares Podcasting 2.0 WebVTT, SRT o JSON).
 *
 * @property startTimeMs Tiempo de inicio de la frase en milisegundos.
 * @property endTimeMs Tiempo de finalización de la frase en milisegundos.
 * @property text Texto transcrito de la locución.
 * @property speaker Nombre o identificador opcional del interlocutor que pronuncia el texto.
 */
@Serializable
data class TranscriptSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val speaker: String? = null
)
