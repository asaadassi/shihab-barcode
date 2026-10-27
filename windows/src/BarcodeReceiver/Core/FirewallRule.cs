using System.ComponentModel;
using System.Diagnostics;

namespace BarcodeReceiver.Core;

/// <summary>
/// The Windows Defender Firewall rule that lets phones on the local network reach the listener (TCP 5870 to 5879).
/// Without it Windows drops the phone's Wi-Fi connection silently (the cable through adb needs none): the app is
/// installed per user, so the installer cannot add it, and the prompt Windows shows on first run allows private
/// networks only while many home Wi-Fi networks are marked public. Added through netsh (the supported tool), with one
/// administrator prompt; any rule Windows made for this program first (a "Block" from a dismissed prompt) is removed.
/// </summary>
internal static class FirewallRule
{
    public const string Name = "Barcode Receiver (Shihab App)";

    private static string Netsh => Path.Combine(Environment.SystemDirectory, "netsh.exe");

    /// <summary>True when our rule exists (reading the rules needs no administrator rights).</summary>
    public static async Task<bool> ExistsAsync()
    {
        try
        {
            using var p = Process.Start(new ProcessStartInfo(Netsh, $"advfirewall firewall show rule name=\"{Name}\"")
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
            })!;
            _ = p.StandardOutput.ReadToEndAsync();
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(8));
            await p.WaitForExitAsync(cts.Token);
            return p.ExitCode == 0;
        }
        catch (Exception ex) when (ex is Win32Exception or InvalidOperationException or OperationCanceledException)
        {
            // netsh missing or stuck: assume the rule is there rather than nag on every start.
            AppLog.Warn("firewall_check_failed", new { error = ex.Message });
            return true;
        }
    }

    /// <summary>Adds the rule after the administrator prompt; false when it was declined or failed (RCV-010).</summary>
    public static async Task<bool> AddAsync()
    {
        var exe = Environment.ProcessPath ?? Path.Combine(AppContext.BaseDirectory, "ShihabBarcodeReceiver.exe");
        var delete = $"\"{Netsh}\" advfirewall firewall delete rule name=all program=\"{exe}\" dir=in";
        var add = $"\"{Netsh}\" advfirewall firewall add rule name=\"{Name}\" dir=in action=allow program=\"{exe}\" " +
                  $"protocol=TCP localport={LinkConfig.FirstPort}-{LinkConfig.LastPort} remoteip=localsubnet profile=any enable=yes";
        try
        {
            // cmd /c "<a> & <b>": one prompt for both; the outer quotes are removed by cmd.
            using var p = Process.Start(new ProcessStartInfo("cmd.exe", $"/c \"{delete} & {add}\"")
            {
                UseShellExecute = true,
                Verb = "runas",
                WindowStyle = ProcessWindowStyle.Hidden,
            })!;
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30));
            await p.WaitForExitAsync(cts.Token);
        }
        catch (Win32Exception ex) when (ex.NativeErrorCode == 1223)
        {
            AppLog.Info("firewall_declined", new { code = ReceiverErrors.FirewallBlocked.Code });
            return false;
        }
        catch (Exception ex) when (ex is Win32Exception or InvalidOperationException or OperationCanceledException)
        {
            AppLog.Error(ReceiverErrors.FirewallBlocked.Code, "firewall_add_failed", ex);
            return false;
        }
        var ok = await ExistsAsync();
        if (ok) AppLog.Info("firewall_rule_added", new { });
        else AppLog.Error(ReceiverErrors.FirewallBlocked.Code, "firewall_rule_missing_after_add");
        return ok;
    }
}
