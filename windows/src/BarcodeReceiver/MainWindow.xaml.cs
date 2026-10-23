using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Globalization;
using System.Windows;
using System.Windows.Media;
using BarcodeReceiver.Core;
using BarcodeReceiver.Infrastructure;
using Shihab.Common;
using Shihab.Common.UI;

namespace BarcodeReceiver;

/// <summary>One row of "recent scans" (memory only, cleared on exit).</summary>
public sealed record ScanRow(string Code, string From, string Time, string Glyph, Brush GlyphBrush, string Spoken);

/// <summary>
/// The receiver's window: the pairing QR code, the state of the three links (Wi-Fi, cable, Bluetooth) and the last
/// scans. Closing it hides it: the receiver keeps working from the tray.
/// </summary>
public partial class MainWindow : Window
{
    private readonly ObservableCollection<ScanRow> _rows = new();
    private readonly List<ExportRow> _session = new();
    private string _qrFor = "";

    private static App A => App.Current;

    public MainWindow()
    {
        InitializeComponent();
        WindowPlacement.Attach(this, App.Info);
        Scans.ItemsSource = _rows;

        A.Server.PhonesChanged += () => Dispatcher.BeginInvoke(Refresh);
        A.Server.WrongKey += () => Dispatcher.BeginInvoke(() => Notice(ReceiverErrors.WrongKey.Text));
        A.Cable.Changed += () => Dispatcher.BeginInvoke(Refresh);
        A.Bluetooth.Changed += () => Dispatcher.BeginInvoke(Refresh);
        A.Queue.Typed += e => Dispatcher.BeginInvoke(() => AddScan(e));
        // Network addresses change (Wi-Fi switched, cable tethering): the QR code follows.
        System.Net.NetworkInformation.NetworkChange.NetworkAddressChanged += (_, _) => Dispatcher.BeginInvoke(Refresh);

        Loaded += async (_, _) =>
        {
            Signature.Show(App.Info);
            CrashHandler.OfferReportAfterCrash(this, App.Info);
            Refresh();
            await LoadPairedPhonesAsync();
            _ = NewsBar.StartAsync(App.Info);
        };
    }

    internal void Notice(string text)
    {
        NoticeText.Text = text;
        NoticeBar.Visibility = Visibility.Visible;
    }

    private void Refresh()
    {
        var ips = NetInfo.LocalIPv4();
        var port = A.Server.Port;
        PcName.Text = Environment.MachineName;
        // One address per line: the caption column beside the code is narrow.
        PcAddress.Text = A.Listening ? string.Join(Environment.NewLine, ips.Select(ip => $"{ip}:{port}")) : "";

        var uri = A.Listening
            ? PairingUri.Build(Environment.MachineName, ips, port, A.Identity.Fingerprint, A.Identity.PairingKey)
            : "";
        if (uri != _qrFor)
        {
            _qrFor = uri;
            QrImage.Source = uri.Length > 0 ? QrRenderer.Render(uri) : null;
        }

        var phones = A.Server.Phones;
        var wifi = phones.Where(p => p.Via == LinkKind.Wifi).ToList();
        var cable = phones.Where(p => p.Via == LinkKind.Cable).ToList();

        if (!A.Listening) State(WifiState, "متوقف", "Danger");
        else if (wifi.Count > 0) State(WifiState, "متصل", "Ok");
        else if (ips.Count == 0) State(WifiState, "لا شبكة", "Muted");
        else State(WifiState, "بانتظار جوال", "Muted");
        WifiDetail.Text = wifi.Count > 0 ? Names(wifi.Select(p => p.Name))
            : ips.Count == 0 ? "الحاسوب غير متصل بشبكة" : "جوال وحاسوب على الشبكة نفسها";

        if (cable.Count > 0) { State(CableState, "متصل", "Ok"); CableDetail.Text = Names(cable.Select(p => p.Name)); }
        else
        {
            switch (A.Cable.State)
            {
                case Core.CableState.NoAdb:
                    State(CableState, "غير متاح", "Danger");
                    CableDetail.Text = ReceiverErrors.AdbMissing.Message;
                    break;
                case Core.CableState.NeedsApproval:
                    State(CableState, "بانتظار السماح", "Warn");
                    CableDetail.Text = "وافق على «السماح بتصحيح USB» على شاشة الجوال";
                    break;
                case Core.CableState.Ready:
                    State(CableState, "جاهز", "Ok");
                    CableDetail.Text = Names(A.Cable.Phones) + ": افتح باركود على الجوال";
                    break;
                default:
                    State(CableState, "لا جوال", "Muted");
                    CableDetail.Text = "صل الجوال بالكبل مع تفعيل تصحيح USB";
                    break;
            }
        }

        var bt = A.Bluetooth;
        switch (bt.State)
        {
            case BluetoothState.Connected:
                State(BtState, "متصل", "Ok");
                BtDetail.Text = Names([bt.PhoneName ?? ""]);
                break;
            case BluetoothState.Connecting:
                State(BtState, "جارٍ الاتصال", "Warn");
                BtDetail.Text = bt.PhoneName ?? "";
                break;
            case BluetoothState.Failed:
                State(BtState, "يعيد المحاولة", "Warn");
                BtDetail.Text = ReceiverErrors.BluetoothFailed.Message;
                break;
            default:
                State(BtState, "متوقف", "Muted");
                BtDetail.Text = "الجوال في وضع «بلوتوث إلى الحاسوب»";
                break;
        }
        var on = bt.State != BluetoothState.Off;
        BtButton.Content = on ? "إيقاف" : "اتصال";
        BtButton.IsEnabled = on || BtPhones.SelectedItem != null;
        BtPhones.IsEnabled = !on;
    }

    /// <summary>Phone names (often Latin) isolated left to right inside the Arabic line.</summary>
    private static string Names(IEnumerable<string> names) => string.Join("، ", names.Select(n => "⁦" + n + "⁩"));

    private void State(System.Windows.Controls.TextBlock t, string text, string brush)
    {
        t.Text = text;
        t.SetResourceReference(System.Windows.Controls.TextBlock.ForegroundProperty, brush);
    }

    private void AddScan(ScanEntry e)
    {
        var code = e.Text.TrimEnd('\r', '\n', '\t');
        var via = e.Via switch { LinkKind.Cable => "كبل", LinkKind.Bluetooth => "بلوتوث", _ => "واي فاي" };
        var ok = e.Result == TypeResult.Typed;
        var brush = (Brush)FindResource(ok ? "Ok" : "Warn");
        var time = e.At.ToString("HH:mm:ss", CultureInfo.InvariantCulture);
        var phone = "⁦" + e.Phone + "⁩";
        var from = ok ? $"{via} · {phone}" : $"{via} · {phone} · {(e.Result == TypeResult.Busy ? "لم يُكتب: مفتاح مضغوط" : "لم يُكتب")}";
        _rows.Insert(0, new ScanRow(code, from, time, ok ? "" : "", brush, $"{code}، {from}، {time}"));
        while (_rows.Count > LinkConfig.RecentScans) _rows.RemoveAt(_rows.Count - 1);
        ScansEmpty.Visibility = Visibility.Collapsed;

        var status = e.Result switch
        {
            TypeResult.Typed => "كُتب",
            TypeResult.Busy => "لم يُكتب: مفتاح مضغوط",
            _ => "لم يُكتب",
        };
        _session.Add(new ExportRow(code, e.At, via, e.Phone, status));
        if (_session.Count > LinkConfig.SessionScans) _session.RemoveAt(0);
        ExportButton.IsEnabled = true;
        if (e.Result == TypeResult.Blocked) Notice(ReceiverErrors.TypingBlocked.Text);
    }

    private async Task LoadPairedPhonesAsync()
    {
        var phones = await BluetoothLink.PairedPhonesAsync();
        BtPhones.ItemsSource = phones;
        var saved = ReceiverSettings.Current.BluetoothPhoneId;
        BtPhones.SelectedItem = phones.FirstOrDefault(p => p.Id == saved) ?? phones.FirstOrDefault();
        BtPhones.SelectionChanged += (_, _) => Refresh();
        if (phones.Count == 0) BtDetail.Text = ReceiverErrors.BluetoothUnavailable.Message;
        Refresh();
    }

    private async void OnBluetoothToggle(object sender, RoutedEventArgs e)
    {
        BtButton.IsEnabled = false;
        if (A.Bluetooth.State != BluetoothState.Off)
        {
            await A.Bluetooth.StopAsync();
            ReceiverSettings.Current.BluetoothPhoneId = null;
        }
        else if (BtPhones.SelectedItem is PairedPhone phone)
        {
            await A.Bluetooth.StartAsync(phone);
            ReceiverSettings.Current.BluetoothPhoneId = phone.Id;
            ReceiverSettings.Current.BluetoothPhoneName = phone.Name;
        }
        ReceiverSettings.Save();
        Refresh();
    }

    private void OnRenewKey(object sender, RoutedEventArgs e)
    {
        const string text = "يتغيّر رمز الاقتران، ولا يتصل بعدها بالواي فاي أو الكبل إلا جوال يمسح الرمز الجديد.";
        if (!ConfirmWindow.Ask(this, App.Info, "إلغاء اقتران كل الجوالات", text, "رمز جديد", danger: true)) return;
        A.RenewPairingKey();
        Refresh();
    }

    private void OnExport(object sender, RoutedEventArgs e)
    {
        var dialog = new Microsoft.Win32.SaveFileDialog
        {
            Title = "تصدير قراءات الجلسة",
            Filter = ScanExport.Filter,
            FileName = "barcode-scans-" + DateTime.Now.ToString("yyyy-MM-dd-HHmm", CultureInfo.InvariantCulture),
            AddExtension = true,
        };
        if (dialog.ShowDialog(this) != true) return;

        try
        {
            ScanExport.Save(dialog.FileName, _session.ToList());
            AppLog.Info("export", new { rows = _session.Count, format = Path.GetExtension(dialog.FileName) });
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or NotSupportedException)
        {
            AppLog.Error(ReceiverErrors.ExportFailed.Code, "export_failed", ex);
            Notice(ReceiverErrors.ExportFailed.Text);
        }
    }

    private void OnAbout(object sender, RoutedEventArgs e) => AboutWindow.Open(this, App.Info);

    private void OnHideNotice(object sender, RoutedEventArgs e) => NoticeBar.Visibility = Visibility.Collapsed;

    protected override void OnClosing(CancelEventArgs e)
    {
        // The receiver keeps running in the tray; "Exit" in the tray menu ends it.
        e.Cancel = true;
        Hide();
        A.HiddenToTray();
        base.OnClosing(e);
    }
}
