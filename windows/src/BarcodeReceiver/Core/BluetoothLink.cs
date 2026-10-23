using System.Text;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Devices.Enumeration;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;

namespace BarcodeReceiver.Core;

internal enum BluetoothState { Off, Unavailable, Connecting, Connected, Failed }

/// <summary>A phone paired with Windows (Settings → Bluetooth), by its Windows device id.</summary>
internal sealed record PairedPhone(string Id, string Name);

/// <summary>
/// The Bluetooth link (docs\PC-LINK.md, "Bluetooth"): this PC connects to the serial port service (SPP) of a paired
/// phone running Barcode in RFCOMM mode, reads its text and types each scan when its line ends. Reconnects on its own
/// (2 s, then up to 15 s apart) while turned on.
/// </summary>
internal sealed class BluetoothLink : IAsyncDisposable
{
    private readonly TypingQueue _queue;
    private CancellationTokenSource? _run;
    private Task? _loop;

    public BluetoothState State { get; private set; } = BluetoothState.Off;
    public string? PhoneName { get; private set; }
    public event Action? Changed;

    public BluetoothLink(TypingQueue queue) => _queue = queue;

    /// <summary>Phones paired with Windows (earbuds, mice and the like left out). Empty when there is no Bluetooth radio.</summary>
    public static async Task<List<PairedPhone>> PairedPhonesAsync()
    {
        try
        {
            var found = await DeviceInformation.FindAllAsync(BluetoothDevice.GetDeviceSelectorFromPairingState(true));
            var phones = new List<PairedPhone>();
            foreach (var d in found.Where(d => !string.IsNullOrWhiteSpace(d.Name)))
            {
                using var dev = await BluetoothDevice.FromIdAsync(d.Id);
                if (dev?.ClassOfDevice.MajorClass == BluetoothMajorClass.Phone) phones.Add(new PairedPhone(d.Id, d.Name));
            }
            return phones.OrderBy(p => p.Name, StringComparer.CurrentCulture).ToList();
        }
        catch (Exception ex)
        {
            AppLog.Error(ReceiverErrors.BluetoothUnavailable.Code, "bt_list_failed", ex);
            return [];
        }
    }

    public async Task StartAsync(PairedPhone phone)
    {
        await StopAsync();
        PhoneName = phone.Name;
        _run = new CancellationTokenSource();
        var ct = _run.Token;
        _loop = Task.Run(() => LoopAsync(phone, ct), ct);
    }

    public async Task StopAsync()
    {
        if (_run == null) return;
        _run.Cancel();
        try { if (_loop != null) await _loop; }
        catch (OperationCanceledException) { /* stopped */ }
        _run.Dispose();
        _run = null;
        _loop = null;
        Set(BluetoothState.Off);
    }

    private async Task LoopAsync(PairedPhone phone, CancellationToken ct)
    {
        var wait = TimeSpan.FromSeconds(2);
        while (!ct.IsCancellationRequested)
        {
            Set(BluetoothState.Connecting);
            try
            {
                await ConnectAndReadAsync(phone, ct);
                wait = TimeSpan.FromSeconds(2);
            }
            catch (OperationCanceledException) { return; }
            catch (Exception ex)
            {
                AppLog.Warn("bt_link_failed", new { code = ReceiverErrors.BluetoothFailed.Code, error = ex.Message });
                Set(BluetoothState.Failed);
            }
            try { await Task.Delay(wait, ct); }
            catch (OperationCanceledException) { return; }
            wait = TimeSpan.FromSeconds(Math.Min(15, wait.TotalSeconds * 2));
        }
    }

    private async Task ConnectAndReadAsync(PairedPhone phone, CancellationToken ct)
    {
        using var device = await BluetoothDevice.FromIdAsync(phone.Id).AsTask(ct)
            ?? throw new InvalidOperationException("device not found");
        var services = await device.GetRfcommServicesForIdAsync(RfcommServiceId.SerialPort, BluetoothCacheMode.Uncached).AsTask(ct);
        var service = services.Services.FirstOrDefault() ?? throw new InvalidOperationException("no serial port service");
        using var socket = new StreamSocket();
        await socket.ConnectAsync(service.ConnectionHostName, service.ConnectionServiceName,
            SocketProtectionLevel.BluetoothEncryptionAllowNullAuthentication).AsTask(ct);
        Set(BluetoothState.Connected);
        AppLog.Info("bt_connected");

        using var reader = new DataReader(socket.InputStream) { InputStreamOptions = InputStreamOptions.Partial };
        var splitter = new ScanSplitter();
        while (!ct.IsCancellationRequested)
        {
            // A scan without a line end (no suffix on the phone) is typed once nothing more arrives for 300 ms.
            var read = reader.LoadAsync(1024).AsTask(ct);
            var first = await Task.WhenAny(read, Task.Delay(300, ct));
            if (first != read)
            {
                if (splitter.Flush() is { } rest) await _queue.TypeAsync(rest, phone.Name, LinkKind.Bluetooth, ct);
                await read;
            }
            var n = read.Result;
            if (n == 0) throw new IOException("the phone closed the link");
            var bytes = new byte[n];
            reader.ReadBytes(bytes);
            foreach (var scan in splitter.Add(bytes))
                await _queue.TypeAsync(scan, phone.Name, LinkKind.Bluetooth, ct);
        }
    }

    private void Set(BluetoothState s)
    {
        if (s == State) return;
        State = s;
        Changed?.Invoke();
    }

    public async ValueTask DisposeAsync() => await StopAsync();
}

/// <summary>Cuts the phone's byte stream into scans: each ends after its "\n" (kept, it is the Enter). UTF-8 safe
/// across chunks. Pure, for tests.</summary>
internal sealed class ScanSplitter
{
    private readonly Decoder _utf8 = new UTF8Encoding(false).GetDecoder();
    private readonly StringBuilder _pending = new();

    public List<string> Add(byte[] bytes)
    {
        var chars = new char[_utf8.GetCharCount(bytes, 0, bytes.Length)];
        _utf8.GetChars(bytes, 0, bytes.Length, chars, 0);
        var done = new List<string>();
        foreach (var c in chars)
        {
            _pending.Append(c);
            if (c == '\n' || _pending.Length >= LinkConfig.MaxScanLength)
            {
                done.Add(_pending.ToString());
                _pending.Clear();
            }
        }
        return done;
    }

    /// <summary>What is left without a line end, or null.</summary>
    public string? Flush()
    {
        if (_pending.Length == 0) return null;
        var s = _pending.ToString();
        _pending.Clear();
        return s;
    }
}
