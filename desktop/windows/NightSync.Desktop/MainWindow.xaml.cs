using System.Collections.ObjectModel;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;

namespace NightSync.Desktop;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<ServerProfile> _profiles = new();
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(12) };
    private readonly string _profilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NightSync", "servers.json");
    private string _serverAddress = "http://127.0.0.1:8787";

    public MainWindow()
    {
        InitializeComponent();
        ServerProfiles.ItemsSource = _profiles;
        LoadProfiles();
        if (_profiles.Count == 0) ServerAddress.Text = _serverAddress;
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
            ? "Admin preview only. This is not server-enforced authorization."
            : "Member view: monitoring and file operations are available; admin controls are hidden.";
    }
    private void RoleSelector_SelectionChanged(object sender, SelectionChangedEventArgs e) => ApplyRole();

    private void Show(string title, string subtitle, string panel)
    {
        PageTitle.Text = title; PageSubtitle.Text = subtitle;
        DashboardPanel.Visibility = panel == "dashboard" ? Visibility.Visible : Visibility.Collapsed;
        ConnectionPanel.Visibility = panel == "connection" ? Visibility.Visible : Visibility.Collapsed;
        PlaceholderPanel.Visibility = panel == "placeholder" ? Visibility.Visible : Visibility.Collapsed;
        FilesPanel.Visibility = panel == "files" ? Visibility.Visible : Visibility.Collapsed;
    }
    private void Overview_Click(object sender, RoutedEventArgs e) => Show("Server overview", "Live server and storage readings.", "dashboard");
    private void Connect_Click(object sender, RoutedEventArgs e) => Show("Connect to server", "Save a server profile and test its health endpoint.", "connection");
    private void Storage_Click(object sender, RoutedEventArgs e) => Show("Storage", "Live volume capacity from the connected server.", "dashboard");
    private void Files_Click(object sender, RoutedEventArgs e) => Show("Files", "Browse and test file operations on your NightSync server.", "files");
    private void Photos_Click(object sender, RoutedEventArgs e) => Placeholder("Photos", "Photo gallery and automatic camera backup are not implemented yet.");
    private void Logs_Click(object sender, RoutedEventArgs e) => Placeholder("Server logs", "The server currently writes logs to its console. Authenticated remote log viewing is a later milestone.");
    private void Placeholder(string title, string details)
    {
        Show(title, "Feature preview", "placeholder"); PlaceholderDetails.Text = details;
    }
    private void Host_Click(object sender, RoutedEventArgs e) =>
        MessageBox.Show("The separate NightSync Server executable is the host for this test build.", "NightSync");

    private void Refresh_Click(object sender, RoutedEventArgs e) => _ = RefreshStatusAsync();
    private async void RefreshLiveStatus_Click(object sender, RoutedEventArgs e) => await RefreshStatusAsync();

    private string CurrentAddress()
    {
        var value = ServerAddress.Text.Trim().TrimEnd('/');
        if (string.IsNullOrWhiteSpace(value)) value = _serverAddress;
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttps && !(uri.Scheme == Uri.UriSchemeHttp && uri.IsLoopback)))
            throw new InvalidOperationException("Use HTTPS for remote servers. HTTP is allowed only for localhost testing.");
        return value;
    }

    private async Task RefreshStatusAsync()
    {
        try
        {
            _serverAddress = CurrentAddress();
            using var response = await _http.GetAsync(_serverAddress + "/api/v1/status");
            response.EnsureSuccessStatusCode();
            using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
            var root = doc.RootElement;
            var storage = root.GetProperty("storage");
            var used = storage.GetProperty("used_bytes").GetInt64();
            var free = storage.GetProperty("free_bytes").GetInt64();
            var memory = root.GetProperty("process_memory_bytes").GetInt64();
            var uptime = TimeSpan.FromSeconds(root.GetProperty("uptime_seconds").GetInt64());
            LiveStatusText.Text = $"{root.GetProperty("name").GetString()} · ONLINE · v{root.GetProperty("version").GetString()}";
            LiveDetailsText.Text = $"Last updated {DateTime.Now:T} · Runtime {root.GetProperty("go_version").GetString()} · Goroutines {root.GetProperty("goroutines").GetInt32()}";
            StorageUsedText.Text = FormatBytes(used);
            StorageFreeText.Text = FormatBytes(free);
            ProcessMemoryText.Text = FormatBytes(memory);
            UptimeText.Text = $"{(int)uptime.TotalDays}d {uptime:hh\\:mm\\:ss}";
            StoragePathText.Text = $"Storage path: {storage.GetProperty("path").GetString()}";
        }
        catch (Exception ex)
        {
            LiveStatusText.Text = "Server unavailable";
            LiveDetailsText.Text = ex.Message;
        }
    }

    private static string FormatBytes(long bytes)
    {
        string[] units = ["B", "KB", "MB", "GB", "TB"];
        double size = Math.Max(0, bytes); int i = 0;
        while (size >= 1024 && i < units.Length - 1) { size /= 1024; i++; }
        return $"{size:0.##} {units[i]}";
    }

    private void AdminDiagnostics_Click(object sender, RoutedEventArgs e)
    {
        if (!IsAdminPreview) return;
        MessageBox.Show("Diagnostics preview only. No server settings were changed.", "NightSync");
    }

    private void ServerProfiles_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (ServerProfiles.SelectedItem is not ServerProfile p) return;
        ServerName.Text = p.Name; ServerAddress.Text = p.Address;
    }

    private void SaveServer_Click(object sender, RoutedEventArgs e)
    {
        var name = ServerName.Text.Trim(); var address = ServerAddress.Text.Trim().TrimEnd('/');
        if (string.IsNullOrWhiteSpace(name) || !Uri.TryCreate(address, UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttps && !(uri.Scheme == Uri.UriSchemeHttp && uri.IsLoopback)))
        {
            MessageBox.Show("Enter a name and a valid HTTPS address. HTTP is allowed only for localhost testing.", "NightSync"); return;
        }
        var existing = _profiles.FirstOrDefault(p => string.Equals(p.Address, address, StringComparison.OrdinalIgnoreCase));
        if (existing is null) _profiles.Add(new ServerProfile { Name = name, Address = address }); else existing.Name = name;
        PersistProfiles(); ServerProfiles.Items.Refresh();
        ConnectionStatus.Text = "Saved"; ConnectionDetails.Text = "Server profile saved on this Windows account.";
        _serverAddress = address;
    }

    private async void TestConnection_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            _serverAddress = CurrentAddress();
            using var response = await _http.GetAsync(_serverAddress + "/health");
            ConnectionStatus.Text = response.IsSuccessStatusCode ? "Reachable" : $"HTTP {(int)response.StatusCode}";
            ConnectionDetails.Text = "Health endpoint responded. Loading live server metrics…";
            await RefreshStatusAsync();
        }
        catch (Exception ex) { ConnectionStatus.Text = "Connection failed"; ConnectionDetails.Text = ex.Message; }
    }

    private async void RefreshFiles_Click(object sender, RoutedEventArgs e) => await LoadFilesAsync();
    private async Task LoadFilesAsync()
    {
        try
        {
            _serverAddress = CurrentAddress();
            using var response = await _http.GetAsync(_serverAddress + "/api/v1/files");
            response.EnsureSuccessStatusCode();
            using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
            FilesList.Items.Clear();
            foreach (var item in doc.RootElement.GetProperty("items").EnumerateArray())
            {
                var name = item.GetProperty("name").GetString() ?? "";
                var size = FormatBytes(item.GetProperty("size_bytes").GetInt64());
                FilesList.Items.Add(new RemoteFile(name, $"{name}  ·  {size}"));
            }
            FilesMessage.Text = $"{FilesList.Items.Count} file(s) in server storage.";
        }
        catch (Exception ex) { FilesMessage.Text = ex.Message; }
    }

    private async void UploadFile_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog { Title = "Select a test file to upload" };
        if (dialog.ShowDialog() != true) return;
        try
        {
            _serverAddress = CurrentAddress();
            var name = Path.GetFileName(dialog.FileName);
            using var content = new StreamContent(File.OpenRead(dialog.FileName));
            using var response = await _http.PutAsync($"{_serverAddress}/api/v1/files?name={Uri.EscapeDataString(name)}", content);
            response.EnsureSuccessStatusCode();
            FilesMessage.Text = $"Uploaded {name}.";
            await LoadFilesAsync();
        }
        catch (Exception ex) { FilesMessage.Text = "Upload failed: " + ex.Message; }
    }

    private async void DownloadFile_Click(object sender, RoutedEventArgs e)
    {
        if (FilesList.SelectedItem is not RemoteFile file) { FilesMessage.Text = "Select a file first."; return; }
        var dialog = new SaveFileDialog { FileName = file.Name };
        if (dialog.ShowDialog() != true) return;
        try
        {
            _serverAddress = CurrentAddress();
            var bytes = await _http.GetByteArrayAsync($"{_serverAddress}/api/v1/files/{Uri.EscapeDataString(file.Name)}");
            await File.WriteAllBytesAsync(dialog.FileName, bytes);
            FilesMessage.Text = $"Downloaded {file.Name}.";
        }
        catch (Exception ex) { FilesMessage.Text = "Download failed: " + ex.Message; }
    }

    private async void DeleteFile_Click(object sender, RoutedEventArgs e)
    {
        if (FilesList.SelectedItem is not RemoteFile file) { FilesMessage.Text = "Select a file first."; return; }
        if (MessageBox.Show($"Delete {file.Name} from server storage?", "Confirm delete", MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        try
        {
            _serverAddress = CurrentAddress();
            using var response = await _http.DeleteAsync($"{_serverAddress}/api/v1/files/{Uri.EscapeDataString(file.Name)}");
            response.EnsureSuccessStatusCode();
            FilesMessage.Text = $"Deleted {file.Name}.";
            await LoadFilesAsync();
        }
        catch (Exception ex) { FilesMessage.Text = "Delete failed: " + ex.Message; }
    }
}

public sealed class ServerProfile
{
    public string Name { get; set; } = "";
    public string Address { get; set; } = "";
    public override string ToString() => Name;
}
public sealed record RemoteFile(string Name, string Display) { public override string ToString() => Display; }
