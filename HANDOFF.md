# Handoff — Daily Expense Tracker (Qmax)

Read this first in a new session. It gets you from zero to "ready to implement" fast.

## Where things stand

- **Repo:** `C:\Users\Administrator\AndroidStudioProjects\DailyExpenseTracker`, GitHub remote `origin` → `https://github.com/TanerKececi/DailyExpenseTracker.git`, default branch `master`.
- **Shipped:** Phase 1 (app foundation + Home/Wallet/Categories/Add-Transaction), all of Phase 2 — **Bills** ([PR #1](https://github.com/TanerKececi/DailyExpenseTracker/pull/1)), **Calendar** ([PR #3](https://github.com/TanerKececi/DailyExpenseTracker/pull/3)), **Reports** ([PR #5](https://github.com/TanerKececi/DailyExpenseTracker/pull/5)) — and two follow-on features: **edit/delete transactions** ([PR #7](https://github.com/TanerKececi/DailyExpenseTracker/pull/7)) and **creating scheduled bills** ([PR #8](https://github.com/TanerKececi/DailyExpenseTracker/pull/8)). All merged 2026-09-04.
- **Phase 2 is complete.** Auth was the planned fourth sub-project; the user **dropped it from scope entirely on 2026-09-04** — do not propose it again.
- **Next action:** none outstanding. See "What's left" below for the open threads, none of which are committed work.

**Branch policy (user's standing instruction, 2026-09-03): do not push directly to `master`.** Work on a feature branch, push that, open a PR, merge. Ask before pushing or merging.

## What's built

Kotlin, MVVM + Clean Architecture (data/domain/ui), Room (KSP) + Hilt (KSP), Jetpack Navigation Component with Safe Args + BottomNavigationView, DataBinding + ViewBinding.

Screens: Home, My Wallet, Categories, Add-Transaction (bottom sheet), **Bills** (Paid/Overdue/Upcoming tabs + live search), **Schedule Bill Detail** (Approve marks Paid, Decline deletes), **Calendar** (month grid with per-day expense/income dots and a day-detail list), **Reports** (three tabs: Expense Chart donut, Budget Planner with editable limits, Billing Reports six-month bars, sharing one month selector).

Bottom nav: Home / Bills / Wallet / Categories / **Reports**. Calendar opens from an icon in Home's header, since the nav's five slots are full.

The Add-Transaction sheet is also the **editor**: tapping a row in Wallet or in the Calendar day list reopens it prefilled, with a Delete button behind a confirmation. It carries the loaded transaction's `cardId`, `isScheduled` and `status` through on save — do not "simplify" that away, or editing a bill silently converts it into an ordinary paid transaction. A **"Scheduled bill"** switch creates `UPCOMING` transactions with optional payee name/role.

`Transaction` carries nullable `payeeName`/`payeeRole`; Room is at **v2** with `fallbackToDestructiveMigration()` (no real users yet, so a schema change wipes and reseeds — that is intended). `Category.budgetLimit` is writable from Budget Planner.

**Overdue is derived, never stored.** `ui/bills/BillBuckets` treats any scheduled bill whose due date has passed as overdue, so bills move tabs on their own with no background job. A bill due *today* is not overdue. Don't reintroduce a stored-status approach — it goes stale the moment a due date passes.

Verified end-to-end on the emulator. `testDebugUnitTest` **50/50 pass** across 8 suites, `lintDebug` **0 errors** / 54 warnings (all benign categories — `SetTextI18n`, `GradleDependency`, `NotifyDataSetChanged`, `UseCompoundDrawables`, `UseKtx`, etc. Do not "fix" the pinned-dependency version warnings).

Still **no third-party dependencies beyond Phase 1's**. The charts trio was the work most likely to break that, and it didn't: both charts are hand-drawn `View` subclasses in `ui/common/view/` whose geometry lives in pure, tested companion functions.

## What's left

Phase 2 was originally decomposed into 4 sub-projects. Three shipped; **Auth was dropped from scope by the user on 2026-09-04.** There is no committed work outstanding.

Two gaps this file used to list are now **closed** — edit/delete of transactions (#7) and creating scheduled bills (#8). Don't rebuild them.

Open threads, in the order they'd most likely matter — none of these has been agreed, so **ask before starting any of them**:

- **11 ViewModels, 0 direct tests.** This is the live thread and the one the user named as the scaling concern. All 50 tests cover use cases and pure objects; nothing tests a ViewModel. Every bug that shipped during Phase 2 lived in exactly that layer — the Bills `TabLayout` desyncing from its retained ViewModel, the category-selection race in the edit sheet, the field-clobbering trap on save — and every one was caught by a human looking at a screenshot, which does not scale.
  - **Cheap route, no new dependency:** keep extracting decision logic into pure objects and test those. `ui/bills/BillBuckets`, `ui/reports/billing/MonthlyTotals`, `ui/common/util/MonthRange` and `ui/calendar/CalendarMonth` are the existing examples of the pattern.
  - **Thorough route:** test the ViewModels directly by asserting on their `StateFlow`s. Catches the wiring bugs the pure-function route cannot, but needs **`kotlinx-coroutines-test`** — which would be this project's *first* new dependency after four sub-projects deliberately held that line. **That is the user's decision, not Claude's — ask, don't assume.**
- **Settings has no home.** Reports took its bottom-nav slot and `PlaceholderFragment` was deleted with it. A real Settings screen would need a new entry point — most likely a second icon in Home's header beside Calendar's — and, more to the point, a decision about what actually goes in it.
- **The seeded database is the only data source.** There is no import, no export and no backup, and a schema change wipes everything by design.
- **9 adapters call `notifyDataSetChanged()`**, rebinding everything on any change. Invisible at current data volumes and *not* worth pre-emptively fixing; it would start to matter in the hundreds of rows. Data access itself is in good shape — every transaction query is bounded by date range or status, and nothing loads transactions unbounded.
- Smaller deferrals live in each spec's "Explicitly Out of Scope" section — chart interaction, custom date ranges, report export, locale-aware week start.

### If a new sub-project is agreed

1. `superpowers:brainstorming` → design doc in `docs/superpowers/specs/`.
2. `superpowers:writing-plans` → plan in `docs/superpowers/plans/`.
3. Ask the user for execution mode: **inline** (what all three shipped sub-projects used, and it worked well since the plans carried all their code) or **subagent-driven**. Then `superpowers:executing-plans` or `superpowers:subagent-driven-development`.

Existing specs and plans are good templates — [Bills](docs/superpowers/specs/2026-09-03-bills-design.md) / [plan](docs/superpowers/plans/2026-09-03-bills-implementation.md), [Calendar](docs/superpowers/specs/2026-09-04-calendar-design.md) / [plan](docs/superpowers/plans/2026-09-04-calendar-implementation.md), [Reports](docs/superpowers/specs/2026-09-04-reports-design.md) / [plan](docs/superpowers/plans/2026-09-04-reports-implementation.md). The Calendar and Reports plans are the better models: every task compiles on its own, so each gets a real build check, whereas three of Bills' tasks only compiled as a set and had to be written blind.

Note the ordering trick Reports used: the three tab fragments were built **before** the host that instantiates them, so no task ever referenced code that didn't exist yet.

## Environment setup — do this before touching code

**You (Claude) cannot run Gradle in this environment at all.** Every `gradlew` invocation fails with `java.io.IOException: Unable to establish loopback connection` — including with `dangerouslyDisableSandbox: true` (confirmed again 2026-09-04). It is a machine-level restriction on Claude's process tree, not a permission prompt. Don't retry it. Full detail in project memory `dailyexpensetracker-watch-build-workflow`.

1. **`watch_build.ps1`** at the repo root (gitignored, dev-only) auto-builds on save. Ask the user to run it once in their own terminal and leave it running:
   ```
   powershell -ExecutionPolicy Bypass -File watch_build.ps1
   ```
   It polls `app\src`, `gradle\`, and the root/app `build.gradle.kts`/`gradle.properties` every ~3s, debounces 2s, then runs `gradlew installDebug`, writing `BUILDING`/`SUCCESS <ts>`/`FAILED <ts>` to `watch_build_status.txt` and full output to `watch_build.log`. **You poll `watch_build_status.txt` yourself** — never ask the user to paste build output.
2. **The watcher does NOT run tests or lint.** `installDebug` never compiles the `test` source set, so a broken or non-compiling unit test still reports `SUCCESS`. `testDebugUnitTest` and `lintDebug` only ever run in the user's terminal. Ask **once**, at the end, and read the output file yourself:
   ```
   ./gradlew testDebugUnitTest lintDebug --console=plain > verify_output.txt 2>&1
   ```
   PowerShell's `>` writes UTF-16 — decode it (`iconv -f UTF-16`), or read the reports directly, which is more reliable: `app/build/test-results/testDebugUnitTest/*.xml` for pass/fail counts and `app/build/reports/lint-results-debug.txt` for the error/warning tally. Note that a re-run shows every task `UP-TO-DATE`, which is *not* evidence tests ran — check the reports' timestamps.
3. **Tell the user not to stop the watcher to run that command** (use a second terminal). Ctrl+C'ing it silently swallowed a rebuild in the last session and nearly shipped an unverified change.
4. `gradle.properties` pins `org.gradle.java.home` to Android Studio's bundled JDK 21 and sets `ksp.useKSP2=false` — required, don't remove. See memory `dailyexpensetracker-toolchain-versions`.
5. Drive the emulator via `adb` directly (`C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe`) — this works fine. Use `uiautomator dump` for exact tap coordinates rather than guessing from screenshots; coordinates shift when the keyboard opens. `adb shell sleep N` works for waits (the Bash tool blocks foreground `sleep`).

### Polling the watcher correctly

Reading `watch_build_status.txt` right after a batch of edits can catch a **mid-write build** — the watcher may have started before your last file landed. Wait for a `SUCCESS`/`FAILED`, then re-read ~10s later and only trust it if unchanged; if it moved back to `BUILDING`, keep waiting. Run the poll loop as a background Bash task and let the completion notification wake you.

**The watcher can silently swallow an edit made while a build is running.** `watch_build.ps1` recomputes `$lastHash` *after* the build finishes, so a file written mid-build is folded into the new baseline and never triggers a rebuild — the settle-check above won't catch this, because the status genuinely is stable. The symptom is a compile error naming something you know you just fixed, often with a code-generating task reporting `UP-TO-DATE`. It cost a debugging detour during Calendar: a `nav_graph.xml` edit was swallowed, `generateSafeArgsDebug` stayed `UP-TO-DATE`, and the build failed on `Unresolved reference 'HomeFragmentDirections'`. **Remedy:** `touch` the files in question to force a fresh cycle. If a failure blames a file whose content on disk is provably correct, suspect this before debugging the code.

**`watch_build.log` is append-only.** Grepping the whole file for `error:` surfaces *historical* failures from earlier sessions and reports them as if they were current — during Calendar it produced a confusing wall of `string/nav_graph` errors left over from the Bills work. Always slice from the last build marker first:

```bash
LAST=$(grep -n "=== Build triggered at" watch_build.log | tail -1 | cut -d: -f1)
tail -n +"$LAST" watch_build.log | tr -d '\r' | sed -n '1,90p'
```

Read that slice raw rather than grepping it — the log wraps at the PowerShell console width, so error messages are split mid-word and a `grep` for the interesting part often misses the line that carries it.

## Known gotchas (read before writing new layouts/adapters)

- **Never name a child view `android:id="@+id/root"`** in any layout used with ViewBinding/DataBinding. It collides with the generated `binding.root` and causes a confusing `RecyclerView` crash (`ViewHolder views must not be attached when created`). Memory: `viewbinding-root-id-collision`.
- **A ViewModel outlives its fragment's view across navigation.** Navigating to a detail screen and back gives you a *fresh view* with a *retained ViewModel* — any widget with its own selection state (TabLayout, spinner) resets to index 0 while the ViewModel still holds the old value, and re-tapping the already-selected item fires no callback. Re-sync the widget from ViewModel state in `onViewCreated`. This bit Bills; see `BillsFragment.tabStatuses`.
- **Material3 `TabLayout` paints its own surface background.** On a colored header it hides a white selected-tab label. Set `android:background="@android:color/transparent"`.
- **Never paint a foreground element in `@color/background_light_gray`.** It is the app's own screen background, so anything drawn in it is invisible. This has now shipped as a bug three separate times — Bills' selected tab label, the Calendar divider, and Budget Planner's progress tracks. Use `@color/divider_light` for hairlines and empty track fills. None of these were caught by a build or a test; only by looking at a screenshot.
- **Canvas text needs its descender band reserved.** `drawText` with the baseline at a view's bottom edge clips the tails off "p", "g", "y" — `BarChartView`'s month labels lost theirs. Offset by `Paint.fontMetrics.descent` and reserve `descent - ascent` for the label strip.
- RecyclerView adapters here are plain `RecyclerView.Adapter` with `submitList()` + `notifyDataSetChanged()`, not `ListAdapter`/`DiffUtil` — match this. `RecentTransactionAdapter` takes an optional row-click callback and is reused by both Wallet and Bills; prefer extending it over writing a near-duplicate adapter.
- `app:tint` (not `android:tint`) on ImageViews per AppCompat lint — declare `xmlns:app` on the layout root if absent.
- For a non-autofillable text field (e.g. search), use `android:importantForAutofill="no"`, not a bogus `autofillHints` value.
- Writing Kotlin/XML via Bash heredocs has repeatedly tripped the shell parser. Use the Write tool for source files.
- Project memory files (`viewbinding-root-id-collision`, `dailyexpensetracker-toolchain-versions`, `dailyexpensetracker-watch-build-workflow`) should auto-load in a new session; if not, read them from `C:\Users\Administrator\.claude\projects\C--Users-Administrator-AndroidStudioProjects-DailyExpenseTracker\memory\`.

## Verification checklist (reuse it)

1. Watcher-driven `installDebug` after every save; poll with the settle-check above.
2. adb-driven walkthrough with screenshots + `logcat -d | grep "FATAL EXCEPTION"` after each meaningful change. Screenshots and `uiautomator` dumps go in the scratchpad, **not** the repo root.
3. `testDebugUnitTest lintDebug` once at the end — one request to the user, then read the reports yourself.
4. Commit per task boundary. Push the feature branch and open a PR only after explicit user confirmation.
