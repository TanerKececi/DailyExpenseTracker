package com.example.dailyexpensetracker.ui.reports.budget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.DialogEditBudgetBinding
import com.example.dailyexpensetracker.databinding.FragmentBudgetPlannerBinding
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.ui.reports.ReportsViewModel
import com.example.dailyexpensetracker.ui.reports.budget.adapter.BudgetPlanAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class BudgetPlannerFragment : Fragment() {

    private var _binding: FragmentBudgetPlannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BudgetPlannerViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private val adapter = BudgetPlanAdapter { showEditDialog(it) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetPlannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvBudgets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBudgets.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                reportsViewModel.selectedMonthStart
                    .flatMapLatest { viewModel.plansFor(it) }
                    .collect { adapter.submitList(it) }
            }
        }
    }

    private fun showEditDialog(item: CategorySpend) {
        val dialogBinding = DialogEditBudgetBinding.inflate(layoutInflater)
        dialogBinding.etBudget.setText(item.category.budgetLimit?.toString().orEmpty())

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.reports_edit_budget_title))
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.reports_cancel, null)
            .setPositiveButton(R.string.reports_save) { _, _ ->
                // Empty or unparseable input clears the budget rather than writing a bogus number.
                val limit = dialogBinding.etBudget.text?.toString()?.trim()?.toDoubleOrNull()
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.setBudget(item.category.id, limit)
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
