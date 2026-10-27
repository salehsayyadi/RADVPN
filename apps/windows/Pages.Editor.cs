using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
namespace RADVPN;
internal sealed partial class MainWindow
{
    Task EditServer(JsonNode original)
    {
        if (ConnectionActive)
            throw new InvalidOperationException("already_running");
        var candidate = original.DeepClone();
        var outbound = candidate["outbound"]!;
        var protocol = outbound["protocol"]!.ToString();
        var endpoint = ((JsonArray)(outbound["settings"]!["vnext"] ?? outbound["settings"]!["servers"])!)[0]!;
        var dialog = Dialog(T("edit_server"));
        dialog.Width = 800;
        dialog.SizeToContent = SizeToContent.Manual;
        dialog.Height = 750;
        dialog.MaxHeight = Math.Max(600, SystemParameters.WorkArea.Height - 40);
        var root = (StackPanel)dialog.Content;
        root.Children.Add(Label(protocol.ToUpperInvariant(), 12, "Orange", true));
        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition());
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(20) });
        grid.ColumnDefinitions.Add(new ColumnDefinition());
        root.Children.Add(new ScrollViewer { Content = grid, MaxHeight = Math.Min(520, dialog.MaxHeight - 220) });
        var error = Label("", 12, "Error");
        root.Children.Add(error);
        var setters = new List<Action>();
        int index = 0;
        void Cell(FrameworkElement element)
        {
            int row = index / 2;
            if (grid.RowDefinitions.Count <= row)
                grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            Grid.SetRow(element, row);
            Grid.SetColumn(element, index % 2 * 2);
            grid.Children.Add(element);
            index++;
        }
        void Bind(JsonNode node, string property, string label, bool number = false)
        {
            var cell = new StackPanel();
            var field = Field(cell, label, node[property]?.ToString() ?? "");
            Cell(cell);
            setters.Add(() => { if (number) { if (!int.TryParse(field.Text, out var n) || n < 1 || n > 65535) throw new InvalidOperationException("invalid_profile"); node[property] = n; } else node[property] = field.Text.Trim(); });
        }
        void Section(string value)
        {
            if (index % 2 != 0)
                index++;
            var label = Label(value, 12, "Subtext", true);
            label.Margin = new Thickness(0, 12, 0, 16);
            Cell(label);
            Grid.SetColumnSpan(label, 3);
            index++;
        }
        void Commit()
        {
            foreach (var setter in setters)
                setter();
        }
        void Pick(JsonObject stream, string property, string label, string[] options, Action<string> update)
        {
            var cell = new StackPanel();
            cell.Children.Add(Label(T(label), 13, "Subtext"));
            var input = new ComboBox { ItemsSource = options, SelectedItem = stream[property]?.ToString() ?? options[0], Margin = new Thickness(0, 0, 0, 16) };
            Cell(cell);
            input.SelectionChanged += (_, _) => { if (input.SelectedItem is not string choice) return; try { Commit(); update(choice); Draw(); } catch (Exception e) { error.Text = T(Code(e)); Draw(); } };
        }
        void Draw()
        {
            grid.Children.Clear();
            grid.RowDefinitions.Clear();
            setters.Clear();
            index = 0;
            Bind(candidate, "name", "name");
            Bind(endpoint, "address", "address");
            Bind(endpoint, "port", "server_port", true);
            var user = endpoint["users"] is JsonArray users && users.Count > 0 ? users[0] : null;
            if (protocol is "vless" or "vmess")
                Bind(user!, "id", "client_id");
            else
            {
                Bind(endpoint, "password", "password");
                if (protocol == "shadowsocks")
                    Bind(endpoint, "method", "method");
            }
            if (outbound["streamSettings"] is not JsonObject stream)
                return;
            Pick(stream, "network", "transport", new[] { "tcp", "ws", "grpc", "xhttp" }, choice => { stream["network"] = choice; foreach (var key in new[] { "tcpSettings", "wsSettings", "grpcSettings", "xhttpSettings" }) stream.Remove(key); if (choice == "ws") stream["wsSettings"] = new JsonObject { ["path"] = "/", ["headers"] = new JsonObject { ["Host"] = endpoint["address"]?.ToString() } }; if (choice == "grpc") stream["grpcSettings"] = new JsonObject { ["serviceName"] = "" }; if (choice == "xhttp") stream["xhttpSettings"] = new JsonObject { ["path"] = "/", ["host"] = endpoint["address"]?.ToString(), ["mode"] = "auto" }; if (choice != "tcp" && protocol == "vless") user?.AsObject().Remove("flow"); });
            Pick(stream, "security", "security", protocol == "vless" ? new[] { "none", "tls", "reality" } : new[] { "none", "tls" }, choice => { stream["security"] = choice; stream.Remove("tlsSettings"); stream.Remove("realitySettings"); if (choice != "none") stream[choice == "reality" ? "realitySettings" : "tlsSettings"] = new JsonObject { ["serverName"] = endpoint["address"]?.ToString(), ["fingerprint"] = "chrome" }; else if (protocol == "vless") user?.AsObject().Remove("flow"); });
            var security = stream["security"]?.ToString();
            if (stream[security == "reality" ? "realitySettings" : "tlsSettings"] is JsonObject tls)
            {
                Section(security == "reality" ? "REALITY & TLS" : "TLS");
                Bind(tls, "serverName", "sni");
                Bind(tls, "fingerprint", "fingerprint");
                if (security == "reality")
                {
                    Bind(tls, "publicKey", "public_key");
                    Bind(tls, "shortId", "short_id");
                }
                if (protocol == "vless" && stream["network"]?.ToString() == "tcp")
                    Bind(user!, "flow", "flow");
                if (security == "reality")
                    Bind(tls, "spiderX", "spider_path");
            }
            if (stream["wsSettings"] is JsonObject ws)
            {
                Bind(ws, "path", "path");
                if (ws["headers"] is JsonObject headers)
                    Bind(headers, "Host", "host");
            }
            if (stream["grpcSettings"] is JsonObject grpc)
                Bind(grpc, "serviceName", "service_name");
            if (stream["xhttpSettings"] is JsonObject http)
            {
                Bind(http, "path", "path");
                Bind(http, "host", "host");
            }
        }
        Draw();
        root.Children.Add(Actions(Action("save", async () => { try { Commit(); if (string.IsNullOrWhiteSpace(candidate["name"]?.ToString()) || candidate["name"]!.ToString().Length > 200) throw new InvalidOperationException("invalid_profile"); var validation = Settings.DeepClone(); validation["mode"] = "proxy"; await core!.Call(new { method = "validate", profile = candidate, settings = validation }); original["name"] = candidate["name"]!.DeepClone(); original["outbound"] = outbound.DeepClone(); original["userNamed"] = true; foreach (var key in new[] { "sourceLink", "probeLatency", "reachable" }) original.AsObject().Remove(key); Save(); dialog.DialogResult = true; Render(); } catch (Exception e) { error.Text = T(Code(e)); } }, "check", true)));
        dialog.ShowDialog();
        return Task.CompletedTask;
    }
}
