package dev.fabik.bluetoothhid.ui.theme

import android.os.Build
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.fabik.bluetoothhid.shihab.BarcodeBrand
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.Theme
import dev.fabik.bluetoothhid.utils.rememberEnumPreference
import dev.fabik.bluetoothhid.utils.rememberPreference

/**
 * Configures window properties for better performance and transparency.
 * - Disables navigation bar contrast enforcement for true transparency
 * - Enables high refresh rate (90/120 Hz) if supported
 *
 * @param window The activity window to configure
 */
fun configureWindow(window: Window) {
    // Disable navigation bar contrast enforcement to allow true transparency
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }

    // Enable high refresh rate (90/120 Hz)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.context.display
        } else {
            window.windowManager.defaultDisplay
        }

        display?.let {
            val modes = it.supportedModes
            val highRefreshMode = modes.maxByOrNull { mode -> mode.refreshRate }
            highRefreshMode?.let { mode ->
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = mode.modeId
                }
            }
        }
    }
}

/**
 * Controls the appearance of system bars (status bar and navigation bar) based on theme.
 * Adjusts icon colors to be light or dark depending on background luminance.
 *
 * @param window The activity window to control
 */
@Composable
fun SystemBarsController(window: Window) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f

    SideEffect {
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = isLightTheme
        insetsController.isAppearanceLightNavigationBars = isLightTheme
    }
}

@Composable
fun BluetoothHIDTheme(
    window: Window? = null,  // Activity window for system bars control
    content: @Composable () -> Unit
) {
    // Use blocking preferences to prevent theme flash on startup
    val prefDarkTheme by rememberEnumPreference(PreferenceStore.THEME)
    val prefDynamicColor by rememberPreference(PreferenceStore.DYNAMIC_THEME)

    val dark = when (prefDarkTheme) {
        Theme.LIGHT -> false
        Theme.DARK -> true
        Theme.SYSTEM -> isSystemInDarkTheme()
    }

    // Shihab colour tokens (light and dark) unless the user picked the phone's dynamic colours.
    val context = LocalContext.current
    val colorScheme = when {
        prefDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> remember(dark) { BarcodeBrand.colorScheme(BarcodeBrand.palette(context, dark)) }
    }
    val typography = remember { BarcodeBrand.typography(context) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        // Control system bars appearance if window is provided
        window?.let { SystemBarsController(it) }

        content()
    }
}
