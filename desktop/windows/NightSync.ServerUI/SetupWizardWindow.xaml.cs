using System.IO;
using System.Windows;
using Microsoft.Win32;

namespace NightSync.ServerUI;

public partial class SetupWizardWindow : Window
{
    private int _step;

    public string StoragePath => StoragePathBox.Text.Trim();
    public bool EnableBackgroundService => true;
    public string OwnerUsername => OwnerNameBox.Text.Trim();
    public string OwnerPassword => OwnerPasswordBox.Password;

    public SetupWizardWindow(string initialStoragePath)
    {
        InitializeComponent();
        StoragePathBox.Text = string.IsNullOrWhiteSpace(initialStoragePath)
            ? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "NightSync Storage")
            : initialStoragePath;
        UpdateStep();
    }

    private void UpdateStep()
    {
        WelcomePanel.Visibility = _step == 0 ? Visibility.Visible : Visibility.Collapsed;
        StoragePanel.Visibility = _step == 1 ? Visibility.Visible : Visibility.Collapsed;
        BackgroundPanel.Visibility = _step == 2 ? Visibility.Visible : Visibility.Collapsed;
        OwnerPanel.Visibility = _step == 3 ? Visibility.Visible : Visibility.Collapsed;
        StepText.Text = $"STEP 0{_step + 1} / 04";
        ProgressTwo.Fill = _step >= 1 ? System.Windows.Media.Brushes.DeepSkyBlue : new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(38, 57, 79));
        ProgressThree.Fill = _step >= 2 ? System.Windows.Media.Brushes.DeepSkyBlue : new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(38, 57, 79));
        ProgressFour.Fill = _step >= 3 ? System.Windows.Media.Brushes.DeepSkyBlue : new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(38, 57, 79));
        BackButton.Visibility = _step == 0 ? Visibility.Collapsed : Visibility.Visible;
        ContinueButton.Visibility = _step == 3 ? Visibility.Collapsed : Visibility.Visible;
        FinishButton.Visibility = _step == 3 ? Visibility.Visible : Visibility.Collapsed;
    }

    private void Browse_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFolderDialog { Title = "Choose your NightSync storage folder", Multiselect = false };
        if (Directory.Exists(StoragePath)) dialog.FolderName = StoragePath;
        if (dialog.ShowDialog() == true) StoragePathBox.Text = dialog.FolderName;
    }

    private void Continue_Click(object sender, RoutedEventArgs e)
    {
        if (_step == 1)
        {
            if (string.IsNullOrWhiteSpace(StoragePath))
            {
                MessageBox.Show("Choose a folder for your NightSync storage.", "NightSync setup", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }
            try { Directory.CreateDirectory(StoragePath); }
            catch (Exception ex)
            {
                MessageBox.Show("NightSync could not use that folder: " + ex.Message, "NightSync setup", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }
        }

        _step = Math.Min(3, _step + 1);
        UpdateStep();
    }

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        _step = Math.Max(0, _step - 1);
        UpdateStep();
    }

    private void Finish_Click(object sender, RoutedEventArgs e)
    {
        var username = OwnerUsername;
        var password = OwnerPassword;
        var confirmation = OwnerConfirmPasswordBox.Password;

        if (username.Length < 3 || username.Length > 32 ||
            username.Any(ch => !(char.IsAsciiLetterOrDigit(ch) || ch is '_' or '-' or '.')))
        {
            MessageBox.Show("Use 3–32 characters for the owner ID: letters, numbers, dots, hyphens or underscores.",
                "NightSync setup", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        if (password.Length < 12 || password.Length > 128)
        {
            MessageBox.Show("Your password must be between 12 and 128 characters.",
                "NightSync setup", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        if (!string.Equals(password, confirmation, StringComparison.Ordinal))
        {
            MessageBox.Show("The passwords do not match. Please check them and try again.",
                "NightSync setup", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        try { Directory.CreateDirectory(StoragePath); }
        catch (Exception ex)
        {
            MessageBox.Show("NightSync could not use that folder: " + ex.Message, "NightSync setup", MessageBoxButton.OK, MessageBoxImage.Warning);
            _step = 1;
            UpdateStep();
            return;
        }
        DialogResult = true;
    }
}