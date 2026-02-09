package com.example.protoshuttleapp.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R

class NotifyFragment : Fragment(R.layout.fragment_notify) {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: NotificationsAdapter
    private val items = mutableListOf<NotificationItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.notificationsRecycler)

        // Load active notifications (also cleans >24h if your store does that)
        items.clear()
        items.addAll(NotificationStore.allActive())

        adapter = NotificationsAdapter(items) { item, position ->
            // dismiss from store + remove from list
            NotificationStore.dismiss(item.id)
            adapter.removeAt(position)

            updateBadge()
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // Initial badge
        updateBadge()

        // (Optional) test notification after 15s
        Handler(Looper.getMainLooper()).postDelayed({
            val test = NotificationItem(
                title = "Test Notification",
                message = "This is a test notification."
            )
            NotificationStore.add(test)

            items.clear()
            items.addAll(NotificationStore.allActive())
            adapter.notifyDataSetChanged()

            updateBadge()
        }, 15_000)
    }

    private fun updateBadge() {
        (activity as? MainActivity)?.updateNotificationBadge(NotificationStore.activeCount())
    }
}
