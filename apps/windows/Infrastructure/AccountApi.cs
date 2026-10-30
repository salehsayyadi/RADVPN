using System;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
namespace RADVPN;
internal static class AccountApi
{
    internal static Uri Base(string value)
    {
        if (!Uri.TryCreate(value.TrimEnd('/') + "/", UriKind.Absolute, out var uri) || uri.Scheme != "https" || !string.IsNullOrEmpty(uri.UserInfo) || !string.IsNullOrEmpty(uri.Query) || !string.IsNullOrEmpty(uri.Fragment))
            throw new InvalidOperationException("account_https_required");
        return uri;
    }
    internal static async Task<JsonObject> Call(string endpoint, string path, string? token = null, JsonObject? payload = null)
    {
        using var handler = new HttpClientHandler { AllowAutoRedirect = false };
        using var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(20) };
        using var request = new HttpRequestMessage(payload == null ? HttpMethod.Get : HttpMethod.Post, new Uri(Base(endpoint), path));
        if (token != null) request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        if (payload != null) request.Content = new StringContent(payload.ToJsonString(), Encoding.UTF8, "application/json");
        try
        {
            using var response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead);
            if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
                throw new InvalidOperationException("account_revoked");
            using var input = await response.Content.ReadAsStreamAsync();
            using var output = new MemoryStream();
            var buffer = new byte[4096]; int n;
            while ((n = await input.ReadAsync(buffer)) > 0) { if (output.Length + n > 262144) throw new InvalidOperationException("account_unavailable"); output.Write(buffer, 0, n); }
            var data = JsonNode.Parse(output.ToArray()) as JsonObject ?? throw new InvalidOperationException("account_unavailable");
            if (!response.IsSuccessStatusCode || data["error"] != null)
            {
                var error = data["error"]?.ToString();
                throw new InvalidOperationException(error == "capacity_reached" ? "account_capacity_reached" : error == "subscription_pending" ? "account_subscription_pending" : "account_unavailable");
            }
            return data;
        }
        catch (HttpRequestException) { throw new InvalidOperationException("account_unavailable"); }
        catch (TaskCanceledException) { throw new InvalidOperationException("account_unavailable"); }
    }
}
