package com.example.decegestin

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.ItemNumberBinding

class NumberAdapter(
    private val onNumberClicked: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<NumberAdapter.NumberViewHolder>() {

    private val numbers = mutableListOf<Int>()
    private val checkedColors = mutableMapOf<Int, String>()

    fun setData(newNumbers: List<Int>, colors: Map<Int, String>) {
        numbers.clear()
        numbers.addAll(newNumbers)
        checkedColors.clear()
        checkedColors.putAll(colors)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NumberViewHolder {
        val binding = ItemNumberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NumberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NumberViewHolder, position: Int) {
        val number = numbers[position]
        holder.bind(number, checkedColors[number])
    }

    override fun getItemCount(): Int = numbers.size

    inner class NumberViewHolder(private val binding: ItemNumberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(number: Int, hexColor: String?) {
            binding.numberText.text = String.format("%03d", number)
            
            val context = binding.root.context
            if (hexColor != null) {
                try {
                    binding.numberCard.setCardBackgroundColor(Color.parseColor(hexColor))
                    binding.numberText.setTextColor(Color.WHITE)
                } catch (e: Exception) {
                    binding.numberCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.teal_button))
                    binding.numberText.setTextColor(Color.WHITE)
                }
            } else {
                binding.numberCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.light_gray_item))
                binding.numberText.setTextColor(ContextCompat.getColor(context, R.color.black))
            }

            binding.root.setOnClickListener {
                val isCurrentlyChecked = checkedColors.containsKey(number)
                onNumberClicked(number, !isCurrentlyChecked)
            }
        }
    }
}
