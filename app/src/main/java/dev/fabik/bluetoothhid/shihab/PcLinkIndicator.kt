package dev.fabik.bluetoothhid.shihab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import app.shihab.shared.locale.ShihabLang
import dev.fabik.bluetoothhid.ElevatedWarningCard
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.LocalNavigation
import dev.fabik.bluetoothhid.ui.Routes

/**
 * The scanner's status card in PC mode: not paired yet, reaching the PC (with the reason when it failed), or
 * connected by cable or Wi-Fi. A tap opens the pairing screen.
 */
@Composable
fun PcLinkIndicator(link: PcLink) {
    val state by link.state.collectAsStateWithLifecycle()
    val navigation = LocalNavigation.current
    val arabic = ShihabLang.isArabic(LocalContext.current)
    // Names (often Latin) isolated left to right inside the Arabic line.
    fun name(n: String) = "⁦$n⁩"
    val (message, sub) = when (val s = state) {
        PcLink.State.NotPaired ->
            stringResource(R.string.shihab_pc_not_paired) to stringResource(R.string.shihab_pc_not_paired_desc)
        is PcLink.State.Connecting -> stringResource(R.string.shihab_pc_connecting, name(s.pcName)) to
            (s.problem?.let { BarcodeErrors.of(it) }?.let { "${if (arabic) it.ar else it.en}  · ${it.code}" }
                ?: stringResource(R.string.shihab_pc_connecting_desc))
        is PcLink.State.Connected ->
            stringResource(R.string.shihab_pc_connected, name(s.pcName)) to
                stringResource(if (s.cable) R.string.shihab_pc_via_cable else R.string.shihab_pc_via_wifi)
    }
    ElevatedWarningCard(
        message = message,
        subMessage = sub,
        onClick = { navigation.navigate("${Routes.Pair}?manage=true") { launchSingleTop = true } },
        visible = true
    )
}
