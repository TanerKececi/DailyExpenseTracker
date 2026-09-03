package com.example.dailyexpensetracker.ui.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.FragmentWalletBinding
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.wallet.adapter.CardCarouselAdapter
import com.example.dailyexpensetracker.ui.wallet.adapter.RecentTransactionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WalletFragment : Fragment() {

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WalletViewModel by viewModels()

    private val cardAdapter = CardCarouselAdapter()
    private val transactionAdapter = RecentTransactionAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCardCarousel.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvCardCarousel.adapter = cardAdapter

        binding.rvRecentTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentTransactions.adapter = transactionAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvRemaining.text = CurrencyFormatter.format(state.remaining)
                    binding.tvTotalBudget.text = "/${CurrencyFormatter.format(state.totalBudget)}"
                    binding.tvSpentBadge.text = "Spent: ${CurrencyFormatter.format(state.spent)}"
                    binding.tvTrackStatus.text = getString(
                        if (state.onTrack) R.string.wallet_spending_on_track else R.string.wallet_spending_over_budget
                    )
                    binding.tvTrackStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), if (state.onTrack) R.color.soft_green else R.color.soft_red)
                    )
                    cardAdapter.submitList(state.cards)
                    transactionAdapter.submitList(state.recentTransactions)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
