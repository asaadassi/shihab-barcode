using System.IO;
using System.Text;
using BarcodeReceiver.Core;
using Xunit;

namespace BarcodeReceiver.Tests;

public class LinkMessageTests
{
    private static string B64(string s) => Base64Url.Encode(Encoding.UTF8.GetBytes(s));

    [Fact]
    public void Hello_is_parsed_with_its_name()
    {
        var m = LinkMessage.Parse($"HELLO 1 KEY123 a1b2 {B64("جوال أسعد")}");
        var h = Assert.IsType<LinkMessage.Hello>(m);
        Assert.Equal("KEY123", h.Key);
        Assert.Equal("a1b2", h.PhoneId);
        Assert.Equal("جوال أسعد", h.PhoneName);
    }

    [Fact]
    public void Scan_text_keeps_its_enter()
    {
        var m = Assert.IsType<LinkMessage.Scan>(LinkMessage.Parse($"SCAN 42 {B64("2000000000015\n")}"));
        Assert.Equal(42, m.Id);
        Assert.Equal("2000000000015\n", m.Text);
    }

    [Theory]
    [InlineData("SCAN x abc")]
    [InlineData("SCAN -1 abc")]
    [InlineData("HELLO 2 k id n")]
    [InlineData("GET / HTTP/1.1")]
    [InlineData("")]
    public void Anything_else_is_invalid(string line) => Assert.IsType<LinkMessage.Invalid>(LinkMessage.Parse(line));

    [Fact]
    public void Ping_and_carriage_return() => Assert.IsType<LinkMessage.Ping>(LinkMessage.Parse("PING\r"));

    [Fact]
    public void Names_lose_control_characters_and_length()
    {
        Assert.Equal("ab", LinkMessage.Clean("a\u0007b", 10));
        Assert.Equal(5, LinkMessage.Clean("abcdefgh", 5).Length);
    }

    [Fact]
    public void Base64url_round_trip()
    {
        var bytes = new byte[] { 0xFB, 0xFF, 0x00, 0x10, 0x3E };
        Assert.Equal(bytes, Base64Url.TryDecode(Base64Url.Encode(bytes)));
        Assert.Null(Base64Url.TryDecode("a"));
    }
}

public class PairingTests
{
    [Fact]
    public void Uri_has_every_field()
    {
        var uri = PairingUri.Build("PC 1", ["192.168.1.5", "10.0.0.2"], 5870, "ab12", "k_-");
        Assert.Equal("shihabbarcode://pair?v=1&n=PC%201&h=192.168.1.5,10.0.0.2&p=5870&fp=ab12&k=k_-", uri);
    }

    [Fact]
    public void A_resent_scan_is_typed_once()
    {
        var seen = new RecentIds(keep: 2);
        Assert.True(seen.Add("p", 1));
        Assert.False(seen.Add("p", 1));
        Assert.True(seen.Add("q", 1));
        Assert.True(seen.Add("p", 2));
        Assert.True(seen.Add("p", 3));
        Assert.True(seen.Add("p", 1));   // forgotten after `keep` newer ids
    }
}

public class TypingTests
{
    // A US-like layout: digits and lowercase plain, uppercase with Shift, "@" with Shift, Arabic missing,
    // "€" only with Ctrl+Alt (AltGr).
    private static short Us(char c) => c switch
    {
        >= '0' and <= '9' => (short)c,
        >= 'a' and <= 'z' => (short)char.ToUpperInvariant(c),
        >= 'A' and <= 'Z' => (short)(0x100 | c),
        '@' => 0x132,
        '€' => 0x645,
        _ => -1,
    };

    [Fact]
    public void Digits_are_real_keys_then_enter()
    {
        var plan = KeyTyper.Plan("2000000000015\n", Us);
        Assert.Equal(14, plan.Count);
        Assert.All(plan.Take(13), k => Assert.False(k.IsUnicode));
        Assert.Equal((ushort)'2', plan[0].Vk);
        Assert.Equal(KeyTyper.VK_RETURN, plan[13].Vk);
    }

    [Fact]
    public void Shift_letters_tab_and_crlf()
    {
        var plan = KeyTyper.Plan("aB\t@\r\n", Us);
        Assert.Equal(5, plan.Count);
        Assert.False(plan[0].Shift);
        Assert.True(plan[1].Shift);
        Assert.Equal(KeyTyper.VK_TAB, plan[2].Vk);
        Assert.True(plan[3].Shift);
        Assert.Equal(KeyTyper.VK_RETURN, plan[4].Vk);
    }

    [Fact]
    public void Missing_or_altgr_characters_are_unicode()
    {
        var plan = KeyTyper.Plan("س€", Us);
        Assert.All(plan, k => Assert.True(k.IsUnicode));
        Assert.Equal('س', plan[0].Unicode);
    }
}

public class SplitterTests
{
    [Fact]
    public void Scans_end_after_their_newline_across_chunks()
    {
        var s = new ScanSplitter();
        Assert.Empty(s.Add(Encoding.UTF8.GetBytes("12345")));
        var done = s.Add(Encoding.UTF8.GetBytes("67\n89\n0"));
        Assert.Equal(["1234567\n", "89\n"], done);
        Assert.Equal("0", s.Flush());
        Assert.Null(s.Flush());
    }

    [Fact]
    public void Utf8_split_inside_a_letter()
    {
        var s = new ScanSplitter();
        var bytes = Encoding.UTF8.GetBytes("ب\n");
        Assert.Empty(s.Add(bytes[..1]));
        Assert.Equal(["ب\n"], s.Add(bytes[1..]));
    }
}

public class ScanExportTests
{
    private static readonly ExportRow[] Rows =
    [
        new("0012345678905", new DateTime(2026, 10, 23, 9, 5, 0), "واي فاي", "جوال أسعد", "كُتب"),
        new("=1+1", new DateTime(2026, 10, 23, 9, 6, 0), "كبل", "A\"B", "لم يُكتب"),
    ];

    private static byte[] Export(string ext)
    {
        using var ms = new MemoryStream();
        ScanExport.Write(ext, ms, Rows);
        return ms.ToArray();
    }

    [Fact]
    public void Xlsx_is_a_workbook_with_text_cells_right_to_left()
    {
        using var zip = new System.IO.Compression.ZipArchive(new MemoryStream(Export(".xlsx")));
        Assert.NotNull(zip.GetEntry("[Content_Types].xml"));
        Assert.NotNull(zip.GetEntry("xl/workbook.xml"));
        using var reader = new StreamReader(zip.GetEntry("xl/worksheets/sheet1.xml")!.Open());
        var sheet = reader.ReadToEnd();
        System.Xml.Linq.XDocument.Parse(sheet);  // well-formed XML
        Assert.Contains("0012345678905", sheet);
        Assert.Contains("rightToLeft=\"1\"", sheet);
        Assert.Contains("A&quot;B", sheet);
    }

    [Fact]
    public void Csv_has_a_utf8_mark_and_no_formulas()
    {
        var bytes = Export(".csv");
        Assert.Equal(new byte[] { 0xEF, 0xBB, 0xBF }, bytes[..3]);
        var text = Encoding.UTF8.GetString(bytes[3..]);
        Assert.Contains("\"'=1+1\"", text);
        Assert.Contains("\"A\"\"B\"", text);
        Assert.Contains("2026-10-23 09:05:00", text);
    }

    [Fact]
    public void Txt_is_one_code_per_line_and_json_keeps_arabic()
    {
        Assert.Equal("0012345678905\r\n=1+1\r\n", Encoding.UTF8.GetString(Export(".txt")[3..]));
        Assert.Contains("جوال أسعد", Encoding.UTF8.GetString(Export(".json")));
    }
}
