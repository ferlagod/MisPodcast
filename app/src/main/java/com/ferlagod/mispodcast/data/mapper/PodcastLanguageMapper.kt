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

package com.ferlagod.mispodcast.data.mapper

import com.ferlagod.mispodcast.util.LanguageManager

object PodcastLanguageMapper {

    /**
     * Resuelve el código de idioma efectivo a dos letras (ej: "es", "en", "fr", "de", "it", "pt").
     * Si es "system", "auto" o está vacío, lee el Locale del sistema con fallback oficial en "en".
     */
    fun resolveLanguageCode(selectedLanguageCode: String): String {
        return LanguageManager.getEffectiveLanguageCode(selectedLanguageCode)
    }

    /**
     * Devuelve el código de país de 2 letras ISO para la API de Apple Podcasts.
     * ES, US, FR, DE, IT, PT (fallback en US).
     */
    fun getCountryCode(selectedLanguageCode: String): String {
        return when (resolveLanguageCode(selectedLanguageCode)) {
            "es" -> "ES"
            "en" -> "US"
            "fr" -> "FR"
            "de" -> "DE"
            "it" -> "IT"
            "pt" -> "PT"
            else -> "US"
        }
    }

    /**
     * Devuelve el código de idioma para el parámetro "lang" de la API de Apple Podcasts.
     */
    fun getLanguageParam(selectedLanguageCode: String): String {
        return when (resolveLanguageCode(selectedLanguageCode)) {
            "es" -> "es_es"
            "en" -> "en_us"
            "fr" -> "fr_fr"
            "de" -> "de_de"
            "it" -> "it_it"
            "pt" -> "pt_pt"
            else -> "en_us"
        }
    }

    /**
     * Devuelve el término de búsqueda traducido al idioma correspondiente según el genreId de Apple Podcasts.
     */
    fun getCategoryTerm(genreId: Int, selectedLanguageCode: String): String {
        val lang = resolveLanguageCode(selectedLanguageCode)
        return when (genreId) {
            1318 -> when (lang) { // Tecnología
                "es" -> "tecnologia"
                "en" -> "technology"
                "fr" -> "technologie"
                "de" -> "technologie"
                "it" -> "tecnologia"
                "pt" -> "tecnologia"
                else -> "tecnologia"
            }
            1533 -> when (lang) { // Ciencia
                "es" -> "ciencia"
                "en" -> "science"
                "fr" -> "science"
                "de" -> "wissenschaft"
                "it" -> "scienza"
                "pt" -> "ciencia"
                else -> "ciencia"
            }
            1487 -> when (lang) { // Historia
                "es" -> "historia"
                "en" -> "history"
                "fr" -> "histoire"
                "de" -> "geschichte"
                "it" -> "storia"
                "pt" -> "historia"
                else -> "historia"
            }
            1303 -> when (lang) { // Comedia
                "es" -> "comedia"
                "en" -> "comedy"
                "fr" -> "comedie"
                "de" -> "comedy"
                "it" -> "commedia"
                "pt" -> "comedia"
                else -> "comedia"
            }
            1489 -> when (lang) { // Noticias
                "es" -> "noticias"
                "en" -> "news"
                "fr" -> "actualites"
                "de" -> "nachrichten"
                "it" -> "notizie"
                "pt" -> "noticias"
                else -> "noticias"
            }
            1483 -> when (lang) { // Ficción
                "es" -> "ficcion"
                "en" -> "fiction"
                "fr" -> "fiction"
                "de" -> "fiktion"
                "it" -> "fiction"
                "pt" -> "ficcao"
                else -> "ficcion"
            }
            1512 -> when (lang) { // Salud
                "es" -> "salud"
                "en" -> "health"
                "fr" -> "sante"
                "de" -> "gesundheit"
                "it" -> "salute"
                "pt" -> "saude"
                else -> "salud"
            }
            1301 -> when (lang) { // Libros / Cultura
                "es" -> "libros"
                "en" -> "books"
                "fr" -> "livres"
                "de" -> "bucher"
                "it" -> "libri"
                "pt" -> "livros"
                else -> "libros"
            }
            else -> getHomeTrendingTerm(selectedLanguageCode)
        }
    }

    /**
     * Término genérico para tendencias de podcasts de inicio según el idioma.
     */
    fun getHomeTrendingTerm(selectedLanguageCode: String): String {
        return when (resolveLanguageCode(selectedLanguageCode)) {
            "es" -> "podcast"
            "en" -> "podcast"
            "fr" -> "podcast"
            "de" -> "podcast"
            "it" -> "podcast"
            "pt" -> "podcast"
            else -> "podcast"
        }
    }

    /**
     * Nombre descriptivo de la región / país correspondiente.
     */
    fun getRegionDisplayName(selectedLanguageCode: String): String {
        return when (resolveLanguageCode(selectedLanguageCode)) {
            "es" -> "España"
            "en" -> "Estados Unidos"
            "fr" -> "Francia"
            "de" -> "Alemania"
            "it" -> "Italia"
            "pt" -> "Portugal"
            else -> "Estados Unidos"
        }
    }

    fun getTrendingTitle(selectedLanguageCode: String): String {
        val region = getRegionDisplayName(selectedLanguageCode)
        return when (resolveLanguageCode(selectedLanguageCode)) {
            "es" -> "Tendencias en $region"
            "en" -> "Trending in $region"
            "fr" -> "Tendances en $region"
            "de" -> "Trends in $region"
            "it" -> "Tendenze in $region"
            "pt" -> "Tendências em $region"
            else -> "Trending in $region"
        }
    }

    fun getTrendingSubtitle(selectedLanguageCode: String): String {
        val region = getRegionDisplayName(selectedLanguageCode)
        return when (resolveLanguageCode(selectedLanguageCode)) {
            "es" -> "Los podcasts más populares de Apple Podcasts ($region)"
            "en" -> "Top popular podcasts on Apple Podcasts ($region)"
            "fr" -> "Les podcasts les plus populaires sur Apple Podcasts ($region)"
            "de" -> "Die beliebtesten Podcasts auf Apple Podcasts ($region)"
            "it" -> "I podcast più popolari su Apple Podcasts ($region)"
            "pt" -> "Os podcasts mais populares no Apple Podcasts ($region)"
            else -> "Top popular podcasts on Apple Podcasts ($region)"
        }
    }

    fun getRecommendedTitle(selectedLanguageCode: String): String {
        return when (resolveLanguageCode(selectedLanguageCode)) {
            "es" -> "Recomendados para ti"
            "en" -> "Recommended for You"
            "fr" -> "Recommandé pour vous"
            "de" -> "Für dich empfohlen"
            "it" -> "Consigliati per te"
            "pt" -> "Recomendados para você"
            else -> "Recommended for You"
        }
    }
}
