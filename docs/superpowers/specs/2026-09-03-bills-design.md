# Bills & Schedule Bill Detail — Design

Phase 2, sub-project 1 of 4 (Bills / Calendar / Charts trio / Auth). Builds on the Phase 1
foundation (Room + Hilt + Navigation Component + DataBinding, already shipped and pushed).

## Context

The Phase 1 schema already anticipated bills: `TransactionEntity` has a `status` field
(`PAID` / `OVERDUE` / `UPCOMING`) and an `isScheduled` flag, both seeded with sample data but
never surfaced in any screen. This sub-project adds the two screens the original PRD calls for
to expose that data: a Bills list (tabbed by status, searchable) and a Schedule Bill Detail
screen reachable from it, with Approve/Decline actions that actually mutate the transaction.

## Data Model

Add two nullable columns to `TransactionEntity` / `domain.model.Transaction`:
- `payeeName: String?`
- `payeeRole: String?`

These cover the mockup's Schedule Bill Detail screen ("Stephen Thomas" / "House Owner"). No
avatar image field — the avatar is rendered as a generic initials-circle (same visual language
as the existing category-icon circles), not a photo, consistent with Phase 1's "no external
image assets" rule.

`AppDatabase` bumps to `version = 2` with `.fallbackToDestructiveMigration()` on the Room
builder in `di/DatabaseModule.kt`. No real users exist yet, so a destructive migration (wipe +
reseed via the existing `DatabaseSeeder`) is simpler and correct for pre-release software; a
real `Migration` object would be premature. `DatabaseSeeder` adds payee name/role to its
existing UPCOMING and OVERDUE sample transactions so the new screens render populated.

## Domain Layer

New `TransactionDao` queries:
- `getByStatus(status: TransactionStatus): Flow<List<TransactionEntity>>`
- `getById(id: Long): Flow<TransactionEntity?>`
- `suspend fun updateStatus(id: Long, status: TransactionStatus)`
- `suspend fun delete(id: Long)`

`TransactionRepository` / `TransactionRepositoryImpl` gain matching methods.

New use cases (`domain/usecase/`):
- `GetTransactionsByStatusUseCase(status): Flow<List<Transaction>>` — powers the Bills tabs
- `GetTransactionByIdUseCase(id): Flow<Transaction?>` — powers the detail screen
- `UpdateTransactionStatusUseCase(id, status): suspend` — powers Approve
- `DeleteTransactionUseCase(id): suspend` — powers Decline

No edit-transaction use case — out of scope, nothing in the mockups needs it yet.

## Bills List — `ui/bills/BillsFragment` + `BillsViewModel`

Replaces the `placeholderGraphFragment` slot in both `nav_graph.xml` and
`bottom_nav_menu.xml` (new `ic_bills_24` receipt-style vector icon, label "Bills" — kept
distinct from `ic_calendar_24`, reserved for the Calendar sub-project later).

Layout: purple header with a `TabLayout` (3 tabs: Paid / Overdue / Upcoming), a rounded search
box below it (reusing the `bg_rounded_card_16` drawable pattern already used elsewhere), then a
vertical `RecyclerView` of bill rows.

`BillsViewModel` holds `MutableStateFlow<TransactionStatus>` (selected tab) and
`MutableStateFlow<String>` (search text), combined via `flatMapLatest` +
`GetTransactionsByStatusUseCase`, then filtered client-side by title substring (list sizes here
are small — no need for a SQL `LIKE` query). Exposes a `StateFlow<List<TransactionListItem>>`
joining in category for the row icon, same pattern as `WalletViewModel`.

Row layout reuses `item_transaction.xml` as-is. New `BillListAdapter` (plain
`RecyclerView.Adapter`, matching the Phase 1 house style established after the ViewBinding
`id="root"` lesson) takes a click callback instead of being read-only like
`RecentTransactionAdapter`, since rows here navigate to detail.

## Schedule Bill Detail — `ui/billdetail/BillDetailFragment` + `BillDetailViewModel`

New `<fragment>` nav destination (not a bottom-nav item), reached via a Safe Args `<action>`
from `billsFragment` passing `transactionId: Long`. `BillDetailViewModel` collects
`GetTransactionByIdUseCase(transactionId)` joined with category (for consistency with the rest
of the app) into a `StateFlow`.

Layout: calendar-icon badge, amount + title, payee row (initials-circle + name + role,
`goneIf(payeeName == null)`-style visibility so non-bill transactions reached here degrade
gracefully), "Scheduled for <date>", masked card info (reusing `CardRepository`/`GetCardsUseCase`
already in place to resolve `cardId` → masked number), Decline (red, outlined) / Approve
(purple, filled) buttons side by side at the bottom.

Approve → `UpdateTransactionStatusUseCase(id, PAID)`, Decline → `DeleteTransactionUseCase(id)`;
both are one-shot suspend calls launched from `viewLifecycleOwner.lifecycleScope`, then
`findNavController().popBackStack()`. No dedicated event/StateFlow plumbing needed — there's no
form validation on this screen, unlike Add-Transaction.

## Navigation Changes

- `nav_graph.xml`: `placeholderGraphFragment` → `billsFragment`; new `billDetailFragment`
  destination with a `transactionId` long argument; new `<action>` on `billsFragment` to
  `billDetailFragment`.
- `bottom_nav_menu.xml`: same slot, new icon + "Bills" label.
- `strings.xml`: `nav_graph` → `nav_bills` (or repurpose in place).

## Testing / Verification

Same process as every Phase 1 screen: `watch_build.ps1` auto-builds+installs on save; adb-driven
walkthrough covering tab switching, live search filtering, tap-through to detail, Approve moving
a row live from Upcoming/Overdue to Paid, Decline removing a row; screenshots checked for visual
correctness against the mockup; `testDebugUnitTest` + `lintDebug` clean before commit.

## Explicitly Out of Scope

Month-scoped "Paid in July"-style tab labels (PRD mockup detail, not essential), a real filter
sheet behind the search bar's filter icon (icon shown, inert), editing a bill's amount/title/date
after creation, recurring-bill scheduling logic (nothing currently *creates* an UPCOMING bill
programmatically — they only exist via seed data for now, same as Phase 1's Add-Transaction
always creating PAID transactions).
