package com.example.protoshuttleapp.ui.manager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.ManagerScheduleEntryDoc
import java.util.Locale

class ManagerScheduleAdapter(
    private val items: List<ManagerScheduleEntryDoc>,
    private val onTap: (ManagerScheduleEntryDoc) -> Unit
) : RecyclerView.Adapter<ManagerScheduleAdapter.ScheduleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manager_schedule_entry, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(items[position], onTap)
    }

    override fun getItemCount(): Int = items.size

    class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stopNameText: TextView = itemView.findViewById(R.id.scheduleStopNameText)
        private val dayText: TextView = itemView.findViewById(R.id.scheduleDayText)
        private val timeText: TextView = itemView.findViewById(R.id.scheduleTimeText)

        fun bind(item: ManagerScheduleEntryDoc, onTap: (ManagerScheduleEntryDoc) -> Unit) {
            stopNameText.text = item.stopName
            dayText.text = dayName(item.dayOfWeek)
            timeText.text = formatMinutes(item.timeMinutes)

            itemView.setOnClickListener {
                onTap(item)
            }
        }

        private fun dayName(day: Int): String {
            return when (day.coerceIn(1, 7)) {
                1 -> "Monday"
                2 -> "Tuesday"
                3 -> "Wednesday"
                4 -> "Thursday"
                5 -> "Friday"
                6 -> "Saturday"
                else -> "Sunday"
            }
        }

        private fun formatMinutes(totalMinutes: Int): String {
            val hour24 = ((totalMinutes / 60) % 24 + 24) % 24
            val minute = ((totalMinutes % 60) + 60) % 60
            val amPm = if (hour24 < 12) "AM" else "PM"
            val hour12 = when (val raw = hour24 % 12) {
                0 -> 12
                else -> raw
            }
            return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
        }
    }
}