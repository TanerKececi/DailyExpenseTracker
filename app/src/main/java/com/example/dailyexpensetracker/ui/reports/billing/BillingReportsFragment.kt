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
