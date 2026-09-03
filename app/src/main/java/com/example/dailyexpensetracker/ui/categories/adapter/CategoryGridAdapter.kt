package com.example.dailyexpensetracker.ui.categories.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.databinding.ItemCategoryCardBinding
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper

class CategoryGridAdapter : RecyclerView.Adapter<CategoryGridAdapter.ViewHolder>() {

    private var items: List<Category> = emptyList()

    fun submitList(list: List<Category>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    class ViewHolder(private val binding: ItemCategoryCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Category) {
            binding.tvName.text = item.name
            binding.ivIcon.setImageResource(CategoryIconMapper.iconFor(item.iconName))
            runCatching { Color.parseColor(item.colorHex) }.getOrNull()?.let { binding.cardContent.setBackgroundColor(it) }
        }
    }
}
