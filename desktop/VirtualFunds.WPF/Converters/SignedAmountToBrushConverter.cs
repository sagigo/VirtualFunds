using System.Globalization;
using System.Windows.Data;
using System.Windows.Media;

namespace VirtualFunds.WPF.Converters;

/// <summary>
/// Converts a signed agora amount (<see cref="long"/>) to a <see cref="Brush"/>.
/// Non-negative → SuccessBrush (green); negative → ErrorBrush (red).
/// Used to color the FormattedSignedAmount TextBlock in transaction history detail rows.
/// </summary>
[ValueConversion(typeof(long), typeof(Brush))]
public sealed class SignedAmountToBrushConverter : IValueConverter
{
    /// <inheritdoc />
    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is long amount)
        {
            var resources = System.Windows.Application.Current.Resources;
            return amount >= 0
                ? resources["SuccessBrush"]
                : resources["ErrorBrush"];
        }
        return null;
    }

    /// <inheritdoc />
    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
