namespace BarcodeReceiver.Core;

/// <summary>Names and folders of the app (rule: system-level names carry the project prefix).</summary>
internal static class AppPaths
{
    public const string AppId = "ShihabBarcodeReceiver";

    public static string Data { get; } =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), AppId);

    public static string Logs => Path.Combine(Data, "logs");
    public static string Identity => Path.Combine(Data, "identity.dat");
    public static string Settings => Path.Combine(Data, "settings.json");
}

/// <summary>Product constants in one place: ports, limits and timings of the PC link (docs\PC-LINK.md).</summary>
internal static class LinkConfig
{
    public const int FirstPort = 5870;
    public const int LastPort = 5879;
    public const int MaxScanLength = 4096;
    public const int MaxLineBytes = 16 * 1024;
    public const int MaxPhones = 16;
    public static readonly TimeSpan HandshakeTimeout = TimeSpan.FromSeconds(10);
    public static readonly TimeSpan IdleTimeout = TimeSpan.FromSeconds(45);
    public static readonly TimeSpan AdbPollInterval = TimeSpan.FromSeconds(4);
    public const int DefaultKeyDelayMs = 8;
    public const int RecentScans = 50;
    /// <summary>Scans kept for export since the receiver started (memory only).</summary>
    public const int SessionScans = 20000;

    public const string ReleasesOwner = "asaadassi";
    public const string ReleasesRepo = "shihab-barcode-releases";
    /// <summary>The releases repository is shared with the Android app: the receiver's tags are "receiver-&lt;version&gt;".</summary>
    public const string ReleasesTagPrefix = "receiver";
}
