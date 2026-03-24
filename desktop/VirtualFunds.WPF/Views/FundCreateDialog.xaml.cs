using System.Globalization;
using System.Windows;

namespace VirtualFunds.WPF.Views;

/// <summary>
/// A modal dialog for creating a new fund with a name and optional initial amount.
/// <para>
/// No ViewModel is needed — the dialog is a lightweight input form.
/// The caller reads <see cref="FundName"/> and <see cref="AmountAgoras"/> after the dialog
/// closes with <c>DialogResult == true</c>.
/// </para>
/// </summary>
public partial class FundCreateDialog : Window
{
    /// <summary>
    /// The fund name entered by the user.
    /// </summary>
    public string FundName
    {
        get => NameTextBox.Text;
        set => NameTextBox.Text = value;
    }

    /// <summary>
    /// The initial amount in agoras, parsed from the shekel input.
    /// Defaults to 0 if the amount field is empty.
    /// </summary>
    public long AmountAgoras { get; private set; }

    /// <summary>Initializes the dialog.</summary>
    public FundCreateDialog()
    {
        InitializeComponent();

        // Focus the name text box and select all text so the user can start typing immediately.
        Loaded += (_, _) =>
        {
            NameTextBox.Focus();
            NameTextBox.SelectAll();
        };
    }

    /// <summary>
    /// OK button click: validates the amount field and closes the dialog with a positive result.
    /// The amount field accepts shekel values (e.g. "150.50") and converts to agoras (× 100).
    /// Empty amount is treated as 0.
    /// </summary>
    private void OkButton_Click(object sender, RoutedEventArgs e)
    {
        var amountText = AmountTextBox.Text.Trim();

        // Empty amount = 0 agoras (no initial balance).
        if (string.IsNullOrEmpty(amountText))
        {
            AmountAgoras = 0;
            DialogResult = true;
            return;
        }

        // Try to parse the shekel amount (e.g. "150.50" or "150").
        if (!decimal.TryParse(amountText, NumberStyles.AllowDecimalPoint, CultureInfo.InvariantCulture, out var shekelAmount))
        {
            ShowAmountError("נא להזין מספר חוקי.");
            return;
        }

        if (shekelAmount < 0)
        {
            ShowAmountError("הסכום אינו יכול להיות שלילי.");
            return;
        }

        // Reject more than 2 decimal places (agoras are the smallest unit).
        var fractionalPart = shekelAmount % 1;
        if (fractionalPart != 0 && decimal.Round(fractionalPart, 2) != fractionalPart)
        {
            ShowAmountError("ניתן להזין עד שתי ספרות אחרי הנקודה.");
            return;
        }

        AmountAgoras = (long)(shekelAmount * 100);
        DialogResult = true;
    }

    /// <summary>
    /// Shows a validation error message in the hint area below the amount field.
    /// </summary>
    private void ShowAmountError(string message)
    {
        AmountHintText.Text = message;
        AmountHintText.Foreground = System.Windows.Media.Brushes.Red;
        AmountTextBox.Focus();
        AmountTextBox.SelectAll();
    }
}
