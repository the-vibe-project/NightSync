using System.Text.Json;

namespace NightSync.ServerUI;

public static class ConnectionQrPayload
{
    public static string Create(string address)
    {
        var normalized = (address ?? string.Empty).Trim().TrimEnd('/');
        if (!Uri.TryCreate(normalized, UriKind.Absolute, out var uri) ||
            uri.Scheme != Uri.UriSchemeHttps ||
            string.IsNullOrWhiteSpace(uri.Host) ||
            !string.IsNullOrEmpty(uri.UserInfo) ||
            !string.IsNullOrEmpty(uri.Query) ||
            !string.IsNullOrEmpty(uri.Fragment))
        {
            throw new ArgumentException(
                "Enter a valid HTTPS base address without credentials, query, or fragment.",
                nameof(address));
        }

        return JsonSerializer.Serialize(new
        {
            type = "nightsync-connect",
            version = 1,
            url = normalized
        });
    }
}
