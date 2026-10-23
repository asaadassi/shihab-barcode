using System.Diagnostics;
using System.Text;

namespace BarcodeReceiver.Core;

internal enum CableState { NoAdb, NoPhone, NeedsApproval, Ready }

/// <summary>
/// The cable link: every few seconds, for each phone adb sees with USB debugging allowed, `adb reverse tcp:P tcp:P`
/// so the phone's 127.0.0.1:P reaches this PC's listener over the cable (no network, no firewall prompt).
/// The adb wrapper follows Shihab Cam's (ShihabAdasa\windows\ShihabCam\Core\Adb.cs): output read asynchronously and
/// the process awaited with a timeout, because `adb start-server` leaves a daemon holding the pipe.
/// </summary>
internal sealed class AdbBridge : IAsyncDisposable
{
    private readonly int _port;
    private readonly CancellationTokenSource _stop = new();
    private readonly HashSet<string> _reversed = new();
    private Task? _loop;

    public CableState State { get; private set; } = CableState.NoPhone;
    /// <summary>Phones on the cable: model names.</summary>
    public IReadOnlyList<string> Phones { get; private set; } = [];
    public event Action? Changed;

    public AdbBridge(int port) => _port = port;

    public static string? AdbPath
    {
        get
        {
            var local = Path.Combine(AppContext.BaseDirectory, "adb", "adb.exe");
            return File.Exists(local) ? local : null;
        }
    }

    public void Start()
    {
        if (AdbPath == null)
        {
            AppLog.Warn("adb_missing", new { code = ReceiverErrors.AdbMissing.Code });
            Set(CableState.NoAdb, []);
            return;
        }
        _loop = Task.Run(LoopAsync);
    }

    private async Task LoopAsync()
    {
        while (!_stop.IsCancellationRequested)
        {
            try { await PollAsync(); }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                AppLog.Error(ReceiverErrors.AdbMissing.Code, "adb_poll_failed", ex);
            }
            try { await Task.Delay(LinkConfig.AdbPollInterval, _stop.Token); }
            catch (OperationCanceledException) { return; }
        }
    }

    private async Task PollAsync()
    {
        var (ok, output) = await RunAsync("devices -l", 8000);
        if (!ok) { Set(CableState.NoAdb, []); return; }
        var ready = new List<(string Serial, string Model)>();
        var waiting = false;
        foreach (var line in output.Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            var p = line.Split(' ', StringSplitOptions.RemoveEmptyEntries);
            if (p.Length < 2 || line.StartsWith("List of", StringComparison.Ordinal)) continue;
            if (p[1] == "unauthorized") waiting = true;
            if (p[1] != "device") continue;
            var model = p.FirstOrDefault(x => x.StartsWith("model:", StringComparison.Ordinal))?[6..].Replace('_', ' ') ?? p[0];
            ready.Add((p[0], model));
        }
        foreach (var (serial, _) in ready)
        {
            var (listed, list) = await RunAsync($"-s \"{serial}\" reverse --list", 5000);
            if (listed && list.Contains($"tcp:{_port} tcp:{_port}", StringComparison.Ordinal)) continue;
            var (done, _) = await RunAsync($"-s \"{serial}\" reverse tcp:{_port} tcp:{_port}", 5000);
            if (done && _reversed.Add(serial)) AppLog.Info("cable_ready", new { port = _port });
        }
        _reversed.IntersectWith(ready.Select(r => r.Serial));
        Set(ready.Count > 0 ? CableState.Ready : waiting ? CableState.NeedsApproval : CableState.NoPhone,
            ready.Select(r => r.Model).ToList());
    }

    private void Set(CableState state, IReadOnlyList<string> phones)
    {
        var changed = state != State || !phones.SequenceEqual(Phones);
        State = state;
        Phones = phones;
        if (changed) Changed?.Invoke();
    }

    private async Task<(bool ok, string output)> RunAsync(string args, int timeoutMs)
    {
        var exe = AdbPath;
        if (exe == null) return (false, "");
        var psi = new ProcessStartInfo(exe, args)
        {
            UseShellExecute = false, CreateNoWindow = true,
            RedirectStandardOutput = true, RedirectStandardError = true,
        };
        using var p = new Process { StartInfo = psi, EnableRaisingEvents = true };
        var sb = new StringBuilder();
        var exited = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        p.Exited += (_, _) => exited.TrySetResult();
        p.OutputDataReceived += (_, e) => { if (e.Data != null) lock (sb) sb.AppendLine(e.Data); };
        p.ErrorDataReceived += (_, e) => { if (e.Data != null) lock (sb) sb.AppendLine(e.Data); };
        try
        {
            p.Start();
            p.BeginOutputReadLine();
            p.BeginErrorReadLine();
        }
        catch (System.ComponentModel.Win32Exception ex)
        {
            AppLog.Error(ReceiverErrors.AdbMissing.Code, "adb_start_failed", ex);
            return (false, "");
        }
        using var cts = CancellationTokenSource.CreateLinkedTokenSource(_stop.Token);
        cts.CancelAfter(timeoutMs);
        try { await exited.Task.WaitAsync(cts.Token); }
        catch (OperationCanceledException)
        {
            try { p.Kill(); }
            catch (InvalidOperationException) { /* it ended meanwhile: nothing to stop */ }
            return (false, "");
        }
        await Task.Delay(30, CancellationToken.None);   // let the async readers flush
        lock (sb) return (p.ExitCode == 0, sb.ToString());
    }

    public async ValueTask DisposeAsync()
    {
        _stop.Cancel();
        if (_loop != null)
        {
            try { await _loop; }
            catch (OperationCanceledException) { /* closing */ }
        }
        _stop.Dispose();
    }
}
