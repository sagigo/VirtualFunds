# CLAUDE.md — Virtual Funds

## Project Overview

Virtual Funds is a cross-platform app for managing a pooled portfolio divided into named funds (ownership shares).

- **Platforms:** Windows Desktop (C# / WPF), Android Mobile (Kotlin) — starting with WPF
- **Backend:** Supabase (Auth + Postgres + RLS + RPC)
- **Currency:** NIS stored as integer agoras (`long` / `bigint`). No floating point for money.

### Authoritative Spec

Always reference `VirtualFundsRequirements.md` as the single source of truth before implementing any feature. When starting work on a feature, cite the relevant spec section (e.g., "Implementing per E6.11") so the developer can verify correctness.

---

## Engineering Philosophy

### YAGNI with Pragmatism
- Write the simplest code that solves the current requirement.
- No speculative abstractions or generic wrappers for unclear future needs.
- Light abstractions are allowed only when they're cheap and clearly useful soon.
- Refactor when a real need appears, not before.

### Developer Involvement
- **Always ask before any code change.** Explain what you're about to do, why, and which spec section it relates to. Wait for approval before proceeding.
- After completing work, summarize what was done.
- The developer must be fully aware of and understand all code in the codebase.
- The developer is not experienced in UI/UX. For any UI-related decision (layout, controls, patterns, styling), present one or more options with simple explanations and pros/cons. Don't assume familiarity with UI terminology.

### Knowledge Transfer
- When introducing a new pattern, library, or non-obvious technique, add a brief explanation so the developer understands what's in the codebase and why.
- Don't assume familiarity — explain on first use.

### Handling Ambiguity
- Always ask before making a non-trivial design decision not covered by the spec.
- Never guess or silently pick an approach for anything that could reasonably go either way.

---

## C# / WPF Conventions

### Target Framework
- **.NET 8 / C# 12** (latest LTS)

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

### Project Structure
- **Monorepo** with platform-based top-level folders.
- **Solution:** `desktop/VirtualFunds.slnx` contains two projects:
  - `VirtualFunds.Core` — .NET 8 class library (business logic, models, services). Reusable for Android later.
  - `VirtualFunds.WPF` — .NET 8 WPF app (UI layer). References Core.
- **Shared SQL:** `db/` at repo root, used by both desktop and future mobile app.

### Repo Layout
```
VirtualFunds/
├── db/                          (shared SQL — both platforms)
│   ├── migrations/              (numbered: 001_..., 002_...)
│   └── seed/                    (optional dev/test data)
├── desktop/                     (C# / WPF)
│   ├── VirtualFunds.slnx
│   ├── VirtualFunds.Core/
│   │   ├── Models/
│   │   ├── Exceptions/
│   │   ├── Services/
│   │   ├── Supabase/
│   │   └── Utilities/
│   └── VirtualFunds.WPF/
│       ├── ViewModels/
│       ├── Views/
│       ├── Converters/
│       └── Resources/
└── mobile/                      (Kotlin / Android — future)
```

### UI Architecture
- **MVVM** with **CommunityToolkit.Mvvm** — source generators for `[ObservableProperty]`, `[RelayCommand]`. Eliminates INotifyPropertyChanged / ICommand boilerplate.
- **DI** with **Microsoft.Extensions.DependencyInjection** — services registered in `App.xaml.cs`, injected into ViewModels via constructor.

### Testing
- **xUnit** as the test framework.
- Test project: `desktop/VirtualFunds.Core.Tests/` (created when needed).
- `[Fact]` for simple tests, `[Theory]` + `[InlineData]` for parameterized tests (especially money math).
- Async test methods return `Task` — xUnit handles them natively.

### Async Patterns
- **`async/await` throughout.** All service methods return `Task<T>` / `Task`. ViewModels use async commands.
- **`ConfigureAwait(false)`** in `VirtualFunds.Core` (library code, no UI context).
- **No `ConfigureAwait`** in `VirtualFunds.WPF` (needs to return to UI thread).
- **`Async` suffix** on all async method names (e.g., `GetPortfoliosAsync`).

---

## Supabase

### Client Approach
- **Community SDK (`supabase-csharp`).** Use the official community C# client library.

### Migration Management
- **To be decided.** Ask before setting up migration workflow.
- All SQL (table definitions, RLS policies, RPC functions) lives in the repo as `.sql` files regardless of how it is applied.
- The `db-reviewer` agent runs automatically after any `.sql` file is written or modified.

### Configuration & Secrets
- **`appsettings.json`** — checked into git with placeholder/empty values for Supabase config keys (`Supabase:Url`, `Supabase:AnonKey`).
- **User Secrets** (`dotnet user-secrets`) — for actual secret values during development. Stored outside the repo.
- Uses `Microsoft.Extensions.Configuration` (same ecosystem as the DI container).
- Never commit secrets to git regardless of approach.

---

## UI & Localization

### Language
- UI strings in **Hebrew** from the start.
- All code, comments, and commit messages in **English**.

### RTL Layout
- **Global RTL.** Set `FlowDirection="RightToLeft"` on the main window. All child elements inherit it.
- Override to `LeftToRight` locally where needed (e.g., numeric input fields).

### Money Display
- **Custom formatter** in Core: `FormatAgoras(long agoras)` → `"1,234.56 ₪"`
- Shekel symbol (`₪`) placed **after** the number.
- Two decimal places always shown.
- Thousands separator: comma.
- Display only — stored value stays `long` agoras. No `decimal` or `float` in storage or arithmetic.

---

## Git

- Free-form, descriptive commit messages. No enforced format like conventional commits.
- Just be clear about what changed and why.


