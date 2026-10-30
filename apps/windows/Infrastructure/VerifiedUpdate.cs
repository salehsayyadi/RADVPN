using System;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
namespace RADVPN;
internal static class VerifiedUpdate
{
    internal sealed record Candidate(Version Version, bool Testing, string Url, string Digest, string Filename);
    internal static async Task<Candidate?> Find()
    {
        using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(20) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("RADVPN/3.0.3");
        var releases = JsonNode.Parse(await client.GetStringAsync("https://api.github.com/repos/salehsayyadi/RADVPN/releases?per_page=20"))!.AsArray();
        foreach (var r in releases.OrderByDescending(r => Version.TryParse(r?["tag_name"]?.ToString().TrimStart('v'), out var v) ? v : new Version(0,0)))
        {
            if (r == null || r["draft"]?.GetValue<bool>() == true || !Version.TryParse(r["tag_name"]?.ToString().TrimStart('v'), out var version) || version <= new Version(3,0,3)) continue;
            var installer = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\RADVPN");
            using (installer)
            {
                var installed = installer != null;
                var asset = r["assets"]!.AsArray().FirstOrDefault(a => a?["name"]?.ToString() == (installed ? "RADVPN-Windows-Setup.exe" : "RADVPN-Windows-Portable.zip"))
                    ?? r["assets"]!.AsArray().FirstOrDefault(a => a?["name"]?.ToString() == (installed ? $"RADVPN-Setup-{version}-x64.exe" : $"RADVPN-Windows-Portable-{version}-x64.zip"));
                var digest = asset?["digest"]?.ToString();
                if (asset == null || digest == null || !System.Text.RegularExpressions.Regex.IsMatch(digest, "^sha256:[a-f0-9]{64}$")) throw new InvalidOperationException("update_integrity_unavailable");
                var url = asset["browser_download_url"]!.ToString();
                if (!url.StartsWith("https://github.com/salehsayyadi/RADVPN/releases/download/", StringComparison.Ordinal)) throw new InvalidOperationException("update_integrity_unavailable");
                return new Candidate(version, r["prerelease"]?.GetValue<bool>() == true, url, digest.Substring(7), installed ? "RADVPN-Update.exe" : "RADVPN-Update.zip");
            }
        }
        return null;
    }
    internal static async Task<string> Download(Candidate candidate)
    {
        var dir = Path.Combine(Path.GetTempPath(), "RADVPN-Update-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(dir);
        var path = Path.Combine(dir, candidate.Filename);
        try
        {
            using var client = new HttpClient { Timeout = TimeSpan.FromMinutes(10) };
            using var response = await client.GetAsync(candidate.Url, HttpCompletionOption.ResponseHeadersRead); response.EnsureSuccessStatusCode();
            using var input = await response.Content.ReadAsStreamAsync();
            using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
            using (var file = new FileStream(path + ".partial", FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                var buffer = new byte[65536]; int n; long length = 0;
                while ((n = await input.ReadAsync(buffer)) > 0) { length += n; if (length > 536870912) throw new InvalidOperationException("update_integrity_failed"); hash.AppendData(buffer,0,n); await file.WriteAsync(buffer.AsMemory(0,n)); }
                if (length == 0 || (response.Content.Headers.ContentLength is long expected && length != expected)) throw new InvalidOperationException("update_integrity_failed");
            }
            if (!CryptographicOperations.FixedTimeEquals(hash.GetHashAndReset(), Convert.FromHexString(candidate.Digest))) throw new InvalidOperationException("update_integrity_failed");
            File.Move(path + ".partial",path); return path;
        }
        catch { Directory.Delete(dir,true); throw; }
    }
}
