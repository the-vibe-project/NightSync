using System.IO;
using System.Windows;
using Microsoft.Win32;

namespace NightSync.ServerUI;

public partial class SetupWizardWindow : Window
{
    private int _step;

    public string StoragePath => StoragePathBox.Text.Trim();
    public bool EnableBackgroundService => BackgroundServiceCheck.IsChecked == true;

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
        StepText.Text = $"STEP 0{_step + 1} / 03";
        ProgressTwo.Fill = _step >= 1 ? System.Windows.Media.Brushes.DeepSkyBlue : new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(38, 57, 79));
        ProgressThree.Fill = _step >= 2 ? System.Windows.Media.Brushes.DeepSkyBlue : new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(38, 57, 79));
        BackButton.Visibility = _step == 0 ? Visibility.Collapsed : Visibility.Visible;
        ContinueButton.Visibility = _step == 2 ? Visibility.Collapsed : Visibility.Visible;
        FinishButton.Visibility = _step == 2 ? Visibility.Visible : Visibility.Collapsed;
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

        _step = Math.Min(2, _step + 1);
        UpdateStep();
    }

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        _step = Math.Max(0, _step - 1);
        UpdateStep();
    }

    private void Finish_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(StoragePath))
        {
            MessageBox.Show("Choose a folder for your NightSync storage.", "NightSync setup", MessageBoxButton.OK, MessageBoxImage.Information);
            _step = 1;
            UpdateStep();
            return;
        }
        try { Directory.CreateDirectory(StoragePath); }
        catch (Exception ex)
        {
            MessageBox.Show("NightSync could not use that folder: " + ex.Message, "NightSync setup", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        DialogResult = true;
    }
}