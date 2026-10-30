using System;
using System.Diagnostics;
namespace RADVPN;
/** Runtime authentication freshness is monotonic, never extended by wall-clock rollback. */
internal sealed class AccountLease
{
    long? verified;
    internal void MarkVerified() => verified = Stopwatch.GetTimestamp();
    internal void Reset() => verified = null;
    internal bool IsFresh => Within(verified, Stopwatch.GetTimestamp(), Stopwatch.Frequency);
    internal static bool Within(long? stamp, long now, long frequency) => stamp.HasValue && frequency > 0 && now >= stamp.Value && ((double)now - stamp.Value) / frequency <= 60;
}
