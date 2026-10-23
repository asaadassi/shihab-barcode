using System.Threading.Channels;

namespace BarcodeReceiver.Core;

/// <summary>Where a scan came from, for the list on screen.</summary>
internal enum LinkKind { Wifi, Cable, Bluetooth }

/// <summary>A scan as it was typed (or not), for the list on screen and the log.</summary>
internal sealed record ScanEntry(DateTime At, string Text, string Phone, LinkKind Via, TypeResult Result);

/// <summary>
/// One queue for every link: scans are typed one after another in arrival order, never two at once (two phones
/// scanning together would mix their keystrokes otherwise). A single background reader does the typing.
/// </summary>
internal sealed class TypingQueue : IAsyncDisposable
{
    private sealed record Job(string Text, string Phone, LinkKind Via, TaskCompletionSource<TypeResult> Done);

    /// <summary>End-to-end tests (SHIHAB_BARCODE_DRYRUN=1): scans are listed and confirmed but never typed, so a test
    /// cannot type into whatever window the developer has open.</summary>
    private static readonly bool DryRun = Environment.GetEnvironmentVariable("SHIHAB_BARCODE_DRYRUN") == "1";

    private readonly Channel<Job> _jobs = Channel.CreateBounded<Job>(new BoundedChannelOptions(256) { SingleReader = true });
    private readonly Func<int> _keyDelay;
    private readonly Task _worker;

    /// <summary>Raised on the typing thread after each scan.</summary>
    public event Action<ScanEntry>? Typed;

    public TypingQueue(Func<int> keyDelayMs)
    {
        _keyDelay = keyDelayMs;
        _worker = Task.Run(RunAsync);
    }

    public async Task<TypeResult> TypeAsync(string text, string phone, LinkKind via, CancellationToken ct = default)
    {
        var job = new Job(text, phone, via, new TaskCompletionSource<TypeResult>(TaskCreationOptions.RunContinuationsAsynchronously));
        await _jobs.Writer.WriteAsync(job, ct);
        return await job.Done.Task.WaitAsync(ct);
    }

    private async Task RunAsync()
    {
        await foreach (var job in _jobs.Reader.ReadAllAsync())
        {
            TypeResult r;
            try { r = DryRun ? TypeResult.Typed : KeyTyper.Type(job.Text, _keyDelay()); }
            catch (Exception ex)
            {
                AppLog.Error(ReceiverErrors.TypingBlocked.Code, "type_failed", ex);
                r = TypeResult.Blocked;
            }
            if (r == TypeResult.Blocked) AppLog.Warn("type_blocked", new { code = ReceiverErrors.TypingBlocked.Code, via = job.Via.ToString() });
            job.Done.TrySetResult(r);
            try { Typed?.Invoke(new ScanEntry(DateTime.Now, job.Text, job.Phone, job.Via, r)); }
            catch (Exception ex) { AppLog.Error("CMN-001", "typed_event_failed", ex); }
        }
    }

    public async ValueTask DisposeAsync()
    {
        _jobs.Writer.TryComplete();
        await _worker;
    }
}
