using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Text;

namespace BarcodeReceiver.Core;

/// <summary>A phone connected over Wi-Fi or the cable.</summary>
internal sealed record PhoneLink(string PhoneId, string Name, LinkKind Via, DateTime Since);

/// <summary>
/// The Wi-Fi and cable listener (docs\PC-LINK.md): TCP 5870 (or the next free port up to 5879), TLS with this PC's
/// certificate, then the pairing key, then scan lines typed through the <see cref="TypingQueue"/>. A connection from
/// 127.0.0.1 is the cable (adb reverse); any other is Wi-Fi.
/// </summary>
internal sealed class LinkServer : IAsyncDisposable
{
    private readonly Func<ReceiverIdentity> _identity;
    private readonly Func<string> _pcName;
    private readonly TypingQueue _queue;
    private readonly RecentIds _seen = new();
    private readonly CancellationTokenSource _stop = new();
    private readonly Dictionary<Guid, PhoneLink> _phones = new();
    private TcpListener? _listener;
    private Task? _accept;
    private int _active;

    public int Port { get; private set; }

    /// <summary>Raised on a pool thread whenever a phone connects or leaves.</summary>
    public event Action? PhonesChanged;

    /// <summary>Raised when a phone was refused for an old pairing key (RCV-007).</summary>
    public event Action? WrongKey;

    public LinkServer(Func<ReceiverIdentity> identity, Func<string> pcName, TypingQueue queue)
    {
        _identity = identity;
        _pcName = pcName;
        _queue = queue;
    }

    public IReadOnlyList<PhoneLink> Phones
    {
        get { lock (_phones) return _phones.Values.OrderBy(p => p.Since).ToList(); }
    }

    /// <summary>Opens the first free port of 5870 to 5879; false when all are busy (RCV-001).</summary>
    public bool Start()
    {
        for (var port = LinkConfig.FirstPort; port <= LinkConfig.LastPort; port++)
        {
            try
            {
                var l = new TcpListener(IPAddress.Any, port) { ExclusiveAddressUse = true };
                l.Start();
                _listener = l;
                Port = port;
                _accept = Task.Run(AcceptLoopAsync);
                AppLog.Info("listening", new { port });
                return true;
            }
            catch (SocketException) { /* this port is taken: try the next one */ }
        }
        AppLog.Error(ReceiverErrors.PortsBusy.Code, "ports_busy");
        return false;
    }

    private async Task AcceptLoopAsync()
    {
        while (!_stop.IsCancellationRequested)
        {
            TcpClient client;
            try { client = await _listener!.AcceptTcpClientAsync(_stop.Token); }
            catch (OperationCanceledException) { return; }
            catch (Exception ex) when (ex is SocketException or ObjectDisposedException)
            {
                if (_stop.IsCancellationRequested) return;
                AppLog.Warn("accept_failed", new { error = ex.Message });
                continue;
            }
            if (Interlocked.Increment(ref _active) > LinkConfig.MaxPhones)
            {
                Interlocked.Decrement(ref _active);
                client.Dispose();
                continue;
            }
            _ = Task.Run(async () =>
            {
                try { await ServeAsync(client); }
                finally { Interlocked.Decrement(ref _active); }
            });
        }
    }

    private async Task ServeAsync(TcpClient client)
    {
        var key = Guid.NewGuid();
        var remote = (client.Client.RemoteEndPoint as IPEndPoint)?.Address;
        var via = remote != null && IPAddress.IsLoopback(remote) ? LinkKind.Cable : LinkKind.Wifi;
        try
        {
            using var _ = client;
            client.NoDelay = true;
            await using var ssl = new SslStream(client.GetStream(), leaveInnerStreamOpen: false);
            using (var hs = CancellationTokenSource.CreateLinkedTokenSource(_stop.Token))
            {
                hs.CancelAfter(LinkConfig.HandshakeTimeout);
                await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                {
                    ServerCertificate = _identity().Certificate,
                    ClientCertificateRequired = false,
                    EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13,
                }, hs.Token);
            }

            var reader = new LineReader(ssl);
            var first = await reader.ReadAsync(LinkConfig.HandshakeTimeout, _stop.Token);
            if (first == null || LinkMessage.Parse(first) is not LinkMessage.Hello hello) return;
            if (!KeyMatches(hello.Key))
            {
                await WriteAsync(ssl, "ERR AUTH");
                AppLog.Warn("wrong_key", new { code = ReceiverErrors.WrongKey.Code, via = via.ToString() });
                WrongKey?.Invoke();
                return;
            }
            await WriteAsync(ssl, LinkMessage.Ok(_pcName()));
            var name = hello.PhoneName.Length > 0 ? hello.PhoneName : "جوال";
            lock (_phones) _phones[key] = new PhoneLink(hello.PhoneId, name, via, DateTime.Now);
            PhonesChanged?.Invoke();
            AppLog.Info("phone_connected", new { via = via.ToString() });

            while (!_stop.IsCancellationRequested)
            {
                var line = await reader.ReadAsync(LinkConfig.IdleTimeout, _stop.Token);
                if (line == null) return;
                switch (LinkMessage.Parse(line))
                {
                    case LinkMessage.Ping:
                        await WriteAsync(ssl, "PONG");
                        break;
                    case LinkMessage.Scan scan when scan.Text.Length > LinkConfig.MaxScanLength:
                        AppLog.Warn("scan_too_long", new { code = ReceiverErrors.ScanTooLong.Code, scan.Text.Length });
                        await WriteAsync(ssl, LinkMessage.Error(scan.Id, "TOO_LONG"));
                        break;
                    case LinkMessage.Scan scan:
                        // A resend after a reconnect: already typed, only confirm it.
                        if (!_seen.Add(hello.PhoneId, scan.Id)) { await WriteAsync(ssl, LinkMessage.Ack(scan.Id)); break; }
                        var r = await _queue.TypeAsync(scan.Text, name, via, _stop.Token);
                        await WriteAsync(ssl, r == TypeResult.Typed ? LinkMessage.Ack(scan.Id)
                            : LinkMessage.Error(scan.Id, r == TypeResult.Busy ? "BUSY" : "BLOCKED"));
                        break;
                    default:
                        return;   // not our protocol
                }
            }
        }
        catch (Exception ex) when (ex is IOException or AuthenticationException or SocketException
                                       or OperationCanceledException or ObjectDisposedException)
        {
            // The phone left, the network dropped or the app is closing: a normal end of a link.
            AppLog.Info("link_closed", new { via = via.ToString(), reason = ex.GetType().Name });
        }
        finally
        {
            bool removed;
            lock (_phones) removed = _phones.Remove(key);
            if (removed) PhonesChanged?.Invoke();
        }
    }

    private bool KeyMatches(string sent)
    {
        var expected = Encoding.ASCII.GetBytes(_identity().PairingKey);
        var got = Encoding.ASCII.GetBytes(sent);
        return expected.Length == got.Length && CryptographicOperations.FixedTimeEquals(expected, got);
    }

    private static async Task WriteAsync(Stream s, string line)
    {
        await s.WriteAsync(Encoding.UTF8.GetBytes(line + "\n"));
        await s.FlushAsync();
    }

    public async ValueTask DisposeAsync()
    {
        _stop.Cancel();
        _listener?.Stop();
        if (_accept != null)
        {
            try { await _accept; }
            catch (Exception ex) { AppLog.Warn("accept_stop", new { error = ex.Message }); }
        }
        _stop.Dispose();
    }
}

/// <summary>Reads "\n"-ended UTF-8 lines of at most 16 KB (a longer line ends the link), with an idle timeout.</summary>
internal sealed class LineReader
{
    private readonly Stream _s;
    private readonly byte[] _buf = new byte[4096];
    private int _len, _pos;

    public LineReader(Stream s) => _s = s;

    /// <summary>The next line without "\n", or null at the end of the stream, on timeout or on a too long line.</summary>
    public async Task<string?> ReadAsync(TimeSpan timeout, CancellationToken ct)
    {
        using var line = new MemoryStream();
        using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(timeout);
        while (true)
        {
            if (_pos == _len)
            {
                try { _len = await _s.ReadAsync(_buf, cts.Token); }
                catch (OperationCanceledException) when (!ct.IsCancellationRequested) { return null; }
                _pos = 0;
                if (_len == 0) return null;
            }
            var nl = Array.IndexOf(_buf, (byte)'\n', _pos, _len - _pos);
            var end = nl < 0 ? _len : nl;
            line.Write(_buf, _pos, end - _pos);
            _pos = nl < 0 ? _len : nl + 1;
            if (line.Length > LinkConfig.MaxLineBytes) return null;
            if (nl >= 0) return Encoding.UTF8.GetString(line.GetBuffer(), 0, (int)line.Length);
        }
    }
}
