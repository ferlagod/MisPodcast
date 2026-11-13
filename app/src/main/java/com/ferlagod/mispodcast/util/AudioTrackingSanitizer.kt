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

    /** Refleja el ajuste "Protección de privacidad (antirrastreo)"; lo actualiza [com.ferlagod.mispodcast.MisPodcastApplication]. */
    @Volatile
    var isEnabled: Boolean = true

    /**
     * Aplica [cleanAudioUrl] solo si el usuario tiene activada la protección antirrastreo.
     */
    fun cleanIfEnabled(rawUrl: String): String = if (isEnabled) cleanAudioUrl(rawUrl) else rawUrl

    /**
     * Servicios de medición que envuelven la URL real del audio como prefijo:
     * `https://<servicio>/<ruta propia>/<host real>/<ruta del audio>` (o con la URL completa incrustada).
     *
     * Solo deben figurar aquí servicios que redirigen a otro host. Los que SON el servidor del audio
     * (p. ej. `traffic.omny.fm` o `traffic.megaphone.fm`) no se pueden quitar: la URL dejaría de existir.
     */
    private val TRACKER_HOSTS = setOf(
        "podtrac.com",          // dts., www., play.
        "chtbl.com",            // Chartable
        "chrt.fm",
        "pdst.fm",              // Podsights
        "pscrb.fm",             // Podscribe
        "verifi.podscribe.com",
        "prfx.byspotify.com",   // Spotify Ad Analytics
        "arttrk.com",           // Artsai
        "clrtpod.com",          // Claritas
        "claritaspod.com",
        "pdrl.fm",              // Podroll
        "op3.dev",              // OP3
        "mgln.ai",              // Magellan
        "2.gum.fm"              // Gumball
    )

    /** Extensiones de audio/vídeo: un segmento como `redirect.mp3` parece un dominio pero no lo es. */
    private val MEDIA_EXTENSIONS = setOf("mp3", "m4a", "m4b", "mp4", "aac", "ogg", "oga", "opus", "wav", "flac", "webm", "mov")

    private val HOST_LIKE = Regex(
        "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+(:[0-9]{1,5})?$",
        RegexOption.IGNORE_CASE
    )
    private val EMBEDDED_SCHEME = Regex("(https?):/{1,2}", RegexOption.IGNORE_CASE)

    /**
     * Quita los prefijos de servicios de medición conocidos y los parámetros de rastreo de la query.
     *
     * Ante cualquier duda (no se encuentra un host claro tras el prefijo) deja la URL como está:
     * es preferible conservar un rastreador a producir una URL que no existe.
     */
    fun cleanAudioUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isBlank()) return rawUrl
        repeat(MAX_UNWRAP_DEPTH) {
            url = unwrapOnce(url) ?: return stripTrackingQueryParams(url)
        }
        return stripTrackingQueryParams(url)
    }

    private const val MAX_UNWRAP_DEPTH = 8

    /** Quita un nivel de prefijo de medición, o devuelve `null` si [url] no empieza por uno reconocible. */
    private fun unwrapOnce(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null

        val rest = url.substring(schemeEnd + 3)
        val host = rest.takeWhile { it != '/' && it != '?' && it != '#' }.lowercase()
        if (!isTrackerHost(host)) return null
        if (rest.length <= host.length || rest[host.length] != '/') return null
        val path = rest.substring(host.length + 1)

        // URL completa incrustada (algunos servidores colapsan «https://» en «https:/»)
        EMBEDDED_SCHEME.find(path)?.let { match ->
            val inner = path.substring(match.range.last + 1)
            return if (inner.isNotBlank()) "${match.groupValues[1].lowercase()}://$inner" else null
        }

        // Host real como segmento de la ruta: el primero con forma de dominio, nunca el último
        // (el último es el archivo de audio)
        val segments = path.split('/')
        for (i in 0 until segments.size - 1) {
            if (isHostLike(segments[i])) return "https://" + segments.drop(i).joinToString("/")
        }
        return null
    }

    private fun isTrackerHost(host: String): Boolean =
        TRACKER_HOSTS.any { host == it || host.endsWith(".$it") }

    private fun isHostLike(segment: String): Boolean {
        if (!HOST_LIKE.matches(segment)) return false
        val tld = segment.substringBefore(':').substringAfterLast('.').lowercase()
        return tld.length >= 2 && tld.all { it in 'a'..'z' } && tld !in MEDIA_EXTENSIONS
    }

    /** Lista CONGELADA de la versión antigua; solo para [stableIdKey]. */
    private val LEGACY_TRACKER_HOSTS = listOf(
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
     * Clave con la que el parser identifica los episodios sin GUID (`feedUrl_hash(clave)`).
     *
     * NO MODIFICAR: es la implementación antigua de [cleanAudioUrl], congelada. Si cambiara, los
     * episodios sin GUID recibirían otro id al refrescar el feed: aparecerían duplicados y perderían
     * su progreso, favoritos y descargas. Para reproducir o descargar se usa [cleanAudioUrl].
     */
    fun stableIdKey(rawUrl: String): String {
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
                if (LEGACY_TRACKER_HOSTS.any { prefix.contains(it) } || prefix.contains("/track/") || prefix.contains("/redirect")) {
                    url = url.substring(nextHttpIndex)
                    changed = true
                    continue
                }
            }

            // Caso B: Redirección sin esquema tras el host de seguimiento (ej: https://dts.podtrac.com/redirect.mp3/cdn.ejemplo.com/audio.mp3)
            for (tracker in LEGACY_TRACKER_HOSTS) {
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
     * Es el mismo en feeds, reproducción y descargas: las estadísticas de los podcasts
     * identifican la app por él, y uno distinto en cada caso las fragmentaría.
     */
    const val GENERIC_PRIVACY_USER_AGENT = "MisPodcast/1.0 (Android; Open Source Podcast Player)"
}
