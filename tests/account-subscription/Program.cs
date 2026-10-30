using System;
using System.Threading.Tasks;
using System.Text.Json.Nodes;
namespace RADVPN;
internal static class DisplayNames { internal static string Repair(string value) => value; }
internal sealed class Core : IDisposable {
 internal static TaskCompletionSource<JsonObject>? Pending;
 internal static int Requests;
 internal Task<JsonObject> Call(object request) { Requests++; return Pending!.Task; }
 public void Dispose() { }
}
internal sealed partial class MainWindow {
 JsonObject data = JsonNode.Parse("""{"selected":"","profiles":[],"subscriptions":[],"radvpn":{"token":"fixture","account":{"account_id":"fixture","subscription":{"url":"https://subscription.invalid/fixture"}}}}""")!.AsObject();
 JsonObject? AccountState => data["radvpn"] as JsonObject;
 bool accountReady = true, ConnectionActive;
 int saves, notices;
 void RepairSelection() { }
 void Save() { saves++; }
 void Render() { }
 void Notice(string key) { notices++; }
 internal static async Task CheckOrchestration() {
  static JsonObject Result() => JsonNode.Parse("""{"profiles":[{"id":"one","name":"Fixture","enabled":true,"outbound":{}}]}""")!.AsObject();
  static void Check(bool ok) { if(!ok) throw new Exception("orchestration failed"); }
  var window = new MainWindow(); Core.Pending = new();
  var first = window.SyncAccountSubscription(); await window.SyncAccountSubscription(); Check(Core.Requests == 1);
  window.accountReady=false; window.data["radvpn"]=new JsonObject(); Core.Pending.SetResult(Result()); await first;
  Check(window.saves == 0 && window.data["profiles"]!.AsArray().Count == 0);
  var retry = new MainWindow(); Core.Pending=new(); var offline=retry.SyncAccountSubscription(); Core.Pending.SetException(new Exception("offline")); await offline;
  Check(retry.accountReady && retry.saves == 0 && retry.notices == 0);
  Core.Pending=new(); var success=retry.SyncAccountSubscription(); Core.Pending.SetResult(Result()); await success;
  Check(retry.saves == 1 && retry.data["profiles"]!.AsArray().Count == 1);
  var prior=Core.Requests; await retry.SyncAccountSubscription(); Check(prior == Core.Requests);
  var busy = new MainWindow { ConnectionActive=true }; await busy.SyncAccountSubscription(); Check(Core.Requests==prior);
  Console.WriteLine("PASS: single-flight; no stale logout mutation; network retry; loaded skip; active VPN preserved");
 }
}
internal static class Program { static async Task Main() { foreach(var check in AccountSubscriptionFixtures.Verify()) Console.WriteLine("PASS: "+check); await MainWindow.CheckOrchestration(); } }
