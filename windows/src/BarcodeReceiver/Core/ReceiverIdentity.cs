using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;

namespace BarcodeReceiver.Core;

/// <summary>
/// This PC's identity for the Wi-Fi and cable link: a self-signed ECDSA P-256 certificate (the phone pins its
/// SHA-256 from the QR code) and the pairing key the phone must send. Kept in identity.dat, protected with DPAPI for
/// the current Windows user; never leaves this PC except the fingerprint and key inside the QR code.
/// </summary>
internal sealed class ReceiverIdentity : IDisposable
{
    public X509Certificate2 Certificate { get; }
    /// <summary>Lowercase hex SHA-256 of the certificate (DER).</summary>
    public string Fingerprint { get; }
    /// <summary>16 random bytes, base64url.</summary>
    public string PairingKey { get; }

    private ReceiverIdentity(X509Certificate2 cert, string key)
    {
        Certificate = cert;
        Fingerprint = Convert.ToHexString(SHA256.HashData(cert.RawData)).ToLowerInvariant();
        PairingKey = key;
    }

    private sealed record Stored(string Pfx, string Password, string Key);

    /// <summary>The saved identity, or a new one (first run, or a damaged file: RCV-002, phones pair again).</summary>
    public static ReceiverIdentity LoadOrCreate(out bool renewed)
    {
        renewed = false;
        if (File.Exists(AppPaths.Identity))
        {
            try
            {
                var plain = ProtectedData.Unprotect(File.ReadAllBytes(AppPaths.Identity), null, DataProtectionScope.CurrentUser);
                var s = JsonSerializer.Deserialize<Stored>(plain) ?? throw new InvalidDataException("empty identity");
                return new ReceiverIdentity(Load(Convert.FromBase64String(s.Pfx), s.Password), s.Key) { _password = s.Password };
            }
            catch (Exception ex) when (ex is CryptographicException or InvalidDataException or JsonException or FormatException)
            {
                AppLog.Error(ReceiverErrors.IdentityReset.Code, "identity_unreadable", ex);
                renewed = true;
            }
        }
        return Create(NewKey());
    }

    /// <summary>"Unpair all phones": the same certificate with a new key, so every phone must scan the new QR code.</summary>
    public ReceiverIdentity WithNewKey()
    {
        var pfx = Certificate.Export(X509ContentType.Pfx, _password);
        var id = new ReceiverIdentity(Load(pfx, _password), NewKey()) { _password = _password };
        id.Save();
        return id;
    }

    private string _password = "";

    private static ReceiverIdentity Create(string key)
    {
        using var ecdsa = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var req = new CertificateRequest("CN=Shihab Barcode Receiver", ecdsa, HashAlgorithmName.SHA256);
        req.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature, critical: true));
        using var self = req.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(20));
        var password = Convert.ToBase64String(RandomNumberGenerator.GetBytes(24));
        var pfx = self.Export(X509ContentType.Pfx, password);
        var id = new ReceiverIdentity(Load(pfx, password), key) { _password = password };
        id.Save();
        AppLog.Info("identity_created", new { fp = id.Fingerprint[..12] });
        return id;
    }

    // A key in the user's key store (not ephemeral): Schannel needs it to serve TLS.
    private static X509Certificate2 Load(byte[] pfx, string password) =>
        new(pfx, password, X509KeyStorageFlags.UserKeySet | X509KeyStorageFlags.Exportable);

    private void Save()
    {
        var json = JsonSerializer.SerializeToUtf8Bytes(new Stored(
            Convert.ToBase64String(Certificate.Export(X509ContentType.Pfx, _password)), _password, PairingKey));
        Directory.CreateDirectory(AppPaths.Data);
        var tmp = AppPaths.Identity + ".tmp";
        File.WriteAllBytes(tmp, ProtectedData.Protect(json, null, DataProtectionScope.CurrentUser));
        File.Move(tmp, AppPaths.Identity, overwrite: true);
    }

    private static string NewKey() => Base64Url.Encode(RandomNumberGenerator.GetBytes(16));

    public void Dispose() => Certificate.Dispose();
}

/// <summary>base64url without padding (RFC 4648 §5), as the phone writes it.</summary>
internal static class Base64Url
{
    public static string Encode(ReadOnlySpan<byte> data) =>
        Convert.ToBase64String(data).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    public static byte[]? TryDecode(string s)
    {
        if (s.Length == 0) return [];
        var b = s.Replace('-', '+').Replace('_', '/');
        b += (b.Length % 4) switch { 2 => "==", 3 => "=", 0 => "", _ => "!" };
        try { return Convert.FromBase64String(b); }
        catch (FormatException) { return null; }
    }
}
