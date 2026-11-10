using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Reflection;
using System.Text;
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
        public string MainPath { get; set; } = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "NightSync Storage");
        public string BackupPath { get; set; } = "";
        public string SyncPath { get; set; } = "";
        public bool SetupComplete { get; set; }
    }

    private readonly string _root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NightSync");
    private readonly string _programRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "NightSync");
    private string ConfigPath => Path.Combine(_root, "server-ui.json");
    private string UserLogPath => Path.Combine(_root, "server.log");
    private string ServiceLogPath => Path.Combine(_programRoot, "server.log");
    private string EnginePath => Path.Combine(_programRoot, "Engine", "NightSync.Server.Engine.exe");
    private string CurrentLogPath => IsServiceInstalled() && File.Exists(ServiceLogPath) ? ServiceLogPath : UserLogPath;

    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(2) };
    private readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromSeconds(5) };
    private Config _config = new();
    private bool _wizardRunning;

    public MainWindow()
    {
        InitializeComponent();
        Directory.CreateDirectory(_root);
        LoadConfig();
        _timer.Tick += async (_, _) => await RefreshStatusAsync();
        _timer.Start();
        Loaded += async (_, _) =>
        {
            await RefreshStatusAsync();
            if (!_config.SetupComplete) await RunSetupWizardAsync();
        };
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

        MainBox.Text = _config.MainPath;
        BackupBox.Text = _config.BackupPath;
        SyncBox.Text = _config.SyncPath;
        LoadLog();
    }

    private void SaveConfig()
    {
        _config.MainPath = MainBox.Text.Trim();
        _config.BackupPath = BackupBox.Text.Trim();
        _config.SyncPath = SyncBox.Text.Trim();

        if (string.IsNullOrWhiteSpace(_config.MainPath))
            throw new InvalidOperationException("Choose a main storage folder first.");

        Directory.CreateDirectory(_config.MainPath);
        if (!string.IsNullOrWhiteSpace(_config.BackupPath)) Directory.CreateDirectory(_config.BackupPath);
        if (!string.IsNullOrWhiteSpace(_config.SyncPath)) Directory.CreateDirectory(_config.SyncPath);

        Directory.CreateDirectory(_root);
        File.WriteAllText(ConfigPath, JsonSerializer.Serialize(_config, new JsonSerializerOptions { WriteIndented = true }));
    }

    private static string Quote(string value) => "\"" + value.Replace("\"", "\\\"") + "\"";
    private static string PowerShellQuote(string value) => "'" + value.Replace("'", "''") + "'";

    private string BuildServiceCommandLine()
    {
        var args = new StringBuilder();
        args.Append(Quote(EnginePath));
        args.Append(" --storage ").Append(Quote(_config.MainPath));
        args.Append(" --log-file ").Append(Quote(ServiceLogPath));
        if (!string.IsNullOrWhiteSpace(_config.BackupPath)) args.Append(" --backup ").Append(Quote(_config.BackupPath));
        if (!string.IsNullOrWhiteSpace(_config.SyncPath)) args.Append(" --sync ").Append(Quote(_config.SyncPath));
        return args.ToString();
    }

    private static string QuoteProcessArgument(string argument)
    {
        if (argument.Length > 0 && !argument.Any(char.IsWhiteSpace) && !argument.Contains('"'))
            return argument;

        var result = new StringBuilder("\"");
        var backslashes = 0;
        foreach (var character in argument)
        {
            if (character == '\\')
            {
                backslashes++;
            }
            else if (character == '"')
            {
                result.Append('\\', backslashes * 2 + 1);
                result.Append('"');
                backslashes = 0;
            }
            else
            {
                result.Append('\\', backslashes);
                result.Append(character);
                backslashes = 0;
            }
        }
        result.Append('\\', backslashes * 2);
        result.Append('"');
        return result.ToString();
    }

    private static async Task<int> RunToolAsync(string fileName, IEnumerable<string> arguments, bool elevate)
    {
        var info = new ProcessStartInfo
        {
            FileName = fileName,
            UseShellExecute = elevate
        };
        if (elevate)
        {
            info.Verb = "runas";
            info.Arguments = string.Join(" ", arguments.Select(QuoteProcessArgument));
        }
        else
        {
            foreach (var argument in arguments) info.ArgumentList.Add(argument);
        }

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

    private async Task<bool> WaitForServerStateAsync(bool shouldBeOnline, int timeoutSeconds = 18)
    {
        for (var i = 0; i < timeoutSeconds * 2; i++)
        {
            if (await IsOnlineAsync() == shouldBeOnline) return true;
            await Task.Delay(500);
        }
        return false;
    }

    private async Task RefreshStatusAsync()
    {
        var online = await IsOnlineAsync();
        StatusText.Text = online ? "Server online" : "Server offline";
        StatusDot.Fill = new SolidColorBrush(online ? Color.FromRgb(75, 226, 184) : Color.FromRgb(135, 145, 160));

        var serviceInstalled = IsServiceInstalled();
        ServiceText.Text = serviceInstalled
            ? "Background service installed · Starts with Windows before sign-in."
            : "Background service is not installed · Complete setup to keep NightSync running after sign-out.";

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
                HealthText.Text = "NightSync is running in the background on this PC. This dashboard can be closed without stopping the server.";
            }
            catch { HealthText.Text = "Server responded, but its status details could not be read."; }
        }
        else
        {
            VersionText.Text = "—";
            UptimeText.Text = "—";
            HealthText.Text = serviceInstalled
                ? "The background service is installed but is not responding. Select Start to try again."
                : "The server is not running. Finish the setup wizard to install background startup.";
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
        double n = Math.Max(0, value);
        var i = 0;
        while (n >= 1024 && i < units.Length - 1) { n /= 1024; i++; }
        return $"{n:0.0} {units[i]}";
    }

    private async Task<bool> InstallBackgroundServiceAsync()
    {
        SaveConfig();
        ControlMessage.Text = "Preparing the NightSync engine…";

        var resource = Assembly.GetExecutingAssembly().GetManifestResourceStream("NightSync.Server.Engine.exe");
        if (resource is null)
            throw new InvalidOperationException("The bundled server engine is missing. Download the complete NightSync Server package again.");

        var stagedEngine = Path.Combine(Path.GetTempPath(), "NightSync.Server.Engine." + Guid.NewGuid().ToString("N") + ".exe");
        try
        {
            await using (resource)
            await using (var output = File.Create(stagedEngine))
                await resource.CopyToAsync(output);

            var engineFolder = Path.GetDirectoryName(EnginePath)!;
            var commandLine = BuildServiceCommandLine();
            var script = "$ErrorActionPreference = 'Stop'; " +
                "$serviceName = " + PowerShellQuote(ServiceName) + "; " +
                "$service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue; " +
                "if ($null -ne $service -and $service.Status -ne 'Stopped') { " +
                    "& sc.exe stop $serviceName | Out-Null; " +
                    "$deadline = (Get-Date).AddSeconds(25); " +
                    "do { Start-Sleep -Milliseconds 500; $service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue; } " +
                    "while ($null -ne $service -and $service.Status -ne 'Stopped' -and (Get-Date) -lt $deadline); " +
                    "if ($null -ne $service -and $service.Status -ne 'Stopped') { throw 'The existing NightSync service did not stop.' } " +
                "} " +
                "New-Item -ItemType Directory -Force -Path " + PowerShellQuote(_programRoot) + " | Out-Null; " +
                "New-Item -ItemType Directory -Force -Path " + PowerShellQuote(engineFolder) + " | Out-Null; " +
                "Copy-Item -LiteralPath " + PowerShellQuote(stagedEngine) + " -Destination " + PowerShellQuote(EnginePath) + " -Force; " +
                "if (-not (Test-Path -LiteralPath " + PowerShellQuote(EnginePath) + ")) { throw 'The NightSync engine file was not installed.' }; " +
                "& icacls.exe " + PowerShellQuote(_config.MainPath) + " '/grant' '*S-1-5-18:(OI)(CI)M' '/T' '/C' | Out-Null; " +
                "if ($LASTEXITCODE -ne 0) { throw 'Windows could not grant the service access to the storage folder.' }; " +
                "if ($null -ne $service) { " +
                    "& sc.exe config $serviceName 'binPath=' " + PowerShellQuote(commandLine) + " 'start=' 'delayed-auto' 'obj=' 'LocalSystem' | Out-Null; " +
                "} else { " +
                    "& sc.exe create $serviceName 'binPath=' " + PowerShellQuote(commandLine) + " 'start=' 'delayed-auto' 'obj=' 'LocalSystem' 'DisplayName=' 'NightSync Server' | Out-Null; " +
                "} " +
                "if ($LASTEXITCODE -ne 0) { throw 'Windows could not register the NightSync background service.' }; " +
                "& sc.exe description $serviceName 'NightSync private cloud server' | Out-Null; " +
                "if ($LASTEXITCODE -ne 0) { throw 'Windows could not set the NightSync service description.' }; " +
                "& sc.exe failure $serviceName 'reset=' '86400' 'actions=' 'restart/5000/restart/10000/restart/30000' | Out-Null; " +
                "if ($LASTEXITCODE -ne 0) { throw 'Windows could not configure NightSync service recovery.' }; " +
                "& sc.exe failureflag $serviceName '1' | Out-Null; " +
                "if ($LASTEXITCODE -ne 0) { throw 'Windows could not enable NightSync service recovery.' }; " +
                "& sc.exe start $serviceName | Out-Null; " +
                "if ($LASTEXITCODE -ne 0) { throw 'Windows could not start the NightSync background service.' }";

            var encoded = Convert.ToBase64String(Encoding.Unicode.GetBytes(script));
            ControlMessage.Text = "Installing the engine and enabling background startup…";
            var installCode = await RunToolAsync("powershell.exe",
                ["-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded], true);
            if (installCode != 0)
                throw new InvalidOperationException($"Windows could not complete background service setup (code {installCode}).");
        }
        finally
        {
            try { if (File.Exists(stagedEngine)) File.Delete(stagedEngine); } catch { }
        }

        ControlMessage.Text = "Checking the background service…";
        var online = await WaitForServerStateAsync(true, 20);
        if (!online)
            throw new InvalidOperationException("The background service was installed, but NightSync did not become ready. Check the activity log.");

        ControlMessage.Text = "Background service is installed and running.";
        await RefreshStatusAsync();
        return true;
    }

    private async Task CreateOwnerAccountAsync(string username, string password)
    {
        using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(20) };
        var json = JsonSerializer.Serialize(new { username, password });
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var response = await client.PostAsync("http://127.0.0.1:8787/api/v1/setup", content);
        var responseText = await response.Content.ReadAsStringAsync();

        if (response.StatusCode == System.Net.HttpStatusCode.Created)
        {
            _config.SetupComplete = true;
            SaveConfig();
            ControlMessage.Text = "NightSync is ready. Your owner account has been created.";
            return;
        }

        if (response.StatusCode == System.Net.HttpStatusCode.Conflict)
        {
            _config.SetupComplete = true;
            SaveConfig();
            ControlMessage.Text = "An existing account database was found. Sign in with the account you already use.";
            return;
        }

        var reason = responseText;
        try
        {
            using var document = JsonDocument.Parse(responseText);
            reason = document.RootElement.TryGetProperty("error", out var error)
                ? error.GetString() ?? responseText
                : responseText;
        }
        catch { }

        throw new InvalidOperationException("Could not create the owner account: " + reason);
    }

    private async Task RunSetupWizardAsync()
    {
        if (_wizardRunning) return;
        _wizardRunning = true;
        try
        {
            var wizard = new SetupWizardWindow(_config.MainPath) { Owner = this };
            if (wizard.ShowDialog() != true) return;

            MainBox.Text = wizard.StoragePath;
            SaveConfig();
            try
            {
                await InstallBackgroundServiceAsync();
                await CreateOwnerAccountAsync(wizard.OwnerUsername, wizard.OwnerPassword);
            }
            catch (System.ComponentModel.Win32Exception)
            {
                ControlMessage.Text = "Administrator approval was cancelled. The background service was not installed.";
            }
            catch (Exception ex)
            {
                ControlMessage.Text = "Setup needs attention: " + ex.Message;
                MessageBox.Show(ex.Message, "NightSync setup", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }
        finally { _wizardRunning = false; }
    }

    private async Task StartServerAsync()
    {
        if (!IsServiceInstalled())
        {
            ControlMessage.Text = "Install the background service first. Open the setup wizard.";
            return;
        }
        try
        {
            var result = await RunToolAsync("sc.exe", ["start", ServiceName], true);
            var online = await WaitForServerStateAsync(true, 20);
            ControlMessage.Text = online
                ? "NightSync is running in the background."
                : $"Windows could not start the service (code {result}). Check the activity log.";
            await RefreshStatusAsync();
        }
        catch (System.ComponentModel.Win32Exception)
        {
            ControlMessage.Text = "Administrator approval was cancelled. NightSync was not started.";
        }
        catch (Exception ex) { ControlMessage.Text = "Could not start NightSync: " + ex.Message; }
    }

    private async Task StopServerAsync()
    {
        if (!IsServiceInstalled())
        {
            ControlMessage.Text = "The background service is not installed.";
            return;
        }
        try
        {
            var result = await RunToolAsync("sc.exe", ["stop", ServiceName], true);
            var stopped = await WaitForServerStateAsync(false, 18);
            ControlMessage.Text = stopped ? "NightSync has stopped." : $"Stop requested. Windows returned code {result}.";
            await RefreshStatusAsync();
        }
        catch (System.ComponentModel.Win32Exception)
        {
            ControlMessage.Text = "Administrator approval was cancelled. NightSync is unchanged.";
        }
        catch (Exception ex) { ControlMessage.Text = "Could not stop NightSync: " + ex.Message; }
    }

    private async void Start_Click(object sender, RoutedEventArgs e) => await StartServerAsync();
    private async void Stop_Click(object sender, RoutedEventArgs e) => await StopServerAsync();
    private async void Restart_Click(object sender, RoutedEventArgs e)
    {
        await StopServerAsync();
        if (!await WaitForServerStateAsync(false, 10)) return;
        await StartServerAsync();
    }
    private async void Refresh_Click(object sender, RoutedEventArgs e) => await RefreshStatusAsync();

    private async void InstallService_Click(object sender, RoutedEventArgs e)
    {
        try { await InstallBackgroundServiceAsync(); }
        catch (System.ComponentModel.Win32Exception)
        {
            ControlMessage.Text = "Administrator approval was cancelled. No service changes were made.";
        }
        catch (Exception ex)
        {
            ControlMessage.Text = "Could not enable background startup: " + ex.Message;
            MessageBox.Show(ex.Message, "NightSync Server", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private async void RemoveService_Click(object sender, RoutedEventArgs e)
    {
        if (!IsServiceInstalled())
        {
            ControlMessage.Text = "The background service is not installed.";
            return;
        }

        if (MessageBox.Show("Remove automatic background startup? Your files and settings will remain untouched.",
            "Remove background service", MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;

        try
        {
            await RunToolAsync("sc.exe", ["stop", ServiceName], true);
            await WaitForServerStateAsync(false, 12);
            var result = await RunToolAsync("sc.exe", ["delete", ServiceName], true);
            ControlMessage.Text = result == 0
                ? "Background service removed. Your NightSync files are untouched."
                : $"Windows could not remove the service (code {result}).";
            _config.SetupComplete = false;
            SaveConfig();
            await RefreshStatusAsync();
        }
        catch (System.ComponentModel.Win32Exception)
        {
            ControlMessage.Text = "Administrator approval was cancelled. No service changes were made.";
        }
        catch (Exception ex) { ControlMessage.Text = "Could not remove the service: " + ex.Message; }
    }

    private async void OpenWizard_Click(object sender, RoutedEventArgs e) => await RunSetupWizardAsync();

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            SaveConfig();
            StorageMessage.Text = IsServiceInstalled()
                ? "Storage settings saved. Re-enable the background service to apply a changed main storage location."
                : "Storage settings saved.";
        }
        catch (Exception ex) { StorageMessage.Text = "Could not save settings: " + ex.Message; }
    }

    private void BrowseMain_Click(object sender, RoutedEventArgs e) => ChooseFolder(MainBox);
    private void BrowseBackup_Click(object sender, RoutedEventArgs e) => ChooseFolder(BackupBox);
    private void BrowseSync_Click(object sender, RoutedEventArgs e) => ChooseFolder(SyncBox);

    private static void ChooseFolder(System.Windows.Controls.TextBox target)
    {
        var dialog = new OpenFolderDialog { Title = "Choose a NightSync folder", Multiselect = false };
        if (Directory.Exists(target.Text)) dialog.FolderName = target.Text;
        if (dialog.ShowDialog() == true) target.Text = dialog.FolderName;
    }

    private void OpenLog_Click(object sender, RoutedEventArgs e)
    {
        var logPath = CurrentLogPath;
        Directory.CreateDirectory(Path.GetDirectoryName(logPath)!);
        if (!File.Exists(logPath)) File.WriteAllText(logPath, "NightSync activity will appear here.\n");
        Process.Start(new ProcessStartInfo(logPath) { UseShellExecute = true });
    }

    private void LoadLog()
    {
        try
        {
            var logPath = CurrentLogPath;
            if (!File.Exists(logPath)) { LogBox.Text = "No activity yet."; return; }
            LogBox.Text = string.Join(Environment.NewLine, File.ReadAllLines(logPath).TakeLast(180));
            LogBox.ScrollToEnd();
        }
        catch { LogBox.Text = "Activity log is unavailable."; }
    }
}