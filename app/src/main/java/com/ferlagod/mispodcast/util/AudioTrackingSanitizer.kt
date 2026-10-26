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

/**
 * Filtro de privacidad y antirrastreo para transmisiones de audio de podcasts.
 *
 * Desenrolla redirecciones publicitarias y servicios de analítica de terceros
 * (Podtrac, Chartable, pdst.fm, Spotify Ad Analytics, Claritas, etc.) que envuelven
 * las URLs originales de los episodios, y limpia parámetros query de rastreo (UTM, etc.).
 *
 * Esto evita el perfilado del oyente y mejora los tiempos de conexión al descargar o reproducir.
 */
object AudioTrackingSanitizer {

    /** Lista de hosts o dominios de seguimiento publicitario conocidos en la industria. */
    private val TRACKER_HOSTS = listOf(
        "dts.podtrac.com",
        "www.podtrac.com",
        "play.podtrac.com",
        "podtrac.com",
        "chtbl.com",
        "chrt.fm",
        "pdst.fm",
        "pscrb.fm",
        "prfx.byspotify.com",
        "claritas.com",
        "traffic.omny.fm",
        "veritonic.com",
        "pdrl.fm"
    )

    private val TRACKING_QUERY_PARAMS = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "ref",
        "tracking",
        "pt_source",
        "pt_medium",
        "pt_campaign"
    )

    /**
     * Limpia la URL de audio eliminando prefijos de redirección publicitaria y parámetros de rastreo.
     * Si la URL no contiene rastreadores conocidos, se devuelve inalterada.
     */
    fun cleanAudioUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isBlank()) return rawUrl

        var changed = true
        var loopCount = 0

        while (changed && loopCount < 6) {
            changed = false
            loopCount++

            // Caso A: Otra URL completa (http:// o https://) incrustada tras el wrapper de seguimiento
            val embeddedHttpIndex = url.indexOf("http://", startIndex = 8, ignoreCase = true)
            val embeddedHttpsIndex = url.indexOf("https://", startIndex = 8, ignoreCase = true)
            val nextHttpIndex = when {
                embeddedHttpIndex > 0 && embeddedHttpsIndex > 0 -> minOf(embeddedHttpIndex, embeddedHttpsIndex)
                embeddedHttpIndex > 0 -> embeddedHttpIndex
                embeddedHttpsIndex > 0 -> embeddedHttpsIndex
                else -> -1
            }

            if (nextHttpIndex > 0) {
                val prefix = url.substring(0, nextHttpIndex).lowercase()
                if (TRACKER_HOSTS.any { prefix.contains(it) } || prefix.contains("/track/") || prefix.contains("/redirect")) {
                    url = url.substring(nextHttpIndex)
                    changed = true
                    continue
                }
            }

            // Caso B: Redirección sin esquema tras el host de seguimiento (ej: https://dts.podtrac.com/redirect.mp3/cdn.ejemplo.com/audio.mp3)
            for (tracker in TRACKER_HOSTS) {
                val prefixHttps = "https://$tracker/"
                val prefixHttp = "http://$tracker/"
                val matchedPrefix = when {
                    url.startsWith(prefixHttps, ignoreCase = true) -> prefixHttps
                    url.startsWith(prefixHttp, ignoreCase = true) -> prefixHttp
                    else -> null
                }

                if (matchedPrefix != null) {
                    val afterHost = url.substring(matchedPrefix.length)
                    // Buscar si contiene un /redirect.mp3/ o similar antes del host real
                    val cleanPath = when {
                        afterHost.startsWith("redirect.mp3/", ignoreCase = true) -> afterHost.substring("redirect.mp3/".length)
                        afterHost.startsWith("pts/redirect.mp3/", ignoreCase = true) -> afterHost.substring("pts/redirect.mp3/".length)
                        afterHost.startsWith("e/", ignoreCase = true) -> afterHost.substring("e/".length)
                        afterHost.startsWith("track/", ignoreCase = true) -> afterHost.substringAfter('/')
                        else -> afterHost
                    }

                    if (cleanPath.contains('.')) {
                        url = if (cleanPath.startsWith("http://", ignoreCase = true) || cleanPath.startsWith("https://", ignoreCase = true)) {
                            cleanPath
                        } else {
                            "https://$cleanPath"
                        }
                        changed = true
                        break
                    }
                }
            }
        }

        // Limpieza de parámetros UTM y de seguimiento en query string
        return stripTrackingQueryParams(url)
    }

    private fun stripTrackingQueryParams(url: String): String {
        val questionIndex = url.indexOf('?')
        if (questionIndex == -1 || questionIndex == url.length - 1) return url

        val baseUrl = url.substring(0, questionIndex)
        val queryString = url.substring(questionIndex + 1)

        val cleanParams = queryString.split('&')
            .filter { param ->
                val key = param.substringBefore('=').trim().lowercase()
                key !in TRACKING_QUERY_PARAMS && key.isNotEmpty()
            }

        return if (cleanParams.isEmpty()) {
            baseUrl
        } else {
            "$baseUrl?${cleanParams.joinToString("&")}"
        }
    }

    /**
     * Devuelve un User-Agent genérico respetuoso con la privacidad
     * para evitar el fingerprinting de dispositivo por parte de servidores publicitarios.
     */
    const val GENERIC_PRIVACY_USER_AGENT = "MisPodcast/1.0 (Android; Open Source Podcast Player)"
}
