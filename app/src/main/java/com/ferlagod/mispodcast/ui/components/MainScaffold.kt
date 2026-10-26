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

package com.ferlagod.mispodcast.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.ui.navigation.NavGraph
import com.ferlagod.mispodcast.ui.navigation.Screen
import com.ferlagod.mispodcast.ui.navigation.moreMenuItems
import com.ferlagod.mispodcast.ui.viewmodel.PlayerViewModel

/**
 * Contenedor principal de la interfaz de usuario de Mis Podcast.
 *
 * Integra:
 * - La barra de navegación inferior ([NavigationBar]) con acceso a Inicio, Cola, Novedades, Suscripciones y Más.
 * - El mini-reproductor flotante animado [MiniPlayer].
 * - El menú emergente de opciones secundarias (Ajustes, Estadísticas, Historial, Favoritos, Acerca de).
 * - El host de navegación [NavGraph] con soporte para navegación directa desde notificaciones o intents.
 */
@Composable
fun MainScaffold(
    navController: NavHostController = rememberNavController(),
    playerViewModel: PlayerViewModel = viewModel(),
    intentPodcastId: String? = null,
    intentFeedUrl: String? = null,
    onIntentHandled: () -> Unit = {}
) {
    androidx.compose.runtime.LaunchedEffect(intentPodcastId, intentFeedUrl) {
        if (!intentPodcastId.isNullOrBlank() || !intentFeedUrl.isNullOrBlank()) {
            navController.navigate(Screen.PodcastDetail.createRoute(intentPodcastId ?: "", intentFeedUrl ?: "")) {
                launchSingleTop = true
            }
            onIntentHandled()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    var showMoreMenu by remember { mutableStateOf(false) }

    val currentEpisode by playerViewModel.currentEpisode.collectAsState()

    val bottomTabRoutes = remember {
        setOf(
            Screen.Home.route,
            Screen.Queue.route,
            Screen.NewReleases.route,
            Screen.Subscriptions.route
        )
    }

    NavigationSuiteScaffold(
        modifier = Modifier.fillMaxSize(),
        navigationSuiteItems = {
            // 1. Inicio, 2. Cola, 3. Novedades, 4. Suscripciones
            Screen.bottomNavTabs.filterNotNull().forEach { item ->
                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                val tabTestTag = when (item.route) {
                    Screen.Home.route -> "nav_item_home"
                    Screen.Queue.route -> "nav_item_queue"
                    Screen.NewReleases.route -> "nav_item_new_releases"
                    Screen.Subscriptions.route -> "nav_item_subscriptions"
                    else -> "nav_item_${item.route}"
                }
                item(
                    selected = selected,
                    onClick = {
                        val isCurrentTab = currentRoute == item.route
                        if (!isCurrentTab) {
                            val isSubscreen = currentRoute !in bottomTabRoutes
                            val backStack = navController.currentBackStack.value
                            val originTabRoute = backStack.asReversed()
                                .mapNotNull { it.destination.route }
                                .firstOrNull { it in bottomTabRoutes }

                            if (isSubscreen && originTabRoute == item.route) {
                                val popped = navController.popBackStack(route = item.route, inclusive = false)
                                if (!popped) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            } else {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            }
                        }
                    },
                    icon = {
                        val iconVector = if (selected) item.selectedIcon else (item.unselectedIcon ?: item.selectedIcon)
                        Icon(
                            imageVector = iconVector,
                            contentDescription = stringResource(item.titleRes)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(item.titleRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag(tabTestTag)
                )
            }

            // 5. Botón de 3 puntos "Más" con menú emergente DropdownMenu
            item(
                selected = showMoreMenu,
                onClick = { showMoreMenu = true },
                icon = {
                    Box {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.nav_more_options)
                        )
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("more_options_dropdown_menu")
                        ) {
                            moreMenuItems.forEach { item ->
                                DropdownMenuItem(
                                    modifier = Modifier.testTag("menu_item_${item.route}"),
                                    text = {
                                        Text(
                                            text = stringResource(item.titleRes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = stringResource(item.titleRes),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        navController.navigate(item.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_more),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.testTag("more_options_button")
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                NavGraph(
                    navController = navController,
                    playerViewModel = playerViewModel
                )
            }

            // MiniPlayer persistente animado en la parte inferior del área de contenido
            AnimatedVisibility(
                visible = currentEpisode != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 250)
                ) + fadeOut(animationSpec = tween(durationMillis = 250))
            ) {
                MiniPlayer(
                    playerViewModel = playerViewModel,
                    onNavigateToQueue = {
                        navController.navigate(Screen.Queue.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
