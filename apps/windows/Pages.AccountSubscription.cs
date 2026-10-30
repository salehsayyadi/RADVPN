using System;
using System.Text.Json.Nodes;
using System.Threading.Tasks;

namespace RADVPN;
internal sealed partial class MainWindow
{
    bool accountSubscriptionBusy;

    async Task SyncAccountSubscription(bool force = false)
    {
        if (!accountReady || accountSubscriptionBusy) return;
        if (ConnectionActive) { if (force) Notice("already_running"); return; }
        var sub = AccountSubscriptions.Prepare(data, force);
        if (sub == null) { if (force) Notice("account_subscription_pending"); return; } // Heartbeat retries pending provisioning.
        var owner = data;
        var state = AccountState;
        var token = state?["token"]?.ToString();
        accountSubscriptionBusy = true;
        bool Current() => accountReady && !ConnectionActive && ReferenceEquals(owner, data)
            && ReferenceEquals(state, AccountState) && token == AccountState?["token"]?.ToString()
            && AccountSubscriptions.Url(data) == sub["url"]!.ToString();
        try
        {
            using var worker = new Core();
            var result = await worker.Call(new { method = "subscription", text = sub["url"]!.ToString() });
            if (!Current()) return; // Logout, restore, revocation or account switch must win.
            AccountSubscriptions.Apply(data, sub, result.AsObject());
            RepairSelection(); Save(); Render();
        }
        catch
        {
            // Do not discard a valid login or paid device slot because fetching profiles failed.
            if (force && Current()) Notice("account_subscription_pending");
        }
        finally { accountSubscriptionBusy = false; }
    }
}
