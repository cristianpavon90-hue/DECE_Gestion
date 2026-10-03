package com.example.decegestin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class EducandoSummary(
    val nombreCompleto: String,
    val anio: Int,
    val calificacionTotal: Int,
    val count: Int
)

class EducandoSummaryAdapter(private var items: List<EducandoSummary>) :
    RecyclerView.Adapter<EducandoSummaryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNameYear: TextView = view.findViewById(R.id.tv_name_year)
        val tvStats: TextView = view.findViewById(R.id.tv_stats)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_educando_summary, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.tvNameYear.text = context.getString(R.string.educando_summary_header, item.nombreCompleto, item.anio)
        holder.tvStats.text = context.getString(R.string.educando_summary_stats, item.calificacionTotal, item.count)
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<EducandoSummary>) {
        items = newItems
        notifyDataSetChanged()
    }
}
