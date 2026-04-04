package com.example.protoshuttleapp.ui.manager

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.data.ManagerScheduleEntryDoc
import com.example.protoshuttleapp.data.ManagerStopDoc
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class ManagerScheduleFragment : Fragment(R.layout.fragment_manager_schedule) {

    private val firebase = FirebaseRepo()

    private lateinit var addScheduleButton: MaterialButton
    private lateinit var scheduleRecycler: RecyclerView
    private lateinit var scheduleStatusText: TextView
    private lateinit var emptyScheduleText: TextView
    private lateinit var scheduleTabs: TabLayout
    private lateinit var scheduleAdapter: ManagerScheduleAdapter

    private var stopsListener: ListenerRegistration? = null
    private var scheduleListener: ListenerRegistration? = null

    private val currentStops = mutableListOf<ManagerStopDoc>()
    private val allSchedule = mutableListOf<ManagerScheduleEntryDoc>()
    private val visibleSchedule = mutableListOf<ManagerScheduleEntryDoc>()

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

        addScheduleButton = view.findViewById(R.id.addScheduleButton)
        scheduleRecycler = view.findViewById(R.id.scheduleRecycler)
        scheduleStatusText = view.findViewById(R.id.scheduleStatusText)
        emptyScheduleText = view.findViewById(R.id.emptyScheduleText)
        scheduleTabs = view.findViewById(R.id.managerScheduleTabs)

        scheduleAdapter = ManagerScheduleAdapter(
            items = visibleSchedule,
            onTap = { entry -> showScheduleEntryOptions(entry) }
        )

        scheduleRecycler.layoutManager = LinearLayoutManager(requireContext())
        scheduleRecycler.adapter = scheduleAdapter

        buildTabs()

        addScheduleButton.setOnClickListener {
            showScheduleEditor(existing = null)
        }

        startListeners()
        applyDayFilter()
    }

    override fun onDestroyView() {
        stopsListener?.remove()
        scheduleListener?.remove()
        stopsListener = null
        scheduleListener = null
        super.onDestroyView()
    }

    private fun buildTabs() {
        scheduleTabs.removeAllTabs()

        for (day in days) {
            scheduleTabs.addTab(scheduleTabs.newTab().setText(shortName(day)))
        }

        scheduleTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val day = days.getOrNull(tab.position) ?: DayOfWeek.MONDAY
                selectedDayOfWeek = day.toManagerDayValue()
                applyDayFilter()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        val initialIndex = days.indexOfFirst { it.toManagerDayValue() == selectedDayOfWeek }
            .takeIf { it >= 0 } ?: 0

        scheduleTabs.getTabAt(initialIndex)?.select()
    }

    private fun startListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                scheduleStatusText.text = "Firebase sign-in failed."
                return@launch
            }

            stopsListener?.remove()
            scheduleListener?.remove()

            stopsListener = firebase.listenManagerStops { stops ->
                currentStops.clear()
                currentStops.addAll(stops.sortedBy { it.stopName.lowercase(Locale.US) })
            }

            scheduleListener = firebase.listenManagerSchedule { entries ->
                allSchedule.clear()
                allSchedule.addAll(entries)
                applyDayFilter()
            }
        }
    }

    private fun applyDayFilter() {
        visibleSchedule.clear()
        visibleSchedule.addAll(
            allSchedule
                .filter { normalizeDayOfWeek(it.dayOfWeek) == selectedDayOfWeek }
                .sortedWith(
                    compareBy<ManagerScheduleEntryDoc>(
                        { it.timeMinutes },
                        { it.stopName.lowercase(Locale.US) }
                    )
                )
        )

        scheduleAdapter.notifyDataSetChanged()
        renderEmptyState()
    }

    private fun renderEmptyState() {
        val dayLabel = dayName(selectedDayOfWeek)
        emptyScheduleText.visibility = if (visibleSchedule.isEmpty()) View.VISIBLE else View.GONE
        if (visibleSchedule.isEmpty()) {
            emptyScheduleText.text = "No schedule entries for $dayLabel yet."
        }
    }

    private fun showScheduleEntryOptions(entry: ManagerScheduleEntryDoc) {
        val options = arrayOf("Edit Entry", "Delete Entry")

        AlertDialog.Builder(requireContext())
            .setTitle("${entry.stopName} — ${dayName(entry.dayOfWeek)} at ${formatMinutes(entry.timeMinutes)}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showScheduleEditor(existing = entry)
                    1 -> showDeleteScheduleDialog(entry)
                }
            }
            .show()
    }

    private fun showDeleteScheduleDialog(entry: ManagerScheduleEntryDoc) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Schedule Entry")
            .setMessage("Delete ${entry.stopName} on ${dayName(entry.dayOfWeek)} at ${formatMinutes(entry.timeMinutes)}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                scheduleStatusText.text = "Deleting schedule entry..."

                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = firebase.ensureSignedIn()
                    if (!ok) {
                        scheduleStatusText.text = "Firebase sign-in failed."
                        return@launch
                    }

                    try {
                        firebase.deleteManagerScheduleEntry(entry.id)
                        scheduleStatusText.text = "Deleted schedule entry."
                    } catch (e: Exception) {
                        scheduleStatusText.text = "Failed to delete entry: ${e.message}"
                    }
                }
            }
            .show()
    }

    private fun showScheduleEditor(existing: ManagerScheduleEntryDoc?) {
        if (currentStops.isEmpty()) {
            Toast.makeText(requireContext(), "Add at least one stop on the map first.", Toast.LENGTH_SHORT).show()
            return
        }

        val stopNames = currentStops.map { it.stopName }
        var selectedMinutes = existing?.timeMinutes ?: (8 * 60)
        var selectedDay = normalizeDayOfWeek(existing?.dayOfWeek ?: selectedDayOfWeek)

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val stopLabel = TextView(requireContext()).apply {
            text = "Stop"
            textSize = 16f
        }

        val stopSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                stopNames
            )
        }

        val initialStopIndex = if (existing == null) {
            0
        } else {
            currentStops.indexOfFirst { it.id == existing.stopId }.let { if (it >= 0) it else 0 }
        }
        stopSpinner.setSelection(initialStopIndex)

        val dayLabel = TextView(requireContext()).apply {
            text = "Day of Week"
            textSize = 16f
        }

        val daySpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            )
            setSelection(selectedDay - 1)
        }

        val timeLabel = TextView(requireContext()).apply {
            text = "Time"
            textSize = 16f
        }

        val timeButton = MaterialButton(requireContext()).apply {
            text = formatMinutes(selectedMinutes)
            setOnClickListener {
                val hour = selectedMinutes / 60
                val minute = selectedMinutes % 60

                TimePickerDialog(
                    requireContext(),
                    { _, pickedHour, pickedMinute ->
                        selectedMinutes = pickedHour * 60 + pickedMinute
                        text = formatMinutes(selectedMinutes)
                    },
                    hour,
                    minute,
                    false
                ).show()
            }
        }

        container.addView(stopLabel)
        container.addView(stopSpinner)
        container.addView(dayLabel)
        container.addView(daySpinner)
        container.addView(timeLabel)
        container.addView(timeButton)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "Add Schedule Entry" else "Edit Schedule Entry")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedStop = currentStops.getOrNull(stopSpinner.selectedItemPosition)
                if (selectedStop == null) {
                    Toast.makeText(requireContext(), "Please select a stop.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                selectedDay = daySpinner.selectedItemPosition + 1

                scheduleStatusText.text = if (existing == null) {
                    "Saving schedule entry..."
                } else {
                    "Updating schedule entry..."
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = firebase.ensureSignedIn()
                    if (!ok) {
                        scheduleStatusText.text = "Firebase sign-in failed."
                        return@launch
                    }

                    try {
                        if (existing == null) {
                            firebase.createManagerScheduleEntry(
                                stopId = selectedStop.id,
                                stopName = selectedStop.stopName,
                                dayOfWeek = selectedDay,
                                timeMinutes = selectedMinutes
                            )
                            scheduleStatusText.text = "Added schedule entry."
                        } else {
                            firebase.updateManagerScheduleEntry(
                                entryId = existing.id,
                                stopId = selectedStop.id,
                                stopName = selectedStop.stopName,
                                dayOfWeek = selectedDay,
                                timeMinutes = selectedMinutes
                            )
                            scheduleStatusText.text = "Updated schedule entry."
                        }
                        dialog.dismiss()
                    } catch (e: Exception) {
                        scheduleStatusText.text = "Failed to save entry: ${e.message}"
                    }
                }
            }
        }

        dialog.show()
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

    private fun shortName(day: DayOfWeek): String {
        return when (day) {
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