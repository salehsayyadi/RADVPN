using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Security.Principal;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Threading;
using Microsoft.Win32;
using Forms = System.Windows.Forms;

namespace RADVPN;
internal sealed partial class MainWindow : Window
{
    JsonObject data = Storage.Fresh(); Dictionary<string, string> words = new(); Core? core;
    readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(1) };
    readonly TrafficLedger ledger = new(); readonly List<(DateTime Time, string Code)> events = new(); readonly List<(double Up, double Down)> samples = new();
    readonly string[] args; readonly bool smoke;
    Forms.NotifyIcon? tray; bool exitRequested, closing, allowClose, ticking, storageLocked, busy; int tick; string page = "home", active = "disconnected", settingsTab = "general"; JsonObject? draft;
    Grid main = new(); StackPanel body = new(); TextBlock? status, sessionInfo, upText, downText, latencyText, toast; Button? connect, power; TrafficGraph? graph; CancellationTokenSource? probes;
    JsonNode? lastSnapshot; string trafficRange = "today";
    JsonArray Profiles => (JsonArray)data["profiles"]!; JsonArray Subs => (JsonArray)data["subscriptions"]!; JsonObject Settings => (JsonObject)data["settings"]!;
    string Lang => data["lang"]?.ToString() ?? "en";
    string T(string key) => words.GetValueOrDefault(key, words.GetValueOrDefault("operation_failed", "Operation failed"));
    Brush B(string name) => (Brush)FindResource(name);
    public MainWindow(string[] arguments)
    {
        args = arguments;
        smoke = args.Contains("--ui-smoke");
        Title = "RADVPN 2.0.0";
        Width = 1440;
        Height = 900;
        MinWidth = 1120;
        MinHeight = 680;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        FontFamily = new FontFamily("Segoe UI, Tahoma");
        FontSize = 14;
        Resources.MergedDictionaries.Add(new ResourceDictionary { Source = new Uri("/RADVPN;component/UI/Theme.xaml", UriKind.Relative) });
        Background = B("App");
        Foreground = B("Text");
        try
        {
            data = Storage.Load();
            if (Storage.Recovered)
                events.Add((DateTime.Now, "storage_recovered"));
        }
        catch { storageLocked = true; events.Add((DateTime.Now, "storage_failed")); }
        SourceInitialized += (_, _) => System.Windows.Interop.HwndSource.FromHwnd(new System.Windows.Interop.WindowInteropHelper(this).Handle)?.AddHook((IntPtr hwnd, int message, IntPtr w, IntPtr l, ref bool handled) => { if (message == WindowSignals.QuitMessage) { handled = true; exitRequested = true; Close(); } return IntPtr.Zero; });
        LoadWords();
        Render();
        Loaded += async (_, _) => await Initialize();
        timer.Tick += async (_, _) => await Poll();
        StateChanged += (_, _) => { if (WindowState == WindowState.Minimized && data["tray"]?.GetValue<bool>() == true && !smoke) Hide(); };
        Closing += async (_, e) => { if (allowClose) return; e.Cancel = true; if (!exitRequested && data["tray"]?.GetValue<bool>() == true && !smoke) { Hide(); return; } if (closing) return; closing = true; timer.Stop(); probes?.Cancel(); try { await StopConnection(); StoreGeometry(); Save(); } catch { } core?.Dispose(); tray?.Dispose(); allowClose = true; Close(); };
    }
    async Task Initialize()
    {
        try
        {
            core = new Core();
            if (data["settings"] == null)
                data["settings"] = await core.Call(new
                {
                    method = "defaults"
                });
            var version = await core.Call(new
            {
                method = "version"
            });
            data["coreVersion"] = version["core"]?.ToString();
            events.Insert(0, (DateTime.Now, "core_loaded"));
            if (!smoke)
            {
                RestoreGeometry();
                SetupTray();
            }
            Render();
            timer.Start();
            if (smoke)
            {
                await NativeSmoke.Capture(this, args);
                return;
            }
            if (args.Contains("--minimized"))
                Hide();
            if (data["autoConnect"]?.GetValue<bool>() == true && Settings["mode"]?.ToString() == "proxy" && Selected() != null)
                await Toggle();
        }
        catch (Exception e) { active = "error"; Notice(Code(e)); }
    }
    void LoadWords()
    {
        words = JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "localization", Lang + ".json")))!;
        FlowDirection = Lang == "fa" ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
    }
    void Save()
    {
        if (storageLocked)
        {
            Notice("storage_failed");
            return;
        }
        try
        {
            Storage.Save(data);
        }
        catch { Notice("storage_failed"); }
    }
    void StoreGeometry()
    {
        if (WindowState == WindowState.Normal)
        {
            data["width"] = ActualWidth;
            data["height"] = ActualHeight;
            data["left"] = Left;
            data["top"] = Top;
        }
    }
    void RestoreGeometry()
    {
        if (double.TryParse(data["width"]?.ToString(), out var w))
            Width = Math.Clamp(w, MinWidth, Math.Max(MinWidth, SystemParameters.WorkArea.Width));
        if (double.TryParse(data["height"]?.ToString(), out var h))
            Height = Math.Clamp(h, MinHeight, Math.Max(MinHeight, SystemParameters.WorkArea.Height));
        if (double.TryParse(data["left"]?.ToString(), out var l) && double.TryParse(data["top"]?.ToString(), out var t))
        {
            Left = Math.Clamp(l, 0, Math.Max(0, SystemParameters.VirtualScreenWidth - Width));
            Top = Math.Clamp(t, 0, Math.Max(0, SystemParameters.VirtualScreenHeight - Height));
        }
    }
    void SetupTray()
    {
        tray = new Forms.NotifyIcon { Icon = new System.Drawing.Icon(Path.Combine(AppContext.BaseDirectory, "RADVPN.ico")), Text = "RADVPN 2.0.0", Visible = true };
        tray.DoubleClick += (_, _) => { Show(); WindowState = WindowState.Normal; Activate(); };
        UpdateTray();
    }
    void UpdateTray()
    {
        if (tray == null)
            return;
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add(T("show"), null, (_, _) => { Show(); WindowState = WindowState.Normal; Activate(); });
        menu.Items.Add(T("quit"), null, (_, _) => { exitRequested = true; Close(); });
        tray.ContextMenuStrip = menu;
    }
    static string Code(Exception e) => e is InvalidOperationException ? e.Message : "operation_failed";
    void Notice(string code)
    {
        events.Insert(0, (DateTime.Now, words.ContainsKey(code) ? code : "operation_failed"));
        if (events.Count > 200)
            events.RemoveAt(events.Count - 1);
        if (toast != null)
        {
            toast.Text = T(code);
            toast.Visibility = Visibility.Visible;
        }
    }
    JsonNode? Selected() => Profiles.FirstOrDefault(p => p?["id"]?.ToString() == data["selected"]?.ToString());
    bool ConnectionActive => active is "starting" or "connecting" or "connected" or "disconnecting";
    async Task Toggle()
    {
        if (busy)
            return;
        busy = true;
        try
        {
            if (ConnectionActive)
            {
                await StopConnection();
                return;
            }
            var p = Selected() ?? throw new InvalidOperationException("select_profile");
            if (Settings["mode"]?.ToString() == "tunnel" && !IsAdministrator())
                throw new InvalidOperationException("admin");
            ledger.Reset();
            active = "starting";
            UpdateConnection();
            events.Insert(0, (DateTime.Now, "starting"));
            await core!.Call(new
            {
                method = "start",
                profile = p,
                settings = Settings,
                fd = -1
            });
            active = "connecting";
            UpdateConnection();
        }
        catch (Exception e) { active = "error"; Notice(Code(e)); UpdateConnection(); }
        finally { busy = false; }
    }
    static bool IsAdministrator() => new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator);
    async Task StopConnection()
    {
        if (core == null)
            return;
        try
        {
            var s = await core.Call(new
            {
                method = "status"
            });
            RecordTraffic(s);
        }
        catch { }
        active = "disconnecting";
        UpdateConnection();
        await core.Call(new
        {
            method = "stop"
        });
        active = "disconnected";
        ledger.Reset();
        lastSnapshot = null;
        Save();
        UpdateConnection();
    }
    async Task SetMode(string mode)
    {
        if (busy)
            throw new InvalidOperationException("already_running");
        if (core != null)
            await StopConnection();
        Settings["mode"] = mode;
        if (mode == "tunnel")
            data["autoConnect"] = false;
        Save();
        Render();
    }
    void RecordTraffic(JsonNode s)
    {
        var up = s["up"]?.GetValue<long>() ?? 0;
        var down = s["down"]?.GetValue<long>() ?? 0;
        if (s["state"]?.ToString() is "connecting" or "connected")
        {
            ledger.Sample(data, up, down);
            samples.Add((ledger.UpRate, ledger.DownRate));
            if (samples.Count > 90)
                samples.RemoveAt(0);
            if (graph != null)
            {
                graph.Samples.Clear();
                graph.Samples.AddRange(samples);
                graph.InvalidateVisual();
            }
        }
    }
    async Task Poll()
    {
        if (ticking || core == null)
            return;
        ticking = true;
        try
        {
            var s = await core.Call(new
            {
                method = "status"
            });
            var state = s["state"]?.ToString() ?? "error";
            RecordTraffic(s);
            lastSnapshot = s;
            if (state != active)
            {
                events.Insert(0, (DateTime.Now, state));
                if (events.Count > 200)
                    events.RemoveAt(200);
                if (state == "connected")
                {
                    data["lastWorking"] = new JsonObject { ["settings"] = Settings.DeepClone(), ["profile"] = Selected()?.DeepClone() };
                    Save();
                }
                active = state;
            }
            if (s["error"]?.ToString() is string error && error != "" && status != null)
            {
                status.Text = T(error);
                status.Foreground = B("Error");
            }
            else
                UpdateConnection();
            UpdateMetrics();
            if (++tick % 15 == 0)
                Save();
            if (tick % 60 == 0 && active == "disconnected")
                foreach (var sub in Subs.ToArray())
                {
                    if (sub == null || sub["paused"]?.GetValue<bool>() == true)
                        continue;
                    var hours = sub["hours"]?.GetValue<int>() ?? 0;
                    if (hours > 0 && DateTime.TryParse(sub["last"]?.ToString(), out var last) && DateTime.UtcNow - last.ToUniversalTime() > TimeSpan.FromHours(hours))
                    {
                        try
                        {
                            await Refresh(sub);
                        }
                        catch { sub["status"] = "refresh_failed"; }
                        Save();
                    }
                }
        }
        catch { active = "error"; Notice("core_start_failed"); UpdateConnection(); }
        finally { ticking = false; }
    }
    void UpdateConnection()
    {
        if (status != null)
        {
            status.Text = T(active);
            status.Foreground = B(active == "connected" ? "Success" : active == "error" ? "Error" : "Subtext");
        }
        if (connect != null)
        {
            connect.Content = ButtonContent(ConnectionActive ? "disconnect" : "connect", ConnectionActive ? "stop" : "power", true);
            connect.IsEnabled = active != "disconnecting";
        }
        if (power != null)
        {
            power.ToolTip = T(ConnectionActive ? "disconnect" : "connect");
            System.Windows.Automation.AutomationProperties.SetName(power, T(ConnectionActive ? "disconnect" : "connect"));
            power.IsEnabled = active != "disconnecting";
            power.BorderBrush = B(active == "error" ? "Error" : "Orange");
            power.BeginAnimation(OpacityProperty, null);
            power.Opacity = 1;
            if (active is "connecting" or "starting" && SystemParameters.ClientAreaAnimation)
                power.BeginAnimation(OpacityProperty, new DoubleAnimation(.45, 1, new Duration(TimeSpan.FromMilliseconds(750))) { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever });
        }
    }
    void UpdateMetrics()
    {
        if (upText != null)
            upText.Text = TrafficLedger.Bytes(ledger.UpRate) + "/s";
        if (downText != null)
            downText.Text = TrafficLedger.Bytes(ledger.DownRate) + "/s";
        if (latencyText != null)
            latencyText.Text = lastSnapshot?["latency"]?.ToString() is string value ? value + " ms" : "—";
        if (sessionInfo != null)
            sessionInfo.Text = $"{T("up")}: {TrafficLedger.Bytes(lastSnapshot?["up"]?.GetValue<long>() ?? 0)}   ·   {T("down")}: {TrafficLedger.Bytes(lastSnapshot?["down"]?.GetValue<long>() ?? 0)}   ·   {TimeSpan.FromSeconds(lastSnapshot?["seconds"]?.GetValue<long>() ?? 0):hh\\:mm\\:ss}";
    }
    public void Navigate(string target)
    {
        page = target;
        draft = null;
        Render();
    }
    void Render()
    {
        status = null;
        sessionInfo = null;
        upText = downText = latencyText = null;
        connect = power = null;
        graph = null;
        var shell = new Grid { Background = B("App") };
        shell.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(204) });
        shell.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var sidebar = new DockPanel { Background = B("Sidebar"), Margin = new Thickness(0) };
        var brand = new StackPanel { Margin = new Thickness(22, 24, 18, 24) };
        var wordmark = new StackPanel { Orientation = Orientation.Horizontal, FlowDirection = FlowDirection.LeftToRight };
        wordmark.Children.Add(Glyphs.Icon("shield", B("Orange"), 34));
        wordmark.Children.Add(new TextBlock { Text = "RAD", FontSize = 24, FontWeight = FontWeights.Bold, Foreground = B("Orange"), Margin = new Thickness(8, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center });
        wordmark.Children.Add(new TextBlock { Text = "VPN", FontSize = 24, FontWeight = FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center });
        brand.Children.Add(wordmark);
        brand.Children.Add(Label("2.0.0  ·  " + T("testing_channel"), 11, "Muted"));
        DockPanel.SetDock(brand, Dock.Top);
        sidebar.Children.Add(brand);
        var bottom = new StackPanel { Margin = new Thickness(18) };
        bottom.Children.Add(Label(T("private_local"), 12, "Muted"));
        bottom.Children.Add(Action("quit", () => { exitRequested = true; Close(); return Task.CompletedTask; }, "power"));
        DockPanel.SetDock(bottom, Dock.Bottom);
        sidebar.Children.Add(bottom);
        var navigation = new StackPanel { Margin = new Thickness(12, 0, 12, 0) };
        foreach (var key in new[] { "home", "servers", "subscriptions", "configs", "profiles", "traffic", "settings", "logs", "about" })
        {
            var target = key;
            var b = Action(key, () => { Navigate(target); return Task.CompletedTask; }, key);
            b.Style = (Style)FindResource("Nav");
            b.Content = ButtonContent(key, key, false);
            if (page == key || (page == "advanced" && key == "settings"))
            {
                b.Background = new SolidColorBrush(Color.FromArgb(46, 255, 106, 0));
                b.Foreground = B("Highlight");
                b.BorderBrush = B("OrangeBorder");
                b.Content = ButtonContent(key, key, false, true);
            }
            navigation.Children.Add(b);
        }
        sidebar.Children.Add(new ScrollViewer { Content = navigation });
        shell.Children.Add(sidebar);
        var content = new Grid { Margin = new Thickness(26, 22, 26, 22) };
        Grid.SetColumn(content, 1);
        content.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        content.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        content.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        var title = new DockPanel { Margin = new Thickness(0, 0, 0, 20) };
        var languages = new StackPanel { Orientation = Orientation.Horizontal, FlowDirection = FlowDirection.LeftToRight, HorizontalAlignment = HorizontalAlignment.Right };
        foreach (var lang in new[] { "en", "fa" })
        {
            var l = lang;
            var b = Action("language", () => { data["lang"] = l; LoadWords(); UpdateTray(); Save(); Render(); return Task.CompletedTask; });
            b.Content = lang == "en" ? "EN" : "FA";
            b.Padding = new Thickness(12, 7, 12, 7);
            b.MinHeight = 34;
            b.Margin = new Thickness(6, 0, 0, 0);
            if (Lang == lang)
                b.Foreground = B("Orange");
            languages.Children.Add(b);
        }
        DockPanel.SetDock(languages, Dock.Right);
        title.Children.Add(languages);
        var heading = new StackPanel();
        heading.Children.Add(Label(T(page), 26, "Text", true));
        heading.Children.Add(Label(T("development"), 12, "Muted"));
        title.Children.Add(heading);
        content.Children.Add(title);
        toast = Label("", 13, "Warning");
        toast.Margin = new Thickness(0, 0, 0, 12);
        toast.Visibility = Visibility.Collapsed;
        Grid.SetRow(toast, 1);
        content.Children.Add(toast);
        main = new Grid();
        Grid.SetRow(main, 2);
        body = new StackPanel();
        main.Children.Add(new ScrollViewer { Content = body });
        content.Children.Add(main);
        shell.Children.Add(content);
        Content = shell;
        if (data["settings"] == null)
        {
            body.Children.Add(Label(T("starting")));
            return;
        }
        switch (page)
        {
            case "home":
                Home();
                break;
            case "servers":
                Servers();
                break;
            case "subscriptions":
                Subscriptions();
                break;
            case "configs":
                Configs();
                break;
            case "profiles":
                ProfileManager();
                break;
            case "traffic":
                Traffic();
                break;
            case "settings":
                SettingsPage();
                break;
            case "advanced":
                Advanced();
                break;
            case "logs":
                Logs();
                break;
            case "about":
                About();
                break;
        }
        UpdateConnection();
        UpdateMetrics();
    }
    TextBlock Label(string value, double size = 14, string color = "Text", bool strong = false) => new() { Text = value, FontSize = size, Foreground = B(color == "Muted" ? "MutedText" : color), FontWeight = strong ? FontWeights.SemiBold : FontWeights.Normal, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 8) };
    FrameworkElement ButtonContent(string key, string? icon = null, bool primary = false, bool selected = false)
    {
        if (icon == null)
            return Label(T(key), 14, primary ? "App" : "Subtext");
        var row = new StackPanel { Orientation = Orientation.Horizontal };
        row.Children.Add(Glyphs.Icon(icon, B(primary ? "App" : selected ? "Highlight" : "Subtext"), 17));
        row.Children.Add(new TextBlock { Text = T(key), Margin = FlowDirection == FlowDirection.RightToLeft ? new Thickness(0, 0, 9, 0) : new Thickness(9, 0, 0, 0), Foreground = B(primary ? "App" : selected ? "Highlight" : "Subtext"), VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.NoWrap });
        return row;
    }
    Button Action(string key, Func<Task> action, string? icon = null, bool primary = false)
    {
        var b = new Button { Content = ButtonContent(key, icon, primary), Margin = new Thickness(0, 0, 8, 8) };
        if (primary)
            b.Style = (Style)FindResource("Primary");
        b.Click += async (_, _) => { b.IsEnabled = false; try { await action(); } catch (Exception e) { Notice(Code(e)); } finally { b.IsEnabled = true; } };
        return b;
    }
    Border Card(UIElement child, bool accent = false, double padding = 20) => new() { Background = B("Panel"), BorderBrush = B(accent ? "OrangeBorder" : "Line"), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(10), Padding = new Thickness(padding), Margin = new Thickness(0, 0, 0, 16), Child = child };
    StackPanel Panel(string key)
    {
        var p = new StackPanel();
        p.Children.Add(Label(T(key), 16, "Text", true));
        return p;
    }
    WrapPanel Actions(params Button[] buttons)
    {
        var row = new WrapPanel { Margin = new Thickness(0, 8, 0, 0) };
        foreach (var b in buttons)
            row.Children.Add(b);
        return row;
    }
    TextBox Field(StackPanel parent, string key, string value = "", bool multiline = false)
    {
        parent.Children.Add(Label(T(key), 13, "Subtext"));
        var field = new TextBox { Text = value, AcceptsReturn = multiline, TextWrapping = multiline ? TextWrapping.Wrap : TextWrapping.NoWrap, MinHeight = multiline ? 110 : 42, MaxHeight = multiline ? 240 : 42, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden, FlowDirection = key is "name" or "subscription_name" ? FlowDirection : FlowDirection.LeftToRight, Margin = new Thickness(0, 0, 0, 16) };
        parent.Children.Add(field);
        return field;
    }
    bool Confirm(string key)
    {
        var dialog = Dialog(T(key));
        var p = (StackPanel)dialog.Content;
        var row = Actions(Action(key == "confirm_delete" || key == "remove_sub" ? "delete" : key == "confirm_clear" ? "clear_logs" : key == "confirm_reset" ? "reset" : "save", () => { dialog.DialogResult = true; return Task.CompletedTask; }, "check", true), Action("cancel", () => { dialog.DialogResult = false; return Task.CompletedTask; }));
        p.Children.Add(row);
        return dialog.ShowDialog() == true;
    }
    Window Dialog(string message)
    {
        var dialog = new Window { Owner = this, Title = "RADVPN", Width = 560, SizeToContent = SizeToContent.Height, MaxHeight = 600, ResizeMode = ResizeMode.NoResize, WindowStartupLocation = WindowStartupLocation.CenterOwner, Background = B("App"), Foreground = B("Text"), FlowDirection = FlowDirection, FontFamily = FontFamily };
        dialog.Resources.MergedDictionaries.Add(Resources.MergedDictionaries[0]);
        var p = new StackPanel { Margin = new Thickness(24), Background = B("App") };
        p.Children.Add(Label(message, 16));
        dialog.Content = p;
        return dialog;
    }
    string? Prompt(string key, string value, bool multiline = false)
    {
        var dialog = Dialog(T(key));
        var panel = (StackPanel)dialog.Content;
        var input = Field(panel, key, value, multiline);
        panel.Children.Add(Actions(Action("save", () => { dialog.DialogResult = true; return Task.CompletedTask; }, "check", true), Action("cancel", () => { dialog.DialogResult = false; return Task.CompletedTask; })));
        return dialog.ShowDialog() == true ? input.Text : null;
    }
}
