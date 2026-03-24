using System.Windows;

namespace VirtualFunds.WPF.Views;

/// <summary>
/// A simple modal dialog for entering or editing a portfolio name.
/// Used for both "Create portfolio" and "Rename portfolio" flows.
/// <para>
/// No ViewModel is needed — the dialog is a lightweight input form with a single text field.
/// The caller sets <see cref="PortfolioName"/> and <see cref="Title"/>, calls
/// <see cref="Window.ShowDialog"/>, and reads the result.
/// </para>
/// </summary>
public partial class NameInputDialog : Window
{
    /// <summary>
    /// The name entered by the user.
    /// Set before showing the dialog to pre-fill (e.g., for rename), or leave empty for create.
    /// Read after the dialog closes with <c>DialogResult == true</c>.
    /// </summary>
    public string InputName
    {
        get => NameTextBox.Text;
        set => NameTextBox.Text = value;
    }

    /// <summary>Initializes the dialog.</summary>
    public NameInputDialog()
    {
        InitializeComponent();

        // Focus the text box and select all text so the user can start typing immediately.
        Loaded += (_, _) =>
        {
            NameTextBox.Focus();
            NameTextBox.SelectAll();
        };
    }

    /// <summary>
    /// OK button click: closes the dialog with a positive result.
    /// </summary>
    private void OkButton_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = true;
    }
}
