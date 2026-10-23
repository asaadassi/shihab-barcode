# باركود · ماسح بلوتوث من شهاب آب

**Barcode by Shihab App** turns an Android phone (9 or later) into a barcode scanner that connects to a computer
as a Bluetooth keyboard (HID): the code is typed wherever the cursor is, followed by Enter.

يحوّل «باركود» جوال أندرويد (9 فما فوق) إلى ماسح باركود يتصل بالحاسوب كلوحة مفاتيح بلوتوث، فيُكتب الرمز حيث
المؤشر ثم Enter. إعداداته الافتراضية تناسب منصة سبر الأوقاف (الرقم الشخصي EAN-13 ثم Enter بسرعة 20 ms للحرف).

It is a modified version of [HID Barcode Scanner](https://github.com/Fabi019/hid-barcode-scanner) by Fabi019,
released under the **GNU GPL v3**: see [NOTICE.md](NOTICE.md) and [LICENSE](LICENSE). The upstream README is kept as
[README.upstream.md](README.upstream.md).

## Build · البناء

Requirements: JDK 17, Android SDK (platform 37), and the Shihab shared libraries next to this folder
(`../ShihabShared`, or `-PshihabShared=<path>`).

```powershell
.\build.ps1            # tests, then signed APK (GitHub) and AAB (Google Play) into dist\
```

Release signing reads `keystore.properties` at the project root (not in source control):
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

| Flavour | Output | Updates |
|---|---|---|
| `direct` | `dist/ShihabBarcode-android-<version>.apk` | in the app, from `asaadassi/shihab-barcode-releases` |
| `play` | `dist/ShihabBarcode-play-<version>.aab` | Google Play |

Version: `appVersionName` / `appVersionCode` in [app/build.gradle.kts](app/build.gradle.kts) (SemVer).
Known errors: [ERRORS.md](ERRORS.md). Privacy: [PRIVACY.md](PRIVACY.md). Changes: [CHANGELOG.md](CHANGELOG.md).
