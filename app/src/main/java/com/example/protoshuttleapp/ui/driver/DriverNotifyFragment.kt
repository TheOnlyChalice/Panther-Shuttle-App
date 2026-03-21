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
import com.example.protoshuttleapp.ui.schedule.ScheduleData
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

class DriverNotifyFragment : Fragment(R.layout.fragment_driver_notify) {

    private val firebase = FirebaseRepo()

    private lateinit var stopDropdown: MaterialAutoCompleteTextView
    private lateinit var titleInput: TextInputEditText
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private lateinit var sendStatus: TextView
    private lateinit var nextStopText: TextView
    private lateinit var nextStopEstimateText: TextView

    private val timeParser: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("h:mma")
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .toFormatter(Locale.US)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stopDropdown = view.findViewById(R.id.stopDropdown)
        titleInput = view.findViewById(R.id.titleInput)
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        sendStatus = view.findViewById(R.id.sendStatus)
        nextStopText = view.findViewById(R.id.nextStopText)
        nextStopEstimateText = view.findViewById(R.id.nextStopEstimateText)

        setupDropdown()
        loadNextStopInfo()

        sendButton.setOnClickListener {
            sendNotification()
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::nextStopText.isInitialized && this::nextStopEstimateText.isInitialized) {
            loadNextStopInfo()
        }
    }

    private fun setupDropdown() {
        val stops = ScheduleData.stopNames()
        val options = ArrayList<String>().apply {
            add("All Stops")
            addAll(stops)
        }

        stopDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)
        )
        stopDropdown.setText("All Stops", false)
    }

    private fun sendNotification() {
        val choice = stopDropdown.text?.toString()?.trim().orEmpty()
        val targetStop = if (choice.equals("All Stops", ignoreCase = true) || choice.isBlank()) {
            null
        } else {
            choice
        }

        val title = titleInput.text?.toString()?.trim().orEmpty()
        val message = messageInput.text?.toString()?.trim().orEmpty()

        if (message.isBlank()) {
            Toast.makeText(requireContext(), "Message can't be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        sendStatus.text = "Sending…"

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
                    message = message
                )

                sendStatus.text = "Sent${if (targetStop == null) " to everyone" else " to $targetStop"}."
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
            nextStopEstimateText.text = "Estimate error: no upcoming stop."
            return
        }

        val whenText = if (next.isTomorrow) {
            "Tomorrow at ${next.timeLabel}"
        } else {
            "Today at ${next.timeLabel}"
        }

        nextStopText.text = "Next scheduled stop: $whenText — ${next.stopName}"
        nextStopEstimateText.text = "Loading estimated students..."

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                nextStopEstimateText.text = "Estimate error: Firebase sign-in failed."
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
                val msg = e.message ?: e.javaClass.simpleName
                nextStopEstimateText.text = "Estimate error: $msg"
            }
        }
    }

    private fun findNextScheduledStop(): UpcomingStopInfo? {
        val nowMinutes = LocalTime.now().hour * 60 + LocalTime.now().minute
        val todaySchedule = ScheduleData.forToday()

        for (stop in todaySchedule) {
            val stopMinutes = parseTimeToMinutes(stop.time)
            if (stopMinutes >= nowMinutes) {
                return UpcomingStopInfo(
                    stopName = stop.name,
                    timeLabel = stop.time,
                    timeMinutes = stopMinutes,
                    isTomorrow = false
                )
            }
        }

        val tomorrowDay = LocalDate.now().plusDays(1).dayOfWeek
        val tomorrowSchedule = ScheduleData.forDay(tomorrowDay)
        val firstTomorrow = tomorrowSchedule.firstOrNull() ?: return null

        return UpcomingStopInfo(
            stopName = firstTomorrow.name,
            timeLabel = firstTomorrow.time,
            timeMinutes = parseTimeToMinutes(firstTomorrow.time),
            isTomorrow = true
        )
    }

    private fun parseTimeToMinutes(timeText: String): Int {
        val normalized = timeText.trim().uppercase(Locale.US)
        val parsed = LocalTime.parse(normalized, timeParser)
        return parsed.hour * 60 + parsed.minute
    }

    private data class UpcomingStopInfo(
        val stopName: String,
        val timeLabel: String,
        val timeMinutes: Int,
        val isTomorrow: Boolean
    )
}