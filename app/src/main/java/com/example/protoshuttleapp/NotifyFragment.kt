package com.example.protoshuttleapp.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotifyFragment : Fragment(R.layout.fragment_notify) {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: NotificationsAdapter
    private val items = mutableListOf<NotificationItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recycler = view.findViewById(R.id.notificationsRecycler)

        // Load active notifications (also cleans >24h)
        items.clear()
        items.addAll(NotificationStore.getActiveNotifications())

        adapter = NotificationsAdapter(items) { item, position ->
            NotificationStore.dismissNotification(item)
            adapter.removeAt(position)
            updateBadge()
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // Initial badge
        updateBadge()

        // Add "Test Notification" after 15 seconds
        viewLifecycleOwner.lifecycleScope.launch {
            delay(15_000L)
            val item = NotificationItem(
                title = "Test Notification",
                message = "This is a test notification generated after 15 seconds."
            )
            NotificationStore.addNotification(item)
            adapter.addAtTop(item)
            updateBadge()
        }
    }

    private fun updateBadge() {
        val count = NotificationStore.activeCount()
        (activity as? MainActivity)?.updateNotificationBadge(count)
    }
}
