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
