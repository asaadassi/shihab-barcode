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

    val PC_REFUSED = CommonError("BCD-005", "The PC refused the pairing key (it made a new pairing code)",
        "رفض الحاسوب هذا الجوال.", "The PC refused this phone.",
        "امسح رمز الاقتران الظاهر الآن في باركود على الحاسوب.", "Scan the pairing code shown now in Barcode on the PC.")

    val PC_NOT_TYPED = CommonError("BCD-006", "The PC received a scan but could not type it (a key held down, or an administrator window)",
        "وصل الرمز إلى الحاسوب لكنه لم يُكتب.", "The PC got the code but could not type it.",
        "انقر داخل خانة الإدخال على الحاسوب وامسح مرة أخرى.", "Click into the input field on the PC and scan again.")

    val PC_NO_ANSWER = CommonError("BCD-007", "No answer from the PC's Wi-Fi addresses (Windows firewall, another network, Wi-Fi client isolation)",
        "لا يصل الجوال إلى الحاسوب عبر الواي فاي.", "The phone can't reach the PC over Wi-Fi.",
        "اجعل الجوال والحاسوب على شبكة الواي فاي نفسها، واضغط «السماح» بجانب الواي فاي في باركود على الحاسوب.",
        "Put the phone and the PC on the same Wi-Fi, and press \"Allow\" next to Wi-Fi in Barcode on the PC.")

    val LOCAL_NETWORK_DENIED = CommonError("BCD-008", "Android 17: the local network permission (ACCESS_LOCAL_NETWORK) is not granted",
        "باركود غير مسموح له بالوصول إلى الشبكة المحلية.", "Barcode is not allowed to reach the local network.",
        "اسمح له بالوصول إلى الأجهزة القريبة والشبكة المحلية.", "Allow it to reach nearby devices and the local network.")

    val PC_OTHER_CERT = CommonError("BCD-009", "The PC's certificate is not the one in the pairing code (receiver reinstalled, or another PC)",
        "الحاسوب لا يطابق رمز الاقتران المحفوظ.", "The PC does not match the saved pairing code.",
        "امسح رمز الاقتران الظاهر الآن في باركود على الحاسوب.", "Scan the pairing code shown now in Barcode on the PC.")

    val PC_CLOSED = CommonError("BCD-010", "The PC answered but nothing listens on the port (Barcode Receiver is not running)",
        "برنامج باركود ليس مفتوحاً على الحاسوب.", "Barcode is not open on the PC.",
        "افتحه من قائمة ابدأ، ثم أعد المحاولة.", "Open it from the Start menu, then try again.")

    val PC_NO_NETWORK = CommonError("BCD-011", "No route from the phone to the PC's address (Wi-Fi off, mobile data only)",
        "الجوال غير متصل بشبكة الحاسوب.", "The phone is not on the PC's network.",
        "شغّل الواي فاي على الجوال واتصل بشبكة الحاسوب نفسها.", "Turn on Wi-Fi on the phone and join the PC's network.")

    val PC_NO_ADDRESS = CommonError("BCD-012", "The pairing code carries no address (the PC had no network when it was shown)",
        "رمز الاقتران لا يحمل عنوان شبكة للحاسوب.", "The pairing code has no network address for the PC.",
        "صل الحاسوب بالشبكة، ثم امسح رمز الاقتران من جديد.", "Connect the PC to the network, then scan the pairing code again.")

    /** The catalog entry the pairing screen shows for why the PC was not reached. */
    fun of(problem: PcLink.Problem): CommonError = when (problem) {
        PcLink.Problem.PERMISSION -> LOCAL_NETWORK_DENIED
        PcLink.Problem.OLD_CODE -> PC_REFUSED
        PcLink.Problem.OTHER_PC -> PC_OTHER_CERT
        PcLink.Problem.CLOSED -> PC_CLOSED
        PcLink.Problem.NO_ANSWER -> PC_NO_ANSWER
        PcLink.Problem.NO_NETWORK -> PC_NO_NETWORK
        PcLink.Problem.NO_ADDRESS -> PC_NO_ADDRESS
    }

    /** "message · BCD-00x" in the app language, as shown on screen and quoted to support. */
    fun text(ctx: Context, e: CommonError): String =
        if (ShihabLang.isArabic(ctx)) "${e.ar} ${e.fixAr}  · ${e.code}" else "${e.en} ${e.fixEn}  · ${e.code}"
}
