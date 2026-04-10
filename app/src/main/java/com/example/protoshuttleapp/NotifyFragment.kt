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
import java.util.Locale

class NotifyFragment : Fragment(R.layout.fragment_notify) {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: NotificationsAdapter
    private val items = mutableListOf<NotificationItem>()

    private val firebase = FirebaseRepo()
    private var listener: ListenerRegistration? = null

    private lateinit var dismissStore: DismissedNotificationStore
    private lateinit var dismissedIds: MutableSet<String>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dismissStore = DismissedNotificationStore(requireContext())
        dismissedIds = dismissStore.load()

        recycler = view.findViewById(R.id.notificationsRecycler)

        adapter = NotificationsAdapter(items) { item, position ->
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
        val favorites = store.getFavoriteStops()

        val stopTimeTags = favorites.map {
            firebase.makeStopTimeAudienceTag(it.stopName, it.timeMinutes)
        }

        val stopTags = favorites
            .map { it.stopName.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val tags = mutableListOf<String>().apply {
            add("ALL")
            addAll(stopTimeTags)
            for (tag in stopTags) {
                if (!contains(tag)) add(tag)
            }
        }.take(10)

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
                        val prefix = when {
                            doc.stopName.isBlank() || doc.audience == listOf("ALL") -> ""
                            doc.timeMinutes > 0 -> "[${doc.stopName} • ${formatMinutes(doc.timeMinutes)}] "
                            else -> "[${doc.stopName}] "
                        }

                        NotificationItem(
                            id = doc.createdAt,
                            title = prefix + doc.title.ifBlank { "Driver Update" },
                            message = doc.message,
                            createdAtMillis = doc.createdAt
                        )
                    }
                    .filter { !dismissedIds.contains(it.id.toString()) }

                items.clear()
                items.addAll(mapped)
                adapter.notifyDataSetChanged()
                updateBadge(items.size)
            }
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

    private fun updateBadge(count: Int) {
        (activity as? MainActivity)?.updateNotificationBadge(count)
    }
}