using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json.Nodes;

namespace RADVPN;
internal static class AccountSubscriptionFixtures
{
    internal static IEnumerable<string> Verify()
    {
        static JsonObject State() => JsonNode.Parse("""
            {"selected":"","profiles":[],"subscriptions":[],"radvpn":{"token":"LOCAL-FIXTURE-NOT-A-TOKEN","account":{"account_id":"fixture","subscription":{"url":"https://subscription.invalid/fixture"}}}}
            """)!.AsObject();
        static JsonObject Result() => JsonNode.Parse("""
            {"profiles":[{"id":"one","name":"Fixture","enabled":true,"outbound":{"protocol":"socks"}}]}
            """)!.AsObject();
        static void Check(bool passed) { if (!passed) throw new Exception("managed subscription fixture failed"); }
        var data = State();
        var plan = AccountSubscriptions.Prepare(data)!;
        Check(data["subscriptions"]!.AsArray().Count == 0);
        AccountSubscriptions.Apply(data, plan, Result());
        Check(data["profiles"]!.AsArray().Count == 1 && data["selected"]!.ToString() == "radvpn-account-fixture:one");
        Check(AccountSubscriptions.Prepare(data) == null);
        for (int i = 0; i < 3; i++) AccountSubscriptions.Apply(data, AccountSubscriptions.Prepare(data, true)!, Result());
        Check(data["subscriptions"]!.AsArray().Count == 1 && data["profiles"]!.AsArray().Count == 1);
        yield return "automatic first import selects profile; restore/retry idempotency";
        var pending = State(); pending["radvpn"]!["account"]!["subscription"]!.AsObject().Remove("url");
        Check(AccountSubscriptions.Prepare(pending) == null);
        pending["radvpn"]!["account"]!["subscription"]!["url"] = "https://subscription.invalid/ready";
        Check(AccountSubscriptions.Prepare(pending) != null);
        var before = data.ToJsonString();
        bool rejected = false;
        try { AccountSubscriptions.Apply(data, plan, new JsonObject { ["profiles"] = new JsonArray() }); }
        catch (InvalidOperationException) { rejected = true; }
        Check(rejected && data.ToJsonString() == before);
        var malformed = Result(); malformed["profiles"]![0]!.AsObject().Remove("outbound");
        rejected = false;
        try { AccountSubscriptions.Apply(data, plan, malformed); } catch (InvalidOperationException) { rejected = true; }
        Check(rejected && data.ToJsonString() == before);
        yield return "pending provisioning retries; empty/malformed parse atomicity";
        data["radvpn"]!["account"]!["subscription"]!["url"] = "https://subscription.invalid/rotated";
        Check(AccountSubscriptions.Prepare(data)!["url"]!.ToString().EndsWith("rotated"));
        var adoption = State();
        adoption["subscriptions"]!.AsArray().Add(new JsonObject { ["id"] = "manual-sub", ["url"] = plan["url"]!.ToString() });
        adoption["profiles"]!.AsArray().Add(new JsonObject { ["id"] = "manual-sub:one", ["group"] = "manual-sub", ["name"] = "My name", ["userNamed"] = true, ["outbound"] = new JsonObject() });
        adoption["profiles"]!.AsArray().Add(new JsonObject { ["id"] = "unrelated", ["outbound"] = new JsonObject() });
        adoption["selected"] = "manual-sub:one";
        AccountSubscriptions.Apply(adoption, AccountSubscriptions.Prepare(adoption)!, Result());
        Check(adoption["subscriptions"]!.AsArray().Count == 1 && adoption["profiles"]!.AsArray().Count == 2);
        Check(adoption["profiles"]![0]!["name"]!.ToString() == "My name" && adoption["selected"]!.ToString() == "radvpn-account-fixture:one");
        adoption["subscriptions"]!.AsArray().Add(new JsonObject { ["id"] = "second-copy", ["url"] = plan["url"]!.ToString() });
        adoption["profiles"]!.AsArray().Add(new JsonObject { ["id"] = "second-copy:one", ["group"] = "second-copy", ["outbound"] = new JsonObject() });
        AccountSubscriptions.Apply(adoption, AccountSubscriptions.Prepare(adoption, true)!, Result());
        Check(adoption["subscriptions"]!.AsArray().Count == 1 && adoption["profiles"]!.AsArray().Count == 2);
        yield return "rotated URL; exact-URL adoption preserves names/selection and unrelated profiles";
    }
}
