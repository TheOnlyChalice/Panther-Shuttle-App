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

class DriverNotifyFragment : Fragment(R.layout.fragment_driver_notify) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val firebase = FirebaseRepo()

        val stopDropdown = view.findViewById<MaterialAutoCompleteTextView>(R.id.stopDropdown)
        val titleInput = view.findViewById<TextInputEditText>(R.id.titleInput)
        val messageInput = view.findViewById<TextInputEditText>(R.id.messageInput)
        val sendButton = view.findViewById<MaterialButton>(R.id.sendButton)
        val status = view.findViewById<TextView>(R.id.sendStatus)

        val stops = ScheduleData.stopNames()
        val options = ArrayList<String>().apply {
            add("All Stops")
            addAll(stops)
        }

        stopDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)
        )
        stopDropdown.setText("All Stops", false)

        sendButton.setOnClickListener {
            val choice = stopDropdown.text?.toString()?.trim().orEmpty()
            val targetStop = if (choice.equals("All Stops", ignoreCase = true) || choice.isBlank()) null else choice

            val title = titleInput.text?.toString()?.trim().orEmpty()
            val message = messageInput.text?.toString()?.trim().orEmpty()

            if (message.isBlank()) {
                Toast.makeText(requireContext(), "Message can't be empty.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            status.text = "Sending…"
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = firebase.ensureSignedIn()
                if (!ok) {
                    status.text = "Firebase offline."
                    Toast.makeText(requireContext(), "Firebase offline; couldn't send.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    firebase.sendDriverNotification(
                        targetStopName = targetStop,
                        title = title.ifBlank { "Driver Update" },
                        message = message
                    )
                    status.text = "Sent${if (targetStop == null) " to everyone" else " to $targetStop"}."
                    Toast.makeText(requireContext(), "Sent!", Toast.LENGTH_SHORT).show()
                    messageInput.setText("")
                } catch (e: Exception) {
                    status.text = "Failed to send."
                    Toast.makeText(requireContext(), "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}