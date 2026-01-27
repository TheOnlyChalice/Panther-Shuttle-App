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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recycler = view.findViewById(R.id.scheduleRecycler)
        tabs = view.findViewById(R.id.scheduleTabs)

        recycler.layoutManager = LinearLayoutManager(requireContext())

        val days = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        )

        days.forEach { d ->
            val label = d.name.lowercase().replaceFirstChar { it.uppercase() }
            tabs.addTab(tabs.newTab().setText(label))
        }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selectedDow = days[tab.position]
                setScheduleFor(selectedDow)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        val today = LocalDate.now()
        val todayIndex = days.indexOf(today.dayOfWeek).coerceAtLeast(0)
        tabs.getTabAt(todayIndex)?.select() ?: setScheduleFor(today.dayOfWeek)
    }

    private fun setScheduleFor(dow: DayOfWeek) {
        val today = LocalDate.now()
        val offset = dow.value - today.dayOfWeek.value
        val date = today.plusDays(offset.toLong())

        val data = ScheduleData.getFor(date)
        adapter = ScheduleAdapter(data)
        recycler.adapter = adapter
    }
}
