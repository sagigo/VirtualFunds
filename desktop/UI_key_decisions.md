# UI Key Decisions

Tracks significant design and architecture decisions made during the visual redesign of the WPF app.

---

## 1. Visual Style: "Bold Dashboard" (2026-04-12)

**Decision:** Adopt the MaterialDesignInXAML library as the UI framework for the redesign.

**Style target:**
- Primary accent: `#5C35D9` (purple)
- Secondary accents: `#00C9A7` (teal), `#F6AD55` (amber) — used for fund color-coding
- Each fund/item gets a colored left-side accent bar
- Main header/top bar: solid purple with white text
- List items and data: white cards with subtle borders
- Background: `#FAFAFA` (very light gray)
- Primary buttons: solid purple. Secondary buttons: light purple tint (`#F0EEFF`) with purple text
- Rounded corners: 8–12px throughout
- Typography: clean sans-serif, large bold numbers, small muted labels

**Packages added (v5.3.1):**
- `MaterialDesignThemes` — styled control templates, theming engine, Cards, Chips, etc.
- `MaterialDesignColors` — Material Design named color swatches used when configuring the theme

**Scope constraint:** Pure visual redesign only. No ViewModel logic, commands, bindings, or Core code may be touched. Only XAML files and resource dictionaries.

---

## 2. App.xaml Theming Setup (2026-04-12)

**Decision:** Use `BundledTheme` (MaterialDesign v5 approach) in `App.xaml` to bootstrap the theme engine.

**Configuration:**
- `BaseTheme="Light"` — white/light-gray surfaces
- `PrimaryColor="Purple"` — Material swatch default; overridden with exact brand hex `#5C35D9` in `Resources/Theme.xaml`
- `SecondaryColor="Teal"` — Material swatch default

**Correct v5 App.xaml setup (two merged dictionaries required):**
1. `BundledTheme` — handles theme colors and light/dark mode
2. `pack://…/MaterialDesignThemes.Wpf;component/Themes/MaterialDesign2.Defaults.xaml` — applies all MD2 control templates and named styles (e.g. `MaterialDesignOutlinedTextBox`, `MaterialDesignRaisedButton`, etc.)

**v4 → v5 rename gotcha:** The old `MaterialDesignTheme.Defaults.xaml` was renamed to `MaterialDesign2.Defaults.xaml` in v5. The old name no longer exists in the package and causes a runtime `IOException` on startup. `MaterialDesign3.Defaults.xaml` also exists for the MD3 visual style (rounded, expressive); we use MD2 to match the "Bold Dashboard" design intent.

**Structure:** Existing app-level converters are preserved inside a `ResourceDictionary` wrapper alongside the merged MaterialDesign dictionaries.

---

## 3. Theme.xaml Structure (2026-04-12)

**Decision:** All brand colors, brushes, and reusable named styles live in `Resources/Theme.xaml`, merged after the MaterialDesign dictionaries in `App.xaml`.

**What's in Theme.xaml:**
- Color/brush definitions (PrimaryBrush, TealBrush, AmberBrush, BackgroundBrush, etc.)
- `CardStyle` — white Border with 10px corners and subtle border for list items
- `AccentBarStyle` / `TealAccentBarStyle` / `AmberAccentBarStyle` — 5px vertical bars for fund item leading edge
- `HeaderBarStyle` — full-width solid purple Border for main window top bars
- `DialogCardStyle` — white padding container for dialog form bodies
- Eight named button styles: `PrimaryButtonStyle`, `SecondaryButtonStyle`, `GhostButtonStyle`, `GhostWhiteButtonStyle`, `HeaderPrimaryButtonStyle`, `DangerButtonStyle`, `LinkButtonStyle`, `SmallGhostButtonStyle`
- Typography styles: `HeaderTitleStyle`, `HeaderTotalStyle`, `SectionHeaderStyle`, `MutedLabelStyle`, `FundNameStyle`, `FundBalanceStyle`, `FieldLabelStyle`, `HintTextStyle`
- `CardListBoxItemStyle` — strips ListBoxItem chrome so the DataTemplate card Border provides the visual container

**Why named styles only (no implicit overrides):** Implicit style overrides in a ResourceDictionary risk breaking controls inside templates (e.g., TextBoxes inside ComboBox dropdowns). Named styles applied explicitly per control are safer and more predictable.

---

## 4. Per-View Styling Approach (2026-04-12)

**Main windows (AuthWindow, PortfolioWindow, ScheduledDepositsDialog):**
- `Background="{StaticResource BackgroundBrush}"` on the Window (`#FAFAFA`)
- Full-width `HeaderBarStyle` Border at the top containing title + action buttons
- Buttons on the purple header use `GhostWhiteButtonStyle` (back/nav) or `HeaderPrimaryButtonStyle` (create/add)
- Section headers use `SectionHeaderStyle`; muted metadata uses `MutedLabelStyle`

**Dialog windows (NameInputDialog, FundCreateDialog, AmountInputDialog, TransferDialog, ScheduledDepositFormDialog):**
- A 4px `PrimaryBrush` horizontal strip at the top of the window (instead of a full header band — dialogs are small)
- Form body wrapped in `DialogCardStyle` (white, padded)
- `MaterialDesignOutlinedTextBox` style applied explicitly to text inputs
- OK = `PrimaryButtonStyle`, Cancel = `SecondaryButtonStyle`

**Fund list items (PortfolioWindow):**
- Each item is a `Border` with `CornerRadius="10"`, `ClipToBounds="True"`, and a `DockPanel.Dock="Right"` `AccentBarStyle` Border providing the purple accent bar on the visual leading (right) edge in RTL
- `CardListBoxItemStyle` strips the ListBoxItem's default background/padding so the inner card Border owns the visual frame
- The MaterialDesign ListBoxItem selection highlight still shows through the transparent container

**Accent bar placement:** `DockPanel.Dock="Right"` in RTL layout = the visual right edge = the reading-start side in Hebrew, consistent with RTL design conventions.

---

## 5. History View — Color-Coded Transaction Types (2026-04-12)

**Decision:** Color-code history rows by transaction type using pure XAML `DataTrigger`s, with no ViewModel or Core changes.

**Color mapping:**
| TransactionType | Color | Use |
|---|---|---|
| `FundDeposit` | `SuccessBrush` `#16A34A` (green) | Amount, label, icon |
| `FundWithdrawal` | `ErrorBrush` `#E53E3E` (red) | Amount, label, icon |
| `Transfer` | `TealBrush` `#00C9A7` | Amount, label, icon |
| `PortfolioRevalued` | `AmberBrush` `#F6AD55` | Amount, label, icon |
| `Undo` | `TextMutedBrush` `#9090A0` (gray) | Amount, label, icon |

**New brush added to Theme.xaml:** `SuccessBrush` / `SuccessColor` `#16A34A` (green). All other colors were already defined.

**How DataTrigger enum comparison works in WPF:** When a binding returns an enum value, the `Value` attribute in `DataTrigger` can be the enum member name as a plain string (e.g. `Value="FundDeposit"`). WPF's TypeConverter converts the string to the correct enum value at parse time — no namespace import needed.

**Three elements colored per row:** amount (`FormattedAmount`), type label (`TransactionTypeLabel`), and icon — all use the same set of `DataTrigger`s so they stay perfectly in sync.

---

## 6. History View — Transaction Type Icons (2026-04-12)

**Decision:** Add a `materialDesign:PackIcon` to the left of each history row's type label. Kind and color both controlled by `DataTrigger` on `TransactionType`.

**Icon mapping:**
| TransactionType | PackIcon Kind | Color |
|---|---|---|
| `FundDeposit` | `ArrowDownBold` | `SuccessBrush` `#16A34A` (green) |
| `FundWithdrawal` | `ArrowUpBold` | `ErrorBrush` `#E53E3E` (red) |
| `Transfer` | `SwapHorizontal` | `TealBrush` `#00C9A7` |
| `PortfolioRevalued` | `TrendingUp` | `AmberBrush` `#F6AD55` |
| `Undo` | `Undo` | `TextMutedBrush` `#9090A0` (gray) |
| *(default)* | `History` | `TextPrimaryBrush` `#1A1A2E` |

**RTL note:** The icon+label pair is wrapped in a `StackPanel` with `FlowDirection="LeftToRight"` so the icon always appears visually to the left of the Hebrew text, regardless of the window's global `FlowDirection="RightToLeft"`. Short Hebrew strings render correctly either way.

---

## 7. History View — Signed Amount Color in Detail Rows (2026-04-12)

**Decision:** Color the `FormattedSignedAmount` TextBlock green for positive amounts and red for negative, using a dedicated converter rather than `DataTrigger`.

**Why a converter instead of DataTrigger:** The sign lives on a `long` (`AmountAgoras`), not an enum. A `DataTrigger` can only compare for equality, not `>= 0`. A converter (`SignedAmountToBrushConverter`) handles the comparison cleanly.

**Implementation:** `Converters/SignedAmountToBrushConverter.cs` — `[ValueConversion(typeof(long), typeof(Brush))]`. Looks up `SuccessBrush` / `ErrorBrush` from `Application.Current.Resources` so it stays in sync with Theme.xaml without duplicating hex values. Registered in App.xaml.

---

## 8. Fund List — Rotating Accent Bar Colors (2026-04-12)

**Decision:** Cycle fund card accent bars through blue → teal → amber using `AlternationCount="3"` on the fund `ListBox`.

**How it works:**
- `AlternationCount="3"` on the `ListBox` causes WPF to set the `ItemsControl.AlternationIndex` attached property (0, 1, or 2) on each `ListBoxItem` in sequence.
- Inside the `DataTemplate`, the accent bar `Border` reads that value via `{Binding (ItemsControl.AlternationIndex), RelativeSource={RelativeSource AncestorType=ListBoxItem}}`.
- Index 0 → `PrimaryBrush` `#2563EB` (blue, the `AccentBarStyle` default), 1 → `TealBrush` `#00C9A7`, 2 → `AmberBrush` `#F6AD55`.
- The cycle repeats: fund #4 gets index 0 again.

**Why `AlternationIndex` instead of a ViewModel property:** No data-model concept of "color" exists per fund, and the spec doesn't define one. Visual rotation is purely a display concern — keeping it in XAML is correct.

---

## 9. History List — Row Striping (2026-04-12)

**Decision:** Alternate history row backgrounds (white / `#F4F6F9`) using `AlternationCount="2"` on the history `ItemsControl`.

**How it works:** Same `AlternationIndex` mechanism as fund accent bars (Section 8), but on an `ItemsControl` whose container is `ContentPresenter` rather than `ListBoxItem`. The binding reads `{Binding (ItemsControl.AlternationIndex), RelativeSource={RelativeSource AncestorType=ContentPresenter}}`.

**Color choice:** Alternate rows use `#F4F6F9` — a blue-tinted light gray that harmonizes with the `#F0F2F5` window background without being distracting. The `Border`'s `Background` attribute was removed and moved into a `<Border.Style>` with a `DataTrigger` (same dual-set rule as elsewhere: cannot have both an attribute and a child style element).

---
