# Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Reports screen with three tabs — Expense Chart (donut of category spend), Budget Planner (spent-vs-limit with editable budgets), Billing Reports (six-month spend/income bar chart) — taking the Settings bottom-nav slot.

**Architecture:** One `reportsFragment` destination hosting a `TabLayout` and a `FrameLayout`, swapping three child fragments through `childFragmentManager`. A shared `ReportsViewModel` owns the selected month so all three tabs stay in step. Charts are two hand-written `View` subclasses whose geometry lives in pure, unit-testable functions. The only data-layer work is a write path for `Category.budgetLimit`.

**Tech Stack:** Same as Phase 1 — Kotlin, Room (KSP), Hilt (KSP), Navigation Component with Safe Args, DataBinding/ViewBinding, Coroutines + StateFlow, JUnit. No new dependencies.

**Spec:** [docs/superpowers/specs/2026-09-04-reports-design.md](../specs/2026-09-04-reports-design.md)

## Global Constraints

- **No new Gradle dependencies.** Charts are hand-drawn on `Canvas`; tab hosting uses `childFragmentManager`, deliberately **not** `ViewPager2` (not a current dependency).
- **No new image assets.** The forward arrow reuses `ic_back_24` with `android:rotation="180"`, as Calendar does.
- **No Room version bump and no schema change.** `budgetLimit` already exists as a nullable column on `categories`; this only adds a write path. Do not touch `AppDatabase`'s version — a bump would trigger the destructive migration and wipe seeded data.
- **`minSdk` is 24 — do NOT use `java.time` and do NOT enable core-library desugaring.** Use `java.util.Calendar`, matching `MonthRange`/`DateFormatter`.
- **Never bucket by `millis / 86_400_000`.** It drifts by the UTC offset and breaks across DST. All date grouping goes through `MonthRange`.
- **Never name a child view `android:id="@+id/root"`** — collides with the generated `binding.root`. See project memory `viewbinding-root-id-collision`.
- **Re-sync the `TabLayout` from the ViewModel in `onViewCreated`, and set `android:background="@android:color/transparent"` on it.** Both are Bills lessons: a recreated TabLayout resets to index 0 while the retained ViewModel holds the old value, and Material3's TabLayout paints its own surface background that hides a white selected label.
- RecyclerView adapters here are plain `RecyclerView.Adapter` with `submitList()` + `notifyDataSetChanged()`, not `ListAdapter`/`DiffUtil`.
- `app:tint` (not `android:tint`) on ImageViews; declare `xmlns:app` on the layout root.
- Claude cannot run Gradle. `watch_build.ps1` auto-builds on save; poll `watch_build_status.txt` with the settle-check in Task 1 Step 5. `testDebugUnitTest`/`lintDebug` need the user's terminal and are requested **once**, in Task 10.
- **If a build fails blaming a file whose on-disk content is provably correct**, the watcher swallowed the edit (it re-hashes after a build completes). `touch` the file to force a fresh cycle rather than debugging the code.
- **Read `watch_build.log` from its last `=== Build triggered at` marker only** — it is append-only, so grepping the whole file reports stale failures from earlier sessions.

---

## File Structure

```
app/src/main/java/com/example/dailyexpensetracker/
  ui/common/util/MonthRange.kt                       (create: month arithmetic, moved out of CalendarMonth)
  ui/calendar/CalendarMonth.kt                       (modify: keep DayCell + cellsFor, delegate arithmetic)
  ui/common/view/DonutChartView.kt                   (create)
  ui/common/view/BarChartView.kt                     (create)
  data/local/dao/CategoryDao.kt                      (modify: +updateBudget)
  domain/repository/CategoryRepository.kt            (modify: +updateBudget)
  data/repository/CategoryRepositoryImpl.kt          (modify: +updateBudget)
  domain/usecase/UpdateCategoryBudgetUseCase.kt      (create)
  ui/reports/ReportsFragment.kt                      (create: tab host)
  ui/reports/ReportsViewModel.kt                     (create: shared selected month)
  ui/reports/expense/ExpenseChartFragment.kt         (create)
  ui/reports/expense/ExpenseChartViewModel.kt        (create)
  ui/reports/expense/adapter/LegendAdapter.kt        (create)
  ui/reports/budget/BudgetPlannerFragment.kt         (create)
  ui/reports/budget/BudgetPlannerViewModel.kt        (create)
  ui/reports/budget/adapter/BudgetPlanAdapter.kt     (create)
  ui/reports/billing/BillingReportsFragment.kt       (create)
  ui/reports/billing/BillingReportsViewModel.kt      (create)
  ui/reports/billing/MonthlyTotals.kt                (create: pure bucketing)
  ui/placeholder/PlaceholderFragment.kt              (DELETE — orphaned)

app/src/test/java/com/example/dailyexpensetracker/
  ui/common/util/MonthRangeTest.kt                   (create: 5 tests moved from CalendarMonthTest)
  ui/calendar/CalendarMonthTest.kt                   (modify: keep 7 cellsFor tests)
  ui/common/view/ChartGeometryTest.kt                (create)
  ui/reports/billing/MonthlyTotalsTest.kt            (create)
  domain/usecase/FakeCategoryRepository.kt           (create)
  domain/usecase/UpdateCategoryBudgetUseCaseTest.kt  (create)

app/src/main/res/
  layout/fragment_reports.xml                        (create: header + tabs + container)
  layout/fragment_expense_chart.xml                  (create)
  layout/fragment_budget_planner.xml                 (create)
  layout/fragment_billing_reports.xml                (create)
  layout/item_legend.xml                             (create)
  layout/item_budget_plan.xml                        (create)
  layout/dialog_edit_budget.xml                      (create)
  layout/fragment_placeholder.xml                    (DELETE — orphaned)
  navigation/nav_graph.xml                           (modify: Settings placeholder -> reportsFragment)
  menu/bottom_nav_menu.xml                           (modify: same slot, new icon + label)
  drawable/ic_reports_24.xml                         (create)
  values/strings.xml                                 (modify: -nav_settings, +reports_* strings)
```

Every task compiles on its own, so each ends with a watcher build. The three tab fragments are built **before** the host that references them, so no task depends on code that doesn't exist yet.

---

### Task 1: Extract MonthRange from CalendarMonth

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/common/util/MonthRange.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonth.kt`
- Create: `app/src/test/java/com/example/dailyexpensetracker/ui/common/util/MonthRangeTest.kt`
- Modify: `app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonthTest.kt`

**Interfaces:**
- Produces: `MonthRange.monthStart(Long)`, `.nextMonthStart(Long)`, `.previousMonthStart(Long)`, `.dayStart(Long)`, `.sameDayInMonth(Long, Long)`, `.label(Long)`, `.monthsEndingAt(monthStartMillis: Long, count: Int): List<Long>`, `.calendarAt(Long): Calendar`. Tasks 5, 8 and the modified `CalendarMonth` all call these.
- `CalendarMonth` keeps only `DayCell` and `cellsFor(Long, List<Transaction>)`; its arithmetic functions are **removed**, not left as delegating wrappers.

This is a move, not a rewrite. Calendar's shipped behaviour must not change — the existing tests are the safety net.

- [ ] **Step 1: Create MonthRange with the arithmetic**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/common/util/MonthRange.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.common.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Month and day arithmetic in the device's local timezone. Free of Android types so it
 * unit-tests on the JVM.
 *
 * Every day comparison goes through [dayStart] rather than dividing millis by a day's length,
 * which would drift by the UTC offset and misfile late-evening values across DST.
 */
object MonthRange {

    private val monthLabelFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    /** Millis at 00:00 on day 1 of the month containing [millis]. */
    fun monthStart(millis: Long): Long = calendarAt(millis).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    /** Millis at 00:00 on day 1 of the following month — an exclusive end bound. */
    fun nextMonthStart(monthStartMillis: Long): Long = calendarAt(monthStartMillis).apply {
        add(Calendar.MONTH, 1)
    }.timeInMillis

    /** Millis at 00:00 on day 1 of the preceding month. */
    fun previousMonthStart(monthStartMillis: Long): Long = calendarAt(monthStartMillis).apply {
        add(Calendar.MONTH, -1)
    }.timeInMillis

    /** Millis at 00:00 on the day containing [millis]. */
    fun dayStart(millis: Long): Long = calendarAt(millis).timeInMillis

    /**
     * The same day-of-month as [dayMillis], moved into the month starting at [monthStartMillis]
     * and clamped to that month's length — so Jan 31 into February lands on the 28th or 29th
     * rather than wrapping into March.
     */
    fun sameDayInMonth(monthStartMillis: Long, dayMillis: Long): Long {
        val day = calendarAt(dayMillis).get(Calendar.DAY_OF_MONTH)
        val target = calendarAt(monthStartMillis)
        target.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(target.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return target.timeInMillis
    }

    /** e.g. "September 2026". */
    fun label(monthStartMillis: Long): String = monthLabelFormat.format(Date(monthStartMillis))

    /**
     * [count] consecutive month starts ending at (and including) [monthStartMillis], oldest first.
     * `monthsEndingAt(septemberStart, 6)` returns April..September.
     */
    fun monthsEndingAt(monthStartMillis: Long, count: Int): List<Long> {
        if (count <= 0) return emptyList()
        val months = ArrayList<Long>(count)
        var cursor = monthStartMillis
        repeat(count) {
            months += cursor
            cursor = previousMonthStart(cursor)
        }
        return months.reversed()
    }

    /**
     * A Calendar at [millis] with the time-of-day zeroed, in the device's local timezone.
     * Public so calendar-specific callers can read weekday and month-length fields off it.
     */
    fun calendarAt(millis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
```

- [ ] **Step 2: Reduce CalendarMonth to grid building**

Replace `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonth.kt` entirely:

```kotlin
package com.example.dailyexpensetracker.ui.calendar

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import java.util.Calendar

/** One cell of the month grid. Leading blanks carry nulls for both day fields. */
data class DayCell(
    val dayOfMonth: Int?,
    val dateMillis: Long?,
    val hasExpense: Boolean,
    val hasIncome: Boolean
)

/**
 * Builds the calendar month grid. Month arithmetic lives in [MonthRange], which Reports shares.
 *
 * ponytail: the week is fixed to start on Sunday so it stays in sync with the static weekday
 * header row; switch to Calendar.firstDayOfWeek if locale-aware week starts are ever needed.
 */
object CalendarMonth {

    /**
     * Leading blank cells to align day 1 under its weekday column, then one cell per day of the
     * month, each flagged by whether any of [transactions] that day was an expense and/or income.
     * Transactions outside the month are ignored. No trailing blanks — the grid just ends.
     */
    fun cellsFor(monthStartMillis: Long, transactions: List<Transaction>): List<DayCell> {
        val month = MonthRange.calendarAt(monthStartMillis)
        val leadingBlanks = month.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)

        val expenseDays = mutableSetOf<Int>()
        val incomeDays = mutableSetOf<Int>()
        for (transaction in transactions) {
            val txDay = MonthRange.calendarAt(transaction.date)
            val sameMonth = txDay.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
                txDay.get(Calendar.MONTH) == month.get(Calendar.MONTH)
            if (!sameMonth) continue
            val dayOfMonth = txDay.get(Calendar.DAY_OF_MONTH)
            if (transaction.type == TransactionType.EXPENSE) {
                expenseDays += dayOfMonth
            } else {
                incomeDays += dayOfMonth
            }
        }

        val cells = ArrayList<DayCell>(leadingBlanks + daysInMonth)
        repeat(leadingBlanks) { cells += DayCell(null, null, hasExpense = false, hasIncome = false) }
        for (day in 1..daysInMonth) {
            val dayCal = MonthRange.calendarAt(monthStartMillis)
            dayCal.set(Calendar.DAY_OF_MONTH, day)
            cells += DayCell(
                dayOfMonth = day,
                dateMillis = dayCal.timeInMillis,
                hasExpense = day in expenseDays,
                hasIncome = day in incomeDays
            )
        }
        return cells
    }
}
```

- [ ] **Step 3: Point CalendarViewModel and CalendarFragment at MonthRange**

`CalendarViewModel.kt` calls `CalendarMonth.dayStart`, `.monthStart`, `.nextMonthStart`, `.previousMonthStart`, `.label` and `.sameDayInMonth`. Change each to `MonthRange.` and add the import:

```kotlin
import com.example.dailyexpensetracker.ui.common.util.MonthRange
```

`CalendarMonth.cellsFor(...)` stays as-is, so keep that import too. Verify no other file references the moved functions:

```bash
grep -rn "CalendarMonth\." app/src/main app/src/test | grep -v "CalendarMonth.cellsFor"
```

Expected: no output.

- [ ] **Step 4: Split the tests to match**

Create `app/src/test/java/com/example/dailyexpensetracker/ui/common/util/MonthRangeTest.kt` with the five arithmetic tests, moved verbatim except that the receiver becomes `MonthRange`:

```kotlin
package com.example.dailyexpensetracker.ui.common.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MonthRangeTest {

    /** Local-time millis for a given date. [month] is 0-based, matching Calendar. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dayOf(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

    @Test
    fun `sameDayInMonth clamps to the shorter target month`() {
        val jan31 = at(2026, Calendar.JANUARY, 31)
        val februaryStart = MonthRange.monthStart(at(2026, Calendar.FEBRUARY, 1))

        assertEquals(28, dayOf(MonthRange.sameDayInMonth(februaryStart, jan31)))
    }

    @Test
    fun `sameDayInMonth keeps the day when the target month is long enough`() {
        val jan15 = at(2026, Calendar.JANUARY, 15)
        val februaryStart = MonthRange.monthStart(at(2026, Calendar.FEBRUARY, 1))

        assertEquals(15, dayOf(MonthRange.sameDayInMonth(februaryStart, jan15)))
    }

    @Test
    fun `dayStart zeroes the time of day`() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = MonthRange.dayStart(at(2026, Calendar.SEPTEMBER, 4, hour = 17, minute = 45))
        }

        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(4, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `month navigation moves one month in each direction`() {
        val septemberStart = MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 10))

        val october = Calendar.getInstance().apply { timeInMillis = MonthRange.nextMonthStart(septemberStart) }
        val august = Calendar.getInstance().apply { timeInMillis = MonthRange.previousMonthStart(septemberStart) }

        assertEquals(Calendar.OCTOBER, october.get(Calendar.MONTH))
        assertEquals(1, october.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.AUGUST, august.get(Calendar.MONTH))
    }

    @Test
    fun `label formats month and year`() {
        assertEquals("September 2026", MonthRange.label(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 4))))
    }

    @Test
    fun `monthsEndingAt returns count months oldest first, ending at the given month`() {
        val septemberStart = MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 15))

        val months = MonthRange.monthsEndingAt(septemberStart, 6)

        assertEquals(6, months.size)
        assertEquals(septemberStart, months.last())
        assertEquals(MonthRange.monthStart(at(2026, Calendar.APRIL, 1)), months.first())
    }

    @Test
    fun `monthsEndingAt crosses a year boundary`() {
        val februaryStart = MonthRange.monthStart(at(2027, Calendar.FEBRUARY, 3))

        val months = MonthRange.monthsEndingAt(februaryStart, 4)

        assertEquals(MonthRange.monthStart(at(2026, Calendar.NOVEMBER, 1)), months.first())
        assertEquals(februaryStart, months.last())
    }

    @Test
    fun `monthsEndingAt returns empty for a non-positive count`() {
        assertEquals(emptyList<Long>(), MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.MAY, 1)), 0))
    }
}
```

Then edit `app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonthTest.kt`: **delete** the five tests now living in `MonthRangeTest` (`sameDayInMonth clamps...`, `sameDayInMonth keeps...`, `dayStart zeroes...`, `month navigation...`, `label formats...`), keep the seven `cellsFor` tests, add `import com.example.dailyexpensetracker.ui.common.util.MonthRange`, and change every `CalendarMonth.monthStart(` in the remaining tests' setup to `MonthRange.monthStart(`.

Net test count after this task: 7 (`CalendarMonthTest`) + 8 (`MonthRangeTest`) = 15, up from 12, because `monthsEndingAt` is new.

- [ ] **Step 5: Save and wait for the watcher build**

```bash
P="C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
prev=$(tr -d '\357\273\277\r' < "$P/watch_build_status.txt")
for i in $(seq 1 48); do
  sleep 5
  cur=$(tr -d '\357\273\277\r' < "$P/watch_build_status.txt")
  if [ "$cur" != "$prev" ] && echo "$cur" | grep -qE "SUCCESS|FAILED"; then
    sleep 10
    again=$(tr -d '\357\273\277\r' < "$P/watch_build_status.txt")
    if [ "$again" = "$cur" ]; then echo "SETTLED: $cur"; break; fi
    prev="$cur"
  fi
done
```

Expected: `SUCCESS`. This only proves the main source set compiles; the moved tests are verified in Task 10.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/common/util/MonthRange.kt app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonth.kt app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarViewModel.kt app/src/test/java/com/example/dailyexpensetracker/ui/common/util/MonthRangeTest.kt app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonthTest.kt
git commit -m "Extract MonthRange from CalendarMonth for reuse by Reports

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Editable budgets — data layer, use case, tests

**Files:**
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/local/dao/CategoryDao.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/domain/repository/CategoryRepository.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/repository/CategoryRepositoryImpl.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/domain/usecase/UpdateCategoryBudgetUseCase.kt`
- Create: `app/src/test/java/com/example/dailyexpensetracker/domain/usecase/FakeCategoryRepository.kt`
- Create: `app/src/test/java/com/example/dailyexpensetracker/domain/usecase/UpdateCategoryBudgetUseCaseTest.kt`

**Interfaces:**
- Produces: `CategoryRepository.updateBudget(id: Long, limit: Double?)` (suspend) and `UpdateCategoryBudgetUseCase` as `suspend operator fun invoke(id: Long, limit: Double?)`. Task 7's ViewModel injects and calls it.

**No schema change.** `budgetLimit` is already a nullable column. Do not touch `AppDatabase`'s version.

- [ ] **Step 1: Write the failing test and its fake**

Create `app/src/test/java/com/example/dailyexpensetracker/domain/usecase/FakeCategoryRepository.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeCategoryRepository : CategoryRepository {
    private val state = MutableStateFlow<List<Category>>(emptyList())

    fun setCategories(categories: List<Category>) {
        state.value = categories
    }

    override fun getAll(): Flow<List<Category>> = state.asStateFlow()

    override fun getByType(isExpense: Boolean): Flow<List<Category>> =
        state.map { list -> list.filter { it.isExpense == isExpense } }

    override suspend fun updateBudget(id: Long, limit: Double?) {
        state.value = state.value.map { if (it.id == id) it.copy(budgetLimit = limit) else it }
    }
}
```

Create `app/src/test/java/com/example/dailyexpensetracker/domain/usecase/UpdateCategoryBudgetUseCaseTest.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCategoryBudgetUseCaseTest {

    private fun category(id: Long, budgetLimit: Double?) = Category(
        id = id,
        name = "Category $id",
        iconName = "grocery",
        colorHex = "#5A67F2",
        budgetLimit = budgetLimit,
        isExpense = true
    )

    @Test
    fun `sets a budget limit on the matching category`() = runBlocking {
        val repo = FakeCategoryRepository()
        repo.setCategories(listOf(category(1, null), category(2, 100.0)))
        val useCase = UpdateCategoryBudgetUseCase(repo)

        useCase(1L, 250.0)

        assertEquals(250.0, repo.getAll().first().first { it.id == 1L }.budgetLimit)
    }

    @Test
    fun `clearing a budget writes null`() = runBlocking {
        val repo = FakeCategoryRepository()
        repo.setCategories(listOf(category(1, 400.0)))
        val useCase = UpdateCategoryBudgetUseCase(repo)

        useCase(1L, null)

        assertNull(repo.getAll().first().first { it.id == 1L }.budgetLimit)
    }

    @Test
    fun `leaves other categories untouched`() = runBlocking {
        val repo = FakeCategoryRepository()
        repo.setCategories(listOf(category(1, 100.0), category(2, 200.0)))
        val useCase = UpdateCategoryBudgetUseCase(repo)

        useCase(1L, 999.0)

        assertEquals(200.0, repo.getAll().first().first { it.id == 2L }.budgetLimit)
    }
}
```

- [ ] **Step 2: Add the DAO query**

In `CategoryDao.kt`, add inside the interface:

```kotlin
    @Query("UPDATE categories SET budgetLimit = :limit WHERE id = :id")
    suspend fun updateBudget(id: Long, limit: Double?)
```

- [ ] **Step 3: Add the repository method**

In `CategoryRepository.kt`, add to the interface:

```kotlin
    suspend fun updateBudget(id: Long, limit: Double?)
```

In `CategoryRepositoryImpl.kt`, add to the class body:

```kotlin
    override suspend fun updateBudget(id: Long, limit: Double?) {
        dao.updateBudget(id, limit)
    }
```

- [ ] **Step 4: Create the use case**

Create `app/src/main/java/com/example/dailyexpensetracker/domain/usecase/UpdateCategoryBudgetUseCase.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.repository.CategoryRepository
import javax.inject.Inject

class UpdateCategoryBudgetUseCase @Inject constructor(
    private val repository: CategoryRepository
) {
    /** Pass a null [limit] to clear the budget, returning the category to "no budget set". */
    suspend operator fun invoke(id: Long, limit: Double?) = repository.updateBudget(id, limit)
}
```

- [ ] **Step 5: Save and wait for the watcher build**

Same polling as Task 1 Step 5. Expected: `SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/data/local/dao/CategoryDao.kt app/src/main/java/com/example/dailyexpensetracker/domain/repository/CategoryRepository.kt app/src/main/java/com/example/dailyexpensetracker/data/repository/CategoryRepositoryImpl.kt app/src/main/java/com/example/dailyexpensetracker/domain/usecase/UpdateCategoryBudgetUseCase.kt app/src/test/java/com/example/dailyexpensetracker/domain/usecase/FakeCategoryRepository.kt app/src/test/java/com/example/dailyexpensetracker/domain/usecase/UpdateCategoryBudgetUseCaseTest.kt
git commit -m "Add a write path for category budgets

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: DonutChartView and BarChartView

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/common/view/DonutChartView.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/common/view/BarChartView.kt`
- Create: `app/src/test/java/com/example/dailyexpensetracker/ui/common/view/ChartGeometryTest.kt`

**Interfaces:**
- Produces: `DonutChartView.setSegments(List<DonutChartView.Segment>)` where `Segment(value: Double, color: Int)`, and the pure `DonutChartView.sweepAngles(values: List<Double>): List<Float>`; `BarChartView.setBars(List<BarChartView.Bar>)` where `Bar(label: String, expense: Double, income: Double)`, and the pure `BarChartView.barHeights(values: List<Double>, maxHeightPx: Float): List<Float>`. Tasks 6 and 8 use these.

- [ ] **Step 1: Write the geometry tests**

Create `app/src/test/java/com/example/dailyexpensetracker/ui/common/view/ChartGeometryTest.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.common.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartGeometryTest {

    @Test
    fun `sweepAngles sum to a full circle`() {
        val angles = DonutChartView.sweepAngles(listOf(1.0, 2.0, 3.0, 4.0))

        assertEquals(360f, angles.sum(), 0.01f)
    }

    @Test
    fun `sweepAngles are proportional to their values`() {
        val angles = DonutChartView.sweepAngles(listOf(25.0, 75.0))

        assertEquals(90f, angles[0], 0.01f)
        assertEquals(270f, angles[1], 0.01f)
    }

    @Test
    fun `sweepAngles gives a single value the whole circle`() {
        val angles = DonutChartView.sweepAngles(listOf(42.0))

        assertEquals(1, angles.size)
        assertEquals(360f, angles[0], 0.01f)
    }

    @Test
    fun `sweepAngles returns empty for an empty list`() {
        assertTrue(DonutChartView.sweepAngles(emptyList()).isEmpty())
    }

    @Test
    fun `sweepAngles returns empty when every value is zero`() {
        assertTrue(DonutChartView.sweepAngles(listOf(0.0, 0.0)).isEmpty())
    }

    @Test
    fun `sweepAngles ignores negative values rather than producing negative arcs`() {
        val angles = DonutChartView.sweepAngles(listOf(-5.0, 100.0))

        assertEquals(0f, angles[0], 0.01f)
        assertEquals(360f, angles[1], 0.01f)
    }

    @Test
    fun `barHeights maps the largest value to the full height`() {
        val heights = BarChartView.barHeights(listOf(10.0, 20.0, 40.0), 200f)

        assertEquals(200f, heights[2], 0.01f)
        assertEquals(100f, heights[1], 0.01f)
        assertEquals(50f, heights[0], 0.01f)
    }

    @Test
    fun `barHeights returns zeros when every value is zero`() {
        val heights = BarChartView.barHeights(listOf(0.0, 0.0), 200f)

        assertEquals(listOf(0f, 0f), heights)
    }

    @Test
    fun `barHeights returns empty for an empty list`() {
        assertTrue(BarChartView.barHeights(emptyList(), 200f).isEmpty())
    }

    @Test
    fun `barHeights clamps negatives to zero`() {
        val heights = BarChartView.barHeights(listOf(-10.0, 50.0), 100f)

        assertEquals(0f, heights[0], 0.01f)
        assertEquals(100f, heights[1], 0.01f)
    }
}
```

- [ ] **Step 2: Create DonutChartView**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/common/view/DonutChartView.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * A ring of proportional arcs. Geometry lives in the companion's pure [sweepAngles] so it can be
 * unit-tested without instrumentation.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Segment(val value: Double, val color: Int)

    private var segments: List<Segment> = emptyList()

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val bounds = RectF()

    fun setSegments(list: List<Segment>) {
        segments = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sweeps = sweepAngles(segments.map { it.value })
        if (sweeps.isEmpty()) return

        val size = minOf(width, height).toFloat()
        val stroke = size * RING_THICKNESS
        arcPaint.strokeWidth = stroke

        val left = (width - size) / 2f + stroke / 2f
        val top = (height - size) / 2f + stroke / 2f
        bounds.set(left, top, left + size - stroke, top + size - stroke)

        var startAngle = -90f // 12 o'clock
        for (index in sweeps.indices) {
            arcPaint.color = segments[index].color
            canvas.drawArc(bounds, startAngle, sweeps[index], false, arcPaint)
            startAngle += sweeps[index]
        }
    }

    companion object {
        private const val RING_THICKNESS = 0.18f

        /**
         * Values converted to sweep angles totalling 360°. Negative values are treated as zero.
         * Returns an empty list when there is nothing to draw, so callers must not assume the
         * result is the same length as the input.
         */
        fun sweepAngles(values: List<Double>): List<Float> {
            val safe = values.map { if (it > 0.0) it else 0.0 }
            val total = safe.sum()
            if (total <= 0.0) return emptyList()
            return safe.map { (it / total * 360.0).toFloat() }
        }
    }
}
```

- [ ] **Step 3: Create BarChartView**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/common/view/BarChartView.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.dailyexpensetracker.R

/**
 * Paired expense/income bars per period. Geometry lives in the companion's pure [barHeights] so
 * it can be unit-tested without instrumentation.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(val label: String, val expense: Double, val income: Double)

    private var bars: List<Bar> = emptyList()

    private val expensePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.soft_red)
    }
    private val incomePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.soft_blue)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = resources.displayMetrics.density * 10f
        textAlign = Paint.Align.CENTER
    }
    private val textBounds = Rect()

    fun setBars(list: List<Bar>) {
        bars = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        labelPaint.getTextBounds("Sep", 0, 3, textBounds)
        val labelHeight = textBounds.height() + resources.displayMetrics.density * 6f
        val plotHeight = height - labelHeight
        if (plotHeight <= 0f) return

        // One shared scale across both series: normalising each to its own max would make a
        // small income bar look the same height as a large expense bar.
        val heights = barHeights(bars.flatMap { listOf(it.expense, it.income) }, plotHeight)

        val slotWidth = width.toFloat() / bars.size
        val barWidth = slotWidth * 0.28f
        val gap = slotWidth * 0.06f

        bars.forEachIndexed { index, bar ->
            val centerX = slotWidth * index + slotWidth / 2f
            val expenseHeight = heights[index * 2]
            val incomeHeight = heights[index * 2 + 1]

            canvas.drawRect(
                centerX - barWidth - gap / 2f, plotHeight - expenseHeight,
                centerX - gap / 2f, plotHeight, expensePaint
            )
            canvas.drawRect(
                centerX + gap / 2f, plotHeight - incomeHeight,
                centerX + barWidth + gap / 2f, plotHeight, incomePaint
            )
            canvas.drawText(bar.label, centerX, height.toFloat(), labelPaint)
        }
    }

    companion object {
        /**
         * Values scaled so the largest maps to [maxHeightPx]. Negatives clamp to zero, and an
         * all-zero input returns zeros rather than dividing by zero. Result is always the same
         * length as the input.
         */
        fun barHeights(values: List<Double>, maxHeightPx: Float): List<Float> {
            if (values.isEmpty()) return emptyList()
            val safe = values.map { if (it > 0.0) it else 0.0 }
            val max = safe.max()
            if (max <= 0.0) return safe.map { 0f }
            return safe.map { (it / max * maxHeightPx).toFloat() }
        }
    }
}
```

- [ ] **Step 4: Save and wait for the watcher build**

Same polling as Task 1 Step 5. Expected: `SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/common/view/DonutChartView.kt app/src/main/java/com/example/dailyexpensetracker/ui/common/view/BarChartView.kt app/src/test/java/com/example/dailyexpensetracker/ui/common/view/ChartGeometryTest.kt
git commit -m "Add hand-drawn donut and bar chart views

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Strings, icon, and the shared ReportsViewModel

**Files:**
- Create: `app/src/main/res/drawable/ic_reports_24.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/ReportsViewModel.kt`

**Interfaces:**
- Produces: `ReportsViewModel.selectedMonthStart: StateFlow<Long>`, `.previousMonth()`, `.nextMonth()`, `.selectedTab: Int`, `.selectTab(Int)`. Tasks 5–8 read it via `viewModels(ownerProducer = { requireParentFragment() })`.

- [ ] **Step 1: Create the nav icon**

```bash
D="C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker/app/src/main/res/drawable"
cat > "$D/ic_reports_24.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zM16.2,13H19v6h-2.8z" />
</vector>
EOF
echo created
```

- [ ] **Step 2: Add the Reports strings**

**Leave `nav_settings` in place** — `nav_graph.xml` and `bottom_nav_menu.xml` still reference it until Task 9, and removing it now would break resource linking and make this task unbuildable. Task 9 deletes it.

In `strings.xml`, add before `</resources>`:

```xml
    <string name="nav_reports">Reports</string>


```xml
    <string name="reports_tab_expense">Expenses</string>
    <string name="reports_tab_budget">Budget</string>
    <string name="reports_tab_billing">Billing</string>
    <string name="reports_previous_month">Previous month</string>
    <string name="reports_next_month">Next month</string>
    <string name="reports_no_expenses">No expenses this month</string>
    <string name="reports_no_budget_set">No budget set</string>
    <string name="reports_total_spent">Total spent</string>
    <string name="reports_spent_of_limit">%1$s of %2$s</string>
    <string name="reports_edit_budget_title">Set monthly budget</string>
    <string name="reports_edit_budget_hint">Amount (leave empty to clear)</string>
    <string name="reports_six_month_totals">Last 6 months</string>
    <string name="reports_legend_percent">%1$d%%</string>
    <string name="reports_save">Save</string>
    <string name="reports_cancel">Cancel</string>
```

Note `nav_settings` is deleted here, but `nav_graph.xml` still references it until Task 9. That is expected — this task's build will fail resource linking unless you also do Task 9's nav edit. **To keep every task independently buildable, leave `nav_settings` in place for now** and delete it in Task 9 instead. Add only the `nav_reports` and `reports_*` strings in this step.

- [ ] **Step 3: Create the shared ViewModel**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/ReportsViewModel.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Owns the month all three Reports tabs share. Child fragments obtain this same instance with
 * `viewModels(ownerProducer = { requireParentFragment() })`, so moving the month moves every tab.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor() : ViewModel() {

    private val _selectedMonthStart = MutableStateFlow(MonthRange.monthStart(System.currentTimeMillis()))
    val selectedMonthStart: StateFlow<Long> = _selectedMonthStart.asStateFlow()

    /**
     * Which tab is showing. Held here, not in the view, because this ViewModel outlives the
     * fragment's view — a recreated TabLayout defaults to index 0 while the restored child
     * fragment is whatever was showing before, and the two silently desync. Bills hit exactly
     * this and it broke a whole screen.
     */
    var selectedTab: Int = 0
        private set

    fun selectTab(position: Int) {
        selectedTab = position
    }

    fun previousMonth() {
        _selectedMonthStart.value = MonthRange.previousMonthStart(_selectedMonthStart.value)
    }

    fun nextMonth() {
        _selectedMonthStart.value = MonthRange.nextMonthStart(_selectedMonthStart.value)
    }
}
```

- [ ] **Step 4: Save and wait for the watcher build**

Same polling as Task 1 Step 5. Expected: `SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_reports_24.xml app/src/main/res/values/strings.xml app/src/main/java/com/example/dailyexpensetracker/ui/reports/ReportsViewModel.kt
git commit -m "Add Reports strings, nav icon, and shared month ViewModel

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Expense Chart tab

**Files:**
- Create: `app/src/main/res/layout/item_legend.xml`
- Create: `app/src/main/res/layout/fragment_expense_chart.xml`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/expense/adapter/LegendAdapter.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/expense/ExpenseChartViewModel.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/expense/ExpenseChartFragment.kt`

**Interfaces:**
- Consumes: `ReportsViewModel` (Task 4), `DonutChartView` (Task 3), existing `GetBudgetSummaryUseCase`, `MonthRange` (Task 1).
- Produces: `ExpenseChartFragment` — instantiated by class in Task 9's tab host.

- [ ] **Step 1: Create the legend row layout**

Create `app/src/main/res/layout/item_legend.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="20dp"
    android:paddingTop="8dp"
    android:paddingEnd="20dp"
    android:paddingBottom="8dp">

    <View
        android:id="@+id/vSwatch"
        android:layout_width="12dp"
        android:layout_height="12dp"
        android:background="@drawable/bg_circle" />

    <TextView
        android:id="@+id/tvName"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_weight="1"
        android:textColor="@color/text_primary"
        android:textSize="14sp"
        tools:text="Medicine" />

    <TextView
        android:id="@+id/tvPercent"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="12dp"
        android:textColor="@color/text_secondary"
        android:textSize="13sp"
        tools:text="34%" />

    <TextView
        android:id="@+id/tvAmount"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/text_primary"
        android:textSize="14sp"
        android:textStyle="bold"
        tools:text="$3,930.65" />

</LinearLayout>
```

- [ ] **Step 2: Create the tab layout**

Create `app/src/main/res/layout/fragment_expense_chart.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="220dp"
            android:layout_marginTop="16dp">

            <com.example.dailyexpensetracker.ui.common.view.DonutChartView
                android:id="@+id/donut"
                android:layout_width="match_parent"
                android:layout_height="match_parent" />

            <LinearLayout
                android:id="@+id/centreLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:gravity="center"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/reports_total_spent"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp" />

                <TextView
                    android:id="@+id/tvTotal"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/text_primary"
                    android:textSize="20sp"
                    android:textStyle="bold"
                    tools:text="$11,510.65" />
            </LinearLayout>
        </FrameLayout>

        <TextView
            android:id="@+id/tvEmpty"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:gravity="center"
            android:text="@string/reports_no_expenses"
            android:textColor="@color/text_secondary"
            android:textSize="13sp"
            android:visibility="gone"
            tools:visibility="visible" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvLegend"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_marginTop="8dp"
            android:layout_weight="1"
            android:clipToPadding="false"
            android:paddingBottom="24dp"
            tools:listitem="@layout/item_legend" />

    </LinearLayout>
</layout>
```

- [ ] **Step 3: Create the legend adapter**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/expense/adapter/LegendAdapter.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.expense.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.databinding.ItemLegendBinding
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter

/** [total] is passed in so each row can show its share without recomputing the sum. */
class LegendAdapter : RecyclerView.Adapter<LegendAdapter.ViewHolder>() {

    private var items: List<CategorySpend> = emptyList()
    private var total: Double = 0.0

    fun submitList(list: List<CategorySpend>, total: Double) {
        items = list
        this.total = total
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLegendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], total)

    class ViewHolder(private val binding: ItemLegendBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategorySpend, total: Double) {
            binding.tvName.text = item.category.name
            binding.tvAmount.text = CurrencyFormatter.format(item.spent)
            val percent = if (total > 0.0) (item.spent / total * 100).toInt() else 0
            binding.tvPercent.text = "$percent%"
            binding.vSwatch.backgroundTintList = ColorStateList.valueOf(parseColor(item.category.colorHex))
        }

        private fun parseColor(hex: String): Int = try {
            Color.parseColor(hex)
        } catch (e: IllegalArgumentException) {
            Color.GRAY // seed data is well-formed, but a bad hex must not crash the screen
        }
    }
}
```

- [ ] **Step 4: Create the ViewModel**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/expense/ExpenseChartViewModel.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.expense

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.domain.model.BudgetSummary
import com.example.dailyexpensetracker.domain.usecase.GetBudgetSummaryUseCase
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class ExpenseChartViewModel @Inject constructor(
    private val getBudgetSummary: GetBudgetSummaryUseCase
) : ViewModel() {

    /** The month is owned by ReportsViewModel, so the fragment passes it in rather than holding it. */
    fun summaryFor(monthStart: Long): Flow<BudgetSummary> =
        getBudgetSummary(monthStart, MonthRange.nextMonthStart(monthStart) - 1)
}
```

- [ ] **Step 5: Create the fragment**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/expense/ExpenseChartFragment.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.expense

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.databinding.FragmentExpenseChartBinding
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.common.view.DonutChartView
import com.example.dailyexpensetracker.ui.reports.ReportsViewModel
import com.example.dailyexpensetracker.ui.reports.expense.adapter.LegendAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class ExpenseChartFragment : Fragment() {

    private var _binding: FragmentExpenseChartBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpenseChartViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private val legendAdapter = LegendAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvLegend.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLegend.adapter = legendAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                reportsViewModel.selectedMonthStart
                    .flatMapLatest { viewModel.summaryFor(it) }
                    .collect { summary ->
                        val breakdown = summary.categoryBreakdown
                        val isEmpty = breakdown.isEmpty()

                        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        binding.centreLabel.visibility = if (isEmpty) View.GONE else View.VISIBLE
                        binding.tvTotal.text = CurrencyFormatter.format(summary.totalSpent)

                        binding.donut.setSegments(
                            breakdown.map {
                                DonutChartView.Segment(it.spent, parseColor(it.category.colorHex))
                            }
                        )
                        legendAdapter.submitList(breakdown, summary.totalSpent)
                    }
            }
        }
    }

    private fun parseColor(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (e: IllegalArgumentException) {
        Color.GRAY
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 6: Save and wait for the watcher build**

Same polling as Task 1 Step 5. Expected: `SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/item_legend.xml app/src/main/res/layout/fragment_expense_chart.xml app/src/main/java/com/example/dailyexpensetracker/ui/reports/expense/
git commit -m "Add Expense Chart tab

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Budget Planner tab

**Files:**
- Create: `app/src/main/res/layout/item_budget_plan.xml`
- Create: `app/src/main/res/layout/dialog_edit_budget.xml`
- Create: `app/src/main/res/layout/fragment_budget_planner.xml`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/budget/adapter/BudgetPlanAdapter.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/budget/BudgetPlannerViewModel.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/budget/BudgetPlannerFragment.kt`

**Interfaces:**
- Consumes: `ReportsViewModel` (Task 4), `UpdateCategoryBudgetUseCase` (Task 2), existing `GetBudgetSummaryUseCase`/`GetCategoriesUseCase`.
- Produces: `BudgetPlannerFragment` — instantiated by class in Task 9's tab host.

Lists **all** expense categories, including those with zero spend — the point of a planner is setting a limit on something you have not spent on yet. `BudgetSummary.categoryBreakdown` filters to `spent > 0`, so this tab combines categories and transactions itself rather than using it.

- [ ] **Step 1: Create the row layout**

Create `app/src/main/res/layout/item_budget_plan.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingStart="20dp"
    android:paddingTop="12dp"
    android:paddingEnd="20dp"
    android:paddingBottom="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <FrameLayout
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:background="@drawable/bg_circle">

            <ImageView
                android:id="@+id/ivIcon"
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:layout_gravity="center"
                android:contentDescription="@null"
                app:tint="@color/white"
                tools:src="@drawable/ic_cat_medicine_24" />
        </FrameLayout>

        <TextView
            android:id="@+id/tvName"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:textColor="@color/text_primary"
            android:textSize="14sp"
            android:textStyle="bold"
            tools:text="Medicine" />

        <TextView
            android:id="@+id/tvAmounts"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/text_secondary"
            android:textSize="12sp"
            tools:text="$3,930.65 of $400.00" />
    </LinearLayout>

    <com.google.android.material.progressindicator.LinearProgressIndicator
        android:id="@+id/progressBudget"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        app:trackColor="@color/background_light_gray"
        app:trackCornerRadius="4dp"
        tools:progress="70" />

</LinearLayout>
```

- [ ] **Step 2: Create the edit dialog layout**

Create `app/src/main/res/layout/dialog_edit_budget.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingStart="24dp"
    android:paddingTop="8dp"
    android:paddingEnd="24dp">

    <EditText
        android:id="@+id/etBudget"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/reports_edit_budget_hint"
        android:importantForAutofill="no"
        android:inputType="numberDecimal"
        android:maxLines="1" />

</LinearLayout>
```

- [ ] **Step 3: Create the tab layout**

Create `app/src/main/res/layout/fragment_budget_planner.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvBudgets"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingTop="8dp"
        android:paddingBottom="24dp"
        tools:listitem="@layout/item_budget_plan" />
</layout>
```

- [ ] **Step 4: Create the adapter**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/budget/adapter/BudgetPlanAdapter.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.budget.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.ItemBudgetPlanBinding
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter

class BudgetPlanAdapter(
    private val onClick: (CategorySpend) -> Unit
) : RecyclerView.Adapter<BudgetPlanAdapter.ViewHolder>() {

    private var items: List<CategorySpend> = emptyList()

    fun submitList(list: List<CategorySpend>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBudgetPlanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class ViewHolder(private val binding: ItemBudgetPlanBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategorySpend) {
            val context = binding.root.context
            binding.tvName.text = item.category.name
            binding.ivIcon.setImageResource(CategoryIconMapper.iconFor(item.category.iconName))

            val limit = item.category.budgetLimit
            if (limit == null || limit <= 0.0) {
                binding.tvAmounts.text = context.getString(R.string.reports_no_budget_set)
                binding.progressBudget.progress = 0
                tint(context, R.color.text_secondary)
                return
            }

            binding.tvAmounts.text = context.getString(
                R.string.reports_spent_of_limit,
                CurrencyFormatter.format(item.spent),
                CurrencyFormatter.format(limit)
            )
            binding.progressBudget.progress = (item.spent / limit * 100).coerceIn(0.0, 100.0).toInt()
            tint(context, if (item.spent >= limit) R.color.soft_red else R.color.purple_primary)
        }

        private fun tint(context: android.content.Context, colorRes: Int) {
            // setIndicatorColor/setTrackColor are LinearProgressIndicator's documented API;
            // do not reach for trackTintList, which it does not expose.
            binding.progressBudget.setIndicatorColor(ContextCompat.getColor(context, colorRes))
            binding.progressBudget.trackColor = ContextCompat.getColor(context, R.color.background_light_gray)
        }
    }
}
```

- [ ] **Step 5: Create the ViewModel**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/budget/BudgetPlannerViewModel.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.budget

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.domain.usecase.UpdateCategoryBudgetUseCase
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class BudgetPlannerViewModel @Inject constructor(
    private val getCategories: GetCategoriesUseCase,
    private val getTransactionsForPeriod: GetTransactionsForPeriodUseCase,
    private val updateCategoryBudget: UpdateCategoryBudgetUseCase
) : ViewModel() {

    /**
     * Every expense category with its spend for the month — including categories with zero spend,
     * which is why this doesn't reuse BudgetSummary.categoryBreakdown (that filters to spent > 0).
     */
    fun plansFor(monthStart: Long): Flow<List<CategorySpend>> = combine(
        getCategories(isExpense = true),
        getTransactionsForPeriod(monthStart, MonthRange.nextMonthStart(monthStart) - 1)
    ) { categories, transactions ->
        categories.map { category ->
            val spent = transactions
                .filter { it.categoryId == category.id && it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
            CategorySpend(category, spent)
        }
    }

    suspend fun setBudget(categoryId: Long, limit: Double?) = updateCategoryBudget(categoryId, limit)
}
```

- [ ] **Step 6: Create the fragment**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/budget/BudgetPlannerFragment.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.budget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.DialogEditBudgetBinding
import com.example.dailyexpensetracker.databinding.FragmentBudgetPlannerBinding
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.ui.reports.ReportsViewModel
import com.example.dailyexpensetracker.ui.reports.budget.adapter.BudgetPlanAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class BudgetPlannerFragment : Fragment() {

    private var _binding: FragmentBudgetPlannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BudgetPlannerViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private val adapter = BudgetPlanAdapter { showEditDialog(it) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetPlannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvBudgets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBudgets.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                reportsViewModel.selectedMonthStart
                    .flatMapLatest { viewModel.plansFor(it) }
                    .collect { adapter.submitList(it) }
            }
        }
    }

    private fun showEditDialog(item: CategorySpend) {
        val dialogBinding = DialogEditBudgetBinding.inflate(layoutInflater)
        dialogBinding.etBudget.setText(item.category.budgetLimit?.toString().orEmpty())

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.reports_edit_budget_title))
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.reports_cancel, null)
            .setPositiveButton(R.string.reports_save) { _, _ ->
                // Empty or unparseable input clears the budget rather than writing a bogus number.
                val limit = dialogBinding.etBudget.text?.toString()?.trim()?.toDoubleOrNull()
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.setBudget(item.category.id, limit)
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

- [ ] **Step 7: Save and wait for the watcher build**

Same polling as Task 1 Step 5. Expected: `SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/layout/item_budget_plan.xml app/src/main/res/layout/dialog_edit_budget.xml app/src/main/res/layout/fragment_budget_planner.xml app/src/main/java/com/example/dailyexpensetracker/ui/reports/budget/
git commit -m "Add Budget Planner tab with editable budgets

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Billing Reports tab

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/billing/MonthlyTotals.kt`
- Create: `app/src/test/java/com/example/dailyexpensetracker/ui/reports/billing/MonthlyTotalsTest.kt`
- Create: `app/src/main/res/layout/fragment_billing_reports.xml`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/billing/BillingReportsViewModel.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/billing/BillingReportsFragment.kt`

**Interfaces:**
- Consumes: `ReportsViewModel` (Task 4), `BarChartView` (Task 3), `MonthRange` (Task 1), existing `GetTransactionsForPeriodUseCase`.
- Produces: `BillingReportsFragment` — instantiated by class in Task 9's tab host; `MonthlyTotals.bucket(monthStarts, transactions): List<MonthTotal>`.

- [ ] **Step 1: Write the bucketing tests**

Create `app/src/test/java/com/example/dailyexpensetracker/ui/reports/billing/MonthlyTotalsTest.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.billing

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MonthlyTotalsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun tx(dateMillis: Long, amount: Double, type: TransactionType) = Transaction(
        id = 0,
        title = "t",
        amount = amount,
        date = dateMillis,
        categoryId = 1L,
        cardId = null,
        type = type,
        isScheduled = false,
        status = TransactionStatus.PAID
    )

    @Test
    fun `buckets transactions into their own month`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 3)

        val totals = MonthlyTotals.bucket(
            months,
            listOf(
                tx(at(2026, Calendar.JULY, 5), 100.0, TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 9), 50.0, TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 20), 25.0, TransactionType.EXPENSE)
            )
        )

        assertEquals(3, totals.size)
        assertEquals(100.0, totals[0].expense, 0.001)
        assertEquals(0.0, totals[1].expense, 0.001)
        assertEquals(75.0, totals[2].expense, 0.001)
    }

    @Test
    fun `separates expense from income`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 1)

        val totals = MonthlyTotals.bucket(
            months,
            listOf(
                tx(at(2026, Calendar.SEPTEMBER, 3), 200.0, TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 4), 900.0, TransactionType.INCOME)
            )
        )

        assertEquals(200.0, totals[0].expense, 0.001)
        assertEquals(900.0, totals[0].income, 0.001)
    }

    @Test
    fun `keeps empty months as zero rather than dropping them`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 6)

        val totals = MonthlyTotals.bucket(months, emptyList())

        assertEquals(6, totals.size)
        assertEquals(0.0, totals.sumOf { it.expense }, 0.001)
    }

    @Test
    fun `buckets correctly across a year boundary`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2027, Calendar.JANUARY, 1)), 3)

        val totals = MonthlyTotals.bucket(
            months,
            listOf(
                tx(at(2026, Calendar.NOVEMBER, 10), 10.0, TransactionType.EXPENSE),
                tx(at(2027, Calendar.JANUARY, 2), 30.0, TransactionType.EXPENSE)
            )
        )

        assertEquals(10.0, totals[0].expense, 0.001)
        assertEquals(0.0, totals[1].expense, 0.001)
        assertEquals(30.0, totals[2].expense, 0.001)
    }

    @Test
    fun `ignores transactions outside the requested months`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 2)

        val totals = MonthlyTotals.bucket(
            months,
            listOf(tx(at(2026, Calendar.JANUARY, 5), 500.0, TransactionType.EXPENSE))
        )

        assertEquals(0.0, totals.sumOf { it.expense }, 0.001)
    }

    @Test
    fun `labels each month with a short name`() {
        val months = MonthRange.monthsEndingAt(MonthRange.monthStart(at(2026, Calendar.SEPTEMBER, 1)), 1)

        assertEquals("Sep", MonthlyTotals.bucket(months, emptyList())[0].label)
    }
}
```

- [ ] **Step 2: Create the bucketing logic**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/billing/MonthlyTotals.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.billing

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonthTotal(
    val monthStartMillis: Long,
    val label: String,
    val expense: Double,
    val income: Double
)

/**
 * Groups transactions into a fixed list of months. Pure, so it unit-tests on the JVM.
 *
 * Months with no transactions are kept as zeroes rather than dropped — the bar chart needs a
 * slot for every month or the x-axis silently lies about the period.
 */
object MonthlyTotals {

    private val shortMonthFormat = SimpleDateFormat("MMM", Locale.US)

    fun bucket(monthStarts: List<Long>, transactions: List<Transaction>): List<MonthTotal> {
        val expenseByMonth = HashMap<Long, Double>()
        val incomeByMonth = HashMap<Long, Double>()

        for (transaction in transactions) {
            val month = MonthRange.monthStart(transaction.date)
            if (transaction.type == TransactionType.EXPENSE) {
                expenseByMonth[month] = (expenseByMonth[month] ?: 0.0) + transaction.amount
            } else {
                incomeByMonth[month] = (incomeByMonth[month] ?: 0.0) + transaction.amount
            }
        }

        return monthStarts.map { monthStart ->
            MonthTotal(
                monthStartMillis = monthStart,
                label = shortMonthFormat.format(Date(monthStart)),
                expense = expenseByMonth[monthStart] ?: 0.0,
                income = incomeByMonth[monthStart] ?: 0.0
            )
        }
    }
}
```

- [ ] **Step 3: Create the tab layout**

Create `app/src/main/res/layout/fragment_billing_reports.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="20dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/reports_six_month_totals"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold" />

        <com.example.dailyexpensetracker.ui.common.view.BarChartView
            android:id="@+id/barChart"
            android:layout_width="match_parent"
            android:layout_height="220dp"
            android:layout_marginTop="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:orientation="horizontal">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/home_spend"
                    android:textColor="@color/soft_red"
                    android:textSize="13sp" />

                <TextView
                    android:id="@+id/tvTotalSpent"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/text_primary"
                    android:textSize="18sp"
                    android:textStyle="bold"
                    tools:text="$32,010.65" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/home_earned"
                    android:textColor="@color/soft_blue"
                    android:textSize="13sp" />

                <TextView
                    android:id="@+id/tvTotalEarned"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/text_primary"
                    android:textSize="18sp"
                    android:textStyle="bold"
                    tools:text="$63,000.00" />
            </LinearLayout>
        </LinearLayout>

    </LinearLayout>
</layout>
```

- [ ] **Step 4: Create the ViewModel**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/billing/BillingReportsViewModel.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.billing

import androidx.lifecycle.ViewModel
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class BillingReportsViewModel @Inject constructor(
    private val getTransactionsForPeriod: GetTransactionsForPeriodUseCase
) : ViewModel() {

    /**
     * The six months ending at [monthStart], fetched as a **single** range query and bucketed in
     * memory — six separate per-month flows would be six Room queries for the same data.
     */
    fun totalsFor(monthStart: Long): Flow<List<MonthTotal>> {
        val months = MonthRange.monthsEndingAt(monthStart, MONTHS_SHOWN)
        val rangeStart = months.first()
        val rangeEnd = MonthRange.nextMonthStart(months.last()) - 1
        return getTransactionsForPeriod(rangeStart, rangeEnd).map { MonthlyTotals.bucket(months, it) }
    }

    companion object {
        const val MONTHS_SHOWN = 6
    }
}
```

- [ ] **Step 5: Create the fragment**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/billing/BillingReportsFragment.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports.billing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dailyexpensetracker.databinding.FragmentBillingReportsBinding
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.common.view.BarChartView
import com.example.dailyexpensetracker.ui.reports.ReportsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class BillingReportsFragment : Fragment() {

    private var _binding: FragmentBillingReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillingReportsViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillingReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                reportsViewModel.selectedMonthStart
                    .flatMapLatest { viewModel.totalsFor(it) }
                    .collect { totals ->
                        binding.barChart.setBars(
                            totals.map { BarChartView.Bar(it.label, it.expense, it.income) }
                        )
                        binding.tvTotalSpent.text = CurrencyFormatter.format(totals.sumOf { it.expense })
                        binding.tvTotalEarned.text = CurrencyFormatter.format(totals.sumOf { it.income })
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 6: Save and wait for the watcher build**

Same polling as Task 1 Step 5. Expected: `SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/fragment_billing_reports.xml app/src/main/java/com/example/dailyexpensetracker/ui/reports/billing/ app/src/test/java/com/example/dailyexpensetracker/ui/reports/billing/
git commit -m "Add Billing Reports tab with six-month totals

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Reports host screen

**Files:**
- Create: `app/src/main/res/layout/fragment_reports.xml`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/reports/ReportsFragment.kt`

**Interfaces:**
- Consumes: `ReportsViewModel` (Task 4) and all three tab fragments (Tasks 5–7).
- Produces: `ReportsFragment` — referenced by class name in Task 9's `nav_graph.xml`.

- [ ] **Step 1: Create the host layout**

Create `app/src/main/res/layout/fragment_reports.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/background_light_gray"
        android:orientation="vertical">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@color/purple_primary"
            android:orientation="vertical"
            android:paddingStart="20dp"
            android:paddingTop="20dp"
            android:paddingEnd="20dp"
            android:paddingBottom="8dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/nav_reports"
                android:textColor="@color/white"
                android:textSize="20sp"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageView
                    android:id="@+id/ivPrevMonth"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:contentDescription="@string/reports_previous_month"
                    android:src="@drawable/ic_back_24"
                    app:tint="@color/white" />

                <TextView
                    android:id="@+id/tvMonthLabel"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:gravity="center"
                    android:textColor="@color/white"
                    android:textSize="15sp"
                    android:textStyle="bold"
                    tools:text="September 2026" />

                <ImageView
                    android:id="@+id/ivNextMonth"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:contentDescription="@string/reports_next_month"
                    android:rotation="180"
                    android:src="@drawable/ic_back_24"
                    app:tint="@color/white" />
            </LinearLayout>

            <com.google.android.material.tabs.TabLayout
                android:id="@+id/tabLayout"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:background="@android:color/transparent"
                app:tabIndicatorColor="@color/white"
                app:tabSelectedTextColor="@color/white"
                app:tabTextColor="@color/purple_accent_light">

                <com.google.android.material.tabs.TabItem
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/reports_tab_expense" />

                <com.google.android.material.tabs.TabItem
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/reports_tab_budget" />

                <com.google.android.material.tabs.TabItem
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/reports_tab_billing" />
            </com.google.android.material.tabs.TabLayout>
        </LinearLayout>

        <FrameLayout
            android:id="@+id/tabContainer"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1" />

    </LinearLayout>
</layout>
```

Note the `android:background="@android:color/transparent"` on the `TabLayout` — without it Material3 paints its own surface and the selected tab's white label becomes invisible on the purple header. That cost a fix in Bills.

- [ ] **Step 2: Create the host fragment**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/reports/ReportsFragment.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dailyexpensetracker.databinding.FragmentReportsBinding
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import com.example.dailyexpensetracker.ui.reports.billing.BillingReportsFragment
import com.example.dailyexpensetracker.ui.reports.budget.BudgetPlannerFragment
import com.example.dailyexpensetracker.ui.reports.expense.ExpenseChartFragment
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReportsViewModel by viewModels()

    /** Tab order must match the TabItem order in fragment_reports.xml. */
    private val tabFragments: List<() -> Fragment> = listOf(
        { ExpenseChartFragment() },
        { BudgetPlannerFragment() },
        { BillingReportsFragment() }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.ivNextMonth.setOnClickListener { viewModel.nextMonth() }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(tab.position)
                showTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // Restore both the strip and the content from the ViewModel, which outlives this view.
        // select() fires the listener (which installs the fragment), but it is a no-op when the
        // tab is already selected — so index 0 has to be installed by hand. Exactly one of these
        // branches installs the fragment; `replace()` also removes any child the FragmentManager
        // restored, so nothing is duplicated.
        val restoredTab = viewModel.selectedTab
        if (binding.tabLayout.selectedTabPosition != restoredTab) {
            binding.tabLayout.getTabAt(restoredTab)?.select()
        } else {
            showTab(restoredTab)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedMonthStart.collect { monthStart ->
                    binding.tvMonthLabel.text = MonthRange.label(monthStart)
                }
            }
        }
    }

    private fun showTab(position: Int) {
        childFragmentManager.beginTransaction()
            .replace(binding.tabContainer.id, tabFragments[position]())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 3: Save and wait for the watcher build**

Same polling as Task 1 Step 5. Expected: `SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_reports.xml app/src/main/java/com/example/dailyexpensetracker/ui/reports/ReportsFragment.kt
git commit -m "Add Reports tab host

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 9: Navigation wiring, placeholder removal, and full verification

**Files:**
- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Modify: `app/src/main/res/menu/bottom_nav_menu.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Delete: `app/src/main/java/com/example/dailyexpensetracker/ui/placeholder/PlaceholderFragment.kt`
- Delete: `app/src/main/res/layout/fragment_placeholder.xml`

- [ ] **Step 1: Swap the nav destination**

In `nav_graph.xml`, replace:

```xml
    <fragment
        android:id="@+id/placeholderSettingsFragment"
        android:name="com.example.dailyexpensetracker.ui.placeholder.PlaceholderFragment"
        android:label="@string/nav_settings">
        <argument
            android:name="arg_title"
            app:argType="string"
            android:defaultValue="Coming soon in the next update" />
    </fragment>
```

with:

```xml
    <fragment
        android:id="@+id/reportsFragment"
        android:name="com.example.dailyexpensetracker.ui.reports.ReportsFragment"
        android:label="@string/nav_reports" />
```

- [ ] **Step 2: Swap the bottom nav item**

In `bottom_nav_menu.xml`, replace:

```xml
    <item
        android:id="@+id/placeholderSettingsFragment"
        android:icon="@drawable/ic_settings_24"
        android:title="@string/nav_settings" />
```

with:

```xml
    <item
        android:id="@+id/reportsFragment"
        android:icon="@drawable/ic_reports_24"
        android:title="@string/nav_reports" />
```

- [ ] **Step 3: Delete the now-orphaned placeholder and its string**

`placeholderSettingsFragment` was `PlaceholderFragment`'s last consumer (Bills removed the Graph one), so all of it goes:

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
git rm -q app/src/main/java/com/example/dailyexpensetracker/ui/placeholder/PlaceholderFragment.kt app/src/main/res/layout/fragment_placeholder.xml
grep -rn "PlaceholderFragment\|fragment_placeholder\|nav_settings\|ic_settings_24" app/src/main || echo "no references remain"
```

Then delete the `<string name="nav_settings">Settings</string>` line from `strings.xml`. Expect the grep to also flag `ic_settings_24` as now-unused — delete `app/src/main/res/drawable/ic_settings_24.xml` too, matching how Bills removed the orphaned `ic_bar_chart_24`.

- [ ] **Step 4: Save and wait for the watcher build**

Same polling as Task 1 Step 5. Expected: `SUCCESS`. If it fails on a missing resource, a reference to `nav_settings` or `fragment_placeholder` survived somewhere — re-run the grep from Step 3.

- [ ] **Step 5: Relaunch the app**

```bash
export MSYS_NO_PATHCONV=1
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" logcat -c
"$ADB" shell am force-stop com.example.dailyexpensetracker
"$ADB" shell am start -n com.example.dailyexpensetracker/.ui.main.MainActivity
"$ADB" shell sleep 3
```

Do **not** clear app data — no schema change, and the seeded transactions are what the charts render. Clearing it would also discard any budget edits made while testing.

- [ ] **Step 6: Open Reports and verify the Expense Chart tab**

Set `S` to your session's scratchpad directory (never the repo root). Find the Reports nav item and tap it:

```bash
export MSYS_NO_PATHCONV=1
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" shell uiautomator dump /sdcard/d.xml >/dev/null
"$ADB" pull /sdcard/d.xml "$S/d.xml" >/dev/null
grep -oE 'resource-id="com.example.dailyexpensetracker:id/reportsFragment"[^>]*bounds="[^"]*"' "$S/d.xml"
```

Tap its centre, screenshot, and check logcat:

```bash
"$ADB" shell input tap <cx> <cy>
"$ADB" shell sleep 2
"$ADB" shell screencap -p /sdcard/r.png
"$ADB" pull /sdcard/r.png "$S/reports.png" >/dev/null
"$ADB" logcat -d -t 300 | grep -A 10 "FATAL EXCEPTION" || echo "no fatal"
```

Expected in `reports.png`: purple header with "Reports", a "‹ September 2026 ›" row, three legible tabs with "Expenses" selected, a donut ring of coloured segments with the month's total in the centre, and a legend listing each category with its percentage and amount. **Check the selected tab label is readable** — that is the Bills failure this layout guards against.

- [ ] **Step 7: Verify the Budget tab and editing a budget**

Tap the "Budget" tab, screenshot. Expected: every expense category listed — including ones with no spend this month — with progress bars, over-budget bars in red, and "No budget set" on any category whose limit is null.

Then tap a category row, type a new limit in the dialog, tap Save, and screenshot. Expected: that row's numbers and bar update immediately without leaving the screen (Room's Flow drives it). Then reopen the same row, clear the field, save, and confirm the row falls back to "No budget set".

- [ ] **Step 8: Verify the Billing tab**

Tap "Billing", screenshot. Expected: six labelled bars (`Apr`…`Sep` for a September selection), with the seeded month's bars clearly taller than the empty ones, and the two six-month totals beneath. Empty months must still appear as labelled slots with no bar — not be missing.

- [ ] **Step 9: Verify the month selector drives all three tabs**

Tap `ivPrevMonth`, then check each tab in turn. Expected: the label moves back a month; Expenses shows the empty state ("No expenses this month") for a month with no data; Budget shows every category at zero spend with limits intact; Billing's six-month window shifts back by one, changing which bars are populated. Screenshot each, and check `logcat` for `FATAL EXCEPTION` after each tab switch.

- [ ] **Step 10: Verify the other four tabs still work**

Tap through Home, Bills, Wallet and Categories. Expected: all render, no crash. Settings is gone from the nav — confirm the bar now reads Home / Bills / Wallet / Category / Reports.

```bash
"$ADB" logcat -d -t 400 | grep -A 10 "FATAL EXCEPTION" || echo "no fatal"
```

- [ ] **Step 11: Clean up scratch files**

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
ls *.png dump*.xml 2>/dev/null || echo clean
```

- [ ] **Step 12: Commit**

```bash
git add app/src/main/res/navigation/nav_graph.xml app/src/main/res/menu/bottom_nav_menu.xml app/src/main/res/values/strings.xml app/src/main/java/com/example/dailyexpensetracker/ui/placeholder app/src/main/res/layout/fragment_placeholder.xml app/src/main/res/drawable/ic_settings_24.xml
git commit -m "Wire Reports into navigation, removing the Settings placeholder

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 10: Final checks

**Files:** none (verification only)

- [ ] **Step 1: Ask the user to run tests and lint**

The only point in this plan needing the user's terminal, and the first time any of this sub-project's tests are compiled or run. Tell them **not** to stop the watcher — a second terminal avoids the gap.

```bash
./gradlew testDebugUnitTest lintDebug --console=plain > verify_output.txt 2>&1
```

- [ ] **Step 2: Read the reports yourself**

Do not ask for output to be pasted, and do not treat `UP-TO-DATE` as evidence a test ran — check the report timestamps.

```bash
P="C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
grep -ho '<testsuite [^>]*' "$P/app/build/test-results/testDebugUnitTest/"*.xml \
  | sed -E 's/.*name="([^"]*)".*tests="([^"]*)".*failures="([^"]*)".*errors="([^"]*)".*/\1: tests=\2 failures=\3 errors=\4/'
tail -3 "$P/app/build/reports/lint-results-debug.txt"
```

Expected suites and counts:

| Suite | Tests |
|---|---|
| `MonthRangeTest` | 8 |
| `CalendarMonthTest` | 7 |
| `ChartGeometryTest` | 10 |
| `MonthlyTotalsTest` | 6 |
| `UpdateCategoryBudgetUseCaseTest` | 3 |
| `TransactionUseCasesTest` | 5 |
| `ExampleUnitTest` | 1 |
| **Total** | **40** |

All with `failures=0 errors=0`. Lint: **0 errors**. Warnings will rise above the current 47 — new `NotifyDataSetChanged` and `SetTextI18n` instances are consistent with the existing code and are not to be "fixed". Fix new *errors* only, and leave `GradleDependency`/`NewerVersionAvailable` alone since those versions are deliberately pinned.

- [ ] **Step 3: Clean up and open the PR**

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
rm -f verify_output.txt
git status
```

Confirm a clean tree, then — **after explicit user confirmation, per the branch policy** — push `feature/reports` and open a PR against `master`.

---

## Explicitly Out of Scope (carried from the spec)

Exporting, sharing or printing reports; custom date ranges beyond the month selector and the fixed six-month window; per-category budget history or trends; budgets on income categories; chart animations, touch interaction, or tap-to-drill-down; a real Settings screen to replace the nav slot Reports takes.
