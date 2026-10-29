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
 * Criterios de ordenación disponibles para la biblioteca de podcasts suscritos.
 */
enum class SubscriptionSortOption(val label: String, @get:StringRes val titleRes: Int) {
    RECENTLY_SUBSCRIBED("Fecha de suscripción: Más recientes", R.string.sort_subscription_recent),
    OLDEST_SUBSCRIBED("Fecha de suscripción: Más antiguos", R.string.sort_subscription_oldest),
    TITLE_ASC("Título: A - Z", R.string.sort_name_asc),
    TITLE_DESC("Título: Z - A", R.string.sort_name_desc),
    RECENTLY_UPDATED("Última actualización", R.string.sort_recently_updated),
    AUTHOR_ASC("Autor: A - Z", R.string.sort_author_asc);

    companion object {
        fun fromName(name: String?): SubscriptionSortOption {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: RECENTLY_SUBSCRIBED
        }
    }
}
