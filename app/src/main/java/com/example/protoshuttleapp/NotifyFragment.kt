package com.example.protoshuttleapp.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.ui.settings.SettingStore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class NotifyFragment : Fragment(R.layout.fragment_notify) {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: NotificationsAdapter
    private val items = mutableListOf<NotificationItem>()

    private val firebase = FirebaseRepo()
    private var listener: ListenerRegistration? = null

    // ✅ Persist dismisses across app restarts
    private lateinit var dismissStore: DismissedNotificationStore
    private lateinit var dismissedIds: MutableSet<String>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dismissStore = DismissedNotificationStore(requireContext())
        dismissedIds = dismissStore.load()

        recycler = view.findViewById(R.id.notificationsRecycler)

        adapter = NotificationsAdapter(items) { item, position ->
            // ✅ Save dismiss persistently
            dismissedIds.add(item.id.toString())
            dismissStore.save(dismissedIds)

            adapter.removeAt(position)
            updateBadge(items.size)
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        updateBadge(0)
    }

    override fun onResume() {
        super.onResume()
        // Reload in case it changed elsewhere
        dismissedIds = dismissStore.load()
        startListening()
    }

    override fun onPause() {
        listener?.remove()
        listener = null
        super.onPause()
    }

    private fun startListening() {
        val store = SettingStore(requireContext())
        val favStops = store.getFavoriteStops()
            .map { it.stopName.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Always include "ALL"
        val tags = (favStops + listOf("ALL")).distinct().take(10)

        listener?.remove()
        listener = null

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                Toast.makeText(requireContext(), "Firebase offline; can't load driver messages.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            listener = firebase.listenDriverMessagesForAudience(tags) { docs ->
                val mapped = docs
                    .map { doc ->
                        val prefix =
                            if (doc.stopName.isNotBlank() && doc.audience != listOf("ALL")) "[${doc.stopName}] "
                            else ""

                        NotificationItem(
                            id = doc.createdAt, // using createdAt as a stable-ish ID
                            title = prefix + (doc.title.ifBlank { "Driver Update" }),
                            message = doc.message,
                            createdAtMillis = doc.createdAt
                        )
                    }
                    // ✅ Filter out dismissed IDs (persisted)
                    .filter { !dismissedIds.contains(it.id.toString()) }

                items.clear()
                items.addAll(mapped)
                adapter.notifyDataSetChanged()
                updateBadge(items.size)
            }
        }
    }

    private fun updateBadge(count: Int) {
        (activity as? MainActivity)?.updateNotificationBadge(count)
    }
}