# Category Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Categories grid and both Home lists somewhere to go — a Category Detail screen showing one category's transactions for a selected month.

**Architecture:** One new fragment, ViewModel and layout. Nothing new below the UI layer: the transaction list reuses `RecentTransactionAdapter`, and the data comes from `GetTransactionsForPeriodUseCase` filtered by `categoryId` in the ViewModel, so every query stays bounded by date range. Three display-only adapters gain an optional `onClick` defaulting to `null`, so no existing call site changes.

**Tech Stack:** Kotlin, MVVM + Clean Architecture, Hilt, Navigation Component + Safe Args, ViewBinding, coroutines/Flow. JUnit 4 + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-09-09-category-detail-design.md`

## Global Constraints

- **No new dependencies.** No new DAO query, no new use case, no new adapter.
- **Scope is Categories and Home only.** `CardCarouselAdapter` (Wallet) and `LegendAdapter` (Reports) stay inert — deliberately out of scope.
- **Colour rules — this is a new layout, which is exactly where these bite.** `@color/white` is foreground-on-purple **only**; card and sheet surfaces are `@color/surface`. The screen ground is `@color/background`, hairlines are `@color/divider`. Putting `white` on a card background looks right in light mode and wrong in dark. Never paint a foreground element in `@color/background`.
- **Never name a child view `android:id="@+id/root"`** — collides with generated `binding.root`.
- Use `app:tint` (not `android:tint`) on `ImageView`, per AppCompat lint.
- Avoid string concatenation in `setText` — lint flags `SetTextI18n`. Use `getString(R.string.x, arg)`.
- **Do not push to `master`.** Work on `feature/category-detail`, push, open a PR, ask before merging.
- **You cannot run Gradle.** Every `gradlew` call fails with `Unable to establish loopback connection`. Ask the user to run `watch_build.ps1` and poll `watch_build_status.txt` yourself; it runs `installDebug`, which **never compiles the test source set**. Ask for one `./gradlew installDebug testDebugUnitTest lintDebug` at the end and read the reports yourself. **Check report timestamps** — stale XMLs look identical to a fresh pass.
- Write Kotlin/XML with the Write tool, not Bash heredocs — the shell parser has tripped on them repeatedly.
- Baselines to preserve: **129 tests / 20 suites passing**, **0 lint errors**. CI runs `testDebugUnitTest` and `lintDebug` on the PR.

---

### Task 1: Give the three display adapters an optional click callback

Purely additive. Every parameter defaults to `null`, so all existing call sites compile unchanged and the app behaves identically until Task 4 passes callbacks in.

**Files:**
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/categories/adapter/CategoryGridAdapter.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/home/adapter/TopSpendingAdapter.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/home/adapter/MonthlyBudgetAdapter.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `CategoryGridAdapter(onClick: ((Category) -> Unit)? = null)`, `TopSpendingAdapter(onClick: ((Category) -> Unit)? = null)`, `MonthlyBudgetAdapter(onClick: ((CategorySpend) -> Unit)? = null)`.

**No unit test.** These are click-wiring changes on `RecyclerView.Adapter`; asserting them needs an instrumented test, and the behaviour is verified in Task 4's emulator walkthrough. The pattern copied here is `RecentTransactionAdapter`'s, which is already in use by three screens.

- [ ] **Step 1: `CategoryGridAdapter`**

Replace the class declaration and `ViewHolder` so the row reports clicks:

```kotlin
/** Tiles are inert unless [onClick] is supplied — Categories passes one to open Category Detail. */
class CategoryGridAdapter(
    private val onClick: ((Category) -> Unit)? = null
) : RecyclerView.Adapter<CategoryGridAdapter.ViewHolder>() {
```

Change `onCreateViewHolder` to pass the callback through:

```kotlin
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }
```

and the `ViewHolder` to bind it:

```kotlin
    class ViewHolder(
        private val binding: ItemCategoryCardBinding,
        private val onClick: ((Category) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Category) {
            binding.tvName.text = item.name
            binding.ivIcon.setImageResource(CategoryIconMapper.iconFor(item.iconName))
            runCatching { Color.parseColor(item.colorHex) }.getOrNull()?.let { binding.cardContent.setBackgroundColor(it) }
            onClick?.let { click -> binding.root.setOnClickListener { click(item) } }
        }
    }
```

- [ ] **Step 2: `TopSpendingAdapter`**

Same shape. Note it binds a `CategorySpend` but reports the `Category`, because the destination only needs the category:

```kotlin
/** Icons are inert unless [onClick] is supplied — Home passes one to open Category Detail. */
class TopSpendingAdapter(
    private val onClick: ((Category) -> Unit)? = null
) : RecyclerView.Adapter<TopSpendingAdapter.ViewHolder>() {
```

```kotlin
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }
```

```kotlin
    class ViewHolder(
        private val binding: ItemCategoryIconBinding,
        private val onClick: ((Category) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategorySpend) {
            binding.tvName.text = item.category.name
            binding.ivIcon.setImageResource(CategoryIconMapper.iconFor(item.category.iconName))
            onClick?.let { click -> binding.root.setOnClickListener { click(item.category) } }
        }
    }
```

Add `import com.example.dailyexpensetracker.domain.model.Category`.

- [ ] **Step 3: `MonthlyBudgetAdapter`**

This one reports the whole `CategorySpend`, since a caller may want the spend too:

```kotlin
/** Cards are inert unless [onClick] is supplied — Home passes one to open Category Detail. */
class MonthlyBudgetAdapter(
    private val onClick: ((CategorySpend) -> Unit)? = null
) : RecyclerView.Adapter<MonthlyBudgetAdapter.ViewHolder>() {
```

Its `onCreateViewHolder` currently sits between the lines shown earlier; change it to `ViewHolder(binding, onClick)`, then give the `ViewHolder` the parameter and append to the end of `bind`:

```kotlin
    class ViewHolder(
        private val binding: ItemBudgetCardBinding,
        private val onClick: ((CategorySpend) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategorySpend) {
            val limit = item.category.budgetLimit ?: 0.0
            binding.tvName.text = item.category.name
            binding.tvPerDay.text = "${CurrencyFormatter.format(limit / 30)} Per day"
            binding.tvSpent.text = CurrencyFormatter.format(item.spent)
            binding.tvLimit.text = CurrencyFormatter.format(limit)
            binding.ivIcon.setImageResource(CategoryIconMapper.iconFor(item.category.iconName))
            binding.progressBudget.progress = if (limit > 0) {
                (item.spent / limit * 100).coerceIn(0.0, 100.0).toInt()
            } else 0
            onClick?.let { click -> binding.root.setOnClickListener { click(item) } }
        }
    }
```

The body above is the existing one verbatim, with only the final line added — repeated in full so you
do not have to reconstruct it. The `tvPerDay` concatenation already trips `SetTextI18n`; that warning
is pre-existing and stays.

- [ ] **Step 4: Verify nothing else broke and build**

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
grep -rn "CategoryGridAdapter(\|TopSpendingAdapter(\|MonthlyBudgetAdapter(" app/src/main/java
```

Expected: the existing constructions in `CategoriesFragment` and `HomeFragment` take no arguments — they must still compile because every parameter defaults to `null`. Do not change them here; Task 4 does.

Poll `watch_build_status.txt` for `SUCCESS` (wait for a value that stays put ~10s; if it returns to `BUILDING`, keep waiting). The app must look and behave exactly as before.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/categories/adapter/CategoryGridAdapter.kt app/src/main/java/com/example/dailyexpensetracker/ui/home/adapter/
git commit -m "Let the display adapters report clicks"
```

---

### Task 2: `CategoryDetailViewModel`

Pure logic and fully JVM-testable. Built before the fragment so nothing references a screen that does not exist.

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/categorydetail/CategoryDetailViewModel.kt`
- Test: `app/src/test/java/com/example/dailyexpensetracker/ui/categorydetail/CategoryDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `GetCategoriesUseCase`, `GetTransactionsForPeriodUseCase`, `MonthRange`, `TransactionListItem`, and the test helpers `MainDispatcherRule`, `subscribe()`, `testCategory()`, `testTransaction()`, `FakeCategoryRepository`, `FakeTransactionRepository`.
- Produces: `data class CategoryDetailUiState(category: Category?, monthLabel: String, total: Double, budgetLimit: Double?, items: List<TransactionListItem>)` and `CategoryDetailViewModel(savedStateHandle, getCategories, getTransactionsForPeriod)` with `uiState: StateFlow<CategoryDetailUiState>`, `previousMonth()`, `nextMonth()`. The Safe Args argument name is **`categoryId`**.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/dailyexpensetracker/ui/categorydetail/CategoryDetailViewModelTest.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.categorydetail

import androidx.lifecycle.SavedStateHandle
import com.example.dailyexpensetracker.MainDispatcherRule
import com.example.dailyexpensetracker.domain.usecase.FakeCategoryRepository
import com.example.dailyexpensetracker.domain.usecase.FakeTransactionRepository
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.subscribe
import com.example.dailyexpensetracker.testCategory
import com.example.dailyexpensetracker.testTransaction
import com.example.dailyexpensetracker.ui.common.util.MonthRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

class CategoryDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = System.currentTimeMillis()
    private val monthStart = MonthRange.monthStart(now)
    private val nextMonthStart = MonthRange.nextMonthStart(monthStart)

    private val transactionRepository = FakeTransactionRepository()
    private val categoryRepository = FakeCategoryRepository()

    /** Mid-morning on [day] of the month starting at [monthStartMillis]. */
    private fun dayInMonth(monthStartMillis: Long, day: Int): Long =
        MonthRange.calendarAt(monthStartMillis).apply {
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 10)
        }.timeInMillis

    private fun viewModel(categoryId: Long = 1L) = CategoryDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("categoryId" to categoryId)),
        getCategories = GetCategoriesUseCase(categoryRepository),
        getTransactionsForPeriod = GetTransactionsForPeriodUseCase(transactionRepository)
    )

    @Before
    fun seed() {
        categoryRepository.setCategories(
            listOf(
                testCategory(1, name = "Groceries", budgetLimit = 500.0),
                testCategory(2, name = "Fuel", budgetLimit = 200.0),
                testCategory(3, name = "Salary", isExpense = false, budgetLimit = null)
            )
        )
    }

    @Test
    fun `shows only the requested category's transactions`() = runTest {
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)),
                testTransaction(2, categoryId = 2, amount = 99.0, date = dayInMonth(monthStart, 3)),
                testTransaction(3, categoryId = 1, amount = 20.0, date = dayInMonth(monthStart, 4))
            )
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals(listOf(1L, 3L), viewModel.uiState.value.items.map { it.transaction.id })
    }

    @Test
    fun `the total is the sum of that category's transactions for the month`() = runTest {
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)),
                testTransaction(2, categoryId = 2, amount = 99.0, date = dayInMonth(monthStart, 3)),
                testTransaction(3, categoryId = 1, amount = 20.0, date = dayInMonth(monthStart, 4))
            )
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals(50.0, viewModel.uiState.value.total, 0.001)
    }

    @Test
    fun `transactions outside the month are excluded`() = runTest {
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)),
                testTransaction(2, categoryId = 1, amount = 999.0, date = dayInMonth(nextMonthStart, 1))
            )
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.transaction.id })
        assertEquals(30.0, viewModel.uiState.value.total, 0.001)
    }

    @Test
    fun `moving to the next month re-queries`() = runTest {
        transactionRepository.setTransactions(
            listOf(
                testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)),
                testTransaction(2, categoryId = 1, amount = 999.0, date = dayInMonth(nextMonthStart, 1))
            )
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        viewModel.nextMonth()

        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.transaction.id })
        assertEquals(MonthRange.label(nextMonthStart), viewModel.uiState.value.monthLabel)
    }

    @Test
    fun `month navigation round-trips`() = runTest {
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        viewModel.previousMonth()
        viewModel.nextMonth()

        assertEquals(MonthRange.label(monthStart), viewModel.uiState.value.monthLabel)
    }

    @Test
    fun `the category's name and budget reach the state`() = runTest {
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals("Groceries", viewModel.uiState.value.category?.name)
        assertEquals(500.0, viewModel.uiState.value.budgetLimit!!, 0.001)
    }

    /** Income categories carry no limit, so the view has to hide the budget rather than show 0. */
    @Test
    fun `a category with no budget limit reports null`() = runTest {
        val viewModel = viewModel(3L)
        subscribe(viewModel.uiState)

        assertEquals("Salary", viewModel.uiState.value.category?.name)
        assertNull(viewModel.uiState.value.budgetLimit)
    }

    @Test
    fun `an unknown category id yields an empty state rather than throwing`() = runTest {
        transactionRepository.setTransactions(
            listOf(testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)))
        )
        val viewModel = viewModel(999L)
        subscribe(viewModel.uiState)

        assertNull(viewModel.uiState.value.category)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(0.0, viewModel.uiState.value.total, 0.001)
    }

    @Test
    fun `each item carries the category so the row can render its icon`() = runTest {
        transactionRepository.setTransactions(
            listOf(testTransaction(1, categoryId = 1, amount = 30.0, date = dayInMonth(monthStart, 2)))
        )
        val viewModel = viewModel(1L)
        subscribe(viewModel.uiState)

        assertEquals("Groceries", viewModel.uiState.value.items.single().category?.name)
    }
}
```

- [ ] **Step 2: Confirm it fails**

`CategoryDetailViewModel` does not exist, so the file will not compile.

- [ ] **Step 3: Write the ViewModel**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/categorydetail/CategoryDetailViewModel.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.categorydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsForPeriodUseCase
import com.example.dailyexpensetracker.ui.common.util.MonthRange
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

data class CategoryDetailUiState(
    val category: Category? = null,
    val monthLabel: String = "",
    val total: Double = 0.0,
    val budgetLimit: Double? = null,
    val items: List<TransactionListItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getCategories: GetCategoriesUseCase,
    getTransactionsForPeriod: GetTransactionsForPeriodUseCase
) : ViewModel() {

    private val categoryId: Long = savedStateHandle.get<Long>("categoryId") ?: 0L

    private val _monthStart = MutableStateFlow(MonthRange.monthStart(System.currentTimeMillis()))

    // getByDateRange uses SQL BETWEEN, inclusive at both ends, so the upper bound is one
    // millisecond before the next month starts.
    private val monthTransactions = _monthStart.flatMapLatest { monthStart ->
        getTransactionsForPeriod(monthStart, MonthRange.nextMonthStart(monthStart) - 1)
    }

    val uiState: StateFlow<CategoryDetailUiState> = combine(
        _monthStart, monthTransactions, getCategories()
    ) { monthStart, transactions, categories ->
        // Filtered here rather than in SQL so every query stays bounded by date range, which is
        // the rule this project holds to. A month is a few dozen rows.
        val category = categories.find { it.id == categoryId }
        val mine = transactions.filter { it.categoryId == categoryId }
        CategoryDetailUiState(
            category = category,
            monthLabel = MonthRange.label(monthStart),
            total = mine.sumOf { it.amount },
            budgetLimit = category?.budgetLimit,
            items = mine.map { TransactionListItem(it, category) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryDetailUiState())

    fun previousMonth() {
        _monthStart.value = MonthRange.previousMonthStart(_monthStart.value)
    }

    fun nextMonth() {
        _monthStart.value = MonthRange.nextMonthStart(_monthStart.value)
    }
}
```

- [ ] **Step 3: Build, then commit**

Poll `watch_build_status.txt` for `SUCCESS`. The watcher does not compile tests, so a broken test still shows `SUCCESS` — the tests are verified in Task 5.

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/categorydetail/ app/src/test/java/com/example/dailyexpensetracker/ui/categorydetail/
git commit -m "Add CategoryDetailViewModel"
```

---

### Task 3: Strings and layout

Resources only, built before the fragment so it never references a missing id.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/layout/fragment_category_detail.xml`

**Interfaces:**
- Consumes: `@drawable/ic_back_24` (used for both arrows — the next-month one is rotated 180°, matching Reports and Calendar), `@color/purple_primary`, `@color/background`, `@color/purple_accent_light`, `@color/text_secondary`, `@color/white`.
- Produces: `FragmentCategoryDetailBinding` with ids `ivBack`, `ivCategoryIcon`, `tvCategoryName`, `ivPrevMonth`, `tvMonthLabel`, `ivNextMonth`, `tvTotal`, `tvBudget`, `rvTransactions`, `tvEmpty`; and the string resources below.

- [ ] **Step 1: Add the strings**

Append inside `<resources>` in `app/src/main/res/values/strings.xml`:

```xml
    <string name="category_detail_back">Back</string>
    <string name="category_detail_previous_month">Previous month</string>
    <string name="category_detail_next_month">Next month</string>
    <string name="category_detail_of_budget">of %1$s</string>
    <string name="category_detail_empty">No transactions this month</string>
```

`category_detail_of_budget` is a format string so the budget line avoids `SetTextI18n`.

- [ ] **Step 2: Create the layout**

Create `app/src/main/res/layout/fragment_category_detail.xml`. The header is `@color/purple_primary` with `@color/white` foreground; everything below is `@color/background` ground with `@color/surface` cards — getting that backwards is the trap this project has hit repeatedly.

**There is no forward-arrow drawable in this project**, and you should not add one. Both
`fragment_reports.xml` and `fragment_calendar.xml` render the next-month arrow as `ic_back_24` with
`android:rotation="180"`, and the layout below follows that convention.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/purple_primary"
        android:orientation="vertical"
        android:paddingStart="20dp"
        android:paddingTop="20dp"
        android:paddingEnd="20dp"
        android:paddingBottom="16dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageView
                android:id="@+id/ivBack"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:contentDescription="@string/category_detail_back"
                android:src="@drawable/ic_back_24"
                app:tint="@color/white" />

            <ImageView
                android:id="@+id/ivCategoryIcon"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:layout_marginStart="16dp"
                android:contentDescription="@null"
                app:tint="@color/white"
                tools:src="@drawable/ic_back_24" />

            <TextView
                android:id="@+id/tvCategoryName"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:layout_weight="1"
                android:textColor="@color/white"
                android:textSize="18sp"
                android:textStyle="bold"
                tools:text="Groceries" />
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
                android:contentDescription="@string/category_detail_previous_month"
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
                android:contentDescription="@string/category_detail_next_month"
                android:rotation="180"
                android:src="@drawable/ic_back_24"
                app:tint="@color/white" />
        </LinearLayout>

        <TextView
            android:id="@+id/tvTotal"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:textColor="@color/white"
            android:textSize="28sp"
            android:textStyle="bold"
            tools:text="$1,230.00" />

        <TextView
            android:id="@+id/tvBudget"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textColor="@color/purple_accent_light"
            android:textSize="14sp"
            tools:text="of $500.00" />
    </LinearLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvTransactions"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        tools:itemCount="4"
        tools:listitem="@layout/item_transaction" />

    <TextView
        android:id="@+id/tvEmpty"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:gravity="center"
        android:text="@string/category_detail_empty"
        android:textColor="@color/text_secondary"
        android:textSize="14sp" />
</LinearLayout>
```

- [ ] **Step 3: Build, then commit**

Poll for `SUCCESS` — a missing drawable or string fails resource compilation loudly.

```bash
git add app/src/main/res/
git commit -m "Add Category Detail resources"
```

---

### Task 4: The fragment, navigation, and the three entry points

Last, so nothing before it referenced a screen that did not exist.

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/categorydetail/CategoryDetailFragment.kt`
- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/home/HomeFragment.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/ui/categories/CategoriesFragment.kt`

**Interfaces:**
- Consumes: `CategoryDetailViewModel` and `CategoryDetailUiState` (Task 2), `FragmentCategoryDetailBinding` and the strings (Task 3), the adapter callbacks (Task 1), `RecentTransactionAdapter(onClick)`.
- Produces: `categoryDetailFragment` destination, `HomeFragmentDirections.actionHomeFragmentToCategoryDetailFragment(categoryId)`, `CategoriesFragmentDirections.actionCategoriesFragmentToCategoryDetailFragment(categoryId)`.

- [ ] **Step 1: Add the destination and three actions**

In `nav_graph.xml`, add inside the existing `homeFragment` block:

```xml
        <action
            android:id="@+id/action_homeFragment_to_categoryDetailFragment"
            app:destination="@id/categoryDetailFragment" />
```

Change the `categoriesFragment` entry from a self-closing tag into a block containing its action:

```xml
    <fragment
        android:id="@+id/categoriesFragment"
        android:name="com.example.dailyexpensetracker.ui.categories.CategoriesFragment"
        android:label="@string/nav_categories">
        <action
            android:id="@+id/action_categoriesFragment_to_categoryDetailFragment"
            app:destination="@id/categoryDetailFragment" />
    </fragment>
```

And add the new destination alongside the others:

```xml
    <fragment
        android:id="@+id/categoryDetailFragment"
        android:name="com.example.dailyexpensetracker.ui.categorydetail.CategoryDetailFragment"
        android:label="@string/nav_categories">
        <argument
            android:name="categoryId"
            app:argType="long" />
        <action
            android:id="@+id/action_categoryDetailFragment_to_addTransactionSheet"
            app:destination="@id/addTransactionSheet" />
    </fragment>
```

**If a later build fails on `Unresolved reference 'HomeFragmentDirections'` or similar,** the watcher swallowed this edit and `generateSafeArgsDebug` stayed `UP-TO-DATE`. `touch app/src/main/res/navigation/nav_graph.xml` to force a fresh cycle before debugging anything else.

- [ ] **Step 2: Write the fragment**

Create `app/src/main/java/com/example/dailyexpensetracker/ui/categorydetail/CategoryDetailFragment.kt`:

```kotlin
package com.example.dailyexpensetracker.ui.categorydetail

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.FragmentCategoryDetailBinding
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.wallet.adapter.RecentTransactionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoryDetailFragment : Fragment() {

    private var _binding: FragmentCategoryDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CategoryDetailViewModel by viewModels()

    private val adapter = RecentTransactionAdapter { item ->
        findNavController().navigate(
            CategoryDetailFragmentDirections.actionCategoryDetailFragmentToAddTransactionSheet(
                item.transaction.id
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }
        binding.ivPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.ivNextMonth.setOnClickListener { viewModel.nextMonth() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvCategoryName.text = state.category?.name.orEmpty()
                    state.category?.let {
                        binding.ivCategoryIcon.setImageResource(CategoryIconMapper.iconFor(it.iconName))
                    }
                    binding.tvMonthLabel.text = state.monthLabel
                    binding.tvTotal.text = CurrencyFormatter.format(state.total)

                    // Income categories carry no limit, so show nothing rather than "of 0.00".
                    val limit = state.budgetLimit
                    if (limit == null) {
                        binding.tvBudget.visibility = View.GONE
                    } else {
                        binding.tvBudget.visibility = View.VISIBLE
                        binding.tvBudget.text = getString(
                            R.string.category_detail_of_budget,
                            CurrencyFormatter.format(limit)
                        )
                    }

                    adapter.submitList(state.items)
                    binding.tvEmpty.visibility =
                        if (state.items.isEmpty()) View.VISIBLE else View.GONE
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

- [ ] **Step 3: Wire Home's two lists**

In `HomeFragment`, replace the two adapter fields so each navigates:

```kotlin
    private val topSpendingAdapter = TopSpendingAdapter { category ->
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToCategoryDetailFragment(category.id)
        )
    }

    private val monthlyBudgetAdapter = MonthlyBudgetAdapter { categorySpend ->
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToCategoryDetailFragment(categorySpend.category.id)
        )
    }
```

`findNavController` is already imported in this file.

- [ ] **Step 4: Wire the Categories grid**

In `CategoriesFragment`, replace the adapter field:

```kotlin
    private val adapter = CategoryGridAdapter { category ->
        findNavController().navigate(
            CategoriesFragmentDirections.actionCategoriesFragmentToCategoryDetailFragment(category.id)
        )
    }
```

Add `import androidx.navigation.fragment.findNavController` — unlike `HomeFragment`, this file has no navigation yet, so the import is missing.

- [ ] **Step 5: Build and walk it on the emulator**

Poll for `SUCCESS`, then drive `adb`. Use `uiautomator dump` for tap coordinates rather than guessing; they shift when the keyboard opens. Screenshots go in the scratchpad, **never** the repo.

1. Categories → tap a tile → lands on that category, correct name and icon.
2. Home → tap a Top Spending circle → same screen, correct category.
3. Home → tap a Monthly Budget card → same screen, correct category.
4. Month arrows move the label and the list.
5. Tap a transaction → the editor opens prefilled.
6. Open an **income** category (Salary) — the budget line must be hidden, not "of $0.00".
7. **Switch to dark mode** (Settings → Appearance → Dark) and repeat 1 and 4. The header must stay purple with white text; the ground and rows must be dark. This is a new layout, so it is exactly where the `white`-versus-`surface` mistake would show.

After each step:

```bash
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
$ADB logcat -d | grep "FATAL EXCEPTION"
```

Expected: no output. A missing Safe Args argument or Hilt binding surfaces here, not at compile time.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/ui/ app/src/main/res/navigation/nav_graph.xml
git commit -m "Add the Category Detail screen and its three entry points"
```

---

### Task 5: Verification and handoff

- [ ] **Step 1: Ask the user to run the suite**

Once, in a second terminal, without stopping the watcher:

```bash
./gradlew installDebug testDebugUnitTest lintDebug --console=plain > verify_output.txt 2>&1
```

- [ ] **Step 2: Read the reports yourself**

```bash
ls -la --time-style=+%m-%d_%H:%M app/build/test-results/testDebugUnitTest/*.xml | head -3
grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | awk -F'"' '{t+=$2;s+=$4;f+=$6;e+=$8} END {print "tests="t,"failures="f,"errors="e}'
tail -2 app/build/reports/lint-results-debug.txt
```

**Check the timestamps.** Stale XMLs from an earlier run are indistinguishable from a fresh pass by content alone, and `UP-TO-DATE` is not evidence anything ran.

Expected: **138 tests across 21 suites** (129 + the 9 new `CategoryDetailViewModelTest` cases), 0 failures, **0 lint errors**.

- [ ] **Step 3: Update HANDOFF.md**

Record the verified numbers. Add Category Detail to the screens list and note the three entry points. Update the "what's left" section: the Wallet card carousel and the Reports chart legend remain the only dead adapters, and the legend is now a one-line change because the destination exists.

- [ ] **Step 4: Open the PR**

Push `feature/category-detail`, open a PR, and **ask before merging**. CI runs `testDebugUnitTest` and `lintDebug` automatically — wait for it to go green before recommending a merge.
