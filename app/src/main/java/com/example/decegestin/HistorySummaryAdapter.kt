package com.example.decegestin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.ItemHistorySummaryBinding

data class SummaryItem(
    val caseType: String,
    val totalCount: Int,
    val details: String // e.g., "1ro EGB A: 5, 2do BGU B: 3"
)

class HistorySummaryAdapter : RecyclerView.Adapter<HistorySummaryAdapter.ViewHolder>() {

    private var items = listOf<SummaryItem>()

    fun setData(newItems: List<SummaryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemHistorySummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.textSummaryCaseType.text = item.caseType
        holder.b.textSummaryTotal.text = item.totalCount.toString()
        holder.b.textSummaryDetails.text = item.details
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(val b: ItemHistorySummaryBinding) : RecyclerView.ViewHolder(b.root)
}
