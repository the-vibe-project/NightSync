using System;
using System.Windows;
namespace NightSync.Desktop;
public partial class ClientConnectWindow : Window {
 public ClientConnectWindow() { InitializeComponent(); }
 private void Continue_Click(object sender, RoutedEventArgs e) {
  if (!Uri.TryCreate(HostAddress.Text.Trim(), UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps) {
   MessageBox.Show("Enter a valid HTTPS address.", "NightSync"); return;
  }
  MessageBox.Show("Address format is valid. Remote connection and sign-in will be added later.", "NightSync development build");
 }
}
