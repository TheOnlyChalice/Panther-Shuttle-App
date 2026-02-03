package com.example.protoshuttleapp.ui.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.ui.schedule.ScheduleData
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var store: SettingStore

    private lateinit var unitsSwitch: SwitchMaterial
    private lateinit var serviceAlertsSwitch: SwitchMaterial
    private lateinit var stopApproachingSwitch: SwitchMaterial

    private lateinit var addFavoriteBtn: MaterialButton
    private lateinit var favoritesRecycler: RecyclerView
    private lateinit var favoritesAdapter: FavoriteStopsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        store = SettingStore(requireContext())

        // controls
        unitsSwitch = view.findViewById(R.id.unitsSwitch)
        serviceAlertsSwitch = view.findViewById(R.id.serviceAlertsSwitch)
        stopApproachingSwitch = view.findViewById(R.id.stopApproachingSwitch)

        addFavoriteBtn = view.findViewById(R.id.addFavoriteStopButton)
        favoritesRecycler = view.findViewById(R.id.favoriteStopsRecycler)

        // stop names come from your schedule system
        val stopNames = try {
            ScheduleData.stopNames()
        } catch (_: Exception) {
            // fallback so settings never crashes
            listOf("Campus", "Panther Bay", "Mary Star")
        }

        // load saved favorites (or start empty)
        val favorites = store.getFavoriteStops().toMutableList()

        favoritesAdapter = FavoriteStopsAdapter(
            stopNames = stopNames,
            items = favorites,
            onPickTime = { position -> showTimePicker(position) },
            onRemove = { position -> removeFavorite(position) },
            onStopChanged = { position, newStop -> updateFavoriteStop(position, newStop) }
        )

        favoritesRecycler.layoutManager = LinearLayoutManager(requireContext())
        favoritesRecycler.adapter = favoritesAdapter

        // Switches (restore)
        unitsSwitch.isChecked = store.useMiles
        unitsSwitch.text = if (store.useMiles) "Miles" else "Km"

        serviceAlertsSwitch.isChecked = store.serviceAlertsOn
        stopApproachingSwitch.isChecked = store.stopApproachingOn

        // Switch listeners
        unitsSwitch.setOnCheckedChangeListener { _, isChecked ->
            store.useMiles = isChecked
            unitsSwitch.text = if (isChecked) "Miles" else "Km"
        }

        serviceAlertsSwitch.setOnCheckedChangeListener { _, isChecked ->
            store.serviceAlertsOn = isChecked
        }

        stopApproachingSwitch.setOnCheckedChangeListener { _, isChecked ->
            store.stopApproachingOn = isChecked
        }

        // Add button
        addFavoriteBtn.setOnClickListener {
            if (stopNames.isEmpty()) {
                Toast.makeText(requireContext(), "No stops available yet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val defaultStop = stopNames.first()
            val defaultTime = 8 * 60 // 8:00 AM

            val newList = favoritesAdapter.getItems().toMutableList()
            newList.add(FavoriteStop(stopName = defaultStop, timeMinutes = defaultTime))
            store.setFavoriteStops(newList)
            favoritesAdapter.replaceAll(newList)
        }
    }

    private fun removeFavorite(position: Int) {
        val list = favoritesAdapter.getItems().toMutableList()
        if (position < 0 || position >= list.size) return

        list.removeAt(position)
        store.setFavoriteStops(list)
        favoritesAdapter.replaceAll(list)
    }

    private fun updateFavoriteStop(position: Int, newStop: String) {
        val list = favoritesAdapter.getItems().toMutableList()
        if (position < 0 || position >= list.size) return

        val old = list[position]
        list[position] = old.copy(stopName = newStop)

        store.setFavoriteStops(list)
        favoritesAdapter.replaceAll(list)
    }

    private fun showTimePicker(position: Int) {
        val list = favoritesAdapter.getItems().toMutableList()
        if (position < 0 || position >= list.size) return

        val current = list[position]
        val h = (current.timeMinutes / 60) % 24
        val m = current.timeMinutes % 60

        val dlg = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val updatedMinutes = hourOfDay * 60 + minute
                val updated = current.copy(timeMinutes = updatedMinutes)
                list[position] = updated

                store.setFavoriteStops(list)
                favoritesAdapter.replaceAll(list)
            },
            h,
            m,
            false
        )

        dlg.show()
    }
}
