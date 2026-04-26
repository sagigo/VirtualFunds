# mobile/CLAUDE.md — Virtual Funds Mobile (Android / Kotlin)

This file covers mobile-specific conventions. It is read **in addition to** the root `CLAUDE.md`, not instead of it.

The developer is new to Android development. Every Android- or Kotlin-specific pattern, annotation, or idiom must be explained on first use — either in chat before the code is written, or as comments in the code itself, or both. See "Knowledge Transfer" in the root `CLAUDE.md`. This applies with extra force here.

---

## Platform Target

- **Language:** Kotlin (latest stable).
- **Target device family:** Android only. No iOS, no cross-platform framework.
- **minSdk:** 31 (Android 12, released 2021). The developer's own test device (Galaxy A54) runs a newer version; minSdk 31 unlocks modern APIs without compatibility shims.
- **targetSdk / compileSdk:** latest stable at the time of writing.

## Build System

- **Gradle with Kotlin DSL** (`build.gradle.kts`). No Groovy build scripts.
- **IDE:** Android Studio (latest stable). All instructions assume it.

---

## UI Toolkit

- **Jetpack Compose.** Declarative UI written as `@Composable` Kotlin functions. No XML layouts.
- **Material 3** as the design system.
- **Primer (for the developer):** in Compose, a screen is a function that describes what the UI should look like given the current state. When state changes, Compose re-runs (recomposes) affected functions. The mental model is close to React — nothing like WPF's XAML + bindings.

---

## Module Structure

Multi-module by layer. This mirrors the desktop's Core/WPF split and enforces clean boundaries through the Gradle build system itself — the `:domain` module literally cannot import Android UI types because it doesn't depend on them.

```
mobile/
├── build.gradle.kts
├── settings.gradle.kts
├── app/         (Android app module — Compose UI, navigation, DI setup)
├── domain/      (pure-Kotlin module — models, validators, money math, business rules)
└── data/        (Supabase client, repositories, auth token storage)
```

- `:domain` has **no Android dependencies**. It's pure Kotlin. Same role as `VirtualFunds.Core` on desktop.
- `:data` depends on `:domain` and on Supabase / Android storage libraries.
- `:app` depends on both. UI code lives only here.

Keep modules flat for now. Do not introduce per-feature modules until there's a real reason.

---

## Architecture Pattern

- **MVVM.** Same mental model as desktop.
- Each screen has a `ViewModel` (Android Jetpack `ViewModel` class) that exposes state as `StateFlow<T>` and handles user actions as functions.
- Composables collect the `StateFlow` and render based on the current state. No business logic in composables.
- ViewModels live in `:app` and depend on repositories from `:data`, which depend on models from `:domain`.

---

## Dependency Injection

- **Hilt** (Google's official DI framework, built on Dagger).
- Services are constructor-injected into ViewModels and repositories.
- Hilt uses compile-time code generation driven by annotations (`@Inject`, `@HiltViewModel`, `@Module`, `@Provides`, etc.).

### Magic-disclosure rule

Hilt works by generating code at build time that the developer never sees. This is easy to forget and hard to debug. Therefore:

- On **every** Hilt-annotated class or provider, add a comment explaining what Hilt will do with it. Example:

```kotlin
  // @HiltViewModel tells Hilt to construct this ViewModel and inject its constructor
  // dependencies. Composables obtain it via hiltViewModel() which pulls the instance
  // from Hilt's generated factory — we never call `new` ourselves.
  @HiltViewModel
  class PortfolioViewModel @Inject constructor(
      private val portfolioRepo: PortfolioRepository
  ) : ViewModel() { ... }
```

- The first time any Hilt concept appears in the codebase (`@Module`, `@Provides`, `@Binds`, `@Singleton`, scopes, etc.), explain it in chat **before** writing the code, and leave a comment at the declaration site.
- When a Hilt error appears, explain what the generated code was trying to do — don't just paste the fix.

This rule generalizes to any other "magic" framework introduced later (Room processors, kotlinx.serialization plugin, etc.).

---

## Concurrency

- **Kotlin Coroutines + Flow** throughout. No RxJava.
- Suspending functions (`suspend fun`) in repositories and services. ViewModels launch coroutines via `viewModelScope`.
- Cold `Flow` for data streams, `StateFlow` for observable UI state, `SharedFlow` for one-shot events (snackbars, navigation).
- Explain coroutine scopes and dispatchers on first use.

---

## Error Handling

- **Sealed result types**, not exceptions, for domain and data errors.
- Define a reusable type, e.g.:

```kotlin
  sealed class AppResult<out T> {
      data class Success<T>(val value: T) : AppResult<T>()
      data class Failure(val error: AppError) : AppResult<Nothing>()
  }
```

- `AppError` is itself a sealed class whose subclasses map to the spec's error tokens (`ERR_VALIDATION:EMPTY_NAME` → `AppError.Validation.EmptyName`, etc.).
- Repository and service functions return `AppResult<T>`. UI branches on success/failure explicitly — the compiler enforces it.
- Truly unexpected errors (programmer bugs, out-of-memory) still propagate as exceptions and crash the app in dev. Don't swallow them.
- This is the one significant idiom divergence from desktop (which uses exceptions). The reason: the Kotlin compiler forces every call site to handle both branches of a sealed type, which is how the Kotlin community handles this.

---

## Local Storage

- **None for domain data.** The app is online-only (see below).
- No Room, no DataStore for domain data. DataStore may be used later for trivial user preferences if needed — ask first.
- **Exception — small non-sensitive identifiers:** `device_id` (spec E2.3) is a stable UUID per installation. It is not sensitive and is stored in regular `SharedPreferences` (not encrypted). Session tokens use `EncryptedSharedPreferences` (see Auth Token Storage above).

## Offline / Caching

- **Online-only.** Every screen fetches from Supabase; no local cache of domain data.
- Network failure → show a clear error state and a retry button. No stale-data fallback.
- Revisit this decision only when lack of caching becomes a felt pain point.

---

## Navigation

- **Navigation Compose** (`androidx.navigation:navigation-compose`), Google's official library.
- A single `NavHost` at the app root defines all destinations.
- **Type-safe routes** (Navigation 2.8+): routes are Kotlin `@Serializable` data objects/classes. Arguments are constructor parameters, not string placeholders. The compiler catches wrong argument types at build time.

```kotlin
// Route declarations (in a shared Routes.kt)
@Serializable object PortfolioList
@Serializable data class PortfolioDetail(val portfolioId: String)

// Navigating (type-checked at compile time)
navController.navigate(PortfolioDetail(portfolioId = id))

// Composable registered in NavHost
composable<PortfolioDetail> { backStackEntry ->
    val route: PortfolioDetail = backStackEntry.toRoute()
    PortfolioDetailScreen(portfolioId = route.portfolioId)
}
```

---

## Supabase (Mobile Client)

- **`supabase-kt`** (community Kotlin SDK). Under the hood it uses Ktor + `kotlinx.serialization`. Same Postgres, same RLS policies, same RPC functions as desktop.
- The `:data` module owns all Supabase client code. `:domain` and `:app` must not import Supabase types directly — they go through repository interfaces defined in `:domain`.

### Configuration & Secrets

- Supabase URL and anon key live in `local.properties` (not committed) and are exposed to code via Gradle `BuildConfig` fields. The `local.properties` file stays in `.gitignore`.
- A `local.properties.example` (or equivalent documented template) shows the required keys with blank values.
- The anon key is safe to ship in a release APK (it's designed to be public alongside RLS), but the dev values still stay out of the repo by convention.

### Auth Token Storage

- **`EncryptedSharedPreferences`** (AndroidX Security / Jetpack Security) for the Supabase session / refresh token.
- Backed by the Android Keystore. Simpler than rolling Keystore encryption by hand.
- Note in a comment at the call site that this library is in maintenance mode — we accept it for now, and may migrate to a DataStore-based scheme later.
- **Biometric unlock is not in v1.** Token presence alone authenticates the app. Reconsider after v1 ships.

---

## Testing

- **Kotest** as the test framework (behavior-spec style, rich matchers).
- **MockK** for mocking. It handles Kotlin's `final` classes and `suspend` functions without workarounds.
- **Turbine** for testing `Flow` emissions.
- **Compose UI Test** (`createComposeRule`) for UI tests, when they're written.
- Unit tests live alongside the module they test (`domain/src/test/kotlin/...`, etc.).
- Prioritize unit tests in `:domain` (money math, validators) — they have the highest value-to-effort ratio and run without a device.
- Explain Kotest's spec styles (`FunSpec`, `BehaviorSpec`, `StringSpec`) the first time one is introduced and pick one consistently.

---

## Code Quality Tooling

- **ktlint** for formatting.
- **detekt** for static analysis.
- Both run locally; CI is not set up yet (see below).

---

## CI / CD

- **None at the start.** Builds run locally in Android Studio; the APK is installed on the developer's device via USB.
- Revisit once the app is worth protecting — most likely GitHub Actions running `./gradlew check` on push, plus a signed release build on tags. Signing keys would live in GitHub Secrets, never in the repo.

---

## RTL & Money (Mobile Implementation)

- **Global RTL.** `android:supportsRtl="true"` in the manifest (default on modern Android), and apply `LayoutDirection.Rtl` at the root of the Compose tree via `CompositionLocalProvider`.
- Override to LTR locally where needed (e.g., numeric input `TextField`s).
- **Money formatter** in `:domain`: `fun formatAgoras(agoras: Long): String` → `"1,234.56 ₪"`, matching the shared format rules in the root `CLAUDE.md` and matching the desktop output byte-for-byte.

---

## Naming & Conventions (Kotlin)

- `PascalCase` for classes and objects.
- `camelCase` for functions, properties, parameters.
- `UPPER_SNAKE_CASE` for top-level constants (`const val`).
- Interfaces: **no** `I` prefix (Kotlin convention — unlike C#). Name them for what they are: `PortfolioRepository`, not `IPortfolioRepository`.
- Composable functions: `PascalCase` (they're functions, but by convention named like classes). Compose's own rule, not arbitrary.
- One public class per file is typical but not required — Kotlin happily co-locates related types.
- KDoc (`/** */`) on all public APIs in `:domain` and `:data`. UI composables can skip KDoc if the function name and a `@Preview` tell the story.

---

## Cross-Platform Consistency

### The principle

The user must feel this is the same product on both platforms — not two apps that happen to share a backend. Functionality and behaviour must be identical. UI patterns differ (Compose vs WPF, touch vs mouse) but every user-observable decision — what's validated, what errors appear, what the flow looks like, how undo works, what the history shows — must match the desktop.

### The process rule

**Before implementing any feature on mobile, read the desktop implementation first.**

1. Find the relevant desktop ViewModel(s) in `desktop/VirtualFunds.WPF/ViewModels/`.
2. Find the relevant service implementation(s) in `desktop/VirtualFunds.Core/Supabase/`.
3. Understand what state the desktop exposes, what actions it handles, what errors it surfaces, and what the exact user flow is.
4. Implement the mobile version to mirror that behaviour as closely as Kotlin/Compose allows. Deviations are acceptable only when the desktop pattern would be genuinely awkward on mobile — and any deviation must be called out explicitly in chat.

This applies to validation logic, error messages (translated), edge-case handling, empty states, loading states, and confirmation dialogs — not just happy paths.

### Specific technical invariants

These must be bit-identical across platforms regardless of any other consideration:

| Item | Desktop reference |
|------|-------------------|
| Money formatter (`"1,234.56 ₪"`) | `desktop/VirtualFunds.Core/Utilities/MoneyFormatter.cs` |
| Banker's rounding for scale operations (E6.4) | `desktop/VirtualFunds.Core/Utilities/IntegerMath.cs` |
| `TransactionType` enum → string values (sent to/from Supabase) | `desktop/VirtualFunds.Core/Models/TransactionType.cs` |
| `ScheduleKind` enum → string values | `desktop/VirtualFunds.Core/Models/ScheduleKind.cs` |
| Name normalization (E2.4): `lower(trim(name))` | applied in all service calls |
| Scheduled deposit Israel-time evaluation (E8) | `desktop/VirtualFunds.Core/Utilities/IsraelTimeHelper.cs` |

**Android timezone implementation:** Use `java.time.ZoneId.of("Asia/Jerusalem")` — available natively since API 26 and fully supported on `minSdk 31`. No third-party library needed. All `ZonedDateTime` / `LocalDateTime` conversions for scheduled deposit scheduling must go through this zone ID.

---

## Summary of Key Decisions

| Area | Decision |
|------|----------|
| UI toolkit | Jetpack Compose |
| minSdk | 31 (Android 12) |
| Module structure | Multi-module by layer (`:app`, `:domain`, `:data`) |
| Architecture | MVVM |
| DI | Hilt (with magic-disclosure rule) |
| Local storage | None (domain data); `SharedPreferences` for `device_id` |
| Offline strategy | Online-only |
| Navigation | Navigation Compose, type-safe routes (`@Serializable` objects) |
| Error handling | Sealed result types (`AppResult`) |
| Token storage | EncryptedSharedPreferences |
| Biometric auth | Deferred (not in v1) |
| Testing | Kotest + MockK + Turbine + Compose UI Test |
| CI/CD | None at start |