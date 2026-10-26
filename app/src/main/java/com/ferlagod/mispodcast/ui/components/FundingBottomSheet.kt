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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ferlagod.mispodcast.R
import com.ferlagod.mispodcast.data.model.ValueForValueConfig
import com.ferlagod.mispodcast.data.model.ValueRecipient
import com.ferlagod.mispodcast.ui.theme.AccentRed

private val LightningAmber = Color(0xFFF7931A)

/**
 * Hoja modal inferior para apoyar a los creadores de un podcast mediante enlaces tradicionales
 * de financiación (Patreon, Ko-fi, PayPal, etc.) o a través de Value4Value en la red Lightning
 * de Bitcoin (Podcasting 2.0).
 *
 * @param podcastTitle Nombre del podcast.
 * @param podcastArtwork URL de la carátula o ilustración del podcast.
 * @param fundingUrl URL de mecenazgo o donación tradicional (etiqueta <podcast:funding>).
 * @param fundingText Texto o nota personalizada del creador solicitando apoyo.
 * @param valueForValueRaw Configuración serializada o parámetros de <podcast:value>.
 * @param onDismissRequest Callback invocado para cerrar la hoja modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundingBottomSheet(
    podcastTitle: String,
    podcastArtwork: String = "",
    fundingUrl: String? = null,
    fundingText: String? = null,
    valueForValueRaw: String? = null,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val v4vConfig = remember(valueForValueRaw) {
        ValueForValueConfig.fromString(valueForValueRaw)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Cabecera superior
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (podcastArtwork.isNotBlank()) {
                        AsyncImage(
                            model = podcastArtwork,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AccentRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.support_podcast_title, podcastTitle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.support_podcast_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.btn_close)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mensaje o cita del creador si existe
            if (!fundingText.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.support_creator_message),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = fundingText.trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sección 1: Enlace tradicional de financiación (Patreon, Ko-fi, PayPal, etc.)
            if (!fundingUrl.isNullOrBlank()) {
                TraditionalFundingCard(
                    fundingUrl = fundingUrl,
                    onOpenUrl = { url ->
                        openUrlInBrowser(context, url)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sección 2: Value4Value en Lightning Network (Podcasting 2.0)
            if (v4vConfig != null && v4vConfig.recipients.isNotEmpty()) {
                ValueForValueCard(
                    config = v4vConfig,
                    onOpenLightningWallet = { address ->
                        openLightningWallet(context, address)
                    },
                    onCopyAddress = { address ->
                        copyToClipboard(context, address)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Si no hay datos específicos de financiación, mostrar tarjeta genérica
            if (fundingUrl.isNullOrBlank() && (v4vConfig == null || v4vConfig.recipients.isEmpty())) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Este podcast aún no ha configurado enlaces directos de Value4Value o financiación en su feed RSS.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Tarjeta para enlaces de mecenazgo tradicionales con detección inteligente de plataforma.
 */
@Composable
private fun TraditionalFundingCard(
    fundingUrl: String,
    onOpenUrl: (String) -> Unit
) {
    val platformName = remember(fundingUrl) {
        val lower = fundingUrl.lowercase()
        when {
            lower.contains("patreon.com") -> "Patreon"
            lower.contains("buymeacoffee.com") -> "Buy Me a Coffee"
            lower.contains("ko-fi.com") -> "Ko-fi"
            lower.contains("paypal.me") || lower.contains("paypal.com") -> "PayPal"
            lower.contains("liberapay.com") -> "Liberapay"
            lower.contains("github.com") -> "GitHub Sponsors"
            lower.contains("subscribestar.com") -> "SubscribeStar"
            lower.contains("gofundme.com") -> "GoFundMe"
            else -> "Sitio web de mecenazgo"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.support_traditional_funding),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = platformName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { onOpenUrl(fundingUrl) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.support_open_link),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Tarjeta enriquecida para Value4Value en Lightning Network con lista de beneficiarios y reparto.
 */
@Composable
private fun ValueForValueCard(
    config: ValueForValueConfig,
    onOpenLightningWallet: (String) -> Unit,
    onCopyAddress: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(LightningAmber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lightning),
                            contentDescription = null,
                            tint = LightningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.support_v4v_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Podcasting 2.0 (${config.method.uppercase()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = LightningAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.support_v4v_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!config.suggested.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = LightningAmber.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(R.string.support_suggested_amount, config.suggested),
                        style = MaterialTheme.typography.labelSmall,
                        color = LightningAmber,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Lista de destinatarios del reparto de sats
            config.recipients.forEachIndexed { index, recipient ->
                RecipientItem(
                    recipient = recipient,
                    onOpenWallet = onOpenLightningWallet,
                    onCopy = onCopyAddress
                )
                if (index < config.recipients.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

/**
 * Fila para cada destinatario del Value4Value (split de porcentajes y botones de acción).
 */
@Composable
private fun RecipientItem(
    recipient: ValueRecipient,
    onOpenWallet: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val displayName = recipient.name?.ifBlank { null } ?: "Creador"
    val isLnAddress = recipient.type.equals("lnaddress", ignoreCase = true) || recipient.address.contains("@")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${recipient.split}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isLnAddress) recipient.address else truncateKey(recipient.address),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onOpenWallet(recipient.address) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lightning),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = LightningAmber
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.support_open_lightning_wallet),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            OutlinedButton(
                onClick = { onCopy(recipient.address) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.support_copy_address),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Trunca una clave pública de nodo o factura larga para visualización limpia.
 */
private fun truncateKey(key: String): String {
    return if (key.length > 20) {
        "${key.take(10)}...${key.takeLast(8)}"
    } else key
}

/**
 * Abre una URL externa en el navegador predeterminado del sistema.
 */
private fun openUrlInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Abre una dirección Lightning mediante el protocolo nativo `lightning:`.
 */
private fun openLightningWallet(context: Context, address: String) {
    try {
        val uriString = if (address.startsWith("lightning:", ignoreCase = true)) {
            address
        } else {
            "lightning:$address"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback: copiar al portapapeles y avisar al usuario
        copyToClipboard(context, address)
        Toast.makeText(
            context,
            context.getString(R.string.support_no_wallet_found),
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Copia un texto o dirección al portapapeles del sistema.
 */
private fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Lightning Address", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            context.getString(R.string.support_address_copied),
            Toast.LENGTH_SHORT
        ).show()
    } catch (_: Exception) {}
}
