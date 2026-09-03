# Calendar — Design

Phase 2, sub-project 2 of 4 (Bills / **Calendar** / Charts trio / Auth). Builds on the Phase 1
foundation and the Bills sub-project, both shipped and merged to `master`.

## Context

Every transaction already carries a `date` (epoch millis), and `TransactionDao.getByDateRange`
plus `GetTransactionsForPeriodUseCase(start, end)` already query by date range — Phase 1 built
them for the Home dashboard's "this month" figures. Nothing in the app presents that data
*as* a calendar, so a user cannot answer "what did I spend, and when?" without scrolling the
Wallet list.

This sub-project adds one screen: a month grid where each day carries a spend marker, and
tapping a day lists that day's transactions beneath it.

**This is a UI-layer-only change.** No new DAO queries, repository methods, use cases, entities,
or database version bump. That is the main reason Calendar was chosen ahead of the charts trio
and Auth.

## Entry Point & Navigation

The bottom navigation's five slots are full (Home / Bills / Wallet / Categories / Settings) and
Material guidance caps it at five, so Calendar is **not** a bottom-nav destination. Instead:

- `fragment_home.xml` gains an `ivCalendar` `ImageView` in its purple header (`ic_calendar_24`,
  already in `res/drawable`, tinted white via `app:tint`).
- `nav_graph.xml` gains a `calendarFragment` destination and an `<action>` from `homeFragment`.

The action takes no arguments — Calendar always opens on the current month. This mirrors how
`billDetailFragment` is a plain `<fragment>` destination reached by Safe Args rather than a
nav-bar entry.

Settings keeps its slot; repurposing it for Calendar would leave a real future screen homeless.

## Date Handling

`minSdk` is 24. `java.time.LocalDate` / `YearMonth` require API 26 or core-library desugaring,
and this project has not enabled desugaring. Phase 1 already standardised on `java.util.Calendar`
and `SimpleDateFormat` (`DatabaseSeeder`, `ui/common/util/DateFormatter`), so Calendar does the
same. **Do not introduce `java.time` or enable desugaring for this sub-project.**

Two consequences worth stating explicitly, because both are easy to get wrong:

- **Day bucketing goes through `Calendar` in the device's local timezone.** `Transaction.date` is
  raw epoch millis; bucketing with `millis / 86_400_000` would drift by the UTC offset and break
  across DST boundaries, putting late-evening transactions on the wrong day.
- **The week is fixed to start on Sunday.** `Calendar.getInstance().firstDayOfWeek` is
  locale-dependent, so a locale-derived value would desynchronise from a static weekday header
  row. Leading blanks are computed as `dayOfWeek - Calendar.SUNDAY`, and the header row is a
  static S/M/T/W/T/F/S. Locale-aware week start is out of scope; mark the assumption with a
  `ponytail:` comment naming it.

## Month Model — `ui/calendar/CalendarMonth.kt`

The only non-trivial logic in this sub-project, deliberately extracted from the ViewModel as a
pure function so it can be unit-tested on the JVM with no Robolectric and no fake repository:

```kotlin
data class DayCell(
    val dayOfMonth: Int?,      // null for leading blanks
    val dateMillis: Long?,     // null for leading blanks
    val hasExpense: Boolean,
    val hasIncome: Boolean
)

object CalendarMonth {
    fun cellsFor(monthStartMillis: Long, transactions: List<Transaction>): List<DayCell>
}
```

`cellsFor` emits leading blank cells to align day 1 under its weekday column, then one cell per
day of that month, each flagged by whether any transaction that day was an `EXPENSE` and/or an
`INCOME`. Trailing blanks are not emitted — the grid simply ends, since nothing renders after
the last day.

Alongside it, small `Calendar`-based helpers for month arithmetic: current month start, month
start ± 1 month, month end (exclusive), and a `SimpleDateFormat("MMMM yyyy", Locale.US)` label
matching `DateFormatter`'s existing style.

## Calendar Screen — `ui/calendar/CalendarFragment` + `CalendarViewModel`

`CalendarViewModel` holds two `MutableStateFlow`s:

- `_monthStart: Long` — millis at day 1, 00:00 of the displayed month; defaults to the current month
- `_selectedDayMillis: Long` — the selected day, defaulting to today

The month flow is `flatMapLatest` into `GetTransactionsForPeriodUseCase(monthStart, monthEndExclusive)`,
then `combine`d with `GetCategoriesUseCase()` and the selection into a single `CalendarUiState`:

```kotlin
data class CalendarUiState(
    val monthLabel: String = "",
    val cells: List<DayCell> = emptyList(),
    val selectedDayMillis: Long = 0L,
    val dayItems: List<TransactionListItem> = emptyList()
)
```

One Room query per displayed month; day bucketing and the selected-day filter both happen in
memory over at most a few dozen rows, matching the client-side-filtering decision the Bills
search box already made.

**Month navigation preserves the selected day-of-month**, clamped to the target month's length
via `getActualMaximum(Calendar.DAY_OF_MONTH)` — the same clamping `DatabaseSeeder.daysAhead`
already does. This keeps a day always selected, so the detail area needs only one empty state
rather than separate "nothing selected" and "nothing on this day" messages.

## UI Layout

`fragment_calendar.xml`:

- Purple header: back arrow (`ic_back_24`, added by Bills), month label, and ‹ › navigation arrows.
- Static weekday row (7 `TextView`s, S M T W T F S) — no code, no adapter.
- `RecyclerView` + `GridLayoutManager(7)` for the grid.
- A divider, then the selected day's transactions in a second `RecyclerView`, with a
  "No transactions on this day" `TextView` shown when that list is empty.

`item_calendar_day.xml`: the day number, above a centred horizontal row of up to two small dots
— `soft_red` if the day has an expense, `soft_green` if it has income, both side by side if it
has each. The dot row occupies fixed height whether or not dots are visible, so day numbers
stay on a consistent baseline across rows. Blank cells render an empty view. The selected day
gets a filled `purple_primary` circle background (reusing `bg_circle`) with white text.

`CalendarDayAdapter(onClick: (Long) -> Unit)` is a plain `RecyclerView.Adapter` with
`submitList()` + `notifyDataSetChanged()`, matching the house style; blank cells pass a null
`dateMillis` and are not clickable. Because selection highlighting is per-cell but the selected
day lives in the ViewModel, `submitList(cells, selectedDayMillis)` takes both and the adapter
compares each cell's `dateMillis` against it — `DayCell` itself carries no `isSelected` flag, so
changing selection doesn't require rebuilding the cell list.

The day-detail list reuses **`RecentTransactionAdapter` with no click callback**, so its rows
stay inert. Bills already generalised that adapter for exactly this kind of reuse — do not add
a third near-identical transaction adapter.

New strings: `calendar_title`, `calendar_empty_day`, and seven weekday initials.

## Testing / Verification

JUnit on `CalendarMonth.cellsFor` — the pure function is where the real risk lives:

- Leading blanks align day 1 correctly for a month starting mid-week
- Correct cell count for 30- and 31-day months
- February in both a leap and a non-leap year
- `hasExpense` and `hasIncome` set independently, and both set for a day carrying each
- A day with no transactions carries neither flag
- Day bucketing puts a late-evening transaction on its local-time day, not the UTC one

Then the standard process: `watch_build.ps1` auto-builds on save; an adb-driven walkthrough
covering opening Calendar from Home, the current month rendering with markers on seeded days,
tapping a day to populate the list, tapping an empty day to show the empty state, and ‹ ›
navigation across a year boundary; `testDebugUnitTest` + `lintDebug` clean before the PR.

## Explicitly Out of Scope

Swiping between months (arrows only), week or year views, jumping to an arbitrary date,
creating or editing a transaction from the calendar, per-day budget indicators or heat-map
shading, locale-aware first-day-of-week, and multi-month preloading.
