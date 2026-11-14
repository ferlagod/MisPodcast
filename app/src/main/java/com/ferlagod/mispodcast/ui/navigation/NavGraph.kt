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

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ferlagod.mispodcast.ui.screens.CategoryPodcastsScreen
import com.ferlagod.mispodcast.ui.screens.AddPodcastScreen
import com.ferlagod.mispodcast.ui.screens.FavoritesScreen
import com.ferlagod.mispodcast.ui.screens.HistoryScreen
import com.ferlagod.mispodcast.ui.screens.HomeScreen
import com.ferlagod.mispodcast.ui.screens.NewReleasesScreen
import com.ferlagod.mispodcast.ui.screens.PodcastDetailScreen
import com.ferlagod.mispodcast.ui.screens.QueueScreen
import com.ferlagod.mispodcast.ui.screens.SettingsScreen
import com.ferlagod.mispodcast.ui.screens.StatsScreen
import com.ferlagod.mispodcast.ui.screens.SubscriptionsScreen
import com.ferlagod.mispodcast.ui.viewmodel.PlayerViewModel
import com.ferlagod.mispodcast.ui.viewmodel.PodcastViewModel

/**
 * Grafo central de navegación composable de Mis Podcast.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    playerViewModel: PlayerViewModel = viewModel(),
    podcastViewModel: PodcastViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val bottomTabRoutes = remember {
        setOf(
            Screen.Home.route,
            Screen.Queue.route,
            Screen.NewReleases.route,
            Screen.Subscriptions.route
        )
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        // Entre pestañas: fundido cruzado. Resto (incluida la ficha del podcast): eje X
        // corto y directo, como un cambio de pantalla nativo; al volver, algo más rápido.
        enterTransition = {
            when (motionKind(initialState.destination.route, targetState.destination.route, bottomTabRoutes)) {
                NavMotionKind.Tabs -> tabEnter()
                NavMotionKind.Axis -> axisEnter(forward = true)
            }
        },
        exitTransition = {
            when (motionKind(initialState.destination.route, targetState.destination.route, bottomTabRoutes)) {
                NavMotionKind.Tabs -> tabExit()
                NavMotionKind.Axis -> axisExit(forward = true)
            }
        },
        popEnterTransition = {
            when (motionKind(initialState.destination.route, targetState.destination.route, bottomTabRoutes)) {
                NavMotionKind.Tabs -> tabEnter()
                NavMotionKind.Axis -> axisEnter(forward = false)
            }
        },
        popExitTransition = {
            when (motionKind(initialState.destination.route, targetState.destination.route, bottomTabRoutes)) {
                NavMotionKind.Tabs -> tabExit()
                NavMotionKind.Axis -> axisExit(forward = false)
            }
        }
    ) {
        // Pestañas Principales
        composable(Screen.Home.route) {
            HomeScreen(
                podcastViewModel = podcastViewModel,
                playerViewModel = playerViewModel,
                onNavigateToAddPodcast = {
                    navController.navigate(Screen.AddPodcast.route)
                },
                onNavigateToCategory = { categoryName, genreId ->
                    navController.navigate(Screen.CategoryPodcasts.createRoute(categoryName, genreId))
                },
                onPodcastClick = { podcastId, feedUrl ->
                    navController.navigate(Screen.PodcastDetail.createRoute(podcastId, feedUrl))
                }
            )
        }

        composable(Screen.Queue.route) {
            QueueScreen(
                podcastViewModel = podcastViewModel,
                playerViewModel = playerViewModel,
                onNavigateToNewReleases = {
                    navController.navigate(Screen.NewReleases.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.NewReleases.route) {
            NewReleasesScreen(
                podcastViewModel = podcastViewModel,
                playerViewModel = playerViewModel,
                onNavigateToAddPodcast = {
                    navController.navigate(Screen.AddPodcast.route)
                },
                onPodcastClick = { podcastId, feedUrl ->
                    navController.navigate(Screen.PodcastDetail.createRoute(podcastId, feedUrl))
                }
            )
        }

        composable(Screen.Subscriptions.route) {
            SubscriptionsScreen(
                podcastViewModel = podcastViewModel,
                onNavigateToAddPodcast = {
                    navController.navigate(Screen.AddPodcast.route)
                },
                onPodcastClick = { podcastId, feedUrl ->
                    navController.navigate(Screen.PodcastDetail.createRoute(podcastId, feedUrl))
                }
            )
        }

        // Pantallas del menú "Más"
        composable(Screen.History.route) {
            HistoryScreen(
                podcastViewModel = podcastViewModel,
                playerViewModel = playerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                podcastViewModel = podcastViewModel,
                playerViewModel = playerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Stats.route) {
            StatsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AddPodcast.route) {
            AddPodcastScreen(
                podcastViewModel = podcastViewModel,
                onNavigateBack = { navController.popBackStack() },
                onPodcastClick = { podcastId, feedUrl ->
                    navController.navigate(Screen.PodcastDetail.createRoute(podcastId, feedUrl))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                podcastViewModel = podcastViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }

        composable(Screen.About.route) {
            com.ferlagod.mispodcast.ui.screens.AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Ficha detallada del podcast
        composable(
            route = Screen.PodcastDetail.route,
            arguments = listOf(
                navArgument("podcastId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("feedUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val rawPodcastId = backStackEntry.arguments?.getString("podcastId").orEmpty()
            val rawFeedUrl = backStackEntry.arguments?.getString("feedUrl").orEmpty()

            PodcastDetailScreen(
                podcastId = rawPodcastId,
                feedUrl = rawFeedUrl,
                podcastViewModel = podcastViewModel,
                playerViewModel = playerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Podcasts por Categoría
        composable(
            route = Screen.CategoryPodcasts.route,
            arguments = listOf(
                navArgument("categoryName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("genreId") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val rawCategoryName = backStackEntry.arguments?.getString("categoryName").orEmpty()
            val genreId = backStackEntry.arguments?.getInt("genreId") ?: 0
            val categoryName = if (rawCategoryName.contains("%")) {
                try { java.net.URLDecoder.decode(rawCategoryName, "UTF-8") } catch (_: Exception) { rawCategoryName }
            } else rawCategoryName

            CategoryPodcastsScreen(
                categoryName = categoryName,
                genreId = genreId,
                podcastViewModel = podcastViewModel,
                onNavigateBack = { navController.popBackStack() },
                onPodcastClick = { podcastId, feedUrl ->
                    navController.navigate(Screen.PodcastDetail.createRoute(podcastId, feedUrl))
                }
            )
        }
    }
}

private enum class NavMotionKind { Tabs, Axis }

private fun motionKind(from: String?, to: String?, tabs: Set<String>): NavMotionKind = when {
    from in tabs && to in tabs -> NavMotionKind.Tabs
    else -> NavMotionKind.Axis
}

/** Desplazamiento del eje X: una fracción pequeña del ancho, suficiente para dar dirección. */
private const val AXIS_OFFSET_FRACTION = 0.08f

private fun tabEnter(): EnterTransition =
    fadeIn(tween(NavMotion.FADE_IN, delayMillis = NavMotion.FADE_IN_DELAY, easing = LinearEasing))

private fun tabExit(): ExitTransition =
    fadeOut(tween(NavMotion.FADE_OUT, easing = LinearEasing))

private fun axisEnter(forward: Boolean): EnterTransition {
    val duration = if (forward) NavMotion.DURATION else NavMotion.DURATION_BACK
    return slideInHorizontally(tween(duration, easing = NavMotion.EmphasizedDecelerate)) { w ->
        val d = (w * AXIS_OFFSET_FRACTION).toInt()
        if (forward) d else -d
    } + fadeIn(tween(NavMotion.FADE_IN, delayMillis = NavMotion.FADE_IN_DELAY, easing = LinearEasing))
}

private fun axisExit(forward: Boolean): ExitTransition {
    val duration = if (forward) NavMotion.DURATION else NavMotion.DURATION_BACK
    return slideOutHorizontally(tween(duration, easing = NavMotion.EmphasizedDecelerate)) { w ->
        val d = (w * AXIS_OFFSET_FRACTION).toInt()
        if (forward) -d else d
    } + fadeOut(tween(NavMotion.FADE_OUT, easing = LinearEasing))
}
