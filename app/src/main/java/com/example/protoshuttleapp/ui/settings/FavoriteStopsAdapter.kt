package com.example.protoshuttleapp.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R
import com.google.android.material.button.MaterialButton

class FavoriteStopsAdapter(
    private val stopNames: List<String>,
    private val items: MutableList<FavoriteStop>,
    private val onPickTime: (position: Int) -> Unit,
    private val onRemove: (position: Int) -> Unit,
    private val onStopChanged: (position: Int, newStop: String) -> Unit
) : RecyclerView.Adapter<FavoriteStopsAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stopSpinner: Spinner = itemView.findViewById(R.id.favoriteStopSpinner)
        val timeButton: MaterialButton = itemView.findViewById(R.id.favoriteTimeButton)
        val removeButton: MaterialButton = itemView.findViewById(R.id.favoriteRemoveButton)
        val subtitle: TextView? = itemView.findViewById(R.id.favoriteSubtitle) // optional if you have it
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_stop, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // Spinner setup (set adapter once per bind; safe + simple for now)
        val spinnerAdapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_item,
            stopNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        holder.stopSpinner.adapter = spinnerAdapter

        // Set selection without triggering "changed" callback accidentally
        val idx = stopNames.indexOf(item.stopName).let { if (it >= 0) it else 0 }
        if (holder.stopSpinner.selectedItemPosition != idx) {
            holder.stopSpinner.setSelection(idx, false)
        }

        // IMPORTANT: remove any old listener before adding a new one
        holder.stopSpinner.onItemSelectedListener = SimpleItemSelectedListener { selectedIndex ->
            val posNow = holder.bindingAdapterPosition
            if (posNow == RecyclerView.NO_POSITION) return@SimpleItemSelectedListener

            val newStop = stopNames.getOrNull(selectedIndex) ?: return@SimpleItemSelectedListener
            if (items[posNow].stopName != newStop) {
                onStopChanged(posNow, newStop)
            }
        }

        holder.timeButton.text = formatTime(item.timeMinutes)

        // ✅ Use bindingAdapterPosition at CLICK time
        holder.timeButton.setOnClickListener {
            val posNow = holder.bindingAdapterPosition
            if (posNow == RecyclerView.NO_POSITION) return@setOnClickListener
            onPickTime(posNow)
        }

        holder.removeButton.setOnClickListener {
            val posNow = holder.bindingAdapterPosition
            if (posNow == RecyclerView.NO_POSITION) return@setOnClickListener
            onRemove(posNow)
        }
    }

    override fun getItemCount(): Int = items.size

    fun getItems(): List<FavoriteStop> = items

    fun replaceAll(newItems: List<FavoriteStop>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun formatTime(totalMinutes: Int): String {
        val h24 = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        val ampm = if (h24 >= 12) "PM" else "AM"
        val h12 = when (val x = h24 % 12) {
            0 -> 12
            else -> x
        }
        return String.format("%d:%02d %s", h12, m, ampm)
    }
}
