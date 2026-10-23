# NOTICE

**Barcode** (باركود · ماسح بلوتوث من شهاب آب, `com.shihabapp.barcode`) is a modified version of
**HID Barcode Scanner** ("Bluetooth QR & Barcode to PC", `dev.fabik.bluetoothhid`).

- Original work: Copyright (C) Fabian Kurz (Fabi019) and contributors,
  https://github.com/Fabi019/hid-barcode-scanner
- Based on upstream release: v2.3.2
- Modified by Shihab App (https://shihab.app/), 2026-10-23 and later.

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. See [LICENSE](LICENSE).

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

## Changes made by Shihab App

- New application ID `com.shihabapp.barcode`, name "Barcode" / "باركود" and Shihab App's icon.
- Own permission name and file provider authority, so it can be installed next to the original app.
- Shihab look: colour tokens, the Almarai font, Arabic first (right to left, 0-9 digits, Levantine
  month names) with English kept; the remaining Arabic translations completed.
- Defaults for the Awqaf sabr platform: Enter after the code, send on detection, auto connect,
  fast scanning, 20 ms per character (at most 40 ms), US keyboard layout.
- Linked to the Shihab shared Android library (About window, error log, updater, language).
- About shows this notice and the full licence; the update check (GitHub build only) and the
  problem report go to Shihab App.
- Build flavours `direct` (GitHub APK with in-app updates) and `play` (Google Play, no self-update).

The Shihab shared Android library (`shihab-android`) is linked into the app at build time; its
source is distributed together with this app's source under the same terms.

Third-party components keep their own licences (see the upstream README): zxing-cpp (Apache-2.0),
AndroidX / Jetpack Compose (Apache-2.0), Accompanist (Apache-2.0), Almarai font (SIL OFL 1.1).
