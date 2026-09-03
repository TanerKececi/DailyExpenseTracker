# Handoff — Daily Expense Tracker (Qmax)

Read this first in a new session. It gets you from zero to "ready to implement" fast.

## Where things stand

- **Repo:** `C:\Users\Administrator\AndroidStudioProjects\DailyExpenseTracker`, GitHub remote `origin` → `https://github.com/TanerKececi/DailyExpenseTracker.git`, default branch `master`.
- **Shipped:** Phase 1 (app foundation + Home/Wallet/Categories/Add-Transaction), and Phase 2's **Bills** ([PR #1](https://github.com/TanerKececi/DailyExpenseTracker/pull/1)) and **Calendar** ([PR #3](https://github.com/TanerKececi/DailyExpenseTracker/pull/3)) sub-projects, both merged 2026-09-04.
- **Next action:** brainstorm the next Phase 2 sub-project — see "Resuming work".

**Branch policy (user's standing instruction, 2026-09-03): do not push directly to `master`.** Work on a feature branch, push that, open a PR, merge. Ask before pushing or merging.

## What's built

Kotlin, MVVM + Clean Architecture (data/domain/ui), Room (KSP) + Hilt (KSP), Jetpack Navigation Component with Safe Args + BottomNavigationView, DataBinding + ViewBinding.

Screens: Home, My Wallet, Categories, Add-Transaction (bottom sheet), **Bills** (Paid/Overdue/Upcoming tabs + live search), **Schedule Bill Detail** (Approve marks Paid, Decline deletes), **Calendar** (month grid with per-day expense/income dots and a day-detail list). Bottom nav: Home / Bills / Wallet / Categories / Settings(placeholder); Calendar opens from an icon in Home's header, since the nav's five slots are full.

`Transaction` carries nullable `payeeName`/`payeeRole`; Room is at **v2** with `fallbackToDestructiveMigration()` (no real users yet, so a schema change wipes and reseeds — that is intended).

Verified end-to-end on the emulator. `testDebugUnitTest` **18/18 pass**, `lintDebug` **0 errors** / 47 warnings (all pre-existing categories — `SetTextI18n`, `GradleDependency`, `NotifyDataSetChanged`, `UseCompoundDrawables`, etc. Do not "fix" the pinned-dependency version warnings).

Still **no third-party dependencies beyond Phase 1's** — both sub-projects held that line. The charts trio is the first work likely to challenge it.

## Phase 2 — remaining work

Phase 2 was decomposed into 4 independent sub-projects during brainstorming. **Bills and Calendar are done.** The remaining two have **no design or plan yet** — each needs its own `superpowers:brainstorming` pass before planning:

- **Charts trio** — Expense Chart, Budget Planner, Billing Reports. Expect a real decision here: hand-drawn chart views versus accepting the project's first new dependency.
- **Auth** — needs the user to decide what auth even means here, since there is no backend (a local-only gate versus a real service).

Don't assume anything about their scope beyond the original PRD the user provided at the start of the project.

### Resuming work

1. Ask the user which sub-project to take next (or confirm the order above).
2. `superpowers:brainstorming` → design doc in `docs/superpowers/specs/`.
3. `superpowers:writing-plans` → plan in `docs/superpowers/plans/`.
4. Ask the user for execution mode: **inline** (execute in-session — what Bills used, and it worked well since the plan carried all the code) or **subagent-driven**. Then `superpowers:executing-plans` or `superpowers:subagent-driven-development`.

Existing specs and plans are good templates — [Bills spec](docs/superpowers/specs/2026-09-03-bills-design.md) / [plan](docs/superpowers/plans/2026-09-03-bills-implementation.md), [Calendar spec](docs/superpowers/specs/2026-09-04-calendar-design.md) / [plan](docs/superpowers/plans/2026-09-04-calendar-implementation.md). The Calendar plan is the better model: every task compiles on its own, so each gets a real build check, whereas three of Bills' tasks only compiled as a set and had to be written blind.

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
