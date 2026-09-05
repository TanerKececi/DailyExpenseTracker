# Dark Mode — Design

Post-Phase-2 sub-project. Follows Settings ([PR #11](https://github.com/TanerKececi/DailyExpenseTracker/pull/11)), which built the
`SettingsStore` and dialog pattern this hangs on, and deliberately deferred Appearance to here.

## Context

The app *looks* dark-ready and is not. The theme is `Theme.Material3.DayNight.NoActionBar` and
`values-night/` exists — but that directory holds **only `themes.xml`**, there is no
`values-night/colors.xml`, and every custom colour is a light-mode literal. Put the device in dark
mode today and nothing changes, because no colour has a dark value to resolve to.

A first estimate called this a ~125-reference semantic refactor. **That was wrong**, and the
correction is the reason this design is small. Counting the 142 layout colour references by
attribute shows that only **one** colour is overloaded:

| `@color/white` usage | Count | Role |
|---|---|---|
| `android:textColor` (on the purple header) | 19 | foreground on primary |
| `app:tint` (header icons) | 16 | foreground on primary |
| `app:cardBackgroundColor` | 6 | **surface** |
| `android:background` | 1 | **surface** |

Every other colour has exactly one semantic role — `text_primary` is 25 `textColor` uses,
`text_secondary` 20 muted foregrounds, `background_light_gray` 13 recessed grounds,
`divider_light` 2 hairlines. Single-role colours need no renaming at all: overriding their
*values* in `values-night/colors.xml` does the whole job with zero layout edits.

So the work is roughly **26 reference edits**, one new file and one deleted file — not a
sweeping refactor.

**The purple header stays purple in dark mode.** That is a deliberate brand decision, it matches
what `values-night/themes.xml` already assumed ("the mockups have no dark variant; keep the same
brand palette"), and it is why the 35 white-on-purple references need no change.

## Colour model

### Split `white`

`white` is the only name doing two jobs. Add a `surface` colour and repoint the **9** genuine
surface uses:

- 6 × `app:cardBackgroundColor="@color/white"` and 1 × `android:background="@color/white"` in layouts
- `drawable/bg_rounded_card_16.xml` and `drawable/bg_rounded_card_24.xml` (`<solid android:color>`)

The remaining uses stay `white` because they are foreground on the unchanged purple header:
19 `textColor`, 16 `app:tint`, `colorOnPrimary` in `themes.xml`, the `Widget.App.Fab` tint in
`styles.xml`, and `CalendarDayAdapter.kt:57` (the selected day's label on its purple circle).

`white` therefore keeps its literal meaning in both themes and gets **no night override**.

### Rename two colours, not five

`background_light_gray` → **`background`** and `divider_light` → **`divider`**.

This is not tidying. HANDOFF.md records that *"never paint a foreground element in
`background_light_gray`"* has shipped as a bug **three separate times** — Bills' selected tab
label, the Calendar divider, and Budget Planner's progress tracks. A name that states the role
rather than the appearance removes the confusion that causes it. And in dark mode a colour called
`light_gray` holding `#121212` is precisely the trap `white` already set.

`text_primary` and `text_secondary` are **deliberately not renamed**. They are already role names,
they stay accurate in dark mode, and renaming them would cost 45 edits for nothing.

### `values-night/colors.xml`

| Token | Light | Dark |
|---|---|---|
| `background` | `#F8F9FA` | `#121212` |
| `surface` | `#FFFFFF` | `#1E1E1E` |
| `text_primary` | `#1A1A2E` | `#E6E1E5` |
| `text_secondary` | `#8D8D9A` | `#A8A3AD` |
| `divider` | `#E9EAEE` | `#2E2E2E` |

Surfaces stay one step lighter than the ground, preserving the figure/ground relationship the
light theme already has, so the existing 2dp `cardElevation` still reads. Material-standard greys
rather than true black: pure black flattens elevation and would force borders onto every card.

`purple_primary`, `purple_primary_variant`, `purple_accent_light` and `white` get no night values —
the header is unchanged, so their contrast is unchanged.

**`soft_red`, `soft_green` and `soft_blue` get no night values either, initially.** All three are
already pastel (`#FF7675`, `#55E6C1`, `#74B9FF`) and should read acceptably on `#1E1E1E`. Adding
speculative overrides would be guessing at a problem the dual-theme walkthrough will answer
directly. If a screen shows one of them vibrating or washed out, add that one override then.

### Delete `values-night/themes.xml`

It is byte-identical to `values/themes.xml` and references the same colour names. Once those names
resolve per-theme it does nothing at all, and leaving it means every future theme change must be
made twice. Deleting it is part of the deliverable, not a side quest.

## The Appearance setting

A fourth Settings row, above the existing three: **System / Light / Dark**, chosen from the same
single-choice `AlertDialog` the currency and week-start rows use.

Stored in `SettingsStore` as an `Int` holding an `AppCompatDelegate.MODE_NIGHT_*` constant,
defaulting to `MODE_NIGHT_FOLLOW_SYSTEM`. Applied by `AppCompatDelegate.setDefaultNightMode` in
two places: `DailyExpenseTrackerApp.onCreate`, beside where the currency symbol is already pushed
into `CurrencyFormatter`, and in `SettingsViewModel` when the value changes.

Calling `setDefaultNightMode` recreates the running activity, so the change is visible
immediately — unlike currency and week start, which rely on `repeatOnLifecycle` re-emission on the
next screen visit. No extra plumbing is needed for that; it is a platform behaviour.

## Testing / Verification

**Unit tests can only cover the plumbing.** `SettingsStore` and `SettingsViewModel` gain cases for
the night-mode key: the default is follow-system, each of the three modes round-trips, and setting
appearance leaves currency and week start untouched. That is the honest limit — no unit test can
tell you a screen is legible.

**The real check is a dual-theme emulator walkthrough**, and it is the bulk of the effort. Every
surface, screenshotted in both light and dark:

Home · My Wallet · Categories · Bills (Paid / Overdue / Upcoming) · Schedule Bill Detail ·
Calendar · Reports (Expense Chart / Budget Planner / Billing Reports) · Add-Transaction sheet ·
Settings

Things to look for specifically, because they are where this will break:

- Any element that vanishes — the failure mode of a foreground painted in a background colour.
- Card edges: on `#1E1E1E` against `#121212` the elevation shadow is subtle; confirm cards are
  still distinguishable from the ground.
- The donut and bar charts. `BarChartView` reads `soft_red`, `soft_blue` and `text_secondary`
  through `ContextCompat.getColor` in property initializers, which resolve at view construction —
  correct across a theme change only because `setDefaultNightMode` recreates the activity. Confirm
  it, don't assume it.
- Category colours come from `Category.colorHex` in the database. They are **data, not theme**, and
  will not adapt. Check they remain legible on a dark card; if one does not, that is a seed-data
  question, out of scope here.
- The status bar stays `purple_primary`; confirm it still looks intentional in dark.

`testDebugUnitTest` and `lintDebug` clean, per the handoff's checklist. Note the watcher runs
`installDebug`, which never compiles the test source set.

## Explicitly Out of Scope

- Changing the purple header, status bar, or any brand colour in dark mode.
- Renaming `text_primary` / `text_secondary` to Material token names.
- Night variants for the status colours unless the walkthrough demands one.
- Re-colouring seeded category `colorHex` values for dark legibility.
- A true-black / OLED variant, or per-screen theme overrides.
- Dynamic colour (Material You `dynamicColor`) — a separate decision with its own palette work.
