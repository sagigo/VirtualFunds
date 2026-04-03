using System.Globalization;
using System.Windows;

namespace VirtualFunds.WPF.Views;

/// <summary>
/// A modal dialog for entering a shekel amount (used for deposit and withdrawal).
/// <para>
/// No ViewModel is needed — the dialog is a lightweight input form.
/// The caller reads <see cref="AmountAgoras"/> after the dialog closes with
/// <c>DialogResult == true</c>.
/// </para>
/// </summary>
public partial class AmountInputDialog : Window
{
    /// <summary>
    /// The amount in agoras, parsed from the shekel input.
    /// Only valid when <c>DialogResult == true</c>.
    /// </summary>
    public long AmountAgoras { get; private set; }

    /// <summary>Initializes the dialog.</summary>
    public AmountInputDialog()
    {
        InitializeComponent();

        // Focus the amount text box so the user can start typing immediately.
        Loaded += (_, _) =>
        {
            AmountTextBox.Focus();
            AmountTextBox.SelectAll();
        };
    }

    /// <summary>
    /// OK button click: validates the amount and closes the dialog with a positive result.
    /// The field accepts shekel values (e.g. "150.50") and converts to agoras (x 100).
    /// The amount must be positive (greater than zero).
    /// </summary>
    private void OkButton_Click(object sender, RoutedEventArgs e)
    {
        var amountText = AmountTextBox.Text.Trim();

        if (string.IsNullOrEmpty(amountText))
        {
            ShowError("נא להזין סכום.");
            return;
        }

        if (!decimal.TryParse(amountText, NumberStyles.AllowDecimalPoint, CultureInfo.InvariantCulture, out var shekelAmount))
        {
            ShowError("נא להזין מספר חוקי.");
            return;
        }

        if (shekelAmount <= 0)
        {
            ShowError("הסכום חייב להיות גדול מאפס.");
            return;
        }

        // Reject more than 2 decimal places (agoras are the smallest unit).
        var fractionalPart = shekelAmount % 1;
        if (fractionalPart != 0 && decimal.Round(fractionalPart, 2) != fractionalPart)
        {
            ShowError("ניתן להזין עד שתי ספרות אחרי הנקודה.");
            return;
        }

        AmountAgoras = (long)(shekelAmount * 100);
        DialogResult = true;
    }

    /// <summary>
    /// Shows a validation error message below the amount field.
    /// </summary>
    private void ShowError(string message)
    {
        HintText.Text = message;
        HintText.Foreground = System.Windows.Media.Brushes.Red;
        AmountTextBox.Focus();
        AmountTextBox.SelectAll();
    }
}
