# Bills & Schedule Bill Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Bills list (Paid/Overdue/Upcoming tabs, searchable) and a Schedule Bill Detail screen (Approve/Decline) that surface the `status`/`isScheduled` data the Phase 1 schema already seeded but never displayed.

**Architecture:** Two new Fragment+ViewModel screens on the existing Room/Hilt/Navigation Component foundation. Bills replaces the inert `placeholderGraphFragment` bottom-nav slot; Schedule Bill Detail is a new non-bottom-nav destination reached by Safe Args. Two new nullable columns (`payeeName`, `payeeRole`) on the existing `transactions` table via a destructive migration (no real users yet). Four new use cases wrap two new repository methods and two new DAO queries.

**Tech Stack:** Same as Phase 1 — Kotlin, Room (KSP), Hilt (KSP), Jetpack Navigation Component with Safe Args, DataBinding/ViewBinding, Coroutines + StateFlow. No new dependencies.

**Spec:** [docs/superpowers/specs/2026-09-03-bills-design.md](../specs/2026-09-03-bills-design.md)

## Global Constraints

- No new Gradle dependencies — everything needed (TabLayout, CardView, Material buttons) is already in the `material`/`androidx.cardview` libraries Phase 1 added.
- No external image assets — payee avatar is a generic initials-circle (`bg_circle` drawable + first-letter TextView), matching the category-icon visual language.
- Follow the Phase 1 house style established after the ViewBinding `id="root"` incident (see project memory `viewbinding-root-id-collision`): **never** name a child view `id="root"` in any new layout.
- RecyclerView adapters in this codebase are plain `RecyclerView.Adapter` with a `submitList()` + `notifyDataSetChanged()`, not `ListAdapter`/`DiffUtil` — match this pattern for `BillListAdapter`.
- Claude cannot run Gradle directly in this environment (sandboxed loopback-socket restriction). `watch_build.ps1` runs in the user's terminal and auto-builds+installs on every save; verification steps in this plan poll `watch_build_status.txt` (via `Read`) and drive the emulator via `adb` — never invoke `./gradlew` directly as an executor.
- `testDebugUnitTest` and `lintDebug` require the user's terminal (not automated by the watcher) — the final task asks the user to run one command, same as Phase 1.

---

## File Structure

```
app/src/main/java/com/example/dailyexpensetracker/
  data/local/entity/TransactionEntity.kt          (modify: +payeeName, +payeeRole)
  data/local/dao/TransactionDao.kt                 (modify: +getByStatus, +getById, +updateStatus, +delete)
  data/local/AppDatabase.kt                        (modify: version 1 -> 2)
  data/local/DatabaseSeeder.kt                      (modify: payee data on 2 sample bills)
  di/DatabaseModule.kt                              (modify: +fallbackToDestructiveMigration)
  domain/model/Transaction.kt                       (modify: +payeeName, +payeeRole)
  domain/repository/TransactionRepository.kt        (modify: +4 method signatures)
  data/repository/TransactionRepositoryImpl.kt      (modify: +4 implementations, mappers carry payee fields)
  domain/usecase/GetTransactionsByStatusUseCase.kt  (create)
  domain/usecase/GetTransactionByIdUseCase.kt       (create)
  domain/usecase/UpdateTransactionStatusUseCase.kt  (create)
  domain/usecase/DeleteTransactionUseCase.kt        (create)
  ui/bills/BillsFragment.kt                         (create)
  ui/bills/BillsViewModel.kt                        (create)
  ui/bills/adapter/BillListAdapter.kt               (create)
  ui/billdetail/BillDetailFragment.kt               (create)
  ui/billdetail/BillDetailViewModel.kt              (create)

app/src/test/java/com/example/dailyexpensetracker/domain/usecase/
  FakeTransactionRepository.kt                      (create)
  TransactionUseCasesTest.kt                        (create)

app/src/main/res/
  drawable/ic_bills_24.xml                          (create)
  drawable/ic_search_24.xml                         (create)
  drawable/ic_filter_24.xml                          (create)
  drawable/ic_back_24.xml                            (create)
  layout/fragment_bills.xml                          (create)
  layout/fragment_bill_detail.xml                    (create)
  navigation/nav_graph.xml                           (modify: swap placeholder, +billDetailFragment)
  menu/bottom_nav_menu.xml                           (modify: swap placeholder item)
  values/strings.xml                                 (modify: -nav_graph, +bills_*/bill_detail_* strings)
```

`TransactionListItem` (already in `ui/wallet/adapter/`) is reused as-is for Bills rows — it's a generic Transaction+Category join, not Wallet-specific.

---

### Task 1: Schema — payee fields, migration, seed data

**Files:**
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/local/entity/TransactionEntity.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/domain/model/Transaction.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/local/DatabaseSeeder.kt`

**Interfaces:**
- Produces: `TransactionEntity(..., payeeName: String? = null, payeeRole: String? = null)`, `Transaction(..., payeeName: String? = null, payeeRole: String? = null)` — Task 2's repository mappers and every later task's UI code read `transaction.payeeName` / `transaction.payeeRole`.

- [ ] **Step 1: Add the two nullable columns to the entity**

In `TransactionEntity.kt`, add after `status: TransactionStatus`:

```kotlin
    val status: TransactionStatus,
    val payeeName: String? = null,
    val payeeRole: String? = null
```

- [ ] **Step 2: Add the matching fields to the domain model**

In `Transaction.kt`, add after `status: TransactionStatus`:

```kotlin
    val status: TransactionStatus,
    val payeeName: String? = null,
    val payeeRole: String? = null
```

- [ ] **Step 3: Bump the database version**

In `AppDatabase.kt`, change:

```kotlin
@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, CardEntity::class],
    version = 1,
    exportSchema = false
)
```

to:

```kotlin
@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, CardEntity::class],
    version = 2,
    exportSchema = false
)
```

- [ ] **Step 4: Add a destructive migration to the database builder**

In `DatabaseModule.kt`, in `provideAppDatabase`, add `.fallbackToDestructiveMigration()` to the builder chain, right after `.addCallback(...)`:

```kotlin
        instance = Room.databaseBuilder(context, AppDatabase::class.java, "daily_expense_tracker.db")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedScope.launch {
                        DatabaseSeeder.seed(instance.categoryDao(), instance.cardDao(), instance.transactionDao())
                    }
                }
            })
            .fallbackToDestructiveMigration()
            .build()
```

(No real users exist yet, so wiping and reseeding on schema change is correct — see spec's "Data Model" section for the reasoning.)

- [ ] **Step 5: Give the two existing bill-like sample transactions payee data**

In `DatabaseSeeder.kt`, change the `Pharmacy Refill` and `Overdue Bill` entries (leave the other 7 transactions unchanged):

```kotlin
            TransactionEntity(title = "Pharmacy Refill", amount = 1250.65, date = daysAhead(3), categoryId = medicineId, cardId = 1, type = TransactionType.EXPENSE, isScheduled = true, status = TransactionStatus.UPCOMING, payeeName = "City Pharmacy", payeeRole = "Pharmacy"),
            TransactionEntity(title = "Overdue Bill", amount = 320.0, date = daysAgo(2), categoryId = foodId, cardId = 2, type = TransactionType.EXPENSE, isScheduled = true, status = TransactionStatus.OVERDUE, payeeName = "Stephen Thomas", payeeRole = "House Owner")
```

- [ ] **Step 6: Save all 5 files, wait for the watcher to rebuild**

Poll `watch_build_status.txt` (in the project root) for a `SUCCESS`/`FAILED` line newer than the one before this task's edits:

```bash
F="C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
prev=$(cat "$F/watch_build_status.txt" 2>/dev/null)
for i in $(seq 1 40); do
  sleep 5
  cur=$(cat "$F/watch_build_status.txt" 2>/dev/null)
  if [ "$cur" != "$prev" ] && echo "$cur" | grep -qE "SUCCESS|FAILED"; then
    echo "$cur"; break
  fi
done
```

Expected: `SUCCESS <timestamp>`. If `FAILED`, read `watch_build.log`'s tail for the error before continuing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/data/local/entity/TransactionEntity.kt app/src/main/java/com/example/dailyexpensetracker/domain/model/Transaction.kt app/src/main/java/com/example/dailyexpensetracker/data/local/AppDatabase.kt app/src/main/java/com/example/dailyexpensetracker/di/DatabaseModule.kt app/src/main/java/com/example/dailyexpensetracker/data/local/DatabaseSeeder.kt
git commit -m "Add payee fields to Transaction, bump DB to v2

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Data layer — DAO queries and repository methods

**Files:**
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/local/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/example/dailyexpensetracker/data/repository/TransactionRepositoryImpl.kt`

**Interfaces:**
- Consumes: `TransactionEntity`/`Transaction` with `payeeName`/`payeeRole` from Task 1.
- Produces: `TransactionRepository.getByStatus(status: TransactionStatus): Flow<List<Transaction>>`, `.getById(id: Long): Flow<Transaction?>`, `suspend .updateStatus(id: Long, status: TransactionStatus)`, `suspend .delete(id: Long)` — Task 3's use cases call these exactly.

- [ ] **Step 1: Add the 4 new queries to the DAO**

In `TransactionDao.kt`, add inside the `interface TransactionDao` block (after `getSumByTypeAndDateRange`):

```kotlin
    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY date DESC")
    fun getByStatus(status: TransactionStatus): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getById(id: Long): Flow<TransactionEntity?>

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TransactionStatus)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)
```

Add the import this needs at the top of the file:

```kotlin
import com.example.dailyexpensetracker.domain.model.TransactionStatus
```

- [ ] **Step 2: Add the 4 matching methods to the repository interface**

In `TransactionRepository.kt`, replace the whole file with:

```kotlin
package com.example.dailyexpensetracker.domain.repository

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAll(): Flow<List<Transaction>>
    fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>>
    fun getRecent(limit: Int): Flow<List<Transaction>>
    fun getSumByTypeAndDateRange(type: TransactionType, start: Long, end: Long): Flow<Double?>
    fun getByStatus(status: TransactionStatus): Flow<List<Transaction>>
    fun getById(id: Long): Flow<Transaction?>
    suspend fun add(transaction: Transaction)
    suspend fun updateStatus(id: Long, status: TransactionStatus)
    suspend fun delete(id: Long)
}
```

- [ ] **Step 3: Implement the 4 new methods and carry payee fields through the mappers**

In `TransactionRepositoryImpl.kt`, replace the whole file with:

```kotlin
package com.example.dailyexpensetracker.data.repository

import com.example.dailyexpensetracker.data.local.dao.TransactionDao
import com.example.dailyexpensetracker.data.local.entity.TransactionEntity
import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private fun TransactionEntity.toDomain() = Transaction(
    id = id,
    title = title,
    amount = amount,
    date = date,
    categoryId = categoryId,
    cardId = cardId,
    type = type,
    isScheduled = isScheduled,
    status = status,
    payeeName = payeeName,
    payeeRole = payeeRole
)

private fun Transaction.toEntity() = TransactionEntity(
    id = id,
    title = title,
    amount = amount,
    date = date,
    categoryId = categoryId,
    cardId = cardId,
    type = type,
    isScheduled = isScheduled,
    status = status,
    payeeName = payeeName,
    payeeRole = payeeRole
)

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao
) : TransactionRepository {
    override fun getAll(): Flow<List<Transaction>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> =
        dao.getByDateRange(start, end).map { list -> list.map { it.toDomain() } }

    override fun getRecent(limit: Int): Flow<List<Transaction>> =
        dao.getRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun getSumByTypeAndDateRange(type: TransactionType, start: Long, end: Long): Flow<Double?> =
        dao.getSumByTypeAndDateRange(type, start, end)

    override fun getByStatus(status: TransactionStatus): Flow<List<Transaction>> =
        dao.getByStatus(status).map { list -> list.map { it.toDomain() } }

    override fun getById(id: Long): Flow<Transaction?> =
        dao.getById(id).map { it?.toDomain() }

    override suspend fun add(transaction: Transaction) {
        dao.insert(transaction.toEntity())
    }

    override suspend fun updateStatus(id: Long, status: TransactionStatus) {
        dao.updateStatus(id, status)
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }
}
```

- [ ] **Step 4: Save all 3 files, wait for the watcher to rebuild**

Same polling approach as Task 1 Step 6. Expected: `SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/data/local/dao/TransactionDao.kt app/src/main/java/com/example/dailyexpensetracker/domain/repository/TransactionRepository.kt app/src/main/java/com/example/dailyexpensetracker/data/repository/TransactionRepositoryImpl.kt
git commit -m "Add getByStatus/getById/updateStatus/delete to TransactionRepository

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Domain use cases + unit tests

**Files:**
- Create: `app/src/main/java/com/example/dailyexpensetracker/domain/usecase/GetTransactionsByStatusUseCase.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/domain/usecase/GetTransactionByIdUseCase.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/domain/usecase/UpdateTransactionStatusUseCase.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/domain/usecase/DeleteTransactionUseCase.kt`
- Create: `app/src/test/java/com/example/dailyexpensetracker/domain/usecase/FakeTransactionRepository.kt`
- Create: `app/src/test/java/com/example/dailyexpensetracker/domain/usecase/TransactionUseCasesTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository` methods from Task 2.
- Produces: `GetTransactionsByStatusUseCase(status): Flow<List<Transaction>>`, `GetTransactionByIdUseCase(id): Flow<Transaction?>`, `UpdateTransactionStatusUseCase` as `suspend (id, status) -> Unit`, `DeleteTransactionUseCase` as `suspend (id) -> Unit` — Task 4 and Task 5's ViewModels inject and call these by these exact names.

- [ ] **Step 1: Write the failing test (and its fake repository)**

Create `FakeTransactionRepository.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeTransactionRepository : TransactionRepository {
    private val state = MutableStateFlow<List<Transaction>>(emptyList())

    fun setTransactions(transactions: List<Transaction>) {
        state.value = transactions
    }

    override fun getAll(): Flow<List<Transaction>> = state.asStateFlow()

    override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> =
        state.map { list -> list.filter { it.date in start..end } }

    override fun getRecent(limit: Int): Flow<List<Transaction>> =
        state.map { list -> list.sortedByDescending { it.date }.take(limit) }

    override fun getSumByTypeAndDateRange(type: TransactionType, start: Long, end: Long): Flow<Double?> =
        state.map { list -> list.filter { it.type == type && it.date in start..end }.sumOf { it.amount } }

    override fun getByStatus(status: TransactionStatus): Flow<List<Transaction>> =
        state.map { list -> list.filter { it.status == status } }

    override fun getById(id: Long): Flow<Transaction?> =
        state.map { list -> list.find { it.id == id } }

    override suspend fun add(transaction: Transaction) {
        state.value = state.value + transaction
    }

    override suspend fun updateStatus(id: Long, status: TransactionStatus) {
        state.value = state.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}
```

Create `TransactionUseCasesTest.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionUseCasesTest {

    private fun sampleTransaction(id: Long, status: TransactionStatus) = Transaction(
        id = id,
        title = "Test $id",
        amount = 10.0,
        date = 0L,
        categoryId = 1L,
        cardId = null,
        type = TransactionType.EXPENSE,
        isScheduled = true,
        status = status
    )

    @Test
    fun `GetTransactionsByStatusUseCase returns only matching status`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(
            listOf(
                sampleTransaction(1, TransactionStatus.PAID),
                sampleTransaction(2, TransactionStatus.UPCOMING),
                sampleTransaction(3, TransactionStatus.UPCOMING)
            )
        )
        val useCase = GetTransactionsByStatusUseCase(repo)

        val result = useCase(TransactionStatus.UPCOMING).first()

        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test
    fun `GetTransactionByIdUseCase returns the matching transaction`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(listOf(sampleTransaction(1, TransactionStatus.PAID)))
        val useCase = GetTransactionByIdUseCase(repo)

        assertEquals(1L, useCase(1L).first()?.id)
    }

    @Test
    fun `GetTransactionByIdUseCase returns null for an unknown id`() = runBlocking {
        val repo = FakeTransactionRepository()
        val useCase = GetTransactionByIdUseCase(repo)

        assertNull(useCase(999L).first())
    }

    @Test
    fun `UpdateTransactionStatusUseCase updates the transaction's status`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(listOf(sampleTransaction(1, TransactionStatus.UPCOMING)))
        val useCase = UpdateTransactionStatusUseCase(repo)

        useCase(1L, TransactionStatus.PAID)

        assertEquals(TransactionStatus.PAID, repo.getById(1L).first()?.status)
    }

    @Test
    fun `DeleteTransactionUseCase removes the transaction`() = runBlocking {
        val repo = FakeTransactionRepository()
        repo.setTransactions(listOf(sampleTransaction(1, TransactionStatus.OVERDUE)))
        val useCase = DeleteTransactionUseCase(repo)

        useCase(1L)

        assertNull(repo.getById(1L).first())
    }
}
```

This won't compile yet — the 4 use case classes don't exist. That's expected; the next step creates them.

- [ ] **Step 2: Create the 4 use cases**

`GetTransactionsByStatusUseCase.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTransactionsByStatusUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    operator fun invoke(status: TransactionStatus): Flow<List<Transaction>> = repository.getByStatus(status)
}
```

`GetTransactionByIdUseCase.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTransactionByIdUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    operator fun invoke(id: Long): Flow<Transaction?> = repository.getById(id)
}
```

`UpdateTransactionStatusUseCase.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import javax.inject.Inject

class UpdateTransactionStatusUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(id: Long, status: TransactionStatus) = repository.updateStatus(id, status)
}
```

`DeleteTransactionUseCase.kt`:

```kotlin
package com.example.dailyexpensetracker.domain.usecase

import com.example.dailyexpensetracker.domain.repository.TransactionRepository
import javax.inject.Inject

class DeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
```

- [ ] **Step 3: Save all 6 files, wait for the watcher to rebuild**

Same polling approach as Task 1 Step 6 (this compiles the main + test source sets; the watcher's `installDebug` target does compile `test` sources as a side effect of a full build, so a `FAILED` status here would also show a test-compile error in `watch_build.log`). Expected: `SUCCESS`.

- [ ] **Step 4: Run the unit tests**

The watcher only runs `installDebug`, not `testDebugUnitTest` — ask the user to run this one command and paste back the tail, or (if by this point in the plan's execution a watcher variant that also runs tests exists) check its output directly:

```bash
.\gradlew testDebugUnitTest --console=plain > test_output.txt 2>&1; type test_output.txt | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed (the existing template test + the 5 new ones — `GetTransactionsByStatusUseCase`, `GetTransactionByIdUseCase` x2, `UpdateTransactionStatusUseCase`, `DeleteTransactionUseCase`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/dailyexpensetracker/domain/usecase/GetTransactionsByStatusUseCase.kt app/src/main/java/com/example/dailyexpensetracker/domain/usecase/GetTransactionByIdUseCase.kt app/src/main/java/com/example/dailyexpensetracker/domain/usecase/UpdateTransactionStatusUseCase.kt app/src/main/java/com/example/dailyexpensetracker/domain/usecase/DeleteTransactionUseCase.kt app/src/test/java/com/example/dailyexpensetracker/domain/usecase/FakeTransactionRepository.kt app/src/test/java/com/example/dailyexpensetracker/domain/usecase/TransactionUseCasesTest.kt
git commit -m "Add Bills domain use cases with unit tests

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Bills List screen

**Files:**
- Create: `app/src/main/res/drawable/ic_bills_24.xml`
- Create: `app/src/main/res/drawable/ic_search_24.xml`
- Create: `app/src/main/res/drawable/ic_filter_24.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/layout/fragment_bills.xml`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/bills/adapter/BillListAdapter.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/bills/BillsViewModel.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/bills/BillsFragment.kt`

**Interfaces:**
- Consumes: `GetTransactionsByStatusUseCase`, `GetCategoriesUseCase` (existing), `TransactionListItem` (existing, in `ui.wallet.adapter`).
- Produces: `BillsFragment` (referenced by class name in Task 6's `nav_graph.xml`/`bottom_nav_menu.xml` edits), `BillListAdapter(onClick: (TransactionListItem) -> Unit)`.

- [ ] **Step 1: Create the 3 new icons**

```bash
D="C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker/app/src/main/res/drawable"

cat > "$D/ic_bills_24.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M6,2v20l2.5,-1.5L11,22l2.5,-1.5L16,22l2.5,-1.5L21,22V2zM17,15H7v-2h10zM17,11H7V9h10zM17,7H7V5h10z" />
</vector>
EOF

cat > "$D/ic_search_24.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5S5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5S14,7.01 14,9.5S11.99,14 9.5,14z" />
</vector>
EOF

cat > "$D/ic_filter_24.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2h-8v2h8zM11,9h2V7h4V5h-4V3h-2v6z" />
</vector>
EOF

echo "created: $(ls "$D"/ic_bills_24.xml "$D"/ic_search_24.xml "$D"/ic_filter_24.xml | wc -l)"
```

Expected output: `created: 3`.

- [ ] **Step 2: Add the new strings, remove the now-unused `nav_graph` string**

In `strings.xml`, replace:

```xml
    <string name="nav_graph">Graph</string>
```

with:

```xml
    <string name="nav_bills">Bills</string>
```

Then add these new strings right before the closing `</resources>` tag:

```xml
    <string name="bills_tab_paid">Paid</string>
    <string name="bills_tab_overdue">Overdue</string>
    <string name="bills_tab_upcoming">Upcoming</string>
    <string name="bills_search_hint">Search</string>
```

- [ ] **Step 3: Create the Bills list layout**

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
                android:text="@string/nav_bills"
                android:textColor="@color/white"
                android:textSize="20sp"
                android:textStyle="bold" />

            <com.google.android.material.tabs.TabLayout
                android:id="@+id/tabLayout"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                app:tabIndicatorColor="@color/white"
                app:tabSelectedTextColor="@color/white"
                app:tabTextColor="@color/purple_accent_light">

                <com.google.android.material.tabs.TabItem
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/bills_tab_paid" />

                <com.google.android.material.tabs.TabItem
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/bills_tab_overdue" />

                <com.google.android.material.tabs.TabItem
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/bills_tab_upcoming" />
            </com.google.android.material.tabs.TabLayout>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_margin="16dp"
            android:background="@drawable/bg_rounded_card_16"
            android:backgroundTint="@color/white"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingStart="14dp"
            android:paddingEnd="14dp">

            <ImageView
                android:layout_width="20dp"
                android:layout_height="20dp"
                android:contentDescription="@null"
                android:src="@drawable/ic_search_24"
                app:tint="@color/text_secondary" />

            <EditText
                android:id="@+id/etSearch"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:background="@null"
                android:hint="@string/bills_search_hint"
                android:inputType="text"
                android:maxLines="1"
                android:paddingStart="10dp"
                android:paddingTop="12dp"
                android:paddingEnd="10dp"
                android:paddingBottom="12dp" />

            <ImageView
                android:layout_width="20dp"
                android:layout_height="20dp"
                android:contentDescription="@null"
                android:src="@drawable/ic_filter_24"
                app:tint="@color/text_secondary" />
        </LinearLayout>

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvBills"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:clipToPadding="false"
            android:paddingBottom="24dp"
            tools:listitem="@layout/item_transaction" />

    </LinearLayout>
</layout>
```

- [ ] **Step 4: Create the Bills row adapter**

```kotlin
package com.example.dailyexpensetracker.ui.bills.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.ItemTransactionBinding
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.common.util.DateFormatter
import com.example.dailyexpensetracker.ui.wallet.adapter.TransactionListItem

class BillListAdapter(
    private val onClick: (TransactionListItem) -> Unit
) : RecyclerView.Adapter<BillListAdapter.ViewHolder>() {

    private var items: List<TransactionListItem> = emptyList()

    fun submitList(list: List<TransactionListItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class ViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TransactionListItem) {
            val tx = item.transaction
            binding.tvTitle.text = tx.title
            binding.tvDate.text = DateFormatter.formatForItem(tx.date)
            binding.ivIcon.setImageResource(item.category?.let { CategoryIconMapper.iconFor(it.iconName) } ?: R.drawable.ic_grid_24)

            val isExpense = tx.type == TransactionType.EXPENSE
            val sign = if (isExpense) "-" else "+"
            binding.tvAmount.text = "$sign${CurrencyFormatter.format(tx.amount)}"
            binding.tvAmount.setTextColor(
                ContextCompat.getColor(binding.root.context, if (isExpense) R.color.soft_red else R.color.soft_green)
            )
        }
    }
}
```

- [ ] **Step 5: Create the Bills ViewModel**

```kotlin
package com.example.dailyexpensetracker.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionsByStatusUseCase
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillsViewModel @Inject constructor(
    getTransactionsByStatus: GetTransactionsByStatusUseCase,
    getCategories: GetCategoriesUseCase
) : ViewModel() {

    private val _status = MutableStateFlow(TransactionStatus.PAID)
    private val _query = MutableStateFlow("")

    private val transactionsFlow = _status.flatMapLatest { getTransactionsByStatus(it) }

    val bills: StateFlow<List<TransactionListItem>> = combine(
        transactionsFlow, getCategories(), _query
    ) { transactions, categories, query ->
        val categoryById = categories.associateBy { it.id }
        transactions
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { TransactionListItem(it, categoryById[it.categoryId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setStatus(status: TransactionStatus) {
        _status.value = status
    }

    fun setQuery(query: String) {
        _query.value = query
    }
}
```

- [ ] **Step 6: Create the Bills Fragment**

This references `BillsFragmentDirections`, which Safe Args generates from the `<action>` added in Task 6 — this file won't compile standalone until Task 6 is done. That's expected for this task; the watcher build in Step 7 below is run at the end of Task 6, not here.

```kotlin
package com.example.dailyexpensetracker.ui.bills

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.dailyexpensetracker.databinding.FragmentBillsBinding
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.ui.bills.adapter.BillListAdapter
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BillsFragment : Fragment() {

    private var _binding: FragmentBillsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BillsViewModel by viewModels()

    private val adapter = BillListAdapter { item ->
        findNavController().navigate(
            BillsFragmentDirections.actionBillsFragmentToBillDetailFragment(item.transaction.id)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvBills.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBills.adapter = adapter

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setStatus(statusForTabPosition(tab.position))
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bills.collect { adapter.submitList(it) }
            }
        }
    }

    private fun statusForTabPosition(position: Int): TransactionStatus = when (position) {
        0 -> TransactionStatus.PAID
        1 -> TransactionStatus.OVERDUE
        else -> TransactionStatus.UPCOMING
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 7: Commit (build verification happens at the end of Task 6, once nav wiring makes this compile)**

```bash
git add app/src/main/res/drawable/ic_bills_24.xml app/src/main/res/drawable/ic_search_24.xml app/src/main/res/drawable/ic_filter_24.xml app/src/main/res/values/strings.xml app/src/main/res/layout/fragment_bills.xml app/src/main/java/com/example/dailyexpensetracker/ui/bills/adapter/BillListAdapter.kt app/src/main/java/com/example/dailyexpensetracker/ui/bills/BillsViewModel.kt app/src/main/java/com/example/dailyexpensetracker/ui/bills/BillsFragment.kt
git commit -m "Add Bills list screen (fragment/viewmodel/adapter/layout)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Schedule Bill Detail screen

**Files:**
- Create: `app/src/main/res/drawable/ic_back_24.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/layout/fragment_bill_detail.xml`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/billdetail/BillDetailViewModel.kt`
- Create: `app/src/main/java/com/example/dailyexpensetracker/ui/billdetail/BillDetailFragment.kt`

**Interfaces:**
- Consumes: `GetTransactionByIdUseCase`, `GetCategoriesUseCase`, `GetCardsUseCase` (existing), `UpdateTransactionStatusUseCase`, `DeleteTransactionUseCase` from Task 3.
- Produces: `BillDetailFragment` (referenced by class name in Task 6's `nav_graph.xml`), reads a `transactionId: Long` nav argument via `SavedStateHandle`.

- [ ] **Step 1: Create the back-arrow icon**

```bash
D="C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker/app/src/main/res/drawable"
cat > "$D/ic_back_24.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z" />
</vector>
EOF
echo "created"
```

- [ ] **Step 2: Add the detail-screen strings**

In `strings.xml`, add these before `</resources>`:

```xml
    <string name="bill_detail_title">Schedule Bill</string>
    <string name="bill_detail_decline">Decline</string>
    <string name="bill_detail_approve">Approve</string>
    <string name="bill_detail_scheduled_for">Scheduled for %1$s</string>
    <string name="bill_detail_card">Card %1$s</string>
```

- [ ] **Step 3: Create the detail layout**

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
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:padding="20dp">

            <ImageView
                android:id="@+id/ivBack"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:contentDescription="@null"
                android:src="@drawable/ic_back_24"
                app:tint="@color/white" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:text="@string/bill_detail_title"
                android:textColor="@color/white"
                android:textSize="18sp"
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
                android:padding="20dp">

                <FrameLayout
                    android:layout_width="44dp"
                    android:layout_height="44dp"
                    android:background="@drawable/bg_rounded_card_16"
                    android:backgroundTint="@color/purple_primary">

                    <ImageView
                        android:layout_width="22dp"
                        android:layout_height="22dp"
                        android:layout_gravity="center"
                        android:contentDescription="@null"
                        android:src="@drawable/ic_calendar_24"
                        app:tint="@color/white" />
                </FrameLayout>

                <TextView
                    android:id="@+id/tvAmount"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:textColor="@color/soft_red"
                    android:textSize="24sp"
                    android:textStyle="bold"
                    tools:text="-1250.65 USD" />

                <TextView
                    android:id="@+id/tvTitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="2dp"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp"
                    tools:text="Pharmacy Refill" />

                <LinearLayout
                    android:id="@+id/payeeRow"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="20dp"
                    android:gravity="center_vertical"
                    android:orientation="horizontal">

                    <FrameLayout
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:background="@drawable/bg_circle">

                        <TextView
                            android:id="@+id/tvPayeeInitial"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_gravity="center"
                            android:textColor="@color/white"
                            android:textStyle="bold"
                            tools:text="S" />
                    </FrameLayout>

                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="10dp"
                        android:layout_weight="1"
                        android:orientation="vertical">

                        <TextView
                            android:id="@+id/tvPayeeName"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:textColor="@color/text_primary"
                            android:textSize="14sp"
                            android:textStyle="bold"
                            tools:text="Stephen Thomas" />

                        <TextView
                            android:id="@+id/tvPayeeRole"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:textColor="@color/text_secondary"
                            android:textSize="12sp"
                            tools:text="House Owner" />
                    </LinearLayout>
                </LinearLayout>

                <TextView
                    android:id="@+id/tvScheduledFor"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="20dp"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp"
                    tools:text="Scheduled for Sep 6, 2026" />

                <TextView
                    android:id="@+id/tvCard"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp"
                    tools:text="Card **** 9324" />

            </LinearLayout>
        </androidx.cardview.widget.CardView>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:layout_marginTop="8dp"
            android:layout_marginEnd="16dp"
            android:orientation="horizontal">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnDecline"
                style="@style/Widget.Material3.Button.OutlinedButton"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginEnd="8dp"
                android:layout_weight="1"
                android:text="@string/bill_detail_decline"
                android:textColor="@color/soft_red"
                app:strokeColor="@color/soft_red" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnApprove"
                style="@style/Widget.App.Button.Primary"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_weight="1"
                android:text="@string/bill_detail_approve" />
        </LinearLayout>

    </LinearLayout>
</layout>
```

- [ ] **Step 4: Create the detail ViewModel**

```kotlin
package com.example.dailyexpensetracker.ui.billdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyexpensetracker.domain.model.Card
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.domain.usecase.DeleteTransactionUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCardsUseCase
import com.example.dailyexpensetracker.domain.usecase.GetCategoriesUseCase
import com.example.dailyexpensetracker.domain.usecase.GetTransactionByIdUseCase
import com.example.dailyexpensetracker.domain.usecase.UpdateTransactionStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class BillDetailUiState(
    val transaction: Transaction? = null,
    val category: Category? = null,
    val card: Card? = null
)

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getTransactionById: GetTransactionByIdUseCase,
    getCategories: GetCategoriesUseCase,
    getCards: GetCardsUseCase,
    private val updateTransactionStatus: UpdateTransactionStatusUseCase,
    private val deleteTransaction: DeleteTransactionUseCase
) : ViewModel() {

    private val transactionId: Long = checkNotNull(savedStateHandle.get<Long>("transactionId"))

    val uiState: StateFlow<BillDetailUiState> = combine(
        getTransactionById(transactionId), getCategories(), getCards()
    ) { transaction, categories, cards ->
        BillDetailUiState(
            transaction = transaction,
            category = transaction?.let { tx -> categories.find { it.id == tx.categoryId } },
            card = transaction?.cardId?.let { id -> cards.find { it.id == id } }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillDetailUiState())

    suspend fun approve() {
        updateTransactionStatus(transactionId, TransactionStatus.PAID)
    }

    suspend fun decline() {
        deleteTransaction(transactionId)
    }
}
```

- [ ] **Step 5: Create the detail Fragment**

```kotlin
package com.example.dailyexpensetracker.ui.billdetail

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
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.FragmentBillDetailBinding
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.common.util.DateFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BillDetailFragment : Fragment() {

    private var _binding: FragmentBillDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BillDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        binding.btnApprove.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.approve()
                findNavController().popBackStack()
            }
        }

        binding.btnDecline.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.decline()
                findNavController().popBackStack()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val tx = state.transaction ?: return@collect
                    val sign = if (tx.type == TransactionType.EXPENSE) "-" else "+"
                    binding.tvAmount.text = "$sign${CurrencyFormatter.format(tx.amount)} USD"
                    binding.tvTitle.text = tx.title
                    binding.tvScheduledFor.text = getString(
                        R.string.bill_detail_scheduled_for,
                        DateFormatter.formatForPicker(tx.date)
                    )

                    if (tx.payeeName != null) {
                        binding.payeeRow.visibility = View.VISIBLE
                        binding.tvPayeeName.text = tx.payeeName
                        binding.tvPayeeRole.text = tx.payeeRole.orEmpty()
                        binding.tvPayeeInitial.text = tx.payeeName.take(1).uppercase()
                    } else {
                        binding.payeeRow.visibility = View.GONE
                    }

                    val card = state.card
                    if (card != null) {
                        binding.tvCard.visibility = View.VISIBLE
                        binding.tvCard.text = getString(R.string.bill_detail_card, card.cardNumberMasked)
                    } else {
                        binding.tvCard.visibility = View.GONE
                    }
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

- [ ] **Step 6: Commit (build verification happens at the end of Task 6, once nav wiring provides the `transactionId` arg and makes this compile)**

```bash
git add app/src/main/res/drawable/ic_back_24.xml app/src/main/res/values/strings.xml app/src/main/res/layout/fragment_bill_detail.xml app/src/main/java/com/example/dailyexpensetracker/ui/billdetail/BillDetailViewModel.kt app/src/main/java/com/example/dailyexpensetracker/ui/billdetail/BillDetailFragment.kt
git commit -m "Add Schedule Bill Detail screen (fragment/viewmodel/layout)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Navigation wiring + full verification

**Files:**
- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Modify: `app/src/main/res/menu/bottom_nav_menu.xml`

**Interfaces:**
- Consumes: `BillsFragment`, `BillDetailFragment` classes from Tasks 4–5. Produces the `BillsFragmentDirections.actionBillsFragmentToBillDetailFragment(Long)` Safe Args function that `BillsFragment.kt` (Task 4) already calls, and the `transactionId` `SavedStateHandle` key `BillDetailViewModel.kt` (Task 5) already reads.

- [ ] **Step 1: Replace the Graph placeholder destination with Bills, add the detail destination**

In `nav_graph.xml`, replace this whole block:

```xml
    <fragment
        android:id="@+id/placeholderGraphFragment"
        android:name="com.example.dailyexpensetracker.ui.placeholder.PlaceholderFragment"
        android:label="@string/nav_graph">
        <argument
            android:name="arg_title"
            app:argType="string"
            android:defaultValue="Coming soon in the next update" />
    </fragment>
```

with:

```xml
    <fragment
        android:id="@+id/billsFragment"
        android:name="com.example.dailyexpensetracker.ui.bills.BillsFragment"
        android:label="@string/nav_bills">
        <action
            android:id="@+id/action_billsFragment_to_billDetailFragment"
            app:destination="@id/billDetailFragment" />
    </fragment>

    <fragment
        android:id="@+id/billDetailFragment"
        android:name="com.example.dailyexpensetracker.ui.billdetail.BillDetailFragment"
        android:label="@string/bill_detail_title">
        <argument
            android:name="transactionId"
            app:argType="long" />
    </fragment>
```

- [ ] **Step 2: Point the bottom nav's second tab at Bills**

In `bottom_nav_menu.xml`, replace:

```xml
    <item
        android:id="@+id/placeholderGraphFragment"
        android:icon="@drawable/ic_bar_chart_24"
        android:title="@string/nav_graph" />
```

with:

```xml
    <item
        android:id="@+id/billsFragment"
        android:icon="@drawable/ic_bills_24"
        android:title="@string/nav_bills" />
```

- [ ] **Step 3: Save both files, wait for the watcher to rebuild**

Same polling approach as Task 1 Step 6. This build compiles everything from Tasks 4–5 that depended on Safe Args generation, so it's the first point the whole feature can actually compile end to end. Expected: `SUCCESS`. If `FAILED`, read `watch_build.log`'s tail — likely causes at this point are a typo in the Safe Args action id (must exactly match `action_billsFragment_to_billDetailFragment` → generated `actionBillsFragmentToBillDetailFragment`) or a missed import.

- [ ] **Step 4: Clear app data for a fresh seed (schema changed in Task 1) and relaunch**

```bash
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
export MSYS_NO_PATHCONV=1
"$ADB" shell pm clear com.example.dailyexpensetracker
sleep 1
"$ADB" logcat -c
"$ADB" shell am start -n com.example.dailyexpensetracker/.ui.main.MainActivity
sleep 2
```

- [ ] **Step 5: Verify the Bills tab loads and shows Paid transactions**

```bash
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
export MSYS_NO_PATHCONV=1
"$ADB" shell uiautomator dump /sdcard/dump.xml
"$ADB" pull /sdcard/dump.xml ./dump.xml
grep -oE 'resource-id="com.example.dailyexpensetracker:id/billsFragment"[^>]*bounds="[^"]*"' dump.xml
```

Use the printed bounds to compute the tab's center, tap it, then screenshot:

```bash
"$ADB" shell input tap <center_x> <center_y>
sleep 1
"$ADB" shell screencap -p /sdcard/bills.png
"$ADB" pull /sdcard/bills.png ./bills.png
"$ADB" logcat -d -t 100 | grep -A 8 "FATAL EXCEPTION"
```

Expected: no `FATAL EXCEPTION` output; `bills.png` (view it) shows the purple header, 3 tabs with "Paid" selected, search box, and a list including "Medicine", "Restaurant", "Cloth Shopping", "Grocery Store", "Gas Station", "Monthly Salary", "Grocery Restock" (the 7 seeded PAID transactions).

- [ ] **Step 6: Verify tab switching and search**

```bash
"$ADB" shell uiautomator dump /sdcard/dump2.xml
"$ADB" pull /sdcard/dump2.xml ./dump2.xml
grep -oE 'text="Overdue"[^>]*bounds="[^"]*"' dump2.xml
```

Tap the Overdue tab's bounds, screenshot, confirm it shows only "Overdue Bill". Then tap the Upcoming tab, screenshot, confirm it shows only "Pharmacy Refill". Then tap the search box and type a substring of one visible title (e.g. `input text "Pharm"` while on the Upcoming tab), screenshot, confirm the list still shows the match; type a substring that matches nothing, confirm the list goes empty.

- [ ] **Step 7: Verify tapping a bill opens the detail screen with correct data**

While on the Upcoming tab, tap the "Pharmacy Refill" row, screenshot. Expected: "Schedule Bill" header, amount "-1250.65 USD" in red, title "Pharmacy Refill", payee row showing "C" avatar initial / "City Pharmacy" / "Pharmacy", a "Scheduled for <date>" line, a "Card **** 9324" line, Decline (red outline) and Approve (purple filled) buttons. No `FATAL EXCEPTION` in logcat.

- [ ] **Step 8: Verify Approve moves the bill to Paid live**

Tap Approve, screenshot. Expected: back on the Bills list (Upcoming tab), "Pharmacy Refill" no longer listed there. Switch to the Paid tab, screenshot, confirm "Pharmacy Refill" now appears there — proves `UpdateTransactionStatusUseCase` → Room → the `StateFlow` chain propagated without restarting the app.

- [ ] **Step 9: Verify Decline removes the bill**

Switch to the Overdue tab, tap "Overdue Bill" to open its detail, tap Decline, screenshot. Expected: back on the Bills list, Overdue tab now shows no rows (it was the only OVERDUE transaction) — proves `DeleteTransactionUseCase` worked live.

- [ ] **Step 10: Re-verify Home/Wallet/Categories still work (schema/nav changes didn't regress Phase 1 screens)**

```bash
ADB="C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
export MSYS_NO_PATHCONV=1
"$ADB" shell am force-stop com.example.dailyexpensetracker
"$ADB" shell am start -n com.example.dailyexpensetracker/.ui.main.MainActivity
sleep 2
"$ADB" shell screencap -p /sdcard/home_check.png
"$ADB" pull /sdcard/home_check.png ./home_check.png
"$ADB" logcat -d -t 100 | grep -A 8 "FATAL EXCEPTION"
```

Expected: Home renders with seeded data, no crash. (Wallet/Categories already share the same `TransactionRepository`/`AppDatabase` — a Home render without crash is sufficient evidence the migration and schema change didn't break the shared data layer; skip a full re-walkthrough of all 5 tabs here to avoid repeating Phase 1's verification.)

- [ ] **Step 11: Clean up screenshots/dumps from the project root**

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
rm -f *.png dump*.xml
```

- [ ] **Step 12: Commit**

```bash
git add app/src/main/res/navigation/nav_graph.xml app/src/main/res/menu/bottom_nav_menu.xml
git commit -m "Wire Bills into navigation, replacing the Graph placeholder tab

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Final checks

**Files:** none (verification only)

- [ ] **Step 1: Ask the user to run unit tests + lint**

```bash
.\gradlew testDebugUnitTest lintDebug --console=plain > verify_output.txt 2>&1; type verify_output.txt | Select-Object -Last 5
```

- [ ] **Step 2: Read the result directly (don't wait for it to be pasted back — read the file)**

```bash
cat "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker/verify_output.txt"
```

Note: PowerShell's `>` redirect writes UTF-16; if the file reads as space-separated characters, that's expected — the content is still legible. Expected: `BUILD SUCCESSFUL`, no `FAILURE` block. If lint reports new errors (not the pre-existing warning-level noise from Phase 1's pinned dependency versions), fix them the same way Phase 1's lint pass did: real errors get fixed, "newer version available" warnings for deliberately-pinned dependencies do not.

- [ ] **Step 3: Clean up and push**

```bash
cd "C:/Users/Administrator/AndroidStudioProjects/DailyExpenseTracker"
rm -f verify_output.txt test_output.txt
git status
git push
```

Confirm `git status` shows a clean tree before pushing (everything from Tasks 1–6 should already be committed).

---

## Explicitly Out of Scope (carried from the spec)

Month-scoped "Paid in July"-style tab labels, a real filter sheet behind the search bar's filter icon, editing a bill's amount/title/date after creation, recurring-bill scheduling logic (nothing currently *creates* an UPCOMING bill programmatically — same limitation Add-Transaction has for PAID transactions).
