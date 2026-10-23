using System.Globalization;
using System.IO.Compression;
using System.Security;
using System.Text;
using System.Text.Json;

namespace BarcodeReceiver.Core;

/// <summary>One received scan as exported: the code, when, by which link, from which phone, and whether it was typed.</summary>
internal sealed record ExportRow(string Code, DateTime At, string Via, string Phone, string Status);

/// <summary>
/// Writes the scans of this session to a file: Excel (.xlsx), CSV, plain text (one code per line) or JSON. The
/// format follows the file extension. Excel is the Office Open XML format written directly (one sheet, right to
/// left, codes as text so leading zeros stay), so no extra library ships with the receiver.
/// </summary>
internal static class ScanExport
{
    public static readonly string[] Headers = ["الرمز", "التاريخ والوقت", "الطريقة", "الجوال", "الحالة"];

    /// <summary>Save dialog filter, Excel first.</summary>
    public const string Filter =
        "Excel (*.xlsx)|*.xlsx|CSV (*.csv)|*.csv|نص (*.txt)|*.txt|JSON (*.json)|*.json";

    private const string TimeFormat = "yyyy-MM-dd HH:mm:ss";

    public static void Save(string path, IReadOnlyList<ExportRow> rows)
    {
        // Written beside the target first, then moved over it: a failed export never leaves half a file.
        var temp = path + ".tmp";
        try
        {
            using (var file = File.Create(temp)) Write(Path.GetExtension(path), file, rows);
            File.Move(temp, path, overwrite: true);
        }
        finally
        {
            if (File.Exists(temp)) File.Delete(temp);
        }
    }

    public static void Write(string extension, Stream output, IReadOnlyList<ExportRow> rows)
    {
        switch (extension.ToLowerInvariant())
        {
            case ".xlsx": WriteXlsx(output, rows); break;
            case ".csv": WriteText(output, Csv(rows)); break;
            case ".txt": WriteText(output, string.Join("\r\n", rows.Select(r => r.Code)) + "\r\n"); break;
            case ".json": WriteJson(output, rows); break;
            default: throw new NotSupportedException(extension);
        }
    }

    private static string Time(ExportRow r) => r.At.ToString(TimeFormat, CultureInfo.InvariantCulture);

    private static string[] Cells(ExportRow r) => [r.Code, Time(r), r.Via, r.Phone, r.Status];

    /// <summary>UTF-8 with its mark, so Excel opens Arabic text correctly.</summary>
    private static void WriteText(Stream output, string text)
    {
        var bytes = new UTF8Encoding(encoderShouldEmitUTF8Identifier: true).GetPreamble()
            .Concat(Encoding.UTF8.GetBytes(text)).ToArray();
        output.Write(bytes);
    }

    private static string Csv(IReadOnlyList<ExportRow> rows)
    {
        static string Field(string v)
        {
            // A cell starting with = + - @ would run as a formula in Excel: a leading apostrophe keeps it text.
            if (v.Length > 0 && "=+-@".Contains(v[0])) v = "'" + v;
            return "\"" + v.Replace("\"", "\"\"") + "\"";
        }
        var sb = new StringBuilder();
        sb.Append(string.Join(",", Headers.Select(Field))).Append("\r\n");
        foreach (var r in rows) sb.Append(string.Join(",", Cells(r).Select(Field))).Append("\r\n");
        return sb.ToString();
    }

    private static void WriteJson(Stream output, IReadOnlyList<ExportRow> rows)
    {
        var items = rows.Select(r => new
        {
            code = r.Code,
            time = Time(r),
            via = r.Via,
            phone = r.Phone,
            status = r.Status,
        });
        JsonSerializer.Serialize(output, items, new JsonSerializerOptions
        {
            WriteIndented = true,
            Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        });
    }

    private static void WriteXlsx(Stream output, IReadOnlyList<ExportRow> rows)
    {
        using var zip = new ZipArchive(output, ZipArchiveMode.Create, leaveOpen: true);

        void Entry(string name, string xml)
        {
            using var w = new StreamWriter(zip.CreateEntry(name, CompressionLevel.Optimal).Open(), new UTF8Encoding(false));
            w.Write(xml);
        }

        const string head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

        Entry("[Content_Types].xml", head +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>");

        Entry("_rels/.rels", head +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>");

        Entry("xl/workbook.xml", head +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"Scans\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");

        Entry("xl/_rels/workbook.xml.rels", head +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>");

        // Style 1: bold header row.
        Entry("xl/styles.xml", head +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Almarai\"/></font><font><b/><sz val=\"11\"/><name val=\"Almarai\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>" +
            "</styleSheet>");

        var sheet = new StringBuilder(head);
        sheet.Append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            .Append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\">")
            .Append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
            .Append("<cols><col min=\"1\" max=\"1\" width=\"24\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"20\" customWidth=\"1\"/>")
            .Append("<col min=\"3\" max=\"5\" width=\"16\" customWidth=\"1\"/></cols><sheetData>");
        AppendRow(sheet, 1, Headers, style: 1);
        for (var i = 0; i < rows.Count; i++) AppendRow(sheet, i + 2, Cells(rows[i]), style: 0);
        sheet.Append("</sheetData></worksheet>");
        Entry("xl/worksheets/sheet1.xml", sheet.ToString());
    }

    /// <summary>Every cell as inline text: codes keep their leading zeros and are never turned into numbers.</summary>
    private static void AppendRow(StringBuilder sb, int row, IReadOnlyList<string> cells, int style)
    {
        sb.Append("<row r=\"").Append(row).Append("\">");
        for (var c = 0; c < cells.Count; c++)
        {
            sb.Append("<c r=\"").Append((char)('A' + c)).Append(row).Append("\" t=\"inlineStr\"");
            if (style != 0) sb.Append(" s=\"").Append(style).Append('"');
            sb.Append("><is><t xml:space=\"preserve\">").Append(SecurityElement.Escape(Clean(cells[c]))).Append("</t></is></c>");
        }
        sb.Append("</row>");
    }

    /// <summary>Control characters other than tab and newlines are not allowed in XML.</summary>
    private static string Clean(string v) =>
        new(v.Where(ch => ch >= ' ' || ch is '\t' or '\n' or '\r').ToArray());
}
