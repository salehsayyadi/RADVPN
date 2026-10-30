using System;
using System.Linq;
using System.Text.Json.Nodes;
namespace RADVPN;
internal static class AccountSessionData
{
    internal static bool Managed(JsonNode? profile) => profile?["group"]?.ToString().StartsWith("radvpn-account-", StringComparison.Ordinal) == true;
    internal static void ClearManaged(JsonObject data)
    {
        var profiles = data["profiles"]!.AsArray();
        var subscriptions = data["subscriptions"]!.AsArray();
        foreach (var profile in profiles.Where(Managed).ToArray())
            profiles.Remove(profile);
        foreach (var sub in subscriptions.Where(s => s?["id"]?.ToString().StartsWith("radvpn-account-", StringComparison.Ordinal) == true).ToArray())
            subscriptions.Remove(sub);
        data["radvpn"] = new JsonObject { ["endpoint"] = AccountApi.ProductionEndpoint };
    }
}
