using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Windows;

namespace NightSync.Desktop;

public partial class LoginWindow : Window
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(10) };
    public LoginWindow(){ InitializeComponent(); }
    private string BaseAddress => AddressBox.Text.Trim().TrimEnd('/');

    private async void Setup_Click(object sender, RoutedEventArgs e)
    {
        if (!ValidAddress()) return;
        var username=UsernameBox.Text.Trim(); var password=PasswordBox.Password;
        if (string.IsNullOrWhiteSpace(username) || password.Length < 12)
        { MessageText.Text="Choose an admin user ID and a password of at least 12 characters."; return; }
        try
        {
            var body=JsonSerializer.Serialize(new { username, password });
            using var response=await _http.PostAsync(BaseAddress+"/api/v1/setup",new StringContent(body,Encoding.UTF8,"application/json"));
            var result=await response.Content.ReadAsStringAsync();
            if(!response.IsSuccessStatusCode){MessageText.Text=result;return;}
            MessageText.Text="Administrator created. Now sign in.";
        }
        catch(Exception ex){MessageText.Text=ex.Message;}
    }

    private async void SignIn_Click(object sender, RoutedEventArgs e)
    {
        if (!ValidAddress()) return;
        try
        {
            var body=JsonSerializer.Serialize(new { username=UsernameBox.Text.Trim(), password=PasswordBox.Password });
            using var response=await _http.PostAsync(BaseAddress+"/api/v1/login",new StringContent(body,Encoding.UTF8,"application/json"));
            var json=await response.Content.ReadAsStringAsync();
            if(!response.IsSuccessStatusCode){MessageText.Text="Sign-in failed. Check the server, user ID and password.";return;}
            using var doc=JsonDocument.Parse(json); var root=doc.RootElement;
            AppSession.ServerAddress=BaseAddress;
            AppSession.Token=root.GetProperty("token").GetString()!;
            AppSession.Username=root.GetProperty("username").GetString()!;
            AppSession.Role=root.GetProperty("role").GetString()!;
            AppSession.MustChangePassword=root.GetProperty("must_change_password").GetBoolean();
            DialogResult=true;
        }
        catch(Exception ex){MessageText.Text=ex.Message;}
    }

    private bool ValidAddress()
    {
        if(!Uri.TryCreate(BaseAddress,UriKind.Absolute,out var uri) ||
           !(uri.Scheme=="https" || (uri.Scheme=="http" && uri.IsLoopback)))
        {MessageText.Text="Use HTTPS remotely. HTTP is allowed only for localhost testing.";return false;}
        return true;
    }
}
