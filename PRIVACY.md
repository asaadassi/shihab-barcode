# Privacy · الخصوصية

**Barcode** (باركود · ماسح بلوتوث من شهاب آب) scans barcodes with the phone camera and sends the value to a device
connected over Bluetooth. **It does not collect or transmit any personal data.** Scan history stays on the phone
(it can be turned off: Settings → Scanner → private mode / persistent history).

يمسح «باركود» الرموز بكاميرا الجوال ويرسل قيمتها إلى جهاز متصل بالبلوتوث، **ولا يجمع أي بيانات شخصية ولا يرسلها**.
سجل المسح يبقى على الجوال فقط ويمكن إيقافه من الإعدادات.

## What leaves the phone · ما يخرج من الجوال

| What | When | Data |
|---|---|---|
| PC link (both builds) | only in "PC: Wi-Fi or USB cable" mode | each scan, the phone's name and a random install id, encrypted (TLS) to the paired PC on the local network or the USB cable; nothing goes to the internet |
| Update check (GitHub APK, `direct`) | when you check for updates | a request to the public GitHub releases repository `asaadassi/shihab-barcode-releases` (GitHub sees the phone's IP address and the app version in the User-Agent) |

- **Problem report** (both builds): only when you tap it, your own email app opens a message to
  support@shihab.app with the app version, Android version, phone model and the last lines of the app's error log.
  Nothing is sent unless you send that email.
- No analytics, no advertising, no accounts.

Contact · التواصل: support@shihab.app · https://shihab.app/

## Permissions

The following table explains all the permissions that are used. Some permissions (marked with legacy) are only required on older Android versions and not relevant on newer devices.

> [!IMPORTANT]
> The app **does not require internet access** to function. `android.permission.INTERNET` is used for the PC link on
> the local network or the USB cable, and in the GitHub build for the update check, which also adds
> `android.permission.REQUEST_INSTALL_PACKAGES` (the system installer always asks before installing).


| Permission                                                                     | Purpose                         | Explanation                                                                                                         |
| ------------------------------------------------------------------------------ | ------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `android.permission.BLUETOOTH` <br> *(only Android 11 and below)*              | Connect to device (legacy)      | Required to communicate with Bluetooth devices.                                                                     |
| `android.permission.BLUETOOTH_ADMIN` <br> *(only Android 11 and below)*        | Discover devices (legacy)       | Needed to initiate scanning for Bluetooth devices.                                                                  |
| `android.permission.ACCESS_COARSE_LOCATION` <br> *(only Android 11 and below)* | Bluetooth scan (legacy)         | Needed on Android <= 11 due to how BLE scanning works on those versions. Not used for actual location tracking.     |
| `android.permission.ACCESS_FINE_LOCATION` <br> *(only Android 11 and below)*   | Bluetooth scan (legacy)         | Needed on Android <= 11 due to how BLE scanning works on those versions. Not used for actual location tracking.     |
| `android.permission.BLUETOOTH_SCAN`                                            | Discover devices                | Allows the app to scan for nearby Bluetooth devices on Android 12+ (API 31+) without requiring location permission. |
| `android.permission.BLUETOOTH_CONNECT`                                         | Connect to devices              | Enables connecting to and communicating with the target device.                                                     |
| `android.permission.FOREGROUND_SERVICE`                                        | Service operation               | Allows the app to run a foreground service, to ensure an uninterrupted communication with the target device.        |
| `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`                       | Long-running device interaction | Foreground service type.                                                                                            |
| `android.permission.CAMERA`                                                    | Scan barcodes                   | Needed to scan barcodes with the phone camera.                                                                      |
| `android.permission.VIBRATE`                                                   | Haptic feedback                 | Used to provide tactile feedback after a new scan.                                                                  |
| `android.permission.POST_NOTIFICATIONS`                                        | User notifications              | Allows the app to show a notification that the foreground service is running.                                       |
