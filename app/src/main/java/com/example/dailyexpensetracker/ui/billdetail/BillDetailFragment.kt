package com.example.dailyexpensetracker.ui.billdetail

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
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.FragmentBillDetailBinding
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.common.util.DateFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BillDetailFragment : Fragment() {

    private var _binding: FragmentBillDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BillDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        binding.btnApprove.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.approve()
                findNavController().popBackStack()
            }
        }

        binding.btnDecline.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.decline()
                findNavController().popBackStack()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val tx = state.transaction ?: return@collect
                    val sign = if (tx.type == TransactionType.EXPENSE) "-" else "+"
                    binding.tvAmount.text = "$sign${CurrencyFormatter.format(tx.amount)} USD"
                    binding.tvTitle.text = tx.title
                    binding.tvScheduledFor.text = getString(
                        R.string.bill_detail_scheduled_for,
                        DateFormatter.formatForPicker(tx.date)
                    )

                    if (tx.payeeName != null) {
                        binding.payeeRow.visibility = View.VISIBLE
                        binding.tvPayeeName.text = tx.payeeName
                        binding.tvPayeeRole.text = tx.payeeRole.orEmpty()
                        binding.tvPayeeInitial.text = tx.payeeName.take(1).uppercase()
                    } else {
                        binding.payeeRow.visibility = View.GONE
                    }

                    val card = state.card
                    if (card != null) {
                        binding.tvCard.visibility = View.VISIBLE
                        binding.tvCard.text = getString(R.string.bill_detail_card, card.cardNumberMasked)
                    } else {
                        binding.tvCard.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
