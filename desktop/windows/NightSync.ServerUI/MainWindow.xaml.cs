using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.Win32;

namespace NightSync.ServerUI;

public partial class MainWindow : Window
{
    private const string ServiceName = "NightSync";
    private const string ServiceRegistryPath = @"SYSTEM\CurrentControlSet\Services\NightSync";

    private sealed class Config
    {
        public string ServerExe { get; set; } = "";
        public string MainPath { get; set; } = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "NightSync", "NightSyncData");
        public string BackupPath { get; set; } = "";
        public string SyncPath { get; set; } = "";
    }

    private readonly string _root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NightSync");
    private string ConfigPath => Path.Combine(_root, "server-ui.json");
    private string LogPath => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "NightSync", "server.log");
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(2) };
    private readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromSeconds(5) };
    private Config _config = new();
    private Process? _ownedServer;

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

    private bool IsServiceInstalled()
    {
        using var key = Registry.LocalMachine.OpenSubKey(ServiceRegistryPath, false);
        return key is not null;
    }

    private void LoadConfig()
    {
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
        LoadLog();
    }

    private void SaveConfig()
    {
        _config.ServerExe = ExeBox.Text.Trim();
        _config.MainPath = MainBox.Text.Trim();
        _config.BackupPath = BackupBox.Text.Trim();
        _config.SyncPath = SyncBox.Text.Trim();

        if (string.IsNullOrWhiteSpace(_config.MainPath))
            throw new InvalidOperationException("Choose a main storage folder first.");

        Directory.CreateDirectory(_config.MainPath);
        if (!string.IsNullOrWhiteSpace(_config.BackupPath)) Directory.CreateDirectory(_config.BackupPath);
        if (!string.IsNullOrWhiteSpace(_config.SyncPath)) Directory.CreateDirectory(_config.SyncPath);
        File.WriteAllText(ConfigPath, JsonSerializer.Serialize(_config, new JsonSerializerOptions { WriteIndented = true }));
    }

    private static string Quote(string value) => "\"" + value.Replace("\"", "\\\"") + "\"";

    private string BuildArguments() =>
        $"--storage {Quote(_config.MainPath)} --backup {Quote(_config.BackupPath)} --sync {Quote(_config.SyncPath)} --log-file {Quote(LogPath)}";

    private string BuildServiceCommandLine() => $"{Quote(_config.ServerExe)} {BuildArguments()}";

    private static async Task<int> RunToolAsync(string fileName, IEnumerable<string> arguments, bool elevate)
    {
        var info = new ProcessStartInfo
        {
            FileName = fileName,
            UseShellExecute = elevate,
            Verb = elevate ? "runas" : ""
        };
        foreach (var argument in arguments) info.ArgumentList.Add(argument);

        using var process = Process.Start(info);
        if (process is null) return -1;
        await process.WaitForExitAsync();
        return process.ExitCode;
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
        StatusText.Text = online ? "Online" : "Offline";
        StatusDot.Fill = new SolidColorBrush(online ? Color.FromRgb(54, 190, 135) : Color.FromRgb(135, 145, 160));

        var serviceInstalled = IsServiceInstalled();
        ServiceText.Text = serviceInstalled
            ? "Boot service installed · NightSync can start before anyone signs in."
            : "Boot service not installed · NightSync starts only when you launch it.";

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
                HealthText.Text = "NightSync is running on this PC. Remote access and automatic file transfers are not enabled in this development build.";
            }
            catch { HealthText.Text = "Server responded, but its status details could not be read."; }
        }
        else
        {
            VersionText.Text = "—";
            UptimeText.Text = "—";
            HealthText.Text = "NightSync is not running. Select Start server to launch it.";
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
            UsedText.Text = "Used " + FormatBytes(drive.TotalSize - drive.AvailableFreeSpace);
            FreeText.Text = FormatBytes(drive.AvailableFreeSpace);
        }
        catch { UsedText.Text = "Used unavailable"; FreeText.Text = "Unavailable"; }
    }

    private static string FormatBytes(long value)
    {
        string[] units = ["B", "KB", "GB", "TB"];
        double n = value;
        var i = 0;
        while (n >= 1024 && i < units.Length - 1) { n /= 1024; i++; }
        return $"{n:0.0} {units[i]}";
    }

    private async Task WaitForServerStateAsync(bool shouldBeOnline)
    {
        for (var i = 0; i < 30; i++)
        {
            if (await IsOnlineAsync() == shouldBeOnline) return;
            await Task.Delay(500);
        }
    }

    private async Task StartServerAsync()
    {
        try { SaveConfig(); }
        catch (Exception ex) { ControlMessage.Text = "Save your settings first: " + ex.Message; return; }

        if (await IsOnlineAsync()) { ControlMessage.Text = "NightSync is already running."; return; }

        if (IsServiceInstalled())
        {
            var result = await RunToolAsync("sc.exe", ["start", ServiceName], true);
            await WaitForServerStateAsync(true);
            ControlMessage.Text = await IsOnlineAsync()
                ? "NightSync service is running."
                : $"Windows could not start the service (code {result}). Check the activity log.";
            await RefreshStatusAsync();
            return;
        }

        if (!File.Exists(_config.ServerExe)) { ControlMessage.Text = "Choose a valid NightSync server application first."; return; }
        var psi = new ProcessStartInfo(_config.ServerExe, BuildArguments())
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            WorkingDirectory = Path.GetDirectoryName(_config.ServerExe) ?? AppContext.BaseDirectory
        };
        _ownedServer = new Process { StartInfo = psi, EnableRaisingEvents = true };
        if (!_ownedServer.Start()) { ControlMessage.Text = "Windows could not start NightSync."; return; }

        ControlMessage.Text = "Starting NightSync…";
        for (var i = 0; i < 20; i++)
        {
            await Task.Delay(500);
            if (await IsOnlineAsync()) { ControlMessage.Text = "NightSync is ready."; await RefreshStatusAsync(); return; }
            if (_ownedServer.HasExited) { ControlMessage.Text = $"NightSync stopped unexpectedly (code {_ownedServer.ExitCode}). Check the activity log."; break; }
        }
        await RefreshStatusAsync();
    }

    private async Task StopServerAsync()
    {
        if (IsServiceInstalled())
        {
            var result = await RunToolAsync("sc.exe", ["stop", ServiceName], true);
            await WaitForServerStateAsync(false);
            ControlMessage.Text = await IsOnlineAsync()
                ? $"NightSync is still responding. Windows returned code {result}."
                : "NightSync service has stopped.";
            await RefreshStatusAsync();
            return;
        }

        if (_ownedServer is null || _ownedServer.HasExited)
        {
            ControlMessage.Text = "This dashboard did not start the running process. Use its original launch method to stop it.";
            return;
        }
        try
        {
            _ownedServer.Kill(entireProcessTree: true);
            await _ownedServer.WaitForExitAsync();
            ControlMessage.Text = "NightSync has stopped.";
        }
        catch (Exception ex) { ControlMessage.Text = "Could not stop NightSync: " + ex.Message; }
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

    private async void InstallService_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            SaveConfig();
            if (!File.Exists(_config.ServerExe))
            {
                ControlMessage.Text = "Choose the NightSync server application first.";
                return;
            }

            Directory.CreateDirectory(Path.GetDirectoryName(LogPath)!);
            var aclCode = await RunToolAsync("icacls.exe",
                [_config.MainPath, "/grant", "*S-1-5-18:(OI)(CI)M"], true);
            if (aclCode != 0)
            {
                ControlMessage.Text = $"Could not grant the Windows service access to main storage (code {aclCode}).";
                return;
            }

            var serviceArgs = new List<string>
            {
                IsServiceInstalled() ? "config" : "create",
                ServiceName,
                "binPath=", BuildServiceCommandLine(),
                "start=", "delayed-auto",
                "obj=", "LocalSystem"
            };
            if (!IsServiceInstalled())
            {
                serviceArgs.Add("DisplayName=");
                serviceArgs.Add("NightSync Server");
            }

            var configCode = await RunToolAsync("sc.exe", serviceArgs, true);
            if (configCode != 0)
            {
                ControlMessage.Text = $"Windows could not register NightSync as a service (code {configCode}).";
                return;
            }

            await RunToolAsync("sc.exe", ["description", ServiceName, "NightSync private cloud server"], true);
            await RunToolAsync("sc.exe", ["failure", ServiceName, "reset=", "86400", "actions=", "restart/5000/restart/10000/restart/30000"], true);
            await RunToolAsync("sc.exe", ["failureflag", ServiceName, "1"], true);

            var startCode = await RunToolAsync("sc.exe", ["start", ServiceName], true);
            await WaitForServerStateAsync(true);
            ControlMessage.Text = await IsOnlineAsync()
                ? "Boot service installed. NightSync will start with Windows, before sign-in."
                : $"Service is installed, but did not become ready (start code {startCode}). Check the activity log.";
            await RefreshStatusAsync();
        }
        catch (System.ComponentModel.Win32Exception)
        {
            ControlMessage.Text = "Administrator approval was cancelled. No service changes were made.";
        }
        catch (Exception ex)
        {
            ControlMessage.Text = "Could not install the boot service: " + ex.Message;
        }
    }

    private async void RemoveService_Click(object sender, RoutedEventArgs e)
    {
        if (!IsServiceInstalled())
        {
            ControlMessage.Text = "The boot service is not installed.";
            return;
        }

        try
        {
            await RunToolAsync("sc.exe", ["stop", ServiceName], true);
            await Task.Delay(1200);
            var result = await RunToolAsync("sc.exe", ["delete", ServiceName], true);
            ControlMessage.Text = result == 0
                ? "Boot service removed. Your NightSync files and settings are untouched."
                : $"Windows could not remove the boot service (code {result}).";
            await RefreshStatusAsync();
        }
        catch (System.ComponentModel.Win32Exception)
        {
            ControlMessage.Text = "Administrator approval was cancelled. No service changes were made.";
        }
        catch (Exception ex) { ControlMessage.Text = "Could not remove the boot service: " + ex.Message; }
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            SaveConfig();
            StorageMessage.Text = "Your storage locations have been saved. Backup and sync are not active yet.";
        }
        catch (Exception ex) { StorageMessage.Text = "Could not save settings: " + ex.Message; }
    }

    private void BrowseExe_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog { Filter = "NightSync server application|NightSync-Server-windows-x64.exe;*.exe|All files|*.*" };
        if (dialog.ShowDialog() == true) ExeBox.Text = dialog.FileName;
    }
    private void BrowseMain_Click(object sender, RoutedEventArgs e) => ChooseFolder(MainBox);
    private void BrowseBackup_Click(object sender, RoutedEventArgs e) => ChooseFolder(BackupBox);
    private void BrowseSync_Click(object sender, RoutedEventArgs e) => ChooseFolder(SyncBox);

    private static void ChooseFolder(System.Windows.Controls.TextBox target)
    {
        var dialog = new OpenFolderDialog { Title = "Choose a NightSync folder", Multiselect = false };
        if (dialog.ShowDialog() == true) target.Text = dialog.FolderName;
    }

    private void OpenLog_Click(object sender, RoutedEventArgs e)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(LogPath)!);
        if (!File.Exists(LogPath)) File.WriteAllText(LogPath, "NightSync activity will appear here.\n");
        Process.Start(new ProcessStartInfo(LogPath) { UseShellExecute = true });
    }

    private void LoadLog()
    {
        try
        {
            if (!File.Exists(LogPath)) { LogBox.Text = "No activity yet."; return; }
            LogBox.Text = string.Join(Environment.NewLine, File.ReadAllLines(LogPath).TakeLast(180));
            LogBox.ScrollToEnd();
        }
        catch { LogBox.Text = "Activity log is unavailable."; }
    }
}