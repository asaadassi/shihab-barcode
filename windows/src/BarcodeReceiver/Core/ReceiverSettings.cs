using System.Text.Json;

namespace BarcodeReceiver.Core;

/// <summary>The user's choices, in settings.json (no secrets: the pairing key lives in identity.dat).</summary>
internal sealed class ReceiverSettings
{
    /// <summary>Milliseconds between typed keys (1 to 40: the Awqaf sabr site takes a scan only under 100 ms apart).</summary>
    public int KeyDelayMs { get; set; } = LinkConfig.DefaultKeyDelayMs;
    /// <summary>The Windows id of the phone the Bluetooth link connects to, or null when it is off.</summary>
    public string? BluetoothPhoneId { get; set; }
    public string? BluetoothPhoneName { get; set; }

    public static ReceiverSettings Current { get; private set; } = new();

    public static void Load()
    {
        try
        {
            if (File.Exists(AppPaths.Settings))
                Current = JsonSerializer.Deserialize<ReceiverSettings>(File.ReadAllText(AppPaths.Settings)) ?? new();
        }
        catch (Exception ex) when (ex is JsonException or IOException)
        {
            AppLog.Error("CMN-001", "settings_unreadable", ex);
            Current = new();
        }
        Current.KeyDelayMs = Math.Clamp(Current.KeyDelayMs, 1, 40);
    }

    public static void Save()
    {
        try
        {
            Directory.CreateDirectory(AppPaths.Data);
            var tmp = AppPaths.Settings + ".tmp";
            File.WriteAllText(tmp, JsonSerializer.Serialize(Current, new JsonSerializerOptions { WriteIndented = true }));
            File.Move(tmp, AppPaths.Settings, overwrite: true);
        }
        catch (IOException ex) { AppLog.Error("CMN-001", "settings_save_failed", ex); }
    }
}
