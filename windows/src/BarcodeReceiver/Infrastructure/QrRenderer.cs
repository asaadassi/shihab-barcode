using System.Windows.Media;
using System.Windows.Media.Imaging;
using ZXing;
using ZXing.QrCode;
using ZXing.QrCode.Internal;

namespace BarcodeReceiver.Infrastructure;

/// <summary>The pairing QR code (ZXing.Net, Apache-2.0) as a crisp bitmap: dark modules on a light quiet zone, as in
/// Shihab Remote's QrRenderer, so any phone camera reads it.</summary>
internal static class QrRenderer
{
    public static BitmapSource? Render(string text, int modulePixels = 6)
    {
        try
        {
            var hints = new Dictionary<EncodeHintType, object>
            {
                [EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M,
                [EncodeHintType.MARGIN] = 4,
                [EncodeHintType.CHARACTER_SET] = "UTF-8",
            };
            var m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
            int w = m.Width, h = m.Height;
            int size = w * modulePixels;
            var pixels = new byte[size * size];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    byte v = m[x, y] ? (byte)0 : (byte)255;
                    for (int dy = 0; dy < modulePixels; dy++)
                        Array.Fill(pixels, v, (y * modulePixels + dy) * size + x * modulePixels, modulePixels);
                }
            var bmp = BitmapSource.Create(size, size, 96, 96, PixelFormats.Gray8, null, pixels, size);
            bmp.Freeze();
            return bmp;
        }
        catch (Exception ex) when (ex is WriterException or ArgumentException)
        {
            AppLog.Error("CMN-001", "qr_failed", ex, new { length = text.Length });
            return null;
        }
    }
}
