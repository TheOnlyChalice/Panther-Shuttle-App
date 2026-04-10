package com.example.protoshuttleapp.ui.home

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.data.ManagerScheduleEntryDoc
import com.example.protoshuttleapp.ui.MainActivity
import com.example.protoshuttleapp.ui.NotificationStore
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
    private var clockJob: Job? = null
    private var estimateJob: Job? = null

    private val allScheduleEntries = mutableListOf<ManagerScheduleEntryDoc>()

    private var cachedEstimateKey: String? = null
    private var cachedEstimateValue: Int? = null
    private var estimateInFlightKey: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        latestContainer = view.findViewById(R.id.latestContainer)
        nextStopText = view.findViewById(R.id.nextStopText)
        estimatedStudentsText = view.findViewById(R.id.estimatedStudentsText)

        view.findViewById<View>(R.id.btnViewLiveMap).setOnClickListener {
            switchToTab(R.id.navigation_map)
        }

        view.findViewById<View>(R.id.btnViewFullSchedule).setOnClickListener {
            switchToTab(R.id.navigation_schedule)
        }

        refreshLatestNotifications()
        startScheduleListener()
        startClockTicker()
    }

    override fun onResume() {
        super.onResume()
        refreshLatestNotifications()
        renderNextStopCard()
    }

    override fun onDestroyView() {
        scheduleListener?.remove()
        scheduleListener = null

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

    private fun refreshLatestNotifications() {
        latestContainer.removeAllViews()

        val latest = NotificationStore.latestActive(limit = 2)

        if (latest.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "• No notifications yet"
                textSize = 14f
            }
            latestContainer.addView(tv)
            return
        }

        for (n in latest) {
            val tv = TextView(requireContext()).apply {
                text = "• ${n.title} — ${n.message}"
                textSize = 14f
            }
            latestContainer.addView(tv)
        }
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
            .sortedWith(compareBy<ManagerScheduleEntryDoc>({ it.timeMinutes }, { it.stopName.lowercase(Locale.US) }))
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