package com.example.dailyexpensetracker.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.databinding.ItemCategoryIconBinding
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.domain.model.CategorySpend
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper

/** Icons are inert unless [onClick] is supplied — Home passes one to open Category Detail. */
class TopSpendingAdapter(
    private val onClick: ((Category) -> Unit)? = null
) : RecyclerView.Adapter<TopSpendingAdapter.ViewHolder>() {

    private var items: List<CategorySpend> = emptyList()

    fun submitList(list: List<CategorySpend>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    class ViewHolder(
        private val binding: ItemCategoryIconBinding,
        private val onClick: ((Category) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategorySpend) {
            binding.tvName.text = item.category.name
            binding.ivIcon.setImageResource(CategoryIconMapper.iconFor(item.category.iconName))
            onClick?.let { click -> binding.root.setOnClickListener { click(item.category) } }
        }
    }
}
