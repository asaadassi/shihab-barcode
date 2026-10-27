package dev.fabik.bluetoothhid.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.fabik.bluetoothhid.Devices
import dev.fabik.bluetoothhid.History
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.Scanner
import dev.fabik.bluetoothhid.shihab.ConnectionStart
import dev.fabik.bluetoothhid.shihab.PcPairing
import dev.fabik.bluetoothhid.ui.model.BarcodeResult
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.ZXingAnalyzer
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Routes {
    const val Start = "Start"  // Shihab: the connection choice, always the first screen
    const val Pair = "Pair"    // Shihab: pairs the phone with the PC (Wi-Fi/cable) before scanning; "Pair?manage=true" from the scanner
    const val Devices = "Devices"
    const val Main = "Main"
    const val History = "History"
}

val LocalNavigation = staticCompositionLocalOf<NavHostController> {
    error("No Navigation provided")
}

@Composable
fun NavGraph() {
    val controller = LocalController.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    var canExit by remember { mutableStateOf(false) }
    val exitString = stringResource(R.string.exit_confirm)

    val context = LocalContext.current

    // Handles shortcut to scanner/history
    val startDestination = remember {
        when (activity?.intent?.dataString) {
            "Scanner" -> Routes.Main
            "History" -> Routes.History
            else -> null
        }
    }

    LaunchedEffect(startDestination) {
        startDestination?.let {
            navController.navigate(it)
        }
    }

    val currentDevice by controller?.currentDevice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    CompositionLocalProvider(LocalNavigation provides navController) {
        NavHost(navController, Routes.Start) {
            // Shihab: Bluetooth goes on to the device list (or straight to the scanner when already
            // connected); Wi-Fi/cable first pairs with the PC and checks the link; direct reading opens the scanner.
            composable(Routes.Start) {
                ConnectionStart { mode ->
                    scope.launch {
                        context.setPreference(PreferenceStore.CONNECTION_MODE, mode.ordinal)
                        val bluetooth = mode == ConnectionMode.HID || mode == ConnectionMode.RFCOMM
                        navController.navigate(
                            when {
                                bluetooth && currentDevice == null -> Routes.Devices
                                mode == ConnectionMode.PC && controller != null -> Routes.Pair
                                else -> Routes.Main
                            }
                        ) { launchSingleTop = true }
                    }
                }

                // Confirm back presses to exit the app
                BackHandler {
                    if (canExit) {
                        activity?.finishAfterTransition()
                    } else {
                        canExit = true
                        Toast.makeText(activity, exitString, Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            canExit = false
                        }
                    }
                }
            }

            composable(
                "${Routes.Pair}?manage={manage}",
                arguments = listOf(navArgument("manage") { type = NavType.BoolType; defaultValue = false })
            ) { entry ->
                val manage = entry.arguments?.getBoolean("manage") == true
                val link = controller?.pcLink
                if (link != null) {
                    PcPairing(link, manage, onBack = { navController.navigateUp() }) {
                        if (manage) navController.navigateUp()
                        else navController.navigate(Routes.Main) {
                            popUpTo(Routes.Start)
                            launchSingleTop = true
                        }
                    }
                }
            }

            composable(Routes.Devices) {
                LaunchedEffect(Unit) {
                    controller?.disconnect()
                }

                Devices {
                    navController.navigate(Routes.Main)
                }
            }

            composable(Routes.Main) {
                // Unbounded channel ensures every barcode result is queued even during active sends
                val sendQueue = remember { Channel<BarcodeResult>(Channel.UNLIMITED) }

                // Sequential queue processor — each sendString awaits completion before next item
                LaunchedEffect(controller) {
                    for (result in sendQueue) {
                        val barcodeType = ZXingAnalyzer.index2String(result.format)
                        controller?.sendString(
                            result.text,
                            true,
                            "SCAN",
                            null,
                            barcodeType,
                            imageName = result.imageName,
                            regexGroups = result.regexGroups
                        )
                    }
                }

                Scanner(currentDevice) { result: BarcodeResult ->
                    sendQueue.trySend(result)
                }
            }

            composable(Routes.History) {
                val onBack: () -> Unit = {
                    navController.navigateUp()
                }

                History(onBack) { historyEntry ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val barcodeType = historyEntry.format.let { ZXingAnalyzer.index2String(it) }
                        controller?.sendString(
                            historyEntry.value,
                            true,
                            "HISTORY",
                            historyEntry.timestamp,
                            barcodeType
                        )
                    }
                }
            }
        }
    }

    // Listen for changes in the current device
    LaunchedEffect(currentDevice) {
        // When connected to a device from the device list, navigate to the scanner
        // (Shihab: not from the start screen, where the user still chooses the connection)
        if (currentDevice != null && navController.currentDestination?.route == Routes.Devices) {
            // Single-top is used to avoid creating multiple instances of the scanner
            navController.navigate(Routes.Main) {
                popUpTo(Routes.Devices)
                launchSingleTop = true
            }
        }
    }

}