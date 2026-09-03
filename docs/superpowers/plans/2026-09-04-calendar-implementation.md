# Calendar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Calendar screen — a month grid with per-day spend markers, where tapping a day lists that day's transactions — reached from a new calendar action in Home's header.

**Architecture:** UI-layer only. All month arithmetic and grid building lives in one pure `CalendarMonth` object that unit-tests on the JVM; a `CalendarViewModel` feeds it from the existing `GetTransactionsForPeriodUseCase` and exposes a single `CalendarUiState`; a fragment renders a 7-column `RecyclerView` grid plus a day-detail list. No DAO, repository, use-case, entity, or database-version changes.

**Tech Stack:** Same as Phase 1/Bills — Kotlin, Room (KSP), Hilt (KSP), Navigation Component with Safe Args, DataBinding/ViewBinding, Coroutines + StateFlow, JUnit. No new dependencies.

**Spec:** [docs/superpowers/specs/2026-09-04-calendar-design.md](../specs/2026-09-04-calendar-design.md)

## Global Constraints

- **No new Gradle dependencies.** Phase 1 and Bills both held this line; the grid is a plain `RecyclerView` + `GridLayoutManager(7)`, not a calendar library.
- **No new image assets.** Arrows reuse `ic_back_24` (added by Bills); the "next month" arrow is the same drawable with `android:rotation="180"`. Dots and the selected-day circle reuse `bg_circle` with `android:backgroundTint`.
- **`minSdk` is 24 — do NOT use `java.time` and do NOT enable core-library desugaring.** Use `java.util.Calendar` and `SimpleDateFormat`, matching `DatabaseSeeder` and `ui/common/util/DateFormatter`.
- **Never bucket days with `millis / 86_400_000`.** It drifts by the UTC offset and breaks across DST. Every day comparison goes through `CalendarMonth.dayStart(...)`.
- **The week starts on Sunday**, fixed. Leading blanks are `DAY_OF_WEEK - Calendar.SUNDAY`, and the weekday header is a static S/M/T/W/T/F/S row. Locale-aware week start is out of scope.
- **Never name a child view `android:id="@+id/root"`** — it collides with the generated `binding.root` and causes a confusing `RecyclerView` crash. See project memory `viewbinding-root-id-collision`.
- RecyclerView adapters here are plain `RecyclerView.Adapter` with `submitList()` + `notifyDataSetChanged()`, not `ListAdapter`/`DiffUtil`.
- `app:tint` (not `android:tint`) on ImageViews, and declare `xmlns:app` on the layout root.
- **Reuse `RecentTransactionAdapter` for the day-detail list** — construct it with no click callback so rows stay inert. Do not write a third transaction adapter.
- Claude cannot run Gradle in this environment. `watch_build.ps1` runs in the user's terminal and auto-builds on save; poll `watch_build_status.txt`. `testDebugUnitTest`/`lintDebug` need the user's terminal and are requested **once**, in Task 6.

---

## File Structure

```
app/src/main/java/com/example/dailyexpensetracker/
  ui/calendar/CalendarMonth.kt                     (create: DayCell + pure month logic)
  ui/calendar/CalendarViewModel.kt                 (create: CalendarUiState + ViewModel)
  ui/calendar/CalendarFragment.kt                  (create)
  ui/calendar/adapter/CalendarDayAdapter.kt        (create)
  ui/home/HomeFragment.kt                          (modify: calendar icon click)

app/src/test/java/com/example/dailyexpensetracker/
  ui/calendar/CalendarMonthTest.kt                 (create)

app/src/main/res/
  layout/item_calendar_day.xml                     (create)
  layout/fragment_calendar.xml                     (create)
  layout/fragment_home.xml                         (modify: header gains calendar action)
  navigation/nav_graph.xml                         (modify: +calendarFragment +action)
  values/strings.xml                               (modify: +calendar_* strings)
```

`DayCell` lives in `CalendarMonth.kt` beside the function that builds it — they change together. `TransactionListItem` (in `ui/wallet/adapter/`) is reused as-is for the day-detail list.

Every task compiles on its own, so each ends with a watcher build.

---

### Task 1: CalendarMonth — pure month logic, test-first

**Files:**
- Create: `app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonthTest.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonth.kt`

**Interfaces:**
- Consumes: `domain.model.Transaction`, `domain.model.TransactionType` (both exist).
- Produces: `DayCell(dayOfMonth: Int?, dateMillis: Long?, hasExpense: Boolean, hasIncome: Boolean)`; `CalendarMonth.monthStart(Long): Long`, `.nextMonthStart(Long): Long`, `.previousMonthStart(Long): Long`, `.dayStart(Long): Long`, `.sameDayInMonth(monthStartMillis: Long, dayMillis: Long): Long`, `.label(Long): String`, `.cellsFor(monthStartMillis: Long, transactions: List<Transaction>): List<DayCell>`. Tasks 2–4 call these by these exact names.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonthTest.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.calendar

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CalendarMonthTest {

    /** Local-time millis for a given date. [month] is 0-based, matching Calendar. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun tx(dateMillis: Long, type: TransactionType) = Transaction(
        id = 0,
        title = "t",
        amount = 1.0,
        date = dateMillis,
        categoryId = 1L,
        cardId = null,
        type = type,
        isScheduled = false,
        status = TransactionStatus.PAID
    )

    // September 2026 starts on a Tuesday -> 2 leading blanks, 30 days.
    @Test
    fun `cellsFor aligns the first day under its weekday column`() {
        val cells = CalendarMonth.cellsFor(CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 1)), emptyList())

        assertEquals(32, cells.size)
        assertEquals(listOf(null, null), cells.take(2).map { it.dayOfMonth })
        assertEquals(1, cells[2].dayOfMonth)
        assertEquals(30, cells.last().dayOfMonth)
    }

    // January 2026 starts on a Thursday -> 4 leading blanks, 31 days.
    @Test
    fun `cellsFor handles a 31-day month`() {
        val cells = CalendarMonth.cellsFor(CalendarMonth.monthStart(at(2026, Calendar.JANUARY, 15)), emptyList())

        assertEquals(35, cells.size)
        assertEquals(4, cells.count { it.dayOfMonth == null })
        assertEquals(31, cells.last().dayOfMonth)
    }

    // February 2024 is a leap February starting on a Thursday -> 4 blanks, 29 days.
    @Test
    fun `cellsFor handles a leap February`() {
        val cells = CalendarMonth.cellsFor(CalendarMonth.monthStart(at(2024, Calendar.FEBRUARY, 10)), emptyList())

        assertEquals(33, cells.size)
        assertEquals(29, cells.last().dayOfMonth)
    }

    // February 2026 is a non-leap February starting on a Sunday -> 0 blanks, 28 days.
    @Test
    fun `cellsFor handles a non-leap February`() {
        val cells = CalendarMonth.cellsFor(CalendarMonth.monthStart(at(2026, Calendar.FEBRUARY, 10)), emptyList())

        assertEquals(28, cells.size)
        assertEquals(1, cells.first().dayOfMonth)
        assertEquals(28, cells.last().dayOfMonth)
    }

    @Test
    fun `cellsFor sets expense and income flags independently`() {
        val monthStart = CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 1))
        val cells = CalendarMonth.cellsFor(
            monthStart,
            listOf(
                tx(at(2026, Calendar.SEPTEMBER, 3), TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 5), TransactionType.INCOME),
                tx(at(2026, Calendar.SEPTEMBER, 7), TransactionType.EXPENSE),
                tx(at(2026, Calendar.SEPTEMBER, 7), TransactionType.INCOME)
            )
        )
        fun day(n: Int) = cells.first { it.dayOfMonth == n }

        assertTrue(day(3).hasExpense); assertFalse(day(3).hasIncome)
        assertFalse(day(5).hasExpense); assertTrue(day(5).hasIncome)
        assertTrue(day(7).hasExpense); assertTrue(day(7).hasIncome)
        assertFalse(day(4).hasExpense); assertFalse(day(4).hasIncome)
    }

    @Test
    fun `cellsFor buckets a late-evening transaction on its local day`() {
        val monthStart = CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 1))
        val cells = CalendarMonth.cellsFor(
            monthStart,
            listOf(tx(at(2026, Calendar.SEPTEMBER, 15, hour = 23, minute = 30), TransactionType.EXPENSE))
        )

        assertTrue(cells.first { it.dayOfMonth == 15 }.hasExpense)
        assertFalse(cells.first { it.dayOfMonth == 16 }.hasExpense)
    }

    @Test
    fun `cellsFor ignores transactions outside the month`() {
        val monthStart = CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 1))
        val cells = CalendarMonth.cellsFor(
            monthStart,
            listOf(tx(at(2026, Calendar.AUGUST, 15), TransactionType.EXPENSE))
        )

        assertTrue(cells.none { it.hasExpense })
    }

    @Test
    fun `sameDayInMonth clamps to the shorter target month`() {
        val jan31 = at(2026, Calendar.JANUARY, 31)
        val februaryStart = CalendarMonth.monthStart(at(2026, Calendar.FEBRUARY, 1))

        val result = CalendarMonth.sameDayInMonth(februaryStart, jan31)

        assertEquals(28, Calendar.getInstance().apply { timeInMillis = result }.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `sameDayInMonth keeps the day when the target month is long enough`() {
        val jan15 = at(2026, Calendar.JANUARY, 15)
        val februaryStart = CalendarMonth.monthStart(at(2026, Calendar.FEBRUARY, 1))

        val result = CalendarMonth.sameDayInMonth(februaryStart, jan15)

        assertEquals(15, Calendar.getInstance().apply { timeInMillis = result }.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `dayStart zeroes the time of day`() {
        val result = CalendarMonth.dayStart(at(2026, Calendar.SEPTEMBER, 4, hour = 17, minute = 45))
        val cal = Calendar.getInstance().apply { timeInMillis = result }

        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(4, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `month navigation moves one month in each direction`() {
        val septemberStart = CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 10))

        val october = Calendar.getInstance().apply { timeInMillis = CalendarMonth.nextMonthStart(septemberStart) }
        val august = Calendar.getInstance().apply { timeInMillis = CalendarMonth.previousMonthStart(septemberStart) }

        assertEquals(Calendar.OCTOBER, october.get(Calendar.MONTH))
        assertEquals(1, october.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.AUGUST, august.get(Calendar.MONTH))
    }

    @Test
    fun `label formats month and year`() {
        assertEquals("September 2026", CalendarMonth.label(CalendarMonth.monthStart(at(2026, Calendar.SEPTEMBER, 4))))
    }
}
```

- [ ] **Step 2: Confirm the tests cannot pass yet**

`CalendarMonth` and `DayCell` don't exist, so this file won't compile. Do **not** ask the user to run Gradle here — the watcher's `installDebug` doesn't compile the test source set anyway, and Task 6 runs the tests once. Just confirm by inspection that no `ui/calendar/CalendarMonth.kt` exists:

```bash
ls app/src/main/java/com/example/dailyexpensetracker/ui/calendar/ 2>/dev/null || echo "does not exist yet - expected"
```

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonth.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.calendar

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One cell of the month grid. Leading blanks carry nulls for both day fields. */
data class DayCell(
    val dayOfMonth: Int?,
    val dateMillis: Long?,
    val hasExpense: Boolean,
    val hasIncome: Boolean
)

/**
 * Month arithmetic and grid building. Deliberately free of Android types so it unit-tests on
 * the JVM without Robolectric — this is the only non-trivial logic in the Calendar screen.
 *
 * Every day comparison goes through [dayStart] rather than dividing millis by a day's length,
 * which would drift by the UTC offset and misfile late-evening transactions across DST.
 *
 * ponytail: the week is fixed to start on Sunday so it stays in sync with the static weekday
 * header row; switch to Calendar.firstDayOfWeek if locale-aware week starts are ever needed.
 */
object CalendarMonth {

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

    /** Millis at 00:00 on the day containing [millis], in the device's local timezone. */
    fun dayStart(millis: Long): Long = calendarAt(millis).timeInMillis

    /**
     * The same day-of-month as [dayMillis], moved into the month starting at [monthStartMillis]
     * and clamped to that month's length — so navigating from Jan 31 to February lands on the
     * 28th (or 29th) rather than wrapping into March.
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
     * Leading blank cells to align day 1 under its weekday column, then one cell per day of the
     * month, each flagged by whether any of [transactions] that day was an expense and/or income.
     * Transactions outside the month are ignored. No trailing blanks — the grid just ends.
     */
    fun cellsFor(monthStartMillis: Long, transactions: List<Transaction>): List<DayCell> {
        val month = calendarAt(monthStartMillis)
        val leadingBlanks = month.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)

        val expenseDays = mutableSetOf<Int>()
        val incomeDays = mutableSetOf<Int>()
        for (transaction in transactions) {
            val txDay = calendarAt(transaction.date)
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
            val dayCal = calendarAt(monthStartMillis)
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

    /** A Calendar at [millis] with the time-of-day zeroed, in the device's local timezone. */
    private fun calendarAt(millis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
```

- [ ] **Step 4: Save and wait for the watcher build**

Poll `watch_build_status.txt` for a status newer than the one before these edits. Wait for a `SUCCESS`/`FAILED`, then re-read ~10s later and only trust it if unchanged (a status read immediately after a batch of saves can belong to a build that started mid-write):

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

Expected: `SUCCESS`. Note this only proves the **main** source set compiles — the test file is verified in Task 6.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonth.kt app/src/test/java/com/example/dailyexpensetracker/ui/calendar/CalendarMonthTest.kt
git commit -m "Add CalendarMonth month-grid logic with unit tests

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Day cell layout, adapter, and strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/layout/item_calendar_day.xml`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/adapter/CalendarDayAdapter.kt`

**Interfaces:**
- Consumes: `DayCell` from Task 1.
- Produces: `CalendarDayAdapter(onClick: (Long) -> Unit)` with `submitList(list: List<DayCell>, selectedDayMillis: Long)`. Task 4's fragment constructs it with exactly that signature.

- [ ] **Step 1: Add the strings**

In `strings.xml`, add before the closing `</resources>` tag:

```xml
    <string name="calendar_title">Calendar</string>
    <string name="calendar_empty_day">No transactions on this day</string>
    <string name="calendar_previous_month">Previous month</string>
    <string name="calendar_next_month">Next month</string>
    <string name="calendar_back">Back</string>
    <string name="calendar_day_sun">S</string>
    <string name="calendar_day_mon">M</string>
    <string name="calendar_day_tue">T</string>
    <string name="calendar_day_wed">W</string>
    <string name="calendar_day_thu">T</string>
    <string name="calendar_day_fri">F</string>
    <string name="calendar_day_sat">S</string>
```

The three content-description strings are deliberate: unlike the decorative icons elsewhere in this app (which use `contentDescription="@null"`), the back and month arrows are interactive controls and need real labels for TalkBack.

- [ ] **Step 2: Create the day cell layout**

Create `app/src/main/res/layout/item_calendar_day.xml`. Note this is a plain layout (no `<layout>` wrapper) like `item_transaction.xml`, and no view is named `root`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_horizontal"
    android:orientation="vertical"
    android:paddingTop="6dp"
    android:paddingBottom="6dp">

    <TextView
        android:id="@+id/tvDay"
        android:layout_width="34dp"
        android:layout_height="34dp"
        android:background="@drawable/bg_circle"
        android:backgroundTint="@android:color/transparent"
        android:gravity="center"
        android:textColor="@color/text_primary"
        android:textSize="14sp"
        tools:text="15" />

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="8dp"
        android:layout_marginTop="2dp"
        android:gravity="center"
        android:orientation="horizontal">

        <View
            android:id="@+id/vDotExpense"
            android:layout_width="6dp"
            android:layout_height="6dp"
            android:background="@drawable/bg_circle"
            android:backgroundTint="@color/soft_red" />

        <View
            android:id="@+id/vDotIncome"
            android:layout_width="6dp"
            android:layout_height="6dp"
            android:layout_marginStart="3dp"
            android:background="@drawable/bg_circle"
            android:backgroundTint="@color/soft_green" />
    </LinearLayout>

</LinearLayout>
```

The dot row keeps its 8dp height whether or not dots are visible, so day numbers stay on a consistent baseline across grid rows — the adapter toggles `INVISIBLE`, never `GONE`.

- [ ] **Step 3: Create the adapter**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/adapter/CalendarDayAdapter.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.calendar.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.ItemCalendarDayBinding
import com.example.dailyexpensetracker.ui.calendar.DayCell

/**
 * Selection is passed in alongside the cells rather than baked into [DayCell], so changing the
 * selected day doesn't require rebuilding the month's cell list.
 */
class CalendarDayAdapter(
    private val onClick: (Long) -> Unit
) : RecyclerView.Adapter<CalendarDayAdapter.ViewHolder>() {

    private var items: List<DayCell> = emptyList()
    private var selectedDayMillis: Long = 0L

    fun submitList(list: List<DayCell>, selectedDayMillis: Long) {
        items = list
        this.selectedDayMillis = selectedDayMillis
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cell = items[position]
        holder.bind(cell, cell.dateMillis != null && cell.dateMillis == selectedDayMillis)
        val dateMillis = cell.dateMillis
        holder.itemView.setOnClickListener(
            if (dateMillis == null) null else View.OnClickListener { onClick(dateMillis) }
        )
    }

    class ViewHolder(private val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cell: DayCell, isSelected: Boolean) {
            val context = binding.root.context
            binding.tvDay.text = cell.dayOfMonth?.toString().orEmpty()
            binding.tvDay.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.purple_primary else android.R.color.transparent
                )
            )
            binding.tvDay.setTextColor(
                ContextCompat.getColor(context, if (isSelected) R.color.white else R.color.text_primary)
            )
            binding.vDotExpense.visibility = if (cell.hasExpense) View.VISIBLE else View.INVISIBLE
            binding.vDotIncome.visibility = if (cell.hasIncome) View.VISIBLE else View.INVISIBLE
        }
    }
}
```

The day circle is always `bg_circle` (set once in the layout in Step 2); only its tint changes at bind time, so there's one code path, no second drawable, and no `setBackgroundResource` call that could disturb the fixed 34dp box.

- [ ] **Step 4: Save and wait for the watcher build**

Same polling approach as Task 1 Step 4. Expected: `SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/layout/item_calendar_day.xml app/src/main/java/com/example/dailyexpensetracker/ui/calendar/adapter/CalendarDayAdapter.kt
git commit -m "Add calendar day cell layout and adapter

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: CalendarViewModel

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarViewModel.kt`

**Interfaces:**
- Consumes: `CalendarMonth` from Task 1; existing `GetTransactionsForPeriodUseCase(start, end)`, `GetCategoriesUseCase()`, and `TransactionListItem` (in `ui.wallet.adapter`).
- Produces: `CalendarUiState(monthLabel: String, cells: List<DayCell>, selectedDayMillis: Long, dayItems: List<TransactionListItem>)`; `CalendarViewModel.uiState: StateFlow<CalendarUiState>`, `.selectDay(dayMillis: Long)`, `.previousMonth()`, `.nextMonth()`. Task 4's fragment calls these exact names.

- [ ] **Step 1: Create the ViewModel**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarViewModel.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.ui.wallet.adapter.TransactionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CalendarUiState(
    val monthLabel: String = "",
    val cells: List<DayCell> = emptyList(),
    val selectedDayMillis: Long = 0L,
    val dayItems: List<TransactionListItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    getTransactionsForPeriod: GetTransactionsForPeriodUseCase,
    getCategories: GetCategoriesUseCase
) : ViewModel() {

    private val today = CalendarMonth.dayStart(System.currentTimeMillis())
    private val _monthStart = MutableStateFlow(CalendarMonth.monthStart(today))
    private val _selectedDay = MutableStateFlow(today)

    // getByDateRange uses SQL BETWEEN, which is inclusive on both ends, so the upper bound is
    // one millisecond before the next month starts.
    private val monthTransactions = _monthStart.flatMapLatest { monthStart ->
        getTransactionsForPeriod(monthStart, CalendarMonth.nextMonthStart(monthStart) - 1)
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        _monthStart, monthTransactions, getCategories(), _selectedDay
    ) { monthStart, transactions, categories, selectedDay ->
        val categoryById = categories.associateBy { it.id }
        CalendarUiState(
            monthLabel = CalendarMonth.label(monthStart),
            cells = CalendarMonth.cellsFor(monthStart, transactions),
            selectedDayMillis = selectedDay,
            dayItems = transactions
                .filter { CalendarMonth.dayStart(it.date) == selectedDay }
                .map { TransactionListItem(it, categoryById[it.categoryId]) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    fun selectDay(dayMillis: Long) {
        _selectedDay.value = dayMillis
    }

    fun previousMonth() = moveTo(CalendarMonth.previousMonthStart(_monthStart.value))

    fun nextMonth() = moveTo(CalendarMonth.nextMonthStart(_monthStart.value))

    /** Keeps the selected day-of-month across a month change so a day is always selected. */
    private fun moveTo(newMonthStart: Long) {
        _selectedDay.value = CalendarMonth.sameDayInMonth(newMonthStart, _selectedDay.value)
        _monthStart.value = newMonthStart
    }
}
```

- [ ] **Step 2: Save and wait for the watcher build**

Same polling approach as Task 1 Step 4. Expected: `SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarViewModel.kt
git commit -m "Add CalendarViewModel with month and day-selection state

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Calendar screen layout and fragment

**Files:**
- Create: `app/src/main/res/layout/fragment_calendar.xml`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarFragment.kt`

**Interfaces:**
- Consumes: `CalendarViewModel`/`CalendarUiState` (Task 3), `CalendarDayAdapter` (Task 2), existing `RecentTransactionAdapter`.
- Produces: `CalendarFragment` — referenced by class name in Task 5's `nav_graph.xml`.

- [ ] **Step 1: Create the layout**

Create `app/src/main/res/layout/fragment_calendar.xml`:

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
            android:padding="20dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageView
                    android:id="@+id/ivBack"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:contentDescription="@string/calendar_back"
                    android:src="@drawable/ic_back_24"
                    app:tint="@color/white" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:text="@string/calendar_title"
                    android:textColor="@color/white"
                    android:textSize="18sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageView
                    android:id="@+id/ivPrevMonth"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:contentDescription="@string/calendar_previous_month"
                    android:src="@drawable/ic_back_24"
                    app:tint="@color/white" />

                <TextView
                    android:id="@+id/tvMonthLabel"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:gravity="center"
                    android:textColor="@color/white"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    tools:text="September 2026" />

                <ImageView
                    android:id="@+id/ivNextMonth"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:contentDescription="@string/calendar_next_month"
                    android:rotation="180"
                    android:src="@drawable/ic_back_24"
                    app:tint="@color/white" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:paddingStart="8dp"
            android:paddingTop="12dp"
            android:paddingEnd="8dp"
            android:paddingBottom="4dp">

            <TextView
                style="@style/CalendarWeekdayLabel"
                android:text="@string/calendar_day_sun" />

            <TextView
                style="@style/CalendarWeekdayLabel"
                android:text="@string/calendar_day_mon" />

            <TextView
                style="@style/CalendarWeekdayLabel"
                android:text="@string/calendar_day_tue" />

            <TextView
                style="@style/CalendarWeekdayLabel"
                android:text="@string/calendar_day_wed" />

            <TextView
                style="@style/CalendarWeekdayLabel"
                android:text="@string/calendar_day_thu" />

            <TextView
                style="@style/CalendarWeekdayLabel"
                android:text="@string/calendar_day_fri" />

            <TextView
                style="@style/CalendarWeekdayLabel"
                android:text="@string/calendar_day_sat" />
        </LinearLayout>

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvDays"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingStart="8dp"
            android:paddingEnd="8dp"
            tools:itemCount="30"
            tools:listitem="@layout/item_calendar_day" />

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:layout_marginStart="16dp"
            android:layout_marginTop="12dp"
            android:layout_marginEnd="16dp"
            android:background="@color/background_light_gray" />

        <TextView
            android:id="@+id/tvEmptyDay"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:gravity="center"
            android:text="@string/calendar_empty_day"
            android:textColor="@color/text_secondary"
            android:textSize="13sp"
            android:visibility="gone"
            tools:visibility="visible" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvDayTransactions"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:clipToPadding="false"
            android:paddingTop="4dp"
            android:paddingBottom="24dp"
            tools:listitem="@layout/item_transaction" />

    </LinearLayout>
</layout>
```

This references a `CalendarWeekdayLabel` style so the seven header cells don't repeat six attributes each. Add it to `app/src/main/res/values/styles.xml`, alongside the existing `Widget.App.*` styles:

```xml
    <style name="CalendarWeekdayLabel">
        <item name="android:layout_width">0dp</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:layout_weight">1</item>
        <item name="android:gravity">center</item>
        <item name="android:textColor">@color/text_secondary</item>
        <item name="android:textSize">12sp</item>
    </style>
```

- [ ] **Step 2: Create the fragment**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarFragment.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.databinding.FragmentCalendarBinding
import com.example.dailyexpensetracker.ui.calendar.adapter.CalendarDayAdapter
import com.example.dailyexpensetracker.ui.wallet.adapter.RecentTransactionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()

    private val dayAdapter = CalendarDayAdapter { dayMillis -> viewModel.selectDay(dayMillis) }

    // No click callback: day-detail rows are inert, unlike the Bills list.
    private val transactionAdapter = RecentTransactionAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvDays.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.rvDays.adapter = dayAdapter

        binding.rvDayTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDayTransactions.adapter = transactionAdapter

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }
        binding.ivPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.ivNextMonth.setOnClickListener { viewModel.nextMonth() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvMonthLabel.text = state.monthLabel
                    dayAdapter.submitList(state.cells, state.selectedDayMillis)
                    transactionAdapter.submitList(state.dayItems)
                    binding.tvEmptyDay.visibility = if (state.dayItems.isEmpty()) View.VISIBLE else View.GONE
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

- [ ] **Step 3: Save and wait for the watcher build**

Same polling approach as Task 1 Step 4. Expected: `SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_calendar.xml app/src/main/res/values/styles.xml app/src/main/java/com/example/dailyexpensetracker/ui/calendar/CalendarFragment.kt
git commit -m "Add Calendar screen layout and fragment

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Home entry point, navigation wiring, and full verification

**Files:**
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/home/HomeFragment.kt`
- Modify: `app/src/main/res/navigation/nav_graph.xml`

**Interfaces:**
- Consumes: `CalendarFragment` (Task 4). Produces the Safe Args `HomeFragmentDirections.actionHomeFragmentToCalendarFragment()` that `HomeFragment` calls.

- [ ] **Step 1: Add the calendar action to Home's header**

In `fragment_home.xml`, the purple header is currently a vertical `LinearLayout` holding two `TextView`s. Replace that whole block:

```xml
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@color/purple_primary"
                android:orientation="vertical"
                android:padding="20dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/home_title"
                    android:textColor="@color/purple_accent_light"
                    android:textSize="14sp" />

                <TextView
                    android:id="@+id/tvTodayBalance"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textColor="@color/white"
                    android:textSize="28sp"
                    android:textStyle="bold"
                    tools:text="$180.75" />
            </LinearLayout>
```

with a horizontal row that keeps the two TextViews in a weighted column and puts the icon at the end:

```xml
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@color/purple_primary"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:padding="20dp">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/home_title"
                        android:textColor="@color/purple_accent_light"
                        android:textSize="14sp" />

                    <TextView
                        android:id="@+id/tvTodayBalance"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:textColor="@color/white"
                        android:textSize="28sp"
                        android:textStyle="bold"
                        tools:text="$180.75" />
                </LinearLayout>

                <ImageView
                    android:id="@+id/ivCalendar"
                    android:layout_width="26dp"
                    android:layout_height="26dp"
                    android:contentDescription="@string/calendar_title"
                    android:src="@drawable/ic_calendar_24"
                    app:tint="@color/white" />
            </LinearLayout>
```

`tvTodayBalance` keeps its id, so `HomeFragment`'s existing binding call is unaffected.

- [ ] **Step 2: Wire the click in HomeFragment**

In `HomeFragment.kt`, add this import alongside the existing ones:

```kotlin
import androidx.navigation.fragment.findNavController
```

Then, in `onViewCreated`, add this immediately after the `rvMonthlyBudget` adapter assignment and before the `viewLifecycleOwner.lifecycleScope.launch` block:

```kotlin
        binding.ivCalendar.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToCalendarFragment())
        }
```

- [ ] **Step 3: Add the destination and action to the nav graph**

In `nav_graph.xml`, replace the existing `homeFragment` entry:

```xml
    <fragment
        android:id="@+id/homeFragment"
        android:name="com.example.dailyexpensetracker.ui.home.HomeFragment"
        android:label="@string/nav_home" />
```

with a version carrying the action, and add the new destination immediately after it:

```xml
    <fragment
        android:id="@+id/homeFragment"
        android:name="com.example.dailyexpensetracker.ui.home.HomeFragment"
        android:label="@string/nav_home">
        <action
            android:id="@+id/action_homeFragment_to_calendarFragment"
            app:destination="@id/calendarFragment" />
    </fragment>

    <fragment
        android:id="@+id/calendarFragment"
        android:name="com.example.dailyexpensetracker.ui.calendar.CalendarFragment"
        android:label="@string/calendar_title" />
```

The action id `action_homeFragment_to_calendarFragment` must match exactly — Safe Args derives `actionHomeFragmentToCalendarFragment()` from it, which Step 2 calls.

- [ ] **Step 4: Save and wait for the watcher build**

Same polling approach as Task 1 Step 4. This is the first build where Safe Args generates the action used in Step 2. Expected: `SUCCESS`. If `FAILED`, the likely cause is a mismatch between the action id and the generated method name.

- [ ] **Step 5: Relaunch the app**

```bash
export MSYS_NO_PATHCONV=1
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" logcat -c
"$ADB" shell am force-stop com.example.dailyexpensetracker
"$ADB" shell am start -n com.example.dailyexpensetracker/.ui.main.MainActivity
"$ADB" shell sleep 3
```

App data is **not** cleared — the schema didn't change, and the existing seeded transactions are what the calendar should show markers for.

- [ ] **Step 6: Open Calendar from Home**

Find the calendar icon and tap it. Use `uiautomator dump` for exact coordinates rather than guessing from a screenshot:

Set `S` to your own session's scratchpad directory (the one named in your system prompt) — never the repo root, so nothing has to be cleaned out of git later.

```bash
export MSYS_NO_PATHCONV=1
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
S="$SCRATCHPAD"   # substitute your session's scratchpad path
"$ADB" shell uiautomator dump /sdcard/d.xml >/dev/null
"$ADB" pull /sdcard/d.xml "$S/d.xml" >/dev/null
grep -oE 'resource-id="com.example.dailyexpensetracker:id/ivCalendar"[^>]*bounds="[^"]*"' "$S/d.xml"
```

Tap the centre of those bounds, then screenshot:

```bash
"$ADB" shell input tap <cx> <cy>
"$ADB" shell sleep 2
"$ADB" shell screencap -p /sdcard/cal.png
"$ADB" pull /sdcard/cal.png "$S/cal.png" >/dev/null
"$ADB" logcat -d -t 300 | grep -A 10 "FATAL EXCEPTION" || echo "no fatal"
```

Expected, viewing `cal.png`: purple header with a back arrow, "Calendar", and a "‹ September 2026 ›" row; a S M T W T F S weekday row; a month grid with today's cell filled purple; red dots under the days carrying seeded expenses and a green dot on the seeded salary day; and today's transactions listed below. No `FATAL EXCEPTION`.

Crucially, check that **day 1 sits under the correct weekday column** — that's the single most likely thing to be wrong, and it's visible at a glance.

- [ ] **Step 7: Verify day selection**

Dump the UI, find a day cell known to carry a dot, tap it, and confirm the detail list changes and the purple selection circle moves. Then tap a day with no dot and confirm the list empties and "No transactions on this day" appears. Screenshot both.

- [ ] **Step 8: Verify month navigation**

Tap `ivNextMonth`, screenshot, confirm the label advances one month, the grid re-aligns, and a day stays selected. Tap `ivPrevMonth` twice to land on the previous month, confirming the label goes back two months from where it was and the grid still renders. Check `logcat` for `FATAL EXCEPTION` after each.

- [ ] **Step 9: Verify Home and the rest of the app still work**

```bash
export MSYS_NO_PATHCONV=1
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" shell input keyevent KEYCODE_BACK
"$ADB" shell sleep 2
"$ADB" shell screencap -p /sdcard/home.png
"$ADB" pull /sdcard/home.png "$S/home.png" >/dev/null
"$ADB" logcat -d -t 300 | grep -A 10 "FATAL EXCEPTION" || echo "no fatal"
```

Expected: back returns to Home, whose header now shows the calendar icon on the right with `tvTodayBalance` unchanged on the left, and the bottom nav still works. The header restructure is the only change touching an existing screen, so this is the regression check that matters.

- [ ] **Step 10: Clean up scratch files**

Screenshots and dumps belong in the scratchpad, never the repo. Confirm the repo root is clean:

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
ls *.png dump*.xml 2>/dev/null || echo clean
```

- [ ] **Step 11: Commit**

```bash
git add app/src/main/res/layout/fragment_home.xml app/src/main/java/com/example/dailyexpensetracker/ui/home/HomeFragment.kt app/src/main/res/navigation/nav_graph.xml
git commit -m "Open Calendar from the Home header

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Final checks

**Files:** none (verification only)

- [ ] **Step 1: Ask the user to run tests and lint**

This is the only point in the plan that needs the user's terminal, and the **first** time `CalendarMonthTest` is compiled or run — the watcher's `installDebug` never compiles the test source set. Tell the user **not** to stop the watcher to run it; a second terminal avoids the gap.

```bash
./gradlew testDebugUnitTest lintDebug --console=plain > verify_output.txt 2>&1
```

- [ ] **Step 2: Read the results yourself**

Don't ask for output to be pasted, and don't trust `UP-TO-DATE` lines as evidence a test ran — read the reports and check their timestamps:

```bash
P="C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
grep -ho '<testsuite [^>]*' "$P/app/build/test-results/testDebugUnitTest/"*.xml \
  | sed -E 's/.*name="([^"]*)".*tests="([^"]*)".*failures="([^"]*)".*errors="([^"]*)".*/\1: tests=\2 failures=\3 errors=\4/'
tail -5 "$P/app/build/reports/lint-results-debug.txt"
```

Expected: `CalendarMonthTest` with `tests=12 failures=0 errors=0`, plus the existing `TransactionUseCasesTest` (5) and `ExampleUnitTest` (1) still green. Lint: **0 errors**. Warnings are expected — the project carries 44 already; fix new *errors* only, and leave `GradleDependency`/`NewerVersionAvailable` notices alone, since those versions are deliberately pinned.

- [ ] **Step 3: Clean up and open the PR**

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
rm -f verify_output.txt
git status
```

Confirm a clean tree, then — **after explicit user confirmation, per the branch policy** — push `feature/calendar` and open a PR against `master`.

---

## Explicitly Out of Scope (carried from the spec)

Swiping between months (arrows only), week or year views, jumping to an arbitrary date, creating or editing a transaction from the calendar, per-day budget indicators or heat-map shading, locale-aware first-day-of-week, and multi-month preloading.
