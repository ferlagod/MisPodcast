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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.alerts.AlertKind

/** Contenido de un Snackbar de aviso: igual que uno normal, más el tipo para elegir su icono. */
class AlertSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    val kind: AlertKind
) : SnackbarVisuals {
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

/**
 * Snackbar del host global. Los avisos ([AlertSnackbarVisuals]) llevan un icono discreto que indica
 * de un vistazo qué ha fallado; el resto (deshacer, errores de reproducción) se muestra como siempre.
 */
@Composable
fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val visuals = data.visuals as? AlertSnackbarVisuals
    if (visuals == null) {
        Snackbar(snackbarData = data, modifier = modifier)
        return
    }
    Snackbar(
        modifier = modifier,
        action = visuals.actionLabel?.let { label ->
            {
                TextButton(onClick = { data.performAction() }) {
                    Text(label, color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        },
        dismissAction = {
            IconButton(onClick = { data.dismiss() }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_dismiss_alert))
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = visuals.kind.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inversePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(visuals.message, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val AlertKind.icon: ImageVector
    get() = when (this) {
        AlertKind.DOWNLOAD -> Icons.Outlined.FileDownloadOff
        AlertKind.SYNC -> Icons.Outlined.SyncProblem
        AlertKind.FEEDS -> Icons.Outlined.CloudOff
    }
