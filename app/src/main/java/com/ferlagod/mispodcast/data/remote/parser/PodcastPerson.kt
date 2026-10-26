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

/**
 * Modelo de datos para representar personas involucradas en el podcast (autores, presentadores, invitados)
 * según la especificación de Podcasting 2.0 (`<podcast:person>`).
 * Compatible con kotlinx.serialization para ejecución sin reflexión y compatibilidad con R8.
 */
@Serializable
data class PodcastPerson(
    val name: String,
    val role: String? = null,
    val group: String? = null,
    val img: String? = null,
    val href: String? = null
)
