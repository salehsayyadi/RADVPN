using System;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
namespace RADVPN;
internal sealed partial class MainWindow
{
    async Task<JsonObject> DeviceRequest(JsonObject state, string path, JsonObject? payload = null)
    {
        try
        {
            if (!ReferenceEquals(state, AccountState))
                throw new InvalidOperationException("account_revoked");
            var result = await AccountApi.Call(AccountApi.ProductionEndpoint, path, state["token"]!.ToString(), payload);
            if (!ReferenceEquals(state, AccountState))
                throw new InvalidOperationException("account_revoked");
            return result;
        }
        catch (InvalidOperationException e) when (e.Message == "account_revoked")
        {
            if (ReferenceEquals(state, AccountState)) { await ClearAccount(); Render(); }
            throw;
        }
    }
    async Task ManageAccountDevices()
    {
        await ValidateAccount();
        var state = AccountState!;
        var response = await DeviceRequest(state, AccountDevicePage.Path());
        var dialog = Dialog(T("account_devices"));
        var panel = (StackPanel)dialog.Content;
        var list = new StackPanel();
        panel.Children.Add(new ScrollViewer { Content = list, MaxHeight = 360, VerticalScrollBarVisibility = ScrollBarVisibility.Auto });
        bool revoking = false, loading = false, closed = false;
        string? cursor = null;
        dialog.Closed += (_, _) => closed = true;
        Button more = null!;
        more = Action("account_more_devices", async () =>
        {
            if (loading || revoking || cursor == null) return;
            loading = true;
            try
            {
                var after = cursor;
                var result = await DeviceRequest(state, AccountDevicePage.Path(after));
                if (!closed) ShowPage(result, after);
            }
            finally { loading = false; }
        });
        panel.Children.Add(more);
        void ShowPage(JsonObject page, string? after = null)
        {
        cursor = AccountDevicePage.Validate(page, after);
        more.Visibility = cursor == null ? Visibility.Collapsed : Visibility.Visible;
        var rows = page["sessions"]!.AsArray();
        var current = page["current_session_id"]?.ToString();
        list.Children.Clear();
        if (rows.Count == 0)
            list.Children.Add(Label(T("account_no_devices")));
        foreach (var row in rows)
        {
            var sid = row?["id"]?.ToString();
            if (!Guid.TryParse(sid, out _))
                throw new InvalidOperationException("account_unavailable");
            var item = new StackPanel();
            item.Children.Add(Label((row?["device_name"]?.ToString() ?? "") + " · " + row?["platform"]?.ToString() + (sid == current ? " · " + T("account_this_device") : "")));
            if (row?["last_seen"] != null)
                item.Children.Add(Label(DateTimeOffset.FromUnixTimeSeconds(row["last_seen"]!.GetValue<long>()).ToLocalTime().ToString("g"), 12, "Subtext"));
            item.Children.Add(Action("account_revoke_device", async () =>
            {
                if (revoking) return;
                var confirm = Dialog(T("account_revoke_confirm"));
                ((StackPanel)confirm.Content).Children.Add(Actions(
                    Action("account_revoke_device", () => { confirm.DialogResult = true; return Task.CompletedTask; }, "stop", true),
                    Action("cancel", () => { confirm.DialogResult = false; return Task.CompletedTask; })));
                if (confirm.ShowDialog() != true) return;
                revoking = true;
                try
                {
                    var ack = await DeviceRequest(state, "v1/account/sessions/" + sid + "/revoke", new JsonObject());
                    dialog.Close();
                    if (ack["current_session"]?.GetValue<bool>() == true)
                        await ClearAccount();
                    else
                        await ValidateAccount();
                    Render();
                }
                finally { revoking = false; }
            }, "stop"));
            list.Children.Add(Card(item));
        }
        }
        ShowPage(response);
        panel.Children.Add(Action("close", () => { dialog.Close(); return Task.CompletedTask; }));
        dialog.ShowDialog();
    }
}
