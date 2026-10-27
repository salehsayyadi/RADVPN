using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Effects;
using Microsoft.Win32;

namespace RADVPN;
internal sealed partial class MainWindow
{
    void Home()
    {
        var columns = new Grid();
        columns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(340) });
        columns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(22) });
        columns.ColumnDefinitions.Add(new ColumnDefinition());
        var left = new StackPanel();
        var center = new StackPanel();
        var modes = new Grid();
        modes.ColumnDefinitions.Add(new ColumnDefinition());
        modes.ColumnDefinitions.Add(new ColumnDefinition());
        int m = 0;
        foreach (var key in new[] { "tunnel", "proxy" })
        {
            var mode = key;
            var selected = Settings["mode"]?.ToString() == mode;
            var button = Action(key, () => SetMode(mode), key == "tunnel" ? "shield" : "globe", selected);
            button.Margin = new Thickness(0);
            button.Padding = new Thickness(10, 8, 10, 8);
            Grid.SetColumn(button, m++);
            modes.Children.Add(button);
        }
        center.Children.Add(new Border { Child = modes, BorderBrush = B("Line"), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(8), Padding = new Thickness(4), Margin = new Thickness(0, 0, 0, 16) });
        power = new Button { Style = (Style)FindResource("PowerControl"), Content = Glyphs.Icon("power", B("Orange"), 52), Width = 156, Height = 156, BorderThickness = new Thickness(2), Margin = new Thickness(0, 8, 0, 12), HorizontalAlignment = HorizontalAlignment.Center };
        AutomationProperties.SetName(power, T("connect"));
        power.Click += async (_, _) => await Toggle();
        center.Children.Add(power);
        status = Label(T(active), 12, "Subtext");
        status.HorizontalAlignment = HorizontalAlignment.Center;
        center.Children.Add(status);
        var selection = new Grid();
        selection.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(40) });
        selection.ColumnDefinitions.Add(new ColumnDefinition());
        selection.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(20) });
        selection.Children.Add(CountryFlags.Icon(Selected()?["name"]?.ToString() ?? ""));
        var details = new StackPanel();
        details.Children.Add(Label(T("selected"), 10, "Subtext"));
        var name = Label(CountryFlags.WithoutEmoji(Selected()?["name"]?.ToString() ?? T("select_profile")), 12, "Text", true);
        name.TextWrapping = TextWrapping.NoWrap;
        name.TextTrimming = TextTrimming.CharacterEllipsis;
        details.Children.Add(name);
        if (Selected() != null)
            details.Children.Add(Label(ProtocolLabel(Selected()!), 10, "Subtext"));
        Grid.SetColumn(details, 1);
        selection.Children.Add(details);
        var arrow = Glyphs.Icon("next", B("Orange"), 16);
        Grid.SetColumn(arrow, 2);
        selection.Children.Add(arrow);
        var select = new Button { Content = selection, HorizontalContentAlignment = HorizontalAlignment.Stretch, Margin = new Thickness(0, 14, 0, 14), Padding = new Thickness(12) };
        select.Click += (_, _) => Navigate("servers");
        center.Children.Add(select);
        connect = Action("connect", Toggle, "power", true);
        connect.Margin = new Thickness(0);
        center.Children.Add(connect);
        if (Settings["mode"]?.ToString() == "proxy")
            center.Children.Add(Label($"SOCKS5 {Settings["bind"]}:{Settings["port"]} · HTTP {(Settings["port"]?.GetValue<int>() ?? 10808) + 1}", 11, "Subtext"));
        left.Children.Add(Card(center, false, 20));
        var metrics = new Grid();
        for (int i = 0; i < 3; i++)
            metrics.ColumnDefinitions.Add(new ColumnDefinition());
        var keys = new[] { "down", "up", "latency" };
        var colors = new[] { "Download", "Orange", "Latency" };
        for (int i = 0; i < 3; i++)
        {
            var item = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
            item.Children.Add(Glyphs.Icon(i == 0 ? "download" : i == 1 ? "upload" : "clock", B(colors[i]), 18));
            item.Children.Add(Label(T(keys[i]), 10, "Subtext"));
            var value = Label(i == 2 ? "—" : "0 B/s", 16, colors[i], true);
            if (i == 0)
                downText = value;
            else if (i == 1)
                upText = value;
            else
                latencyText = value;
            item.Children.Add(value);
            Grid.SetColumn(item, i);
            metrics.Children.Add(item);
        }
        left.Children.Add(Card(metrics, false, 16));
        left.Children.Add(Label(T(Settings["mode"]?.ToString() == "tunnel" ? "not_full_protection" : "proxy_help"), 11, "Subtext"));
        columns.Children.Add(left);
        var right = new StackPanel();
        right.Children.Add(Label(T("your_servers"), 17, "Text", true));
        var tabs = new StackPanel { Margin = new Thickness(0, 8, 0, 8) };
        SubscriptionTabs(tabs, Render);
        right.Children.Add(tabs);
        right.Children.Add(ServerHeader());
        var list = new StackPanel();
        foreach (var item in Profiles.Where(x => x != null && (groupFilter == "" || x["group"]?.ToString() == groupFilter)))
            list.Children.Add(ServerRow(item!));
        if (Profiles.Count == 0)
            list.Children.Add(Empty("empty", () => { Navigate("configs"); return Task.CompletedTask; }));
        right.Children.Add(new ScrollViewer { Content = list, MaxHeight = 530 });
        right.Children.Add(Label(Profiles.Count + " " + T("all_servers_count"), 11, "Subtext"));
        var card = Card(right, false, 20);
        Grid.SetColumn(card, 2);
        columns.Children.Add(card);
        body.Children.Add(columns);
    }
    TrafficGraph NewGraph(double height)
    {
        var g = new TrafficGraph { Height = height, UpBrush = B("Orange"), DownBrush = B("Download"), GridBrush = B("Line"), Margin = new Thickness(0, 8, 0, 0) };
        g.Samples.AddRange(samples);
        g.ToolTip = T("measured_only");
        return g;
    }
    FrameworkElement Empty(string message, Func<Task> import)
    {
        var p = new StackPanel { Margin = new Thickness(18, 30, 18, 30) };
        p.Children.Add(Glyphs.Icon("servers", B("Muted"), 38));
        var title = Label(T(message), 18, "Subtext", true);
        title.TextAlignment = TextAlignment.Center;
        title.Margin = new Thickness(0, 16, 0, 12);
        p.Children.Add(title);
        var helper = Label(T("import_hint"), 14, "Muted");
        helper.TextAlignment = TextAlignment.Center;
        p.Children.Add(helper);
        var button = Action("import", import, "plus", true);
        button.HorizontalAlignment = HorizontalAlignment.Center;
        p.Children.Add(button);
        return p;
    }
    async Task SelectProfile(JsonNode p)
    {
        if (ConnectionActive)
            await StopConnection();
        data["selected"] = p["id"]!.ToString();
        Save();
        Navigate("home");
    }
    string ProtocolLabel(JsonNode p)
    {
        var outbound = p["outbound"]!;
        var protocol = outbound["protocol"]?.ToString() ?? "";
        var security = outbound["streamSettings"]?["security"]?.ToString();
        return protocol.Equals("shadowsocks", StringComparison.OrdinalIgnoreCase) ? "Shadowsocks" : protocol.ToUpperInvariant() + (security is "reality" or "tls" ? " / " + security.ToUpperInvariant() : "");
    }
    string GroupLabel(JsonNode p) => Subs.FirstOrDefault(x => x?["id"]?.ToString() == p["group"]?.ToString())?["name"]?.ToString() ?? "—";
    Grid TableColumns()
    {
        var row = new Grid();
        foreach (var width in new[] { 3.2, 2.1, 1.3, 1.6 })
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(width, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(38) });
        return row;
    }
    FrameworkElement ServerHeader()
    {
        var row = TableColumns();
        int i = 0;
        foreach (var key in new[] { "server", "protocol", "subscription", "connection_check" })
        {
            var label = Label(T(key), 11, "Subtext");
            Grid.SetColumn(label, i++);
            row.Children.Add(label);
        }
        return new Border { Child = row, Padding = new Thickness(0, 14, 0, 8), BorderBrush = B("Line"), BorderThickness = new Thickness(0, 0, 0, 1) };
    }
    FrameworkElement ServerRow(JsonNode p, bool compact = false)
    {
        var row = TableColumns();
        var nameGrid = new Grid { Margin = new Thickness(0, 0, 12, 0) };
        nameGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(34) });
        nameGrid.ColumnDefinitions.Add(new ColumnDefinition());
        nameGrid.Children.Add(CountryFlags.Icon(p["name"]?.ToString() ?? ""));
        var name = Label(CountryFlags.WithoutEmoji(p["name"]?.ToString() ?? ""), 12, "Text");
        name.Margin = new Thickness(0);
        name.TextWrapping = TextWrapping.NoWrap;
        name.TextTrimming = TextTrimming.CharacterEllipsis;
        name.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(name, 1);
        nameGrid.Children.Add(name);
        var choose = new Button { Content = nameGrid, Padding = new Thickness(0), MinHeight = 40, HorizontalContentAlignment = HorizontalAlignment.Stretch, Background = Brushes.Transparent, BorderBrush = Brushes.Transparent, IsEnabled = p["enabled"]?.GetValue<bool>() != false, ToolTip = CountryFlags.WithoutEmoji(p["name"]?.ToString() ?? "") };
        choose.Click += async (_, _) => await SelectProfile(p);
        row.Children.Add(choose);
        var values = new[] { ProtocolLabel(p), GroupLabel(p), p["probeLatency"]?.ToString() is string ms ? ms + " ms" : T("not_tested") };
        for (int i = 0; i < values.Length; i++)
        {
            var label = Label(values[i], 11, i == 2 && p["reachable"]?.GetValue<bool>() == true ? "Success" : "Subtext");
            label.Margin = new Thickness(0, 0, 8, 0);
            label.VerticalAlignment = VerticalAlignment.Center;
            label.TextWrapping = TextWrapping.NoWrap;
            label.TextTrimming = TextTrimming.CharacterEllipsis;
            Grid.SetColumn(label, i + 1);
            row.Children.Add(label);
        }
        var more = ProfileMenu(p);
        more.Content = new TextBlock { Text = "⋮", Foreground = B("Subtext"), FontSize = 19 };
        more.MinHeight = 36;
        more.Padding = new Thickness(6);
        more.Margin = new Thickness(0);
        more.Background = Brushes.Transparent;
        more.BorderBrush = Brushes.Transparent;
        Grid.SetColumn(more, 4);
        row.Children.Add(more);
        return new Border { Child = row, MinHeight = 42, BorderBrush = B("Line"), BorderThickness = new Thickness(0, 0, 0, 1), Background = data["selected"]?.ToString() == p["id"]?.ToString() ? B("Panel") : Brushes.Transparent };
    }
    Button IconAction(string key, string icon, Func<Task> action, string color = "Subtext")
    {
        var b = Action(key, action);
        b.Content = Glyphs.Icon(icon, B(color), 17);
        b.ToolTip = T(key);
        AutomationProperties.SetName(b, T(key));
        b.Padding = new Thickness(10);
        b.Margin = new Thickness(4, 0, 0, 0);
        return b;
    }
    Button ProfileMenu(JsonNode p)
    {
        var b = IconAction("manage", "settings", () => Task.CompletedTask);
        var menu = new ContextMenu { Background = B("Secondary"), Foreground = B("Text") };
        void Add(string key, Func<Task> action)
        {
            var item = new MenuItem { Header = T(key), Padding = new Thickness(12, 8, 12, 8) };
            item.Click += async (_, _) => { try { await action(); } catch (Exception e) { Notice(Code(e)); } };
            menu.Items.Add(item);
        }
        Add("select", () => SelectProfile(p));
        Add("test", () => RunProbes(new[] { p }));
        Add("rename", () => { var name = Prompt("name", p["name"]!.ToString()); if (!string.IsNullOrWhiteSpace(name)) { p["name"] = name[..Math.Min(200, name.Length)]; p["userNamed"] = true; Save(); Render(); } return Task.CompletedTask; });
        Add("duplicate", () => { var copy = p.DeepClone(); copy["id"] = Guid.NewGuid().ToString("N"); Profiles.Add(copy); Save(); Render(); return Task.CompletedTask; });
        Add("toggle", async () => { if (ConnectionActive && data["selected"]?.ToString() == p["id"]?.ToString()) await StopConnection(); p["enabled"] = !(p["enabled"]?.GetValue<bool>() ?? true); Save(); Render(); });
        Add("edit_server", () => EditServer(p));
        Add("delete", async () => { if (!Confirm("confirm_delete")) return; if (ConnectionActive && data["selected"]?.ToString() == p["id"]?.ToString()) await StopConnection(); Profiles.Remove(p); RepairSelection(); Save(); Render(); });
        b.ContextMenu = menu;
        b.Click += (_, _) => { menu.PlacementTarget = b; menu.IsOpen = true; };
        return b;
    }
    void RepairSelection()
    {
        if (Selected() == null)
            data["selected"] = Profiles.FirstOrDefault(p => p?["enabled"]?.GetValue<bool>() == true)?["id"]?.ToString() ?? "";
    }
    string serverSort = "name", groupFilter = ""; bool favoritesOnly;
    void SubscriptionTabs(Panel parent, Action changed)
    {
        var tabs = new WrapPanel();
        void Tab(string title, string id)
        {
            var count = Profiles.Count(x => x != null && (id == "" || x["group"]?.ToString() == id));
            var selected = groupFilter == id;
            var button = new Button { Content = title + "   " + count, Foreground = B(selected ? "App" : "Subtext"), Background = B(selected ? "Orange" : "Input"), Margin = new Thickness(0, 0, 8, 0), Padding = new Thickness(16, 8, 16, 8), MinHeight = 40 };
            button.Click += (_, _) => { groupFilter = id; changed(); };
            tabs.Children.Add(button);
        }
        Tab(T("all"), "");
        foreach (var sub in Subs.Where(x => x != null))
            Tab(sub!["name"]?.ToString() ?? "", sub["id"]!.ToString());
        parent.Children.Add(tabs);
    }
    Button ServerTools()
    {
        var tools = new Button { Content = "⋮", MinWidth = 42, Margin = new Thickness(8, 0, 0, 0) };
        var menu = new ContextMenu();
        void Add(string key, Func<Task> action)
        {
            var item = new MenuItem { Header = T(key) };
            item.Click += async (_, _) => { try { await action(); } catch (Exception e) { Notice(Code(e)); } };
            menu.Items.Add(item);
        }
        Add("test_configs", () => RunProbes(Profiles.Where(x => x != null && x["enabled"]?.GetValue<bool>() != false && (groupFilter == "" || x["group"]?.ToString() == groupFilter)).Select(x => x!)));
        Add("favorites", () => { favoritesOnly = !favoritesOnly; Navigate("servers"); return Task.CompletedTask; });
        Add("edit_server", () => Selected() != null ? EditServer(Selected()!) : throw new InvalidOperationException("select_profile"));
        Add("copy_link", CopySelectedLink);
        Add("delete", async () => { var p = Selected() ?? throw new InvalidOperationException("select_profile"); if (!Confirm("confirm_delete")) return; if (ConnectionActive) await StopConnection(); Profiles.Remove(p); RepairSelection(); Save(); Render(); });
        tools.Click += (_, _) => { menu.PlacementTarget = tools; menu.IsOpen = true; };
        return tools;
    }
    void Servers()
    {
        var p = new StackPanel();
        var toolbar = new DockPanel { Margin = new Thickness(0, 0, 0, 16) };
        var controls = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        var sort = new ComboBox { MinWidth = 185 };
        foreach (var key in new[] { "sort", "connection_latency", "sort_subscription", "recently_added" })
            sort.Items.Add(T(key));
        sort.SelectedIndex = serverSort switch
        {
            "latency" => 1,
            "subscription" => 2,
            "recent" => 3,
            _ => 0
        };
        controls.Children.Add(sort);
        controls.Children.Add(ServerTools());
        DockPanel.SetDock(controls, Dock.Right);
        toolbar.Children.Add(controls);
        var tabHost = new StackPanel();
        SubscriptionTabs(tabHost, Render);
        toolbar.Children.Add(tabHost);
        p.Children.Add(toolbar);
        var search = new TextBox { MinHeight = 42, Margin = new Thickness(0, 0, 0, 12) };
        System.Windows.Automation.AutomationProperties.SetName(search, T("search"));
        search.ToolTip = T("search");
        var searchBox = new Grid();
        searchBox.Children.Add(search);
        var searchHint = Label(T("search"), 12, "Subtext");
        searchHint.IsHitTestVisible = false;
        searchHint.Margin = new Thickness(14, 11, 14, 0);
        searchBox.Children.Add(searchHint);
        search.TextChanged += (_, _) => searchHint.Visibility = string.IsNullOrEmpty(search.Text) ? Visibility.Visible : Visibility.Collapsed;
        p.Children.Add(searchBox);
        p.Children.Add(ServerHeader());
        var list = new StackPanel();
        p.Children.Add(list);
        void Fill()
        {
            list.Children.Clear();
            IEnumerable<JsonNode> items = Profiles.Where(x => x != null).Select(x => x!).Where(x => (groupFilter == "" || x["group"]?.ToString() == groupFilter) && (!favoritesOnly || x["favorite"]?.GetValue<bool>() == true) && CountryFlags.WithoutEmoji(x["name"]?.ToString() ?? "").Contains(search.Text, StringComparison.CurrentCultureIgnoreCase));
            items = serverSort switch
            {
                "latency" => items.OrderBy(x => x["probeLatency"] == null).ThenBy(x => x["probeLatency"]?.GetValue<long>() ?? long.MaxValue),
                "subscription" => items.OrderBy(GroupLabel),
                "recent" => items.Reverse(),
                _ => items.OrderBy(x => CountryFlags.WithoutEmoji(x["name"]?.ToString() ?? ""))
            };
            foreach (var item in items)
                list.Children.Add(ServerRow(item));
            if (list.Children.Count == 0)
                list.Children.Add(Empty("no_matches", () => { Navigate("configs"); return Task.CompletedTask; }));
        }
        sort.SelectionChanged += (_, _) => { serverSort = sort.SelectedIndex switch { 1 => "latency", 2 => "subscription", 3 => "recent", _ => "name" }; Fill(); };
        search.TextChanged += (_, _) => Fill();
        Fill();
        p.Children.Add(Label(Profiles.Count + " " + T("all_servers_count"), 11, "Subtext"));
        body.Children.Add(Card(p, false, 20));
    }
    Task CopySelectedLink()
    {
        var p = Selected() ?? throw new InvalidOperationException("select_profile");
        if (p["sourceLink"]?.ToString() is not string link || string.IsNullOrEmpty(link))
            throw new InvalidOperationException("link_not_available");
        System.Windows.Clipboard.SetText(link);
        Notice("copied");
        return Task.CompletedTask;
    }
    async Task RunProbes(IEnumerable<JsonNode> selected)
    {
        probes?.Cancel();
        probes?.Dispose();
        probes = new CancellationTokenSource();
        var token = probes.Token;
        Notice("testing_in_progress");
        try
        {
            if (ConnectionActive)
                throw new InvalidOperationException("already_running");
            var result = await ConfigurationProbe.All(selected.Select(p => p.DeepClone()).ToArray(), Settings.DeepClone(), token);
            foreach (var r in result)
            {
                var p = Profiles.FirstOrDefault(p => p?["id"]?.ToString() == r.Id);
                if (p == null)
                    continue;
                p["probeLatency"] = r.Milliseconds;
                p["reachable"] = r.Reachable;
                p["probeAt"] = DateTime.UtcNow.ToString("O");
                p["probeKind"] = "configuration";
            }
            Save();
            Render();
        }
        catch (OperationCanceledException) { Notice("cancelled"); }
    }
    Task SelectBest()
    {
        var best = Profiles.Where(p => p?["enabled"]?.GetValue<bool>() == true && p["reachable"]?.GetValue<bool>() == true && p["probeLatency"] != null).OrderBy(p => p!["probeLatency"]!.GetValue<long>()).FirstOrDefault();
        if (best == null)
            throw new InvalidOperationException("not_tested");
        return SelectProfile(best);
    }
    void ProfileManager()
    {
        var p = Panel("profiles");
        p.Children.Add(Label(T("import_hint"), 14, "Subtext"));
        var search = Field(p, "search");
        var rows = new StackPanel();
        void Fill()
        {
            rows.Children.Clear();
            foreach (var item in Profiles.Where(p => p != null && (p["name"]?.ToString() ?? "").Contains(search.Text, StringComparison.CurrentCultureIgnoreCase)).Take(256))
                rows.Children.Add(ServerRow(item!));
            if (rows.Children.Count == 0)
                rows.Children.Add(Empty(Profiles.Count == 0 ? "empty" : "no_matches", () => { Navigate("configs"); return Task.CompletedTask; }));
        }
        search.TextChanged += (_, _) => Fill();
        p.Children.Add(rows);
        Fill();
        p.Children.Add(Actions(Action("import", () => { Navigate("configs"); return Task.CompletedTask; }, "plus", true), Action("export", ExportBackup, "copy"), Action("restore", RestoreBackup, "refresh")));
        body.Children.Add(Card(p));
    }
    async Task ImportText(string text)
    {
        var result = await core!.Call(new
        {
            method = "import",
            text
        });
        MergeItems((JsonArray)result["profiles"]!, null);
        foreach (var line in text.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries))
        {
            if (!line.Contains("://"))
                continue;
            try
            {
                var single = await core.Call(new
                {
                    method = "import",
                    text = line
                });
                foreach (var entry in single["profiles"]!.AsArray())
                {
                    var stored = Profiles.FirstOrDefault(x => x?["id"]?.ToString() == entry?["id"]?.ToString());
                    if (stored != null)
                        stored["sourceLink"] = line;
                }
            }
            catch { }
        }
        data["importInfo"] = result["info"]?.DeepClone() ?? new JsonArray();
        Save();
        Navigate("servers");
        if (toast != null)
        {
            toast.Text = $"{T("imported")}: {((JsonArray)result["profiles"]!).Count} / {result["rejected"]}";
            toast.Visibility = Visibility.Visible;
        }
    }
    void MergeItems(JsonArray items, string? group)
    {
        foreach (var item in items.Where(x => x != null))
        {
            var copy = item!.DeepClone();
            copy["name"] = DisplayNames.Repair(copy["name"]?.ToString() ?? "");
            if (group != null)
            {
                copy["group"] = group;
                copy["id"] = group + ":" + item["id"]!.ToString();
            }
            var existing = Profiles.FirstOrDefault(p => p?["id"]?.ToString() == copy["id"]!.ToString());
            if (existing == null)
                Profiles.Add(copy);
            else
            {
                existing["outbound"] = copy["outbound"]!.DeepClone();
                existing["sourceLink"] = copy["sourceLink"]?.DeepClone();
                if (existing["userNamed"]?.GetValue<bool>() != true)
                    existing["name"] = copy["name"]!.DeepClone();
                existing.AsObject().Remove("probeLatency");
                existing.AsObject().Remove("reachable");
            }
        }
        RepairSelection();
    }
    async Task AddDirectLinks(string text)
    {
        if (text.Length > 2 * 1024 * 1024)
            throw new InvalidOperationException("import_too_large");
        var links = text.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries);
        if (links.Length == 0 || links.Any(line => !new[] { "vless://", "vmess://", "trojan://", "ss://" }.Any(prefix => line.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))))
            throw new InvalidOperationException("direct_links_only");
        await ImportText(text);
    }
    StackPanel SubscriptionForm()
    {
        var form = Panel("add_subscription");
        form.Children.Add(Label(T("subscription_prompt"), 12, "Subtext"));
        var name = Field(form, "subscription_name");
        var url = Field(form, "sub_url");
        form.Children.Add(Actions(Action("add_subscription", async () =>
        {
            var address = url.Text.Trim();
            if (Subs.Any(x => x?["url"]?.ToString() == address))
                throw new InvalidOperationException("subscription_exists");
            var sub = new JsonObject { ["id"] = Guid.NewGuid().ToString("N"), ["name"] = string.IsNullOrWhiteSpace(name.Text) ? new Uri(address).Host : name.Text.Trim(), ["url"] = address, ["hours"] = 0, ["paused"] = false };
            await Refresh(sub);
            Subs.Add(sub);
            Save();
            Navigate("servers");
        }, "plus")));
        return form;
    }
    void Configs()
    {
        var columns = new Grid();
        columns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1.35, GridUnitType.Star) });
        columns.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(22) });
        columns.ColumnDefinitions.Add(new ColumnDefinition());
        var form = Panel("add_configuration");
        form.Children.Add(Label(T("paste_server_links"), 12, "Subtext"));
        var input = new TextBox { AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, MinHeight = 225, MaxHeight = 300, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Margin = new Thickness(0, 14, 0, 14), FlowDirection = FlowDirection.LeftToRight };
        System.Windows.Automation.AutomationProperties.SetName(input, T("server_links"));
        var placeholder = new TextBlock { Text = "vless://…\n\nvmess://…\n\ntrojan://…\n\nss://…", Foreground = B("Subtext"), Margin = new Thickness(16, 30, 16, 16), IsHitTestVisible = false, FontFamily = new FontFamily("Consolas"), FontSize = 13 };
        var entry = new Grid();
        entry.Children.Add(input);
        entry.Children.Add(placeholder);
        input.TextChanged += (_, _) => placeholder.Visibility = string.IsNullOrEmpty(input.Text) ? Visibility.Visible : Visibility.Collapsed;
        form.Children.Add(entry);
        var toolbar = new DockPanel();
        var add = Action("add_servers", () => AddDirectLinks(input.Text), "plus", true);
        DockPanel.SetDock(add, Dock.Right);
        toolbar.Children.Add(add);
        toolbar.Children.Add(Actions(Action("paste", () => { input.Text = System.Windows.Clipboard.GetText(); return Task.CompletedTask; }, "copy"), Action("file", async () => { var file = new OpenFileDialog { Filter = "Server links (*.txt;*.conf)|*.txt;*.conf|All files (*.*)|*.*" }; if (file.ShowDialog() != true) return; if (new FileInfo(file.FileName).Length > 2 * 1024 * 1024) throw new InvalidOperationException("import_too_large"); input.Text = await File.ReadAllTextAsync(file.FileName); }, "configs"), Action("scan_qr", async () => { var value = await ReadQrImage(); if (value != null) input.Text = value; }, "configs")));
        form.Children.Add(toolbar);
        form.Children.Add(Label(T("direct_protocols"), 12, "Subtext"));
        var left = Card(form, false, 20);
        left.MinHeight = 420;
        columns.Children.Add(left);
        var right = new StackPanel();
        right.Children.Add(Card(SubscriptionForm(), false, 20));
        var info = Panel("subscription_details");
        info.Children.Add(Label(T("subscription_details_help"), 12, "Subtext"));
        if (data["importInfo"] is JsonArray details)
            foreach (var line in details)
                info.Children.Add(Label(line?.ToString() ?? "", 12, "Subtext"));
        right.Children.Add(Card(info, false, 20));
        Grid.SetColumn(right, 2);
        columns.Children.Add(right);
        body.Children.Add(columns);
    }
}
