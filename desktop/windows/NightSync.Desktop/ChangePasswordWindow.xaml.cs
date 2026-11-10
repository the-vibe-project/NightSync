using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Windows;

namespace NightSync.Desktop;

public partial class ChangePasswordWindow : Window
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(10) };
    public ChangePasswordWindow() { InitializeComponent(); }
    private async void Change_Click(object sender, RoutedEventArgs e)
    {
        if (NewBox.Password.Length < 12) { MessageText.Text = "Use at least 12 characters for your new password."; return; }
        if (NewBox.Password != ConfirmBox.Password) { MessageText.Text = "The new passwords do not match."; return; }
        try
        {
            _http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", AppSession.Token);
            var body = JsonSerializer.Serialize(new { current_password = CurrentBox.Password, new_password = NewBox.Password });
            using var response = await _http.PostAsync(AppSession.ServerAddress + "/api/v1/change-password",
                new StringContent(body, Encoding.UTF8, "application/json"));
            if (!response.IsSuccessStatusCode) { MessageText.Text = "Password change failed. Check your temporary password."; return; }
            DialogResult = true;
            Close();
        }
        catch (Exception ex) { MessageText.Text = ex.Message; }
    }
}
