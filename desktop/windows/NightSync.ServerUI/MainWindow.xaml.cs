using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.Win32;

namespace NightSync.ServerUI;

public partial class MainWindow : Window
{
    private sealed class Config
    {
        public string ServerExe { get; set; } = "";
        public string MainPath { get; set; } = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "NightSync", "NightSyncData");
        public string BackupPath { get; set; } = "";
        public string SyncPath { get; set; } = "";
        public bool AutoStart { get; set; }
    }

    private readonly string _root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NightSync");
    private string ConfigPath => Path.Combine(_root, "server-ui.json");
    private string LogPath => Path.Combine(_root, "server.log");
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string RunValue = "NightSyncServer";
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(2) };
    private readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromSeconds(3) };
    private Config _config = new();
    private Process? _ownedServer;
    private bool _loading;

    public MainWindow()
    {
        InitializeComponent();
        Directory.CreateDirectory(_root);
        LoadConfig();
        _timer.Tick += async (_, _) => await RefreshStatusAsync();
        _timer.Start();
        Loaded += async (_, _) => await RefreshStatusAsync();
        Closing += (_, _) => { _timer.Stop(); _http.Dispose(); };
    }

    private void LoadConfig()
    {
        _loading = true;
        try
        {
            if (File.Exists(ConfigPath))
                _config = JsonSerializer.Deserialize<Config>(File.ReadAllText(ConfigPath)) ?? new Config();
        }
        catch { _config = new Config(); }
        ExeBox.Text = _config.ServerExe;
        MainBox.Text = _config.MainPath;
        BackupBox.Text = _config.BackupPath;
        SyncBox.Text = _config.SyncPath;
        AutoStartCheck.IsChecked = _config.AutoStart;
        _loading = false;
        LoadLog();
    }

    private void SaveConfig()
    {
        _config.ServerExe = ExeBox.Text.Trim();
        _config.MainPath = MainBox.Text.Trim();
        _config.BackupPath = BackupBox.Text.Trim();
        _config.SyncPath = SyncBox.Text.Trim();
        _config.AutoStart = AutoStartCheck.IsChecked == true;
        Directory.CreateDirectory(_config.MainPath);
        if (!string.IsNullOrWhiteSpace(_config.BackupPath)) Directory.CreateDirectory(_config.BackupPath);
        if (!string.IsNullOrWhiteSpace(_config.SyncPath)) Directory.CreateDirectory(_config.SyncPath);
        File.WriteAllText(ConfigPath, JsonSerializer.Serialize(_config, new JsonSerializerOptions { WriteIndented = true }));
        SetAutoStart();
    }

    private void SetAutoStart()
    {
        using var key = Registry.CurrentUser.OpenSubKey(RunKey, true) ?? Registry.CurrentUser.CreateSubKey(RunKey, true);
        if (key is null) throw new InvalidOperationException("Cannot configure Windows startup.");
        if (_config.AutoStart && File.Exists(_config.ServerExe))
        {
            var args = BuildArguments();
            key.SetValue(RunValue, $"\"{_config.ServerExe}\" {args}");
        }
        else key.DeleteValue(RunValue, false);
    }

    private string BuildArguments()
    {
        var args = $"--storage \"{_config.MainPath}\" --backup \"{_config.BackupPath}\" --sync \"{_config.SyncPath}\" --log-file \"{LogPath}\"";
        return args;
    }

    private async Task<bool> IsOnlineAsync()
    {
        try
        {
            using var response = await _http.GetAsync("http://127.0.0.1:8787/health");
            return response.IsSuccessStatusCode;
        }
        catch { return false; }
    }

    private async Task RefreshStatusAsync()
    {
        var online = await IsOnlineAsync();
        StatusText.Text = online ? "Online · localhost" : "Offline";
        StatusDot.Fill = new SolidColorBrush(online ? Color.FromRgb(34, 197, 94) : Color.FromRgb(100, 116, 139));
        if (online)
        {
            try
            {
                using var response = await _http.GetAsync("http://127.0.0.1:8787/health");
                using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
                var root = doc.RootElement;
                VersionText.Text = root.TryGetProperty("version", out var v) ? v.GetString() ?? "—" : "—";
                var uptime = root.TryGetProperty("uptime_seconds", out var u) ? u.GetInt64() : 0;
                UptimeText.Text = TimeSpan.FromSeconds(uptime).ToString(@"d\.hh\:mm\:ss");
                HealthText.Text = "Health endpoint responding: http://127.0.0.1:8787/health\nServer process is reachable on this PC.";
            }
            catch { HealthText.Text = "Server responded, but its health details could not be read."; }
        }
        else
        {
            VersionText.Text = "—";
            UptimeText.Text = "—";
            HealthText.Text = "No server is responding on 127.0.0.1:8787. Select the server executable and press Start.";
        }
        UpdateDriveStats();
        LoadLog();
    }

    private void UpdateDriveStats()
    {
        try
        {
            var path = string.IsNullOrWhiteSpace(MainBox.Text) ? _config.MainPath : MainBox.Text;
            var root = Path.GetPathRoot(Path.GetFullPath(path));
            if (string.IsNullOrWhiteSpace(root)) throw new IOException("Storage drive not found.");
            var drive = new DriveInfo(root);
            if (!drive.IsReady) throw new IOException("Storage drive is not ready.");
            UsedText.Text = FormatBytes(drive.TotalSize - drive.AvailableFreeSpace);
            FreeText.Text = FormatBytes(drive.AvailableFreeSpace);
        }
        catch { UsedText.Text = "Unavailable"; FreeText.Text = "Unavailable"; }
    }

    private static string FormatBytes(long value)
    {
        string[] units = ["B", "KB", "GB", "TB"];
        double n = value; int i = 0;
        while (n >= 1024 && i < units.Length - 1) { n /= 1024; i++; }
        return $"{n:0.0} {units[i]}";
    }

    private async Task StartServerAsync()
    {
        SaveConfig();
        if (await IsOnlineAsync()) { ControlMessage.Text = "A NightSync server is already responding on port 8787."; return; }
        if (!File.Exists(_config.ServerExe)) { ControlMessage.Text = "Choose a valid NightSync-Server-windows-x64.exe first."; return; }
        var psi = new ProcessStartInfo(_config.ServerExe, BuildArguments())
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            WorkingDirectory = Path.GetDirectoryName(_config.ServerExe) ?? AppContext.BaseDirectory
        };
        _ownedServer = new Process { StartInfo = psi, EnableRaisingEvents = true };
        if (!_ownedServer.Start()) { ControlMessage.Text = "Windows could not start the server process."; return; }
        ControlMessage.Text = "Start command sent. Waiting for the health endpoint…";
        for (var i = 0; i < 20; i++)
        {
            await Task.Delay(500);
            if (await IsOnlineAsync()) { ControlMessage.Text = "NightSync Server is online."; await RefreshStatusAsync(); return; }
            if (_ownedServer.HasExited) { ControlMessage.Text = $"Server exited with code {_ownedServer.ExitCode}. Check the log."; break; }
        }
        await RefreshStatusAsync();
    }

    private async Task StopServerAsync()
    {
        if (_ownedServer is null || _ownedServer.HasExited)
        {
            ControlMessage.Text = "This dashboard did not start the running server. Close its original console window to stop it.";
            return;
        }
        try
        {
            _ownedServer.Kill(entireProcessTree: true);
            await _ownedServer.WaitForExitAsync();
            ControlMessage.Text = "NightSync Server stopped.";
        }
        catch (Exception ex) { ControlMessage.Text = "Could not stop server: " + ex.Message; }
        await RefreshStatusAsync();
    }

    private async void Start_Click(object sender, RoutedEventArgs e) => await StartServerAsync();
    private async void Stop_Click(object sender, RoutedEventArgs e) => await StopServerAsync();
    private async void Restart_Click(object sender, RoutedEventArgs e)
    {
        await StopServerAsync();
        await Task.Delay(700);
        await StartServerAsync();
    }
    private async void Refresh_Click(object sender, RoutedEventArgs e) => await RefreshStatusAsync();

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        try { SaveConfig(); StorageMessage.Text = "Configuration saved. Main storage is used by the current server. Backup and sync paths are configured only."; }
        catch (Exception ex) { StorageMessage.Text = "Could not save: " + ex.Message; }
    }

    private void AutoStart_Changed(object sender, RoutedEventArgs e)
    {
        if (_loading) return;
        try { _config.AutoStart = AutoStartCheck.IsChecked == true; SaveConfig(); }
        catch (Exception ex) { ControlMessage.Text = "Auto-start setting failed: " + ex.Message; }
    }

    private void BrowseExe_Click(object sender, RoutedEventArgs e)
    {
        var d = new OpenFileDialog { Filter = "NightSync server executable|NightSync-Server-windows-x64.exe;*.exe|All files|*.*" };
        if (d.ShowDialog() == true) ExeBox.Text = d.FileName;
    }
    private void BrowseMain_Click(object sender, RoutedEventArgs e) => ChooseFolder(MainBox);
    private void BrowseBackup_Click(object sender, RoutedEventArgs e) => ChooseFolder(BackupBox);
    private void BrowseSync_Click(object sender, RoutedEventArgs e) => ChooseFolder(SyncBox);

    private static void ChooseFolder(TextBox target)
    {
        var dialog = new OpenFolderDialog { Title = "Choose NightSync folder", Multiselect = false };
        if (dialog.ShowDialog() == true) target.Text = dialog.FolderName;
    }

    private void OpenLog_Click(object sender, RoutedEventArgs e)
    {
        Directory.CreateDirectory(_root);
        if (!File.Exists(LogPath)) File.WriteAllText(LogPath, "NightSync Server log will appear here.\n");
        Process.Start(new ProcessStartInfo(LogPath) { UseShellExecute = true });
    }

    private void LoadLog()
    {
        try
        {
            if (!File.Exists(LogPath)) { LogBox.Text = "No log file yet."; return; }
            var lines = File.ReadAllLines(LogPath);
            LogBox.Text = string.Join(Environment.NewLine, lines.TakeLast(250));
            LogBox.ScrollToEnd();
        }
        catch { LogBox.Text = "Unable to read the server log."; }
    }
}
