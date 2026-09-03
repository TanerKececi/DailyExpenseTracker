package com.example.dailyexpensetracker.ui.calendar.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.R
import com.example.dailyexpensetracker.databinding.ItemCalendarDayBinding
import com.example.dailyexpensetracker.ui.calendar.DayCell

/**
 * Selection is passed in alongside the cells rather than baked into [DayCell], so changing the
 * selected day doesn't require rebuilding the month's cell list.
 */
class CalendarDayAdapter(
    private val onClick: (Long) -> Unit
) : RecyclerView.Adapter<CalendarDayAdapter.ViewHolder>() {

    private var items: List<DayCell> = emptyList()
    private var selectedDayMillis: Long = 0L

    fun submitList(list: List<DayCell>, selectedDayMillis: Long) {
        items = list
        this.selectedDayMillis = selectedDayMillis
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cell = items[position]
        holder.bind(cell, cell.dateMillis != null && cell.dateMillis == selectedDayMillis)
        val dateMillis = cell.dateMillis
        holder.itemView.setOnClickListener(
            if (dateMillis == null) null else View.OnClickListener { onClick(dateMillis) }
        )
    }

    class ViewHolder(private val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cell: DayCell, isSelected: Boolean) {
            val context = binding.root.context
            binding.tvDay.text = cell.dayOfMonth?.toString().orEmpty()
            binding.tvDay.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.purple_primary else android.R.color.transparent
                )
            )
            binding.tvDay.setTextColor(
                ContextCompat.getColor(context, if (isSelected) R.color.white else R.color.text_primary)
            )
            binding.vDotExpense.visibility = if (cell.hasExpense) View.VISIBLE else View.INVISIBLE
            binding.vDotIncome.visibility = if (cell.hasIncome) View.VISIBLE else View.INVISIBLE
        }
    }
}
