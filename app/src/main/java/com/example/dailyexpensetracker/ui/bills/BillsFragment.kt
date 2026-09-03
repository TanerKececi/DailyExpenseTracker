package com.example.dailyexpensetracker.ui.bills

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.dailyexpensetracker.databinding.FragmentBillsBinding
import com.example.dailyexpensetracker.domain.model.TransactionStatus
import com.example.dailyexpensetracker.ui.wallet.adapter.RecentTransactionAdapter
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BillsFragment : Fragment() {

    private var _binding: FragmentBillsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BillsViewModel by viewModels()

    /** Tab order in fragment_bills.xml — the single source of truth for position <-> status. */
    private val tabStatuses = listOf(
        TransactionStatus.PAID,
        TransactionStatus.OVERDUE,
        TransactionStatus.UPCOMING
    )

    private val adapter = RecentTransactionAdapter { item ->
        findNavController().navigate(
            BillsFragmentDirections.actionBillsFragmentToBillDetailFragment(item.transaction.id)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvBills.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBills.adapter = adapter

        // The ViewModel outlives this view (navigating to the detail screen and back), but the
        // recreated TabLayout defaults to position 0 — re-point it at the status still held there,
        // or the strip says "Paid" while the list is still filtered to the previous tab.
        binding.tabLayout.getTabAt(tabStatuses.indexOf(viewModel.status))?.select()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setStatus(tabStatuses[tab.position])
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bills.collect { adapter.submitList(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
