# desktop/CLAUDE.md — Virtual Funds Desktop (C# / WPF)

This file covers desktop-specific conventions. It is read **in addition to** the root `CLAUDE.md`, not instead of it.

---

## Target Framework

- **.NET 8 / C# 12** (latest LTS)

## C# Conventions

### Naming

- `PascalCase` for public members, properties, methods, classes
- `_camelCase` for private fields (underscore prefix)
- `I` prefix for interfaces (e.g., `IPortfolioService`)
- Standard .NET conventions throughout

### Nullable Reference Types

- Enabled project-wide: `<Nullable>enable</Nullable>`
- Treat nullable warnings as errors: `<WarningsAsErrors>nullable</WarningsAsErrors>`

### Code Documentation

- XML doc comments (`///`) on **all** public types and methods.
- Well-commented code overall. Inline comments to explain "why" and non-obvious logic.
- Code should be readable on its own, but don't skip comments where they add clarity.

### Error Handling

- Use **exceptions** for business logic errors — throw and catch custom exception types.
- Define clear exception classes that map to the spec's error tokens (e.g., `ERR_VALIDATION:EMPTY_NAME` → `EmptyNameException` or similar).
- Let truly unexpected errors propagate naturally.

### File Organization

- Reasonable file sizes — keep related things together.
- Split only when it genuinely helps readability.
- One primary class per file as a default, but co-locating small helper types in the same file is fine.

---

## Project Structure

- **Solution:** `desktop/VirtualFunds.slnx` contains two projects:
  - `VirtualFunds.Core` — .NET 8 class library (business logic, models, services). Pure logic with no UI dependencies.
  - `VirtualFunds.WPF` — .NET 8 WPF app (UI layer). References Core.

### Layout

```
desktop/
├── VirtualFunds.slnx
├── VirtualFunds.Core/
│   ├── Models/
│   ├── Exceptions/
│   ├── Services/
│   ├── Supabase/
│   └── Utilities/
└── VirtualFunds.WPF/
    ├── ViewModels/
    ├── Views/
    ├── Converters/
    └── Resources/
```

---

## UI Architecture

- **MVVM** with **CommunityToolkit.Mvvm** — source generators for `[ObservableProperty]`, `[RelayCommand]`. Eliminates INotifyPropertyChanged / ICommand boilerplate.
- **DI** with **Microsoft.Extensions.DependencyInjection** — services registered in `App.xaml.cs`, injected into ViewModels via constructor.

---

## Testing

- **xUnit** as the test framework.
- Test project: `desktop/VirtualFunds.Core.Tests/` (created when needed).
- `[Fact]` for simple tests, `[Theory]` + `[InlineData]` for parameterized tests (especially money math).
- Async test methods return `Task` — xUnit handles them natively.

---

## Async Patterns

- **`async/await` throughout.** All service methods return `Task<T>` / `Task`. ViewModels use async commands.
- **`ConfigureAwait(false)`** in `VirtualFunds.Core` (library code, no UI context).
- **No `ConfigureAwait`** in `VirtualFunds.WPF` (needs to return to UI thread).
- **`Async` suffix** on all async method names (e.g., `GetPortfoliosAsync`).

---

## Supabase (Desktop Client)

- **Community SDK (`supabase-csharp`).** Official community C# client library.

### Configuration & Secrets

- **`appsettings.json`** — checked into git with placeholder/empty values for Supabase config keys (`Supabase:Url`, `Supabase:AnonKey`).
- **User Secrets** (`dotnet user-secrets`) — for actual secret values during development. Stored outside the repo.
- Uses `Microsoft.Extensions.Configuration` (same ecosystem as the DI container).
- Never commit secrets to git regardless of approach.

---

## RTL & Money (Desktop Implementation)

- **Global RTL.** Set `FlowDirection="RightToLeft"` on the main window. All child elements inherit it.
- Override to `LeftToRight` locally where needed (e.g., numeric input fields).
- **Money formatter** in Core: `FormatAgoras(long agoras)` → `"1,234.56 ₪"` per the shared rules in the root `CLAUDE.md`.