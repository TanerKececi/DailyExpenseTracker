# Dark Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the app render correctly in dark mode, and let the user choose System / Light / Dark from Settings.

**Architecture:** Only `@color/white` is semantically overloaded, so it is split into `white` (foreground on the unchanged purple header) and a new `surface`. Two colours whose names would become lies in dark mode are renamed (`background_light_gray` → `background`, `divider_light` → `divider`). Everything else has a single role and is handled purely by value overrides in a new `values-night/colors.xml` with no layout edits. The Settings toggle then drives `AppCompatDelegate.setDefaultNightMode`.

**Tech Stack:** Kotlin, Android resource qualifiers (`values-night`), AppCompat `AppCompatDelegate`, Hilt, ViewBinding. JUnit 4 + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-09-05-dark-mode-design.md`

## Global Constraints

- **No new dependencies.** `AppCompatDelegate` ships in `androidx.appcompat`, already a dependency. No Material You / dynamic colour library.
- **The purple header does not change.** `purple_primary`, `purple_primary_variant`, `purple_accent_light` and `white` get **no** night values. All 35 white-on-purple references stay exactly as they are.
- **Do not rename `text_primary` or `text_secondary`.** They are role names, they stay accurate in dark, and renaming costs 45 edits for nothing.
- **Do not add night variants for `soft_red`, `soft_green`, `soft_blue`** unless Task 6's walkthrough shows a specific problem. They are already pastel.
- **Do not push to `master`.** Work on `feature/dark-mode`, push, open a PR, ask before merging.
- **You cannot run Gradle.** Every `gradlew` call fails with `Unable to establish loopback connection`. Ask the user to run `watch_build.ps1` and poll `watch_build_status.txt` yourself. The watcher runs `installDebug`, which **never compiles the test source set** — so ask for one `./gradlew testDebugUnitTest lintDebug` at the end and read the reports yourself. **Check report timestamps**; stale XMLs look identical to a fresh pass.
- **Never name a child view `android:id="@+id/root"`** — collides with generated `binding.root`.
- Use `app:tint` (not `android:tint`) on `ImageView`, per AppCompat lint.
- Write Kotlin/XML with the Write tool, not Bash heredocs — the shell parser has tripped on them repeatedly.
- Baselines to preserve: **126 tests / 20 suites passing**, **0 lint errors / 57 warnings**.
- Toggle the emulator's theme without the app: `adb shell cmd uimode night yes` / `no` / `auto`.

---

### Task 1: Split `@color/white` into `white` and `surface`

Pure refactor. `surface` is `#FFFFFF` in light, so **the app must look pixel-identical afterwards**. That is the check.

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/layout/activity_main.xml:26`, `fragment_bill_detail.xml:42`, `fragment_home.xml:71`, `fragment_settings.xml:45`, `fragment_settings.xml:112`, `fragment_wallet.xml:37`, `item_budget_card.xml:8`
- Modify: `app/src/main/res/drawable/bg_rounded_card_16.xml:3`, `bg_rounded_card_24.xml:3`

**Interfaces:**
- Consumes: nothing.
- Produces: `@color/surface`, a card/sheet background used by later tasks' night override.

- [ ] **Step 1: Add the colour**

In `app/src/main/res/values/colors.xml`, directly beneath the `white` entry:

```xml
    <!-- Card and sheet background. Split out of @color/white, which is also the foreground on
         the purple header — those two roles need opposite values in dark mode. -->
    <color name="surface">#FFFFFF</color>
```

- [ ] **Step 2: Repoint the nine surface uses**

These are the **only** `white` references that change. Every other one is foreground on the purple header and must be left alone.

```bash
cd app/src/main/res
sed -i 's|app:cardBackgroundColor="@color/white"|app:cardBackgroundColor="@color/surface"|g' \
  layout/fragment_bill_detail.xml layout/fragment_home.xml layout/fragment_settings.xml \
  layout/fragment_wallet.xml layout/item_budget_card.xml
sed -i 's|android:background="@color/white"|android:background="@color/surface"|g' layout/activity_main.xml
sed -i 's|<solid android:color="@color/white" />|<solid android:color="@color/surface" />|g' \
  drawable/bg_rounded_card_16.xml drawable/bg_rounded_card_24.xml
```

- [ ] **Step 3: Verify the split is exactly right**

```bash
cd app/src/main/res
echo "surface refs (expect 10):"; grep -roh "@color/surface" . | wc -l
echo "remaining white by attribute (expect textColor 19, tint 16, and nothing else):"
grep -rohE '[a-zA-Z:]+="@color/white"' layout/ | sort | uniq -c
```

Expected: 10 `surface` references; remaining `white` is only `android:textColor` (19) and `app:tint` (16). **If any `cardBackgroundColor` or `android:background` still says `white`, you missed one** — that card will stay white in dark mode.

- [ ] **Step 4: Build and confirm nothing moved**

Poll `watch_build_status.txt` for `SUCCESS` (wait for a value that stays put ~10s; if it returns to `BUILDING`, keep waiting). Then screenshot Home and Wallet and confirm they look **identical** to before — `surface` is `#FFFFFF`, so any visible change means a wrong reference was repointed.

```bash
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
$ADB logcat -d | grep "FATAL EXCEPTION"
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/layout/ app/src/main/res/drawable/
git commit -m "Split @color/white into white and surface"
```

---

### Task 2: Rename `background_light_gray` → `background` and `divider_light` → `divider`

Also a pure refactor with no visual change. These names describe appearance, not role; in dark mode `light_gray` holding `#121212` is the same trap `white` just set. HANDOFF.md records that painting a foreground in `background_light_gray` has shipped as a bug **three times**.

**Files:**
- Modify: `app/src/main/res/values/colors.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml`
- Modify: 11 layouts (13 refs) — `activity_main`, `fragment_add_transaction`, `fragment_bill_detail`, `fragment_bills`, `fragment_calendar`, `fragment_categories`, `fragment_home` (×3), `fragment_reports`, `fragment_settings`, `fragment_wallet`, `item_budget_card`
- Modify: `app/src/main/res/layout/fragment_calendar.xml:134`, `fragment_settings.xml:81` (divider)
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/budget/adapter/BudgetPlanAdapter.kt:65-67`

**Interfaces:**
- Consumes: nothing.
- Produces: `@color/background` and `@color/divider`, both overridden by Task 3.

- [ ] **Step 1: Rename across every resource and source file**

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
grep -rl "background_light_gray\|divider_light" app/src/main | while read -r f; do
  sed -i 's/background_light_gray/background/g; s/divider_light/divider/g' "$f"
done
```

This deliberately also rewrites the explanatory comments in `colors.xml` and `BudgetPlanAdapter.kt`, which reference the old names — leaving them would make the comments wrong.

- [ ] **Step 2: Verify no stragglers and the counts are right**

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
echo "old names left (expect 0):"; grep -rc "background_light_gray\|divider_light" app/src/main | grep -v ":0" || echo 0
echo "@color/background refs (expect 14: 13 layout + themes.xml):"; grep -roh "@color/background\b" app/src/main/res | wc -l
echo "divider refs:"; grep -rn "@color/divider\b\|R.color.divider\b" app/src/main | wc -l
```

**Watch for a false match:** `@color/background` is a prefix of nothing else here, but `android:background=` is a *attribute* name containing the word — the `\b` and the `@color/` prefix keep them apart. Do not drop either from the grep.

- [ ] **Step 3: Fix the colour definition's comment**

In `app/src/main/res/values/colors.xml`, the divider entry's comment now reads awkwardly after the rename. Replace that comment with:

```xml
    <!-- hairline separator and empty progress tracks; distinct from @color/background so it stays
         visible against the screen's own ground -->
```

- [ ] **Step 4: Build and confirm nothing moved**

Poll for `SUCCESS`, then screenshot Home, Calendar and Budget Planner. All three must look identical — the values are unchanged, only the names moved. Check logcat for `FATAL EXCEPTION`; expected none.

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git commit -m "Rename colours after their role, not their appearance"
```

---

### Task 3: Add the dark palette

This is the task that actually delivers dark mode. After it, dark mode works from the **device** setting; the in-app toggle comes in Tasks 4–5.

**Files:**
- Create: `app/src/main/res/values-night/colors.xml`
- Delete: `app/src/main/res/values-night/themes.xml`

**Interfaces:**
- Consumes: `@color/surface` (Task 1), `@color/background` and `@color/divider` (Task 2).
- Produces: dark values for five colours; no new names.

- [ ] **Step 1: Create the night palette**

Create `app/src/main/res/values-night/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!--
      Dark overrides. Only colours whose role flips between themes appear here.

      The purple brand colours and @color/white are deliberately absent: the header stays purple
      in dark mode, so white-on-purple remains correct and needs no dark value.

      soft_red / soft_green / soft_blue are also absent. They are already pastel and should read
      on #1E1E1E; add an override only if a screenshot shows one failing.

      Surfaces stay one step lighter than the ground so the existing 2dp cardElevation still
      reads, mirroring the light theme's white-on-light-grey relationship.
    -->
    <color name="background">#121212</color>
    <color name="surface">#1E1E1E</color>
    <color name="text_primary">#E6E1E5</color>
    <color name="text_secondary">#A8A3AD</color>
    <color name="divider">#2E2E2E</color>
</resources>
```

- [ ] **Step 2: Delete the redundant night theme**

```bash
git rm app/src/main/res/values-night/themes.xml
```

It is byte-identical to `values/themes.xml` and references the same colour names, which now resolve per-theme on their own. Keeping it would mean every future theme change has to be made twice.

- [ ] **Step 3: Build, then look at dark mode for the first time**

Poll `watch_build_status.txt` for `SUCCESS`. Then:

```bash
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
# Your session's scratchpad directory — never write screenshots into the repo.
SP="C:/Users/ADMINI~1/AppData/Local/Temp/claude/C--Users-Administrator-AndroidStudioProjects-DailyExpenseTracker/<session-id>/scratchpad"
mkdir -p "$SP"
$ADB shell cmd uimode night yes
$ADB shell sleep 3
$ADB exec-out screencap -p > "$SP/dark_home.png"
$ADB shell cmd uimode night no
$ADB shell sleep 3
$ADB exec-out screencap -p > "$SP/light_home.png"
$ADB logcat -d | grep "FATAL EXCEPTION"
```

Read both screenshots. In dark: the ground is near-black, cards are visibly lighter than the ground, body text is light, and the header is still purple with white text. In light: unchanged from before.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values-night/
git commit -m "Add the dark colour palette"
```

---

### Task 4: Store the appearance preference

**Files:**
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsStore.kt`
- Modify: `app/src/test/java/com/example/dailyexpensetracker/ui/settings/FakeSettingsStore.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/DailyExpenseTrackerApp.kt`

**Interfaces:**
- Consumes: the existing `SettingsStore` interface with `currencySymbol: String` and `weekStart: Int`.
- Produces: `SettingsStore.nightMode: Int` (an `AppCompatDelegate.MODE_NIGHT_*` constant) and `SettingsStore.DEFAULT_NIGHT_MODE`.

**No unit test for this task** — it is a `SharedPreferences` property plus one line in `Application`, and testing it would need Robolectric, a new dependency, for no signal. Task 5's ViewModel tests cover the behaviour. Verified here by build and by the emulator check in Step 4.

- [ ] **Step 1: Add the property to the interface**

In `SettingsStore.kt`, add to the interface body and its companion:

```kotlin
interface SettingsStore {
    var currencySymbol: String
    var weekStart: Int
    /** One of the `AppCompatDelegate.MODE_NIGHT_*` constants. */
    var nightMode: Int

    companion object {
        val SUPPORTED_SYMBOLS = listOf("$", "€", "£", "₺", "¥")
        const val DEFAULT_WEEK_START = Calendar.SUNDAY
        const val DEFAULT_NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
```

Add `import androidx.appcompat.app.AppCompatDelegate`.

- [ ] **Step 2: Implement it**

In `SharedPreferencesSettingsStore`, beside the existing properties:

```kotlin
    override var nightMode: Int
        get() = prefs.getInt(KEY_NIGHT_MODE, SettingsStore.DEFAULT_NIGHT_MODE)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MODE, value).apply()
```

and in its private companion:

```kotlin
        const val KEY_NIGHT_MODE = "night_mode"
```

- [ ] **Step 3: Update the fake and apply at startup**

`FakeSettingsStore.kt` becomes:

```kotlin
package com.example.dailyexpensetracker.ui.settings

import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter

class FakeSettingsStore(
    override var currencySymbol: String = CurrencyFormatter.DEFAULT_SYMBOL,
    override var weekStart: Int = SettingsStore.DEFAULT_WEEK_START,
    override var nightMode: Int = SettingsStore.DEFAULT_NIGHT_MODE
) : SettingsStore
```

In `DailyExpenseTrackerApp.onCreate`, after the currency line:

```kotlin
        AppCompatDelegate.setDefaultNightMode(settingsStore.nightMode)
```

with `import androidx.appcompat.app.AppCompatDelegate`.

- [ ] **Step 4: Build and confirm startup is unaffected**

Poll for `SUCCESS`, launch the app, and confirm it still starts and still follows the device theme (the default is follow-system, so behaviour is unchanged):

```bash
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
$ADB logcat -d | grep "FATAL EXCEPTION"
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsStore.kt app/src/main/java/com/example/dailyexpensetracker/DailyExpenseTrackerApp.kt app/src/test/java/com/example/dailyexpensetracker/ui/settings/FakeSettingsStore.kt
git commit -m "Persist the appearance preference"
```

---

### Task 5: The Appearance row in Settings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsFragment.kt`
- Test: `app/src/test/java/com/example/dailyexpensetracker/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsStore.nightMode`, `SettingsStore.DEFAULT_NIGHT_MODE`, `FakeSettingsStore(currencySymbol, weekStart, nightMode)` (Task 4).
- Produces: `SettingsUiState(currencySymbol, weekStart, nightMode)` — **note the third field**; `SettingsViewModel.setNightMode(Int)`; layout ids `rowAppearance` and `tvAppearanceValue`.

- [ ] **Step 1: Write the failing tests**

Add to `SettingsViewModelTest`:

```kotlin
    @Test
    fun `appearance defaults to following the system`() {
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            viewModel().uiState.value.nightMode
        )
    }

    @Test
    fun `every appearance mode round-trips`() {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)

        for (mode in listOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )) {
            viewModel.setNightMode(mode)

            assertEquals(mode, viewModel.uiState.value.nightMode)
            assertEquals(mode, store.nightMode)
        }
    }

    @Test
    fun `changing appearance leaves currency and week start alone`() {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)

        viewModel.setNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        assertEquals(CurrencyFormatter.DEFAULT_SYMBOL, store.currencySymbol)
        assertEquals(SettingsStore.DEFAULT_WEEK_START, store.weekStart)
    }
```

Add `import androidx.appcompat.app.AppCompatDelegate`.

- [ ] **Step 2: Confirm they fail**

`SettingsUiState` has no `nightMode` and `SettingsViewModel` has no `setNightMode`, so the file will not compile.

- [ ] **Step 3: Extend the ViewModel**

In `SettingsViewModel.kt`, add the field to the state and the setter:

```kotlin
data class SettingsUiState(
    val currencySymbol: String,
    val weekStart: Int,
    val nightMode: Int
)
```

Update the initial value:

```kotlin
    private val _uiState = MutableStateFlow(
        SettingsUiState(settingsStore.currencySymbol, settingsStore.weekStart, settingsStore.nightMode)
    )
```

and add:

```kotlin
    fun setNightMode(mode: Int) {
        settingsStore.nightMode = mode
        // Recreates the running activity, so unlike currency and week start this is visible at
        // once rather than on the next screen visit.
        AppCompatDelegate.setDefaultNightMode(mode)
        _uiState.value = _uiState.value.copy(nightMode = mode)
    }
```

with `import androidx.appcompat.app.AppCompatDelegate`.

- [ ] **Step 4: Add the strings**

In `app/src/main/res/values/strings.xml`, beside the other `settings_*` entries:

```xml
    <string name="settings_appearance">Appearance</string>
    <string name="settings_appearance_system">Follow system</string>
    <string name="settings_appearance_light">Light</string>
    <string name="settings_appearance_dark">Dark</string>
```

- [ ] **Step 5: Add the row to the layout**

In `fragment_settings.xml`, inside the first `CardView`'s inner `LinearLayout`, **above** the existing `rowCurrency` block, insert the row and its divider:

```xml
                <LinearLayout
                    android:id="@+id/rowAppearance"
                    style="@style/SettingsRow">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_appearance"
                        android:textColor="@color/text_primary"
                        android:textSize="15sp" />

                    <TextView
                        android:id="@+id/tvAppearanceValue"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textColor="@color/text_secondary"
                        android:textSize="15sp"
                        tools:text="Follow system" />
                </LinearLayout>

                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:layout_marginStart="16dp"
                    android:layout_marginEnd="16dp"
                    android:background="@color/divider" />
```

- [ ] **Step 6: Wire the row up**

In `SettingsFragment.onViewCreated`, beside the other listeners:

```kotlin
        binding.rowAppearance.setOnClickListener { showAppearanceDialog() }
```

In the state collector, add:

```kotlin
                    binding.tvAppearanceValue.setText(appearanceLabel(state.nightMode))
```

And add these two methods:

```kotlin
    private fun appearanceLabel(nightMode: Int) = when (nightMode) {
        AppCompatDelegate.MODE_NIGHT_NO -> R.string.settings_appearance_light
        AppCompatDelegate.MODE_NIGHT_YES -> R.string.settings_appearance_dark
        else -> R.string.settings_appearance_system
    }

    private fun showAppearanceDialog() {
        val labels = arrayOf(
            getString(R.string.settings_appearance_system),
            getString(R.string.settings_appearance_light),
            getString(R.string.settings_appearance_dark)
        )
        val values = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val checked = values.indexOf(viewModel.uiState.value.nightMode).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_appearance)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setNightMode(values[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }
```

with `import androidx.appcompat.app.AppCompatDelegate`.

- [ ] **Step 7: Build and drive it**

Poll for `SUCCESS`. Then open Settings, tap Appearance, pick Dark — the app must switch immediately (the activity recreates). Pick Light, then Follow system. Confirm the row's summary tracks the choice, and that the choice survives a force-stop and relaunch:

```bash
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
$ADB shell am force-stop com.example.dailyexpensetracker
$ADB shell monkey -p com.example.dailyexpensetracker -c android.intent.category.LAUNCHER 1
$ADB shell sleep 3
$ADB logcat -d | grep "FATAL EXCEPTION"
```

Expected: no output, and the app reopens in the chosen theme.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/layout/fragment_settings.xml app/src/main/java/com/example/dailyexpensetracker/ui/settings/ app/src/test/java/com/example/dailyexpensetracker/ui/settings/SettingsViewModelTest.kt
git commit -m "Add the Appearance setting"
```

---

### Task 6: Dual-theme walkthrough and verification

**This task is the actual deliverable check.** No unit test can tell you a screen is legible; only screenshots can. Budget most of the effort here.

- [ ] **Step 1: Ask the user to run the suite**

Once, in a second terminal, without stopping the watcher:

```bash
./gradlew testDebugUnitTest lintDebug --console=plain > verify_output.txt 2>&1
```

- [ ] **Step 2: Read the reports yourself**

```bash
ls -la --time-style=+%m-%d_%H:%M app/build/test-results/testDebugUnitTest/*.xml
grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml
tail -3 app/build/reports/lint-results-debug.txt
```

**Check the timestamps** — stale XMLs from an earlier run are indistinguishable from a fresh pass by content alone. Expected: 129 tests / 20 suites (126 + the 3 new appearance cases), 0 failures, **0 lint errors**. Warning count may shift by one or two from the new layout rows; confirm any new warning is a benign category.

- [ ] **Step 3: Screenshot every surface in both themes**

Set the theme in-app via Settings → Appearance (this also exercises the toggle). For each screen, capture light and dark:

Home · My Wallet · Categories · Bills (Paid, Overdue, Upcoming) · Schedule Bill Detail · Calendar · Reports (Expense Chart, Budget Planner, Billing Reports) · Add-Transaction sheet · Settings

Screenshots go in the scratchpad, **never** the repo. Use `uiautomator dump` for tap coordinates rather than guessing; they shift when the keyboard opens.

- [ ] **Step 4: Judge each dark screenshot against these specific failures**

- **Anything invisible.** A foreground painted in a background colour is this project's most-repeated bug — it has shipped three times. Look hardest at tab labels, dividers and progress-bar tracks.
- **Card edges.** On `#1E1E1E` over `#121212` the 2dp elevation shadow is subtle. Cards must still be distinguishable from the ground.
- **Both charts.** `BarChartView` reads `soft_red`, `soft_blue` and `text_secondary` via `ContextCompat.getColor` in property initializers, resolved at construction — correct across a theme switch only because `setDefaultNightMode` recreates the activity. **Confirm it rather than assuming it.** The donut chart draws from `Category.colorHex`.
- **Category colours are data, not theme.** They come from the database and will not adapt. Check they stay legible on a dark card. If one does not, that is a seed-data question and out of scope — note it, don't fix it here.
- **Status bar** stays `purple_primary`; confirm it still looks deliberate in dark.
- **The status colours.** If `soft_red`, `soft_green` or `soft_blue` vibrates or washes out against `#1E1E1E`, add a night override for **that one colour only** in `values-night/colors.xml`, then re-screenshot the affected screen.

- [ ] **Step 5: Update HANDOFF.md**

Record the **verified** numbers, not projected ones. Move dark mode out of "What's left". Add to the gotchas: `@color/white` is foreground-on-purple only — card surfaces are `@color/surface`, and putting `white` on a card background will look right in light and wrong in dark.

- [ ] **Step 6: Open the PR**

Push `feature/dark-mode`, open a PR summarising what changed and what the walkthrough showed, and **ask before merging**.
