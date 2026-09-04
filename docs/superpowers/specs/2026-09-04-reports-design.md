# Reports (Expense Chart / Budget Planner / Billing Reports) — Design

Phase 2, sub-project 3 of 4 (Bills / Calendar / **Charts trio** / Auth). Builds on Phase 1 plus the
Bills and Calendar sub-projects, all shipped and merged to `master`.

## Context

The PRD's three chart screens — Expense Chart, Budget Planner, Billing Reports — are the last
substantial feature before Auth. The app currently has no visualisation at all: the Graph
bottom-nav placeholder was consumed by Bills, and Home shows only flat progress bars.

Almost all the data these screens need already exists. `GetBudgetSummaryUseCase(start, end)`
returns `totalEarned`, `totalSpent`, `totalBudget`, `remaining`, and a `categoryBreakdown` of
`CategorySpend(category, spent)` sorted descending; `Category` already carries a nullable
`budgetLimit` and a `colorHex`. The only genuinely new data-layer capability is **writing** a
budget back.

## Scope: one screen, three tabs

The three "screens" ship as a single `reportsFragment` destination hosting three tabs, not as
three peer destinations. They share one data source, one period selection, and one navigation
slot — and solving "where does this live" once rather than three times matters, because the
bottom nav is full.

**Tab hosting:** a `TabLayout` plus a `FrameLayout` container, swapping one of three child
fragments through `childFragmentManager` on tab select. Deliberately **not** `ViewPager2` —
it isn't currently a dependency, and this sub-project adds none. The alternative of three
sections inside one fragment was rejected: it would produce a ~350-line fragment and an
unwieldy layout, where three child fragments stay focused and independently readable.

## Navigation

Reports takes the **Settings** bottom-nav slot, exactly as Bills took Graph's. Settings is still
an inert placeholder; Reports is a real destination users would visit regularly.

This orphans the placeholder machinery completely — `placeholderSettingsFragment` was its last
consumer. Delete all of it:

- `ui/placeholder/PlaceholderFragment.kt`
- `res/layout/fragment_placeholder.xml`
- the `nav_settings` string

**Follow-up this creates:** Settings now has nowhere to live. When a real Settings screen is
built it will need an entry point — most likely an icon in Home's header beside Calendar's.
That is out of scope here but should not be forgotten.

## Shared month arithmetic

Billing Reports needs month boundaries and month labels, which `ui/calendar/CalendarMonth`
already implements and has 12 passing tests for. Rather than write a second copy, extract the
generic helpers — `monthStart`, `nextMonthStart`, `previousMonthStart`, `dayStart`,
`sameDayInMonth`, `label` — into `ui/common/util/MonthRange.kt`, beside the existing
`DateFormatter` and `CurrencyFormatter`.

`CalendarMonth` keeps `DayCell` and `cellsFor`, delegating to `MonthRange` for arithmetic. The
existing tests move with the code and must still pass unchanged — that is the safety net for
this refactor. Same constraints apply as in Calendar: **`java.util.Calendar` only, no
`java.time`** (`minSdk` is 24 with no desugaring), and **never bucket by
`millis / 86_400_000`**.

This is the only change to already-shipped code, and it is a move, not a rewrite.

## Data Layer

The only new capability, all in service of editable budgets:

- `CategoryDao`: `@Query("UPDATE categories SET budgetLimit = :limit WHERE id = :id") suspend fun updateBudget(id: Long, limit: Double?)`
- `CategoryRepository` / `CategoryRepositoryImpl`: matching `suspend fun updateBudget(id: Long, limit: Double?)`
- `domain/usecase/UpdateCategoryBudgetUseCase`: `suspend operator fun invoke(id: Long, limit: Double?)`

**No schema change and no Room version bump.** `budgetLimit` already exists as a nullable column
on `categories`; this only writes to it. The destructive migration stays untriggered, so seeded
data survives — a deliberate contrast with Bills, which did bump the version.

No new query is needed for the month-over-month chart: it reuses `GetTransactionsForPeriodUseCase`
over the whole six-month span.

## Reports host — `ui/reports/ReportsFragment` + `ReportsViewModel`

Layout: purple header with the title and a month selector (‹ label ›, matching Calendar's
arrows and reusing `ic_back_24` with `android:rotation="180"` for forward), a `TabLayout` with
three tabs, and a `FrameLayout` container beneath.

`ReportsViewModel` owns exactly one piece of state — `selectedMonthStart: StateFlow<Long>`,
defaulting to the current month — plus `previousMonth()` / `nextMonth()`. Child fragments read
it via `viewModels(ownerProducer = { requireParentFragment() })` so all three tabs stay on the
same month.

The `TabLayout` must be re-synced from the ViewModel in `onViewCreated`, and given
`android:background="@android:color/transparent"` — both lessons from Bills, where a recreated
TabLayout desynced from its retained ViewModel and where Material3's own surface background hid
the selected tab's white label.

## Tab 1 — Expense Chart (`ui/reports/expense/`)

A `DonutChartView` of the selected month's category spend, with the month's total in the middle,
above a legend list of category name / amount / percentage. Segment colours come from
`Category.colorHex`, which the seeder already populates.

Reads `GetBudgetSummaryUseCase(monthStart, monthEnd)` and uses `categoryBreakdown` as-is — it is
already filtered to expenses with `spent > 0` and sorted descending.

Empty state when the month has no expenses: hide the donut, show "No expenses this month".

## Tab 2 — Budget Planner (`ui/reports/budget/`)

Every expense category listed with spent-versus-limit on a `LinearProgressIndicator` — the widget
Home's Monthly Budget cards already use, so this tab needs no custom drawing at all. Bars run
`purple_primary` under budget and `soft_red` at or over it. Categories with no limit set show
"No budget set" instead of a bar.

Tapping a row opens a small `AlertDialog` with a numeric `EditText` to set that category's
monthly limit, committed through `UpdateCategoryBudgetUseCase`. Clearing the field writes `null`,
returning the category to "no budget set". Room's Flow makes the list update itself; no manual
refresh.

Unlike Home's cards, this lists **all** expense categories, including those with zero spend — the
point of a planner is to set limits on categories you have not spent on yet.

## Tab 3 — Billing Reports (`ui/reports/billing/`)

A `BarChartView` of spend and income across the **six months ending at the selected month**, with
that period's totals beneath it. So the shared month selector drives this tab too: it moves the
six-month window rather than being ignored.

**One** query — `GetTransactionsForPeriodUseCase(sixMonthsBackStart, selectedMonthEnd)` — with
bucketing by month done in memory. Not six separate flows. The bucketing is a pure function so it
can be tested directly.

## Custom views — `ui/common/view/`

Two small `View` subclasses, both keeping geometry in pure functions so the maths is unit-testable
without instrumentation:

- **`DonutChartView`** — `drawArc` over a list of (value, colour). A pure
  `sweepAngles(values: List<Double>): List<Float>` converts values to angles.
- **`BarChartView`** — `drawRect` over a list of (label, expense, income), drawn as a pair of
  bars per month. A pure `barHeights(values: List<Double>, maxHeightPx: Float): List<Float>`
  normalises to the largest value. It is called **once over every expense and income figure
  together**, not per series — normalising each series to its own max would make a £100 income
  bar the same height as a £5,000 expense bar and render the chart meaningless.

Both must handle a zero total and an empty list without dividing by zero — that is the specific
failure these tests exist to prevent.

## Testing / Verification

JUnit on the pure logic, which is where the real risk is:

- `sweepAngles`: angles sum to 360°; proportions are correct for uneven values; a single value
  yields one full circle; an empty list and an all-zero list return empty rather than dividing
  by zero
- `barHeights`: tallest value maps to full height, others scale proportionally; all-zero input
  returns zeros rather than NaN
- Month bucketing: transactions land in the right month across a year boundary; months with no
  transactions still appear as zero bars rather than being dropped
- `UpdateCategoryBudgetUseCase`: sets a limit, and clearing it writes `null` — using a
  `FakeCategoryRepository` in the style of the existing `FakeTransactionRepository`
- `CalendarMonth`'s existing 12 tests must still pass unchanged after the `MonthRange` extraction

Then the standard process: `watch_build.ps1` auto-builds on save; an adb-driven walkthrough
covering all three tabs, the shared month selector moving all of them, editing and clearing a
budget, and the empty states; `testDebugUnitTest` + `lintDebug` clean before the PR.

## Explicitly Out of Scope

Exporting, sharing or printing reports; custom date ranges beyond the month selector and the
fixed six-month window; per-category budget history or trends; budgets on income categories;
chart animations, touch interaction, or tap-to-drill-down; a real Settings screen to replace the
nav slot Reports takes.
