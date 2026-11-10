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
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "NightSync", "servers.json");

    public MainWindow()
    {
        InitializeComponent();
        ServerProfiles.ItemsSource = _profiles;
        LoadProfiles();
        if (_profiles.Count == 0) ShowPage("Overview");
    }

    private void LoadProfiles()
    {
        try
        {
            if (!File.Exists(_profilePath)) return;
            var json = File.ReadAllText(_profilePath);
            var saved = JsonSerializer.Deserialize<List<ServerProfile>>(json) ?? new();
            foreach (var item in saved) _profiles.Add(item);
            if (_profiles.Count > 0) ServerProfiles.SelectedIndex = 0;
        }
        catch
        {
            ConnectionDetails.Text = "Saved server profiles could not be read. You can create a new profile.";
        }
    }

    private void PersistProfiles()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_profilePath)!);
        File.WriteAllText(_profilePath, JsonSerializer.Serialize(_profiles,
            new JsonSerializerOptions { WriteIndented = true }));
    }

    private void ServerProfiles_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (ServerProfiles.SelectedItem is not ServerProfile profile) return;
        ServerName.Text = profile.Name;
        ServerAddress.Text = profile.Address;
        ConnectionStatus.Text = "Saved profile selected";
        ConnectionDetails.Text = "Select Test connection to check this server.";
    }

    private void SaveServer_Click(object sender, RoutedEventArgs e)
    {
        var name = ServerName.Text.Trim();
        var address = ServerAddress.Text.Trim().TrimEnd('/');
        if (string.IsNullOrWhiteSpace(name))
        {
            MessageBox.Show("Enter a name for this server.", "NightSync");
            return;
        }
        if (!Uri.TryCreate(address, UriKind.Absolute, out var uri) ||
            uri.Scheme != Uri.UriSchemeHttps || string.IsNullOrEmpty(uri.Host))
        {
            MessageBox.Show("Enter a valid HTTPS address, for example https://cloud.example.com",
                "HTTPS address required");
            return;
        }

        var existing = _profiles.FirstOrDefault(p =>
            string.Equals(p.Address, address, StringComparison.OrdinalIgnoreCase));
        if (existing is null) _profiles.Add(new ServerProfile { Name = name, Address = address });
        else existing.Name = name;

        PersistProfiles();
        ServerProfiles.Items.Refresh();
        ServerProfiles.SelectedItem = _profiles.First(p =>
            string.Equals(p.Address, address, StringComparison.OrdinalIgnoreCase));
        ConnectionStatus.Text = "Saved";
        ConnectionDetails.Text = "Server profile saved on this Windows account.";
    }

    private async void TestConnection_Click(object sender, RoutedEventArgs e)
    {
        var address = ServerAddress.Text.Trim().TrimEnd('/');
        if (!Uri.TryCreate(address, UriKind.Absolute, out var uri) ||
            uri.Scheme != Uri.UriSchemeHttps || string.IsNullOrEmpty(uri.Host))
        {
            ConnectionStatus.Text = "Address required";
            ConnectionDetails.Text = "Use a valid HTTPS server address.";
            return;
        }

        ConnectionStatus.Text = "Testing…";
        ConnectionDetails.Text = "Checking the NightSync /health endpoint.";
        try
        {
            using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(8) };
            using var response = await client.GetAsync(new Uri(uri, "/health"));
            if (response.IsSuccessStatusCode)
            {
                ConnectionStatus.Text = "Reachable";
                ConnectionDetails.Text = $"The server returned HTTP {(int)response.StatusCode}. API compatibility will be checked in a later milestone.";
            }
            else
            {
                ConnectionStatus.Text = "Server responded";
                ConnectionDetails.Text = $"HTTP {(int)response.StatusCode} — the address is reachable, but the health check was not successful.";
            }
        }
        catch (Exception ex)
        {
            ConnectionStatus.Text = "Connection failed";
            ConnectionDetails.Text = ex is TaskCanceledException
                ? "The server did not respond within 8 seconds."
                : "Could not reach this server. Check the address, internet/LAN connection and HTTPS certificate.";
        }
    }

    private void ShowPage(string page)
    {
        PageTitle.Text = page == "Overview" ? "Welcome to NightSync" : page;
        PageSubtitle.Text = page switch
        {
            "Overview" => "Connect to your private cloud and manage your files across devices.",
            "Files" => "File browsing will be available after server API integration.",
            "Photos" => "Photo backup and gallery will be added in a later milestone.",
            "Storage" => "Storage capacity and drive information will appear when connected to a server.",
            _ => "Manage your NightSync connection."
        };
        ConnectionStatus.Text = "Not connected";
    }

    private void Overview_Click(object sender, RoutedEventArgs e) => ShowPage("Overview");
    private void Connect_Click(object sender, RoutedEventArgs e) => ShowPage("Connect to your NightSync server");
    private void Files_Click(object sender, RoutedEventArgs e) => ShowPage("Files");
    private void Photos_Click(object sender, RoutedEventArgs e) => ShowPage("Photos");
    private void Storage_Click(object sender, RoutedEventArgs e) => ShowPage("Storage");
    private void Host_Click(object sender, RoutedEventArgs e)
    {
        MessageBox.Show("NightSync Server hosting is planned for a later release. The separate server application and its logs will be retained in the project.",
            "NightSync Server — coming later");
    }
}

public sealed class ServerProfile
{
    public string Name { get; set; } = "";
    public string Address { get; set; } = "";
    public override string ToString() => Name;
}
