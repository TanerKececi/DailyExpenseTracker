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
        // branches installs the fragment; replace() also removes any child the FragmentManager
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
