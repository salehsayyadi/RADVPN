using System;
using System.Diagnostics;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
namespace RADVPN;
internal sealed class Core : IDisposable
{
    readonly Process process;
    readonly SemaphoreSlim gate = new(1, 1);
    public Core() { process = Process.Start(new ProcessStartInfo(Path.Combine(AppContext.BaseDirectory, "radcore.exe")) { UseShellExecute = false, RedirectStandardInput = true, RedirectStandardOutput = true, RedirectStandardError = true, CreateNoWindow = true, WorkingDirectory = AppContext.BaseDirectory }) ?? throw new InvalidOperationException("core_start_failed"); process.ErrorDataReceived += (_, _) => { }; process.BeginErrorReadLine(); }
    public async Task<JsonNode> Call(object request)
    {
        await gate.WaitAsync();
        try
        {
            if (process.HasExited) throw new InvalidOperationException("core_start_failed");
            await process.StandardInput.WriteLineAsync(JsonSerializer.Serialize(request)); await process.StandardInput.FlushAsync();
            var line = await process.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(35));
            if (line == null || line.Length > 8 * 1024 * 1024) throw new InvalidOperationException("core_start_failed");
            var response = JsonNode.Parse(line)!;
            if (response["ok"]?.GetValue<bool>() != true) throw new InvalidOperationException(response["error"]?.ToString() ?? "operation_failed");
            return response["data"]?.DeepClone() ?? new JsonObject();
        }
        finally { gate.Release(); }
    }
    public void Dispose() { try { process.StandardInput.Close(); if (!process.WaitForExit(8000)) process.Kill(true); } catch { } process.Dispose(); }
}
internal static class Storage
{
    public static readonly string FilePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "RADVPN", "vault.bin");
    public static bool Recovered { get; private set; }
    public static byte[] Encrypt(JsonObject data) => ProtectedData.Protect(Encoding.UTF8.GetBytes(data.ToJsonString()), null, DataProtectionScope.CurrentUser);
    public static JsonObject Decrypt(byte[] data) { if (data.Length > 8 * 1024 * 1024) throw new InvalidOperationException("import_too_large"); var value = (JsonObject)JsonNode.Parse(Encoding.UTF8.GetString(ProtectedData.Unprotect(data, null, DataProtectionScope.CurrentUser)))!; Validate(value); return value; }
    public static void Validate(JsonObject value) { if (value["profiles"] is not JsonArray || value["subscriptions"] is not JsonArray) throw new InvalidOperationException("storage_failed"); if (value["lang"]?.ToString() is not ("en" or "fa")) throw new InvalidOperationException("storage_failed"); }
    public static JsonObject Fresh() => new() { ["profiles"] = new JsonArray(), ["subscriptions"] = new JsonArray(), ["lang"] = "en", ["selected"] = "", ["tray"] = true, ["trafficDays"] = new JsonObject() };
    public static JsonObject Load(string? target = null) { var path = target ?? FilePath; Recovered = false; if (!File.Exists(path)) return Fresh(); try { return Decrypt(File.ReadAllBytes(path)); } catch { if (!File.Exists(path + ".bak")) throw; Recovered = true; return Decrypt(File.ReadAllBytes(path + ".bak")); } }
    public static void Save(JsonObject value, string? target = null) { Validate(value); var path = target ?? FilePath; Directory.CreateDirectory(Path.GetDirectoryName(path)!); var tmp = path + ".tmp"; var bytes = Encrypt(value); using (var stream = new FileStream(tmp, FileMode.Create, FileAccess.Write, FileShare.None, 4096, FileOptions.WriteThrough)) { stream.Write(bytes); stream.Flush(true); } if (File.Exists(path)) File.Replace(tmp, path, path + ".bak", true); else File.Move(tmp, path); }
}
