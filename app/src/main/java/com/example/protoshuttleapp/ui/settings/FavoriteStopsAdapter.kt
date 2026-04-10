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
    stopNames: List<String>,
    timeOptionsByStop: Map<String, List<Int>>,
    private val items: MutableList<FavoriteStop>,
    private val onRemove: (position: Int) -> Unit,
    private val onStopChanged: (position: Int, newStop: String) -> Unit,
    private val onTimeChanged: (position: Int, newTime: Int) -> Unit
) : RecyclerView.Adapter<FavoriteStopsAdapter.VH>() {

    private val stopNamesMutable = stopNames.toMutableList()
    private val timeOptionsByStopMutable = timeOptionsByStop.toMutableMap()

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stopSpinner: Spinner = itemView.findViewById(R.id.favoriteStopSpinner)
        val timeSpinner: Spinner = itemView.findViewById(R.id.favoriteTimeSpinner)
        val removeButton: MaterialButton = itemView.findViewById(R.id.favoriteRemoveButton)
        val subtitle: TextView? = itemView.findViewById(R.id.favoriteSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_stop, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        val stopAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            stopNamesMutable
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        holder.stopSpinner.adapter = stopAdapter

        val selectedStopIndex = stopNamesMutable.indexOf(item.stopName).let { if (it >= 0) it else 0 }
        if (holder.stopSpinner.selectedItemPosition != selectedStopIndex) {
            holder.stopSpinner.setSelection(selectedStopIndex, false)
        }

        val currentStop = stopNamesMutable.getOrNull(selectedStopIndex)
        val timeOptions = timeOptionsByStopMutable[currentStop].orEmpty()
        val timeLabels = timeOptions.map { formatTime(it) }

        val timeAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            timeLabels
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        holder.timeSpinner.adapter = timeAdapter

        val selectedTimeIndex = timeOptions.indexOf(item.timeMinutes).let { if (it >= 0) it else 0 }
        if (holder.timeSpinner.selectedItemPosition != selectedTimeIndex) {
            holder.timeSpinner.setSelection(selectedTimeIndex, false)
        }

        holder.stopSpinner.onItemSelectedListener = SimpleItemSelectedListener { selectedIndex ->
            val posNow = holder.bindingAdapterPosition
            if (posNow == RecyclerView.NO_POSITION) return@SimpleItemSelectedListener

            val newStop = stopNamesMutable.getOrNull(selectedIndex) ?: return@SimpleItemSelectedListener
            if (items[posNow].stopName != newStop) {
                onStopChanged(posNow, newStop)
            }
        }

        holder.timeSpinner.onItemSelectedListener = SimpleItemSelectedListener { selectedIndex ->
            val posNow = holder.bindingAdapterPosition
            if (posNow == RecyclerView.NO_POSITION) return@SimpleItemSelectedListener

            val newTime = timeOptions.getOrNull(selectedIndex) ?: return@SimpleItemSelectedListener
            if (items[posNow].timeMinutes != newTime) {
                onTimeChanged(posNow, newTime)
            }
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

    fun updateScheduleOptions(
        stopNames: List<String>,
        timeOptionsByStop: Map<String, List<Int>>
    ) {
        stopNamesMutable.clear()
        stopNamesMutable.addAll(stopNames)

        timeOptionsByStopMutable.clear()
        timeOptionsByStopMutable.putAll(timeOptionsByStop)

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