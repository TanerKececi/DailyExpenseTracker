package com.example.dailyexpensetracker.ui.addtransaction

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.ItemCategoryChipBinding
import com.example.dailyexpensetracker.domain.model.Category
import com.example.dailyexpensetracker.ui.common.util.CategoryIconMapper

class CategoryPickerAdapter(
    private val onSelected: (Category) -> Unit
) : RecyclerView.Adapter<CategoryPickerAdapter.ViewHolder>() {

    private var items: List<Category> = emptyList()

    var selectedId: Long? = null
        private set

    fun submitList(list: List<Category>) {
        items = list
        notifyDataSetChanged()
    }

    fun setSelectedId(id: Long?) {
        selectedId = id
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, item.id == selectedId)
        holder.itemView.setOnClickListener {
            setSelectedId(item.id)
            onSelected(item)
        }
    }

    inner class ViewHolder(private val binding: ItemCategoryChipBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Category, isSelected: Boolean) {
            binding.tvName.text = item.name
            binding.ivIcon.setImageResource(CategoryIconMapper.iconFor(item.iconName))
            val tintColor = ContextCompat.getColor(
                binding.root.context,
                if (isSelected) R.color.purple_primary else R.color.purple_accent_light
            )
            DrawableCompat.setTint(binding.circle.background.mutate(), tintColor)
        }
    }
}
