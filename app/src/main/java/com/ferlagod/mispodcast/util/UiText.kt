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

package com.ferlagod.mispodcast.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Texto destinado a la interfaz que se resuelve en el momento de mostrarse.
 *
 * Permite que los ViewModels emitan mensajes traducibles sin depender del contexto de la aplicación,
 * que en Android < 13 no refleja el idioma elegido dentro de la app (AppCompat solo localiza las Activity).
 */
sealed interface UiText {
    /** Texto literal ya listo para mostrar (p. ej. un nombre de podcast o un mensaje técnico). */
    data class Raw(val value: String) : UiText

    /** Recurso de texto con argumentos de formato opcionales (pueden ser a su vez [UiText]). */
    class Res(@StringRes val id: Int, vararg val args: Any) : UiText

    /** Recurso de plurales elegido según [count], con argumentos de formato opcionales. */
    class Plural(@PluralsRes val id: Int, val count: Int, vararg val args: Any) : UiText

    fun asString(context: Context): String = when (this) {
        is Raw -> value
        is Res -> context.getString(id, *resolveArgs(context, args))
        is Plural -> context.resources.getQuantityString(id, count, *resolveArgs(context, args))
    }

    @Composable
    fun asString(): String = asString(LocalContext.current)

    private fun resolveArgs(context: Context, args: Array<out Any>): Array<Any> =
        args.map { if (it is UiText) it.asString(context) else it }.toTypedArray()
}
