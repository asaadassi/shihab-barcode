using System.Text;

namespace BarcodeReceiver.Core;

/// <summary>The pairing QR code: shihabbarcode://pair?v=1&amp;n=&amp;h=&amp;p=&amp;fp=&amp;k= (docs\PC-LINK.md).</summary>
internal static class PairingUri
{
    public const string Prefix = "shihabbarcode://pair?";

    public static string Build(string pcName, IEnumerable<string> hosts, int port, string fingerprint, string key) =>
        Prefix + string.Join('&',
            "v=1",
            "n=" + Uri.EscapeDataString(pcName),
            "h=" + string.Join(',', hosts),
            "p=" + port,
            "fp=" + fingerprint,
            "k=" + key);
}

/// <summary>One line from a phone, parsed (docs\PC-LINK.md, "Messages").</summary>
internal abstract record LinkMessage
{
    public sealed record Hello(string Key, string PhoneId, string PhoneName) : LinkMessage;
    public sealed record Scan(long Id, string Text) : LinkMessage;
    public sealed record Ping : LinkMessage;
    public sealed record Invalid(string Reason) : LinkMessage;

    public static LinkMessage Parse(string line)
    {
        var p = line.TrimEnd('\r').Split(' ');
        switch (p[0])
        {
            case "HELLO" when p.Length == 5 && p[1] == "1":
                var name = Base64Url.TryDecode(p[4]);
                if (name == null || p[2].Length == 0 || p[3].Length is 0 or > 64) return new Invalid("hello");
                return new Hello(p[2], p[3], Clean(Encoding.UTF8.GetString(name), 64));
            case "SCAN" when p.Length == 3 && long.TryParse(p[1], out var id) && id >= 0:
                var text = Base64Url.TryDecode(p[2]);
                return text == null ? new Invalid("scan") : new Scan(id, Encoding.UTF8.GetString(text));
            case "PING" when p.Length == 1:
                return new Ping();
            default:
                return new Invalid(p[0].Length > 12 ? p[0][..12] : p[0]);
        }
    }

    /// <summary>A name for the screen: no control characters, at most <paramref name="max"/> characters.</summary>
    public static string Clean(string s, int max)
    {
        var sb = new StringBuilder(Math.Min(s.Length, max));
        foreach (var c in s)
        {
            if (sb.Length >= max) break;
            if (!char.IsControl(c)) sb.Append(c);
        }
        return sb.ToString().Trim();
    }

    public static string Ok(string pcName) => "OK " + Base64Url.Encode(Encoding.UTF8.GetBytes(pcName));
    public static string Ack(long id) => "ACK " + id;
    public static string Error(long id, string code) => $"ERR {id} {code}";
}

/// <summary>The last scan ids a phone sent, so a scan resent after a reconnect is typed once only.</summary>
internal sealed class RecentIds
{
    private readonly Dictionary<string, Queue<long>> _byPhone = new();
    private readonly int _keep;

    public RecentIds(int keep = 64) => _keep = keep;

    /// <summary>True the first time this phone sends this id.</summary>
    public bool Add(string phoneId, long id)
    {
        lock (_byPhone)
        {
            if (!_byPhone.TryGetValue(phoneId, out var q)) _byPhone[phoneId] = q = new Queue<long>();
            if (q.Contains(id)) return false;
            q.Enqueue(id);
            while (q.Count > _keep) q.Dequeue();
            return true;
        }
    }
}
