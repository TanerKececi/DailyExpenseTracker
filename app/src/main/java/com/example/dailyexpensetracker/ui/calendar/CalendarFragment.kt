package com.example.dailyexpensetracker.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.FragmentCalendarBinding
import com.example.dailyexpensetracker.ui.calendar.adapter.CalendarDayAdapter
import com.example.dailyexpensetracker.ui.settings.SettingsStore
import com.example.dailyexpensetracker.ui.wallet.adapter.RecentTransactionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()

    @Inject
    lateinit var settingsStore: SettingsStore

    private val dayAdapter = CalendarDayAdapter { dayMillis -> viewModel.selectDay(dayMillis) }

    private val transactionAdapter = RecentTransactionAdapter { item ->
        findNavController().navigate(
            CalendarFragmentDirections.actionCalendarFragmentToAddTransactionSheet(item.transaction.id)
        )
    }

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

        bindWeekdayHeader()

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

    /**
     * The seven header labels are fixed in XML in Sunday-first order. Rotating them here keeps the
     * header aligned with the grid — if they ever disagree, the calendar looks like an off-by-one
     * in CalendarMonth's blank count, which sends you debugging the wrong file.
     */
    private fun bindWeekdayHeader() {
        val sundayFirst = listOf(
            R.string.calendar_day_sun,
            R.string.calendar_day_mon,
            R.string.calendar_day_tue,
            R.string.calendar_day_wed,
            R.string.calendar_day_thu,
            R.string.calendar_day_fri,
            R.string.calendar_day_sat
        )
        val offset = settingsStore.weekStart - Calendar.SUNDAY
        for (column in 0 until 7) {
            val label = binding.llWeekdayHeader.getChildAt(column) as TextView
            label.setText(sundayFirst[(offset + column) % 7])
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
