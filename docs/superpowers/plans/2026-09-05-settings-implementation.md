# Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Settings screen with three working settings — currency symbol, calendar week start, and reset-to-seed-data — reachable from a new icon in the Home header.

**Architecture:** Settings persist in `SharedPreferences` behind a `SettingsStore` interface (Hilt singleton, faked in tests). Nothing is reactive: every fragment already collects inside `repeatOnLifecycle(STARTED)`, so returning to a screen re-subscribes, the `StateFlow` re-emits, and the binding block re-formats everything. Currency reaches five DI-less adapters through one mutable field on the existing `CurrencyFormatter` object; week start is threaded as a plain parameter into the pure `CalendarMonth.cellsFor`.

**Tech Stack:** Kotlin, MVVM + Clean Architecture, Room (KSP), Hilt (KSP), Navigation Component + Safe Args, ViewBinding. JUnit 4 + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-09-05-settings-design.md`

## Global Constraints

- **No new dependencies.** Not DataStore, not `androidx.preference`, not Robolectric. `SharedPreferences` and `AlertDialog` are platform features. The only dependency added since Phase 1 is `kotlinx-coroutines-test` (test-only).
- **Do not push to `master`.** Work on `feature/settings`, push, open a PR, ask before merging.
- **You cannot run Gradle.** Every `gradlew` call fails with `Unable to establish loopback connection`. Use `watch_build.ps1` (user runs it; you poll `watch_build_status.txt`). The watcher runs `installDebug`, which **never compiles the test source set** — so a broken unit test still reports `SUCCESS`. Ask the user to run `./gradlew testDebugUnitTest lintDebug` **once, at the end**, and read the reports yourself. Check report timestamps: stale XMLs from a previous session look identical to a fresh pass.
- **Never name a child view `android:id="@+id/root"`** — collides with generated `binding.root`.
- **Never paint a foreground element `@color/background_light_gray`** — it is the screen background. Use `@color/divider_light` for hairlines.
- Use `app:tint` (not `android:tint`) on `ImageView`, per AppCompat lint.
- Write Kotlin/XML with the Write tool, not Bash heredocs — the shell parser has tripped on them repeatedly.
- Existing lint baseline: **0 errors / 55 warnings**. Do not "fix" pinned-dependency version warnings.
- Existing test baseline: **105 tests across 16 suites, all passing.**

---

### Task 1: Make `CurrencyFormatter` symbol-aware

Pure and fully JVM-testable. Nothing else depends on it yet, so this task stands alone.

**Files:**
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/common/util/CurrencyFormatter.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/billdetail/BillDetailFragment.kt:61`
- Test: `app/src/test/java/com/example/dailyexpensetracker/ui/common/util/CurrencyFormatterTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `CurrencyFormatter.symbol: String` (mutable, `@Volatile`), `CurrencyFormatter.DEFAULT_SYMBOL: String = "$"`, and the unchanged `CurrencyFormatter.format(amount: Double): String`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/dailyexpensetracker/ui/common/util/CurrencyFormatterTest.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.common.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatterTest {

    /**
     * `symbol` is global mutable state shared by every test in this JVM. Leaving it changed would
     * break any later suite that asserts on a formatted string.
     */
    @After
    fun resetSymbol() {
        CurrencyFormatter.symbol = CurrencyFormatter.DEFAULT_SYMBOL
    }

    @Test
    fun `defaults to a dollar sign`() {
        assertEquals("$1,234.56", CurrencyFormatter.format(1234.56))
    }

    @Test
    fun `uses the configured symbol`() {
        CurrencyFormatter.symbol = "€"

        assertEquals("€1,234.56", CurrencyFormatter.format(1234.56))
    }

    @Test
    fun `keeps grouping and two decimal places for every symbol`() {
        CurrencyFormatter.symbol = "₺"

        assertEquals("₺1,000,000.00", CurrencyFormatter.format(1_000_000.0))
        assertEquals("₺0.50", CurrencyFormatter.format(0.5))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

You cannot run Gradle. Confirm by inspection that `CurrencyFormatter` has no `symbol` and no `DEFAULT_SYMBOL` member — the test will not compile until Step 3. Do not skip this reasoning step.

- [ ] **Step 3: Write minimal implementation**

Replace the whole of `CurrencyFormatter.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.common.util

import java.util.Locale

object CurrencyFormatter {

    const val DEFAULT_SYMBOL = "$"

    /**
     * ponytail: global mutable display state, in place of injecting a formatter into five adapters
     * that are plain fragment fields with no DI. Written on the main thread at app start and when
     * the setting changes; read on the main thread at bind time. Inject a formatter instead if this
     * is ever read off the main thread.
     */
    @Volatile
    var symbol: String = DEFAULT_SYMBOL

    fun format(amount: Double): String = String.format(Locale.US, "%s%,.2f", symbol, amount)
}
```

Note `"%s%,.2f"` with two arguments rather than interpolating the symbol into the format string — a symbol is never passed to `String.format` as a format specifier that way.

- [ ] **Step 4: Remove the contradicting hardcoded currency**

In `BillDetailFragment.kt` line 61, change:

```kotlin
binding.tvAmount.text = "$sign${CurrencyFormatter.format(tx.amount)} USD"
```

to:

```kotlin
binding.tvAmount.text = "$sign${CurrencyFormatter.format(tx.amount)}"
```

This is the only place that appends a hardcoded currency name; left in, it would read "€1,234.56 USD".

- [ ] **Step 5: Verify the build**

Ask the user to have `watch_build.ps1` running, then poll `watch_build_status.txt` for `SUCCESS`. Re-read ~10s later and only trust a value that has not moved. If a failure names a file whose content is provably correct, `touch` it — the watcher can swallow an edit made mid-build.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/common/util/CurrencyFormatter.kt app/src/main/java/com/example/dailyexpensetracker/ui/billdetail/BillDetailFragment.kt app/src/test/java/com/example/dailyexpensetracker/ui/common/util/CurrencyFormatterTest.kt
git commit -m "Make CurrencyFormatter symbol-aware"
```

---

### Task 2: `SettingsStore` and its Hilt binding

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsStore.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/di/SettingsModule.kt`
- Create: `app/src/test/java/com/example/dailyexpensetracker/ui/settings/FakeSettingsStore.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/DailyExpenseTrackerApp.kt`

**Interfaces:**
- Consumes: `CurrencyFormatter.symbol`, `CurrencyFormatter.DEFAULT_SYMBOL` (Task 1).
- Produces: `interface SettingsStore { var currencySymbol: String; var weekStart: Int }`, `class SharedPreferencesSettingsStore`, and `FakeSettingsStore` for test sources.

**No unit test for this task.** It is a `SharedPreferences` wrapper plus DI wiring; testing it would require Robolectric, a new dependency, for no real signal. The deliverable it produces — `FakeSettingsStore` — is what makes Tasks 4 and 8 testable. Verified by build and by the emulator check in Step 5.

- [ ] **Step 1: Create the store**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsStore.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's three persisted preferences.
 *
 * Deliberately not reactive. Every fragment collects its state inside
 * `repeatOnLifecycle(STARTED)`, so returning to a screen re-subscribes, the StateFlow re-emits and
 * the binding block re-runs — which re-formats every amount and rebuilds the calendar grid. A
 * synchronous read at bind time therefore reaches every screen on its next visit. Do not convert
 * this to a Flow; the lifecycle already does that work.
 */
interface SettingsStore {
    var currencySymbol: String
    var weekStart: Int

    companion object {
        val SUPPORTED_SYMBOLS = listOf("$", "€", "£", "₺", "¥")
        const val DEFAULT_WEEK_START = Calendar.SUNDAY
    }
}

@Singleton
class SharedPreferencesSettingsStore @Inject constructor(
    @ApplicationContext context: Context
) : SettingsStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY_SYMBOL, CurrencyFormatter.DEFAULT_SYMBOL)
            ?: CurrencyFormatter.DEFAULT_SYMBOL
        set(value) = prefs.edit().putString(KEY_CURRENCY_SYMBOL, value).apply()

    override var weekStart: Int
        get() = prefs.getInt(KEY_WEEK_START, SettingsStore.DEFAULT_WEEK_START)
        set(value) = prefs.edit().putInt(KEY_WEEK_START, value).apply()

    private companion object {
        const val KEY_CURRENCY_SYMBOL = "currency_symbol"
        const val KEY_WEEK_START = "week_start"
    }
}
```

- [ ] **Step 2: Bind it**

Create `app/src/main/java/com/example/dailyexpensetracker/di/SettingsModule.kt`:

```kotlin
package com.example.dailyexpensetracker.di

import com.example.dailyexpensetracker.ui.settings.SettingsStore
import com.example.dailyexpensetracker.ui.settings.SharedPreferencesSettingsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindSettingsStore(impl: SharedPreferencesSettingsStore): SettingsStore
}
```

- [ ] **Step 3: Apply the stored symbol at startup**

Replace `DailyExpenseTrackerApp.kt`:

```kotlin
package com.example.dailyexpensetracker

import android.app.Application
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.settings.SettingsStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DailyExpenseTrackerApp : Application() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate() {
        super.onCreate()
        // The formatter is an object read by DI-less adapters, so the stored symbol is pushed into
        // it once here rather than plumbed through every call site.
        CurrencyFormatter.symbol = settingsStore.currencySymbol
    }
}
```

- [ ] **Step 4: Create the fake for later tasks**

Create `app/src/test/java/com/example/dailyexpensetracker/ui/settings/FakeSettingsStore.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.settings

import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter

class FakeSettingsStore(
    override var currencySymbol: String = CurrencyFormatter.DEFAULT_SYMBOL,
    override var weekStart: Int = SettingsStore.DEFAULT_WEEK_START
) : SettingsStore
```

- [ ] **Step 5: Verify build and startup**

Poll `watch_build_status.txt` for `SUCCESS`. Then launch the app on the emulator and confirm it still starts and Home still shows `$` amounts:

```bash
C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat -d | grep "FATAL EXCEPTION"
```

Expected: no output. A missing Hilt binding surfaces here, not at compile time.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsStore.kt app/src/main/java/com/example/dailyexpensetracker/di/SettingsModule.kt app/src/main/java/com/example/dailyexpensetracker/DailyExpenseTrackerApp.kt app/src/test/java/com/example/dailyexpensetracker/ui/settings/FakeSettingsStore.kt
git commit -m "Add SettingsStore over SharedPreferences"
```

---

### Task 3: Week start as a parameter on `CalendarMonth`

Pure and fully JVM-testable. The default keeps all seven existing `CalendarMonthTest` cases compiling and passing untouched.

**Files:**
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonth.kt`
- Test: `app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonthTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `CalendarMonth.cellsFor(monthStartMillis: Long, transactions: List<Transaction>, weekStart: Int = Calendar.SUNDAY): List<DayCell>`.

- [ ] **Step 1: Write the failing tests**

Append to the existing `CalendarMonthTest` class. Use a month whose first day is known: 1 March 2026 is a Sunday, 1 June 2026 is a Monday.

```kotlin
    @Test
    fun `a Sunday-start month needs no leading blanks on a Sunday week`() {
        // 1 March 2026 falls on a Sunday.
        val marchStart = MonthRange.monthStart(dateMillis(2026, Calendar.MARCH, 1))

        val cells = CalendarMonth.cellsFor(marchStart, emptyList(), Calendar.SUNDAY)

        assertEquals(1, cells.first().dayOfMonth)
    }

    @Test
    fun `a Sunday-start month needs six leading blanks on a Monday week`() {
        val marchStart = MonthRange.monthStart(dateMillis(2026, Calendar.MARCH, 1))

        val cells = CalendarMonth.cellsFor(marchStart, emptyList(), Calendar.MONDAY)

        assertEquals(6, cells.count { it.dayOfMonth == null })
        assertEquals(1, cells[6].dayOfMonth)
    }

    @Test
    fun `a Monday-start month needs no leading blanks on a Monday week`() {
        // 1 June 2026 falls on a Monday.
        val juneStart = MonthRange.monthStart(dateMillis(2026, Calendar.JUNE, 1))

        val cells = CalendarMonth.cellsFor(juneStart, emptyList(), Calendar.MONDAY)

        assertEquals(1, cells.first().dayOfMonth)
    }

    @Test
    fun `week start does not change how many real days the month has`() {
        val marchStart = MonthRange.monthStart(dateMillis(2026, Calendar.MARCH, 1))

        val sunday = CalendarMonth.cellsFor(marchStart, emptyList(), Calendar.SUNDAY)
        val monday = CalendarMonth.cellsFor(marchStart, emptyList(), Calendar.MONDAY)

        assertEquals(31, sunday.count { it.dayOfMonth != null })
        assertEquals(31, monday.count { it.dayOfMonth != null })
    }
```

Add this helper to the same class if it does not already have one:

```kotlin
    private fun dateMillis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
```

Ensure `import java.util.Calendar` and `import com.example.dailyexpensetracker.ui.common.util.MonthRange` are present.

- [ ] **Step 2: Confirm the tests cannot pass yet**

`cellsFor` currently takes two parameters. The three-argument calls will not compile. Confirm by reading the file.

- [ ] **Step 3: Add the parameter**

In `CalendarMonth.cellsFor`, change the signature and the blank-count line only:

```kotlin
    fun cellsFor(
        monthStartMillis: Long,
        transactions: List<Transaction>,
        weekStart: Int = Calendar.SUNDAY
    ): List<DayCell> {
        val month = MonthRange.calendarAt(monthStartMillis)
        val leadingBlanks = (month.get(Calendar.DAY_OF_WEEK) - weekStart + 7) % 7
```

The `+ 7) % 7` is what makes a Sunday (`Calendar.SUNDAY` = 1) land in the last column of a Monday-start week rather than producing `-1`.

Update the KDoc: replace the existing `ponytail:` note about the week being fixed to Sunday with a line saying the week start is now a caller-supplied parameter defaulting to Sunday.

- [ ] **Step 4: Verify the build, then commit**

Poll `watch_build_status.txt` for `SUCCESS`.

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonth.kt app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonthTest.kt
git commit -m "Support a configurable week start in CalendarMonth"
```

---

### Task 4: Wire week start through the calendar

**Files:**
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarViewModel.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarFragment.kt`
- Modify: `app/src/main/res/layout/fragment_calendar.xml`
- Test: `app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsStore` (Task 2), `FakeSettingsStore` (Task 2), `CalendarMonth.cellsFor(..., weekStart)` (Task 3).
- Produces: `CalendarViewModel(getTransactionsForPeriod, getCategories, settingsStore)` — note the **third constructor parameter**, which Task 4's tests and nothing else depend on.

- [ ] **Step 1: Write the failing test**

Add to `CalendarViewModelTest`. Its existing `viewModel()` helper takes no store, so change the helper and add a case:

```kotlin
    private fun viewModel(settingsStore: SettingsStore = FakeSettingsStore()) = CalendarViewModel(
        GetTransactionsForPeriodUseCase(transactionRepository),
        GetCategoriesUseCase(categoryRepository),
        settingsStore
    )

    @Test
    fun `the grid honours a Monday week start`() = runTest {
        val sundayStart = viewModel(FakeSettingsStore(weekStart = Calendar.SUNDAY))
        subscribe(sundayStart.uiState)
        val sundayBlanks = sundayStart.uiState.value.cells.count { it.dayOfMonth == null }

        val mondayStart = viewModel(FakeSettingsStore(weekStart = Calendar.MONDAY))
        subscribe(mondayStart.uiState)
        val mondayBlanks = mondayStart.uiState.value.cells.count { it.dayOfMonth == null }

        // The two week starts differ by exactly one column, wrapping at seven.
        assertEquals((sundayBlanks + 6) % 7, mondayBlanks)
    }
```

Add `import com.example.dailyexpensetracker.ui.settings.FakeSettingsStore`, `import com.example.dailyexpensetracker.ui.settings.SettingsStore` and `import java.util.Calendar` if absent.

- [ ] **Step 2: Confirm it fails**

`CalendarViewModel` takes two constructor parameters today, so the three-argument call will not compile.

- [ ] **Step 3: Inject the store into the ViewModel**

In `CalendarViewModel`, add the constructor parameter and pass it through:

```kotlin
class CalendarViewModel @Inject constructor(
    getTransactionsForPeriod: GetTransactionsForPeriodUseCase,
    getCategories: GetCategoriesUseCase,
    private val settingsStore: SettingsStore
) : ViewModel() {
```

and in the `combine` block change the `cells` line to:

```kotlin
            cells = CalendarMonth.cellsFor(monthStart, transactions, settingsStore.weekStart),
```

Add `import com.example.dailyexpensetracker.ui.settings.SettingsStore`.

- [ ] **Step 4: Give the weekday header row an id**

In `fragment_calendar.xml`, the `LinearLayout` holding the seven `@style/CalendarWeekdayLabel` TextViews (it begins immediately after the month-navigation row and has `android:paddingBottom="4dp"`) gains an id. Add as its first attribute:

```xml
            android:id="@+id/llWeekdayHeader"
```

Leave the seven child `TextView`s exactly as they are — they are bound by index, so they need no ids of their own.

- [ ] **Step 5: Reorder the header labels to match**

In `CalendarFragment.onViewCreated`, before the state collection, add:

```kotlin
        bindWeekdayHeader()
```

and add this method to the fragment:

```kotlin
    /**
     * The seven header labels are fixed in XML in Sunday-first order. Rotating them here keeps the
     * header aligned with the grid — if they ever disagree, the calendar looks like an off-by-one
     * in CalendarMonth's blank count, which sends you debugging the wrong file.
     */
    private fun bindWeekdayHeader() {
        val sundayFirst = listOf(
            R.string.calendar_day_sun,
            R.string.calendar_day_mon,
            R.string.calendar_day_tue,
            R.string.calendar_day_wed,
            R.string.calendar_day_thu,
            R.string.calendar_day_fri,
            R.string.calendar_day_sat
        )
        val offset = settingsStore.weekStart - Calendar.SUNDAY
        for (column in 0 until 7) {
            val label = binding.llWeekdayHeader.getChildAt(column) as TextView
            label.setText(sundayFirst[(offset + column) % 7])
        }
    }
```

The fragment needs the store injected. Add to `CalendarFragment`:

```kotlin
    @Inject
    lateinit var settingsStore: SettingsStore
```

`CalendarFragment` is already annotated `@AndroidEntryPoint`, so field injection works. Add imports: `android.widget.TextView`, `com.example.dailyexpensetracker.R`, `com.example.dailyexpensetracker.ui.settings.SettingsStore`, `java.util.Calendar`, `javax.inject.Inject`.

- [ ] **Step 6: Verify build and appearance**

Poll for `SUCCESS`, then open Calendar on the emulator and screenshot it. With the default Sunday start the header must still read `S M T W T F S` and day 1 must sit under the correct column — this task must be a no-op visually until Task 9 lets the setting change.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/calendar/ app/src/main/res/layout/fragment_calendar.xml app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarViewModelTest.kt
git commit -m "Thread the week start through the calendar"
```

---

### Task 5: Fix the seeder's hardcoded ids

This is the blocker described in the spec. `DatabaseSeeder` assumes `autoGenerate` starts at 1, which is true only on a fresh database. `AUTOINCREMENT` sequences survive a row delete, so a second seed would insert categories with fresh ids while transactions still referenced 1–10 — and `TransactionEntity`'s foreign key would reject them.

**Files:**
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/local/dao/CategoryDao.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/local/dao/CardDao.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/local/DatabaseSeeder.kt`
- Create: `app/src/test/java/com/example/dailyexpensetracker/data/local/FakeDaos.kt`
- Test: `app/src/test/java/com/example/dailyexpensetracker/data/local/DatabaseSeederTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `CategoryDao.insertAll(...): List<Long>`, `CardDao.insertAll(...): List<Long>`, and the test fakes `FakeCategoryDao`, `FakeCardDao`, `FakeTransactionDao` (each with an `items` list for assertions).

- [ ] **Step 1: Write the fakes**

Create `app/src/test/java/com/example/dailyexpensetracker/data/local/FakeDaos.kt`. These fakes reproduce the behaviour that matters: ids are handed out from an ever-increasing counter that a `deleteAll` does **not** reset, exactly like `AUTOINCREMENT`.

```kotlin
package com.example.dailyexpensetracker.data.local

import com.example.dailyexpensetracker.data.local.dao.CardDao
import com.example.dailyexpensetracker.data.local.dao.CategoryDao
import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import com.example.dailyexpensetracker.data.local.entity.CardEntity
import com.example.dailyexpensetracker.data.local.entity.CategoryEntity
import com.example.dailyexpensetracker.data.local.entity.TransactionEntity
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Hands out ids the way AUTOINCREMENT does: monotonic, and never reset by a delete. */
private class IdSequence {
    private var next = 1L
    fun take(): Long = next++
}

class FakeCategoryDao : CategoryDao {
    val items = mutableListOf<CategoryEntity>()
    private val ids = IdSequence()
    private val state = MutableStateFlow<List<CategoryEntity>>(emptyList())

    override fun getAll(): Flow<List<CategoryEntity>> = state
    override fun getByType(isExpense: Boolean): Flow<List<CategoryEntity>> =
        state.map { list -> list.filter { it.isExpense == isExpense } }

    override suspend fun updateBudget(id: Long, limit: Double?) {
        items.replaceAll { if (it.id == id) it.copy(budgetLimit = limit) else it }
        state.value = items.toList()
    }

    override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> {
        val assigned = categories.map { it.copy(id = ids.take()) }
        items += assigned
        state.value = items.toList()
        return assigned.map { it.id }
    }

    override suspend fun deleteAll() {
        items.clear()
        state.value = emptyList()
    }
}

class FakeCardDao : CardDao {
    val items = mutableListOf<CardEntity>()
    private val ids = IdSequence()
    private val state = MutableStateFlow<List<CardEntity>>(emptyList())

    override fun getAll(): Flow<List<CardEntity>> = state

    override suspend fun insertAll(cards: List<CardEntity>): List<Long> {
        val assigned = cards.map { it.copy(id = ids.take()) }
        items += assigned
        state.value = items.toList()
        return assigned.map { it.id }
    }

    override suspend fun deleteAll() {
        items.clear()
        state.value = emptyList()
    }
}
```

In the same file, the transaction fake — `TransactionDao` has twelve members and every one must be implemented:

```kotlin
class FakeTransactionDao : TransactionDao {
    val items = mutableListOf<TransactionEntity>()
    private val ids = IdSequence()
    private val state = MutableStateFlow<List<TransactionEntity>>(emptyList())

    private fun publish() {
        state.value = items.toList()
    }

    override fun getAll(): Flow<List<TransactionEntity>> = state

    override fun getByDateRange(start: Long, end: Long): Flow<List<TransactionEntity>> =
        state.map { list -> list.filter { it.date in start..end } }

    override fun getRecent(limit: Int): Flow<List<TransactionEntity>> =
        state.map { list -> list.sortedByDescending { it.date }.take(limit) }

    override fun getSumByTypeAndDateRange(
        type: TransactionType,
        start: Long,
        end: Long
    ): Flow<Double?> = state.map { list ->
        list.filter { it.type == type && it.date in start..end }.sumOf { it.amount }
    }

    override fun getByStatus(status: TransactionStatus): Flow<List<TransactionEntity>> =
        state.map { list -> list.filter { it.status == status } }

    override fun getById(id: Long): Flow<TransactionEntity?> =
        state.map { list -> list.find { it.id == id } }

    override suspend fun updateStatus(id: Long, status: TransactionStatus) {
        items.replaceAll { if (it.id == id) it.copy(status = status) else it }
        publish()
    }

    override suspend fun delete(id: Long) {
        items.removeAll { it.id == id }
        publish()
    }

    override suspend fun update(transaction: TransactionEntity) {
        items.replaceAll { if (it.id == transaction.id) transaction else it }
        publish()
    }

    override suspend fun insert(transaction: TransactionEntity): Long {
        val assigned = transaction.copy(id = ids.take())
        items += assigned
        publish()
        return assigned.id
    }

    override suspend fun insertAll(transactions: List<TransactionEntity>) {
        items += transactions.map { it.copy(id = ids.take()) }
        publish()
    }

    override suspend fun deleteAll() {
        items.clear()
        publish()
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/example/dailyexpensetracker/data/local/DatabaseSeederTest.kt`:

```kotlin
package com.example.dailyexpensetracker.data.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSeederTest {

    @Test
    fun `every seeded transaction points at a real category`() = runTest {
        val categoryDao = FakeCategoryDao()
        val cardDao = FakeCardDao()
        val transactionDao = FakeTransactionDao()

        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)

        val categoryIds = categoryDao.items.map { it.id }.toSet()
        assertTrue(transactionDao.items.all { it.categoryId in categoryIds })
    }

    /**
     * The regression this test exists for: AUTOINCREMENT does not reset when rows are deleted, so a
     * reseed hands out fresh ids. A seeder that hardcodes 1..10 would leave every transaction
     * pointing at a category that no longer exists, and the foreign key would reject the insert.
     */
    @Test
    fun `a reseed after a clear still resolves every category`() = runTest {
        val categoryDao = FakeCategoryDao()
        val cardDao = FakeCardDao()
        val transactionDao = FakeTransactionDao()

        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)
        transactionDao.deleteAll()
        categoryDao.deleteAll()
        cardDao.deleteAll()
        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)

        val categoryIds = categoryDao.items.map { it.id }.toSet()
        assertEquals(10, categoryIds.size)
        assertTrue(transactionDao.items.all { it.categoryId in categoryIds })
        assertTrue(transactionDao.items.all { it.cardId in cardDao.items.map { card -> card.id } })
    }
}
```

- [ ] **Step 3: Confirm it fails**

The second test fails on the current seeder: after the clear, categories are inserted with ids 11–20 while the seeder still writes `categoryId = 1`. The first test passes today. Both fail to compile until Step 4 adds `deleteAll` and the `List<Long>` returns.

- [ ] **Step 4: Change the DAOs**

In `CategoryDao`, change the insert and add a delete:

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
```

In `CardDao`, the same:

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>): List<Long>

    @Query("DELETE FROM cards")
    suspend fun deleteAll()
```

In `TransactionDao`, add only the delete (nothing needs transaction ids back):

```kotlin
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
```

- [ ] **Step 5: Use the returned ids in the seeder**

In `DatabaseSeeder.seed`, replace `categoryDao.insertAll(categories)` and the hardcoded id block with:

```kotlin
        val categoryIds = categoryDao.insertAll(categories)
```

and

```kotlin
        val cardIds = cardDao.insertAll(cards)
```

Then replace the comment and constants:

```kotlin
        // Ids as actually assigned by the insert, by position in the lists above. Hardcoding 1..10
        // held only on a fresh database; AUTOINCREMENT does not reset when rows are deleted, so a
        // reseed would leave every transaction pointing at a category that no longer exists.
        val groceryId = categoryIds[0]
        val foodId = categoryIds[1]
        val clothesId = categoryIds[2]
        val medicineId = categoryIds[4]
        val fuelId = categoryIds[5]
        val salaryId = categoryIds[9]
        val primaryCardId = cardIds[0]
        val secondaryCardId = cardIds[1]
```

In the `transactions` list, replace every `cardId = 1` with `cardId = primaryCardId` and every `cardId = 2` with `cardId = secondaryCardId`. There are nine transactions; check each one.

- [ ] **Step 6: Verify build, then commit**

Poll for `SUCCESS`. A Room `@Insert` returning `List<Long>` is valid and needs no other change.

```bash
git add app/src/main/java/com/example/dailyexpensetracker/data/local/ app/src/test/java/com/example/dailyexpensetracker/data/local/
git commit -m "Seed from the ids Room actually assigns"
```

---

### Task 6: Reset-to-seed-data, through the repository layer

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/domain/repository/DataResetRepository.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/data/repository/DataResetRepositoryImpl.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/domain/usecase/ResetDataUseCase.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/example/dailyexpensetracker/data/repository/DataResetRepositoryImplTest.kt`

The extra interface exists so `domain` never imports `data`, matching every other repository here. Reset goes through a use case for the same reason — every ViewModel in this codebase talks to use cases, not repositories.

**Interfaces:**
- Consumes: DAO `deleteAll()` methods and `DatabaseSeeder.seed` (Task 5), the fake DAOs (Task 5).
- Produces: `interface DataResetRepository { suspend fun resetToSeed() }`, `class DataResetRepositoryImpl`, `class ResetDataUseCase { suspend operator fun invoke() }`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/dailyexpensetracker/data/repository/DataResetRepositoryImplTest.kt`:

```kotlin
package com.example.dailyexpensetracker.data.repository

import com.example.dailyexpensetracker.data.local.FakeCardDao
import com.example.dailyexpensetracker.data.local.FakeCategoryDao
import com.example.dailyexpensetracker.data.local.FakeTransactionDao
import com.example.dailyexpensetracker.data.local.entity.CategoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataResetRepositoryImplTest {

    @Test
    fun `reset replaces edited data with a fresh seed`() = runTest {
        val categoryDao = FakeCategoryDao()
        val cardDao = FakeCardDao()
        val transactionDao = FakeTransactionDao()
        categoryDao.insertAll(
            listOf(
                CategoryEntity(
                    name = "A category the user made",
                    iconName = "grocery",
                    colorHex = "#FFFFFF",
                    budgetLimit = null,
                    isExpense = true
                )
            )
        )
        val repository = DataResetRepositoryImpl(categoryDao, cardDao, transactionDao)

        repository.resetToSeed()

        assertEquals(10, categoryDao.items.size)
        assertTrue(categoryDao.items.none { it.name == "A category the user made" })
        assertTrue(transactionDao.items.isNotEmpty())
    }

    @Test
    fun `reset leaves every transaction pointing at a seeded category`() = runTest {
        val categoryDao = FakeCategoryDao()
        val cardDao = FakeCardDao()
        val transactionDao = FakeTransactionDao()
        val repository = DataResetRepositoryImpl(categoryDao, cardDao, transactionDao)

        repository.resetToSeed()
        repository.resetToSeed()

        val categoryIds = categoryDao.items.map { it.id }.toSet()
        assertTrue(transactionDao.items.all { it.categoryId in categoryIds })
    }
}
```

- [ ] **Step 2: Confirm it fails**

`DataResetRepositoryImpl` does not exist yet.

- [ ] **Step 3: Write the interface, the implementation and the use case**

`domain/repository/DataResetRepository.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.repository

interface DataResetRepository {
    /** Clears every table and repopulates it with the sample data shipped in DatabaseSeeder. */
    suspend fun resetToSeed()
}
```

`data/repository/DataResetRepositoryImpl.kt`:

```kotlin
package com.example.dailyexpensetracker.data.repository

import com.example.dailyexpensetracker.data.local.DatabaseSeeder
import com.example.dailyexpensetracker.data.local.dao.CardDao
import com.example.dailyexpensetracker.data.local.dao.CategoryDao
import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import com.example.dailyexpensetracker.domain.repository.DataResetRepository
import javax.inject.Inject

class DataResetRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val cardDao: CardDao,
    private val transactionDao: TransactionDao
) : DataResetRepository {

    override suspend fun resetToSeed() {
        // Transactions first: they hold the foreign keys into categories and cards.
        transactionDao.deleteAll()
        categoryDao.deleteAll()
        cardDao.deleteAll()
        DatabaseSeeder.seed(categoryDao, cardDao, transactionDao)
    }
}
```

`domain/usecase/ResetDataUseCase.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.repository.DataResetRepository
import javax.inject.Inject

class ResetDataUseCase @Inject constructor(
    private val repository: DataResetRepository
) {
    suspend operator fun invoke() = repository.resetToSeed()
}
```

- [ ] **Step 4: Bind the repository**

In `RepositoryModule`, add alongside the existing bindings:

```kotlin
    @Binds
    abstract fun bindDataResetRepository(impl: DataResetRepositoryImpl): DataResetRepository
```

with imports for both types.

- [ ] **Step 5: Verify build, then commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/domain/ app/src/main/java/com/example/dailyexpensetracker/data/repository/ app/src/main/java/com/example/dailyexpensetracker/di/RepositoryModule.kt app/src/test/java/com/example/dailyexpensetracker/data/repository/
git commit -m "Add reset-to-seed through the repository layer"
```

---

### Task 7: Settings resources

Resources only — no Kotlin, so nothing to unit-test. Built before the fragment so Task 9 never references a resource that does not exist.

**Files:**
- Create: `app/src/main/res/drawable/ic_settings_24.xml`
- Create: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: `R.drawable.ic_settings_24`, `R.layout.fragment_settings` (binding class `FragmentSettingsBinding` with ids `ivBack`, `rowCurrency`, `tvCurrencyValue`, `rowWeekStart`, `tvWeekStartValue`, `rowResetData`), and the string resources listed below.

- [ ] **Step 1: Add the icon**

Create `app/src/main/res/drawable/ic_settings_24.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z" />
</vector>
```

- [ ] **Step 2: Add the strings**

Append inside `<resources>` in `app/src/main/res/values/strings.xml`:

```xml
    <string name="settings_title">Settings</string>
    <string name="settings_back">Back</string>
    <string name="settings_currency">Currency</string>
    <string name="settings_week_start">First day of week</string>
    <string name="settings_week_start_sunday">Sunday</string>
    <string name="settings_week_start_monday">Monday</string>
    <string name="settings_reset_data">Reset data</string>
    <string name="settings_reset_data_summary">Delete everything and restore the sample data</string>
    <string name="settings_reset_confirm_title">Reset all data?</string>
    <string name="settings_reset_confirm_message">Every transaction, category and card is deleted and replaced with the original sample data. This cannot be undone.</string>
    <string name="settings_reset_confirm_action">Reset</string>
    <string name="settings_reset_done">Data reset</string>
    <string name="settings_cancel">Cancel</string>
```

- [ ] **Step 3: Add the layout**

Create `app/src/main/res/layout/fragment_settings.xml`. Match the app's existing screens: a `@color/purple_primary` header, `@color/background_light_gray` ground, white `CardView` sections.

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_light_gray"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@color/purple_primary"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:padding="20dp">

            <ImageView
                android:id="@+id/ivBack"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:contentDescription="@string/settings_back"
                android:src="@drawable/ic_back_24"
                app:tint="@color/white" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:text="@string/settings_title"
                android:textColor="@color/white"
                android:textSize="20sp"
                android:textStyle="bold" />
        </LinearLayout>

        <androidx.cardview.widget.CardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_margin="16dp"
            app:cardBackgroundColor="@color/white"
            app:cardCornerRadius="20dp"
            app:cardElevation="2dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="4dp">

                <LinearLayout
                    android:id="@+id/rowCurrency"
                    style="@style/SettingsRow">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_currency"
                        android:textColor="@color/text_primary"
                        android:textSize="15sp" />

                    <TextView
                        android:id="@+id/tvCurrencyValue"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textColor="@color/text_secondary"
                        android:textSize="15sp"
                        tools:text="$" />
                </LinearLayout>

                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:layout_marginStart="16dp"
                    android:layout_marginEnd="16dp"
                    android:background="@color/divider_light" />

                <LinearLayout
                    android:id="@+id/rowWeekStart"
                    style="@style/SettingsRow">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_week_start"
                        android:textColor="@color/text_primary"
                        android:textSize="15sp" />

                    <TextView
                        android:id="@+id/tvWeekStartValue"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textColor="@color/text_secondary"
                        android:textSize="15sp"
                        tools:text="Sunday" />
                </LinearLayout>
            </LinearLayout>
        </androidx.cardview.widget.CardView>

        <androidx.cardview.widget.CardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:layout_marginEnd="16dp"
            android:layout_marginBottom="16dp"
            app:cardBackgroundColor="@color/white"
            app:cardCornerRadius="20dp"
            app:cardElevation="2dp">

            <LinearLayout
                android:id="@+id/rowResetData"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:orientation="vertical"
                android:paddingStart="16dp"
                android:paddingTop="16dp"
                android:paddingEnd="16dp"
                android:paddingBottom="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_reset_data"
                    android:textColor="@color/soft_red"
                    android:textSize="15sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/settings_reset_data_summary"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp" />
            </LinearLayout>
        </androidx.cardview.widget.CardView>
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

Add the shared row style to `app/src/main/res/values/styles.xml`, inside `<resources>`:

```xml
    <style name="SettingsRow">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:orientation">horizontal</item>
        <item name="android:gravity">center_vertical</item>
        <item name="android:background">?attr/selectableItemBackground</item>
        <item name="android:paddingStart">16dp</item>
        <item name="android:paddingTop">18dp</item>
        <item name="android:paddingEnd">16dp</item>
        <item name="android:paddingBottom">18dp</item>
    </style>
```

The back arrow is the existing `@drawable/ic_back_24` — already used by the Bill Detail and Calendar headers. Do not create a second one; `ic_arrow_up_24` is a different icon and is not a substitute.

- [ ] **Step 4: Verify build, then commit**

Poll for `SUCCESS` — a bad vector path or a missing drawable fails resource compilation.

```bash
git add app/src/main/res/
git commit -m "Add Settings resources"
```

---

### Task 8: `SettingsViewModel`

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/example/dailyexpensetracker/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsStore` and `FakeSettingsStore` (Task 2), `ResetDataUseCase` (Task 6), `CurrencyFormatter.symbol` (Task 1).
- Produces: `data class SettingsUiState(val currencySymbol: String, val weekStart: Int)`, `SettingsViewModel(settingsStore, resetData)` with `uiState: StateFlow<SettingsUiState>`, `setCurrencySymbol(String)`, `setWeekStart(Int)`, `suspend fun resetData()`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/dailyexpensetracker/ui/settings/SettingsViewModelTest.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.settings

import com.example.dailyexpensetracker.domain.repository.DataResetRepository
import com.example.dailyexpensetracker.domain.usecase.ResetDataUseCase
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SettingsViewModelTest {

    private class RecordingResetRepository : DataResetRepository {
        var resetCount = 0
        override suspend fun resetToSeed() {
            resetCount++
        }
    }

    /** `CurrencyFormatter.symbol` is global; leaving it changed would leak into other suites. */
    @After
    fun resetSymbol() {
        CurrencyFormatter.symbol = CurrencyFormatter.DEFAULT_SYMBOL
    }

    private fun viewModel(
        store: SettingsStore = FakeSettingsStore(),
        repository: DataResetRepository = RecordingResetRepository()
    ) = SettingsViewModel(store, ResetDataUseCase(repository))

    @Test
    fun `opens showing what is stored`() {
        val store = FakeSettingsStore(currencySymbol = "£", weekStart = Calendar.MONDAY)

        val state = viewModel(store).uiState.value

        assertEquals("£", state.currencySymbol)
        assertEquals(Calendar.MONDAY, state.weekStart)
    }

    @Test
    fun `changing the currency persists it and updates the formatter`() {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)

        viewModel.setCurrencySymbol("€")

        assertEquals("€", viewModel.uiState.value.currencySymbol)
        assertEquals("€", store.currencySymbol)
        assertEquals("€", CurrencyFormatter.symbol)
        assertTrue(CurrencyFormatter.format(1.0).startsWith("€"))
    }

    @Test
    fun `changing the week start persists it`() {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store)

        viewModel.setWeekStart(Calendar.MONDAY)

        assertEquals(Calendar.MONDAY, viewModel.uiState.value.weekStart)
        assertEquals(Calendar.MONDAY, store.weekStart)
    }

    @Test
    fun `reset delegates to the use case`() = runTest {
        val repository = RecordingResetRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.resetData()

        assertEquals(1, repository.resetCount)
    }
}
```

- [ ] **Step 2: Confirm it fails**

`SettingsViewModel` does not exist yet.

- [ ] **Step 3: Write the ViewModel**

```kotlin
package com.example.dailyexpensetracker.ui.settings

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.domain.usecase.ResetDataUseCase
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val currencySymbol: String,
    val weekStart: Int
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val resetDataUseCase: ResetDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(settingsStore.currencySymbol, settingsStore.weekStart)
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setCurrencySymbol(symbol: String) {
        settingsStore.currencySymbol = symbol
        // Pushed straight into the formatter so screens re-read it on their next visit, without
        // waiting for a restart.
        CurrencyFormatter.symbol = symbol
        _uiState.value = _uiState.value.copy(currencySymbol = symbol)
    }

    fun setWeekStart(weekStart: Int) {
        settingsStore.weekStart = weekStart
        _uiState.value = _uiState.value.copy(weekStart = weekStart)
    }

    suspend fun resetData() = resetDataUseCase()
}
```

The constructor property is `resetDataUseCase`, not `resetData` — a property and a function sharing one name here reads as a recursive call and is needlessly confusing.

Note this ViewModel has no `stateIn`, so its tests need neither `MainDispatcherRule` nor `subscribe()` — the state is a plain `MutableStateFlow` read synchronously.

- [ ] **Step 4: Verify build, then commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsViewModel.kt app/src/test/java/com/example/dailyexpensetracker/ui/settings/SettingsViewModelTest.kt
git commit -m "Add SettingsViewModel"
```

---

### Task 9: `SettingsFragment`, navigation, and the Home entry point

Last, so nothing before it referenced a screen that did not exist.

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsFragment.kt`
- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/home/HomeFragment.kt`

**Interfaces:**
- Consumes: `SettingsViewModel` (Task 8), `FragmentSettingsBinding` and the strings (Task 7), `SettingsStore.SUPPORTED_SYMBOLS` (Task 2).
- Produces: the `settingsFragment` destination and `HomeFragmentDirections.actionHomeFragmentToSettingsFragment()`.

- [ ] **Step 1: Add the destination**

In `nav_graph.xml`, add a second action inside the existing `homeFragment` block, next to the calendar one:

```xml
        <action
            android:id="@+id/action_homeFragment_to_settingsFragment"
            app:destination="@id/settingsFragment" />
```

and a new destination alongside the others:

```xml
    <fragment
        android:id="@+id/settingsFragment"
        android:name="com.example.dailyexpensetracker.ui.settings.SettingsFragment"
        android:label="@string/settings_title" />
```

**If the build later fails on `Unresolved reference 'HomeFragmentDirections'`,** the watcher swallowed this edit and `generateSafeArgsDebug` stayed `UP-TO-DATE`. `touch` `nav_graph.xml` to force a fresh cycle before debugging anything else.

- [ ] **Step 2: Add the header icon**

In `fragment_home.xml`, the header `LinearLayout` currently ends with the `ivCalendar` `ImageView`. Add a second icon **before** it so settings sits to the left of the calendar, or after it to sit right — put it **after**, with a start margin:

```xml
                <ImageView
                    android:id="@+id/ivSettings"
                    android:layout_width="26dp"
                    android:layout_height="26dp"
                    android:layout_marginStart="16dp"
                    android:contentDescription="@string/settings_title"
                    android:src="@drawable/ic_settings_24"
                    app:tint="@color/white" />
```

- [ ] **Step 3: Wire the click**

In `HomeFragment.onViewCreated`, below the existing `ivCalendar` listener:

```kotlin
        binding.ivSettings.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToSettingsFragment())
        }
```

- [ ] **Step 4: Write the fragment**

```kotlin
package com.example.dailyexpensetracker.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivBack.setOnClickListener { findNavController().navigateUp() }
        binding.rowCurrency.setOnClickListener { showCurrencyDialog() }
        binding.rowWeekStart.setOnClickListener { showWeekStartDialog() }
        binding.rowResetData.setOnClickListener { showResetDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvCurrencyValue.text = state.currencySymbol
                    binding.tvWeekStartValue.setText(weekStartLabel(state.weekStart))
                }
            }
        }
    }

    private fun weekStartLabel(weekStart: Int) =
        if (weekStart == Calendar.MONDAY) R.string.settings_week_start_monday
        else R.string.settings_week_start_sunday

    private fun showCurrencyDialog() {
        val symbols = SettingsStore.SUPPORTED_SYMBOLS
        val checked = symbols.indexOf(viewModel.uiState.value.currencySymbol).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_currency)
            .setSingleChoiceItems(symbols.toTypedArray(), checked) { dialog, which ->
                viewModel.setCurrencySymbol(symbols[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showWeekStartDialog() {
        val labels = arrayOf(
            getString(R.string.settings_week_start_sunday),
            getString(R.string.settings_week_start_monday)
        )
        val values = intArrayOf(Calendar.SUNDAY, Calendar.MONDAY)
        val checked = values.indexOf(viewModel.uiState.value.weekStart).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_week_start)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setWeekStart(values[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showResetDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_reset_confirm_title)
            .setMessage(R.string.settings_reset_confirm_message)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_reset_confirm_action) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.resetData()
                    Toast.makeText(requireContext(), R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 5: Walk it on the emulator**

Poll for `SUCCESS`, then drive `adb` and screenshot each step. Use `uiautomator dump` for tap coordinates rather than guessing.

1. Home shows two header icons; tapping the gear opens Settings.
2. Change currency to `€`. Back out to Home — every amount now reads `€`. Check Wallet, Bills, Reports and a bill detail too; the detail screen must **not** say "USD".
3. Set week start to Monday, open Calendar: the header must read `M T W T F S S` **and** the grid must shift with it. A header that moves alone is the failure this was designed to avoid.
4. Reset data, confirm, then check every screen repopulates.

After each step:

```bash
C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat -d | grep "FATAL EXCEPTION"
```

Expected: no output. A foreign-key failure after reset shows up here.

Screenshots and dumps go in the scratchpad, never the repo.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/settings/SettingsFragment.kt app/src/main/java/com/example/dailyexpensetracker/ui/home/HomeFragment.kt app/src/main/res/navigation/nav_graph.xml app/src/main/res/layout/fragment_home.xml
git commit -m "Add the Settings screen and its Home entry point"
```

---

### Task 10: Full verification and handoff

- [ ] **Step 1: Ask the user to run the suite**

Once, in a second terminal, without stopping the watcher:

```bash
./gradlew testDebugUnitTest lintDebug --console=plain > verify_output.txt 2>&1
```

- [ ] **Step 2: Read the reports yourself**

Do not ask the user to paste output.

```bash
ls -la --time-style=+%m-%d_%H:%M app/build/test-results/testDebugUnitTest/*.xml
grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml
tail -5 app/build/reports/lint-results-debug.txt
```

**Check the timestamps.** Stale XMLs from an earlier run are indistinguishable from a fresh pass by their contents alone, and `UP-TO-DATE` tasks are not evidence that anything ran.

Expected: every suite green; new suites `CurrencyFormatterTest`, `DatabaseSeederTest`, `DataResetRepositoryImplTest`, `SettingsViewModelTest` present, plus the new cases in `CalendarMonthTest` and `CalendarViewModelTest`. Lint: **0 errors**. Warning count may rise slightly from the new layout — check any new warning is a benign category, not a real defect.

- [ ] **Step 3: Update HANDOFF.md**

Record the **verified** test and lint numbers, not projected ones. Move Settings out of "What's left". Add the dark-mode sub-project as an open thread, carrying the spec's reasoning: `values-night/` has no colours file, `@color/white` is used 43 times as a card surface, and the real work is a semantic-colour refactor across ~125 references.

- [ ] **Step 4: Open the PR**

Push `feature/settings`, open a PR, and **ask before merging**.
