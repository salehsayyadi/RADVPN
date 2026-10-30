using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace RADVPN;
internal static class NativeSmoke
{
    public static Task Capture(MainWindow window, string[] args) => window.CaptureNative(args);
    public static async Task<int> Checks(string output)
    {
        var checks = new List<string>();
        try
        {
            using var core = new Core();
            var version = await core.Call(new
            {
                method = "version"
            });
            if (version["app"]?.ToString() != "3.0.3")
                throw new Exception("version");
            checks.Add("native child IPC/version");
            var defaults = await core.Call(new
            {
                method = "defaults"
            });
            if (defaults["mode"]?.ToString() != "tunnel" || defaults["port"]?.GetValue<int>() != 10808)
                throw new Exception("defaults");
            checks.Add("Tunnel/10808 defaults");
            try
            {
                await core.Call(new
                {
                    method = "import",
                    text = "not-a-valid-profile"
                });
                throw new Exception("invalid import accepted");
            }
            catch (InvalidOperationException) { checks.Add("invalid import rejected"); }
            var valid = await core.Call(new
            {
                method = "import",
                text = "http://test-only@127.0.0.1:3128#unit-test"
            });
            if (((JsonArray)valid["profiles"]!).Count != 1)
                throw new Exception("valid import");
            checks.Add("valid profile import (no remote connection claim)");
            var metadataProfile = valid["profiles"]![0]!.DeepClone();
            metadataProfile["probeLatency"] = 42L;
            metadataProfile["probeAt"] = "test";
            metadataProfile["reachable"] = true;
            metadataProfile["userNamed"] = true;
            var proxySettings = defaults.DeepClone();
            proxySettings["mode"] = "proxy";
            await core.Call(new
            {
                method = "validate",
                profile = metadataProfile,
                settings = proxySettings
            });
            checks.Add("UI metadata excluded from strict native profile request");
            var unicode = await core.Call(new
            {
                method = "import",
                text = "socks://127.0.0.1:19876#" + Uri.EscapeDataString("هلند 🇳🇱")
            });
            if (unicode["profiles"]![0]!["name"]!.ToString() != "هلند 🇳🇱")
                throw new Exception("UTF8 IPC");
            if (CountryFlags.Code("Netherlands 🇳🇱") != "NL")
                throw new Exception("flag");
            checks.Add("UTF8 names and offline country flag lookup");
            System.Text.Encoding.RegisterProvider(System.Text.CodePagesEncodingProvider.Instance);
            var proper = "NETHERLANDS-5 🇳🇱";
            var broken = System.Text.Encoding.GetEncoding(1252).GetString(System.Text.Encoding.UTF8.GetBytes(proper));
            if (DisplayNames.Repair(broken) != proper || CountryFlags.WithoutEmoji(broken) != "NETHERLANDS-5")
                throw new Exception("persisted mojibake repair");
            if (DisplayNames.Repair("ایران") != "ایران" || DisplayNames.Repair("François") != "François")
                throw new Exception("valid name changed");
            checks.Add("legacy persisted name repair and preservation of valid Unicode");
            var dir = Path.Combine(Path.GetTempPath(), "RADVPN-native-tests-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(dir);
            var path = Path.Combine(dir, "vault.bin");
            try
            {
                var state = Storage.Fresh();
                state["selected"] = "fixture-not-user-credential-392135";
                state["profiles"]!.AsArray().Add(new JsonObject { ["id"] = "name-migration-fixture", ["name"] = broken });
                Storage.Save(state, path);
                if (Storage.Load(path)["selected"]?.ToString() != state["selected"]?.ToString())
                    throw new Exception("DPAPI restart roundtrip");
                if (Storage.Load(path)["profiles"]![0]!["name"]!.ToString() != proper)
                    throw new Exception("legacy vault name migration");
                state["selected"] = "second";
                Storage.Save(state, path);
                File.WriteAllBytes(path, new byte[] { 1, 2, 3 });
                if (Storage.Load(path)["selected"]?.ToString() != "fixture-not-user-credential-392135" || !Storage.Recovered)
                    throw new Exception("backup recovery");
                checks.Add("DPAPI persistence, atomic backup and corrupted-primary recovery");
            }
            finally { Directory.Delete(dir, true); }
            var ledger = new TrafficLedger();
            var data = Storage.Fresh();
            ledger.Sample(data, 100, 200);
            ledger.Sample(data, 150, 260);
            var totals = TrafficLedger.Totals(data, 0);
            if (totals.Up != 150 || totals.Down != 260)
                throw new Exception("history delta");
            ledger.Sample(data, 2, 3);
            totals = TrafficLedger.Totals(data, 0);
            if (totals.Up != 152 || totals.Down != 263)
                throw new Exception("history reset");
            checks.Add("traffic delta and counter-reset arithmetic (unit test)");
            var listener = new TcpListener(IPAddress.Loopback, 0);
            listener.Start();
            try
            {
                var p = new JsonObject { ["id"] = "tcp-test", ["outbound"] = new JsonObject { ["settings"] = new JsonObject { ["servers"] = new JsonArray(new JsonObject { ["address"] = "127.0.0.1", ["port"] = ((IPEndPoint)listener.LocalEndpoint).Port }) } } };
                var result = await EndpointProbe.One(p, CancellationToken.None);
                if (!result.Reachable || result.Milliseconds == null)
                    throw new Exception("TCP probe");
                checks.Add("real loopback TCP endpoint measurement");
            }
            finally { listener.Stop(); }
            await core.Call(new
            {
                method = "stop"
            });
            checks.Add("clean core stop");
            File.WriteAllText(output, JsonSerializer.Serialize(new
            {
                passed = true,
                checks
            }, new JsonSerializerOptions { WriteIndented = true }));
            return 0;
        }
        catch (Exception e) { File.WriteAllText(output, JsonSerializer.Serialize(new { passed = false, checks, error = e.GetType().Name }, new JsonSerializerOptions { WriteIndented = true })); return 1; }
    }
    public static void Bitmap(FrameworkElement element, string path)
    {
        element.UpdateLayout();
        var width = (int)Math.Ceiling(element.ActualWidth);
        var height = (int)Math.Ceiling(element.ActualHeight);
        if (width < 100 || height < 100)
            throw new InvalidOperationException("render_size");
        var bitmap = new RenderTargetBitmap(width, height, 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(element);
        // Rendering a subtree omits the inherited RTL root transform applied by its Window.
        bool inheritedMirror = element.FlowDirection == FlowDirection.RightToLeft && PresentationSource.FromVisual(element)?.RootVisual != element;
        BitmapSource frame = inheritedMirror ? new TransformedBitmap(bitmap, new ScaleTransform(-1, 1)) : bitmap;
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(frame));
        using var file = File.Create(path);
        encoder.Save(file);
    }
}
internal sealed partial class MainWindow
{
    internal async Task CaptureNative(string[] options)
    {
        string Read(string key, string fallback)
        {
            var index = Array.IndexOf(options, key);
            return index >= 0 && index + 1 < options.Length ? options[index + 1] : fallback;
        }
        var directory = Read("--capture-dir", Path.Combine(Path.GetTempPath(), "RADVPN-captures"));
        Directory.CreateDirectory(directory);
        var results = new List<object>();
        var failures = new List<string>();
        var originalState = (JsonObject)data.DeepClone();
        try
        {
            foreach (var language in new[] { "en", "fa" })
            {
                foreach (var size in new[] { (Width: 1440, Height: 900), (Width: 1280, Height: 720) })
                {
                    foreach (var target in new[] { "home", "account", "servers", "subscriptions", "configs", "profiles", "traffic", "settings", "settings-connection", "settings-security", "settings-advanced", "advanced", "logs", "about", "home-proxy", "home-populated", "servers-populated", "subscriptions-populated" })
                    {
                        Width = size.Width;
                        Height = size.Height;
                        data["lang"] = language;
                        LoadWords();
                        var priorMode = Settings["mode"]!.DeepClone();
                        Profiles.Clear();
                        Subs.Clear();
                        data["selected"] = "";
                        if (target.EndsWith("-populated", StringComparison.Ordinal))
                        {
                            Subs.Add(new JsonObject { ["id"] = "qa-personal", ["name"] = "Personal", ["url"] = "https://personal.example.invalid/subscription" });
                            Subs.Add(new JsonObject { ["id"] = "qa-backup", ["name"] = "Backup", ["url"] = "https://backup.example.invalid/subscription" });
                            var fixtures = new[] { "Netherlands 🇳🇱", "Germany 🇩🇪", "France 🇫🇷", "Japan 🇯🇵", "United Kingdom 🇬🇧", "United States 🇺🇸" };
                            for (int i = 0; i < 72; i++)
                            {
                                var imported = await core!.Call(new
                                {
                                    method = "import",
                                    text = ((i % 4) switch
                                    {
                                        0 => "vless://11111111-1111-4111-8111-111111111111@example.invalid:" + (19000 + i) + "?security=reality&sni=example.invalid&pbk=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY&sid=1122334455667788&type=tcp&flow=xtls-rprx-vision",
                                        1 => "trojan://qa-fixture-only@example.invalid:" + (19000 + i) + "?security=tls&sni=example.invalid",
                                        2 => "vmess://11111111-1111-4111-8111-111111111111@example.invalid:" + (19000 + i) + "?security=tls&type=ws&sni=example.invalid&path=%2Ffixture",
                                        _ => "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpxYS1maXh0dXJlLW9ubHk@example.invalid:" + (19000 + i)
                                    }) + "#" + Uri.EscapeDataString(fixtures[i % fixtures.Length] + " · " + (i + 1))
                                });
                                MergeItems((JsonArray)imported["profiles"]!, i % 2 == 0 ? "qa-personal" : "qa-backup");
                                if (i < 6)
                                {
                                    var profile = Profiles.Last()!;
                                    System.Text.Encoding.RegisterProvider(System.Text.CodePagesEncodingProvider.Instance);
                                    profile["name"] = System.Text.Encoding.GetEncoding(1252).GetString(System.Text.Encoding.UTF8.GetBytes(fixtures[i] + " · " + (i + 1)));
                                }

                            }
                            data["selected"] = Profiles[0]!["id"]!.ToString();
                        }
                        if (target.StartsWith("settings-", StringComparison.Ordinal))
                        {
                            settingsTab = target[9..];
                            Navigate("settings");
                        }
                        else if (target.EndsWith("-populated", StringComparison.Ordinal))
                            Navigate(target[..^10]);
                        else if (target == "home-proxy")
                        {
                            Settings["mode"] = "proxy";
                            Navigate("home");
                        }
                        else
                        {
                            if (target == "settings")
                                settingsTab = "general";
                            Navigate(target);
                        }
                        await Dispatcher.InvokeAsync(() => { }, DispatcherPriority.ApplicationIdle);
                        await Task.Delay(160);
                        if (target == "servers-populated" && size.Width == 1440)
                        {
                            var editTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
                            editTimer.Tick += (_, _) => { var editor = OwnedWindows.Cast<Window>().FirstOrDefault(); if (editor == null) return; editTimer.Stop(); NativeSmoke.Bitmap(editor, Path.Combine(directory, "editor-" + language + ".png")); editor.Close(); };
                            editTimer.Start();
                            await EditServer(Profiles[0]!);
                            editTimer.Stop();
                        }
                        var root = (FrameworkElement)Content;
                        // A hosted service-session display constrains native window size. Render the
                        // real compiled page offscreen at the requested CLIENT viewport instead;
                        // retain both actual native-window dimensions and layout dimensions in evidence.
                        root.FlowDirection = FlowDirection;
                        root.Resources.MergedDictionaries.Add(Resources.MergedDictionaries[0]);
                        Content = null;
                        UpdateLayout();
                        await Dispatcher.InvokeAsync(() => { }, DispatcherPriority.ApplicationIdle);
                        // Reconnect the removed subtree to a dedicated native presentation source.
                        // WPF suspends layout for removed visuals until a PresentationSource resumes it.
                        using var surface = new System.Windows.Interop.HwndSource(new System.Windows.Interop.HwndSourceParameters("RADVPN offscreen QA")
                        {
                            Width = size.Width,
                            Height = size.Height,
                            PositionX = -10000,
                            PositionY = -10000,
                            WindowStyle = unchecked((int)0x90000000)
                        });
                        surface.RootVisual = root;
                        await Dispatcher.InvokeAsync(() => { }, DispatcherPriority.ApplicationIdle);
                        root.Width = size.Width;
                        root.Height = size.Height;
                        root.InvalidateMeasure();
                        root.Measure(new Size(size.Width, size.Height));
                        root.Arrange(new Rect(0, 0, size.Width, size.Height));
                        root.UpdateLayout();
                        var filename = $"{target}-{language}-{size.Width}x{size.Height}.png";
                        NativeSmoke.Bitmap(root, Path.Combine(directory, filename));
                        var overflow = Audit(root);
                        if (Math.Abs(root.ActualWidth - size.Width) > 1 || Math.Abs(root.ActualHeight - size.Height) > 1)
                            overflow.Add("Offscreen native viewport size mismatch");
                        results.Add(new
                        {
                            page = target,
                            language,
                            windowWidth = ActualWidth,
                            windowHeight = ActualHeight,
                            clientWidth = root.ActualWidth,
                            clientHeight = root.ActualHeight,
                            filename,
                            overflow
                        });
                        failures.AddRange(overflow.Select(x => filename + ": " + x));
                        surface.RootVisual = null;
                        Content = root;
                        Settings["mode"] = priorMode;
                    }
                }
            }
            foreach (var language in new[] { "en", "fa" })
            {
                data["lang"] = language;
                LoadWords();
                var dialog = Dialog(T("invalid_profile"));
                var panel = (StackPanel)dialog.Content;
                Field(panel, "import", "", true);
                panel.Children.Add(Actions(Action("save", () => Task.CompletedTask, "check", true), Action("cancel", () => Task.CompletedTask)));
                dialog.Show();
                await Task.Delay(200);
                // Capture the complete native Window so the content margin does not crop buttons.
                NativeSmoke.Bitmap(dialog, Path.Combine(directory, "dialog-" + language + ".png"));
                dialog.Close();
            }
            File.WriteAllText(Path.Combine(directory, "ui-report.json"), JsonSerializer.Serialize(new
            {
                passed = failures.Count == 0,
                captures = results,
                failures,
                scope = "Actual compiled WPF pages rendered offscreen at requested CLIENT viewport sizes with RTL root transform restored. Native host window dimensions are recorded separately. Physical window-resize/desktop-display QA and VPN leak tests are NOT RUN."
            }, new JsonSerializerOptions { WriteIndented = true }));
            Environment.ExitCode = failures.Count == 0 ? 0 : 2;
        }
        catch (Exception e) { File.WriteAllText(Path.Combine(directory, "ui-error.txt"), e.GetType().Name + ": " + e.Message); Environment.ExitCode = 1; }
        finally { data = originalState; LoadWords(); exitRequested = true; Close(); }
    }
    static List<string> Audit(FrameworkElement root)
    {
        var errors = new List<string>();
        void Walk(System.Windows.DependencyObject parent)
        {
            for (var i = 0; i < VisualTreeHelper.GetChildrenCount(parent); i++)
            {
                var child = VisualTreeHelper.GetChild(parent, i);
                if (child is FrameworkElement element && element.IsVisible && element.ActualWidth > 0 && element is Button or TextBox or PasswordBox)
                {
                    try
                    {
                        var bounds = element.TransformToAncestor(root).TransformBounds(new Rect(0, 0, element.ActualWidth, element.ActualHeight));
                        if (bounds.Left < -3 || bounds.Right > root.ActualWidth + 3)
                            errors.Add(element.GetType().Name + " horizontal overflow");
                    }
                    catch { }
                }
                Walk(child);
            }
        }
        Walk(root);
        return errors;
    }
}
