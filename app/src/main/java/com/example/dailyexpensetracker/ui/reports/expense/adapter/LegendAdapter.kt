package com.example.dailyexpensetracker.ui.reports.expense.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.databinding.ItemLegendBinding
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter

/**
 * [total] is passed in so each row can show its share without recomputing the sum.
 *
 * Rows are inert unless [onClick] is supplied — Expense Chart passes one to open Category Detail.
 */
class LegendAdapter(
    private val onClick: ((Category) -> Unit)? = null
) : RecyclerView.Adapter<LegendAdapter.ViewHolder>() {

    private var items: List<CategorySpend> = emptyList()
    private var total: Double = 0.0

    fun submitList(list: List<CategorySpend>, total: Double) {
        items = list
        this.total = total
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLegendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], total)

    class ViewHolder(
        private val binding: ItemLegendBinding,
        private val onClick: ((Category) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategorySpend, total: Double) {
            binding.tvName.text = item.category.name
            binding.tvAmount.text = CurrencyFormatter.format(item.spent)
            val percent = if (total > 0.0) (item.spent / total * 100).toInt() else 0
            binding.tvPercent.text = "$percent%"
            binding.vSwatch.backgroundTintList = ColorStateList.valueOf(parseColor(item.category.colorHex))
            onClick?.let { click -> binding.root.setOnClickListener { click(item.category) } }
        }

        private fun parseColor(hex: String): Int = try {
            Color.parseColor(hex)
        } catch (e: IllegalArgumentException) {
            Color.GRAY // seed data is well-formed, but a bad hex must not crash the screen
        }
    }
}
