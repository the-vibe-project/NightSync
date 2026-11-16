using System.Text.Json;
using NightSync.ServerUI;
using Xunit;

namespace NightSync.Desktop.Tests;

public sealed class ConnectionQrPayloadTests
{
    [Theory]
    [InlineData("https://sync.example", "https://sync.example")]
    [InlineData(" https://sync.example:8443/nightsync/ ", "https://sync.example:8443/nightsync")]
    public void CreateEmitsTheAndroidConnectionPayload(string input, string expectedUrl)
    {
        using var document = JsonDocument.Parse(ConnectionQrPayload.Create(input));
        var root = document.RootElement;

        Assert.Equal("nightsync-connect", root.GetProperty("type").GetString());
        Assert.Equal(1, root.GetProperty("version").GetInt32());
        Assert.Equal(expectedUrl, root.GetProperty("url").GetString());
        Assert.Equal(3, root.EnumerateObject().Count());
    }

    [Theory]
    [InlineData("")]
    [InlineData("not-a-url")]
    [InlineData("http://sync.example")]
    [InlineData("https:///missing-host")]
    [InlineData("https://user:password@sync.example")]
    [InlineData("https://sync.example?redirect=https://attacker.example")]
    [InlineData("https://sync.example#fragment")]
    public void CreateRejectsUnsafeOrInvalidAddresses(string input)
    {
        Assert.Throws<ArgumentException>(() => ConnectionQrPayload.Create(input));
    }
}
