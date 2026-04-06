# Virtual Funds — Consolidated Requirements

**Version:** Consolidated rewrite based on the proportional ownership model  
**Platforms:** Windows Desktop (C#) and Android Mobile (Kotlin)  
**Backend:** Supabase (Auth + Postgres + Row Level Security)  
**Currency model:** NIS stored as integer agoras (`int64` / `bigint`)  
**Document purpose:** Single source of truth for the product and its engineering requirements.

---

## Table of Contents

- Part I — Product Requirements
  - 1. Product Overview
  - 2. Product Model and Core Concepts
  - 3. Functional Requirements
  - 4. Validation and Safety Rules
  - 5. User Experience Principles
  - 6. Non-Functional Requirements
  - 7. Product to Engineering Traceability
- Part II — Engineering Requirements
  - E1. Architecture Overview
  - E2. Shared Conventions and Definitions
  - E3. Authentication
  - E4. Supabase Backend
  - E5. Portfolio and Fund Management
  - E6. Fund Operations
  - E7. Transactions Log and History
  - E8. Scheduled Deposits
  - E9. Export
  - E10. Testing and Acceptance Checklist

---

# Part I — Product Requirements

## 1. Product Overview

Virtual Funds is a cross-platform application for managing a pooled portfolio that is divided into named funds.

The typical use case is a single savings account held at one investment firm. All money is invested together as one pool. The application provides a **virtual division** of that pool into named funds, each representing a purpose or beneficiary. These funds exist only inside the application — the investment firm sees a single account. When the real account value changes due to investment returns, losses, or fees, the user enters the new total and the application scales all fund balances proportionally, preserving each fund's ownership share.

A fund represents a logical share of the total pooled money. The total portfolio balance is the sum of all fund balances.

Examples:
- Child 1 fund
- Child 2 fund
- Apartment renovation fund
- Vacation fund
- Emergency reserve

The application is available on:
- Windows Desktop
- Android Mobile

All data is stored in Supabase and available on all signed-in devices.

This product is designed around the following idea:

> Funds represent named ownership shares of one pooled portfolio.

This is not a target-allocation application. The system does **not** use target percentages or rebalance-to-target behavior.

---

## 2. Product Model and Core Concepts

### 2.1 User
- A registered user authenticated by Supabase Auth.
- Each user owns one or more portfolios.

### 2.2 Portfolio
- A named collection of funds.
- The total portfolio balance is the sum of all fund balances.
- Users can create, rename, close, and browse multiple portfolios.
- Portfolio close is a soft delete. Closed portfolios remain stored for audit and history purposes.

### 2.3 Fund
- A named ownership bucket inside a portfolio.
- Each fund has:
  - a name
  - a balance in agoras
  - a derived current percentage of the portfolio total
- The current percentage is derived from the current balance and total. It is not stored as a fixed configuration value.

### 2.4 Transaction History Record
- An immutable audit record.
- History records are append-only.
- Every balance-changing action creates history records.
- Structural actions also create history records.
- Balances are stored state. History is the audit trail, not the source of truth for balance reconstruction during normal operation.

### 2.5 Scheduled Deposit
- A scheduled rule that automatically deposits money to a specific fund.
- Scheduled deposits are device-triggered and server-coordinated.
- Only credit operations are supported.

### 2.6 Device
- Each application installation generates a stable `device_id`.
- Some behavior is device-local, especially Undo.

### 2.7 Proportional Ownership Model
This product assumes that funds represent shares of one pooled total.

Example:
- Child 1: 1000
- Child 2: 800
- Apartment renovation: 1200
- Total: 3000

If the pooled investment grows and the total changes from 3000 to 3300, the system can scale all funds proportionally so that ownership is preserved.

This use case is first-class and is one of the core operations of the product.

---

## 3. Functional Requirements

### PR-1 Authentication and Session Persistence
**Engineering reference:** E3, E4

The application must support:
- Sign up with email and password
- Sign in with email and password
- Sign out
- Auto-login after app restart when the session is still valid or refreshable
- Multiple valid sessions across desktop and mobile at the same time

Out of scope for version 1:
- Password reset flow
- Email confirmation

---

### PR-2 Cloud Storage and Restore
**Engineering reference:** E4

The system must persist and restore:
- Users
- Portfolios
- Funds
- Balances
- Transaction history
- Scheduled deposits
- Scheduled deposit occurrences

A user must be able to close the application and later continue from the same data state on the same or another device.

---

### PR-3 Sync Across Devices
**Engineering reference:** E1, E4

The system must make the same portfolio data available on all signed-in devices.

Version 1 sync model:
- Refresh on app startup
- Refresh on app resume
- Refresh after every successful mutation
- No real-time push requirement

The system does not require background sync while the app is closed.

---

### PR-4 Portfolio Management
**Engineering reference:** E4, E5, E7

Users can:
- Create a portfolio
- Rename a portfolio
- Close a portfolio
- Browse and open existing portfolios

Rules:
- Close is a soft delete
- Closed portfolios are hidden from the default list
- Closed portfolios remain stored with their history
- Closed portfolios are read-only

The concept of an “active portfolio” is not part of the product.

---

### PR-5 Fund Management
**Engineering reference:** E4, E5, E7, E8

Users can:
- Create a fund
- Create a fund with an optional initial amount
- Rename a fund
- Delete a fund
- View a fund list showing:
  - name
  - balance
  - current derived allocation percent

Rules:
- Fund names must not be empty
- Fund names must be unique within a portfolio, case-insensitively
- Hebrew names are allowed
- Fund balances may never be negative
- Fund delete is allowed only when:
  - balance is zero, and
  - no enabled scheduled deposit targets that fund

---

### PR-6 Fund Operations
**Engineering reference:** E6, E7

The product supports the following balance-changing operations:

1. Deposit into a fund
2. Withdraw from a fund
3. Transfer money between funds
4. Revalue portfolio
5. Undo the last locally executed balance-changing operation on the current device

Explanation of the fourth operation:
- This operation is used when the pooled portfolio total changes due to investment return, loss, fees, or any other total-level change.
- The system scales all fund balances proportionally so that ownership shares are preserved as closely as possible with exact integer sums.

The following features are intentionally not part of the product:
- Target percentages
- Rebalance to targets
- Deposit to total by current allocation
- Withdraw from total by current allocation

---

### PR-7 Transaction History
**Engineering reference:** E7

The system must keep immutable history for:
- All fund operations
- Portfolio creation, rename, close
- Fund creation, rename, delete
- Scheduled deposit execution

Users can:
- View full history for a portfolio
- Filter by date range
- Filter by fund
- Filter by type
- Sort by date
- Export history to CSV

History records must never be edited or deleted by the user.

---

### PR-8 Scheduled Deposits
**Engineering reference:** E8

Users can create scheduled deposits that automatically deposit into a specific fund.

Each scheduled deposit includes:
- Name
- Target fund
- Amount
- Schedule
- Enabled/disabled state
- Optional note

Supported schedules:
- One time
- Daily
- Weekly
- Monthly

Users can:
- Create a scheduled deposit
- Edit a scheduled deposit
- Enable a scheduled deposit
- Disable a scheduled deposit
- Delete a scheduled deposit

Not required:
- Run now
- Skip next
- Snooze
- End date

Each execution creates normal transaction history records.

---

### PR-9 Export
**Engineering reference:** E9

Users can export:
- Full transaction history as CSV
- Current portfolio snapshot as CSV

The portfolio snapshot export includes at least:
- portfolio name
- fund name
- balance
- current derived allocation percent

---

## 4. Validation and Safety Rules

### 4.1 General rules
- Portfolio total must always equal the sum of fund balances
- Fund balances must never be negative
- All mutations must be atomic
- History must remain append-only
- Closed portfolios are read-only

### 4.2 Delete confirmations
The application must require explicit confirmation for:
- Close portfolio
- Delete fund
- Delete scheduled deposit

### 4.3 Undo rules
- Undo is history-based: each undoable operation in the transaction history panel shows an undo button
- An operation is undoable if its type is one of the supported kinds (see E6.12) and it has not already been undone
- Undo is implemented by compensating history records, not by deleting old records
- An operation is considered "already undone" when another operation’s `undo_of_operation_id` references it

### 4.4 Scheduled deposit safety
- Scheduled deposit execution must be exactly-once per scheduled occurrence
- No background server runner is required
- If both apps are closed, no scheduled deposit executes until a device triggers execution later

---

## 5. User Experience Principles

- Simple and intuitive interface
- Clear visibility of fund balances and total balance
- Immediate feedback after each action
- No hidden automatic reallocation logic
- Full traceability in history
- Same logical behavior on desktop and mobile

---

## 6. Non-Functional Requirements

- Cloud storage via Supabase
- Secure user isolation by authentication and Row Level Security
- Deterministic money behavior across C# and Kotlin
- Reasonable performance for paging, filtering, and history rendering
- Exact integer arithmetic for stored money

---

## 7. Product to Engineering Traceability

| Product Requirement | Engineering Sections |
|---|---|
| PR-1 Authentication and Session Persistence | E3 |
| PR-2 Cloud Storage and Restore | E4 |
| PR-3 Sync Across Devices | E1, E4 |
| PR-4 Portfolio Management | E4, E5, E7 |
| PR-5 Fund Management | E4, E5, E7, E8 |
| PR-6 Fund Operations | E6, E7 |
| PR-7 Transaction History | E7 |
| PR-8 Scheduled Deposits | E8 |
| PR-9 Export | E9 |

---

# Part II — Engineering Requirements

## E1. Architecture Overview

### E1.1 System architecture
The system consists of:
- Windows Desktop application written in C#
- Android application written in Kotlin
- Supabase Auth for identity and session management
- Supabase Postgres for persistent storage and RPC-based mutations

### E1.2 Authoritative model
The authoritative runtime model is:
- Stored fund balances as state
- Append-only transaction history as audit

This means:
- UI reads balances from the current state tables
- History is used for audit, undo, and user traceability
- History is not replayed on every normal app startup to derive balances

### E1.3 Proportional ownership financial model
Funds represent logical ownership shares of a pooled portfolio.

Implications:
- There is no fixed target percentage field
- There is no rebalance-to-target feature
- When the pooled total changes, the correct operation is to scale all fund balances proportionally

### E1.4 Mutation model
All state mutations must happen through RPC functions in Supabase.

Reasons:
- Atomicity
- Invariant enforcement
- Centralized audit logging
- Consistent behavior across clients

### E1.5 Sync model
Version 1 sync strategy:
- App startup: refresh portfolio list and selected portfolio data
- App resume: refresh current portfolio state and recent history
- After successful mutation: refresh affected data

Realtime subscriptions are optional future work, not part of version 1.

---

## E2. Shared Conventions and Definitions

### E2.1 Time and timezone
- All timestamps stored in the database are UTC (`timestamptz`)
- Israel time (`Asia/Jerusalem`) is the business timezone for scheduled deposit schedules
- The scheduled deposit authoring UI (time-of-day picker, weekday picker, day-of-month picker) **must** present and interpret values in Israel time (`Asia/Jerusalem`)
- General timestamp display elsewhere (history feed, created-at fields) may be presented in the user's local device time or Israel time — this is a UI-level decision and is not mandated by this spec

### E2.2 Money type
- `MoneyAgoras` is signed 64-bit integer
- Database type: `bigint`
- Stored balances must be non-negative
- Transaction deltas may be negative
- Floating point is forbidden for stored money and money algorithms

### E2.3 Identifiers
- All entities use UUID identifiers
- `device_id` is a stable UUID per installation
- `operation_id` groups related history rows for one logical user action
- `transaction_id` uniquely identifies one history row

### E2.4 Name normalization
For portfolio and fund uniqueness checks:
- trim leading and trailing whitespace
- preserve all Unicode characters
- compare case-insensitively using a stable normalization rule

Recommended normalized form:
- `normalized_name = lower(trim(name))`

### E2.5 Derived values
The following values are derived, not stored as configuration:
- portfolio total = sum of fund balances
- fund current allocation percent = fund balance / portfolio total

### E2.6 Soft delete terminology
In this document:
- “close portfolio” means soft delete
- fund delete is a hard delete, but a tombstone row is stored for history rendering

### E2.7 Read-only closed portfolios
A closed portfolio:
- remains visible in close-aware queries if requested
- is hidden from default active lists
- rejects all mutation RPC calls
- keeps all history and scheduled deposit data stored

---

## E3. Authentication

### E3.1 Scope
Supported authentication behavior:
- email + password
- immediate login after sign-up
- stay logged in
- multiple valid sessions across devices
- no password reset flow

### E3.2 Supabase configuration requirements
- Enable Email provider
- Enable email/password sign-up
- Disable email confirmation
- Use one shared Supabase project for both apps

### E3.3 Session model
A session contains at least:
- access token
- refresh token
- expiration time
- user object

Clients must:
- persist the session locally
- restore the session on startup
- refresh tokens automatically through the SDK when possible

### E3.4 Common client flow
#### App launch
1. Load the locally persisted session
2. If a valid or refreshable session exists:
   - move to authenticated app state
3. Otherwise:
   - show authentication UI

#### Sign up
1. Validate fields locally
2. Call Supabase sign-up
3. Persist returned session
4. Enter authenticated app state

#### Sign in
1. Validate fields locally
2. Call Supabase sign-in
3. Persist returned session
4. Enter authenticated app state

#### Sign out
1. Call Supabase sign out
2. Clear persisted session
3. Return to authentication UI

### E3.5 Security requirements
- Every user-owned table includes `user_id`
- Row Level Security restricts each row to `auth.uid()`
- Clients must not trust local user ownership assumptions
- All mutation RPC functions derive ownership from `auth.uid()`

### E3.6 Platform notes
#### C# desktop
- Persist serialized session in local app storage
- Maintain a central auth state object:
  - Unknown
  - SignedOut
  - SignedIn(user_id)

#### Kotlin Android
- Use Supabase Kotlin persistence support
- Restore the session before deciding whether to show login
- Avoid unnecessary re-login prompts on resume

---

## E4. Supabase Backend

### E4.1 Goals
Supabase must provide:
- persistent storage for all product state
- atomic RPC-based mutations
- user isolation
- exactly-once scheduled deposit coordination
- efficient read patterns for lists and history

### E4.2 Non-goals
Supabase does not provide:
- analytics or reporting logic
- server-stored undo state (undo eligibility is computed client-side from `undo_of_operation_id`)
- server-side scheduled runner

### E4.3 Database tables

#### E4.3.1 `portfolios`
Purpose: top-level portfolio container.

Columns:
- `portfolio_id uuid primary key default gen_random_uuid()`
- `user_id uuid not null`
- `name text not null`
- `normalized_name text not null`
- `created_at_utc timestamptz not null default now()`
- `updated_at_utc timestamptz not null default now()`
- `closed_at_utc timestamptz null`

Constraints and indexes:
- partial unique index on `(user_id, normalized_name)` where `closed_at_utc is null`
- index on `(user_id, created_at_utc)`

#### E4.3.2 `funds`
Purpose: fund state table.

Columns:
- `fund_id uuid primary key default gen_random_uuid()`
- `user_id uuid not null`
- `portfolio_id uuid not null references portfolios(portfolio_id)`
- `name text not null`
- `normalized_name text not null`
- `balance_agoras bigint not null`
- `created_at_utc timestamptz not null default now()`
- `updated_at_utc timestamptz not null default now()`

Constraints and indexes:
- `balance_agoras >= 0`
- unique `(portfolio_id, normalized_name)`
- index `(portfolio_id, normalized_name)`
- index `(portfolio_id, created_at_utc)`

#### E4.3.3 `deleted_funds`
Purpose: preserve fund name information after delete for history rendering.

Columns:
- `deleted_fund_id uuid primary key default gen_random_uuid()`
- `user_id uuid not null`
- `portfolio_id uuid not null`
- `fund_id uuid not null`
- `name text not null`
- `normalized_name text not null`
- `deleted_at_utc timestamptz not null default now()`

Constraints and indexes:
- unique `(portfolio_id, fund_id)`
- index `(portfolio_id, fund_id)`
- index `(portfolio_id, deleted_at_utc)`

#### E4.3.4 `transactions`
Purpose: append-only audit log.

Columns:
- `transaction_id uuid primary key`
- `user_id uuid not null`
- `portfolio_id uuid not null`
- `operation_id uuid not null`
- `committed_at_utc timestamptz not null default now()`
- `record_kind text not null`
- `transaction_type text not null`
- `fund_id uuid null`
- `amount_agoras bigint not null`
- `before_balance_agoras bigint null`
- `after_balance_agoras bigint null`
- `undo_of_operation_id uuid null`
- `summary_text text null`
- `note text null`
- `created_by_device_id uuid null`
- `client_app text null`
- `client_version text null`
- `metadata jsonb not null default '{}'::jsonb`

Rules:
- `record_kind in ('Summary', 'Detail')`
- if `record_kind = 'Summary'` then:
  - `fund_id is null`
  - `summary_text is not null`
- if `record_kind = 'Detail'` then:
  - `fund_id is not null`
- detail rows normally have non-zero `amount_agoras`
- structure detail rows are allowed to use `amount_agoras = 0`
- append-only permissions: no UPDATE and no DELETE for clients

Indexes:
- `(portfolio_id, committed_at_utc desc, operation_id, transaction_id)`
- `(portfolio_id, operation_id)`
- `(portfolio_id, fund_id, committed_at_utc desc)`

Design note:
- `fund_id` is intentionally not a foreign key to `funds`, because funds may be deleted and history must remain readable.

#### E4.3.5 `scheduled_deposits`
Purpose: schedule automatic credits to a fund.

Columns:
- `scheduled_deposit_id uuid primary key default gen_random_uuid()`
- `user_id uuid not null`
- `portfolio_id uuid not null references portfolios(portfolio_id)`
- `name text not null`
- `note text null`
- `is_enabled boolean not null default true`
- `fund_id uuid not null`
- `amount_agoras bigint not null`
- `schedule_kind text not null`
- `time_of_day_minutes int null`
- `weekday_mask int null`
- `day_of_month int null`
- `next_run_at_utc timestamptz not null`
- `created_at_utc timestamptz not null default now()`
- `updated_at_utc timestamptz not null default now()`

Constraints:
- `amount_agoras > 0`
- `schedule_kind in ('Daily', 'Weekly', 'Monthly', 'OneTime')`
- `time_of_day_minutes between 0 and 1439` when not null
- `day_of_month between 1 and 28` when not null
- `weekday_mask between 1 and 127` when not null
- schedule-specific required-field validation is enforced in RPC (see §E8.2)

`weekday_mask` encoding:
- bit 0 (value 1) = Sunday
- bit 1 (value 2) = Monday
- bit 2 (value 4) = Tuesday
- bit 3 (value 8) = Wednesday
- bit 4 (value 16) = Thursday
- bit 5 (value 32) = Friday
- bit 6 (value 64) = Saturday
- valid range: 1–127 (at least one day must be selected; 0 is invalid)
- example: Monday + Wednesday = 2 + 8 = 10
- both client apps and RPC functions must use this identical encoding

`next_run_at_utc` for `OneTime` schedules:
- at creation time, `next_run_at_utc` is set to the desired single execution time (converted from Israel time to UTC)
- there is no separate `one_time_at_utc` column; `next_run_at_utc` carries this value directly
- after a `OneTime` scheduled deposit executes, `is_enabled` is set to `false` and `next_run_at_utc` is not advanced

Indexes:
- `(portfolio_id, is_enabled, next_run_at_utc)`
- `(portfolio_id, created_at_utc)`

#### E4.3.6 `scheduled_deposit_occurrences`
Purpose: exactly-once execution coordination.

Columns:
- `occurrence_id uuid primary key default gen_random_uuid()`
- `user_id uuid not null`
- `portfolio_id uuid not null`
- `scheduled_deposit_id uuid not null references scheduled_deposits(scheduled_deposit_id)`
- `scheduled_for_utc timestamptz not null`
- `status text not null default 'Pending'`
- `claimed_at_utc timestamptz null`
- `claimed_by_device_id uuid null`
- `done_at_utc timestamptz null`
- `operation_id uuid null`
- `created_at_utc timestamptz not null default now()`

Constraints:
- `status in ('Pending', 'Claimed', 'Done')`
- unique `(scheduled_deposit_id, scheduled_for_utc)`

Indexes:
- `(portfolio_id, status, scheduled_for_utc)`
- `(scheduled_deposit_id, scheduled_for_utc)`

### E4.4 Row Level Security
Enable RLS on all user-owned tables.

Minimum rule:
- row belongs to current user when `user_id = auth.uid()`

### E4.5 Mutation policy
Clients must not directly mutate state tables.

Authoritative rule:
- reads may be direct
- writes must go through RPC

This is required to guarantee:
- atomicity
- invariant checks
- history writes
- consistent error behavior

### E4.6 Read patterns
The backend must support efficient reads for:
- portfolio list
- fund list by portfolio
- scheduled deposit list by portfolio
- history feed with paging and filters

Recommended queries:
- active portfolios: `where closed_at_utc is null order by normalized_name`
- funds: `where portfolio_id = ? order by normalized_name`
- history: `where portfolio_id = ? order by committed_at_utc desc, operation_id, transaction_id`

### E4.7 Error model
RPCs must return stable machine-readable error tokens.

Standard prefixes:
- `ERR_UNAUTHORIZED`
- `ERR_NOT_FOUND`
- `ERR_CONFLICT`
- `ERR_VALIDATION`
- `ERR_INVARIANT`

Examples:
- `ERR_VALIDATION:EMPTY_NAME`
- `ERR_VALIDATION:DUPLICATE_NAME`
- `ERR_VALIDATION:PORTFOLIO_CLOSED`
- `ERR_VALIDATION:FUND_NOT_EMPTY`
- `ERR_VALIDATION:FUND_HAS_ENABLED_SCHEDULED_DEPOSIT`
- `ERR_VALIDATION:PORTFOLIO_TOTAL_IS_ZERO` — raised when a scale operation is attempted but the current portfolio total is zero
- `ERR_INVARIANT:NEGATIVE_BALANCE`
- `ERR_INVARIANT:TOTAL_MISMATCH`

### E4.8 Migration and versioning
- all schema and RPC changes are managed via Supabase migrations
- additive changes are preferred
- breaking RPC changes should use versioned function names

Migration note — `target_percent_basis_points`:
- earlier drafts of this project (see `Requirements.md`) included a `target_percent_basis_points` column on the `funds` table. **This column must not be created.** The current product model has no target-percentage or rebalance-to-target feature.
- if the column was already applied to a database, remove it via a migration: `ALTER TABLE funds DROP COLUMN IF EXISTS target_percent_basis_points;`

---

## E5. Portfolio and Fund Management

### E5.1 Design principles
- stored balances are authoritative
- portfolio total is always derived from the sum of fund balances
- structural changes must be logged
- all operations are atomic
- closed portfolios are read-only

### E5.2 Canonical transaction type registry (authoritative)

This is the single source of truth for all `transaction_type` values used in the `transactions` table.

The **Commit path** column indicates how the operation is recorded:
- **Structural RPC** — written directly inside a dedicated RPC function (not via `rpc_commit_fund_operation`)
- **rpc_commit_fund_operation** — passed as `summary_transaction_type` or detail `transaction_type`

| `transaction_type` | `record_kind` | Commit path | Description |
|---|---|---|---|
| `PortfolioCreated` | Summary | Structural RPC | Portfolio was created |
| `PortfolioRenamed` | Summary | Structural RPC | Portfolio was renamed |
| `PortfolioClosed` | Summary | Structural RPC | Portfolio was closed (soft deleted) |
| `FundCreated` | Summary + Detail | Structural RPC | Fund was created |
| `FundRenamed` | Summary + Detail | Structural RPC | Fund was renamed |
| `FundDeleted` | Summary + Detail | Structural RPC | Fund was deleted |
| `FundDeposit` | Summary + Detail | rpc_commit_fund_operation | Money added to a single fund |
| `FundWithdrawal` | Summary + Detail | rpc_commit_fund_operation | Money withdrawn from a single fund |
| `Transfer` | Summary | rpc_commit_fund_operation | Transfer between two funds (summary only) |
| `TransferCredit` | Detail | rpc_commit_fund_operation | Receiving side of a transfer |
| `TransferDebit` | Detail | rpc_commit_fund_operation | Sending side of a transfer |
| `PortfolioRevalued` | Summary | rpc_commit_fund_operation | Portfolio total was proportionally scaled |
| `RevaluationCredit` | Detail | rpc_commit_fund_operation | Fund increased during a scale |
| `RevaluationDebit` | Detail | rpc_commit_fund_operation | Fund decreased during a scale |
| `ScheduledDepositExecuted` | Summary | rpc_commit_fund_operation | Scheduled deposit auto-execution (summary) |
| `Undo` | Summary + Detail | rpc_commit_fund_operation | Compensating operation for a previous action |

Notes:
- Structural-RPC types may **not** be passed as `summary_transaction_type` to `rpc_commit_fund_operation`.
- `FundDeposit` may appear as a detail row inside a `FundCreated` operation (initial amount) or a `ScheduledDepositExecuted` operation, in addition to being a standalone summary type.

### E5.3 RPC: `app.rpc_create_portfolio(name, operation_id, summary_transaction_id)`

Purpose:
- Create a new active portfolio.

Inputs:
- `name text`
- `operation_id uuid` — client-generated, stable across retries
- `summary_transaction_id uuid` — client-generated, used as the idempotency anchor

Validation:
- trimmed name must not be empty
- normalized name must be unique among active portfolios for the user

High-level algorithm:
1. derive `user_id` from `auth.uid()`
2. normalize the input name
3. validate name is not empty
4. **idempotency check**: if a transaction row with `transaction_id = summary_transaction_id` already exists, return success with the existing `portfolio_id` without any further changes
5. validate uniqueness among `closed_at_utc is null`
6. insert a row into `portfolios`
7. write history summary row:
   - `transaction_id = summary_transaction_id`
   - `operation_id = operation_id`
   - `transaction_type = PortfolioCreated`
   - `record_kind = Summary`
   - `amount_agoras = 0`
   - `summary_text = 'Portfolio created: <name>'`
8. return `portfolio_id`

### E5.4 RPC: `app.rpc_rename_portfolio(portfolio_id, new_name, operation_id, summary_transaction_id)`

Inputs:
- `portfolio_id uuid`
- `new_name text`
- `operation_id uuid` — client-generated, stable across retries
- `summary_transaction_id uuid` — idempotency anchor

Validation:
- portfolio exists and belongs to user
- portfolio is not closed
- new name valid and unique among active portfolios

High-level algorithm:
1. **idempotency check**: if a transaction row with `transaction_id = summary_transaction_id` already exists, return success without any further changes
2. lock the portfolio row for update
3. validate ownership and active state
4. normalize new name
5. validate non-empty and unique
6. update `name`, `normalized_name`, `updated_at_utc`
7. write history summary row:
   - `transaction_id = summary_transaction_id`
   - `operation_id = operation_id`
   - `transaction_type = PortfolioRenamed`
   - `summary_text = 'Portfolio renamed to: <new_name>'`
8. commit

### E5.5 RPC: `app.rpc_close_portfolio(portfolio_id, operation_id, summary_transaction_id)`

Purpose:
- Soft delete a portfolio.

Inputs:
- `portfolio_id uuid`
- `operation_id uuid` — client-generated, stable across retries
- `summary_transaction_id uuid` — idempotency anchor

Validation:
- portfolio exists and belongs to user
- portfolio is not already closed

High-level algorithm:
1. **idempotency check**: if a transaction row with `transaction_id = summary_transaction_id` already exists, return success without any further changes
2. lock portfolio row for update
3. validate ownership and active state
4. set `closed_at_utc = now()` and `updated_at_utc = now()`
5. **must** disable all scheduled deposits in that portfolio by setting `is_enabled = false` for every scheduled deposit row in the portfolio, as part of the same database transaction
6. write history summary row:
   - `transaction_id = summary_transaction_id`
   - `operation_id = operation_id`
   - `transaction_type = PortfolioClosed`
   - `summary_text = 'Portfolio closed: <name>'`
7. commit

### E5.6 RPC: `app.rpc_create_fund(portfolio_id, name, initial_amount_agoras, operation_id, summary_transaction_id)`

Purpose:
- Create a fund, optionally with an initial amount.

Inputs:
- `portfolio_id uuid`
- `name text`
- `initial_amount_agoras bigint` — must be `>= 0`
- `operation_id uuid` — client-generated, stable across retries
- `summary_transaction_id uuid` — idempotency anchor

Validation:
- portfolio exists and is active
- name valid and unique within portfolio
- `initial_amount_agoras >= 0`

High-level algorithm:
1. **idempotency check**: if a transaction row with `transaction_id = summary_transaction_id` already exists, return success without any further changes
2. lock or validate the portfolio row
3. normalize name
4. validate non-empty and unique within the portfolio
5. insert new fund with `balance_agoras = 0`
6. write history:
   - summary row:
     - `transaction_id = summary_transaction_id`
     - `operation_id = operation_id`
     - `transaction_type = FundCreated`
     - `summary_text`:
       - if `initial_amount_agoras = 0`: `'Fund created: <name>'`
       - if `initial_amount_agoras > 0`: `'Fund created: <name> with initial balance <initial_amount_agoras>'`
   - detail row: `FundCreated`, `fund_id = new_fund_id`, `amount_agoras = 0`
7. if `initial_amount_agoras > 0`:
   - update fund balance to `initial_amount_agoras`
   - write detail row:
     - `transaction_type = FundDeposit`
     - `amount_agoras = +initial_amount_agoras`
8. commit atomically

Notes:
- initial credit is part of the same logical operation
- the total portfolio balance increases by `initial_amount_agoras`

### E5.7 RPC: `app.rpc_rename_fund(portfolio_id, fund_id, new_name, operation_id, summary_transaction_id)`

Inputs:
- `portfolio_id uuid`
- `fund_id uuid`
- `new_name text`
- `operation_id uuid` — client-generated, stable across retries
- `summary_transaction_id uuid` — idempotency anchor

Validation:
- portfolio exists and is active
- fund exists in that portfolio
- name valid and unique within portfolio

High-level algorithm:
1. **idempotency check**: if a transaction row with `transaction_id = summary_transaction_id` already exists, return success without any further changes
2. lock fund row for update
3. validate portfolio and fund ownership
4. normalize and validate new name
5. update `name`, `normalized_name`, `updated_at_utc`
6. write history:
   - summary row:
     - `transaction_id = summary_transaction_id`
     - `operation_id = operation_id`
     - `transaction_type = FundRenamed`
     - `summary_text = 'Fund renamed to: <new_name>'`
   - detail row: `FundRenamed`, `fund_id = fund_id`, `amount_agoras = 0`
7. commit

### E5.8 RPC: `app.rpc_delete_fund(portfolio_id, fund_id, operation_id, summary_transaction_id)`

Inputs:
- `portfolio_id uuid`
- `fund_id uuid`
- `operation_id uuid` — client-generated, stable across retries
- `summary_transaction_id uuid` — idempotency anchor

Validation:
- portfolio exists and is active
- fund exists in that portfolio
- fund balance is zero
- no enabled scheduled deposit targets that fund

High-level algorithm:
1. **idempotency check**: if a transaction row with `transaction_id = summary_transaction_id` already exists, return success without any further changes
2. lock fund row for update
3. validate `balance_agoras = 0`; if not, raise `ERR_VALIDATION:FUND_NOT_EMPTY`
4. query enabled scheduled deposits for the same fund
5. if any exist, fail with `ERR_VALIDATION:FUND_HAS_ENABLED_SCHEDULED_DEPOSIT`
6. insert row into `deleted_funds`
7. delete the row from `funds`
8. write history:
   - summary row:
     - `transaction_id = summary_transaction_id`
     - `operation_id = operation_id`
     - `transaction_type = FundDeleted`
     - `summary_text = 'Fund deleted: <n>'`
   - detail row: `FundDeleted`, `fund_id = deleted_fund_id`, `amount_agoras = 0`
9. commit

### E5.9 Derived allocation display
For UI and export only:
- if total > 0, `allocation_percent = balance_agoras / total_agoras`
- if total = 0, all displayed allocation percents are 0

### E5.10 Sorting requirements
Default fund ordering:
- by `normalized_name` ascending

Optional UI sort modes:
- name
- balance
- allocation percent
- created date

These sort modes are for presentation only. No money algorithm may depend on UI sort.

---

## E6. Fund Operations

### E6.1 Transaction types used in fund operations
The authoritative list of all `transaction_type` values — including those used in fund operations — is defined in §E5.2. This section does not duplicate that list.

Fund operations use only the types whose commit path is `rpc_commit_fund_operation` in §E5.2.

### E6.2 Design principles
- integer agoras only
- deterministic results across platforms
- exact total preservation after each operation
- no hidden target logic
- all balance-changing operations are atomic and logged

### E6.3 Shared validation rules
For every fund operation:
- portfolio exists and is active
- referenced funds exist and belong to that portfolio
- all arithmetic is overflow-checked
- resulting balances must not be negative
- after success:
  - `portfolio_total = sum(fund balances)` must hold exactly

### E6.4 Deterministic bankers rounding for signed values
This rule is required for proportional scaling.

Given a rational value `numerator / denominator`, where `denominator > 0`:
1. let `sign = +1` if `numerator >= 0`, else `-1`
2. let `absolute_numerator = abs(numerator)`
3. compute:
   - `q = absolute_numerator / denominator`
   - `r = absolute_numerator % denominator`
4. determine `rounded_absolute`:
   - if `2*r < denominator`, result is `q`
   - if `2*r > denominator`, result is `q + 1`
   - if `2*r == denominator`, result is:
     - `q` if `q` is even
     - `q + 1` if `q` is odd
5. apply the sign back:
   - `rounded = sign * rounded_absolute`

Cross-platform requirement:
- C# and Kotlin implementations must follow this exact definition
- do not rely on language-specific negative division quirks

### E6.5 Deterministic remainder rule
Whenever proportional rounding produces a remainder mismatch, fix it deterministically.

Canonical order:
- funds sorted by `fund_id` ascending

Adjustment rule:
- if remainder is positive, add `+1` agora to the first `remainder` funds in order
- if remainder is negative, subtract `1` agora from the first `abs(remainder)` funds in order whose provisional new balance is greater than zero

Edge case — insufficient qualifying funds for negative remainder:
- if fewer funds with `provisional_new_balance > 0` exist than `abs(remainder)` requires, the operation must be rejected before any commit with `ERR_INVARIANT:NEGATIVE_BALANCE`
- clients must validate this condition before calling `rpc_commit_fund_operation`

This guarantees:
- exact final sum
- deterministic behavior across platforms

### E6.6 Common commit pattern
All fund operations follow this logical pattern:
1. read and validate current state
2. compute resulting per-fund deltas on the client or server-side caller logic
3. call `app.rpc_commit_fund_operation(...)`
4. server locks affected rows, re-validates invariants, applies deltas, writes history, and commits
5. client refreshes state and history

### E6.7 RPC: `app.rpc_commit_fund_operation(...)`

Recommended input shape:
- `portfolio_id uuid`
- `operation_id uuid`
- `summary_transaction_id uuid`
- `summary_transaction_type text`
- `summary_text text`
- `note text null`
- `created_by_device_id uuid null`
- `client_app text null`
- `client_version text null`
- `details jsonb`

Each detail item contains:
- `transaction_id uuid`
- `transaction_type text`
- `fund_id uuid`
- `amount_agoras bigint`
- `before_balance_agoras bigint null`
- `after_balance_agoras bigint null`
- `undo_of_operation_id uuid null`

Server behavior:
1. validate ownership and active portfolio
2. if `summary_transaction_id` already exists, return success without applying anything again
3. lock all affected funds `for update`
4. compute new balances from current database state and requested deltas
5. reject if any new balance would be negative
6. apply fund updates
7. insert one summary row and all detail rows with the same `committed_at_utc`
8. re-check total invariant by summing fund balances for that portfolio; if the sum does not equal the expected total, roll back and raise `ERR_INVARIANT:TOTAL_MISMATCH` — this indicates a bug in client-side delta computation
9. commit or roll back everything

### E6.8 Operation: Deposit into fund
Purpose:
- Increase one fund balance by a positive amount.

Inputs:
- `portfolio_id`
- `fund_id`
- `amount_agoras > 0`

High-level algorithm:
1. validate fund exists and belongs to active portfolio
2. compute:
   - `delta = +amount_agoras`
   - `new_balance = old_balance + amount_agoras`
3. create `operation_id`
4. call `rpc_commit_fund_operation` with:
   - summary type `FundDeposit`
   - summary text such as `Deposit into fund`
   - one detail row:
     - `transaction_type = FundDeposit`
     - `amount_agoras = +amount_agoras`
5. after commit, the portfolio total is larger by `amount_agoras`

### E6.9 Operation: Withdraw from fund
Purpose:
- Decrease one fund balance by a positive amount.

Inputs:
- `portfolio_id`
- `fund_id`
- `amount_agoras > 0`

Validation:
- `old_balance >= amount_agoras`

High-level algorithm:
1. validate fund and ownership
2. validate sufficient funds
3. compute:
   - `delta = -amount_agoras`
4. create `operation_id`
5. call `rpc_commit_fund_operation` with:
   - summary type `FundWithdrawal`
   - one detail row with `amount_agoras = -amount_agoras`
6. after commit, the portfolio total is smaller by `amount_agoras`

### E6.10 Operation: Transfer between funds
Purpose:
- Move money from one fund to another without changing the portfolio total.

Inputs:
- `portfolio_id`
- `source_fund_id`
- `destination_fund_id`
- `amount_agoras > 0`

Validation:
- source and destination are different
- source balance is sufficient

High-level algorithm:
1. validate both funds belong to the same active portfolio
2. validate source != destination
3. validate source has enough balance
4. compute two deltas:
   - source: `-amount_agoras`
   - destination: `+amount_agoras`
5. create `operation_id`
6. call `rpc_commit_fund_operation` with:
   - summary type `Transfer`
   - two detail rows:
     - `TransferDebit`
     - `TransferCredit`
7. after commit, the total portfolio balance is unchanged

### E6.11 Operation: Revalue portfolio
Purpose:
- Change the total portfolio value while preserving current ownership proportions as closely as possible.

Typical use cases:
- investment growth
- investment loss
- fees affecting the pooled total
- manual correction of externally tracked total

Inputs:
- `portfolio_id`
- `new_total_agoras > 0`

Preconditions:
- the portfolio has at least one fund
- current total is greater than zero; if not, raise `ERR_VALIDATION:PORTFOLIO_TOTAL_IS_ZERO`

Definitions:
- `old_total_agoras = sum(old balances)`
- `old_balance_i` is the current balance of fund `i`

High-level algorithm:
1. validate portfolio active and contains at least one fund
2. compute `old_total_agoras`
3. if `new_total_agoras == old_total_agoras`:
   - treat as no-op
   - do not log anything
4. for each fund `i`, compute provisional new balance:
   - `provisional_new_balance_i = bankers_round(new_total_agoras * old_balance_i / old_total_agoras)`
5. compute provisional sum:
   - `provisional_sum = sum(provisional_new_balance_i)`
6. compute remainder:
   - `remainder = new_total_agoras - provisional_sum`
7. sort funds by `fund_id` ascending
8. fix remainder deterministically:
   - if `remainder > 0`, add `+1` to the first `remainder` funds in order
   - if `remainder < 0`, subtract `1` from the first `abs(remainder)` funds in order whose provisional new balance is greater than zero
9. after the fix, define final `new_balance_i`
10. compute per-fund deltas:
   - `delta_i = new_balance_i - old_balance_i`
11. create one `operation_id`
12. call `rpc_commit_fund_operation` with:
   - summary type `PortfolioRevalued`
   - summary text such as `Revalue portfolio total from <old> to <new>`
   - one detail row per fund with non-zero delta:
     - `RevaluationCredit` when `delta_i > 0`
     - `RevaluationDebit` when `delta_i < 0`
13. after commit, exact invariant must hold:
   - `sum(new balances) == new_total_agoras`

Important notes:
- this operation preserves the current relative ownership structure, not any target configuration
- this is the correct operation for pooled investment growth and loss in this product model

### E6.12 Operation: Undo (history-based, per-operation)
Purpose:
- Reverse any undoable operation visible in the transaction history panel.

Model:
- each undoable operation in the history panel shows an "undo" button (↩) in its row
- the button is visible only when:
  1. the operation type is one of the supported kinds listed below
  2. no other operation in history references this operation’s `operation_id` via `undo_of_operation_id` (i.e., it has not already been undone)
- undo operations themselves are **not** undoable ("undo of undo" is not supported)

Supported original operation kinds for undo:
- Deposit into fund (`FundDeposit`)
- Withdraw from fund (`FundWithdrawal`)
- Transfer (`Transfer`)
- Revalue portfolio (`PortfolioRevalued`)

Scheduled deposit executions (`ScheduledDepositExecuted`) are **not** undoable. They are automatic system actions, not direct user inputs.

Detection of "already undone":
- after loading full history, collect all `undo_of_operation_id` values from all transaction groups
- any `operation_id` present in that set has been undone and must not show the undo button
- this is computed client-side from the loaded data; no extra server query is needed

High-level algorithm:
1. user clicks the undo button on a specific operation row in the history panel
2. query the original detail rows for that operation from history
3. create `undo_operation_id`
4. for each original detail row:
   - `compensating_amount = -original_amount_agoras`
5. validate that applying all compensating amounts would not create negative balances
6. call `rpc_commit_fund_operation` with:
   - summary type `Undo`
   - detail rows with `transaction_type = Undo`
   - `undo_of_operation_id = original_operation_id`
7. reload the history panel — the undone operation’s undo button disappears; the new Undo entry appears
8. reload the fund list to reflect updated balances

---

## E7. Transactions Log and History

### E7.1 Purpose
The transactions log is the immutable audit trail for both:
- fund operations
- structural operations

### E7.2 Summary and detail model
Every logical user action is grouped by `operation_id`.

Required structure:
- exactly one summary row
- zero or more detail rows

Summary row:
- `record_kind = Summary`
- `fund_id = null`
- contains the user-facing summary of the action

Detail row:
- `record_kind = Detail`
- `fund_id != null`
- contains the per-fund effect

### E7.3 Structural events in fund-filtered history
Structural events that affect a fund must also write a fund detail row with `amount_agoras = 0`.

Reason:
- when the user filters history by fund, they must see events such as:
  - fund created
  - fund renamed
  - fund deleted

### E7.4 Idempotency
Idempotency is required for all operations — both fund operations and structural operations.

The client generates stable UUIDs for:
- `operation_id` — groups all history rows for one logical action
- `summary_transaction_id` — the `transaction_id` of the single summary row; this is the idempotency anchor

These UUIDs must be generated once per user action and reused unchanged on every retry of the same action (e.g., after a network timeout).

Idempotency rule (applied in every RPC):
- if a `transactions` row with `transaction_id = summary_transaction_id` already exists, the RPC returns success immediately with no state changes

This applies to:
- `rpc_commit_fund_operation` (fund operations)
- `rpc_create_portfolio`, `rpc_rename_portfolio`, `rpc_close_portfolio` (portfolio structural RPCs)
- `rpc_create_fund`, `rpc_rename_fund`, `rpc_delete_fund` (fund structural RPCs)

Client behavior:
- a `DUPLICATE_NAME` error returned on retry of a create-style RPC should **not** be treated as an idempotency success, because the idempotency check runs before the uniqueness check and would have caught a true retry before reaching it

### E7.5 Timestamp rule
All rows of one operation must share the same server-generated `committed_at_utc`.

### E7.6 History feed requirements
Default feed:
- full history for one portfolio
- sorted newest first by:
  - `committed_at_utc desc`
  - `operation_id`
  - `transaction_id`

Required filters:
- date range
- fund — an operation matches if any of its detail rows has `fund_id = X`
- transaction type — filter is applied to the **summary row's** `transaction_type`; an operation matches if its single summary row has the selected type; detail-only types (e.g. `TransferCredit`) do not appear as standalone filter options

Recommended UI behavior:
- show summary row in the main list
- expand or navigate to show detail rows

### E7.7 Name resolution for deleted funds
When showing a history detail row:
1. if `fund_id` exists in `funds`, show the current fund name
2. otherwise if it exists in `deleted_funds`, show the tombstone name
3. otherwise show a generic deleted-fund label

### E7.8 History retention
- keep forever
- no client-side edit or delete
- corrections are represented as new compensating rows only

### E7.9 Optional diagnostic integrity check
The summary row may optionally store expected detail count in metadata.
This can help detect corruption in debug builds.

---

## E8. Scheduled Deposits

### E8.1 Scope
Scheduled deposits support one capability only:
- credit a fund by a positive amount

No support for:
- debit scheduled deposits
- transfer scheduled deposits
- run now
- snooze
- skip next
- end date

### E8.2 Schedule model
Supported schedule kinds:
- `OneTime`
- `Daily`
- `Weekly`
- `Monthly`

Field rules per schedule kind:
- `OneTime`:
  - `next_run_at_utc` set to the desired execution time (no separate `one_time_at_utc` field)
  - `time_of_day_minutes` null
  - `weekday_mask` null
  - `day_of_month` null
- `Daily`:
  - `time_of_day_minutes` required
  - `weekday_mask` null
  - `day_of_month` null
- `Weekly`:
  - `time_of_day_minutes` required
  - `weekday_mask` required (see §E4.3.5 for bitmask encoding)
  - `day_of_month` null
- `Monthly`:
  - `time_of_day_minutes` required
  - `day_of_month` required (1–28)
  - `weekday_mask` null

### E8.3 Timezone rule
Schedules are authored and interpreted in Israel time.
Stored timestamps remain UTC.

### E8.4 Device responsibilities
Each app installation must:
- create and persist a stable `device_id`
- trigger scheduled deposit execution:
  - on login
  - on app start
  - on app resume
  - optionally every few minutes while foregrounded

The client must never directly mutate balances for scheduled deposits.

### E8.5 Exactly-once model
Exactly-once behavior is enforced by:
- unique `(scheduled_deposit_id, scheduled_for_utc)`
- claim state transitions
- claim timeout recovery
- atomic money mutation + history insert + occurrence completion

Statuses:
- Pending
- Claimed
- Done

Claim timeout:
- `CLAIM_TTL = 10 minutes`

No `Failed` status is used.

### E8.6 Catch-up rule
When a device triggers execution, the backend executes overdue occurrences where:
- `scheduled_for_utc <= now_utc`

Processing order:
- oldest first

Hard cap:
- `MAX_CATCH_UP_OCCURRENCES_PER_TRIGGER = 30`

### E8.7 RPC: `app.rpc_upsert_scheduled_deposit(...)`
Purpose:
- create or update a scheduled deposit
- this is also the mechanism for enabling and disabling a scheduled deposit: pass `is_enabled = true` or `is_enabled = false` as part of the upsert; there is no separate enable/disable RPC

Validation:
- portfolio exists and is active
- target fund exists in the same portfolio
- amount is positive
- schedule fields match the schedule kind (see §E8.2)

High-level algorithm:
1. validate active portfolio
2. validate target fund
3. validate schedule-specific fields
4. compute `next_run_at_utc` (see §E8.12 for advancement rules)
5. insert or update the scheduled deposit row
6. update `updated_at_utc`
7. return scheduled deposit identifier and next run time

### E8.8 RPC: `app.rpc_delete_scheduled_deposit(scheduled_deposit_id)`
High-level algorithm:
1. validate ownership and active portfolio
2. delete the scheduled deposit row
3. no existing history rows are touched
4. past occurrence rows may remain for audit

### E8.9 RPC: `app.rpc_execute_due_scheduled_deposits(portfolio_id, now_utc, device_id)`
Purpose:
- device-triggered, exactly-once execution of due scheduled deposits

High-level algorithm per due scheduled deposit:
1. validate portfolio exists, belongs to user, and is active
2. select enabled scheduled deposits with `next_run_at_utc <= now_utc`, ordered by `next_run_at_utc asc` (oldest first, consistent with §E8.6), limited to `MAX_CATCH_UP_OCCURRENCES_PER_TRIGGER = 30`
3. for each due scheduled deposit:
   1. set `scheduled_for_utc = next_run_at_utc`
   2. create the occurrence row if missing, or read the existing one
   3. attempt claim if:
      - status is `Pending`, or
      - status is `Claimed` and older than claim timeout
   4. if claim succeeds:
      - create `operation_id`
      - execute one normal fund credit through `rpc_commit_fund_operation`
      - write summary type `ScheduledDepositExecuted`
      - write detail type `FundDeposit`
      - note on summary row: `SD:<scheduled_deposit_id> <optional text>`
      - set occurrence status to `Done`
      - store `operation_id`
      - advance `next_run_at_utc` to the next scheduled time
      - if schedule kind is `OneTime`, set `is_enabled = false`
4. return executed occurrences

### E8.12 `next_run_at_utc` advancement algorithm

This algorithm is used both at scheduled deposit creation time (to set the initial `next_run_at_utc`) and after each successful execution (to advance to the next occurrence). All time calculations are performed in Israel time (`Asia/Jerusalem`) and then converted to UTC for storage.

#### OneTime
- At creation: `next_run_at_utc` = the user-specified single execution time, converted to UTC.
- After execution: do not advance. Set `is_enabled = false`.

#### Daily
- At creation: find the next calendar day (in Israel time) at `time_of_day_minutes` that is strictly in the future relative to `now_utc`.
  - If today's occurrence at `time_of_day_minutes` is still in the future, use today.
  - Otherwise use tomorrow.
- After execution: advance by exactly one calendar day in Israel time at the same `time_of_day_minutes`, then convert to UTC.
  - Daylight saving transitions are handled by computing the wall-clock time in `Asia/Jerusalem` rather than adding a fixed UTC offset.

#### Weekly
- At creation: find the next occurrence among the selected weekdays (from `weekday_mask`) in Israel time at `time_of_day_minutes` that is strictly in the future relative to `now_utc`.
- After execution: find the next weekday matching `weekday_mask` strictly after the current `scheduled_for_utc` (converted to Israel date), at `time_of_day_minutes` Israel time, then convert to UTC.

#### Monthly
- At creation: find the next occurrence on `day_of_month` in the current or next month in Israel time at `time_of_day_minutes` that is strictly in the future relative to `now_utc`.
- After execution: advance by one calendar month in Israel time. If the target month has fewer than `day_of_month` days, this cannot happen in practice because `day_of_month` is capped at 28.

Cross-platform requirement:
- Both C# and Kotlin must use the `Asia/Jerusalem` IANA timezone for all schedule computations to produce identical `next_run_at_utc` values.
If a crash happens after claim and before completion:
- occurrence stays `Claimed`
- a future trigger may reclaim it after `CLAIM_TTL`
- no duplicate completion is allowed because the occurrence key is unique and status transitions are coordinated in the database

### E8.11 Fund delete interaction
Fund delete must be blocked when an enabled scheduled deposit targets that fund.

Reason:
- prevents future invalid executions
- keeps user intent explicit

---

## E9. Export

### E9.1 Transaction history CSV
The app must support exporting history to CSV.

Recommended columns:
- `committed_at_utc`
- `operation_id`
- `transaction_id`
- `record_kind`
- `transaction_type`
- `portfolio_id`
- `fund_id`
- `fund_name`
- `amount_agoras`
- `summary_text`
- `note`

The export may contain one row per history row.
This is the simplest and most faithful representation.

### E9.2 Portfolio snapshot CSV
Recommended columns:
- `portfolio_id`
- `portfolio_name`
- `fund_id`
- `fund_name`
- `balance_agoras`
- `allocation_percent`

Allocation percent is derived at export time.

---

## E10. Testing and Acceptance Checklist

### E10.1 Authentication
- sign up logs in immediately
- sign in persists session
- sign out clears session
- both devices can stay signed in simultaneously

### E10.2 Structural operations
- create portfolio succeeds and logs history
- rename portfolio updates name and logs history
- close portfolio hides it from active list and blocks future mutations
- create, rename, and delete fund behave according to validation rules

### E10.3 Fund operations
- deposit increases one fund and total
- withdrawal decreases one fund and total
- transfer changes two funds and preserves total
- revaluation rescales all funds proportionally and exact final sum matches input total
- no negative balance is ever committed

### E10.4 Scaling determinism
Given the same input state and `new_total_agoras`, both C# and Kotlin must produce identical final balances.

### E10.5 Undo
- undo creates compensating history rows
- undo does not delete old rows
- undo is history-based: each undoable operation shows an undo button in the transaction history panel
- an operation that has already been undone does not show the undo button
- undo operations themselves are not undoable

### E10.6 History
- every logical action has one summary row
- detail rows have correct fund references
- structure events appear in fund-filtered history
- deleted fund names resolve through tombstones

### E10.7 Scheduled deposits
- create, edit, enable, disable, delete behave correctly
- two devices triggering simultaneously still execute each occurrence once
- crash after claim is recoverable by timeout-based reclaim
- one-time scheduled deposit disables itself after successful execution
- catch-up cap is enforced

### E10.8 Closed portfolios
- closed portfolio is read-only
- mutation RPCs return `ERR_VALIDATION:PORTFOLIO_CLOSED`
- history remains queryable

---

## Appendix A — English–Hebrew Glossary

This glossary maps all common English terms used throughout this document to their Hebrew equivalents. Use these translations consistently across all UI strings.

### Core Domain

| English | Hebrew |
|---------|--------|
| Portfolio | תיק |
| Fund | קרן |
| User | משתמש |
| Device | מכשיר |
| Balance | יתרה |
| Allocation percent | אחוז הקצאה |
| Ownership share | חלק בעלות |

### Currency & Money

| English | Hebrew |
|---------|--------|
| NIS | ש״ח |
| Agora / Agoras | אגורה / אגורות |
| Amount | סכום |
| Total | סה״כ |

### Fund Operations

| English | Hebrew |
|---------|--------|
| Deposit | הפקדה |
| Withdrawal | משיכה |
| Transfer | העברה |
| Revalue / Revaluation | עדכון שווי |
| Undo | ביטול |

### Portfolio Operations

| English | Hebrew |
|---------|--------|
| Create portfolio | יצירת תיק |
| Rename portfolio | שינוי שם תיק |
| Close portfolio | סגירת תיק |
| Active | פעיל |
| Closed | סגור |

### Fund Management

| English | Hebrew |
|---------|--------|
| Create fund | יצירת קרן |
| Rename fund | שינוי שם קרן |
| Delete fund | מחיקת קרן |
| Fund name | שם קרן |
| Initial amount | סכום התחלתי |

### Transaction History

| English | Hebrew |
|---------|--------|
| Transaction | תנועה |
| Transaction history | היסטוריית תנועות |
| Operation | פעולה |
| Summary | סיכום |
| Details | פירוט |
| Note | הערה |
| Date | תאריך |
| Date range | טווח תאריכים |
| Filter | סינון |
| Sort | מיון |

### Scheduled Deposits

| English | Hebrew |
|---------|--------|
| Scheduled deposit | הפקדה מתוזמנת |
| Schedule | תזמון |
| One-time | חד פעמי |
| Daily | יומי |
| Weekly | שבועי |
| Monthly | חודשי |
| Time of day | שעה ביום |
| Day of month | יום בחודש |
| Enabled | מופעל |
| Disabled | מושבת |
| Next run | הרצה הבאה |

### Authentication

| English | Hebrew |
|---------|--------|
| Sign up | הרשמה |
| Sign in | התחברות |
| Sign out | התנתקות |
| Email | אימייל |
| Password | סיסמה |

### Export

| English | Hebrew |
|---------|--------|
| Export | ייצוא |
| Snapshot | תמונת מצב |
| CSV | CSV |

### Validation & Errors

| English | Hebrew |
|---------|--------|
| Error | שגיאה |
| Validation | אימות |
| Name already exists | השם כבר קיים |
| Fund not empty | הקרן אינה ריקה |
| Insufficient balance | יתרה לא מספיקה |
| Portfolio is closed | התיק סגור |
| Name cannot be empty | השם לא יכול להיות ריק |

### General UI

| English | Hebrew |
|---------|--------|
| Save | שמירה |
| Cancel | ביטול |
| Confirm | אישור |
| Delete | מחיקה |
| Edit | עריכה |
| Back | חזרה |
| Settings | הגדרות |
| Name | שם |
| Search | חיפוש |
| Loading | טוען |
| Refresh | רענון |
| From | מ |
| To | אל |

---

## End of Document

This document is the authoritative consolidated specification for the current product model.
