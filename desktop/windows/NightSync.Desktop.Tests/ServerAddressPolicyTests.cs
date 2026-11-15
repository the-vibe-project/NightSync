using NightSync.Desktop;
using Xunit;

namespace NightSync.Desktop.Tests;

public sealed class ServerAddressPolicyTests
{
    [Theory]
    [InlineData("https://sync.example", "https://sync.example", true)]
    [InlineData("https://SYNC.EXAMPLE", "https://sync.example", true)]
    [InlineData("https://sync.example:8443", "https://sync.example", false)]
    [InlineData("https://attacker.example", "https://sync.example", false)]
    [InlineData("http://127.0.0.1:8787", "http://127.0.0.1:8787", true)]
    [InlineData("http://127.0.0.1.attacker.example:8787", "http://127.0.0.1:8787", false)]
    [InlineData("http://localhost:8787", "http://127.0.0.1:8787", false)]
    [InlineData("http://192.0.2.10:8787", "http://192.0.2.10:8787", false)]
    [InlineData("https://user:password@sync.example", "https://sync.example", false)]
    [InlineData("https://sync.example", "https://user:password@sync.example", false)]
    [InlineData("not a URL", "https://sync.example", false)]
    public void CandidateMustUseTheAuthenticatedOrigin(
        string candidate,
        string authenticated,
        bool expected)
    {
        Assert.Equal(expected, ServerAddressPolicy.IsSameAuthenticatedOrigin(candidate, authenticated));
    }
}
