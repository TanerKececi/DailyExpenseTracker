package com.example.dailyexpensetracker.ui.addtransaction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.FragmentAddTransactionBinding
import com.example.dailyexpensetracker.domain.model.Transaction
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.DateFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddTransactionSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAddTransactionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AddTransactionViewModel by viewModels()

    private val categoryAdapter = CategoryPickerAdapter { category -> viewModel.setCategory(category.id) }

    /** Guards against re-populating the fields over the user's own typing on later emissions. */
    private var prefilled = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCategoryPicker.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvCategoryPicker.adapter = categoryAdapter

        if (viewModel.isEditing) {
            binding.tvSheetTitle.setText(R.string.edit_transaction_title)
            binding.btnDelete.visibility = View.VISIBLE
        }

        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                viewModel.setType(if (checkedId == binding.btnExpense.id) TransactionType.EXPENSE else TransactionType.INCOME)
            }
        }

        binding.tvDate.setOnClickListener { showDatePicker() }

        binding.btnSave.setOnClickListener {
            viewModel.save(
                binding.etTitle.text?.toString().orEmpty(),
                binding.etAmount.text?.toString().orEmpty()
            )
        }

        binding.btnDelete.setOnClickListener { confirmDelete() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { categories ->
                        categoryAdapter.submitList(categories)
                        // Re-apply from the ViewModel rather than clearing: in edit mode the
                        // selection is already set before the category list arrives.
                        val selectedId = viewModel.selectedCategoryId.value
                        categoryAdapter.setSelectedId(selectedId)
                        scrollToSelected(selectedId)
                    }
                }
                launch {
                    viewModel.selectedCategoryId.collect { id ->
                        categoryAdapter.setSelectedId(id)
                        // Also scroll here, not just in the categories collector: in edit mode the
                        // category list usually arrives first and the selection lands afterwards,
                        // once the transaction has loaded.
                        scrollToSelected(id)
                    }
                }
                launch {
                    viewModel.original.collect { transaction ->
                        if (transaction != null && !prefilled) {
                            prefilled = true
                            prefill(transaction)
                        }
                    }
                }
                launch {
                    viewModel.dateMillis.collect { binding.tvDate.text = DateFormatter.formatForPicker(it) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AddTransactionEvent.Saved -> dismiss()
                            is AddTransactionEvent.Error -> Toast.makeText(requireContext(), event.messageRes, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * The picker scrolls horizontally, so a selection further along the list sits off-screen and
     * an edit looks like no category was chosen.
     */
    private fun scrollToSelected(categoryId: Long?) {
        if (categoryId == null) return
        val index = viewModel.categories.value.indexOfFirst { it.id == categoryId }
        if (index >= 0) binding.rvCategoryPicker.scrollToPosition(index)
    }

    private fun prefill(transaction: Transaction) {
        binding.etTitle.setText(transaction.title)
        binding.etAmount.setText(transaction.amount.toString())
        binding.toggleType.check(
            if (transaction.type == TransactionType.EXPENSE) binding.btnExpense.id else binding.btnIncome.id
        )
        // Must follow the toggle: checking it fires the listener, and setType clears the category.
        viewModel.setCategory(transaction.categoryId)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.edit_transaction_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete() }
            .show()
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(viewModel.dateMillis.value)
            .build()
        picker.addOnPositiveButtonClickListener { selection -> viewModel.setDate(selection) }
        picker.show(childFragmentManager, "date_picker")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
