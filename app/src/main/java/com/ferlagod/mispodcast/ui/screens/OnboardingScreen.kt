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

package com.ferlagod.mispodcast.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.ui.navigation.Screen
import com.ferlagod.mispodcast.ui.viewmodel.SettingsViewModel

/**
 * Pantalla de primer arranque. No es un tutorial: es un único paso que lleva al usuario
 * directamente a tener contenido (importar, restaurar, explorar o añadir un feed)
 * y le permite activar los avisos de nuevos episodios explicando para qué sirven.
 *
 * @param onFinish Se invoca al terminar. Recibe la ruta a la que navegar después, o `null` para ir a Inicio.
 */
@Composable
fun OnboardingScreen(
    onFinish: (nextRoute: String?) -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val isProcessing by settingsViewModel.isProcessingBackup.collectAsState()
    val backupMessage by settingsViewModel.backupMessage.collectAsState()
    // Se activa al lanzar una importación; a partir de ahí se muestra el progreso en lugar de las opciones.
    var importStarted by rememberSaveable { mutableStateOf(false) }

    val importOpmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            importStarted = true
            settingsViewModel.importOpml(it)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            importStarted = true
            settingsViewModel.importDatabaseZip(it)
        }
    }

    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notificationsGranted by remember {
        mutableStateOf(
            !needsNotificationPermission ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }

    val forYouLabel = stringResource(R.string.category_for_you)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag("onboarding_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            if (importStarted) {
                ImportProgress(
                    isProcessing = isProcessing,
                    message = backupMessage?.asString().orEmpty(),
                    onContinue = {
                        settingsViewModel.clearBackupMessage()
                        onFinish(null)
                    },
                    onBack = {
                        settingsViewModel.clearBackupMessage()
                        importStarted = false
                    }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OnboardingOption(
                        icon = Icons.Filled.FileDownload,
                        title = stringResource(R.string.onboarding_import_opml_title),
                        description = stringResource(R.string.onboarding_import_opml_desc),
                        testTag = "onboarding_import_opml",
                        onClick = {
                            importOpmlLauncher.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "*/*"))
                        }
                    )
                    OnboardingOption(
                        icon = Icons.Filled.Restore,
                        title = stringResource(R.string.onboarding_restore_title),
                        description = stringResource(R.string.onboarding_restore_desc),
                        testTag = "onboarding_restore",
                        onClick = {
                            restoreLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/x-sqlite3",
                                    "application/vnd.sqlite3",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        }
                    )
                    OnboardingOption(
                        icon = Icons.Filled.Explore,
                        title = stringResource(R.string.onboarding_explore_title),
                        description = stringResource(R.string.onboarding_explore_desc),
                        testTag = "onboarding_explore",
                        onClick = { onFinish(Screen.CategoryPodcasts.createRoute(forYouLabel, 0)) }
                    )
                    OnboardingOption(
                        icon = Icons.Filled.Link,
                        title = stringResource(R.string.onboarding_add_url_title),
                        description = stringResource(R.string.onboarding_add_url_desc),
                        testTag = "onboarding_add_url",
                        onClick = { onFinish(Screen.AddPodcast.route) }
                    )
                    OnboardingOption(
                        icon = Icons.Filled.CloudSync,
                        title = stringResource(R.string.onboarding_nextcloud_title),
                        description = stringResource(R.string.onboarding_nextcloud_desc),
                        testTag = "onboarding_nextcloud",
                        onClick = { onFinish(Screen.Settings.route) }
                    )
                }

                if (needsNotificationPermission) {
                    Spacer(modifier = Modifier.height(20.dp))
                    NotificationCard(
                        granted = notificationsGranted,
                        onEnable = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = { onFinish(null) },
                    modifier = Modifier.testTag("onboarding_skip")
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }
    }
}

@Composable
private fun OnboardingOption(
    icon: ImageVector,
    title: String,
    description: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationCard(granted: Boolean, onEnable: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Notifications,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.onboarding_notifications_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(
                        if (granted) R.string.onboarding_notifications_enabled else R.string.onboarding_notifications_desc
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!granted) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onEnable,
                    modifier = Modifier.testTag("onboarding_enable_notifications")
                ) {
                    Text(stringResource(R.string.onboarding_notifications_enable))
                }
            }
        }
    }
}

@Composable
private fun ImportProgress(
    isProcessing: Boolean,
    message: String,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isProcessing) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = message.ifBlank { stringResource(R.string.onboarding_importing) },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (!isProcessing) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onContinue, modifier = Modifier.testTag("onboarding_continue")) {
                    Text(stringResource(R.string.onboarding_continue))
                }
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.onboarding_other_option))
                }
            }
        }
    }
}
