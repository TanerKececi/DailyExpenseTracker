package com.example.dailyexpensetracker.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.databinding.ItemBudgetCardBinding
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter

/** Cards are inert unless [onClick] is supplied — Home passes one to open Category Detail. */
class MonthlyBudgetAdapter(
    private val onClick: ((CategorySpend) -> Unit)? = null
) : RecyclerView.Adapter<MonthlyBudgetAdapter.ViewHolder>() {

    private var items: List<CategorySpend> = emptyList()

    fun submitList(list: List<CategorySpend>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBudgetCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    class ViewHolder(
        private val binding: ItemBudgetCardBinding,
        private val onClick: ((CategorySpend) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategorySpend) {
            val limit = item.category.budgetLimit ?: 0.0
            binding.tvName.text = item.category.name
            binding.tvPerDay.text = "${CurrencyFormatter.format(limit / 30)} Per day"
            binding.tvSpent.text = CurrencyFormatter.format(item.spent)
            binding.tvLimit.text = CurrencyFormatter.format(limit)
            binding.ivIcon.setImageResource(CategoryIconMapper.iconFor(item.category.iconName))
            binding.progressBudget.progress = if (limit > 0) {
                (item.spent / limit * 100).coerceIn(0.0, 100.0).toInt()
            } else 0
            onClick?.let { click -> binding.root.setOnClickListener { click(item) } }
        }
    }
}
