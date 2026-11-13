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

import androidx.compose.material.icons.filled.Swipe
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ferlagod.mispodcast.ui.viewmodel.PodcastViewModel
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
import com.ferlagod.mispodcast.alerts.AlertAction
import com.ferlagod.mispodcast.alerts.AppAlerts
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
    podcastViewModel: PodcastViewModel = viewModel(),
    intentPodcastId: String? = null,
    intentFeedUrl: String? = null,
    onIntentHandled: () -> Unit = {},
    initialRoute: String? = null,
    onInitialRouteHandled: () -> Unit = {}
) {
    // Destino elegido en la pantalla de bienvenida (Explorar, Añadir feed, Ajustes…)
    androidx.compose.runtime.LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            navController.navigate(initialRoute) { launchSingleTop = true }
            onInitialRouteHandled()
        }
    }

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
    val newReleasesCount = podcastViewModel.newReleases.collectAsState().value.size

    // Snackbar global con «Deshacer» para las acciones rápidas de deslizar de cualquier pestaña
    val snackbarHostState = remember { SnackbarHostState() }
    val undoableAction by podcastViewModel.undoableAction.collectAsState()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)
    androidx.compose.runtime.LaunchedEffect(undoableAction?.id) {
        val action = undoableAction ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = action.message.asString(context),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            podcastViewModel.undoAction(action.id)
        } else {
            podcastViewModel.dismissUndoableAction(action.id)
        }
    }

    // Errores de reproducción (sin red, episodio no disponible…) con opción de reintentar
    val playbackError by playerViewModel.playbackError.collectAsState()
    val retryLabel = stringResource(R.string.retry)
    androidx.compose.runtime.LaunchedEffect(playbackError?.id) {
        val error = playbackError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = error.message.asString(context),
            actionLabel = retryLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            playerViewModel.retryPlayback()
        } else {
            playerViewModel.dismissPlaybackError(error.id)
        }
    }

    // Avisos de fallos lejos de la pantalla actual (descargas, sincronización, feeds); ver AppAlerts
    val appAlert by AppAlerts.current.collectAsState()
    val settingsLabel = stringResource(R.string.alert_action_settings)
    androidx.compose.runtime.LaunchedEffect(appAlert?.id) {
        val alert = appAlert ?: return@LaunchedEffect
        val actionLabel = when (alert.action) {
            null -> null
            AlertAction.OpenSyncSettings -> settingsLabel
            else -> retryLabel
        }
        val result = snackbarHostState.showSnackbar(
            AlertSnackbarVisuals(alert.message.asString(context), actionLabel, alert.kind)
        )
        AppAlerts.dismiss(alert.id)
        if (result == SnackbarResult.ActionPerformed) {
            when (val action = alert.action) {
                is AlertAction.RetryDownloads -> podcastViewModel.retryDownloads(action.episodeIds)
                AlertAction.RetrySync -> podcastViewModel.syncWithNextcloud(debounceMs = 0L)
                AlertAction.RetryFeeds -> podcastViewModel.refreshAllPodcasts()
                AlertAction.OpenSyncSettings -> navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                null -> Unit
            }
        }
    }

    val bottomTabRoutes = remember {
        setOf(
            Screen.Home.route,
            Screen.Queue.route,
            Screen.NewReleases.route,
            Screen.Subscriptions.route
        )
    }

    // Cancelar y eliminar descargas desde cualquier botón de descarga (ver EpisodeDownloadButton)
    val playingWhileDeleteText = stringResource(R.string.delete_download_playing)
    val downloadActions = remember(podcastViewModel, playerViewModel, playingWhileDeleteText) {
        DownloadActions(
            cancel = { id -> podcastViewModel.cancelDownload(id) },
            delete = { id -> podcastViewModel.deleteDownload(id) },
            // ExoPlayer lee el archivo del episodio cargado (aunque esté en pausa): borrarlo rompería
            // la reproducción al reanudar o al saltar
            canDelete = { id -> playerViewModel.currentEpisode.value?.id != id },
            onDeleteBlocked = {
                android.widget.Toast.makeText(context, playingWhileDeleteText, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalDownloadActions provides downloadActions) {
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
                            @Suppress("RestrictedApi") // No hay API pública para leer la pila completa
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
                        val badgeCount = if (item.route == Screen.NewReleases.route) newReleasesCount else 0
                        if (badgeCount > 0) {
                            val badgeDescription = stringResource(R.string.cd_new_episodes_count, badgeCount)
                            BadgedBox(
                                badge = {
                                    Badge(modifier = Modifier.semantics { contentDescription = badgeDescription }) {
                                        Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = stringResource(item.titleRes)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = stringResource(item.titleRes)
                            )
                        }
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
                    playerViewModel = playerViewModel,
                    podcastViewModel = podcastViewModel
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    snackbar = { data -> AppSnackbar(data) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
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
                Column {
                    OneTimeHint(
                        hintId = HintIds.MINI_PLAYER_GESTURES,
                        text = stringResource(R.string.hint_mini_player_gestures),
                        icon = Icons.Filled.Swipe,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
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
    }
}
