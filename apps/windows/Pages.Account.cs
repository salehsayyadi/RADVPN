using System;
using System.Diagnostics;
using System.Linq;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
namespace RADVPN;
internal sealed partial class MainWindow
{
    JsonObject? AccountState => data["radvpn"] as JsonObject;
    readonly AccountLease accountLease = new();
    int accountTicks;
    bool accountReady, accountSigningIn;
    bool ManagedProfile(JsonNode? p) => AccountSessionData.Managed(p);
    void AccountPage()
    {
        var panel = Panel("account");
        if (!accountReady)
        {
            panel.Children.Add(Label(T("account_login_help")));
            panel.Children.Add(Action("account_get_telegram", () =>
            {
                Process.Start(new ProcessStartInfo("https://t.me/RadVpn7bot?start=register") { UseShellExecute = true });
                return Task.CompletedTask;
            }, "account", true));
            if (AccountState?["token"] != null)
                panel.Children.Add(Action("account_restore", async () => { await ValidateAccount(); page = "home"; Render(); }));
            panel.Children.Add(Label(T("account_code")));
            var code = new PasswordBox { Margin = new Thickness(0, 0, 0, 12) };
            panel.Children.Add(code);
            panel.Children.Add(Action("account_login", async () =>
            {
                if (accountSigningIn)
                    return;
                accountSigningIn = true;
                try
                {
                    var endpoint = AccountApi.ProductionEndpoint;
                    var enteredCode = code.Password.Trim();
                    code.Clear();
                    var result = await AccountApi.Call(endpoint, "v1/auth/login", payload: new JsonObject { ["code"] = enteredCode, ["platform"] = "windows", ["device_name"] = "RADVPN Windows" });
                    data["radvpn"] = new JsonObject { ["endpoint"] = endpoint, ["token"] = result["token"]!.DeepClone(), ["session_id"] = result["session_id"]!.DeepClone(), ["account"] = result["account"]!.DeepClone() };
                    accountLease.MarkVerified();
                    accountReady = true;
                    page = "home";
                    Save();
                    Render();
                }
                finally { accountSigningIn = false; }
            }));
        }
        else
        {
            var a = AccountState!["account"];
            foreach (var (key, value) in new[] {
                ("account_id", a?["telegram_id"]?.ToString()), ("account_status", a?["status"]?.ToString()),
                ("account_subscription", a?["subscription"]?["status"]?.ToString()),
                ("account_total", GiB(a?["subscription"]?["total_bytes"])), ("account_used", GiB(a?["subscription"]?["used_bytes"])),
                ("account_remaining", GiB(a?["subscription"]?["remaining_bytes"])),
                ("account_capacity", a?["device_capacity"]?.ToString()), ("account_active", a?["active_sessions"]?.ToString()),
                ("account_slots", a?["available_slots"]?.ToString()) })
                panel.Children.Add(Label(T(key) + ": " + (value ?? "—")));
            panel.Children.Add(Action("update", async () => { await ValidateAccount(); Render(); }));
            panel.Children.Add(Action("account_import", async () =>
            {
                await ValidateAccount();
                var a = AccountState?["account"];
                var url = a?["subscription"]?["url"]?.ToString();
                if (string.IsNullOrWhiteSpace(url))
                    throw new InvalidOperationException("account_subscription_pending");
                var group = "radvpn-account-" + a!["account_id"];
                var sub = Subs.FirstOrDefault(s => s?["id"]?.ToString() == group);
                if (sub == null)
                {
                    sub = new JsonObject { ["id"] = group, ["name"] = "RADVPN account", ["url"] = url };
                    Subs.Add(sub);
                }
                await Refresh(sub);
                Save();
                Render();
            }));
            panel.Children.Add(Action("account_purchase", () =>
            {
                var url = AccountState?["account"]?["purchase_url"]?.ToString();
                if (Uri.TryCreate(url, UriKind.Absolute, out var uri) && uri.Scheme == "https" && uri.Host == "t.me")
                    Process.Start(new ProcessStartInfo(uri.ToString()) { UseShellExecute = true });
                return Task.CompletedTask;
            }));
            panel.Children.Add(Action("account_devices", ManageAccountDevices, "servers"));
            panel.Children.Add(Action("account_logout", async () =>
            {
                // Keep the token on network failure: do not leak a paid slot by silently abandoning logout.
                try
                {
                    await AccountApi.Call(AccountApi.ProductionEndpoint, "v1/auth/logout", AccountState["token"]!.ToString(), new JsonObject());
                }
                catch (InvalidOperationException e) when (e.Message == "account_revoked") { }
                await ClearAccount();
                Render();
            }));
        }
        body.Children.Add(Card(panel, false, 20));
    }
    async Task CheckForUpdate()
    {
        var candidate = await VerifiedUpdate.Find();
        if (candidate == null)
        {
            MessageBox.Show(this, T("update_no_newer"), "RADVPN");
            return;
        }
        if (MessageBox.Show(this, T(candidate.Testing ? "update_testing_confirm" : "update_download_confirm") + " " + candidate.Version, "RADVPN", MessageBoxButton.YesNo) != MessageBoxResult.Yes)
            return;
        var path = await VerifiedUpdate.Download(candidate);
        if (path.EndsWith(".zip", StringComparison.OrdinalIgnoreCase))
        {
            Process.Start(new ProcessStartInfo("explorer.exe") { ArgumentList = { "/select,", path }, UseShellExecute = false });
            MessageBox.Show(this, T("update_portable_help"));
        }
        else
        {
            await StopConnection();
            Save();
            Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
            exitRequested = true;
            Close();
        }
    }
    static string GiB(JsonNode? value) => value == null ? "—" : (value.GetValue<long>() / 1073741824.0).ToString("F2") + " GiB";
    async Task ClearAccount()
    {
        if (ConnectionActive)
            await StopConnection();
        accountReady = false;
        page = "account";
        AccountSessionData.ClearManaged(data);
        accountLease.Reset();
        RepairSelection();
        Save();
    }
    async Task ValidateAccount()
    {
        var state = AccountState;
        if (state?["token"] == null)
            throw new InvalidOperationException("account_revoked");
        try
        {
            var result = await AccountApi.Call(AccountApi.ProductionEndpoint, "v1/account", state["token"]!.ToString());
            if (!ReferenceEquals(state, AccountState))
                throw new InvalidOperationException("account_revoked");
            state["endpoint"] = AccountApi.ProductionEndpoint;
            state["account"] = result;
            accountLease.MarkVerified();
            accountReady = true;
            Save();
        }
        catch (InvalidOperationException e) when (e.Message == "account_revoked") { if (ReferenceEquals(state, AccountState)) { await ClearAccount(); Render(); } throw; }
    }
    async Task AccountHeartbeat()
    {
        if (AccountState?["token"] == null || ++accountTicks % 30 != 0)
            return;
        try
        {
            await ValidateAccount();
        }
        catch
        {
            if (!accountLease.IsFresh)
            {
                accountReady = false;
                page = "account";
                if (ConnectionActive)
                    await StopConnection();
                Render();
            }
            Notice("account_unavailable");
        }
    }
}
