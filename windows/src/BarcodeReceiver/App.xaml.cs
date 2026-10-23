using System.Windows;
using System.Windows.Threading;
using BarcodeReceiver.Core;
using Shihab.Common;
using Shihab.Common.UI;
using Shihab.Updater;
using Shihab.Updater.UI;

namespace BarcodeReceiver;

/// <summary>
/// Start-up order: digits and calendar → log → settings → crash handler → single instance (a second start shows the
/// first copy's window) → identity → typing queue and the three links → main window → tray icon → updates.
/// The receiver keeps running in the tray when its window is closed: phones scan while another app has the focus.
/// </summary>
public partial class App : Application
{
    public static string Version { get; } =
        (typeof(App).Assembly.GetName().Version ?? new Version(0, 1, 0)).ToString(3);

    internal static ShihabAppInfo Info { get; private set; } = null!;
    internal static new App Current => (App)Application.Current;

    internal ReceiverIdentity Identity { get; private set; } = null!;
    internal TypingQueue Queue { get; private set; } = null!;
    internal LinkServer Server { get; private set; } = null!;
    internal AdbBridge Cable { get; private set; } = null!;
    internal BluetoothLink Bluetooth { get; private set; } = null!;
    internal bool Listening { get; private set; }

    private SingleInstance? _single;
    private TrayIcon? _tray;
    private Updater? _updater;
    private bool _trayHintShown;

    protected override void OnStartup(StartupEventArgs e)
    {
        ShihabCulture.Apply();   // 0-9 digits and Gregorian dates on any Windows region (before windows/threads)
        AppLog.Init(AppPaths.Logs);
        base.OnStartup(e);
        ReceiverSettings.Load();
        Info = new ShihabAppInfo
        {
            AppId = AppPaths.AppId, AppName = "Barcode Receiver", Version = Version, Language = "ar",
            ProductName = "باركود", ProductDescription = "مستقبل الماسح على الحاسوب",
            LogoUri = "pack://application:,,,/Assets/logo.png", LogsFolder = AppLog.Folder,
            ReleasesUrl = $"https://github.com/{LinkConfig.ReleasesOwner}/{LinkConfig.ReleasesRepo}/releases",
            ShowPrivacy = owner => new PrivacyWindow { Owner = owner }.ShowDialog(),
            CheckForUpdates = () => _ = CheckUpdatesAsync(manual: true),
            OfferRunAtStartup = true,
            Log = (evt, fields) => AppLog.Write(evt is "crash" or "error" ? "error" : "info", evt, fields),
        };
        CrashHandler.Install(this, Info);

        _single = SingleInstance.Acquire(AppPaths.AppId, () => Dispatcher.BeginInvoke(ShowMain));
        if (_single == null) { Shutdown(); return; }
        AppLog.Info("start", new { version = Version, os = Environment.OSVersion.VersionString });

        Identity = ReceiverIdentity.LoadOrCreate(out var renewed);
        Queue = new TypingQueue(() => ReceiverSettings.Current.KeyDelayMs);
        Server = new LinkServer(() => Identity, () => Environment.MachineName, Queue);
        Listening = Server.Start();
        Cable = new AdbBridge(Listening ? Server.Port : LinkConfig.FirstPort);
        if (Listening) Cable.Start();
        Bluetooth = new BluetoothLink(Queue);

        var main = new MainWindow();
        MainWindow = main;
        // Started with Windows: straight to the tray, the window opens from the tray icon.
        if (!e.Args.Contains(Info.StartupArgument)) main.Show();
        if (renewed) main.Notice(ReceiverErrors.IdentityReset.Text);
        if (!Listening) main.Notice(ReceiverErrors.PortsBusy.Text);
        if (ReceiverSettings.Current.BluetoothPhoneId is { } id)
            _ = Bluetooth.StartAsync(new PairedPhone(id, ReceiverSettings.Current.BluetoothPhoneName ?? ""));

        _tray = new TrayIcon(Info, "pack://application:,,,/Assets/app.ico", ShowMain, TrayItems);

        _updater = new Updater(new UpdateOptions
        {
            Owner = LinkConfig.ReleasesOwner, Repo = LinkConfig.ReleasesRepo, TagPrefix = LinkConfig.ReleasesTagPrefix,
            AppId = Info.AppId, AppName = Info.AppName, CurrentVersion = Version,
            AssetPattern = "ShihabBarcodeReceiver-Setup-*.exe", Language = "ar",
            PublicKeys = ShihabLinks.UpdatePublicKeys, DataFolders = [AppPaths.Data],
            Log = (evt, fields) => AppLog.Info(evt, fields),
        });
        var later = new DispatcherTimer { Interval = TimeSpan.FromSeconds(30) };
        later.Tick += async (_, _) =>
        {
            later.Interval = TimeSpan.FromHours(6);
            await CheckUpdatesAsync(manual: false);
        };
        later.Start();
        Dispatcher.BeginInvoke(async () => await UpdateWindow.ShowWhatsNewIfUpdatedAsync(MainWindow is { IsVisible: true } w ? w : null, _updater),
            DispatcherPriority.ApplicationIdle);
    }

    /// <summary>"Unpair all phones": a new pairing key, so the QR code changes and old phones are refused.</summary>
    internal void RenewPairingKey()
    {
        var old = Identity;
        Identity = old.WithNewKey();
        AppLog.Info("pairing_key_renewed");
        old.Dispose();
    }

    /// <summary>The tray menu: the pairing code, then open, about, exit.</summary>
    private IEnumerable<TrayMenuItem> TrayItems()
    {
        yield return new TrayMenuItem("رمز الاقتران", null, ShowMain);
        yield return TrayMenuItem.Separator;
        yield return new TrayMenuItem("فتح", null, ShowMain);
        yield return new TrayMenuItem("حول", null, () => AboutWindow.Open(MainWindow is { IsVisible: true } w ? w : null, Info));
        yield return new TrayMenuItem("خروج", null, ExitApp);
    }

    internal void ShowMain()
    {
        if (MainWindow is not { } w) return;
        if (!w.IsVisible) w.Show();
        if (w.WindowState == WindowState.Minimized) w.WindowState = WindowState.Normal;
        w.Activate();
    }

    /// <summary>Closing the window keeps the receiver in the tray; said once per run.</summary>
    internal void HiddenToTray()
    {
        if (_trayHintShown) return;
        _trayHintShown = true;
        _tray?.Balloon("باركود يعمل بجانب الساعة ويستقبل المسح. للخروج: انقر الأيقونة بالزر الأيمن ثم «خروج».");
    }

    private async Task CheckUpdatesAsync(bool manual)
    {
        if (_updater == null) return;
        var owner = MainWindow is { IsVisible: true } m ? m : null;
        var outcome = await UpdateWindow.CheckAndShowAsync(owner, _updater, manual);
        if (outcome != UpdateWindowResult.Closed) ExitApp();   // the installer runs, or a required update was refused
    }

    internal async void ExitApp()
    {
        AppLog.Info("exit");
        _tray?.Dispose();
        try
        {
            await Bluetooth.DisposeAsync();
            await Cable.DisposeAsync();
            await Server.DisposeAsync();
            await Queue.DisposeAsync();
        }
        catch (Exception ex) { AppLog.Error("CMN-001", "shutdown_failed", ex); }
        Identity.Dispose();
        _single?.Dispose();
        Shutdown();
    }
}
