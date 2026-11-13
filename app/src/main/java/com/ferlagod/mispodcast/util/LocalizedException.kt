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

import androidx.annotation.StringRes
import com.ferlagod.mispodcast.R

/**
 * Error con un mensaje traducible para el usuario.
 *
 * Las capas de datos no conocen el idioma elegido en la app: en lugar de un texto fijo llevan un
 * [UiText] que la interfaz resuelve al mostrarlo. [message] queda solo para los registros.
 */
open class LocalizedException(
    val text: UiText,
    debugMessage: String? = null,
    cause: Throwable? = null
) : Exception(debugMessage, cause) {
    constructor(@StringRes id: Int, vararg args: Any) : this(UiText.Res(id, *args))
}

/**
 * Mensaje de este error apto para mostrar al usuario en su idioma.
 *
 * Los errores de red y de formato de feed se traducen a un texto comprensible; el resto
 * conserva su mensaje original (o, si no tiene, el genérico [fallback]).
 */
fun Throwable.asUiText(@StringRes fallback: Int = R.string.error_unexpected): UiText = when (this) {
    is LocalizedException -> text
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    is java.net.NoRouteToHostException -> UiText.Res(R.string.error_network)
    is java.net.SocketTimeoutException -> UiText.Res(R.string.error_timeout)
    is javax.net.ssl.SSLException -> UiText.Res(R.string.error_secure_connection)
    is org.xmlpull.v1.XmlPullParserException -> UiText.Res(R.string.error_feed_invalid)
    // Resto de fallos de E/S de red: su mensaje técnico (en inglés) no ayuda al usuario
    is java.io.IOException -> UiText.Res(R.string.error_network)
    else -> cause?.takeIf { it !== this && it is LocalizedException }?.asUiText(fallback)
        ?: localizedMessage?.takeIf { it.isNotBlank() }?.let(UiText::Raw)
        ?: UiText.Res(fallback)
}
