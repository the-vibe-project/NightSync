using System.Windows;
namespace NightSync.Desktop;
public partial class HostSetupWindow : Window {
 public HostSetupWindow() { InitializeComponent(); }
 private void Continue_Click(object sender, RoutedEventArgs e) {
  MessageBox.Show("Prototype only. Storage validation, owner account creation and engine installation are not implemented yet.", "NightSync development build");
 }
}
