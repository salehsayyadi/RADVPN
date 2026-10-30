using System;
using System.Linq;
using System.Text.Json.Nodes;

namespace RADVPN;

/// <summary>Pure, staged managed-subscription import; never creates an account/session.</summary>
internal static class AccountSubscriptions
{
    internal static string? Url(JsonObject data) => data["radvpn"]?["account"]?["subscription"]?["url"]?.ToString();

    internal static JsonObject? Prepare(JsonObject data, bool force = false)
    {
        var account = data["radvpn"]?["account"];
        var url = Url(data);
        var accountId = account?["account_id"]?.ToString();
        if (string.IsNullOrWhiteSpace(url) || string.IsNullOrWhiteSpace(accountId)) return null;
        var id = "radvpn-account-" + accountId;
        var old = data["subscriptions"]!.AsArray().FirstOrDefault(s => s?["id"]?.ToString() == id);
        var loaded = data["profiles"]!.AsArray().Any(p => p?["group"]?.ToString() == id && p["enabled"]?.GetValue<bool>() != false && p["outbound"] != null);
        if (!force && old?["url"]?.ToString() == url && (old["rejected"]?.GetValue<int>() ?? 0) == 0 && loaded) return null;
        var sub = old?.DeepClone().AsObject() ?? new JsonObject { ["id"] = id, ["name"] = "RADVPN account" };
        sub["url"] = url;
        return sub;
    }

    internal static void Apply(JsonObject data, JsonObject sub, JsonObject result)
    {
        if (result["profiles"] is not JsonArray incoming || incoming.Count == 0)
            throw new InvalidOperationException("account_subscription_pending");
        var staged = data.DeepClone().AsObject();
        var subscriptions = staged["subscriptions"]!.AsArray();
        var profiles = staged["profiles"]!.AsArray();
        var group = sub["id"]!.ToString();
        var aliases = subscriptions.Where(s => s?["url"]?.ToString() == sub["url"]!.ToString() && s["id"]?.ToString() != group)
            .Select(s => s!["id"]!.ToString()).ToHashSet();
        foreach (var p in profiles.Where(p => p != null))
        {
            var oldGroup = p!["group"]?.ToString() ?? "";
            if (!aliases.Contains(oldGroup)) continue;
            var oldId = p["id"]!.ToString();
            var next = group + ":" + (oldId.StartsWith(oldGroup + ":", StringComparison.Ordinal) ? oldId[(oldGroup.Length + 1)..] : oldId);
            p["id"] = next; p["group"] = group;
            if (staged["selected"]?.ToString() == oldId) staged["selected"] = next;
        }
        var seen = new System.Collections.Generic.HashSet<string>();
        foreach (var p in profiles.Where(p => p?["group"]?.ToString() == group).ToArray())
            if (!seen.Add(p!["id"]!.ToString())) profiles.Remove(p);
        var ids = incoming.Select(p => group + ":" + p!["id"]!.ToString()).ToHashSet();
        if ((result["rejected"]?.GetValue<int>() ?? 0) == 0)
            foreach (var p in profiles.Where(p => p?["group"]?.ToString() == group && !ids.Contains(p["id"]!.ToString())).ToArray()) profiles.Remove(p);
        foreach (var item in incoming)
        {
            var copy = item?.DeepClone() ?? throw new InvalidOperationException("invalid_profile");
            if (string.IsNullOrWhiteSpace(copy["id"]?.ToString()) || copy["outbound"] is not JsonObject)
                throw new InvalidOperationException("invalid_profile");
            copy["id"] = group + ":" + copy["id"]!.ToString(); copy["group"] = group;
            copy["name"] = DisplayNames.Repair(copy["name"]?.ToString() ?? "");
            var old = profiles.FirstOrDefault(p => p?["id"]?.ToString() == copy["id"]!.ToString());
            if (old == null) profiles.Add(copy);
            else
            {
                old["outbound"] = copy["outbound"]!.DeepClone();
                if (copy["sourceLink"] != null) old["sourceLink"] = copy["sourceLink"]!.DeepClone();
                if (old["userNamed"]?.GetValue<bool>() != true) old["name"] = copy["name"]!.DeepClone();
                old.AsObject().Remove("probeLatency"); old.AsObject().Remove("reachable");
            }
        }
        foreach (var old in subscriptions.Where(s => s?["id"]?.ToString() == group || aliases.Contains(s?["id"]?.ToString() ?? "")).ToArray()) subscriptions.Remove(old);
        var nextSub = sub.DeepClone();
        nextSub["last"] = DateTime.UtcNow.ToString("O"); nextSub["status"] = "success";
        nextSub["info"] = result["info"]?.DeepClone() ?? new JsonArray(); nextSub["rejected"] = result["rejected"]?.DeepClone() ?? JsonValue.Create(0);
        subscriptions.Add(nextSub);
        if (!profiles.Any(p => p?["id"]?.ToString() == staged["selected"]?.ToString() && p?["enabled"]?.GetValue<bool>() != false))
            staged["selected"] = profiles.FirstOrDefault(p => p?["group"]?.ToString() == group && p["enabled"]?.GetValue<bool>() != false)?["id"]?.ToString() ?? "";
        data["profiles"] = profiles.DeepClone(); data["subscriptions"] = subscriptions.DeepClone(); data["selected"] = staged["selected"]?.DeepClone();
    }
}
