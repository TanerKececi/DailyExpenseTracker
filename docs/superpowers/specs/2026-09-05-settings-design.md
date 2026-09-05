# Settings — Design

Post-Phase-2 sub-project. Builds on Phase 1 plus Bills, Calendar and Reports, all shipped and
merged to `master`, and on the ViewModel test suites merged in PR #10.

## Context

Settings is the last screen named in the original app structure that has no home. Reports took the
fifth bottom-nav slot and `PlaceholderFragment` was deleted with it, so Settings needs both a new
entry point and — more to the point — a decision about what actually goes in it.

Four candidates were considered. Three ship here:

- **Currency symbol.** The app hardcodes US dollars in `CurrencyFormatter`.
- **Week start.** `CalendarMonth` hardcodes a Sunday week, and already carries a `ponytail:`
  comment naming this exact upgrade path.
- **Reset & reseed data.** `DatabaseSeeder` already exists; the seeded database is the app's only
  data source and a schema change already wipes it by design.

**Light/Dark/System appearance was deliberately cut** and becomes its own sub-project. The app is
not dark-ready: `values-night/` contains only `themes.xml`, there is no `values-night/colors.xml`,
and every custom colour is a light-mode literal — `@color/white` is used 43 times as a card
surface, `@color/text_primary` (`#1A1A2E`) 23 times, `@color/background_light_gray` 12 times.
Switching to dark today produces near-black text on white cards on a light ground. The honest fix
is semantic colour names (`surface`, `on_surface`, `background`) across roughly 125 references in
every layout, plus a dual-theme emulator walkthrough. That is a design-system refactor, larger than
these three settings combined, and it is invisible to both the build and all 105 tests. It does not
belong bolted onto a settings screen.

## Storage — `ui/settings/SettingsStore`

`SharedPreferences` behind a Hilt `@Singleton`, wrapped by a small interface so it can be faked in
tests without a `Context`.

| Key | Type | Default |
|---|---|---|
| `currency_symbol` | `String` | `"$"` |
| `week_start` | `Int` | `Calendar.SUNDAY` |

**No new dependency.** `SharedPreferences` is a platform feature; DataStore would be the project's
first *production* dependency since Phase 1 and buys nothing here.

**Settings do not need to be reactive.** Every fragment collects its state inside
`repeatOnLifecycle(Lifecycle.State.STARTED)`. Returning to a screen re-subscribes, the `StateFlow`
immediately re-emits its current value, and the whole binding block re-runs — re-formatting every
amount and rebuilding the calendar grid. A synchronous read at bind time therefore propagates a
changed setting to every screen on its next visit, with no `callbackFlow` bridge and no reactive
plumbing. Do not "improve" this into a Flow; the lifecycle already does the work.

## Currency — `ui/common/util/CurrencyFormatter`

`CurrencyFormatter` stays an `object` and gains a single mutable field:

```kotlin
@Volatile var symbol: String = "$"
fun format(amount: Double): String = String.format(Locale.US, "$symbol%,.2f", amount)
```

Set from `SettingsStore` in `Application.onCreate()`, and again when the setting changes. All five
existing call sites are untouched.

This is global mutable state and gets a `ponytail:` comment saying so — it is display-only, written
on the main thread, and read on the main thread. The alternative, injecting a formatter into five
adapters that are plain fragment fields with no DI, is a much larger diff for no behavioural gain.

**Also fixes:** `BillDetailFragment.kt:61` appends a hardcoded `" USD"`, which contradicts any
other symbol. It goes.

The symbol is chosen from a fixed list — `$`, `€`, `£`, `₺`, `¥` — via a single-choice dialog. No
free text: it would need validation and a length cap, and a long value breaks the tight header
layouts. Placement stays prefix; locale-aware `NumberFormat.getCurrencyInstance()` is out of scope
(see below).

## Week start — `ui/calendar/CalendarMonth`

`cellsFor` gains a parameter with a default:

```kotlin
fun cellsFor(monthStartMillis: Long, transactions: List<Transaction>, weekStart: Int = Calendar.SUNDAY)
```

with `leadingBlanks = (dayOfWeek - weekStart + 7) % 7`. The default keeps all seven existing
`CalendarMonthTest` cases compiling and passing untouched. `CalendarViewModel` injects
`SettingsStore` and passes the stored value.

**The header row is the invasive part.** `fragment_calendar.xml` hardcodes seven `TextView`s
(`calendar_day_sun` … `calendar_day_sat`) with no ids. They need ids and programmatic binding from
an ordered list, or the grid silently misaligns against a fixed header — a bug that looks exactly
like an off-by-one in the cell maths and would be debugged in the wrong file.

## Reset & reseed — `data/local/DatabaseSeeder`

**The seeder must be fixed before reset can work at all.** It currently hardcodes its foreign keys:

```kotlin
// categoryId indices below correspond to insertion order above (autoGenerate starts at 1)
val groceryId = 1L
...
val salaryId = 10L
```

That holds only on a fresh database, which is why it is correct today — seeding runs once, on first
launch. But `@PrimaryKey(autoGenerate = true)` emits `AUTOINCREMENT`, and SQLite does not reset that
sequence when rows are deleted. A second seed would insert categories as ids 11–20 while the
transactions still reference 1–10, and `TransactionEntity`'s `ForeignKey` would reject them. Reset
would fail on its first use.

**Fix:** `CategoryDao.insertAll` and `CardDao.insertAll` return `List<Long>` (Room supports this on
`@Insert` directly), and the seeder uses the returned ids. This removes the assumption permanently
rather than working around it, and is worth doing on its own merits — the existing comment is a
latent trap for anyone who ever reseeds.

Then: `deleteAll()` on all three DAOs, a `ResetDataUseCase` that clears and reseeds in one
transaction, and a confirmation dialog. Reset is destructive and always confirms.

## Navigation

A second `ImageView` in the Home header beside `ivCalendar` — `ic_settings_24`,
`app:tint="@color/white"` (per AppCompat lint, `app:` not `android:`), with a real
`contentDescription`. The bottom nav stays at five items; all slots are full.

`nav_graph.xml` gains a `settingsFragment` destination and an
`action_homeFragment_to_settingsFragment`.

## The screen — `ui/settings/`

`SettingsFragment` + `SettingsViewModel` + ViewBinding, laid out as CardView sections to match the
existing screens.

**Not `androidx.preference`.** It is a new dependency, and `PreferenceFragmentCompat` brings its own
Material styling that would have to be fought into this app's purple design. Three hand-rolled rows
are less code than that fight.

| Row | Control |
|---|---|
| Currency | Single-choice `AlertDialog`, shows current symbol |
| Week start | Single-choice `AlertDialog` (Sunday / Monday) |
| Reset data | Destructive; confirmation `AlertDialog` |

## Testing / Verification

- `SettingsViewModel` tests in the pattern established by PR #10 — `MainDispatcherRule`, the
  `subscribe()` helper, a fake `SettingsStore`.
- New `CalendarMonthTest` cases for a Monday week start, including a month whose first day *is* a
  Monday (zero leading blanks) and one that needs six.
- A `DatabaseSeeder` test asserting that a second seed produces transactions whose `categoryId`s all
  resolve — the regression that motivated the id fix.
- `testDebugUnitTest` and `lintDebug` clean, per the handoff's checklist. Note the watcher runs
  `installDebug`, which never compiles the test source set.
- Emulator walkthrough: change currency, confirm every money-showing screen picks it up on next
  visit; switch to Monday and confirm the header row and grid move together; reset and confirm the
  app reseeds without a foreign-key crash.

## Explicitly Out of Scope

- **Light/Dark/System appearance.** Its own sub-project; see Context.
- Locale-aware currency formatting (`NumberFormat.getCurrencyInstance`), symbol placement
  (prefix/suffix), decimal separators and grouping.
- Free-text currency entry.
- Import, export and backup — a separate open thread.
- Any notification or reminder setting; the app has no background job and Overdue is derived.
- Per-category or per-screen settings; these three are global.
