using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;

namespace RADVPN;
internal sealed partial class MainWindow
{
    void Subscriptions()
    {
        var columns = new Grid();
        columns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        columns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(22) });
        columns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1.2, GridUnitType.Star) });
        var form = Panel("add");
        var name = Field(form, "subscription_name");
        var url = Field(form, "sub_url");
        var interval = new TextBox { Text = "0" };
        form.Children.Add(Actions(Action("paste", () => { url.Text = System.Windows.Clipboard.GetText(); return Task.CompletedTask; }, "copy"), Action("add", async () => { if (!int.TryParse(interval.Text, out var hours) || hours < 0 || hours > 720) throw new InvalidOperationException("invalid_settings"); var sub = new JsonObject { ["id"] = Guid.NewGuid().ToString("N"), ["url"] = url.Text.Trim(), ["name"] = string.IsNullOrWhiteSpace(name.Text) ? T("subscriptions") : name.Text[..Math.Min(200, name.Text.Length)], ["hours"] = hours, ["paused"] = false }; await Refresh(sub); Subs.Add(sub); Save(); Render(); }, "plus", true)));
        form.Children.Add(Label(T("privacy"), 12, "Muted"));
        columns.Children.Add(Card(form));
        var existing = Panel("subscriptions");
        if (Subs.Count == 0)
            existing.Children.Add(Empty("no_subs", () => { url.Focus(); return Task.CompletedTask; }));
        foreach (var sub in Subs.ToArray())
        {
            if (sub == null)
                continue;
            var card = new StackPanel();
            card.Children.Add(Label(sub["name"]?.ToString() ?? T("subscriptions"), 17, "Text", true));
            var host = Uri.TryCreate(sub["url"]?.ToString(), UriKind.Absolute, out var uri) ? uri.Host : "—";
            card.Children.Add(Label(host, 13, "Muted"));
            card.Children.Add(Label(T("last_refresh") + ": " + (sub["last"]?.ToString() ?? "—"), 12, "Subtext"));
            var count = Profiles.Count(p => p?["group"]?.ToString() == sub["id"]?.ToString());
            card.Children.Add(Label($"{count} · {T(sub["paused"]?.GetValue<bool>() == true ? "paused" : "active_sub")} · {sub["hours"] ?? 0} h", 12, "Subtext"));
            if (sub["status"]?.ToString() == "refresh_failed")
                card.Children.Add(Label(T("refresh_failed"), 13, "Error"));
            card.Children.Add(Actions(Action("update", async () => { try { await Refresh(sub); } catch { sub["status"] = "refresh_failed"; Save(); throw; } Render(); }, "refresh"), Action("rename", () => { var value = Prompt("subscription_name", sub["name"]?.ToString() ?? ""); if (!string.IsNullOrWhiteSpace(value)) { sub["name"] = value[..Math.Min(200, value.Length)]; Save(); Render(); } return Task.CompletedTask; }), Action("delete", async () => { if (!Confirm("remove_sub")) return; var removed = Profiles.Where(p => p?["group"]?.ToString() == sub["id"]?.ToString()).ToArray(); if (ConnectionActive && removed.Contains(Selected())) await StopConnection(); foreach (var p in removed) Profiles.Remove(p); Subs.Remove(sub); RepairSelection(); Save(); Render(); }, "delete")));
            existing.Children.Add(Card(card, false, 16));
        }
        var right = Card(existing);
        Grid.SetColumn(right, 2);
        columns.Children.Add(right);
        body.Children.Add(columns);
    }
    async Task Refresh(JsonNode sub)
    {
        using var worker = new Core();
        var result = await worker.Call(new
        {
            method = "subscription",
            text = sub["url"]!.ToString()
        });
        var items = (JsonArray)result["profiles"]!;
        var ids = items.Select(p => sub["id"]!.ToString() + ":" + p!["id"]!.ToString()).ToHashSet();
        var group = sub["id"]!.ToString();
        var removed = Profiles.Where(p => p?["group"]?.ToString() == group && !ids.Contains(p["id"]!.ToString())).ToArray();
        if (ConnectionActive && removed.Contains(Selected()))
            await StopConnection();
        foreach (var p in removed)
            Profiles.Remove(p);
        MergeItems(items, group);
        sub["last"] = DateTime.UtcNow.ToString("O");
        sub["status"] = "success";
        RepairSelection();
        Save();
    }
    void Traffic()
    {
        var p = Panel("traffic");
        p.Children.Add(Label(T("history_help"), 13, "Subtext"));
        p.Children.Add(Actions(new[] { "today", "week", "month", "total" }.Select(key => Action(key, () => { trafficRange = key; Render(); return Task.CompletedTask; }, null, key == trafficRange)).ToArray()));
        var days = trafficRange switch
        {
            "today" => 1,
            "week" => 7,
            "month" => 30,
            _ => 0
        };
        var totals = TrafficLedger.Totals(data, days);
        var cards = new Grid();
        cards.ColumnDefinitions.Add(new ColumnDefinition());
        cards.ColumnDefinitions.Add(new ColumnDefinition());
        var down = Panel("down");
        down.Children.Add(Label(TrafficLedger.Bytes(totals.Down), 30, "Download", true));
        cards.Children.Add(Card(down));
        var up = Panel("up");
        up.Children.Add(Label(TrafficLedger.Bytes(totals.Up), 30, "Orange", true));
        var upload = Card(up);
        upload.Margin = new Thickness(16, 0, 0, 16);
        Grid.SetColumn(upload, 1);
        cards.Children.Add(upload);
        p.Children.Add(cards);
        var live = Panel("live_traffic");
        graph = NewGraph(180);
        live.Children.Add(graph);
        if (samples.Count == 0)
            live.Children.Add(Label(T("awaiting_traffic"), 12, "Muted"));
        p.Children.Add(Card(live));
        if (totals.Up + totals.Down == 0)
            p.Children.Add(Label(T("no_history"), 14, "Muted"));
        else if (data["trafficDays"] is JsonObject recorded)
        {
            foreach (var entry in recorded.OrderByDescending(x => x.Key).Where(x => DateTime.TryParse(x.Key, out var date) && (days == 0 || date.Date >= DateTime.UtcNow.Date.AddDays(-(days - 1)))).Take(31))
            {
                p.Children.Add(Label($"{entry.Key} UTC    {T("down")}: {TrafficLedger.Bytes(entry.Value?["down"]?.GetValue<long>() ?? 0)}    {T("up")}: {TrafficLedger.Bytes(entry.Value?["up"]?.GetValue<long>() ?? 0)}", 13, "Subtext"));
            }
        }
        body.Children.Add(Card(p));
    }
    void SettingsPage()
    {
        draft ??= new JsonObject { ["settings"] = Settings.DeepClone(), ["tray"] = data["tray"]?.DeepClone() ?? JsonValue.Create(true), ["startWindows"] = StartupRegistration.IsRegistered(), ["autoConnect"] = data["autoConnect"]?.DeepClone() ?? JsonValue.Create(false) };
        var p = Panel("settings");
        p.Children.Add(Actions(new[] { "general", "connection", "security", "advanced" }.Select(tab => Action(tab, () => { settingsTab = tab; Render(); return Task.CompletedTask; }, null, settingsTab == tab)).ToArray()));
        p.Children.Add(Label(T("save_cancel"), 12, "Muted"));
        var settings = (JsonObject)draft["settings"]!;
        if (settingsTab == "general")
        {
            p.Children.Add(Label(T("language"), 14, "Subtext"));
            p.Children.Add(Label(Lang == "fa" ? "فارسی · RTL" : "English · LTR", 17, "Orange", true));
            p.Children.Add(Label(T("dark"), 14, "Subtext"));
            AddCheck(p, "tray", draft["tray"]?.GetValue<bool>() ?? true, v => draft["tray"] = v);
            AddCheck(p, "start_windows", draft["startWindows"]?.GetValue<bool>() ?? false, v => draft["startWindows"] = v);
            AddCheck(p, "autoconnect_proxy", draft["autoConnect"]?.GetValue<bool>() ?? false, v => draft["autoConnect"] = v);
            p.Children.Add(Label(T("autoconnect_help"), 12, "Muted"));
            p.Children.Add(Action("check_updates", () => { OpenUrl("https://github.com/salehsayyadi/RADVPN/releases"); return Task.CompletedTask; }, "refresh"));
        }
        if (settingsTab == "connection")
        {
            p.Children.Add(Actions(Action("tunnel", () => { settings["mode"] = "tunnel"; Render(); return Task.CompletedTask; }, "shield", settings["mode"]?.ToString() == "tunnel"), Action("proxy", () => { settings["mode"] = "proxy"; Render(); return Task.CompletedTask; }, "globe", settings["mode"]?.ToString() == "proxy")));
            BindText(p, "port", settings, "port", true);
            BindText(p, "bind", settings, "bind");
            BindText(p, "mtu", settings, "mtu", true);
            p.Children.Add(Label(T("proxy_help"), 13, "Muted"));
        }
        if (settingsTab == "security")
        {
            var dns = Field(p, "dns", string.Join(",", ((JsonArray)settings["dns"]!).Select(x => x!.ToString())));
            dns.TextChanged += (_, _) => settings["dns"] = new JsonArray(dns.Text.Split(',').Select(x => (JsonNode?)JsonValue.Create(x.Trim())).ToArray());
            p.Children.Add(Label(T("ipv6"), 14, "Subtext"));
            p.Children.Add(Actions(Action("ipv6_tunnel", () => { settings["ipv6"] = "tunnel"; Render(); return Task.CompletedTask; }, "globe", settings["ipv6"]?.ToString() != "block"), Action("ipv6_block", () => { settings["ipv6"] = "block"; Render(); return Task.CompletedTask; }, "shield", settings["ipv6"]?.ToString() == "block")));
            p.Children.Add(Label(T("ipv6_help"), 12, "Muted"));
            p.Children.Add(Label(T("logging_fixed"), 13, "Subtext"));
            p.Children.Add(Label(T("not_full_protection"), 13, "Warning"));
        }
        if (settingsTab == "advanced")
        {
            p.Children.Add(Label(T("rules_help"), 13, "Muted"));
            var rules = Field(p, "rules", draft["rulesText"]?.ToString() ?? settings["rules"]?.ToJsonString() ?? "[]", true);
            rules.TextChanged += (_, _) => draft["rulesText"] = rules.Text;
            p.Children.Add(Action("advanced", () => { Navigate("advanced"); return Task.CompletedTask; }, "settings"));
            p.Children.Add(Label(T("backup_help"), 12, "Muted"));
            p.Children.Add(Actions(Action("export", ExportBackup, "copy"), Action("restore", RestoreBackup, "refresh")));
        }
        p.Children.Add(Actions(Action("save", async () => { if (ConnectionActive) throw new InvalidOperationException("already_running"); if (draft["rulesText"] != null) settings["rules"] = JsonNode.Parse(draft["rulesText"]!.ToString()); await core!.Call(new { method = "settings", settings }); if (draft["autoConnect"]?.GetValue<bool>() == true && settings["mode"]?.ToString() != "proxy") throw new InvalidOperationException("autoconnect_help"); var startup = draft["startWindows"]?.GetValue<bool>() ?? false; StartupRegistration.Set(startup); data["settings"] = settings.DeepClone(); data["tray"] = draft["tray"]!.DeepClone(); data["startWindows"] = startup; data["autoConnect"] = draft["autoConnect"]!.DeepClone(); Save(); draft = null; Render(); Notice("applied"); }, "check", true), Action("cancel", () => { draft = null; Render(); return Task.CompletedTask; }), Action("reset", async () => { if (ConnectionActive) throw new InvalidOperationException("already_running"); if (!Confirm("confirm_reset")) return; draft!["settings"] = await core!.Call(new { method = "defaults" }); draft.Remove("rulesText"); Render(); }, "refresh")));
        body.Children.Add(Card(p));
    }
    void BindText(StackPanel p, string key, JsonObject target, string property, bool number = false)
    {
        var field = Field(p, key, target[property]?.ToString() ?? "");
        field.TextChanged += (_, _) => { if (number) { if (int.TryParse(field.Text, out var n)) target[property] = n; else target[property] = null; } else target[property] = field.Text.Trim(); };
    }
    void AddCheck(StackPanel p, string key, bool value, Action<bool> update)
    {
        var checkbox = new CheckBox { Content = T(key), IsChecked = value };
        checkbox.Checked += (_, _) => update(true);
        checkbox.Unchecked += (_, _) => update(false);
        p.Children.Add(checkbox);
    }
    Task ExportBackup()
    {
        var file = new SaveFileDialog { Filter = "RADVPN (*.radvpn)|*.radvpn", FileName = "RADVPN-2.0.2.radvpn" };
        if (file.ShowDialog() == true)
            File.WriteAllBytes(file.FileName, Storage.Encrypt(data));
        return Task.CompletedTask;
    }
    async Task RestoreBackup()
    {
        var file = new OpenFileDialog { Filter = "RADVPN (*.radvpn)|*.radvpn" };
        if (file.ShowDialog() != true)
            return;
        if (new FileInfo(file.FileName).Length > 8 * 1024 * 1024)
            throw new InvalidOperationException("import_too_large");
        var restored = Storage.Decrypt(File.ReadAllBytes(file.FileName));
        if (restored["settings"] == null)
            throw new InvalidOperationException("invalid_settings");
        await core!.Call(new
        {
            method = "settings",
            settings = restored["settings"]
        });
        await StopConnection();
        data = restored;
        RepairSelection();
        LoadWords();
        UpdateTray();
        Save();
        draft = null;
        Render();
    }
    void Advanced()
    {
        var p = Panel("advanced");
        p.Children.Add(Label("Xray " + (data["coreVersion"]?.ToString() ?? "—"), 22, "Orange", true));
        p.Children.Add(Label(T("advanced_help"), 14, "Subtext"));
        p.Children.Add(Label(T("last_working_help"), 13, "Muted"));
        p.Children.Add(Actions(Action("last_working", async () => { var snapshot = data["lastWorking"] ?? throw new InvalidOperationException("no_backup"); await core!.Call(new { method = "settings", settings = snapshot["settings"] }); await StopConnection(); var profile = snapshot["profile"]?.DeepClone() ?? throw new InvalidOperationException("no_backup"); var current = Profiles.FirstOrDefault(x => x?["id"]?.ToString() == profile["id"]?.ToString()); if (current != null) Profiles.Remove(current); Profiles.Add(profile); data["settings"] = snapshot["settings"]!.DeepClone(); data["selected"] = profile["id"]!.ToString(); Save(); Navigate("home"); }, "refresh"), Action("restart_core", async () => { await StopConnection(); await Toggle(); }, "power"), Action("disconnect", StopConnection, "stop")));
        var profile = Selected();
        if (profile != null)
        {
            p.Children.Add(Label(profile["name"]?.ToString() ?? "", 18, "Text", true));
            var raw = Field(p, "advanced", profile["outbound"]!.ToJsonString(new JsonSerializerOptions { WriteIndented = true }), true);
            raw.MinHeight = 220;
            p.Children.Add(Action("save", async () => { if (ConnectionActive) throw new InvalidOperationException("already_running"); var candidate = profile.DeepClone(); candidate["outbound"] = JsonNode.Parse(raw.Text); await core!.Call(new { method = "validate", profile = candidate, settings = Settings }); profile["outbound"] = candidate["outbound"]!.DeepClone(); Save(); Notice("applied"); }, "check", true));
        }
        else
            p.Children.Add(Empty("empty", () => { Navigate("configs"); return Task.CompletedTask; }));
        body.Children.Add(Card(p));
    }
    JsonObject Diagnostics() => new() { ["appVersion"] = "2.0.2", ["coreVersion"] = data["coreVersion"]?.ToString(), ["channel"] = "experimental-unsigned", ["state"] = active, ["mode"] = Settings["mode"]?.ToString(), ["socksPort"] = Settings["port"]?.DeepClone(), ["httpPort"] = (Settings["port"]?.GetValue<int>() ?? 10808) + 1, ["driverFilePresent"] = File.Exists(Path.Combine(AppContext.BaseDirectory, "wintun.dll")), ["administrator"] = IsAdministrator(), ["deviceTunnelVerified"] = false, ["events"] = new JsonArray(events.Select(e => (JsonNode?)new JsonObject { ["time"] = e.Time.ToString("O"), ["code"] = e.Code }).ToArray()) };
    void Logs()
    {
        var p = Panel("logs");
        p.Children.Add(Label(T("safe_name"), 13, "Muted"));
        p.Children.Add(Actions(Action("copy_diagnostics", () => { System.Windows.Clipboard.SetText(Diagnostics().ToJsonString(new JsonSerializerOptions { WriteIndented = true })); Notice("copied"); return Task.CompletedTask; }, "copy"), Action("clear_logs", () => { if (Confirm("confirm_clear")) { events.Clear(); Render(); } return Task.CompletedTask; }, "delete")));
        p.Children.Add(Label(T("logging_fixed"), 13, "Subtext"));
        var box = new StackPanel { Margin = new Thickness(0, 12, 0, 0) };
        foreach (var item in events.Take(200))
        {
            var row = new Grid { Margin = new Thickness(0, 0, 0, 12) };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(96) });
            row.ColumnDefinitions.Add(new ColumnDefinition());
            row.Children.Add(Label(item.Time.ToString("HH:mm:ss"), 13, "Muted"));
            var message = Label(T(item.Code), 14, item.Code.Contains("failed") || item.Code == "error" ? "Error" : "Subtext");
            Grid.SetColumn(message, 1);
            row.Children.Add(message);
            box.Children.Add(row);
        }
        p.Children.Add(box);
        body.Children.Add(Card(p));
    }
    Task CheckPorts()
    {
        if (ConnectionActive)
            throw new InvalidOperationException("already_running");
        TcpListener? first = null, second = null;
        try
        {
            var address = IPAddress.Parse(Settings["bind"]!.ToString());
            var port = Settings["port"]!.GetValue<int>();
            first = new TcpListener(address, port);
            second = new TcpListener(address, port + 1);
            first.Start();
            second.Start();
            Notice("ports_free");
        }
        catch { Notice("ports_busy"); }
        finally { first?.Stop(); second?.Stop(); }
        return Task.CompletedTask;
    }
    static void OpenUrl(string url)
    {
        if (!url.StartsWith("https://github.com/salehsayyadi/RADVPN", StringComparison.Ordinal))
            throw new InvalidOperationException("invalid_url");
        Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
    }
    void About()
    {
        var p = Panel("about");
        p.Children.Add(Glyphs.Icon("shield", B("Orange"), 66));
        p.Children.Add(Label("RADVPN 2.0.2", 30, "Text", true));
        p.Children.Add(Label(T("testing_channel"), 14, "Warning"));
        p.Children.Add(Label("Xray " + (data["coreVersion"]?.ToString() ?? "—"), 18, "Orange", true));
        p.Children.Add(Label(T("privacy"), 14, "Subtext"));
        p.Children.Add(Label(T("not_full_protection"), 14, "Warning"));
        p.Children.Add(Label(T("not_installed_driver"), 13, "Muted"));
        p.Children.Add(Actions(Action("check_updates", () => { OpenUrl("https://github.com/salehsayyadi/RADVPN/releases"); return Task.CompletedTask; }, "globe"), Action("license_notices", () => { Process.Start(new ProcessStartInfo(Path.Combine(AppContext.BaseDirectory, "THIRD_PARTY_NOTICES.md")) { UseShellExecute = true }); return Task.CompletedTask; }, "configs"), Action("open_data", () => { Directory.CreateDirectory(Path.GetDirectoryName(Storage.FilePath)!); Process.Start(new ProcessStartInfo("explorer.exe") { ArgumentList = { Path.GetDirectoryName(Storage.FilePath)! }, UseShellExecute = false }); return Task.CompletedTask; }, "profiles")));
        body.Children.Add(Card(p));
    }
}
internal static class StartupRegistration
{
    const string Key = @"Software\Microsoft\Windows\CurrentVersion\Run";
    public static bool IsRegistered()
    {
        using var key = Registry.CurrentUser.OpenSubKey(Key);
        return key?.GetValue("RADVPN")?.ToString() == Command;
    }
    static string Command => "\"" + Environment.ProcessPath + "\" --minimized";
    public static void Set(bool enabled)
    {
        using var key = Registry.CurrentUser.CreateSubKey(Key);
        if (enabled)
            key.SetValue("RADVPN", Command);
        else
            key.DeleteValue("RADVPN", false);
    }
}
