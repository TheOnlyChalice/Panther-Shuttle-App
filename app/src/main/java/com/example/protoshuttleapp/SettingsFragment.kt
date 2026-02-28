package com.example.protoshuttleapp.ui.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.ui.schedule.ScheduleData
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var store: SettingStore

    private lateinit var unitsSwitch: SwitchMaterial
    private lateinit var serviceAlertsSwitch: SwitchMaterial
    private lateinit var stopApproachingSwitch: SwitchMaterial

    private lateinit var addFavoriteBtn: MaterialButton
    private lateinit var favoritesRecycler: RecyclerView
    private lateinit var favoritesAdapter: FavoriteStopsAdapter

    private val firebase = FirebaseRepo()
    private var isFirebaseReady = false
    private var favoritesListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        store = SettingStore(requireContext())

        // controls
        unitsSwitch = view.findViewById(R.id.unitsSwitch)
        serviceAlertsSwitch = view.findViewById(R.id.serviceAlertsSwitch)
        stopApproachingSwitch = view.findViewById(R.id.stopApproachingSwitch)

        addFavoriteBtn = view.findViewById(R.id.addFavoriteStopButton)
        favoritesRecycler = view.findViewById(R.id.favoriteStopsRecycler)

        // stop names come from schedule system
        val stopNames = try {
            ScheduleData.stopNames()
        } catch (_: Exception) {
            listOf("Campus", "Panther Bay", "Mary Star")
        }

        // local fallback list (used immediately so UI never blocks)
        val initialFavorites = store.getFavoriteStops().toMutableList()

        favoritesAdapter = FavoriteStopsAdapter(
            stopNames = stopNames,
            items = initialFavorites,
            onPickTime = { position -> showTimePicker(position) },
            onRemove = { position -> removeFavorite(position) },
            onStopChanged = { position, newStop -> updateFavoriteStop(position, newStop) }
        )

        favoritesRecycler.layoutManager = LinearLayoutManager(requireContext())
        favoritesRecycler.adapter = favoritesAdapter

        // Switch restore
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

        // ✅ Firebase: sign in + listen for favorites from Firestore
        viewLifecycleOwner.lifecycleScope.launch {
            isFirebaseReady = firebase.ensureSignedIn()
            if (!isFirebaseReady) {
                context?.let {
                    Toast.makeText(it, "Firebase offline; using local favorites only.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            favoritesListener?.remove()
            favoritesListener = firebase.listenFavoriteStops { docs ->
                val mapped = docs
                    .map { FavoriteStop(stopName = it.stopName, timeMinutes = it.timeMinutes) }
                    .sortedWith(compareBy({ it.stopName }, { it.timeMinutes }))

                store.setFavoriteStops(mapped)
                favoritesAdapter.replaceAll(mapped)
            }
        }

        // Add button
        addFavoriteBtn.setOnClickListener {
            if (stopNames.isEmpty()) {
                Toast.makeText(requireContext(), "No stops available yet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val defaultStop = stopNames.first()
            val defaultTime = 8 * 60 // 8:00 AM

            val newFav = FavoriteStop(stopName = defaultStop, timeMinutes = defaultTime)

            val newList = favoritesAdapter.getItems().toMutableList()
            newList.add(newFav)

            // local save immediately (UI feels instant)
            store.setFavoriteStops(newList)
            favoritesAdapter.replaceAll(newList)

            // ✅ Firestore upsert (guard + catch)
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = firebase.ensureSignedIn()
                if (!ok) {
                    context?.let {
                        Toast.makeText(it, "Saved locally (Firebase offline).", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                try {
                    firebase.upsertFavoriteStop(newFav.stopName, newFav.timeMinutes)
                } catch (e: Exception) {
                    Log.e("SettingsFragment", "upsertFavoriteStop failed", e)
                    context?.let {
                        Toast.makeText(it, "Saved locally (sync failed).", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        favoritesListener?.remove()
        favoritesListener = null
        super.onDestroyView()
    }

    private fun removeFavorite(position: Int) {
        val list = favoritesAdapter.getItems().toMutableList()
        if (position !in list.indices) return

        val removed = list[position]
        list.removeAt(position)

        store.setFavoriteStops(list)
        favoritesAdapter.replaceAll(list)

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) return@launch

            try {
                firebase.deleteFavoriteStop(removed.stopName, removed.timeMinutes)
            } catch (e: Exception) {
                Log.e("SettingsFragment", "deleteFavoriteStop failed", e)
            }
        }
    }

    private fun updateFavoriteStop(position: Int, newStop: String) {
        val list = favoritesAdapter.getItems().toMutableList()
        if (position !in list.indices) return

        val old = list[position]
        val updated = old.copy(stopName = newStop)
        list[position] = updated

        store.setFavoriteStops(list)
        favoritesAdapter.replaceAll(list)

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) return@launch

            try {
                // doc id depends on stop+time so we must delete old doc and add new doc
                firebase.deleteFavoriteStop(old.stopName, old.timeMinutes)
                firebase.upsertFavoriteStop(updated.stopName, updated.timeMinutes)
            } catch (e: Exception) {
                Log.e("SettingsFragment", "updateFavoriteStop sync failed", e)
            }
        }
    }

    private fun showTimePicker(position: Int) {
        val list = favoritesAdapter.getItems().toMutableList()
        if (position !in list.indices) return

        val current = list[position]
        val h = (current.timeMinutes / 60) % 24
        val m = current.timeMinutes % 60

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val updatedMinutes = hourOfDay * 60 + minute
                val updated = current.copy(timeMinutes = updatedMinutes)
                list[position] = updated

                store.setFavoriteStops(list)
                favoritesAdapter.replaceAll(list)

                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = firebase.ensureSignedIn()
                    if (!ok) return@launch

                    try {
                        firebase.deleteFavoriteStop(current.stopName, current.timeMinutes)
                        firebase.upsertFavoriteStop(updated.stopName, updated.timeMinutes)
                    } catch (e: Exception) {
                        Log.e("SettingsFragment", "time update sync failed", e)
                    }
                }
            },
            h, m, false
        ).show()
    }
}