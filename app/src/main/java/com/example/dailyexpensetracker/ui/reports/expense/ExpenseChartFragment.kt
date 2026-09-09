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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.databinding.FragmentExpenseChartBinding
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.common.view.DonutChartView
import com.example.dailyexpensetracker.ui.reports.ReportsFragmentDirections
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

    // findNavController() walks up to the host NavController, and the current destination is
    // reportsFragment — this fragment is a child added via childFragmentManager, not a destination.
    private val legendAdapter = LegendAdapter { category ->
        findNavController().navigate(
            ReportsFragmentDirections.actionReportsFragmentToCategoryDetailFragment(category.id)
        )
    }

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
