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
import kotlinx.serialization.json.Json

/**
 * Beneficiario individual de Value4Value en la red Lightning de Bitcoin según la especificación Podcasting 2.0.
 *
 * @property name Nombre descriptivo del beneficiario (ej. "Creador", "Edición").
 * @property type Tipo de dirección ("node" para clave pública de nodo o "lnaddress" para dirección tipo email Lightning).
 * @property address Dirección de pago (pubkey de 66 caracteres hex o usuario@dominio.com).
 * @property split Porcentaje o peso de reparto de los satoshis (ej. 90, 10).
 * @property fee Indica si el destinatario es una comisión del servicio/nodo.
 * @property customKey Clave opcional para pagos directos keysend.
 * @property customValue Valor opcional para pagos directos keysend.
 */
@Serializable
data class ValueRecipient(
    val name: String? = null,
    val type: String = "node",
    val address: String = "",
    val split: Int = 100,
    val fee: Boolean = false,
    val customKey: String? = null,
    val customValue: String? = null
)

/**
 * Configuración de Value4Value (V4V) extraída de las etiquetas <podcast:value> en feeds RSS de Podcasting 2.0.
 *
 * @property type Tipo de tecnología (generalmente "lightning").
 * @property method Método de pago ("keysend", "lnurl", etc.).
 * @property suggested Cantidad sugerida por minuto o por donación (ej. "0.00000005000" BTC o satoshis).
 * @property recipients Lista de beneficiarios receptores del pago con sus respectivos porcentajes.
 */
@Serializable
data class ValueForValueConfig(
    val type: String = "lightning",
    val method: String = "keysend",
    val suggested: String? = null,
    val recipients: List<ValueRecipient> = emptyList()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Parsea una cadena de texto guardada en base de datos. Soporta tanto el formato JSON nuevo
         * como el formato legacy plano "type=...;method=...".
         */
        fun fromString(raw: String?): ValueForValueConfig? {
            if (raw.isNullOrBlank()) return null
            return try {
                if (raw.trim().startsWith("{")) {
                    json.decodeFromString<ValueForValueConfig>(raw)
                } else {
                    val parts = raw.split(";").associate {
                        val kv = it.split("=")
                        if (kv.size == 2) kv[0].trim() to kv[1].trim() else "" to ""
                    }
                    ValueForValueConfig(
                        type = parts["type"] ?: "lightning",
                        method = parts["method"] ?: "keysend"
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
