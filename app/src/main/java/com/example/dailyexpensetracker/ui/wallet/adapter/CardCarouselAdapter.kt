package com.example.dailyexpensetracker.ui.wallet.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyexpensetracker.databinding.ItemPaymentCardBinding
import com.example.dailyexpensetracker.domain.model.Card
import com.example.dailyexpensetracker.ui.common.util.CurrencyFormatter

class CardCarouselAdapter : RecyclerView.Adapter<CardCarouselAdapter.ViewHolder>() {

    private var items: List<Card> = emptyList()

    fun submitList(list: List<Card>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPaymentCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    class ViewHolder(private val binding: ItemPaymentCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Card) {
            binding.tvBalance.text = CurrencyFormatter.format(item.currentBalance)
            binding.tvMasked.text = item.cardNumberMasked
            binding.tvCardType.text = item.cardType.name
        }
    }
}
