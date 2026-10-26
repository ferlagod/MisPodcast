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

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val countryCode: String
) {
    SYSTEM("system", "Predeterminado del sistema", "System default", ""),
    SPANISH("es", "Español", "Español", "ES"),
    ENGLISH("en", "English", "English", "US"),
    FRENCH("fr", "Français", "Français", "FR"),
    GERMAN("de", "Deutsch", "Deutsch", "DE"),
    ITALIAN("it", "Italiano", "Italiano", "IT"),
    PORTUGUESE("pt", "Português", "Português", "PT");

    companion object {
        fun fromCode(code: String): LanguageOption {
            val normalized = code.trim().lowercase()
            return entries.firstOrNull { it.code.equals(normalized, ignoreCase = true) }
                ?: if (normalized == "auto") SYSTEM else SYSTEM
        }
    }
}

object LanguageManager {

    val SUPPORTED_LANGUAGES = listOf("es", "en", "fr", "de", "it", "pt")
    const val DEFAULT_FALLBACK_LANGUAGE = "en"

    /**
     * Aplica el idioma elegido al instante en la aplicación mediante AppCompatDelegate.
     */
    fun applyLanguage(langCode: String) {
        val normalized = langCode.trim().lowercase()
        if (normalized.isBlank() || normalized == "system" || normalized == "auto") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            val targetTag = if (normalized in SUPPORTED_LANGUAGES) normalized else DEFAULT_FALLBACK_LANGUAGE
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(targetTag))
        }
    }

    /**
     * Obtiene el código de idioma efectivo de 2 letras ("es", "en", "fr", "de", "it", "pt").
     * Si está en modo sistema o auto, comprueba Locale.getDefault().language.
     * Si el idioma no está soportado, devuelve "en" como fallback.
     */
    fun getEffectiveLanguageCode(selectedLanguageCode: String? = null): String {
        val selected = selectedLanguageCode?.trim()?.lowercase()
        if (selected.isNullOrBlank() || selected == "system" || selected == "auto") {
            val deviceLanguage = Locale.getDefault().language.lowercase()
            return if (deviceLanguage in SUPPORTED_LANGUAGES) deviceLanguage else DEFAULT_FALLBACK_LANGUAGE
        }
        return if (selected in SUPPORTED_LANGUAGES) selected else DEFAULT_FALLBACK_LANGUAGE
    }
}
