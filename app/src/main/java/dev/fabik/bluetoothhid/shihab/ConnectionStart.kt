package dev.fabik.bluetoothhid.shihab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.Dropdown
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberEnumPreference

/**
 * The first screen: how the scans leave the phone. Bluetooth (the HID keyboard, or RFCOMM when set), the PC
 * receiver by Wi-Fi or cable, or direct reading into the history with nothing sent. The last choice is marked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStart(onChoose: (ConnectionMode) -> Unit) {
    val mode by rememberEnumPreference(PreferenceStore.CONNECTION_MODE)
    val isBluetooth = mode == ConnectionMode.HID || mode == ConnectionMode.RFCOMM

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { Dropdown() }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.shihab_start_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                StartOption(
                    Icons.Default.Bluetooth,
                    stringResource(R.string.shihab_start_bluetooth),
                    stringResource(R.string.shihab_start_bluetooth_desc),
                    selected = isBluetooth
                ) { onChoose(if (isBluetooth) mode else ConnectionMode.HID) }

                StartOption(
                    Icons.Default.Wifi,
                    stringResource(R.string.shihab_start_wifi),
                    stringResource(R.string.shihab_start_wifi_desc),
                    selected = mode == ConnectionMode.PC
                ) { onChoose(ConnectionMode.PC) }

                StartOption(
                    Icons.Default.QrCodeScanner,
                    stringResource(R.string.shihab_start_local),
                    stringResource(R.string.shihab_start_local_desc),
                    selected = mode == ConnectionMode.LOCAL
                ) { onChoose(ConnectionMode.LOCAL) }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.shihab_signature),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StartOption(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val colors = MaterialTheme.colorScheme

    ElevatedCard(
        onClick = onClick,
        interactionSource = interaction,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                role = Role.Button
                this.selected = selected
            }
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.surface else colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}
