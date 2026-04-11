package com.example.protoshuttleapp.ui.home

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.DriverMessageDoc
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.data.ManagerScheduleEntryDoc
import com.example.protoshuttleapp.ui.DismissedNotificationStore
import com.example.protoshuttleapp.ui.MainActivity
import com.example.protoshuttleapp.ui.settings.FavoriteStop
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var latestContainer: LinearLayout
    private lateinit var nextStopText: TextView
    private lateinit var estimatedStudentsText: TextView

    private val firebase = FirebaseRepo()

    private var scheduleListener: ListenerRegistration? = null
    private var favoritesListener: ListenerRegistration? = null
    private var driverMessagesListener: ListenerRegistration? = null

    private var clockJob: Job? = null
    private var estimateJob: Job? = null

    private val allScheduleEntries = mutableListOf<ManagerScheduleEntryDoc>()
    private val currentFavorites = mutableListOf<FavoriteStop>()
    private val currentDriverMessages = mutableListOf<DriverMessageDoc>()

    private var cachedEstimateKey: String? = null
    private var cachedEstimateValue: Int? = null
    private var estimateInFlightKey: String? = null

    private lateinit var dismissedStore: DismissedNotificationStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dismissedStore = DismissedNotificationStore(requireContext())

        latestContainer = view.findViewById(R.id.latestContainer)
        nextStopText = view.findViewById(R.id.nextStopText)
        estimatedStudentsText = view.findViewById(R.id.estimatedStudentsText)

        view.findViewById<View>(R.id.btnViewLiveMap).setOnClickListener {
            switchToTab(R.id.navigation_map)
        }

        view.findViewById<View>(R.id.btnViewFullSchedule).setOnClickListener {
            switchToTab(R.id.navigation_schedule)
        }

        renderLatestDriverNotifications()
        startScheduleListener()
        startFavoritesAndDriverNotificationListeners()
        startClockTicker()
    }

    override fun onResume() {
        super.onResume()
        renderNextStopCard()
        renderLatestDriverNotifications()
    }

    override fun onDestroyView() {
        scheduleListener?.remove()
        scheduleListener = null

        favoritesListener?.remove()
        favoritesListener = null

        driverMessagesListener?.remove()
        driverMessagesListener = null

        clockJob?.cancel()
        clockJob = null

        estimateJob?.cancel()
        estimateJob = null

        super.onDestroyView()
    }

    private fun switchToTab(menuItemId: Int) {
        val bottomNav =
            (activity as? MainActivity)?.findViewById<BottomNavigationView>(R.id.navView)

        bottomNav?.selectedItemId = menuItemId
    }

    private fun startScheduleListener() {
        nextStopText.text = "Next Stop: Loading..."
        estimatedStudentsText.text = "Estimated students: Loading..."

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                nextStopText.text = "Next Stop: Schedule unavailable"
                estimatedStudentsText.text = "Estimated students: unavailable"
                return@launch
            }

            scheduleListener?.remove()
            scheduleListener = firebase.listenManagerSchedule { entries ->
                allScheduleEntries.clear()
                allScheduleEntries.addAll(entries)
                renderNextStopCard()
            }
        }
    }

    private fun startFavoritesAndDriverNotificationListeners() {
        renderLatestDriverNotifications()

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                showNoDriverNotifications("• Driver notifications unavailable")
                return@launch
            }

            favoritesListener?.remove()
            driverMessagesListener?.remove()

            favoritesListener = firebase.listenFavoriteStops { docs ->
                currentFavorites.clear()
                currentFavorites.addAll(
                    docs.map {
                        FavoriteStop(
                            stopName = it.stopName,
                            timeMinutes = it.timeMinutes
                        )
                    }
                )
                resubscribeToDriverMessages()
            }
        }
    }

    private fun resubscribeToDriverMessages() {
        val stopTimeTags = currentFavorites.map {
            firebase.makeStopTimeAudienceTag(it.stopName, it.timeMinutes)
        }

        val stopTags = currentFavorites
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

        driverMessagesListener?.remove()
        driverMessagesListener = firebase.listenDriverMessagesForAudience(tags, limit = 25) { docs ->
            currentDriverMessages.clear()
            currentDriverMessages.addAll(docs.sortedByDescending { it.createdAt })
            renderLatestDriverNotifications()
        }
    }

    private fun renderLatestDriverNotifications() {
        latestContainer.removeAllViews()

        val dismissedIds = dismissedStore.load()

        val latestThree = currentDriverMessages
            .filter { !dismissedIds.contains(it.createdAt.toString()) }
            .sortedByDescending { it.createdAt }
            .take(3)

        if (latestThree.isEmpty()) {
            showNoDriverNotifications("• No driver notifications yet")
            return
        }

        for (doc in latestThree) {
            val line = buildNotificationLine(doc)
            val tv = TextView(requireContext()).apply {
                text = line
                textSize = 14f
            }
            latestContainer.addView(tv)
        }
    }

    private fun showNoDriverNotifications(text: String) {
        latestContainer.removeAllViews()
        val tv = TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
        }
        latestContainer.addView(tv)
    }

    private fun buildNotificationLine(doc: DriverMessageDoc): String {
        val prefix = when {
            doc.stopName.isBlank() || doc.audience == listOf("ALL") -> ""
            doc.timeMinutes > 0 -> "[${doc.stopName} • ${formatClockTime(doc.timeMinutes)}] "
            else -> "[${doc.stopName}] "
        }

        val title = doc.title.ifBlank { "Driver Update" }
        val message = doc.message.trim()

        return if (message.isBlank()) {
            "• $prefix$title"
        } else {
            "• $prefix$title — $message"
        }
    }

    private fun startClockTicker() {
        clockJob?.cancel()
        clockJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                renderNextStopCard()
                delay(30_000L)
            }
        }
    }

    private fun renderNextStopCard() {
        val today = LocalDate.now().dayOfWeek.toManagerDayValue()
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute

        val nextEntry = allScheduleEntries
            .filter { normalizeDayOfWeek(it.dayOfWeek) == today }
            .sortedWith(
                compareBy<ManagerScheduleEntryDoc>(
                    { it.timeMinutes },
                    { it.stopName.lowercase(Locale.US) }
                )
            )
            .firstOrNull { it.timeMinutes >= nowMinutes }

        if (nextEntry == null) {
            nextStopText.text = "Next Stop: No more scheduled stops today"
            estimatedStudentsText.text = "Estimated students: --"
            cachedEstimateKey = null
            cachedEstimateValue = null
            estimateInFlightKey = null
            return
        }

        val etaMinutes = (nextEntry.timeMinutes - nowMinutes).coerceAtLeast(0)
        nextStopText.text = "Next Stop: ${nextEntry.stopName} (${formatEta(etaMinutes)})"

        val estimateKey = "${today}_${nextEntry.stopName}_${nextEntry.timeMinutes}"

        if (cachedEstimateKey == estimateKey && cachedEstimateValue != null) {
            estimatedStudentsText.text = "Estimated students: ${cachedEstimateValue}"
            return
        }

        if (estimateInFlightKey == estimateKey) {
            estimatedStudentsText.text = "Estimated students: Loading..."
            return
        }

        cachedEstimateKey = estimateKey
        cachedEstimateValue = null
        estimateInFlightKey = estimateKey
        estimatedStudentsText.text = "Estimated students: Loading..."

        estimateJob?.cancel()
        estimateJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ok = firebase.ensureSignedIn()
                if (!ok) {
                    if (estimateInFlightKey == estimateKey) {
                        estimatedStudentsText.text = "Estimated students: unavailable"
                        estimateInFlightKey = null
                    }
                    return@launch
                }

                val count = firebase.countIndexedFavoriteUsersForStopAroundTime(
                    stopName = nextEntry.stopName,
                    targetTimeMinutes = nextEntry.timeMinutes,
                    windowMinutes = 15
                )

                if (cachedEstimateKey == estimateKey) {
                    cachedEstimateValue = count
                    estimatedStudentsText.text = "Estimated students: $count"
                }
            } catch (_: Exception) {
                if (cachedEstimateKey == estimateKey) {
                    estimatedStudentsText.text = "Estimated students: unavailable"
                }
            } finally {
                if (estimateInFlightKey == estimateKey) {
                    estimateInFlightKey = null
                }
            }
        }
    }

    private fun formatEta(minutes: Int): String {
        return when {
            minutes <= 0 -> "ETA <1 min"
            minutes == 1 -> "ETA 1 min"
            else -> "ETA $minutes min"
        }
    }

    private fun formatClockTime(totalMinutes: Int): String {
        val hour24 = ((totalMinutes / 60) % 24 + 24) % 24
        val minute = ((totalMinutes % 60) + 60) % 60
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val raw = hour24 % 12) {
            0 -> 12
            else -> raw
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
    }

    private fun normalizeDayOfWeek(day: Int): Int {
        return day.coerceIn(1, 7)
    }

    private fun DayOfWeek.toManagerDayValue(): Int {
        return when (this) {
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
            DayOfWeek.SUNDAY -> 7
        }
    }
}