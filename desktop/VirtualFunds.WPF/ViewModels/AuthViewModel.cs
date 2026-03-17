using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;

namespace VirtualFunds.WPF.ViewModels;

/// <summary>
/// ViewModel for the authentication window.
/// Handles both Sign In and Sign Up modes via the <see cref="IsSignUpMode"/> toggle.
/// <para>
/// The ViewModel fires <see cref="AuthSucceeded"/> when authentication is complete.
/// The View's code-behind subscribes to this event to perform window navigation.
/// </para>
/// <para>
/// Note on PasswordBox: WPF's PasswordBox does not support data binding for security reasons.
/// The View code-behind forwards the password to <see cref="Password"/> via the PasswordChanged event.
/// </para>
/// </summary>
public sealed partial class AuthViewModel : ObservableObject
{
    private readonly IAuthService _authService;

    /// <summary>
    /// Fired when authentication succeeds. The new <see cref="AuthState"/> is passed as the argument.
    /// The AuthWindow code-behind subscribes and navigates to MainWindow.
    /// </summary>
    public event Action<AuthState>? AuthSucceeded;

    // -----------------------------------------------------------------------------------------
    // Observable properties — CommunityToolkit.Mvvm generates public properties + PropertyChanged
    // for each [ObservableProperty] field.
    // -----------------------------------------------------------------------------------------

    /// <summary>The email address entered by the user.</summary>
    [ObservableProperty]
    private string _email = string.Empty;

    /// <summary>
    /// The password entered by the user.
    /// Set by the View's code-behind from the PasswordBox.PasswordChanged event.
    /// </summary>
    [ObservableProperty]
    private string _password = string.Empty;

    /// <summary>Hebrew error message shown below the form. Empty when there is no error.</summary>
    [ObservableProperty]
    private string _errorMessage = string.Empty;

    /// <summary>
    /// True while an auth operation is in progress.
    /// The View binds the submit button's IsEnabled to the inverse of this property.
    /// </summary>
    [ObservableProperty]
    private bool _isLoading;

    /// <summary>
    /// False = Sign In mode. True = Sign Up mode.
    /// Toggled by <see cref="ToggleModeCommand"/>.
    /// </summary>
    [ObservableProperty]
    private bool _isSignUpMode;

    // -----------------------------------------------------------------------------------------

    /// <summary>Initializes the ViewModel with the auth service.</summary>
    public AuthViewModel(IAuthService authService)
    {
        _authService = authService;
    }

    // -----------------------------------------------------------------------------------------
    // Commands — [RelayCommand] generates IAsyncRelayCommand / IRelayCommand properties.
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Submits the form — calls Sign In or Sign Up depending on <see cref="IsSignUpMode"/>.
    /// On success, fires <see cref="AuthSucceeded"/>. On failure, sets <see cref="ErrorMessage"/>.
    /// </summary>
    [RelayCommand]
    private async Task SubmitAsync()
    {
        ErrorMessage = string.Empty;
        IsLoading = true;

        try
        {
            AuthState result = IsSignUpMode
                ? await _authService.SignUpAsync(Email, Password)
                : await _authService.SignInAsync(Email, Password);

            AuthSucceeded?.Invoke(result);
        }
        catch (EmptyEmailException)
        {
            ErrorMessage = "נא להזין כתובת דוא״ל.";
        }
        catch (InvalidEmailFormatException)
        {
            ErrorMessage = "כתובת הדוא״ל אינה תקינה.";
        }
        catch (EmptyPasswordException)
        {
            ErrorMessage = "נא להזין סיסמה.";
        }
        catch (PasswordTooShortException)
        {
            ErrorMessage = "הסיסמה חייבת להכיל לפחות 6 תווים.";
        }
        catch (AuthenticationFailedException)
        {
            ErrorMessage = "דוא״ל או סיסמה שגויים. נסה שוב.";
        }
        catch (RegistrationFailedException)
        {
            ErrorMessage = "ההרשמה נכשלה. ייתכן שהדוא״ל כבר רשום.";
        }
        catch (Exception)
        {
            ErrorMessage = "אירעה שגיאה. נסה שוב מאוחר יותר.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    /// <summary>
    /// Toggles between Sign In and Sign Up modes.
    /// Clears the error message so the user starts fresh.
    /// </summary>
    [RelayCommand]
    private void ToggleMode()
    {
        IsSignUpMode = !IsSignUpMode;
        ErrorMessage = string.Empty;
    }
}
