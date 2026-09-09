# Category Detail — Design

Post-Phase-2 sub-project. Follows Settings ([PR #11](https://github.com/TanerKececi/DailyExpenseTracker/pull/11))
and dark mode ([PR #13](https://github.com/TanerKececi/DailyExpenseTracker/pull/13)).

## Context

Most of the app's list items are decorative. An audit of every adapter found **five with no click
handling at all**:

| Adapter | Screen | What a user would expect |
|---|---|---|
| `CategoryGridAdapter` | Categories | the whole screen's content — nine tiles, none tappable |
| `TopSpendingAdapter` | Home | the five most-spent categories |
| `MonthlyBudgetAdapter` | Home | per-category budget cards |
| `CardCarouselAdapter` | My Wallet | payment cards |
| `LegendAdapter` | Reports → Expenses | chart legend rows |

Bills, Calendar, Settings, Bill Detail and the Add-Transaction sheet are fully interactive. The gap
is concentrated in the display-only screens — Categories has nothing tappable but its
Expense/Income toggle.

The cause is visible in the code: every adapter that got a click callback was one a feature
*required* (edit a transaction, pick a day, edit a budget limit). The purely presentational ones
were never given a destination, because nothing forced the question of what tapping them should do.

That question is the actual design work, and the answer is a **Category Detail screen**: one
destination that gives three of the five dead surfaces something sensible to do.

**Scope for this sub-project is Categories and Home.** The Wallet card carousel and the Reports
legend stay inert — see Explicitly Out of Scope.

## The screen — `ui/categorydetail/`

A purple header carrying the category's icon and name, its spend for the selected month shown
against its budget limit, and a month selector (`‹ September 2026 ›`) matching the one Reports and
Calendar already use. Below it, that category's transactions for the month.

Tapping a transaction opens the Add-Transaction sheet in edit mode, exactly as Wallet and Calendar
already do.

An unknown or deleted `categoryId` renders an empty state rather than crashing — the same shape
`BillDetailViewModel` already uses for a missing transaction.

**Income categories are not a special case, but budget is.** The header total is simply the sum of
that category's transactions for the month, whichever type they are. `budgetLimit` is nullable and
is null for income categories (and for any expense category with no limit set), so the
"of €X" budget portion is **hidden whenever the limit is null** rather than rendering "of €0.00".
The list itself behaves identically either way.

## Reuse, not new code

The screen is cheap because almost everything it needs exists.

**No new adapter.** `RecentTransactionAdapter` already takes an optional `onClick` and is already
shared by Wallet, Bills and Calendar. Its KDoc states *"Rows are inert unless `onClick` is
supplied"* — this is simply its fourth consumer.

**No new DAO query and no new use case.** `GetTransactionsForPeriodUseCase` returns a month's
transactions; the ViewModel filters them by `categoryId`. Every transaction query in this project is
deliberately bounded by date range or status (HANDOFF.md records this), and filtering one month's
rows in memory preserves that. A `WHERE categoryId = :id` query would add a data-layer method to
avoid a filter over a few dozen rows.

**Month arithmetic** is `MonthRange`, shared with Reports and Calendar. **Category metadata** —
name, icon, colour, `budgetLimit` — comes from the existing `GetCategoriesUseCase`. Spend is the sum
of the filtered list.

So the new production code is one fragment, one ViewModel, one layout.

## The three adapters gain an optional callback

`CategoryGridAdapter`, `TopSpendingAdapter` and `MonthlyBudgetAdapter` each take an
`onClick: ((T) -> Unit)? = null` constructor parameter, following the pattern
`RecentTransactionAdapter` already documents. The grid and Top Spending pass a `Category`; the
budget cards pass a `CategorySpend`.

Defaulting to `null` means every existing call site compiles unchanged, and an adapter reused
somewhere without a destination stays inert rather than being forced to invent one.

## Navigation

A `categoryDetailFragment` destination with a `categoryId: Long` Safe Args argument, plus two
actions — `action_homeFragment_to_categoryDetailFragment` and
`action_categoriesFragment_to_categoryDetailFragment`. Three entry points (Top Spending, Monthly
Budget, the Categories grid) resolve to one destination.

`MainActivity` has no destination-changed listener, so the bottom nav and FAB remain visible here as
they do on Calendar, Bill Detail and Settings. That is pre-existing behaviour and is not changed.

## Testing / Verification

`CategoryDetailViewModel` tested against its `StateFlow` in the pattern established by
[PR #10](https://github.com/TanerKececi/DailyExpenseTracker/pull/10) — `MainDispatcherRule`, the
`subscribe()` helper, the existing fake repositories:

- Only the requested category's transactions appear; another category's are excluded.
- The header total is the sum of that category's transactions for the month, and the budget portion is hidden when `budgetLimit` is null.
- Transactions outside the selected month are excluded, and month navigation re-queries.
- An unknown `categoryId` yields the empty state rather than throwing.
- The category's name and `budgetLimit` reach the state.

Emulator walkthrough: tap a tile in Categories, a circle in Top Spending, and a Monthly Budget card
— all three land on the right category. Confirm the month selector moves the list, that tapping a
transaction opens the editor prefilled, and that the screen reads correctly in **both themes**
(dark mode shipped in #13; a new layout is exactly where the `@color/white` versus `@color/surface`
trap resurfaces).

`testDebugUnitTest` and `lintDebug` clean. CI runs both on the pull request.

## Explicitly Out of Scope

- **`CardCarouselAdapter`** (Wallet payment cards). Tapping a card implies a card-detail or
  card-filtered view, which is a different destination and a separate decision.
- **`LegendAdapter`** (Reports chart legend). It would become a one-line change once this screen
  exists, since it is the same destination — deliberately deferred rather than bundled.
- Making the donut or bar chart itself interactive; already out of scope in the Reports spec.
- Editing or creating categories from the grid.
- Any filter beyond category-and-month.
