using System.Windows;
namespace NightSync.Desktop;
public partial class MainWindow : Window {
 public MainWindow() { InitializeComponent(); }
 private void Host_Click(object sender, RoutedEventArgs e) { new HostSetupWindow { Owner = this }.ShowDialog(); }
 private void Client_Click(object sender, RoutedEventArgs e) { new ClientConnectWindow { Owner = this }.ShowDialog(); }
}
