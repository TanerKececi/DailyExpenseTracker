package com.example.dailyexpensetracker.ui.categorydetail

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
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.FragmentCategoryDetailBinding
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.wallet.adapter.RecentTransactionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoryDetailFragment : Fragment() {

    private var _binding: FragmentCategoryDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CategoryDetailViewModel by viewModels()

    private val adapter = RecentTransactionAdapter { item ->
        findNavController().navigate(
            CategoryDetailFragmentDirections.actionCategoryDetailFragmentToAddTransactionSheet(
                item.transaction.id
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }
        binding.ivPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.ivNextMonth.setOnClickListener { viewModel.nextMonth() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvCategoryName.text = state.category?.name.orEmpty()
                    state.category?.let {
                        binding.ivCategoryIcon.setImageResource(CategoryIconMapper.iconFor(it.iconName))
                    }
                    binding.tvMonthLabel.text = state.monthLabel
                    binding.tvTotal.text = CurrencyFormatter.format(state.total)

                    // Income categories carry no limit, so show nothing rather than "of 0.00".
                    val limit = state.budgetLimit
                    if (limit == null) {
                        binding.tvBudget.visibility = View.GONE
                    } else {
                        binding.tvBudget.visibility = View.VISIBLE
                        binding.tvBudget.text = getString(
                            R.string.category_detail_of_budget,
                            CurrencyFormatter.format(limit)
                        )
                    }

                    adapter.submitList(state.items)
                    binding.tvEmpty.visibility =
                        if (state.items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
