namespace NightSync.Desktop;

public static class ServerAddressPolicy
{
    public static bool IsSameAuthenticatedOrigin(string candidateAddress, string authenticatedAddress)
    {
        if (!TryParse(candidateAddress, out var candidate) ||
            !TryParse(authenticatedAddress, out var authenticated))
        {
            return false;
        }

        if (!UsesAllowedTransport(candidate) || !UsesAllowedTransport(authenticated))
            return false;

        return string.Equals(
            candidate.GetLeftPart(UriPartial.Authority),
            authenticated.GetLeftPart(UriPartial.Authority),
            StringComparison.OrdinalIgnoreCase);
    }

    public static bool IsLocalServerAddress(string address) =>
        TryParse(address, out var uri) &&
        uri.Scheme == Uri.UriSchemeHttp &&
        uri.IsLoopback &&
        uri.Port == 8787;

    private static bool TryParse(string value, out Uri uri)
    {
        uri = null!;
        if (!Uri.TryCreate(value?.Trim().TrimEnd('/'), UriKind.Absolute, out var parsed))
            return false;

        if (string.IsNullOrWhiteSpace(parsed.Host) ||
            !string.IsNullOrEmpty(parsed.UserInfo) ||
            !string.IsNullOrEmpty(parsed.Query) ||
            !string.IsNullOrEmpty(parsed.Fragment))
        {
            return false;
        }

        uri = parsed;
        return true;
    }

    private static bool UsesAllowedTransport(Uri uri) =>
        uri.Scheme == Uri.UriSchemeHttps ||
        (uri.Scheme == Uri.UriSchemeHttp && uri.IsLoopback);
}
