package com.example.dailyexpensetracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for an unconfined test dispatcher. Any ViewModel that touches
 * `viewModelScope` needs this — both `stateIn(viewModelScope, ...)` and `viewModelScope.launch`
 * dispatch to `Main.immediate`, which throws outright on a plain JVM test.
 *
 * Unconfined rather than standard so ViewModel work runs eagerly on the calling thread: a
 * setter's effect is visible on the very next line, with no scheduler to advance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/**
 * Keeps [flow] collected for the rest of the test. Every ViewModel here shares its state with
 * `SharingStarted.WhileSubscribed`, which sits on the initial value forever unless something
 * subscribes — so assertions run without this pass against an empty state and prove nothing.
 *
 * Collects on `Dispatchers.Main`, i.e. the rule's dispatcher, so there is one dispatcher in play.
 */
fun TestScope.subscribe(flow: StateFlow<*>) {
    backgroundScope.launch(Dispatchers.Main) { flow.collect {} }
}
