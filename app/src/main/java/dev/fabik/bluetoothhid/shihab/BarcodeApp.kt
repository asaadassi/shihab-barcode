package dev.fabik.bluetoothhid.shihab

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import app.shihab.shared.diag.ShihabLog
import app.shihab.shared.locale.ShihabLang
import dev.fabik.bluetoothhid.BuildConfig

/**
 * Barcode's application: the app language (Arabic first, 0-9 digits, or English) before any screen or service
 * runs, and the error log with its crash handler (Shihab shared library).
 */
class BarcodeApp : Application() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(ShihabLang.wrap(base))

    override fun onCreate() {
        super.onCreate()
        ShihabLang.applyDefault(this)
        ShihabLog.init(this, prefix = "barcode", systemLog = BuildConfig.DEBUG)
        ShihabLog.installCrashHandler(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ShihabLang.reapply(this, resources)
    }
}
