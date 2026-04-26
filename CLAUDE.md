# CLAUDE.md — Virtual Funds

## Project Overview

Virtual Funds is a cross-platform app for managing a pooled portfolio divided into named funds (ownership shares).

- **Platforms:** Windows Desktop (C# / WPF) and Android Mobile (Kotlin)
- **Backend:** Supabase (Auth + Postgres + RLS + RPC)
- **Currency:** NIS stored as integer agoras (`long` / `bigint`). No floating point for money — ever, in any platform.

### Authoritative Spec

Always reference `VirtualFundsRequirements.md` as the single source of truth before implementing any feature. When starting work on a feature, cite the relevant spec section (e.g., "Implementing per E6.11") so the developer can verify correctness.

### Platform-Specific Instructions

This file contains only instructions common to both apps. **For platform-specific conventions, read the appropriate file in addition to this one:**

- Desktop (C# / WPF) work → also read `desktop/CLAUDE.md`
- Mobile (Android / Kotlin) work → also read `mobile/CLAUDE.md`

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
- Anything that relies on "magic" (code generation, annotation processing, DI containers, source generators) must be explicitly documented in comments at the point of use. A line like `@Inject constructor(...)` is not self-explanatory to a developer new to the stack.

### Handling Ambiguity

- Always ask before making a non-trivial design decision not covered by the spec.
- Never guess or silently pick an approach for anything that could reasonably go either way.

---

## Repo Layout

```
VirtualFunds/
├── db/                          (shared SQL — both platforms)
│   ├── migrations/              (numbered: 001_..., 002_...)
│   └── seed/                    (optional dev/test data)
├── desktop/                     (C# / WPF)
│   ├── CLAUDE.md                (desktop-specific instructions)
│   └── ...
├── mobile/                      (Kotlin / Android)
│   ├── CLAUDE.md                (mobile-specific instructions)
│   └── ...
├── CLAUDE.md                    (this file — shared instructions)
└── VirtualFundsRequirements.md  (authoritative spec)
```

---

## Supabase (Shared)

- **Auth + Postgres + RLS + RPC.** Row-Level Security policies in Postgres are the primary security boundary. Both clients hit Supabase directly using the anon key plus the authenticated user's JWT.
- **Shared SQL in `db/`.** All table definitions, RLS policies, and RPC functions live as `.sql` files in the repo, regardless of how they are applied. Both desktop and mobile depend on this.
- **Migration workflow:** to be decided. Ask before setting one up.
- The `db-reviewer` agent runs automatically after any `.sql` file is written or modified.
- The `kotlin-code-reviewer` agent runs automatically after any `.kt` file is written or modified.

---

## UI & Localization (Shared Principles)

Each platform implements these in its own way (see platform-specific files), but the rules themselves are shared.

### Language

- UI strings in **Hebrew** from the start.
- All code, comments, and commit messages in **English**.

### RTL Layout

- **Global RTL** on each platform.
- Override to LTR locally where needed (e.g., numeric input fields).

### Money Display

- Format: `"1,234.56 ₪"` — thousands separator is a comma, two decimal places always, Shekel symbol (`₪`) **after** the number.
- Display only — stored/computed value stays as `long` agoras. No `decimal`, `float`, `Double`, or similar type appears anywhere in storage or arithmetic.
- Each platform implements its own `FormatAgoras` / `formatAgoras` function with identical output.

---

## Git

- Free-form, descriptive commit messages. No enforced format like conventional commits.
- Just be clear about what changed and why.