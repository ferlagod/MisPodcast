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

package com.ferlagod.mispodcast.data.local.model

import androidx.annotation.StringRes
import com.ferlagod.mispodcast.R

/**
 * Criterios de ordenación automática inteligente disponibles para la cola de reproducción.
 *
 * Permite ordenar episodios por duración (más cortos o más largos), tamaño de archivo descargado,
 * título alfabético o fecha de publicación.
 */
enum class QueueSortOption(val label: String, @get:StringRes val titleRes: Int) {
    DURATION_ASC("Duración: Más cortos primero", R.string.sort_duration_asc),
    DURATION_DESC("Duración: Más largos primero", R.string.sort_duration_desc),
    SIZE_ASC("Tamaño: Menor peso primero", R.string.sort_size_asc),
    SIZE_DESC("Tamaño: Mayor peso primero", R.string.sort_size_desc),
    NAME_ASC("Título: A - Z", R.string.sort_name_asc),
    NAME_DESC("Título: Z - A", R.string.sort_name_desc),
    DATE_DESC("Fecha: Más recientes primero", R.string.sort_date_desc),
    DATE_ASC("Fecha: Más antiguos primero", R.string.sort_date_asc)
}
