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
        recycler.layoutManager = LinearLayoutManager(requireContext())

        // load active notifications (also cleans 24h)
        items.clear()
        items.addAll(NotificationStore.allActive())

        adapter = NotificationsAdapter(items) { item, position ->
            // X clicked
            NotificationStore.dismiss(item.id)
            adapter.removeAt(position)
            updateBadge()
        }

        recycler.adapter = adapter
        updateBadge()

        // Test notification after 15 seconds (remove later if you want)
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
