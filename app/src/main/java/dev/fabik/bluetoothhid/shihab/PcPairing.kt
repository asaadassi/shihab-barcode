package dev.fabik.bluetoothhid.shihab

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shihab.shared.diag.ShihabLog
import app.shihab.shared.locale.ShihabLang
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.RequiresCameraPermission
import kotlinx.coroutines.delay
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors

/**
 * Pairs the phone with Barcode Receiver before any scan in PC mode: the local network permission (Android 17),
 * the pairing QR code read here with its own camera (never typed nor kept in the history), then a real connection
 * to the PC. Scanning starts once the PC answers; when it does not, the reason and its fix are shown.
 * [manage]: opened from the scanner's PC card, so it does not leave on its own once connected.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PcPairing(link: PcLink, manage: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val state by link.state.collectAsStateWithLifecycle()
    var scanning by rememberSaveable { mutableStateOf(link.load() == null) }
    var badCode by remember { mutableStateOf(false) }

    val needsPermission = PcLink.needsLocalNetworkPermission()
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberPermissionState(PcLink.LOCAL_NETWORK_PERMISSION) { granted ->
        if (granted) link.retry()
    }
    val allowed = !needsPermission || permission.status.isGranted

    LaunchedEffect(Unit) {
        if (!allowed) {
            asked = true
            permission.launchPermissionRequest()
        }
    }

    LaunchedEffect(state, manage) {
        if (!manage && state is PcLink.State.Connected) {
            delay(900)   // the "connected" mark is seen before the scanner opens
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (manage) R.string.shihab_pair_manage else R.string.shihab_pair_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
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
                if (!allowed) {
                    StepCard(
                        Icons.Default.WifiFind,
                        stringResource(R.string.shihab_pair_permission),
                        stringResource(R.string.shihab_pair_permission_desc),
                        warn = true
                    ) {
                        FilledTonalButton(onClick = {
                            // Asked once and refused: Android shows no prompt again, the app's settings do.
                            if (asked && !permission.status.isGranted) {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                )
                            } else {
                                asked = true
                                permission.launchPermissionRequest()
                            }
                        }) {
                            Text(stringResource(if (asked) R.string.shihab_pair_open_settings else R.string.shihab_pair_allow))
                        }
                    }
                }

                if (scanning) {
                    Text(
                        stringResource(R.string.shihab_pair_scan_hint),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        RequiresCameraPermission {
                            PairingCamera { text ->
                                if (!text.startsWith(PcLink.PREFIX)) {
                                    badCode = true
                                } else if (link.pair(text) != null) {
                                    badCode = false
                                    scanning = false
                                } else {
                                    badCode = true
                                }
                            }
                        }
                    }
                    AnimatedVisibility(badCode, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            stringResource(R.string.shihab_pair_not_code),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (link.load() != null) {
                        TextButton(onClick = { scanning = false }) {
                            Text(stringResource(R.string.shihab_close))
                        }
                    }
                } else {
                    LinkStatus(
                        state,
                        onRetry = { link.retry() },
                        onRescan = { scanning = true },
                        onDone = onDone
                    )
                }
            }
        }
    }
}

/** The paired PC and how the link stands: reaching it, connected, or why it failed and what to do. */
@Composable
private fun LinkStatus(state: PcLink.State, onRetry: () -> Unit, onRescan: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    // Names (often Latin) isolated left to right inside the Arabic line.
    fun name(n: String) = "⁦$n⁩"

    when (state) {
        PcLink.State.NotPaired -> Unit

        is PcLink.State.Connected -> StepCard(
            Icons.Default.CheckCircle,
            stringResource(R.string.shihab_pc_connected, name(state.pcName)),
            stringResource(if (state.cable) R.string.shihab_pc_via_cable else R.string.shihab_pc_via_wifi)
        ) {
            Button(onClick = onDone) { Text(stringResource(R.string.shihab_pair_start)) }
            Box(Modifier.weight(1f))
            IconAction(Icons.Default.QrCodeScanner, stringResource(R.string.shihab_pair_rescan), onRescan)
        }

        is PcLink.State.Connecting -> {
            val problem = state.problem
            val error = problem?.let { BarcodeErrors.of(it) }
            val arabic = ShihabLang.isArabic(context)
            StepCard(
                if (error == null) Icons.Default.Computer else Icons.Default.WarningAmber,
                if (error == null) stringResource(R.string.shihab_pc_connecting, name(state.pcName))
                else if (arabic) error.ar else error.en,
                if (error == null) stringResource(R.string.shihab_pc_connecting_desc)
                else "${if (arabic) error.fixAr else error.fixEn}  · ${error.code}",
                warn = error != null,
                busy = error == null
            ) {
                FilledTonalButton(onClick = onDone) {
                    Text(stringResource(R.string.shihab_pair_continue))
                }
                Box(Modifier.weight(1f))
                IconAction(Icons.Default.Refresh, stringResource(R.string.shihab_pair_retry), onRetry)
                IconAction(Icons.Default.QrCodeScanner, stringResource(R.string.shihab_pair_rescan), onRescan)
            }
        }
    }
}

@Composable
private fun StepCard(
    icon: ImageVector,
    title: String,
    desc: String,
    warn: Boolean = false,
    busy: Boolean = false,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (warn) colors.errorContainer else colors.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    else Icon(icon, null, tint = if (warn) colors.error else colors.primary, modifier = Modifier.size(28.dp))
                }
                Column(
                    Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        color = if (warn) colors.onErrorContainer else colors.onSurface)
                    Text(desc, style = MaterialTheme.typography.bodySmall,
                        color = if (warn) colors.onErrorContainer else colors.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
            Icon(icon, null)
        }
    }
}

/**
 * The back camera reading QR codes only, for the pairing code. Its own small reader: the scanner's settings
 * (formats, regex, scan delay, duplicates, history) do not apply, so a pairing code is always read and never kept.
 */
@Composable
private fun PairingCamera(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val reader = BarcodeReader(BarcodeReader.Options().apply { formats = setOf(BarcodeReader.Format.QR_CODE) })
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        var last = ""
        analysis.setAnalyzer(executor) { image ->
            val text = image.use { runCatching { reader.read(it) }.getOrNull()?.firstOrNull()?.text }
            // The same code stays in view for many frames: report it once.
            if (!text.isNullOrEmpty() && text != last) {
                last = text
                main.execute { currentOnCode(text) }
            }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching { future.get().bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
                .onFailure { ShihabLog.info("pair_camera_failed", mapOf("error" to it.javaClass.simpleName)) }
        }, main)

        onDispose {
            if (future.isDone) runCatching { future.get().unbind(preview, analysis) }
                .onFailure { ShihabLog.info("pair_camera_unbind", mapOf("error" to it.javaClass.simpleName)) }
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }

    AndroidView({ previewView }, Modifier.fillMaxSize())
}
