# Handoff — Daily Expense Tracker (Qmax)

Read this first in a new session. It gets you from zero to "ready to implement" fast.

## Where things stand

- **Repo:** `C:\Users\Administrator\AndroidStudioProjects\DailyExpenseTracker`, GitHub remote `origin` → `https://github.com/TanerKececi/DailyExpenseTracker.git`, branch `master`.
- **Pushed to GitHub:** Phase 1 (full app foundation + Home/Wallet/Categories/Add-Transaction) and a lint-fix follow-up.
- **Committed locally, not yet pushed:** the Bills sub-project spec (`docs/superpowers/specs/2026-09-03-bills-design.md`) and its implementation plan (`docs/superpowers/plans/2026-09-03-bills-implementation.md`). Run `git status`/`git log` to confirm current state — check whether these got pushed before you start; if `git status` shows "ahead of origin/master" by these 2 commits, push them first (ask the user, don't push silently).
- **Not yet started:** actual implementation of the Bills plan. **This is the next action** — see "Resuming work" below.

## What's built (Phase 1 — shipped)

Kotlin, MVVM + Clean Architecture (data/domain/ui), Room (KSP) + Hilt (KSP), Jetpack Navigation Component + BottomNavigationView, DataBinding + ViewBinding. Screens: Home, My Wallet, Categories, Add-Transaction (bottom sheet). Bottom nav: Home / Graph(placeholder) / Wallet / Categories / Settings(placeholder). Verified end-to-end on an emulator, `testDebugUnitTest` + `lintDebug` clean.

## Phase 2 plan (in progress)

Phase 2 = the screens Phase 1 deferred: Bills+Detail, Calendar, 3 chart screens, Auth. Decomposed into 4 independent sub-projects during brainstorming; **only the first (Bills) has been speced and planned so far** — Calendar, Charts, and Auth haven't been brainstormed yet.

**Bills sub-project** (spec: `docs/superpowers/specs/2026-09-03-bills-design.md`, plan: `docs/superpowers/plans/2026-09-03-bills-implementation.md`):
- Bills list (Paid/Overdue/Upcoming tabs + live search) replaces the inert Graph bottom-nav tab.
- Schedule Bill Detail screen (payee info, Approve→marks Paid, Decline→deletes).
- Adds nullable `payeeName`/`payeeRole` to the existing `Transaction`, bumps Room to v2 with a destructive migration (no real users yet, so this is fine).
- 7 tasks, each ending in a commit. Full code already written into the plan — no design decisions left, just execution.

### Resuming work

1. Read `docs/superpowers/plans/2026-09-03-bills-implementation.md` in full.
2. Ask the user which execution mode they want: **subagent-driven** (a fresh subagent per task, review between tasks — recommended) or **inline** (execute tasks directly in-session). This question was asked and not yet answered when this handoff was written.
3. Invoke the matching skill: `superpowers:subagent-driven-development` or `superpowers:executing-plans`.

After Bills ships (built, verified, tested, linted, committed, pushed), the remaining Phase 2 sub-projects — Calendar, the charts trio (Expense Chart/Budget Planner/Billing Reports), Auth — still need their own brainstorming pass each (`superpowers:brainstorming`) before planning. None of their designs exist yet; don't assume anything about them beyond what's in the original PRD the user provided at the start of this project.

## Environment setup — do this before touching code

**You (Claude) cannot run Gradle directly in this sandbox** — every invocation of `gradlew` fails with `Unable to establish loopback connection`, even with elevated sandbox settings. This is a hard limitation, not a permission prompt; don't waste turns retrying it. Full detail in project memory (`dailyexpensetracker-watch-build-workflow`), summary here:

1. **Check if `watch_build.ps1` already exists** at the repo root (`ls watch_build.ps1`). It's gitignored (dev-only, not part of the app), so a fresh clone won't have it — but this is the same machine/directory as before, so it's probably still there.
   - If it exists: ask the user to run `powershell -ExecutionPolicy Bypass -File watch_build.ps1` in their own terminal once, then leave it running. It polls `app\src`, `gradle\`, and the root/app `build.gradle.kts`/`gradle.properties` every ~3s and auto-runs `gradlew installDebug` on change, writing `SUCCESS`/`FAILED <timestamp>` to `watch_build_status.txt` and full output to `watch_build.log`. You (Claude) poll `watch_build_status.txt` via `Read`/`Bash` — never ask the user to paste build output back, read the file yourself.
   - If it's missing, recreate it — full script content is in git history (`git log --all --oneline -- watch_build.ps1` won't find it since it's gitignored and never committed; instead see the version embedded in this session's transcript, or reconstruct: a `while ($true)` loop hashing `LastWriteTime.Ticks` across the watched paths, running `.\gradlew.bat installDebug --console=plain` on change, writing status/log files as described above).
2. `gradle.properties` already pins `org.gradle.java.home` to Android Studio's bundled JDK 21 and sets `ksp.useKSP2=false` — required, don't remove. See `dailyexpensetracker-toolchain-versions` memory for why (JDK 26 on PATH, Kotlin/KSP/Hilt version compatibility chain).
3. For runtime verification, drive the emulator via `adb` directly (`C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe`) — this works fine from the sandbox, only Gradle/JVM-fork tooling is blocked. Use `uiautomator dump` for exact tap coordinates rather than guessing from screenshots — coordinates shift when the keyboard opens, and guessed taps have caused real mis-clicks in this project before.

## Known gotchas (read before writing new layouts/adapters)

- **Never name a child view `android:id="@+id/root"`** in any layout used with ViewBinding/DataBinding. It collides with the generated synthetic `binding.root` property and causes a very confusing `RecyclerView` crash (`ViewHolder views must not be attached when created`) that looks like a Fragment/Navigation timing bug but isn't. Burned a long debugging session on this in Phase 1. Full writeup: project memory `viewbinding-root-id-collision`.
- RecyclerView adapters in this codebase are plain `RecyclerView.Adapter` with `submitList()` + `notifyDataSetChanged()`, not `ListAdapter`/`DiffUtil` — match this pattern.
- `app:tint` (not `android:tint`) on ImageViews per AppCompat lint rules — and remember to declare `xmlns:app` on the layout root if it's not already there.
- Claude's project memory files (`viewbinding-root-id-collision`, `dailyexpensetracker-toolchain-versions`, `dailyexpensetracker-watch-build-workflow`) should auto-load as background context in a new session for this project — if they don't seem to be present, read them directly from `C:\Users\Administrator\.claude\projects\C--Users-Administrator-AndroidStudioProjects-DailyExpenseTracker\memory\`.

## Verification checklist (matches Phase 1's process, reuse it)

1. Watcher-driven `installDebug` after every save.
2. adb-driven walkthrough with screenshots + `logcat | grep "FATAL EXCEPTION"` after each meaningful change.
3. `.\gradlew testDebugUnitTest lintDebug` once at the end of the plan (needs the user's terminal — ask them to run it, then read the output file yourself rather than asking them to paste it).
4. Clean up scratch screenshots/dumps from the repo root before committing.
5. Commit locally per the plan's task boundaries; push only after explicit user confirmation.
