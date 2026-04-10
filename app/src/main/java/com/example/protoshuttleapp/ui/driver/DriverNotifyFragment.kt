package com.example.protoshuttleapp.ui.driver

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.data.ManagerScheduleEntryDoc
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class DriverNotifyFragment : Fragment(R.layout.fragment_driver_notify) {

    private val firebase = FirebaseRepo()

    private lateinit var stopDropdown: MaterialAutoCompleteTextView
    private lateinit var timeDropdown: MaterialAutoCompleteTextView
    private lateinit var timeDropdownLayout: TextInputLayout
    private lateinit var titleInput: TextInputEditText
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private lateinit var sendStatus: TextView
    private lateinit var nextStopText: TextView
    private lateinit var nextStopEstimateText: TextView

    private var scheduleListener: ListenerRegistration? = null
    private var clockJob: Job? = null

    private val allScheduleEntries = mutableListOf<ManagerScheduleEntryDoc>()

    private var currentStopOptions: List<String> = emptyList()
    private var currentTimeOptions: List<TimeOption> = emptyList()

    private data class TimeOption(
        val label: String,
        val timeMinutes: Int? = null
    )

    private data class UpcomingStopInfo(
        val stopName: String,
        val timeMinutes: Int,
        val dayOffset: Int
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stopDropdown = view.findViewById(R.id.stopDropdown)
        timeDropdown = view.findViewById(R.id.timeDropdown)
        timeDropdownLayout = view.findViewById(R.id.timeDropdownLayout)
        titleInput = view.findViewById(R.id.titleInput)
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        sendStatus = view.findViewById(R.id.sendStatus)
        nextStopText = view.findViewById(R.id.nextStopText)
        nextStopEstimateText = view.findViewById(R.id.nextStopEstimateText)

        setupDropdownListeners()
        startScheduleListener()
        startClockTicker()

        sendButton.setOnClickListener {
            sendNotification()
        }
    }

    override fun onDestroyView() {
        scheduleListener?.remove()
        scheduleListener = null
        clockJob?.cancel()
        clockJob = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        renderFromSchedule()
    }

    private fun setupDropdownListeners() {
        stopDropdown.setOnItemClickListener { _, _, _, _ ->
            refreshTimeOptions(preserveCurrentSelection = false)
        }
    }

    private fun startScheduleListener() {
        sendStatus.text = ""
        nextStopText.text = "Loading next stop..."
        nextStopEstimateText.text = "Loading estimated students..."

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                nextStopText.text = "Schedule unavailable."
                nextStopEstimateText.text = "Estimated students unavailable."
                return@launch
            }

            scheduleListener?.remove()
            scheduleListener = firebase.listenManagerSchedule { entries ->
                allScheduleEntries.clear()
                allScheduleEntries.addAll(entries)
                renderFromSchedule()
            }
        }
    }

    private fun startClockTicker() {
        clockJob?.cancel()
        clockJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                renderFromSchedule()
                delay(30_000L)
            }
        }
    }

    private fun renderFromSchedule() {
        refreshStopOptions()
        refreshTimeOptions(preserveCurrentSelection = true)
        loadNextStopInfo()
    }

    private fun refreshStopOptions() {
        val currentSelection = stopDropdown.text?.toString()?.trim().orEmpty()
        val nextUpcoming = findNextScheduledStop()
        val scheduleForChoices = scheduleEntriesForNotificationChoices()

        val stops = scheduleForChoices
            .map { it.stopName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        currentStopOptions = listOf("All Stops") + stops

        stopDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, currentStopOptions)
        )

        val stopToSelect = when {
            currentSelection in currentStopOptions -> currentSelection
            nextUpcoming != null && nextUpcoming.stopName in currentStopOptions -> nextUpcoming.stopName
            else -> "All Stops"
        }

        stopDropdown.setText(stopToSelect, false)
    }

    private fun refreshTimeOptions(preserveCurrentSelection: Boolean) {
        val selectedStop = stopDropdown.text?.toString()?.trim().orEmpty()
        val currentTimeText = timeDropdown.text?.toString()?.trim().orEmpty()

        if (selectedStop.isBlank() || selectedStop.equals("All Stops", ignoreCase = true)) {
            currentTimeOptions = listOf(TimeOption(label = "Any Time", timeMinutes = null))
            timeDropdown.setAdapter(
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    currentTimeOptions.map { it.label }
                )
            )
            timeDropdown.setText("Any Time", false)
            timeDropdownLayout.isEnabled = false
            timeDropdown.isEnabled = false
            return
        }

        val entriesForStop = scheduleEntriesForNotificationChoices()
            .filter { it.stopName == selectedStop }
            .sortedBy { it.timeMinutes }

        val timeOptions = mutableListOf(TimeOption(label = "Any Time", timeMinutes = null))
        timeOptions.addAll(
            entriesForStop
                .map { it.timeMinutes }
                .distinct()
                .sorted()
                .map { minutes -> TimeOption(label = formatMinutes(minutes), timeMinutes = minutes) }
        )

        currentTimeOptions = timeOptions

        timeDropdown.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                currentTimeOptions.map { it.label }
            )
        )

        timeDropdownLayout.isEnabled = true
        timeDropdown.isEnabled = true

        val defaultSelection = if (preserveCurrentSelection && currentTimeOptions.any { it.label == currentTimeText }) {
            currentTimeText
        } else {
            val nextForSameStop = findNextScheduledStop()
                ?.takeIf { it.stopName == selectedStop }
                ?.let { formatMinutes(it.timeMinutes) }

            when {
                nextForSameStop != null && currentTimeOptions.any { it.label == nextForSameStop } -> nextForSameStop
                else -> "Any Time"
            }
        }

        timeDropdown.setText(defaultSelection, false)
    }

    private fun scheduleEntriesForNotificationChoices(): List<ManagerScheduleEntryDoc> {
        val nextUpcoming = findNextScheduledStop()

        val chosenDay = if (nextUpcoming != null) {
            dayValueForOffset(nextUpcoming.dayOffset)
        } else {
            LocalDate.now().dayOfWeek.toManagerDayValue()
        }

        return allScheduleEntries.filter { normalizeDayOfWeek(it.dayOfWeek) == chosenDay }
    }

    private fun sendNotification() {
        val selectedStop = stopDropdown.text?.toString()?.trim().orEmpty()
        val targetStop = if (selectedStop.equals("All Stops", ignoreCase = true) || selectedStop.isBlank()) {
            null
        } else {
            selectedStop
        }

        val selectedTimeLabel = timeDropdown.text?.toString()?.trim().orEmpty()
        val selectedTime = currentTimeOptions.firstOrNull { it.label == selectedTimeLabel }?.timeMinutes
        val exactTimeMatch = targetStop != null && selectedTime != null

        val title = titleInput.text?.toString()?.trim().orEmpty()
        val message = messageInput.text?.toString()?.trim().orEmpty()

        if (message.isBlank()) {
            Toast.makeText(requireContext(), "Message can't be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        sendStatus.text = "Sending..."

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                sendStatus.text = "Firebase offline."
                Toast.makeText(requireContext(), "Firebase offline; couldn't send.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                firebase.sendDriverNotification(
                    targetStopName = targetStop,
                    title = title.ifBlank { "Driver Update" },
                    message = message,
                    timeMinutes = selectedTime,
                    exactTimeMatch = exactTimeMatch
                )

                sendStatus.text = when {
                    targetStop == null -> "Sent to everyone."
                    selectedTime != null -> "Sent to $targetStop at ${formatMinutes(selectedTime)}."
                    else -> "Sent to $targetStop."
                }

                Toast.makeText(requireContext(), "Sent!", Toast.LENGTH_SHORT).show()
                messageInput.setText("")
            } catch (e: Exception) {
                sendStatus.text = "Failed to send."
                Toast.makeText(requireContext(), "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadNextStopInfo() {
        val next = findNextScheduledStop()

        if (next == null) {
            nextStopText.text = "No upcoming stop found."
            nextStopEstimateText.text = "Estimated students unavailable."
            return
        }

        val whenText = when (next.dayOffset) {
            0 -> "Today at ${formatMinutes(next.timeMinutes)}"
            1 -> "Tomorrow at ${formatMinutes(next.timeMinutes)}"
            else -> "${dayName(dayValueForOffset(next.dayOffset))} at ${formatMinutes(next.timeMinutes)}"
        }

        nextStopText.text = "Next scheduled stop: $whenText — ${next.stopName}"
        nextStopEstimateText.text = "Loading estimated students..."

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                nextStopEstimateText.text = "Estimated students unavailable."
                return@launch
            }

            try {
                val count = firebase.countIndexedFavoriteUsersForStopAroundTime(
                    stopName = next.stopName,
                    targetTimeMinutes = next.timeMinutes,
                    windowMinutes = 15
                )

                nextStopEstimateText.text = "Estimated students near this stop/time: $count"
            } catch (e: Exception) {
                nextStopEstimateText.text = "Estimated students unavailable."
            }
        }
    }

    private fun findNextScheduledStop(): UpcomingStopInfo? {
        if (allScheduleEntries.isEmpty()) return null

        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute

        for (dayOffset in 0..6) {
            val dayValue = dayValueForOffset(dayOffset)

            val entriesForDay = allScheduleEntries
                .filter { normalizeDayOfWeek(it.dayOfWeek) == dayValue }
                .sortedWith(compareBy<ManagerScheduleEntryDoc>({ it.timeMinutes }, { it.stopName.lowercase(Locale.US) }))

            val nextEntry = if (dayOffset == 0) {
                entriesForDay.firstOrNull { it.timeMinutes >= nowMinutes }
            } else {
                entriesForDay.firstOrNull()
            }

            if (nextEntry != null) {
                return UpcomingStopInfo(
                    stopName = nextEntry.stopName,
                    timeMinutes = nextEntry.timeMinutes,
                    dayOffset = dayOffset
                )
            }
        }

        return null
    }

    private fun dayValueForOffset(offset: Int): Int {
        return LocalDate.now().plusDays(offset.toLong()).dayOfWeek.toManagerDayValue()
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