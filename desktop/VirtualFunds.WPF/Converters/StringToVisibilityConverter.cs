using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace VirtualFunds.WPF.Converters;

/// <summary>
/// Converts a string to <see cref="Visibility"/>.
/// Non-empty string → <see cref="Visibility.Visible"/>;
/// null or empty string → <see cref="Visibility.Collapsed"/>.
/// Used to show/hide the error message TextBlock in AuthWindow.
/// </summary>
[ValueConversion(typeof(string), typeof(Visibility))]
public sealed class StringToVisibilityConverter : IValueConverter
{
    /// <inheritdoc />
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        => string.IsNullOrEmpty(value as string) ? Visibility.Collapsed : Visibility.Visible;

    /// <inheritdoc />
    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
