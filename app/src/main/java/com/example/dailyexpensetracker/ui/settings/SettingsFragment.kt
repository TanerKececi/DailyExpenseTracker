package com.example.dailyexpensetracker.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivBack.setOnClickListener { findNavController().navigateUp() }
        binding.rowCurrency.setOnClickListener { showCurrencyDialog() }
        binding.rowWeekStart.setOnClickListener { showWeekStartDialog() }
        binding.rowResetData.setOnClickListener { showResetDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvCurrencyValue.text = state.currencySymbol
                    binding.tvWeekStartValue.setText(weekStartLabel(state.weekStart))
                }
            }
        }
    }

    private fun weekStartLabel(weekStart: Int) =
        if (weekStart == Calendar.MONDAY) R.string.settings_week_start_monday
        else R.string.settings_week_start_sunday

    private fun showCurrencyDialog() {
        val symbols = SettingsStore.SUPPORTED_SYMBOLS
        val checked = symbols.indexOf(viewModel.uiState.value.currencySymbol).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_currency)
            .setSingleChoiceItems(symbols.toTypedArray(), checked) { dialog, which ->
                viewModel.setCurrencySymbol(symbols[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showWeekStartDialog() {
        val labels = arrayOf(
            getString(R.string.settings_week_start_sunday),
            getString(R.string.settings_week_start_monday)
        )
        val values = intArrayOf(Calendar.SUNDAY, Calendar.MONDAY)
        val checked = values.indexOf(viewModel.uiState.value.weekStart).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_week_start)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setWeekStart(values[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showResetDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_reset_confirm_title)
            .setMessage(R.string.settings_reset_confirm_message)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_reset_confirm_action) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.resetData()
                    Toast.makeText(requireContext(), R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
