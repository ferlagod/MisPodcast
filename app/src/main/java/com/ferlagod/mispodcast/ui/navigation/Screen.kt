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

package com.ferlagod.mispodcast.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.ui.graphics.vector.ImageVector
import com.ferlagod.mispodcast.R

/**
 * Definición jerárquica de destinos y rutas de navegación de la aplicación.
 *
 * Cada destino especifica su ruta única, título localizable e icono para la barra de navegación o menús.
 */
sealed class Screen(
    val route: String,
    val title: String,
    @get:StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector = selectedIcon
) {
    // 4 pestañas principales del NavigationBar
    data object Home : Screen(
        route = "home",
        title = "Inicio",
        titleRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Queue : Screen(
        route = "queue",
        title = "Cola",
        titleRes = R.string.nav_queue,
        selectedIcon = Icons.AutoMirrored.Filled.QueueMusic,
        unselectedIcon = Icons.AutoMirrored.Outlined.QueueMusic
    )

    data object NewReleases : Screen(
        route = "new_releases",
        title = "Novedades",
        titleRes = R.string.nav_new_releases,
        selectedIcon = Icons.Filled.NewReleases,
        unselectedIcon = Icons.Outlined.NewReleases
    )

    data object Subscriptions : Screen(
        route = "subscriptions",
        title = "Suscripciones",
        titleRes = R.string.nav_subscriptions,
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic
    )

    // Destinos accesibles desde el menú emergente "Más"
    data object History : Screen(
        route = "history",
        title = "Historial",
        titleRes = R.string.menu_history,
        selectedIcon = Icons.Filled.History
    )

    data object Favorites : Screen(
        route = "favorites",
        title = "Episodios favoritos",
        titleRes = R.string.menu_favorites,
        selectedIcon = Icons.Filled.Favorite
    )

    data object Stats : Screen(
        route = "stats",
        title = "Estadísticas",
        titleRes = R.string.menu_stats,
        selectedIcon = Icons.Filled.BarChart
    )

    data object AddPodcast : Screen(
        route = "add_podcast",
        title = "Añadir podcast",
        titleRes = R.string.menu_add_podcast,
        selectedIcon = Icons.Filled.AddCircle
    )

    data object Settings : Screen(
        route = "settings",
        title = "Ajustes",
        titleRes = R.string.menu_settings,
        selectedIcon = Icons.Filled.Settings
    )

    data object About : Screen(
        route = "about",
        title = "Acerca de",
        titleRes = R.string.menu_about,
        selectedIcon = Icons.Filled.Info
    )

    data object PodcastDetail : Screen(
        route = "podcast_detail?podcastId={podcastId}&feedUrl={feedUrl}",
        title = "Detalle del podcast",
        titleRes = R.string.podcast_detail_title,
        selectedIcon = Icons.Filled.LibraryMusic
    ) {
        fun createRoute(podcastId: String = "", feedUrl: String = ""): String {
            val encodedId = android.net.Uri.encode(podcastId)
            val encodedUrl = android.net.Uri.encode(feedUrl)
            return "podcast_detail?podcastId=$encodedId&feedUrl=$encodedUrl"
        }
    }

    data object CategoryPodcasts : Screen(
        route = "category_podcasts?categoryName={categoryName}&genreId={genreId}",
        title = "Categoría",
        titleRes = R.string.category_title,
        selectedIcon = Icons.Filled.Podcasts
    ) {
        fun createRoute(categoryName: String, genreId: Int): String {
            val encodedName = android.net.Uri.encode(categoryName)
            return "category_podcasts?categoryName=$encodedName&genreId=$genreId"
        }
    }

    companion object {
        val bottomNavTabs: List<Screen>
            get() = listOf(Home, Queue, NewReleases, Subscriptions)
    }
}

data class MoreMenuItem(
    val title: String,
    @get:StringRes val titleRes: Int,
    val icon: ImageVector,
    val route: String
)

val moreMenuItems: List<MoreMenuItem>
    get() = listOf(
        MoreMenuItem("Historial", R.string.menu_history, Icons.Filled.History, Screen.History.route),
        MoreMenuItem("Episodios favoritos", R.string.menu_favorites, Icons.Filled.Favorite, Screen.Favorites.route),
        MoreMenuItem("Estadísticas", R.string.menu_stats, Icons.Filled.BarChart, Screen.Stats.route),
        MoreMenuItem("Añadir podcast", R.string.menu_add_podcast, Icons.Filled.AddCircle, Screen.AddPodcast.route),
        MoreMenuItem("Ajustes", R.string.menu_settings, Icons.Filled.Settings, Screen.Settings.route),
        MoreMenuItem("Acerca de", R.string.menu_about, Icons.Filled.Info, Screen.About.route)
    )
