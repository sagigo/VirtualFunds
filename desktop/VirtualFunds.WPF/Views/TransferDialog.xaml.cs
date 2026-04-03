using System.Globalization;
using System.Windows;
using VirtualFunds.Core.Models;

namespace VirtualFunds.WPF.Views;

/// <summary>
/// A modal dialog for transferring money between funds within a portfolio (E6.10).
/// <para>
/// The source fund is fixed (pre-selected from context menu). The user picks
/// a destination fund from a dropdown and enters a shekel amount.
/// </para>
/// </summary>
public partial class TransferDialog : Window
{
    /// <summary>The selected destination fund. Only valid when <c>DialogResult == true</c>.</summary>
    public FundListItem? DestinationFund { get; private set; }

    /// <summary>The transfer amount in agoras. Only valid when <c>DialogResult == true</c>.</summary>
    public long AmountAgoras { get; private set; }

    /// <summary>
    /// Initializes the transfer dialog.
    /// </summary>
    /// <param name="sourceFund">The fund to transfer from (shown as read-only label).</param>
    /// <param name="otherFunds">The list of eligible destination funds (excludes the source).</param>
    public TransferDialog(FundListItem sourceFund, IReadOnlyList<FundListItem> otherFunds)
    {
        InitializeComponent();

        SourceFundText.Text = sourceFund.Name;
        DestinationComboBox.ItemsSource = otherFunds;

        if (otherFunds.Count > 0)
            DestinationComboBox.SelectedIndex = 0;

        Loaded += (_, _) =>
        {
            AmountTextBox.Focus();
            AmountTextBox.SelectAll();
        };
    }

    /// <summary>
    /// OK button click: validates destination selection and amount, then closes with a positive result.
    /// </summary>
    private void OkButton_Click(object sender, RoutedEventArgs e)
    {
        // Validate destination selection.
        if (DestinationComboBox.SelectedItem is not FundListItem destination)
        {
            ShowError("נא לבחור קרן יעד.");
            return;
        }

        // Validate amount.
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

        DestinationFund = destination;
        AmountAgoras = (long)(shekelAmount * 100);
        DialogResult = true;
    }

    /// <summary>
    /// Shows a validation error message.
    /// </summary>
    private void ShowError(string message)
    {
        HintText.Text = message;
        HintText.Foreground = System.Windows.Media.Brushes.Red;
    }
}
