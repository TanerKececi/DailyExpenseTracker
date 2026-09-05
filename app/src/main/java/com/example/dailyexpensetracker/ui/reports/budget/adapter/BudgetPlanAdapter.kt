package com.example.dailyexpensetracker.ui.reports.budget.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.ItemBudgetPlanBinding
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter

class BudgetPlanAdapter(
    private val onClick: (CategorySpend) -> Unit
) : RecyclerView.Adapter<BudgetPlanAdapter.ViewHolder>() {

    private var items: List<CategorySpend> = emptyList()

    fun submitList(list: List<CategorySpend>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBudgetPlanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class ViewHolder(private val binding: ItemBudgetPlanBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategorySpend) {
            val context = binding.root.context
            binding.tvName.text = item.category.name
            binding.ivIcon.setImageResource(CategoryIconMapper.iconFor(item.category.iconName))

            val limit = item.category.budgetLimit
            if (limit == null || limit <= 0.0) {
                binding.tvAmounts.text = context.getString(R.string.reports_no_budget_set)
                binding.progressBudget.progress = 0
                tint(context, R.color.text_secondary)
                return
            }

            binding.tvAmounts.text = context.getString(
                R.string.reports_spent_of_limit,
                CurrencyFormatter.format(item.spent),
                CurrencyFormatter.format(limit)
            )
            binding.progressBudget.progress = (item.spent / limit * 100).coerceIn(0.0, 100.0).toInt()
            tint(context, if (item.spent >= limit) R.color.soft_red else R.color.purple_primary)
        }

        private fun tint(context: Context, colorRes: Int) {
            // setIndicatorColor/trackColor are LinearProgressIndicator's own API; it does not
            // expose trackTintList.
            binding.progressBudget.setIndicatorColor(ContextCompat.getColor(context, colorRes))
            // divider, not background — the latter is the screen's own
            // background, so an empty or part-filled track would be invisible against it.
            binding.progressBudget.trackColor = ContextCompat.getColor(context, R.color.divider)
        }
    }
}
