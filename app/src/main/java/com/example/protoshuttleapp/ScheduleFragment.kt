package com.example.protoshuttleapp.ui.schedule

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R
import com.google.android.material.tabs.TabLayout
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ScheduleAdapter
    private lateinit var tabs: TabLayout

    // Tabs shown in order (Mon -> Sun)
    private val days = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // IMPORTANT: Match your XML ids
        recycler = view.findViewById(R.id.scheduleRecycler)
        tabs = view.findViewById(R.id.scheduleTabs)

        recycler.layoutManager = LinearLayoutManager(requireContext())

        // Start with today's schedule
        adapter = ScheduleAdapter(ScheduleData.forToday())
        recycler.adapter = adapter

        // Build tabs (Mon-Sun)
        tabs.removeAllTabs()
        for (d in days) {
            tabs.addTab(tabs.newTab().setText(shortName(d)))
        }

        // When a tab is selected, show that day's schedule
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val pos = tab.position
                if (pos in days.indices) {
                    setScheduleFor(days[pos])
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Auto-select today's tab
        val todayDow = LocalDate.now().dayOfWeek
        val todayIndex = days.indexOf(todayDow).coerceAtLeast(0)
        tabs.getTabAt(todayIndex)?.select()
    }

    private fun setScheduleFor(dow: DayOfWeek) {
        val data = ScheduleData.forDay(dow)
        adapter = ScheduleAdapter(data)
        recycler.adapter = adapter
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
}
