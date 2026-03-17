using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace VirtualFunds.WPF.Converters;

/// <summary>
/// Converts a <see cref="bool"/> to <see cref="Visibility"/> with inverse logic:
/// <c>true</c> → <see cref="Visibility.Collapsed"/>;
/// <c>false</c> → <see cref="Visibility.Visible"/>.
/// Used to hide UI elements (e.g., the submit button) while IsLoading is true.
/// </summary>
[ValueConversion(typeof(bool), typeof(Visibility))]
public sealed class InverseBooleanToVisibilityConverter : IValueConverter
{
    /// <inheritdoc />
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        => value is true ? Visibility.Collapsed : Visibility.Visible;

    /// <inheritdoc />
    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
