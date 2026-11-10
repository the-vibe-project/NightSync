namespace NightSync.Desktop;
public static class AppSession
{
    public static string ServerAddress { get; set; } = "http://127.0.0.1:8787";
    public static string Token { get; set; } = "";
    public static string Username { get; set; } = "";
    public static string Role { get; set; } = "";
    public static bool MustChangePassword { get; set; }
}
