namespace BarcodeReceiver.Core;

/// <summary>One known error: stable code, cause (for support), message and fix (for the user).</summary>
internal sealed record ReceiverError(string Code, string Cause, string Message, string Fix)
{
    /// <summary>"message fix · RCV-00x", as shown on screen and quoted to support.</summary>
    public string Text => $"{Message} {Fix}  · {Code}";
}

/// <summary>
/// The receiver's known errors (family RCV, documented in windows\ERRORS.md). The shared ones (CMN) come from
/// Shihab.Common. Codes are never reused.
/// </summary>
internal static class ReceiverErrors
{
    public static readonly ReceiverError PortsBusy = new("RCV-001", "TCP ports 5870 to 5879 are all in use",
        "تعذّر فتح منفذ الاستقبال على هذا الحاسوب.", "أغلق البرنامج الذي يستخدم المنافذ 5870 إلى 5879 ثم أعد تشغيل باركود.");

    public static readonly ReceiverError IdentityReset = new("RCV-002", "identity.dat could not be read and was made again",
        "تجدّد رمز الاقتران لهذا الحاسوب.", "امسح رمز الاقتران الجديد من كل جوال.");

    public static readonly ReceiverError TypingBlocked = new("RCV-003", "SendInput was refused (an administrator window is active, or the desktop is locked)",
        "تعذّرت كتابة الرمز في النافذة الحالية.", "انقر داخل خانة الإدخال في البرنامج أو المتصفح، ولا تشغّله كمسؤول.");

    public static readonly ReceiverError AdbMissing = new("RCV-004", "adb.exe is missing next to the app or could not start",
        "الاتصال بالكبل غير متاح.", "أعد تثبيت باركود للحاسوب، أو استخدم الواي فاي أو البلوتوث.");

    public static readonly ReceiverError BluetoothFailed = new("RCV-005", "RFCOMM connection to the phone failed (phone off, out of range, app not in RFCOMM mode)",
        "تعذّر الاتصال بالجوال عبر البلوتوث.", "تأكد أن باركود على الجوال في وضع «بلوتوث إلى الحاسوب» ثم أعد المحاولة.");

    public static readonly ReceiverError BluetoothUnavailable = new("RCV-006", "No Bluetooth radio, or Bluetooth is off in Windows",
        "البلوتوث غير متاح على هذا الحاسوب.", "شغّل البلوتوث من إعدادات ويندوز، أو استخدم الواي فاي أو الكبل.");

    public static readonly ReceiverError WrongKey = new("RCV-007", "A phone sent an old or wrong pairing key",
        "رفض الحاسوب جوالاً برمز اقتران قديم.", "امسح رمز الاقتران الظاهر الآن من ذلك الجوال.");

    public static readonly ReceiverError ScanTooLong = new("RCV-008", "A scan over 4096 characters was refused",
        "رمز طويل جداً لم يُكتب.", "امسح رمزاً أقصر.");

    public static readonly ReceiverError ExportFailed = new("RCV-009", "The export file could not be written (folder read only, file open in Excel, disk full)",
        "تعذّر حفظ ملف التصدير.", "أغلق الملف إن كان مفتوحاً في Excel، أو اختر مجلداً آخر.");

    public static readonly ReceiverError FirewallBlocked = new("RCV-010", "No Windows Firewall rule for the listener (the administrator prompt was declined, or netsh failed)",
        "جدار حماية ويندوز يمنع الجوالات من الوصول عبر الواي فاي.", "اضغط «السماح» بجانب الواي فاي ووافق على طلب المسؤول، أو استخدم الكبل.");
}
