package dev.fabik.bluetoothhid.utils

import android.bluetooth.BluetoothClass

object DeviceInfo {
    // Device classes that can never act as a HID host (headphones, speakers, mice, watches...)
    private val nonHostMajors = setOf(
        BluetoothClass.Device.Major.AUDIO_VIDEO,
        BluetoothClass.Device.Major.PERIPHERAL,
        BluetoothClass.Device.Major.WEARABLE,
        BluetoothClass.Device.Major.TOY,
        BluetoothClass.Device.Major.HEALTH,
        BluetoothClass.Device.Major.IMAGING,
    )

    // Devices without a class (some LE devices) are kept to be safe
    fun isPossibleHost(bluetoothClass: BluetoothClass?): Boolean =
        bluetoothClass == null || bluetoothClass.majorDeviceClass !in nonHostMajors

    fun deviceClassString(classMajor: Int?): String = when (classMajor) {
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "AUDIO_VIDEO"
        BluetoothClass.Device.Major.COMPUTER -> "COMPUTER"
        BluetoothClass.Device.Major.HEALTH -> "HEALTH"
        BluetoothClass.Device.Major.MISC -> "MISC"
        BluetoothClass.Device.Major.IMAGING -> "IMAGING"
        BluetoothClass.Device.Major.NETWORKING -> "NETWORKING"
        BluetoothClass.Device.Major.PERIPHERAL -> "PERIPHERAL"
        BluetoothClass.Device.Major.PHONE -> "PHONE"
        BluetoothClass.Device.Major.TOY -> "TOY"
        BluetoothClass.Device.Major.WEARABLE -> "WEARABLE"
        BluetoothClass.Device.Major.UNCATEGORIZED -> "UNCATEGORIZED"
        else -> "UNKNOWN"
    }

    fun deviceServiceInfo(bluetoothClass: BluetoothClass?): List<String> {
        val services = mutableListOf<String>()
        if (bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) == true)
            services.add("AUDIO")
        if (bluetoothClass?.hasService(BluetoothClass.Service.CAPTURE) == true)
            services.add("CAPTURE")
        if (bluetoothClass?.hasService(BluetoothClass.Service.NETWORKING) == true)
            services.add("NETWORKING")
        if (bluetoothClass?.hasService(BluetoothClass.Service.INFORMATION) == true)
            services.add("INFORMATION")
        if (bluetoothClass?.hasService(BluetoothClass.Service.LE_AUDIO) == true)
            services.add("LE_AUDIO")
        if (bluetoothClass?.hasService(BluetoothClass.Service.LIMITED_DISCOVERABILITY) == true)
            services.add("LIMITED_DISCOVERABILITY")
        if (bluetoothClass?.hasService(BluetoothClass.Service.OBJECT_TRANSFER) == true)
            services.add("OBJECT_TRANSFER")
        if (bluetoothClass?.hasService(BluetoothClass.Service.POSITIONING) == true)
            services.add("POSITIONING")
        if (bluetoothClass?.hasService(BluetoothClass.Service.RENDER) == true)
            services.add("RENDER")
        if (bluetoothClass?.hasService(BluetoothClass.Service.TELEPHONY) == true)
            services.add("TELEPHONY")
        return services
    }
}
