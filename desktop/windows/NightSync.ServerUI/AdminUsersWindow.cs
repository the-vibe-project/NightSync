using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace NightSync.ServerUI;

public sealed class AdminUsersWindow : Window
{
    private readonly HttpClient _http = new() { BaseAddress = new Uri("http://127.0.0.1:8787/"), Timeout = TimeSpan.FromSeconds(12) };
    private readonly TextBox _adminName = Field("Administrator username");
    private readonly PasswordBox _adminPassword = new() { Height = 34, Background = Brush("#0B1422"), Foreground = Brush("#ECF7FF"), BorderBrush = Brush("#29435F"), Padding = new Thickness(9) };
    private readonly TextBox _newUsername = Field("New username");
    private readonly ComboBox _role = new() { Height = 34, Background = Brush("#152238"), Foreground = Brush("#ECF7FF"), BorderBrush = Brush("#29435F") };
    private readonly ListBox _users = new() { Background = Brush("#09121E"), Foreground = Brush("#ECF7FF"), BorderBrush = Brush("#243B57"), Padding = new Thickness(8) };
    private readonly TextBlock _status = new() { Foreground = Brush("#91A7C1"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 8, 0, 0) };
    private string? _token;

    public AdminUsersWindow()
    {
        Title = "NightSync · User management";
        Width = 760; Height = 650; MinWidth = 650; MinHeight = 560;
        WindowStartupLocation = WindowStartupLocation.CenterOwner;
        Background = Brush("#070B14");
        _role.Items.Add("member"); _role.Items.Add("admin"); _role.SelectedIndex = 0;

        var root = new StackPanel { Margin = new Thickness(26) };
        root.Children.Add(new TextBlock { Text = "USER MANAGEMENT", Foreground = Brush("#69E5FF"), FontSize = 10, FontWeight = FontWeights.Bold });
        root.Children.Add(new TextBlock { Text = "Control who can access this NightSync server.", Foreground = Brush("#ECF7FF"), FontSize = 23, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 5, 0, 18) });

        var login = Panel();
        login.Children.Add(Heading("Administrator sign-in"));
        login.Children.Add(_adminName);
        login.Children.Add(_adminPassword);
        var loginButton = Button("Sign in as administrator");
        loginButton.Click += async (_, _) => await SignInAsync();
        login.Children.Add(loginButton);
        root.Children.Add(login);

        var create = Panel();
        create.Margin = new Thickness(0, 14, 0, 0);
        create.Children.Add(Heading("Create account"));
        create.Children.Add(_newUsername);
        create.Children.Add(_role);
        var createButton = Button("Create user and generate temporary password");
        createButton.Click += async (_, _) => await CreateUserAsync();
        create.Children.Add(createButton);
        root.Children.Add(create);

        var listPanel = Panel();
        listPanel.Margin = new Thickness(0, 14, 0, 0);
        listPanel.Children.Add(Heading("Server accounts"));
        _users.Height = 190;
        listPanel.Children.Add(_users);
        var actions = new WrapPanel { Margin = new Thickness(0, 10, 0, 0) };
        var refresh = Button("Refresh users");
        refresh.Click += async (_, _) => await LoadUsersAsync();
        var reset = Button("Reset selected user's password");
        reset.Background = Brush("#263049"); reset.Foreground = Brush("#ECF7FF");
        reset.Click += async (_, _) => await ResetSelectedAsync();
        actions.Children.Add(refresh); actions.Children.Add(reset);
        listPanel.Children.Add(actions);
        listPanel.Children.Add(_status);
        root.Children.Add(listPanel);

        Content = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Content = root };
        Closed += (_, _) => _http.Dispose();
        SetManagementEnabled(false);
    }

    private static SolidColorBrush Brush(string color) => new((Color)ColorConverter.ConvertFromString(color));
    private static TextBox Field(string hint) => new() { Height = 34, Margin = new Thickness(0, 5, 0, 8), Padding = new Thickness(9), Background = Brush("#0B1422"), Foreground = Brush("#ECF7FF"), BorderBrush = Brush("#29435F"), ToolTip = hint };
    private static StackPanel Panel() => new() { Margin = new Thickness(0, 8, 0, 0) };
    private static TextBlock Heading(string text) => new() { Text = text, Foreground = Brush("#A48CFF"), FontSize = 12, FontWeight = FontWeights.Bold, Margin = new Thickness(0, 0, 0, 8) };
    private static Button Button(string text) => new() { Content = text, Background = Brush("#69E5FF"), Foreground = Brush("#06111C"), BorderThickness = new Thickness(0), Padding = new Thickness(14, 9, 14, 9), Margin = new Thickness(0, 5, 8, 0), FontWeight = FontWeights.SemiBold };

    private void SetManagementEnabled(bool enabled)
    {
        _newUsername.IsEnabled = enabled; _role.IsEnabled = enabled; _users.IsEnabled = enabled;
        if (Content is ScrollViewer scroll && scroll.Content is StackPanel root && root.Children.Count >= 3)
        {
            root.Children[1].IsEnabled = enabled;
            root.Children[2].IsEnabled = enabled;
        }
    }

    private async Task SignInAsync()
    {
        try
        {
            _status.Text = "Authenticating administrator…";
            using var payload = JsonDocument.Parse(JsonSerializer.Serialize(new { username = _adminName.Text.Trim(), password = _adminPassword.Password }));
            using var response = await _http.PostAsync("api/v1/login", JsonContent(payload.RootElement));
            var body = await response.Content.ReadAsStringAsync();
            if (!response.IsSuccessStatusCode) throw new InvalidOperationException("Sign-in failed. Check the administrator credentials.");
            using var doc = JsonDocument.Parse(body);
            if (doc.RootElement.GetProperty("role").GetString() != "admin") throw new InvalidOperationException("This account does not have administrator permissions.");
            _token = doc.RootElement.GetProperty("token").GetString();
            _http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", _token);
            SetManagementEnabled(true);
            _status.Text = "Administrator authenticated. User management is unlocked.";
            await LoadUsersAsync();
        }
        catch (Exception ex) { _status.Text = ex.Message; SetManagementEnabled(false); }
    }

    private static StringContent JsonContent(JsonElement value) => new(value.GetRawText(), Encoding.UTF8, "application/json");

    private async Task LoadUsersAsync()
    {
        if (_token is null) return;
        try
        {
            using var response = await _http.GetAsync("api/v1/admin/users");
            var body = await response.Content.ReadAsStringAsync();
            if (!response.IsSuccessStatusCode) throw new InvalidOperationException("Could not load users: " + body);
            using var doc = JsonDocument.Parse(body);
            _users.Items.Clear();
            foreach (var user in doc.RootElement.GetProperty("users").EnumerateArray())
            {
                var name = user.GetProperty("username").GetString() ?? "";
                var role = user.GetProperty("role").GetString() ?? "member";
                var mustChange = user.GetProperty("must_change_password").GetBoolean();
                _users.Items.Add(new UserItem(name, role, mustChange));
            }
            _status.Text = $"Loaded {_users.Items.Count} account(s). Password hashes are never displayed.";
        }
        catch (Exception ex) { _status.Text = ex.Message; }
    }

    private async Task CreateUserAsync()
    {
        if (_token is null) return;
        var username = _newUsername.Text.Trim();
        if (username.Length < 3) { _status.Text = "Username must contain at least 3 characters."; return; }
        var temporaryPassword = "Ns!" + Convert.ToHexString(RandomNumberGenerator.GetBytes(18)) + "a7";
        try
        {
            var json = JsonSerializer.Serialize(new { username, role = _role.SelectedItem?.ToString() ?? "member", temporary_password = temporaryPassword });
            using var response = await _http.PostAsync("api/v1/admin/users", new StringContent(json, Encoding.UTF8, "application/json"));
            var body = await response.Content.ReadAsStringAsync();
            if (!response.IsSuccessStatusCode) throw new InvalidOperationException(body);
            _newUsername.Clear();
            MessageBox.Show($"Account created. Share this temporary password with the user through a private channel. It will be required to change it at first sign-in.\n\n{temporaryPassword}", "NightSync temporary password", MessageBoxButton.OK, MessageBoxImage.Information);
            await LoadUsersAsync();
        }
        catch (Exception ex) { _status.Text = "Could not create account: " + ex.Message; }
    }

    private async Task ResetSelectedAsync()
    {
        if (_token is null || _users.SelectedItem is not UserItem selected) { _status.Text = "Select a user first."; return; }
        if (MessageBox.Show($"Generate a new temporary password for {selected.Username}?", "Reset user password", MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        try
        {
            using var response = await _http.PostAsync("api/v1/admin/users/" + Uri.EscapeDataString(selected.Username) + "/reset", null);
            var body = await response.Content.ReadAsStringAsync();
            if (!response.IsSuccessStatusCode) throw new InvalidOperationException(body);
            using var doc = JsonDocument.Parse(body);
            var password = doc.RootElement.GetProperty("temporary_password").GetString();
            MessageBox.Show($"Temporary password for {selected.Username}:\n\n{password}\n\nThe user must change it after signing in.", "NightSync password reset", MessageBoxButton.OK, MessageBoxImage.Information);
            await LoadUsersAsync();
        }
        catch (Exception ex) { _status.Text = "Could not reset password: " + ex.Message; }
    }

    private sealed record UserItem(string Username, string Role, bool MustChangePassword)
    {
        public override string ToString() => $"{Username}    ·    {Role}    ·    {(MustChangePassword ? "password change required" : "active")}";
    }
}
