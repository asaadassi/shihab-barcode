package dev.fabik.bluetoothhid.shihab

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.shihab.shared.ShihabLinks
import app.shihab.shared.diag.ShihabLog
import app.shihab.shared.locale.ShihabLang
import app.shihab.shared.ui.ShihabAbout
import app.shihab.shared.update.ShihabUpdater
import dev.fabik.bluetoothhid.BuildConfig
import dev.fabik.bluetoothhid.MainActivity
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.ButtonPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Shihab items of the About section in Settings: About (shared window, with the GPL notice and licence),
 * language, update check (GitHub build only: Google Play updates the app itself), problem report, and the
 * source code this app is built from.
 */
object BarcodeAbout {
    private const val ASSET_PREFIX = "ShihabBarcode-android"

    @Volatile private var updaterInstance: ShihabUpdater? = null

    private fun updater(ctx: Context): ShihabUpdater = updaterInstance ?: ShihabUpdater(
        repo = BuildConfig.RELEASES_REPO,
        assetPrefix = ASSET_PREFIX,
        currentVersion = BuildConfig.VERSION_NAME,
        userAgent = "ShihabBarcode-Android/${BuildConfig.VERSION_NAME}",
        log = { evt, detail, e ->
            val err = when (evt) {
                "update_check" -> BarcodeErrors.UPDATE_CHECK
                "update_rejected" -> BarcodeErrors.UPDATE_REJECTED
                "update_download", "update_install" -> BarcodeErrors.UPDATE_DOWNLOAD
                else -> null
            }
            if (err == null) ShihabLog.info(evt, mapOf("detail" to detail))
            else ShihabLog.error(err.code, listOfNotNull(evt, detail).joinToString(": "), e)
        },
        prefs = ctx.applicationContext.getSharedPreferences("update", Context.MODE_PRIVATE),
    ).also { updaterInstance = it }

    /** The settings items, in the order they are shown; the source and licence links (also in About) are advanced. */
    fun items(advanced: Boolean): List<@Composable () -> Unit> = buildList {
        add { AboutItem() }
        add { LanguageItem() }
        if (BuildConfig.SELF_UPDATE) add { UpdateItem() }
        add { ReportItem() }
        if (advanced) add { SourceItem() }
    }

    @Composable
    private fun AboutItem() {
        val ctx = LocalContext.current
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        var showLicense by remember { mutableStateOf(false) }
        val licenseName = stringResource(R.string.shihab_license_title)
        ButtonPreference(
            title = stringResource(R.string.shihab_about),
            desc = stringResource(R.string.shihab_signature),
            icon = Icons.Default.Info,
        ) {
            val activity = ctx.findActivity() ?: return@ButtonPreference
            ShihabAbout.show(
                activity,
                ShihabAbout.Info(
                    productName = ctx.getString(R.string.app_name),
                    productDescription = ctx.getString(R.string.shihab_product_description),
                    version = BuildConfig.VERSION_NAME,
                    documents = listOf(licenseName to { showLicense = true }),
                ),
                BarcodeBrand.palette(ctx, dark),
            )
        }
        if (showLicense) LicenseDialog { showLicense = false }
    }

    /** The GPL notice (original author kept), then the full licence text bundled with the app. */
    @Composable
    private fun LicenseDialog(onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val license = remember {
            runCatching { ctx.assets.open("legal/LICENSE.txt").bufferedReader().use { it.readText() } }
                .onFailure { ShihabLog.error("CMN-001", "license asset missing", it) }
                .getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.shihab_license_title)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(R.string.shihab_license_notice, BuildConfig.UPSTREAM_VERSION),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // The official licence text is English: left to right, so its punctuation stays in place.
                    Text(
                        license,
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.shihab_close)) } },
            dismissButton = {
                TextButton(onClick = { uriHandler.openUri(BuildConfig.SOURCE_URL) }) {
                    Text(stringResource(R.string.shihab_source_code))
                }
            },
        )
    }

    @Composable
    private fun LanguageItem() {
        val ctx = LocalContext.current
        val arabic = remember { ShihabLang.isArabic(ctx) }
        ButtonPreference(
            title = stringResource(R.string.shihab_language),
            desc = if (arabic) "العربية" else "English",
            icon = Icons.Default.Language,
        ) {
            ShihabLang.set(ctx, if (arabic) ShihabLang.ENGLISH else ShihabLang.ARABIC)
            // Every open screen is rebuilt in the new language and direction.
            ctx.startActivity(Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }

    private sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Available(val release: ShihabUpdater.Release) : UpdateState()
        data class Downloading(val percent: Int) : UpdateState()
    }

    @Composable
    private fun UpdateItem() {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
        val s = state
        ButtonPreference(
            title = stringResource(R.string.shihab_update_check),
            desc = when (s) {
                UpdateState.Checking -> stringResource(R.string.shihab_update_checking)
                is UpdateState.Downloading -> stringResource(R.string.shihab_update_downloading, s.percent)
                else -> stringResource(R.string.shihab_update_check_desc)
            },
            icon = Icons.Default.SystemUpdate,
        ) {
            if (s != UpdateState.Idle) return@ButtonPreference
            state = UpdateState.Checking
            scope.launch {
                val lang = if (ShihabLang.isArabic(ctx)) "ar" else "en"
                val r = withContext(Dispatchers.IO) { updater(ctx).check(lang) }
                state = UpdateState.Idle
                when (r) {
                    is ShihabUpdater.Check.Available -> state = UpdateState.Available(r.release)
                    ShihabUpdater.Check.UpToDate -> ctx.toast(ctx.getString(R.string.shihab_update_up_to_date))
                    ShihabUpdater.Check.NotPublished -> ctx.toast(ctx.getString(R.string.shihab_update_none))
                    is ShihabUpdater.Check.Failed -> ctx.toast(BarcodeErrors.text(ctx, BarcodeErrors.UPDATE_CHECK))
                }
            }
        }
        if (s is UpdateState.Available) {
            AlertDialog(
                onDismissRequest = { state = UpdateState.Idle },
                title = { Text(stringResource(R.string.shihab_update_available, s.release.version)) },
                text = {
                    Text(
                        s.release.notes,
                        Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val release = s.release
                        state = UpdateState.Downloading(0)
                        scope.launch {
                            val failure = withContext(Dispatchers.IO) {
                                updater(ctx).downloadAndInstall(ctx, release, progress = { done, total ->
                                    if (total > 0) scope.launch { state = UpdateState.Downloading((done * 100 / total).toInt()) }
                                })
                            }
                            state = UpdateState.Idle
                            failure?.let {
                                val e = if (it == ShihabUpdater.Failure.REJECTED) BarcodeErrors.UPDATE_REJECTED
                                else BarcodeErrors.UPDATE_DOWNLOAD
                                ctx.toast(BarcodeErrors.text(ctx, e))
                            }
                        }
                    }) { Text(stringResource(R.string.shihab_update_install)) }
                },
                dismissButton = {
                    TextButton(onClick = { state = UpdateState.Idle }) { Text(stringResource(R.string.shihab_later)) }
                },
            )
        }
    }

    @Composable
    private fun ReportItem() {
        val ctx = LocalContext.current
        ButtonPreference(
            title = stringResource(R.string.report_issue),
            desc = stringResource(R.string.shihab_report_desc),
            icon = Icons.Default.BugReport,
        ) { sendReport(ctx) }
    }

    @Composable
    private fun SourceItem() {
        val uriHandler = LocalUriHandler.current
        ButtonPreference(
            title = stringResource(R.string.source_code),
            desc = stringResource(R.string.shihab_source_desc),
            icon = Icons.Default.Code,
        ) { uriHandler.openUri(BuildConfig.SOURCE_URL) }
        ButtonPreference(
            title = stringResource(R.string.shihab_license_title),
            desc = "GNU GPL v3",
            icon = Icons.Default.Gavel,
        ) { uriHandler.openUri("https://www.gnu.org/licenses/gpl-3.0.html") }
    }

    /** An email to support with the version, the phone and the last lines of the error log (nothing else). */
    private fun sendReport(ctx: Context) {
        val log = runCatching { ShihabLog.files().maxByOrNull { it.name }?.readLines()?.takeLast(40)?.joinToString("\n") }
            .onFailure { ShihabLog.error("CMN-001", "report: log read failed", it) }
            .getOrNull().orEmpty()
        val body = buildString {
            appendLine(ctx.getString(R.string.shihab_report_body_hint))
            appendLine()
            appendLine("${ctx.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.FLAVOR})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            if (log.isNotBlank()) {
                appendLine()
                appendLine(log)
            }
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${ShihabLinks.supportEmail}"))
            .putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.shihab_report_subject, BuildConfig.VERSION_NAME))
            .putExtra(Intent.EXTRA_TEXT, body)
        try {
            ctx.startActivity(intent)
            ShihabLog.info("report_opened")
        } catch (e: ActivityNotFoundException) {
            ShihabLog.error(BarcodeErrors.NO_MAIL_APP.code, "report: no mail app", e)
            ctx.toast(BarcodeErrors.text(ctx, BarcodeErrors.NO_MAIL_APP))
        }
    }

    private fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
