using System;
using System.Text.Json.Nodes;
namespace RADVPN;
internal static class AccountDevicePage
{
    static bool Valid(string value) => Guid.TryParse(value, out var id) && id.ToString("D") == value;
    internal static string Path(string? after = null)
    {
        if (after != null && !Valid(after)) throw new InvalidOperationException("account_unavailable");
        return "v1/account/sessions" + (after == null ? "" : "?after=" + after);
    }
    internal static string? Validate(JsonObject response, string? after = null)
    {
        var rows = response["sessions"]?.AsArray() ?? throw new InvalidOperationException("account_unavailable");
        if (rows.Count > 100 || !Valid(response["current_session_id"]?.ToString() ?? "")) throw new InvalidOperationException("account_unavailable");
        foreach (var row in rows) if (!Valid(row?["id"]?.ToString() ?? "")) throw new InvalidOperationException("account_unavailable");
        var next = response["next_cursor"]?.ToString();
        if (next != null && (!Valid(next) || rows.Count == 0 || (after != null && string.CompareOrdinal(next, after) <= 0))) throw new InvalidOperationException("account_unavailable");
        return next;
    }
}
