package com.example.protoshuttleapp.ui.schedule

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.data.ManagerScheduleEntryDoc
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ScheduleAdapter
    private lateinit var tabs: TabLayout
    private lateinit var scheduleStatusText: TextView
    private lateinit var emptyScheduleText: TextView

    private val firebase = FirebaseRepo()
    private var scheduleListener: ListenerRegistration? = null

    private val allEntries = mutableListOf<ManagerScheduleEntryDoc>()
    private val visibleStops = mutableListOf<ScheduleStop>()

    private val days = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )

    private var selectedDayOfWeek: Int = LocalDate.now().dayOfWeek.toManagerDayValue()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.scheduleRecycler)
        tabs = view.findViewById(R.id.scheduleTabs)
        scheduleStatusText = view.findViewById(R.id.scheduleStatusText)
        emptyScheduleText = view.findViewById(R.id.emptyScheduleText)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = ScheduleAdapter(visibleStops)
        recycler.adapter = adapter

        buildTabs()
        startScheduleListener()
        applyDayFilter()
    }

    override fun onDestroyView() {
        scheduleListener?.remove()
        scheduleListener = null
        super.onDestroyView()
    }

    private fun buildTabs() {
        tabs.removeAllTabs()

        for (d in days) {
            tabs.addTab(tabs.newTab().setText(shortName(d)))
        }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val pos = tab.position
                if (pos in days.indices) {
                    selectedDayOfWeek = days[pos].toManagerDayValue()
                    applyDayFilter()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        val todayDow = LocalDate.now().dayOfWeek
        val todayIndex = days.indexOf(todayDow).coerceAtLeast(0)
        tabs.getTabAt(todayIndex)?.select()
    }

    private fun startScheduleListener() {
        scheduleStatusText.text = "Loading schedule..."

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                scheduleStatusText.text = "Could not connect to Firebase schedule."
                emptyScheduleText.visibility = View.VISIBLE
                emptyScheduleText.text = "Schedule unavailable."
                return@launch
            }

            scheduleListener?.remove()
            scheduleListener = firebase.listenManagerSchedule { entries ->
                allEntries.clear()
                allEntries.addAll(entries)
                scheduleStatusText.text = ""
                applyDayFilter()
            }
        }
    }

    private fun applyDayFilter() {
        visibleStops.clear()

        val filtered = allEntries
            .filter { normalizeDayOfWeek(it.dayOfWeek) == selectedDayOfWeek }
            .sortedWith(
                compareBy<ManagerScheduleEntryDoc>(
                    { it.timeMinutes },
                    { it.stopName.lowercase(Locale.US) }
                )
            )

        for (entry in filtered) {
            visibleStops.add(
                ScheduleStop(
                    time = formatMinutes(entry.timeMinutes),
                    name = entry.stopName
                )
            )
        }

        adapter.notifyDataSetChanged()
        renderEmptyState()
    }

    private fun renderEmptyState() {
        if (visibleStops.isEmpty()) {
            emptyScheduleText.visibility = View.VISIBLE
            emptyScheduleText.text = "No schedule entries for ${dayName(selectedDayOfWeek)} yet."
        } else {
            emptyScheduleText.visibility = View.GONE
        }
    }

    private fun normalizeDayOfWeek(day: Int): Int {
        return day.coerceIn(1, 7)
    }

    private fun dayName(day: Int): String {
        return when (normalizeDayOfWeek(day)) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            else -> "Sunday"
        }
    }

    private fun shortName(d: DayOfWeek): String {
        return when (d) {
            DayOfWeek.MONDAY -> "Mon"
            DayOfWeek.TUESDAY -> "Tue"
            DayOfWeek.WEDNESDAY -> "Wed"
            DayOfWeek.THURSDAY -> "Thu"
            DayOfWeek.FRIDAY -> "Fri"
            DayOfWeek.SATURDAY -> "Sat"
            DayOfWeek.SUNDAY -> "Sun"
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