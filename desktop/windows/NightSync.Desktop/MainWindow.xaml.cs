using System.Collections.ObjectModel;
using System.Diagnostics;
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
    private Process? _serverProcess;
    private readonly string _serverLogPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NightSync", "server.log");
    private readonly string _hostConfigPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NightSync", "host-config.json");
    private const string AutoRunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

    public MainWindow()
    {
        InitializeComponent();
        _serverAddress = AppSession.ServerAddress;
        _http.DefaultRequestHeaders.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", AppSession.Token);
        RoleSelector.Visibility = Visibility.Collapsed;
        ServerProfiles.ItemsSource = _profiles;
        LoadProfiles();
        LoadHostConfig();
        if (_profiles.Count == 0) ServerAddress.Text = _serverAddress;
        Title = $"NightSync - {AppSession.Username} ({AppSession.Role})";
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

    private bool IsAdminPreview => AppSession.Role == "admin";
    private void ApplyRole()
    {
        if (AdminActionButton is null) return;
        AdminActionButton.Visibility = IsAdminPreview ? Visibility.Visible : Visibility.Collapsed;
        RoleNotice.Text = IsAdminPreview
            ? "Signed in as administrator. Server permissions are enforced by the API."
            : "Signed in as member. Administrative actions are unavailable.";
    }
    private void RoleSelector_SelectionChanged(object sender, SelectionChangedEventArgs e) => ApplyRole();

    private void Show(string title, string subtitle, string panel)
    {
        PageTitle.Text = title; PageSubtitle.Text = subtitle;
        DashboardPanel.Visibility = panel == "dashboard" ? Visibility.Visible : Visibility.Collapsed;
        ConnectionPanel.Visibility = panel == "connection" ? Visibility.Visible : Visibility.Collapsed;
        PlaceholderPanel.Visibility = panel == "placeholder" ? Visibility.Visible : Visibility.Collapsed;
        FilesPanel.Visibility = panel == "files" ? Visibility.Visible : Visibility.Collapsed;
        HostPanel.Visibility = panel == "host" ? Visibility.Visible : Visibility.Collapsed;
    }
    private void Overview_Click(object sender, RoutedEventArgs e) => Show("Server overview", "Live server and storage readings.", "dashboard");
    private void Connect_Click(object sender, RoutedEventArgs e) => Show("Connect to server", "Save a server profile and test its health endpoint.", "connection");
    private void Storage_Click(object sender, RoutedEventArgs e) => Show("Storage", "Live volume capacity from the connected server.", "dashboard");
    private void Files_Click(object sender, RoutedEventArgs e) => Show("Files", "Browse and test file operations on your NightSync server.", "files");
    private void Photos_Click(object sender, RoutedEventArgs e) => Placeholder("Photos", "Photo gallery and automatic camera backup are not implemented yet.");
    private void Logs_Click(object sender, RoutedEventArgs e)
    {
        if (File.Exists(_serverLogPath))
            Process.Start(new ProcessStartInfo(_serverLogPath) { UseShellExecute = true });
        else
            Placeholder("Server logs", $"No log file yet. Start the local server to create: {_serverLogPath}");
    }
    private void Placeholder(string title, string details)
    {
        Show(title, "Feature preview", "placeholder"); PlaceholderDetails.Text = details;
    }
    private void Host_Click(object sender, RoutedEventArgs e)
    {
        if (!IsAdminPreview) { MessageBox.Show("Only an administrator can manage this host.", "NightSync"); return; }
        Show("Server control & setup", "Configure this Windows PC as your NightSync host.", "host");
        _ = CheckHostStatusAsync();
    }

    private sealed class HostConfig
    {
        public string ServerExe { get; set; } = "";
        public string StoragePath { get; set; } = "";
        public string BackupPath { get; set; } = "";
        public string SyncPath { get; set; } = "";
        public bool AutoStart { get; set; }
    }
    private HostConfig _hostConfig = new();

    private void LoadHostConfig()
    {
        try { if (File.Exists(_hostConfigPath)) _hostConfig = JsonSerializer.Deserialize<HostConfig>(File.ReadAllText(_hostConfigPath)) ?? new(); }
        catch { _hostConfig = new(); }
        ServerExePathBox.Text = _hostConfig.ServerExe;
        StoragePathBox.Text = _hostConfig.StoragePath;
        BackupPathBox.Text = _hostConfig.BackupPath;
        SyncPathBox.Text = _hostConfig.SyncPath;
        AutoStartCheck.IsChecked = _hostConfig.AutoStart;
    }

    private string ChooseFolder(string current)
    {
        var dialog = new OpenFolderDialog { Title = "Choose NightSync folder", Multiselect = false };
        if (Directory.Exists(current)) dialog.FolderName = current;
        return dialog.ShowDialog() == true ? dialog.FolderName : current;
    }
    private void BrowseServerExe_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog { Title = "Select NightSync Server executable", Filter = "NightSync Server (*.exe)|*.exe|All files (*.*)|*.*" };
        if (dialog.ShowDialog() == true) ServerExePathBox.Text = dialog.FileName;
    }
    private void BrowseStorage_Click(object sender, RoutedEventArgs e) => StoragePathBox.Text = ChooseFolder(StoragePathBox.Text);
    private void BrowseBackup_Click(object sender, RoutedEventArgs e) => BackupPathBox.Text = ChooseFolder(BackupPathBox.Text);
    private void BrowseSync_Click(object sender, RoutedEventArgs e) => SyncPathBox.Text = ChooseFolder(SyncPathBox.Text);

    private HostConfig ReadHostConfig()
    {
        var cfg = new HostConfig { ServerExe=ServerExePathBox.Text.Trim(), StoragePath=StoragePathBox.Text.Trim(),
            BackupPath=BackupPathBox.Text.Trim(), SyncPath=SyncPathBox.Text.Trim(), AutoStart=AutoStartCheck.IsChecked==true };
        if (!File.Exists(cfg.ServerExe)) throw new InvalidOperationException("Choose a valid NightSync Server .exe file.");
        if (string.IsNullOrWhiteSpace(cfg.StoragePath) || string.IsNullOrWhiteSpace(cfg.BackupPath) || string.IsNullOrWhiteSpace(cfg.SyncPath))
            throw new InvalidOperationException("Choose all three folders: main storage, backup destination and sync destination.");
        var paths = new[] { cfg.StoragePath, cfg.BackupPath, cfg.SyncPath }.Select(Path.GetFullPath).ToArray();
        if (paths.Distinct(StringComparer.OrdinalIgnoreCase).Count() != 3)
            throw new InvalidOperationException("Main storage, backup and sync destinations must be different folders.");
        return cfg;
    }

    private void SaveHostSetup_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            _hostConfig=ReadHostConfig();
            Directory.CreateDirectory(_hostConfig.StoragePath); Directory.CreateDirectory(_hostConfig.BackupPath); Directory.CreateDirectory(_hostConfig.SyncPath);
            Directory.CreateDirectory(Path.GetDirectoryName(_hostConfigPath)!);
            File.WriteAllText(_hostConfigPath,JsonSerializer.Serialize(_hostConfig,new JsonSerializerOptions{WriteIndented=true}));
            ConfigureAutoStart(_hostConfig);
            HostStatusText.Text="Setup saved";
            HostDetailsText.Text="Main storage is active. Backup and sync destinations are configured only; their jobs are not implemented yet.";
        }
        catch(Exception ex){HostStatusText.Text="Setup not saved";HostDetailsText.Text=ex.Message;}
    }

    private void ConfigureAutoStart(HostConfig cfg)
    {
        using var key = Registry.CurrentUser.OpenSubKey(AutoRunKey, writable: true)
            ?? Registry.CurrentUser.CreateSubKey(AutoRunKey, writable: true);
        if (key is null) throw new InvalidOperationException("Could not access the Windows startup registry key.");
        if (cfg.AutoStart)
        {
            var runCommand = "\"" + cfg.ServerExe + "\" --storage \"" + cfg.StoragePath +
                "\" --backup \"" + cfg.BackupPath + "\" --sync \"" + cfg.SyncPath +
                "\" --log-file \"" + _serverLogPath + "\"";
            key.SetValue("NightSyncServer", runCommand);
        }
        else
        {
            key.DeleteValue("NightSyncServer", throwOnMissingValue: false);
        }
    }

    private ProcessStartInfo BuildServerStartInfo(HostConfig cfg)
    {
        var info=new ProcessStartInfo(cfg.ServerExe){WorkingDirectory=Path.GetDirectoryName(cfg.ServerExe)!,UseShellExecute=false,CreateNoWindow=true};
        info.ArgumentList.Add("--storage");info.ArgumentList.Add(cfg.StoragePath);
        info.ArgumentList.Add("--backup");info.ArgumentList.Add(cfg.BackupPath);
        info.ArgumentList.Add("--sync");info.ArgumentList.Add(cfg.SyncPath);
        info.ArgumentList.Add("--log-file");info.ArgumentList.Add(_serverLogPath);
        return info;
    }

    private async Task<bool> WaitForServerAsync(bool shouldBeOnline,int timeoutSeconds=12)
    {
        for(var i=0;i<timeoutSeconds*2;i++)
        {
            try{using var response=await _http.GetAsync("http://127.0.0.1:8787/health");if(response.IsSuccessStatusCode==shouldBeOnline)return true;}
            catch{if(!shouldBeOnline)return true;}
            await Task.Delay(500);
        }
        return false;
    }
    private async Task CheckHostStatusAsync()
    {
        var online=await WaitForServerAsync(true,1);
        HostStatusText.Text=online?"Server is running":"Server is stopped";
        HostDetailsText.Text=online?"Local health endpoint is responding on 127.0.0.1:8787.":"Choose the server executable and folders, save setup, then select Start server.";
    }
    private async void StartServer_Click(object sender,RoutedEventArgs e)
    {
        try
        {
            _hostConfig=ReadHostConfig();
            if(await WaitForServerAsync(true,1)){HostStatusText.Text="Server already running";return;}
            Directory.CreateDirectory(_hostConfig.StoragePath);Directory.CreateDirectory(_hostConfig.BackupPath);Directory.CreateDirectory(_hostConfig.SyncPath);
            Directory.CreateDirectory(Path.GetDirectoryName(_hostConfigPath)!);
            File.WriteAllText(_hostConfigPath,JsonSerializer.Serialize(_hostConfig,new JsonSerializerOptions{WriteIndented=true}));
            ConfigureAutoStart(_hostConfig);
            _serverProcess=Process.Start(BuildServerStartInfo(_hostConfig));
            if(await WaitForServerAsync(true)){HostStatusText.Text="Server started";HostDetailsText.Text="NightSync Server is responding locally. Backup and sync destinations are configured, but their jobs are not active yet.";}
            else{HostStatusText.Text="Start timed out";HostDetailsText.Text="Check the server executable and whether port 8787 is free.";}
        }
        catch(Exception ex){HostStatusText.Text="Could not start server";HostDetailsText.Text=ex.Message;}
    }
    private async void StopServer_Click(object sender,RoutedEventArgs e)=>await StopServerAsync();
    private async Task StopServerAsync()
    {
        try
        {
            using var response=await _http.PostAsync("http://127.0.0.1:8787/api/v1/admin/server/stop",null);
            if(!response.IsSuccessStatusCode){HostStatusText.Text="Stop request failed";HostDetailsText.Text=$"Server returned HTTP {(int)response.StatusCode}. Confirm you are signed in as admin.";return;}
            var stopped=await WaitForServerAsync(false);
            HostStatusText.Text=stopped?"Server stopped":"Stop requested";
            HostDetailsText.Text=stopped?"NightSync Server has stopped.":"The server did not stop within the expected time.";
        }
        catch(Exception ex){HostStatusText.Text="Server not reachable";HostDetailsText.Text=ex.Message;}
    }
    private async void RestartServer_Click(object sender,RoutedEventArgs e)
    {
        try
        {
            await StopServerAsync();
            if(!await WaitForServerAsync(false,8))throw new InvalidOperationException("The server did not stop; restart cancelled.");
            await Task.Delay(500);_hostConfig=ReadHostConfig();
            _serverProcess=Process.Start(BuildServerStartInfo(_hostConfig));
            if(await WaitForServerAsync(true)){HostStatusText.Text="Server restarted";HostDetailsText.Text="NightSync Server is responding on localhost. Sign in again if the current session expired.";}
            else{HostStatusText.Text="Restart timed out";HostDetailsText.Text="Check the server executable and port 8787.";}
        }
        catch(Exception ex){HostStatusText.Text="Restart failed";HostDetailsText.Text=ex.Message;}
    }

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
