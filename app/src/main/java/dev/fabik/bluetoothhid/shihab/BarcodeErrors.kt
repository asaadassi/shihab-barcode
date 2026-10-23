package dev.fabik.bluetoothhid.shihab

import android.content.Context
import app.shihab.shared.diag.CommonError
import app.shihab.shared.locale.ShihabLang

/**
 * Barcode's own known errors (family BCD, documented in ERRORS.md); the shared ones (CMN) come from ShihabErrors.
 * Codes are never reused.
 */
object BarcodeErrors {
    val UPDATE_CHECK = CommonError("BCD-001", "The releases repository could not be read (no internet, GitHub blocked)",
        "تعذّر التحقق من وجود تحديث.", "Couldn't check for updates.",
        "تحقق من الاتصال بالإنترنت ثم أعد المحاولة.", "Check the internet connection, then try again.")

    val UPDATE_REJECTED = CommonError("BCD-002", "The downloaded APK failed its hash, signature or signer check",
        "رُفض ملف التحديث لأنه لم يجتز فحص السلامة.", "The update file was rejected: it failed the integrity check.",
        "لا تثبّته، وأبلغ الدعم.", "Don't install it; tell support.")

    val UPDATE_DOWNLOAD = CommonError("BCD-003", "The update could not be downloaded or handed to the installer",
        "تعذّر تنزيل التحديث أو تثبيته.", "Couldn't download or install the update.",
        "أعد المحاولة، وإن كان GitHub محجوباً فشغّل VPN.", "Try again; if GitHub is blocked, turn on a VPN.")

    val NO_MAIL_APP = CommonError("BCD-004", "No app on the phone handles mailto: links",
        "لا يوجد تطبيق بريد على الجوال لإرسال التقرير.", "There is no email app on the phone to send the report.",
        "ثبّت تطبيق بريد، أو راسل الدعم من جهاز آخر.", "Install an email app, or write to support from another device.")

    /** "message · BCD-00x" in the app language, as shown on screen and quoted to support. */
    fun text(ctx: Context, e: CommonError): String =
        if (ShihabLang.isArabic(ctx)) "${e.ar} ${e.fixAr}  · ${e.code}" else "${e.en} ${e.fixEn}  · ${e.code}"
}
