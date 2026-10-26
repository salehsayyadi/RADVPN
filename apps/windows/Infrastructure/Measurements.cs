using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace RADVPN;
internal sealed record ProbeResult(string Id, long? Milliseconds, bool Reachable);
internal static class EndpointProbe
{
    public static (string Host, int Port) Endpoint(JsonNode profile)
    {
        var o = profile["outbound"] ?? throw new InvalidOperationException("invalid_profile");
        var settings = o["settings"]!;
        var servers = (JsonArray?)(settings["vnext"] ?? settings["servers"]);
        var first = servers?.FirstOrDefault() ?? throw new InvalidOperationException("invalid_profile");
        var host = first["address"]!.ToString();
        var port = first["port"]!.GetValue<int>();
        if (string.IsNullOrWhiteSpace(host) || host.Contains('\n') || port < 1 || port > 65535)
            throw new InvalidOperationException("invalid_profile");
        return (host, port);
    }
    public static async Task<ProbeResult> One(JsonNode p, CancellationToken token)
    {
        try
        {
            var (host, port) = Endpoint(p);
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
            timeout.CancelAfter(TimeSpan.FromSeconds(3));
            using var tcp = new TcpClient();
            var watch = Stopwatch.StartNew();
            await tcp.ConnectAsync(host, port, timeout.Token);
            return new(p["id"]!.ToString(), watch.ElapsedMilliseconds, true);
        }
        catch (OperationCanceledException) when (token.IsCancellationRequested) { throw; }
        catch { return new(p["id"]?.ToString() ?? "", null, false); }
    }
    public static async Task<ProbeResult[]> All(IEnumerable<JsonNode> profiles, CancellationToken token)
    {
        using var gate = new SemaphoreSlim(4);
        var tasks = profiles.Take(256).Select(async p => { await gate.WaitAsync(token); try { return await One(p, token); } finally { gate.Release(); } });
        return await Task.WhenAll(tasks);
    }
}
internal sealed class TrafficLedger
{
    long lastUp, lastDown; DateTime lastAt = DateTime.UtcNow;
    public double UpRate
    {
        get; private set;
    }
    public double DownRate
    {
        get; private set;
    }
    public void Reset()
    {
        lastUp = lastDown = 0;
        lastAt = DateTime.UtcNow;
        UpRate = DownRate = 0;
    }
    public void Sample(JsonObject data, long up, long down, DateTime? at = null)
    {
        var now = at ?? DateTime.UtcNow;
        var du = up >= lastUp ? up - lastUp : up;
        var dd = down >= lastDown ? down - lastDown : down;
        var seconds = Math.Max(.1, (now - lastAt).TotalSeconds);
        UpRate = du / seconds;
        DownRate = dd / seconds;
        lastUp = up;
        lastDown = down;
        lastAt = now;
        if (du == 0 && dd == 0)
            return;
        var days = data["trafficDays"] as JsonObject;
        if (days == null)
        {
            days = new();
            data["trafficDays"] = days;
        }
        var key = now.ToString("yyyy-MM-dd");
        var row = days[key] as JsonObject;
        if (row == null)
        {
            row = new JsonObject { ["up"] = 0L, ["down"] = 0L };
            days[key] = row;
        }
        row["up"] = (row["up"]?.GetValue<long>() ?? 0) + du;
        row["down"] = (row["down"]?.GetValue<long>() ?? 0) + dd;
    }
    public static (long Up, long Down) Totals(JsonObject data, int days)
    {
        var rows = data["trafficDays"] as JsonObject;
        if (rows == null)
            return (0, 0);
        long up = 0, down = 0;
        foreach (var pair in rows)
        {
            if (DateTime.TryParse(pair.Key, out var date) && (days == 0 || date.Date >= DateTime.UtcNow.Date.AddDays(-(days - 1))))
            {
                up += pair.Value?["up"]?.GetValue<long>() ?? 0;
                down += pair.Value?["down"]?.GetValue<long>() ?? 0;
            }
        }
        return (up, down);
    }
    public static string Bytes(double count)
    {
        string[] suffix = { "B", "KiB", "MiB", "GiB", "TiB" };
        int i = 0;
        while (count >= 1024 && i < suffix.Length - 1)
        {
            count /= 1024;
            i++;
        }
        return $"{count:0.##} {suffix[i]}";
    }
}
