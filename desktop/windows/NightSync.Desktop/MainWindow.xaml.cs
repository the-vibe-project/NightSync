using System.Collections.ObjectModel;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;

namespace NightSync.Desktop;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<ServerProfile> _profiles = new();
    private readonly string _profilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NightSync", "servers.json");

    public MainWindow()
    {
        InitializeComponent();
        ServerProfiles.ItemsSource = _profiles;
        LoadProfiles();
        ApplyRole();
    }

    private void LoadProfiles()
    {
        try
        {
            if (!File.Exists(_profilePath)) return;
            foreach (var p in JsonSerializer.Deserialize<List<ServerProfile>>(File.ReadAllText(_profilePath)) ?? new())
                _profiles.Add(p);
            if (_profiles.Count > 0) ServerProfiles.SelectedIndex = 0;
        }
        catch { ConnectionDetails.Text = "Could not read saved profiles. You can create a new one."; }
    }

    private void PersistProfiles()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_profilePath)!);
        File.WriteAllText(_profilePath, JsonSerializer.Serialize(_profiles, new JsonSerializerOptions { WriteIndented = true }));
    }

    private bool IsAdminPreview => RoleSelector.SelectedIndex == 1;

    private void ApplyRole()
    {
        if (AdminActionButton is null) return;
        AdminActionButton.Visibility = IsAdminPreview ? Visibility.Visible : Visibility.Collapsed;
        RoleNotice.Text = IsAdminPreview
            ? "Admin preview: management controls are demonstrations only. Server-side permissions are not implemented yet."
            : "Member access: monitoring is read-only. Management controls are hidden.";
    }

    private void RoleSelector_SelectionChanged(object sender, SelectionChangedEventArgs e) => ApplyRole();

    private void Show(string title, string subtitle, string panel)
    {
        PageTitle.Text = title;
        PageSubtitle.Text = subtitle;
        DashboardPanel.Visibility = panel == "dashboard" ? Visibility.Visible : Visibility.Collapsed;
        ConnectionPanel.Visibility = panel == "connection" ? Visibility.Visible : Visibility.Collapsed;
        PlaceholderPanel.Visibility = panel == "placeholder" ? Visibility.Visible : Visibility.Collapsed;
    }

    private void Overview_Click(object sender, RoutedEventArgs e) =>
        Show("Server overview", "Health, storage and activity at a glance.", "dashboard");
    private void Connect_Click(object sender, RoutedEventArgs e) =>
        Show("Connect to server", "Save a server profile and test its health endpoint.", "connection");
    private void Storage_Click(object sender, RoutedEventArgs e) => Placeholder("Storage", "Drive discovery, real capacity and pool management will be connected to the server later.");
    private void Files_Click(object sender, RoutedEventArgs e) => Placeholder("Files", "File browsing, uploads, downloads and sync are planned for a later milestone.");
    private void Photos_Click(object sender, RoutedEventArgs e) => Placeholder("Photos", "Photo library and camera backup are planned for a later milestone.");
    private void Logs_Click(object sender, RoutedEventArgs e) => Placeholder("Server logs", "Server logs will be available to authorised administrators when the separate NightSync Server is implemented.");
    private void Placeholder(string title, string details)
    {
        Show(title, "Feature preview", "placeholder");
        PlaceholderDetails.Text = details;
    }

    private void Host_Click(object sender, RoutedEventArgs e) =>
        MessageBox.Show("NightSync Server remains a separate planned application. Its setup and logs will be added later.", "NightSync Server");

    private void Refresh_Click(object sender, RoutedEventArgs e) =>
        MessageBox.Show("Demo dashboard refreshed. Values are illustrative and are not readings from a real server.", "NightSync");

    private void AdminDiagnostics_Click(object sender, RoutedEventArgs e)
    {
        if (!IsAdminPreview) return;
        MessageBox.Show("Demo only: diagnostics preview completed. No server settings were changed. Real admin actions require server-side authentication and authorization.", "Admin diagnostics");
    }

    private void ServerProfiles_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (ServerProfiles.SelectedItem is not ServerProfile p) return;
        ServerName.Text = p.Name;
        ServerAddress.Text = p.Address;
    }

    private void SaveServer_Click(object sender, RoutedEventArgs e)
    {
        var name = ServerName.Text.Trim();
        var address = ServerAddress.Text.Trim().TrimEnd('/');
        if (string.IsNullOrWhiteSpace(name) ||
            !Uri.TryCreate(address, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
        {
            MessageBox.Show("Enter a server name and valid HTTPS address.", "NightSync");
            return;
        }
        var existing = _profiles.FirstOrDefault(p => string.Equals(p.Address, address, StringComparison.OrdinalIgnoreCase));
        if (existing is null) _profiles.Add(new ServerProfile { Name = name, Address = address });
        else existing.Name = name;
        PersistProfiles();
        ServerProfiles.Items.Refresh();
        ConnectionStatus.Text = "Saved";
        ConnectionDetails.Text = "Server profile saved on this Windows account.";
    }

    private async void TestConnection_Click(object sender, RoutedEventArgs e)
    {
        var address = ServerAddress.Text.Trim().TrimEnd('/');
        if (!Uri.TryCreate(address, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
        {
            ConnectionStatus.Text = "HTTPS address required";
            return;
        }
        ConnectionStatus.Text = "Testing…";
        try
        {
            using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(8) };
            using var response = await client.GetAsync(new Uri(uri, "/health"));
            ConnectionStatus.Text = response.IsSuccessStatusCode ? "Reachable" : $"HTTP {(int)response.StatusCode}";
            ConnectionDetails.Text = "This checks basic endpoint reachability only, not login or full API compatibility.";
        }
        catch (Exception ex)
        {
            ConnectionStatus.Text = "Connection failed";
            ConnectionDetails.Text = ex is TaskCanceledException ? "Timed out after 8 seconds." : "Check the address, network and HTTPS certificate.";
        }
    }
}

public sealed class ServerProfile
{
    public string Name { get; set; } = "";
    public string Address { get; set; } = "";
    public override string ToString() => Name;
}
