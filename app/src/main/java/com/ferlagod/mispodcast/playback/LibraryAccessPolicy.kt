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

package com.ferlagod.mispodcast.playback

/**
 * Decide qué controladores multimedia pueden explorar la biblioteca (suscripciones, cola,
 * historial, búsqueda) del servicio de reproducción.
 */
object LibraryAccessPolicy {

    /** Apps de Google que exploran la biblioteca sin ser «de confianza» para el sistema. */
    val ALLOWED_PACKAGES = setOf(
        "com.google.android.projection.gearhead", // Android Auto
        "com.google.android.googlequicksearchbox", // Asistente de Google
        "com.google.android.carassistant",
        "com.google.android.wearable.app", // Wear OS
        "com.google.android.apps.wearables.maestro.companion"
    )

    /**
     * @param isTrusted la app, el sistema o quien tenga MEDIA_CONTENT_CONTROL / escucha de notificaciones.
     * @param isCarOrNotification Android Auto, Android Automotive o el controlador de la notificación.
     * @param isPackageNameVerified el sistema ha confirmado que [packageName] pertenece al llamante;
     *   sin eso, cualquier app podría hacerse pasar por una de [ALLOWED_PACKAGES].
     */
    fun canBrowseLibrary(
        isTrusted: Boolean,
        isCarOrNotification: Boolean,
        packageName: String,
        isPackageNameVerified: Boolean
    ): Boolean = isTrusted || isCarOrNotification ||
        (isPackageNameVerified && packageName in ALLOWED_PACKAGES)
}
