package com.example.protoshuttleapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R

class NotificationsAdapter(
    private val items: MutableList<NotificationItem>,
    private val onDismiss: (NotificationItem, Int) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.notificationTitle)
        private val message: TextView = itemView.findViewById(R.id.notificationMessage)
        private val dismiss: TextView = itemView.findViewById(R.id.notificationDismiss)

        fun bind(item: NotificationItem) {
            title.text = item.title
            message.text = item.message

            dismiss.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDismiss(item, pos)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun removeAt(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun addAtTop(item: NotificationItem) {
        items.add(0, item)
        notifyItemInserted(0)
    }
}
