using System.Windows;
namespace NightSync.Desktop;
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        while (true)
        {
            var login = new LoginWindow();
            if (login.ShowDialog() != true) { Shutdown(); return; }
            if (AppSession.MustChangePassword)
            {
                var change = new ChangePasswordWindow();
                if (change.ShowDialog() != true) { Shutdown(); return; }
                MessageBox.Show("Password changed. Please sign in again with your new password.", "NightSync");
                continue;
            }
            var main = new MainWindow();
            MainWindow = main;
            main.Show();
            return;
        }
    }
}
