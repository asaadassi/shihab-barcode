package dev.fabik.bluetoothhid

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HdrAuto
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VideoStable
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.shihab.BarcodeAbout
import dev.fabik.bluetoothhid.ui.AdvancedOptionsModal
import dev.fabik.bluetoothhid.ui.ButtonPreference
import dev.fabik.bluetoothhid.ui.CheckBoxPreference
import dev.fabik.bluetoothhid.ui.ComboBoxEnumPreference
import dev.fabik.bluetoothhid.ui.CustomKeysDialog
import dev.fabik.bluetoothhid.ui.ExternalPluginsModal
import dev.fabik.bluetoothhid.ui.JavaScriptOption
import dev.fabik.bluetoothhid.ui.ProfileManageDialog
import dev.fabik.bluetoothhid.ui.SaveScanImageOptionsModal
import dev.fabik.bluetoothhid.ui.SliderPreference
import dev.fabik.bluetoothhid.ui.SwitchPreference
import dev.fabik.bluetoothhid.ui.TextBoxPreference
import dev.fabik.bluetoothhid.ui.VolumeKeyOptionsModal
import dev.fabik.bluetoothhid.ui.profileDisplayName
import dev.fabik.bluetoothhid.ui.rememberDialogState
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.LocalDataStore
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.ProfileManager
import dev.fabik.bluetoothhid.utils.rememberEnumPreference
import dev.fabik.bluetoothhid.utils.rememberPreference
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val strings = remember {
        SettingsStrings(context)
    }

    val connectionMode by rememberEnumPreference(PreferenceStore.CONNECTION_MODE)
    val isHidOnly = connectionMode == ConnectionMode.HID
    val isExternal = connectionMode == ConnectionMode.EXTERNAL

    // Shihab: the simple screen by default; the upstream options stay one switch away.
    var advanced by rememberPreference(PreferenceStore.SHOW_ADVANCED)

    val profileSettings = remember(advanced) { buildProfileSettings(advanced) }
    val connectionSettings = remember(isHidOnly, isExternal, advanced) {
        buildConnectionSettings(strings, isHidOnly, isExternal, advanced)
    }
    val appearanceSettings = remember(advanced) { buildAppearanceSettings(strings, advanced) }
    val cameraSettings = remember(advanced) { buildCameraSettings(strings, advanced) }
    val scannerSettings =
        remember(isHidOnly, advanced) { buildScannerSettings(strings, isHidOnly, context, scope, advanced) }
    val aboutSettings = remember(advanced) { buildAboutSettings(strings, context, uriHandler, scope, advanced) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp, 0.dp)
    ) {
        if (profileSettings.isNotEmpty()) {
            item { SectionTitle(strings[R.string.profiles], true) }
            items(profileSettings) { it() }
        }

        item { SectionTitle(strings[R.string.connection], profileSettings.isEmpty()) }
        items(connectionSettings) { it() }

        item { SectionTitle(strings[R.string.appearance]) }
        items(appearanceSettings) { it() }

        if (cameraSettings.isNotEmpty()) {
            item { SectionTitle(strings[R.string.camera]) }
            items(cameraSettings) { it() }
        }

        item { SectionTitle(strings[R.string.scanner]) }
        items(scannerSettings) { it() }

        item { SectionTitle(strings[R.string.about]) }
        items(aboutSettings) { it() }

        item { Spacer(Modifier.height(16.dp)) }
        item {
            Box(Modifier.clip(RoundedCornerShape(16.dp))) {
                SwitchPreference(
                    title = strings[R.string.shihab_advanced],
                    desc = strings[R.string.shihab_advanced_desc],
                    icon = Icons.Default.Tune,
                    checked = advanced,
                ) { advanced = it }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun SectionTitle(text: String, isStart: Boolean = false) {
    Spacer(Modifier.height(if (isStart) 8.dp else 16.dp))
    Text(
        text,
        Modifier.padding(start = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall
    )
    Spacer(Modifier.height(4.dp))
}

internal fun buildProfileSettings(advanced: Boolean): List<@Composable () -> Unit> = buildSettings(advanced) {
    addAdvanced {
        val activeProfile by ProfileManager.activeProfile.collectAsStateWithLifecycle()
        val dialogState = rememberDialogState()

        ProfileManageDialog(dialogState)

        ButtonPreference(
            title = profileDisplayName(activeProfile),
            desc = stringResource(R.string.active_profile_desc),
            icon = Icons.Default.Layers,
            onClick = dialogState::open
        )
    }
}

internal fun buildConnectionSettings(
    strings: SettingsStrings,
    isHidOnly: Boolean,
    isExternal: Boolean,
    advanced: Boolean
): List<@Composable () -> Unit> = buildSettings(advanced) {
    add {
        ComboBoxEnumPreference(
            title = strings[R.string.connection_mode],
            desc = strings[R.string.connection_mode_desc],
            icon = Icons.Default.PhonelinkSetup,
            values = strings.array(R.array.connection_mode_values),
            preference = PreferenceStore.CONNECTION_MODE
        )
    }

    add {
        SwitchPreference(
            title = strings[R.string.auto_connect],
            desc = strings[R.string.auto_connect_desc],
            icon = Icons.Default.Link,
            // Auto-connect is a Bluetooth device concept — irrelevant for External output
            enabled = !isExternal,
            preference = PreferenceStore.AUTO_CONNECT
        )
    }

    /*SwitchPreference(
            title = strings[R.string.show_unnamed],
            desc = strings[R.string.show_unnamed_desc],
            icon = Icons.Default.DeviceUnknown,
            preference = PreferenceStore.SHOW_UNNAMED
    )*/

    add {
        SliderPreference(
            title = strings[R.string.send_delay],
            desc = strings[R.string.send_delay_desc],
            valueFormat = strings[R.string.send_delay_template],
            // At most 40 ms per character: the Awqaf sabr site and entry app take a scan only when keystrokes
            // arrive less than 100 ms apart.
            range = 0f..40f,
            icon = Icons.Default.Timer,
            enabled = isHidOnly,
            preference = PreferenceStore.SEND_DELAY
        )
    }

    add {
        ComboBoxEnumPreference(
            title = strings[R.string.keyboard_layout],
            desc = strings[R.string.keyboard_layout_desc],
            icon = Icons.Default.Keyboard,
            values = strings.array(R.array.keyboard_layout_values),
            enabled = isHidOnly,
            preference = PreferenceStore.KEYBOARD_LAYOUT
        )
    }

    addAdvanced {
        val customKeysDialog = rememberDialogState()
        CustomKeysDialog(customKeysDialog)
        ButtonPreference(
            title = strings[R.string.custom_keys],
            desc = strings[R.string.define_custom_keys],
            icon = Icons.Default.KeyboardCommandKey,
            enabled = isHidOnly,
            onClick = customKeysDialog::open
        )
    }

    add {
        ComboBoxEnumPreference(
            title = strings[R.string.extra_keys],
            desc = strings[R.string.extra_keys_desc],
            icon = Icons.Default.AddCircle,
            values = strings.array(R.array.extra_keys_values),
            preference = PreferenceStore.EXTRA_KEYS
        )
    }

    addAdvanced {
        val errorString = strings.errorTemplate
        val codeRegex = remember { Regex("\\{[^{}]*CODE[^{}]*\\}") }
        TextBoxPreference(
            title = strings[R.string.custom_template],
            desc = strings[R.string.custom_templ_desc],
            descLong = strings[R.string.custom_templ_desc_long],
            validator = { template ->
                // Check if template contains at least one CODE placeholder (flexible format)
                if (!codeRegex.containsMatchIn(template))
                    return@TextBoxPreference errorString

                // Check each CODE placeholder for conflicts
                val matches = codeRegex.findAll(template)
                for (match in matches) {
                    val content = match.value.removePrefix("{").removeSuffix("}")
                    val parts = content.split("_")

                    // Check for duplicate components
                    val uniqueParts = parts.toSet()
                    if (uniqueParts.size != parts.size) {
                        val duplicates =
                            parts.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
                        return@TextBoxPreference strings.templateErrorDuplicates(
                            duplicates.joinToString(
                                ", "
                            )
                        )
                    }
                }

                null
            },
            icon = Icons.Default.LibraryAdd,
            preference = PreferenceStore.TEMPLATE_TEXT
        )
    }

    addAdvanced {
        SwitchPreference(
            title = strings[R.string.templates_in_value],
            desc = strings[R.string.template_in_value_desc],
            icon = Icons.Default.Expand,
            preference = PreferenceStore.EXPAND_CODE
        )
    }

    add {
        SwitchPreference(
            title = strings[R.string.auto_send],
            desc = strings[R.string.auto_send_desc],
            icon = Icons.AutoMirrored.Filled.Send,
            preference = PreferenceStore.AUTO_SEND
        )
    }

//    SwitchPreference(
//        title = strings[R.string.send_with_volume_keys],
//        desc = strings[R.string.send_vol_keys_desc],
//        icon = Icons.AutoMirrored.Filled.VolumeMute,
//        preference = PreferenceStore.SEND_WITH_VOLUME
//    )
    addAdvanced {
        VolumeKeyOptionsModal()
    }

    addAdvanced {
        JavaScriptOption()
    }

    addAdvanced {
        // External output plugin picker — available in every mode (parallel output toggle
        // for HID/RFCOMM lives inside it).
        ExternalPluginsModal()
    }
}

internal fun buildAppearanceSettings(strings: SettingsStrings, advanced: Boolean): List<@Composable () -> Unit> =
    buildSettings(advanced) {
        add {
            SwitchPreference(
                title = strings[R.string.keep_screen_on],
                desc = strings[R.string.keep_screen_on_desc],
                icon = Icons.Default.ScreenLockPortrait,
                preference = PreferenceStore.KEEP_SCREEN_ON
            )
        }

        addAdvanced {
            SwitchPreference(
                title = strings[R.string.allow_screen_rotation],
                desc = strings[R.string.allow_screen_rotation_desc],
                icon = Icons.Default.ScreenRotation,
                preference = PreferenceStore.ALLOW_SCREEN_ROTATION
            )
        }

        addAdvanced {
            SwitchPreference(
                title = strings[R.string.full_screen_scanner],
                desc = strings[R.string.full_screen_scanner_desc],
                icon = Icons.Default.Fullscreen,
                preference = PreferenceStore.SCANNER_FULL_SCREEN
            )
        }

        add {
            ComboBoxEnumPreference(
                title = strings[R.string.theme],
                desc = strings[R.string.theme_desc],
                icon = Icons.Default.AutoFixHigh,
                values = strings.array(R.array.theme_values),
                preference = PreferenceStore.THEME
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addAdvanced {
                SwitchPreference(
                    title = strings[R.string.dynamic_theme],
                    desc = strings[R.string.dynamic_theme_desc],
                    icon = Icons.Default.AutoAwesome,
                    preference = PreferenceStore.DYNAMIC_THEME
                )
            }
        }
    }

internal fun buildCameraSettings(strings: SettingsStrings, advanced: Boolean): List<@Composable () -> Unit> =
    buildSettings(advanced) {
        addAdvanced {
            SwitchPreference(
                title = strings[R.string.front_camera],
                desc = strings[R.string.front_camera_desc],
                icon = Icons.Default.FlipCameraAndroid,
                preference = PreferenceStore.FRONT_CAMERA
            )
        }

        addAdvanced {
            ComboBoxEnumPreference(
                title = strings[R.string.focus_mode],
                desc = strings[R.string.focus_mode_desc],
                values = strings.array(R.array.focus_mode_values),
                icon = Icons.Default.HdrAuto,
                preference = PreferenceStore.FOCUS_MODE
            )
        }

        addAdvanced {
            SwitchPreference(
                title = strings[R.string.fix_exposure],
                desc = strings[R.string.fix_exposure_desc],
                icon = Icons.Default.Exposure,
                preference = PreferenceStore.FIX_EXPOSURE
            )
        }

        addAdvanced {
            SwitchPreference(
                title = strings[R.string.preview_performance_mode],
                desc = strings[R.string.preview_mode_desc],
                icon = Icons.Default.Bolt,
                preference = PreferenceStore.PREVIEW_PERFORMANCE_MODE
            )
        }

        addAdvanced {
            SwitchPreference(
                title = strings[R.string.preview_stabilization],
                desc = strings[R.string.preview_stabilization_desc],
                icon = Icons.Default.VideoStable,
                preference = PreferenceStore.PREVIEW_STABILIZATION
            )
        }

        addAdvanced {
            ComboBoxEnumPreference(
                title = strings[R.string.scan_res],
                desc = strings[R.string.scan_res_desc],
                icon = Icons.Default.Hd,
                values = strings.array(R.array.scan_res_values),
                preference = PreferenceStore.SCAN_RESOLUTION
            )
        }

        addAdvanced {
            SliderPreference(
                title = strings[R.string.initial_zoom],
                desc = strings[R.string.initial_zoom_desc],
                valueFormat = "%.1fx",
                range = 1.0f..10.0f,
                steps = 8,
                icon = Icons.Default.ZoomIn,
                preference = PreferenceStore.INITIAL_ZOOM
            )
        }

        addAdvanced {
            CheckBoxPreference(
                title = strings[R.string.zoom_gesture],
                desc = strings[R.string.zoom_gesture_desc],
                valueStrings = strings.array(R.array.zoom_gesture_values),
                icon = Icons.Default.ZoomIn,
                preference = PreferenceStore.ZOOM_GESTURES
            )
        }

        /*SwitchPreference(
                title = strings[R.string.auto_zoom],
                desc = strings[R.string.zooms_in_on_codes_to_far_away],
                icon = Icons.Default.ZoomIn,
                preference = PreferenceStore.AUTO_ZOOM
        )*/
    }

internal fun buildScannerSettings(
    strings: SettingsStrings,
    isHidMode: Boolean,
    context: Context,
    scope: CoroutineScope,
    advanced: Boolean
): List<@Composable () -> Unit> = buildSettings(advanced) {
    add {
        CheckBoxPreference(
            title = strings[R.string.code_types],
            desc = strings[R.string.code_types_desc_short],
            descLong = strings[R.string.code_types_desc],
            valueStrings = strings.array(R.array.code_types_values),
            icon = Icons.Default.QrCode2,
            preference = PreferenceStore.CODE_TYPES
        )
    }

    addAdvanced {
        AdvancedOptionsModal()
    }

    addAdvanced {
        SwitchPreference(
            title = strings[R.string.restrict_area],
            desc = strings[R.string.restrict_area_desc],
            icon = Icons.Default.CropFree,
            preference = PreferenceStore.RESTRICT_AREA
        )
    }

    addAdvanced {
        val errorString = strings.errorRegex
        TextBoxPreference(
            title = strings[R.string.filter_regex],
            desc = strings[R.string.filter_regex_desc],
            descLong = strings[R.string.filter_desc_long],
            validator = {
                val result = runCatching { it.toRegex() }
                if (!it.isBlank() && result.isFailure)
                    return@TextBoxPreference result.exceptionOrNull()?.message ?: errorString
                null
            },
            icon = Icons.Default.FilterAlt,
            preference = PreferenceStore.SCAN_REGEX
        )
    }

    addAdvanced {
        ComboBoxEnumPreference(
            title = strings[R.string.overlay_type],
            desc = strings[R.string.overlay_type_desc],
            icon = Icons.Default.CenterFocusWeak,
            values = strings.array(R.array.overlay_values),
            preference = PreferenceStore.OVERLAY_TYPE,
            onReset = {
                scope.launch {
                    context.setPreference(
                        PreferenceStore.OVERLAY_POS_X,
                        PreferenceStore.OVERLAY_POS_X.defaultValue
                    )
                    context.setPreference(
                        PreferenceStore.OVERLAY_POS_Y,
                        PreferenceStore.OVERLAY_POS_Y.defaultValue
                    )
                    context.setPreference(
                        PreferenceStore.OVERLAY_WIDTH,
                        PreferenceStore.OVERLAY_WIDTH.defaultValue
                    )
                    context.setPreference(
                        PreferenceStore.OVERLAY_HEIGHT,
                        PreferenceStore.OVERLAY_HEIGHT.defaultValue
                    )
                }
            }
        )
    }

    /*ComboBoxEnumPreference(
        title = strings[R.string.highlight_type],
        desc = strings[R.string.highlight_type_desc],
        icon = Icons.Default.Highlight,
        values = stringArrayResource(R.array.highlight_values),
        preference = PreferenceStore.HIGHLIGHT_TYPE
    )*/

    /*SwitchPreference(
        title = strings[R.string.show_possible],
        desc = strings[R.string.highlights_possible_codes],
        icon = Icons.Default.Highlight,
        preference = PreferenceStore.SHOW_POSSIBLE
    )*/

    addAdvanced {
        SwitchPreference(
            title = strings[R.string.full_inside],
            desc = strings[R.string.full_inside_desc],
            icon = Icons.Default.QrCodeScanner,
            preference = PreferenceStore.FULL_INSIDE
        )
    }

    add {
        ComboBoxEnumPreference(
            title = strings[R.string.scan_freq],
            desc = strings[R.string.scan_freq_desc],
            icon = Icons.Default.ShutterSpeed,
            values = strings.array(R.array.scan_freq_values),
            preference = PreferenceStore.SCAN_FREQUENCY
        )
    }

    add {
        SwitchPreference(
            title = strings[R.string.play_sound],
            desc = strings[R.string.play_sound_desc],
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            preference = PreferenceStore.PLAY_SOUND
        )
    }

    add {
        SwitchPreference(
            title = strings[R.string.haptic_feedback],
            desc = strings[R.string.haptic_feedback_desc],
            icon = Icons.Default.Vibration,
            preference = PreferenceStore.VIBRATE
        )
    }

    addAdvanced {
        SwitchPreference(
            title = strings[R.string.auto_copy_to_clipboard],
            desc = strings[R.string.auto_copy_to_clipboard_desc],
            icon = Icons.Default.ContentCopy,
            preference = PreferenceStore.AUTO_COPY_TO_CLIPBOARD
        )
    }

    /*SwitchPreference(
        title = strings[R.string.raw_value],
        desc = strings[R.string.raw_value_desc],
        icon = Icons.Default.Description,
        preference = PreferenceStore.RAW_VALUE
    )*/

    addAdvanced {
        SwitchPreference(
            title = strings[R.string.clear_after_send],
            desc = strings[R.string.clear_after_send_desc],
            icon = Icons.Default.Clear,
            preference = PreferenceStore.CLEAR_AFTER_SEND
        )
    }

    addAdvanced {
        SwitchPreference(
            title = strings[R.string.enable_undo_send],
            desc = strings[R.string.enable_undo_send_desc],
            icon = Icons.AutoMirrored.Filled.Undo,
            enabled = isHidMode,
            preference = PreferenceStore.ENABLE_UNDO_SEND
        )
    }

    addAdvanced {
        ComboBoxEnumPreference(
            title = strings[R.string.clear_after_time],
            desc = strings[R.string.clear_after_time_desc],
            values = strings.array(R.array.clear_after_time_values),
            icon = Icons.Default.Timer,
            preference = PreferenceStore.CLEAR_AFTER_TIME
        )
    }

    addAdvanced {
        SwitchPreference(
            title = strings[R.string.private_mode],
            desc = strings[R.string.private_mode_desc],
            icon = Icons.Default.Shield,
            preference = PreferenceStore.PRIVATE_MODE,
        )
    }

    addAdvanced {
        SwitchPreference(
            title = strings[R.string.persistent_history],
            desc = strings[R.string.persistent_history_desc],
            icon = Icons.Default.Save,
            preference = PreferenceStore.PERSIST_HISTORY,
        )
    }

    addAdvanced {
        SwitchPreference(
            title = strings[R.string.multi_code_detection],
            desc = strings[R.string.multi_code_detection_desc],
            icon = Icons.Default.QrCode2,
            preference = PreferenceStore.MULTI_CODE_DETECTION
        )
    }

    addAdvanced {
        SaveScanImageOptionsModal()
    }
}

internal fun buildAboutSettings(
    strings: SettingsStrings,
    context: Context,
    uriHandler: UriHandler,
    scope: CoroutineScope,
    advanced: Boolean
): List<@Composable () -> Unit> = buildSettings(advanced) {
    // Shihab: About (with the GPL notice), language, updates, problem report to Shihab App's support, source code.
    BarcodeAbout.items(advanced).forEach { add(it) }

    add {
        val versionString = remember {
            strings.buildVersionDescription(
                BuildConfig.BUILD_TYPE,
                BuildConfig.VERSION_NAME,
                BuildConfig.GIT_COMMIT_HASH,
                BuildConfig.VERSION_CODE
            )
        }

        val clipboard = LocalClipboard.current

        ButtonPreference(
            title = strings[R.string.build_version],
            desc = versionString,
            icon = Icons.Default.Info,
            onLongClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipData.newPlainText("Version", versionString).toClipEntry()
                    )
                }
            }
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        }
    }
}

private fun getAppLogsAsString(): String {
    return try {
        val pid = android.os.Process.myPid()

        Runtime.getRuntime()
            .exec(arrayOf("logcat", "--pid=$pid", "-d"))
            .inputStream
            .bufferedReader()
            .use { it.readText() }
    } catch (e: Exception) {
        e.stackTraceToString()
    }
}

// Cached strings to avoid repeated resource lookups during scroll
internal class SettingsStrings(private val context: Context) {
    private val stringCache = mutableMapOf<Int, String>()
    private val arrayCache = mutableMapOf<Int, Array<String>>()

    operator fun get(@StringRes id: Int): String =
        stringCache.getOrPut(id) { context.getString(id) }

    fun array(@ArrayRes id: Int): Array<String> =
        arrayCache.getOrPut(id) { context.resources.getStringArray(id) }

    val errorTemplate: String = this[R.string.template_error]
    val errorRegex: String = this[R.string.invalid_regex]

    fun templateErrorDuplicates(duplicates: String): String =
        context.getString(R.string.template_error_duplicates, duplicates)

    fun buildVersionDescription(
        buildType: String,
        versionName: String,
        commitHash: String,
        versionCode: Int
    ): String =
        context.getString(
            R.string.build_version_desc,
            buildType,
            versionName,
            commitHash,
            versionCode
        )
}

internal class SettingsBuilder(private val advanced: Boolean = true) {
    private val items = mutableListOf<@Composable () -> Unit>()

    fun add(content: @Composable () -> Unit) {
        items += content
    }

    /** Shihab: shown only with "Advanced settings" on; the simple screen keeps what data entry needs. */
    fun addAdvanced(content: @Composable () -> Unit) {
        if (advanced) items += content
    }

    fun build(): List<@Composable () -> Unit> =
        items.mapIndexed { index, content ->
            @Composable {
                val shape = when {
                    items.size == 1 -> RoundedCornerShape(16.dp)
                    index == 0 ->
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

                    index == items.lastIndex ->
                        RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

                    else -> null
                }

                when (shape) {
                    null -> content()
                    else -> Box(Modifier.clip(shape)) { content() }
                }
            }
        }
}

private fun buildSettings(
    advanced: Boolean = true,
    block: SettingsBuilder.() -> Unit
): List<@Composable () -> Unit> = SettingsBuilder(advanced).apply(block).build()

@Composable
@Preview
private fun Preview() {
    val context = LocalContext.current
    val activeDataStore by remember { ProfileManager.activeStoreFlow(context) }
        .collectAsStateWithLifecycle(initialValue = ProfileManager.currentStore(context))
    CompositionLocalProvider(LocalDataStore provides activeDataStore) {
        SettingsContent()
    }
}