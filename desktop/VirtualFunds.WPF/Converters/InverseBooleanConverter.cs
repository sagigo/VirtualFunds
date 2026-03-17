using System.Globalization;
using System.Windows.Data;

namespace VirtualFunds.WPF.Converters;

/// <summary>
/// Converts a <see cref="bool"/> to its inverse.
/// <c>true</c> → <c>false</c>; <c>false</c> → <c>true</c>.
/// Used to bind <c>IsEnabled</c> to the inverse of <c>IsLoading</c>.
/// </summary>
[ValueConversion(typeof(bool), typeof(bool))]
public sealed class InverseBooleanConverter : IValueConverter
{
    /// <inheritdoc />
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        => value is not true;

    /// <inheritdoc />
    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => value is not true;
}
