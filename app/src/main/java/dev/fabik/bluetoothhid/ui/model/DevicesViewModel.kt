package dev.fabik.bluetoothhid.ui.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.utils.DeviceInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DevicesViewModel : ViewModel() {
    var foundDevices = mutableStateListOf<BluetoothDevice>()
    var pairedDevices = mutableStateListOf<BluetoothDevice>()

    var isScanning by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)

    // Initially assume it is enabled to prevent the card from wrongly showing up
    var isBluetoothEnabled by mutableStateOf(true)

    @SuppressLint("MissingPermission")
    fun refresh(controller: BluetoothController?) {
        viewModelScope.launch {
            isRefreshing = true
            pairedDevices.clear()
            pairedDevices.addAll(
                controller?.pairedDevices?.filter { DeviceInfo.isPossibleHost(it.bluetoothClass) }
                    ?: emptyList()
            )
            if (!isScanning) {
                controller?.scanDevices()
            }
            delay(500)
            isRefreshing = false
        }
    }
}
