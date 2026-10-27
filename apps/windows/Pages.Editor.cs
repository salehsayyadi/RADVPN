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
        dialog.SizeToContent = SizeToContent.Manual;
        dialog.Height = 700;
        dialog.MaxHeight = Math.Max(600, SystemParameters.WorkArea.Height - 70);
        var root = (StackPanel)dialog.Content;
        var form = new StackPanel();
        root.Children.Add(new ScrollViewer { Content = form, MaxHeight = 440 });
        form.Children.Add(Label(protocol.ToUpperInvariant(), 14, "Orange", true));
        var setters = new List<Action>();
        void Bind(JsonNode node, string property, string label, bool number = false)
        {
            var field = Field(form, label, node[property]?.ToString() ?? "");
            setters.Add(() => { if (number) { if (!int.TryParse(field.Text, out var value) || value < 1 || value > 65535) throw new InvalidOperationException("invalid_profile"); node[property] = value; } else node[property] = field.Text.Trim(); });
        }
        Bind(candidate, "name", "name");
        Bind(endpoint, "address", "address");
        Bind(endpoint, "port", "server_port", true);
        if (protocol is "vless" or "vmess")
        {
            var user = endpoint["users"]![0]!;
            Bind(user, "id", "client_id");
            if (protocol == "vless")
                Bind(user, "flow", "flow");
        }
        else
        {
            Bind(endpoint, "password", "password");
            if (protocol == "shadowsocks")
                Bind(endpoint, "method", "method");
        }
        if (outbound["streamSettings"] is JsonObject stream)
        {
            var security = stream["security"]?.ToString();
            form.Children.Add(Label((stream["network"]?.ToString() ?? "tcp").ToUpperInvariant() + " / " + (security ?? "none").ToUpperInvariant(), 13, "Subtext"));
            var tls = stream[security == "reality" ? "realitySettings" : "tlsSettings"];
            if (tls != null)
            {
                Bind(tls, "serverName", "sni");
                Bind(tls, "fingerprint", "fingerprint");
                if (security == "reality")
                {
                    Bind(tls, "publicKey", "public_key");
                    Bind(tls, "shortId", "short_id");
                    Bind(tls, "spiderX", "spider_path");
                }
                else
                {
                    var alpn = Field(form, "alpn", tls["alpn"] is JsonArray a ? string.Join(",", a.Select(x => x?.ToString())) : "");
                    setters.Add(() => tls["alpn"] = new JsonArray(alpn.Text.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).Select(x => JsonValue.Create(x) as JsonNode).ToArray()));
                }
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
        var error = Label("", 13, "Error");
        root.Children.Add(error);
        root.Children.Add(Actions(Action("save", async () =>
        {
            try
            {
                foreach (var setter in setters)
                    setter();
                if (string.IsNullOrWhiteSpace(candidate["name"]?.ToString()) || candidate["name"]!.ToString().Length > 200)
                    throw new InvalidOperationException("invalid_profile");
                var validation = Settings.DeepClone();
                validation["mode"] = "proxy";
                await core!.Call(new
                {
                    method = "validate",
                    profile = candidate,
                    settings = validation
                });
                original["name"] = candidate["name"]!.DeepClone();
                original["outbound"] = outbound.DeepClone();
                original["userNamed"] = true;
                original.AsObject().Remove("probeLatency");
                original.AsObject().Remove("reachable");
                Save();
                dialog.DialogResult = true;
                Render();
            }
            catch (Exception e) { error.Text = T(Code(e)); }
        }, "check", true)));
        dialog.ShowDialog();
        return Task.CompletedTask;
    }
}
