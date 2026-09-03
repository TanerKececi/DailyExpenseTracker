package com.example.dailyexpensetracker.ui.wallet.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.ItemTransactionBinding
import com.example.dailyexpensetracker.domain.model.TransactionType
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter
import com.example.dailyexpensetracker.ui.common.util.DateFormatter

class RecentTransactionAdapter : RecyclerView.Adapter<RecentTransactionAdapter.ViewHolder>() {

    private var items: List<TransactionListItem> = emptyList()

    fun submitList(list: List<TransactionListItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    class ViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TransactionListItem) {
            val tx = item.transaction
            binding.tvTitle.text = tx.title
            binding.tvDate.text = DateFormatter.formatForItem(tx.date)
            binding.ivIcon.setImageResource(item.category?.let { CategoryIconMapper.iconFor(it.iconName) } ?: R.drawable.ic_grid_24)

            val isExpense = tx.type == TransactionType.EXPENSE
            val sign = if (isExpense) "-" else "+"
            binding.tvAmount.text = "$sign${CurrencyFormatter.format(tx.amount)}"
            binding.tvAmount.setTextColor(
                ContextCompat.getColor(binding.root.context, if (isExpense) R.color.soft_red else R.color.soft_green)
            )
        }
    }
}
