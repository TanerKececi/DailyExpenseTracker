package com.example.dailyexpensetracker.ui.home

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
import com.example.dailyexpensetracker.databinding.FragmentHomeBinding
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.home.adapter.MonthlyBudgetAdapter
import com.example.dailyexpensetracker.ui.home.adapter.TopSpendingAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private val topSpendingAdapter = TopSpendingAdapter()
    private val monthlyBudgetAdapter = MonthlyBudgetAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvTopSpending.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTopSpending.adapter = topSpendingAdapter

        binding.rvMonthlyBudget.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMonthlyBudget.adapter = monthlyBudgetAdapter

        binding.ivCalendar.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToCalendarFragment())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvTodayBalance.text = CurrencyFormatter.format(state.todayNet)
                    binding.tvEarnedAmount.text = CurrencyFormatter.format(state.earned)
                    binding.tvSpendAmount.text = CurrencyFormatter.format(state.spent)
                    val total = (state.earned + state.spent).takeIf { it > 0 } ?: 1.0
                    binding.progressEarned.progress = (state.earned / total * 100).toInt()
                    binding.progressSpend.progress = (state.spent / total * 100).toInt()
                    topSpendingAdapter.submitList(state.topSpending)
                    monthlyBudgetAdapter.submitList(state.monthlyBudget)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
