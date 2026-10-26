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

package com.ferlagod.mispodcast

import android.app.Application
import com.ferlagod.mispodcast.di.AppContainer
import com.ferlagod.mispodcast.di.DefaultAppContainer

/**
 * Clase [Application] principal de Mis Podcast.
 *
 * Responsable de inicializar el ciclo de vida del proceso y alojar el [AppContainer]
 * que provee inyección manual de dependencias y servicios compartidos en toda la aplicación.
 */
class MisPodcastApplication : Application() {

    /**
     * Contenedor de dependencias de la aplicación.
     */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = DefaultAppContainer(this)
    }

    companion object {
        @Volatile
        private var instance: MisPodcastApplication? = null

        /**
         * Acceso estático seguro al contenedor de dependencias de la aplicación.
         */
        val appContainer: AppContainer
            get() = instance?.container
                ?: throw IllegalStateException("MisPodcastApplication aún no ha sido inicializada")
    }
}
