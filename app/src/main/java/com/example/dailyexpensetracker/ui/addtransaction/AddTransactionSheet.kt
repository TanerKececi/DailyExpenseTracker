package com.example.dailyexpensetracker.ui.addtransaction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.databinding.FragmentAddTransactionBinding
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect {
                        categoryAdapter.setSelectedId(null)
                        categoryAdapter.submitList(it)
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
