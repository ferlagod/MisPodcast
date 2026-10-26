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

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
 *
 * Define el enrutador [NavHost] dentro de un [SharedTransitionLayout] para permitir transiciones
 * compartidas de carátulas entre pantallas de forma inmersiva y de alto impacto visual.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
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

    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                enterTransition = {
                    val fromRoute = initialState.destination.route
                    val toRoute = targetState.destination.route
                    val isBetweenTabs = (fromRoute in bottomTabRoutes) && (toRoute in bottomTabRoutes)
                    if (isBetweenTabs) {
                        fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(300))
                    }
                },
                exitTransition = {
                    val fromRoute = initialState.destination.route
                    val toRoute = targetState.destination.route
                    val isBetweenTabs = (fromRoute in bottomTabRoutes) && (toRoute in bottomTabRoutes)
                    if (isBetweenTabs) {
                        fadeOut(animationSpec = tween(180))
                    } else {
                        slideOutHorizontally(
                            targetOffsetX = { -it / 3 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200))
                    }
                },
                popEnterTransition = {
                    val fromRoute = initialState.destination.route
                    val toRoute = targetState.destination.route
                    val isBetweenTabs = (fromRoute in bottomTabRoutes) && (toRoute in bottomTabRoutes)
                    if (isBetweenTabs) {
                        fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(200))
                    }
                },
                popExitTransition = {
                    val fromRoute = initialState.destination.route
                    val toRoute = targetState.destination.route
                    val isBetweenTabs = (fromRoute in bottomTabRoutes) && (toRoute in bottomTabRoutes)
                    if (isBetweenTabs) {
                        fadeOut(animationSpec = tween(180))
                    } else {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200))
                    }
                }
            ) {
                // Pestañas Principales
                composable(Screen.Home.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
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
                }

                composable(Screen.Queue.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        QueueScreen(
                            podcastViewModel = podcastViewModel,
                            playerViewModel = playerViewModel
                        )
                    }
                }

                composable(Screen.NewReleases.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        NewReleasesScreen(
                            podcastViewModel = podcastViewModel,
                            playerViewModel = playerViewModel,
                            onNavigateToAddPodcast = {
                                navController.navigate(Screen.AddPodcast.route)
                            }
                        )
                    }
                }

                composable(Screen.Subscriptions.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
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
                }

                // Pantallas del menú "Más"
                composable(Screen.History.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        HistoryScreen(
                            podcastViewModel = podcastViewModel,
                            playerViewModel = playerViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(Screen.Favorites.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        FavoritesScreen(
                            podcastViewModel = podcastViewModel,
                            playerViewModel = playerViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(Screen.Stats.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        StatsScreen(
                            podcastViewModel = podcastViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(Screen.AddPodcast.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        AddPodcastScreen(
                            podcastViewModel = podcastViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onPodcastClick = { podcastId, feedUrl ->
                                navController.navigate(Screen.PodcastDetail.createRoute(podcastId, feedUrl))
                            }
                        )
                    }
                }

                composable(Screen.Settings.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        SettingsScreen(
                            podcastViewModel = podcastViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToAbout = { navController.navigate(Screen.About.route) }
                        )
                    }
                }

                composable(Screen.About.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        com.ferlagod.mispodcast.ui.screens.AboutScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
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
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        val rawPodcastId = backStackEntry.arguments?.getString("podcastId").orEmpty()
                        val rawFeedUrl = backStackEntry.arguments?.getString("feedUrl").orEmpty()

                        val podcastId = if (rawPodcastId.contains("%")) {
                            try { java.net.URLDecoder.decode(rawPodcastId, "UTF-8") } catch (_: Exception) { rawPodcastId }
                        } else rawPodcastId

                        val feedUrl = if (rawFeedUrl.contains("%")) {
                            try { java.net.URLDecoder.decode(rawFeedUrl, "UTF-8") } catch (_: Exception) { rawFeedUrl }
                        } else rawFeedUrl

                        PodcastDetailScreen(
                            podcastId = podcastId,
                            feedUrl = feedUrl,
                            podcastViewModel = podcastViewModel,
                            playerViewModel = playerViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
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
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
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
        }
    }
}
