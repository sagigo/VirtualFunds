package com.virtualfunds.domain.error

import com.virtualfunds.domain.AppError

/**
 * Maps a typed [AppError] to a Hebrew, user-facing message.
 *
 * The strings follow the terminology of `VirtualFundsRequirements.md` Appendix A.
 *
 * Deliberate deviation from desktop (reviewed and accepted in the Phase 11 parity pass):
 * desktop assigns operation-specific Hebrew wording at each catch-site in its WPF ViewModels
 * (e.g. empty fund name → "נא להזין שם לקרן."), while mobile maps each error token to one
 * generic message here. The meaning per token is identical; only the phrasing differs.
 * Full alignment was considered and declined — revisit if the difference bothers real use.
 *
 * Why this lives in `:domain` and not in Android string resources:
 *   - `:data` and `:domain` produce [AppError]s; we want the mapping callable from those
 *     layers without an Android `Context`.
 *   - The strings are static — no plurals, no parameterised substitutions that would
 *     benefit from `resources.getString(R.string.x)`.
 *   - Keeping them here means the desktop and mobile codebases can be diffed for parity
 *     without bouncing through Android XML.
 *
 * If a string ever needs locale or plural handling, move it to `:app` and inject a
 * `StringResolver` at the UI boundary.
 */
fun AppError.toHebrewMessage(): String = when (this) {
    // ── Validation errors (E4.7 prefix ERR_VALIDATION) ──────────────────────
    AppError.Validation.EmptyName                       -> "השם לא יכול להיות ריק"
    AppError.Validation.DuplicateName                   -> "השם כבר קיים"
    AppError.Validation.PortfolioClosed                 -> "התיק סגור ולא ניתן לערוך אותו"
    AppError.Validation.FundNotEmpty                    -> "לא ניתן למחוק קרן עם יתרה שונה מאפס"
    AppError.Validation.FundHasEnabledScheduledDeposit  -> "לא ניתן למחוק קרן עם הפקדה מתוזמנת פעילה"
    AppError.Validation.PortfolioTotalIsZero            -> "לא ניתן לעדכן שווי כאשר סה״כ התיק הוא אפס"
    AppError.Validation.NegativeAmount                  -> "הסכום חייב להיות חיובי"
    AppError.Validation.InvalidScheduleFields           -> "שדות התזמון אינם תקינים עבור סוג התזמון שנבחר"
    AppError.Validation.InvalidScheduleKind             -> "סוג תזמון לא חוקי"
    AppError.Validation.SameFundTransfer                -> "קרן המקור והיעד חייבות להיות שונות"
    AppError.Validation.RevalueWouldZeroFund            -> "עדכון השווי יאפס את יתרת אחת הקרנות — לא ניתן להמשיך"
    is AppError.Validation.Unknown                      -> "שגיאת אימות: ${this.token}"

    // ── Invariant errors (E4.7 prefix ERR_INVARIANT) ────────────────────────
    AppError.Invariant.NegativeBalance                  -> "יתרה לא מספיקה לפעולה"
    AppError.Invariant.TotalMismatch                    -> "שגיאת עקביות במסד הנתונים — נסה שוב"

    // ── Auth (E3) ────────────────────────────────────────────────────────────
    AppError.Auth.EmptyEmail                            -> "כתובת אימייל לא יכולה להיות ריקה"
    AppError.Auth.InvalidEmailFormat                    -> "כתובת אימייל לא תקינה"
    AppError.Auth.EmptyPassword                         -> "סיסמה לא יכולה להיות ריקה"
    AppError.Auth.PasswordTooShort                      -> "סיסמה חייבת להכיל לפחות 6 תווים"
    AppError.Auth.InvalidCredentials                    -> "אימייל או סיסמה שגויים"
    AppError.Auth.RegistrationFailed                    -> "ההרשמה נכשלה — ייתכן שהאימייל כבר רשום"

    // ── Generic / transport ──────────────────────────────────────────────────
    AppError.Unauthorized                               -> "אינך מורשה לבצע פעולה זו"
    is AppError.NotFound                                -> "הפריט המבוקש לא נמצא"
    is AppError.Conflict                                -> "התנגשות מצב — נסה לרענן ולנסות שוב"
    is AppError.Network                                 -> "שגיאת רשת — בדוק את החיבור ונסה שוב"
    is AppError.Unknown                                 -> "אירעה שגיאה לא צפויה"
}
