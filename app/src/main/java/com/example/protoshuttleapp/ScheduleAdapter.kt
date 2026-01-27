package com.example.protoshuttleapp.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R

class ScheduleAdapter(
    private val items: List<ScheduleStop>
) : RecyclerView.Adapter<ScheduleAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val time: TextView = itemView.findViewById(R.id.stopTime)
        private val name: TextView = itemView.findViewById(R.id.stopName)

        fun bind(item: ScheduleStop) {
            time.text = item.time
            name.text = item.name
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_stop, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
