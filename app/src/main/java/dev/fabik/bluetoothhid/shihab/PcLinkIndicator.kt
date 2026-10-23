package dev.fabik.bluetoothhid.shihab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.ElevatedWarningCard
import dev.fabik.bluetoothhid.R

/** The scanner's status card in PC mode: not paired yet, reaching the PC, or connected by cable or Wi-Fi. */
@Composable
fun PcLinkIndicator(link: PcLink) {
    val state by link.state.collectAsStateWithLifecycle()
    // Names (often Latin) isolated left to right inside the Arabic line.
    fun name(n: String) = "⁦$n⁩"
    val (message, sub) = when (val s = state) {
        PcLink.State.NotPaired ->
            stringResource(R.string.shihab_pc_not_paired) to stringResource(R.string.shihab_pc_not_paired_desc)
        is PcLink.State.Connecting ->
            stringResource(R.string.shihab_pc_connecting, name(s.pcName)) to stringResource(R.string.shihab_pc_connecting_desc)
        is PcLink.State.Connected ->
            stringResource(R.string.shihab_pc_connected, name(s.pcName)) to
                stringResource(if (s.cable) R.string.shihab_pc_via_cable else R.string.shihab_pc_via_wifi)
    }
    ElevatedWarningCard(message = message, subMessage = sub, onClick = {}, visible = true)
}
