package com.example.protoshuttleapp.ui.settings

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
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlin.math.abs

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var store: SettingStore

    private lateinit var unitsSwitch: SwitchMaterial
    private lateinit var serviceAlertsSwitch: SwitchMaterial
    private lateinit var stopApproachingSwitch: SwitchMaterial

    private lateinit var addFavoriteBtn: MaterialButton
    private lateinit var favoritesRecycler: RecyclerView
    private lateinit var favoritesAdapter: FavoriteStopsAdapter

    private val firebase = FirebaseRepo()

    private var favoritesListener: ListenerRegistration? = null
    private var managerScheduleListener: ListenerRegistration? = null

    private var availableStopNames: List<String> = emptyList()
    private var availableTimesByStop: Map<String, List<Int>> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        store = SettingStore(requireContext())

        unitsSwitch = view.findViewById(R.id.unitsSwitch)
        serviceAlertsSwitch = view.findViewById(R.id.serviceAlertsSwitch)
        stopApproachingSwitch = view.findViewById(R.id.stopApproachingSwitch)

        addFavoriteBtn = view.findViewById(R.id.addFavoriteStopButton)
        favoritesRecycler = view.findViewById(R.id.favoriteStopsRecycler)

        val initialFavorites = store.getFavoriteStops().toMutableList()

        favoritesAdapter = FavoriteStopsAdapter(
            stopNames = emptyList(),
            timeOptionsByStop = emptyMap(),
            items = initialFavorites,
            onRemove = { position -> removeFavorite(position) },
            onStopChanged = { position, newStop -> updateFavoriteStop(position, newStop) },
            onTimeChanged = { position, newTime -> updateFavoriteTime(position, newTime) }
        )

        favoritesRecycler.layoutManager = LinearLayoutManager(requireContext())
        favoritesRecycler.adapter = favoritesAdapter

        unitsSwitch.isChecked = store.useMiles
        unitsSwitch.text = if (store.useMiles) "Miles" else "Km"
        serviceAlertsSwitch.isChecked = store.serviceAlertsOn
        stopApproachingSwitch.isChecked = store.stopApproachingOn

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

        startFirebaseListeners()

        addFavoriteBtn.setOnClickListener {
            val firstStop = availableStopNames.firstOrNull()
            if (firstStop == null) {
                Toast.makeText(
                    requireContext(),
                    "Manager schedule has not loaded yet.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val firstTime = availableTimesByStop[firstStop]?.firstOrNull()
            if (firstTime == null) {
                Toast.makeText(
                    requireContext(),
                    "No valid times found for that stop yet.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val newFav = FavoriteStop(stopName = firstStop, timeMinutes = firstTime)
            val newList = favoritesAdapter.getItems().toMutableList().apply { add(newFav) }

            store.setFavoriteStops(newList)
            favoritesAdapter.replaceAll(newList)

            viewLifecycleOwner.lifecycleScope.launch {
                val ok = firebase.ensureSignedIn()
                if (!ok) {
                    Toast.makeText(requireContext(), "Saved locally (Firebase offline).", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    firebase.upsertFavoriteStop(newFav.stopName, newFav.timeMinutes)
                } catch (e: Exception) {
                    Log.e("SettingsFragment", "upsertFavoriteStop failed", e)
                    Toast.makeText(requireContext(), "Saved locally (sync failed).", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        favoritesListener?.remove()
        managerScheduleListener?.remove()
        favoritesListener = null
        managerScheduleListener = null
        super.onDestroyView()
    }

    private fun startFirebaseListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                Toast.makeText(
                    requireContext(),
                    "Firebase offline; using local favorites only.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            favoritesListener?.remove()
            managerScheduleListener?.remove()

            favoritesListener = firebase.listenFavoriteStops { docs ->
                val mapped = docs
                    .map { FavoriteStop(stopName = it.stopName, timeMinutes = it.timeMinutes) }
                    .sortedWith(compareBy({ it.stopName }, { it.timeMinutes }))

                store.setFavoriteStops(mapped)
                favoritesAdapter.replaceAll(mapped)

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        firebase.rebuildFavoriteStopIndexForCurrentUser(docs)
                    } catch (e: Exception) {
                        Log.e("SettingsFragment", "rebuildFavoriteStopIndexForCurrentUser failed", e)
                    }
                }

                reconcileFavoritesWithManagerSchedule()
            }

            managerScheduleListener = firebase.listenManagerSchedule { entries ->
                val stopNames = entries
                    .map { it.stopName.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                val timesByStop = entries
                    .groupBy { it.stopName.trim() }
                    .mapValues { (_, stopEntries) ->
                        stopEntries
                            .map { it.timeMinutes }
                            .distinct()
                            .sorted()
                    }
                    .filterKeys { it.isNotBlank() }

                availableStopNames = stopNames
                availableTimesByStop = timesByStop

                favoritesAdapter.updateScheduleOptions(
                    stopNames = availableStopNames,
                    timeOptionsByStop = availableTimesByStop
                )

                reconcileFavoritesWithManagerSchedule()
            }
        }
    }

    private fun reconcileFavoritesWithManagerSchedule() {
        if (availableStopNames.isEmpty()) return

        val current = favoritesAdapter.getItems()
        if (current.isEmpty()) return

        val replacements = mutableListOf<Pair<FavoriteStop, FavoriteStop>>()
        val updated = current.map { old ->
            val normalizedStop = if (old.stopName in availableStopNames) {
                old.stopName
            } else {
                availableStopNames.first()
            }

            val validTimes = availableTimesByStop[normalizedStop].orEmpty()
            val normalizedTime = when {
                validTimes.isEmpty() -> old.timeMinutes
                old.timeMinutes in validTimes -> old.timeMinutes
                else -> nearestTime(old.timeMinutes, validTimes)
            }

            val newItem = FavoriteStop(
                stopName = normalizedStop,
                timeMinutes = normalizedTime
            )

            if (newItem != old) {
                replacements.add(old to newItem)
            }

            newItem
        }

        if (replacements.isEmpty()) return

        store.setFavoriteStops(updated)
        favoritesAdapter.replaceAll(updated)

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) return@launch

            for ((oldItem, newItem) in replacements) {
                try {
                    firebase.deleteFavoriteStop(oldItem.stopName, oldItem.timeMinutes)
                    firebase.upsertFavoriteStop(newItem.stopName, newItem.timeMinutes)
                } catch (e: Exception) {
                    Log.e("SettingsFragment", "reconcileFavoritesWithManagerSchedule failed", e)
                }
            }
        }
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
        val validTimes = availableTimesByStop[newStop].orEmpty()

        val newTime = when {
            validTimes.isEmpty() -> old.timeMinutes
            old.timeMinutes in validTimes -> old.timeMinutes
            else -> validTimes.first()
        }

        val updated = old.copy(
            stopName = newStop,
            timeMinutes = newTime
        )

        list[position] = updated

        store.setFavoriteStops(list)
        favoritesAdapter.replaceAll(list)

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) return@launch

            try {
                firebase.deleteFavoriteStop(old.stopName, old.timeMinutes)
                firebase.upsertFavoriteStop(updated.stopName, updated.timeMinutes)
            } catch (e: Exception) {
                Log.e("SettingsFragment", "updateFavoriteStop sync failed", e)
            }
        }
    }

    private fun updateFavoriteTime(position: Int, newTime: Int) {
        val list = favoritesAdapter.getItems().toMutableList()
        if (position !in list.indices) return

        val old = list[position]
        if (old.timeMinutes == newTime) return

        val updated = old.copy(timeMinutes = newTime)
        list[position] = updated

        store.setFavoriteStops(list)
        favoritesAdapter.replaceAll(list)

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) return@launch

            try {
                firebase.deleteFavoriteStop(old.stopName, old.timeMinutes)
                firebase.upsertFavoriteStop(updated.stopName, updated.timeMinutes)
            } catch (e: Exception) {
                Log.e("SettingsFragment", "updateFavoriteTime sync failed", e)
            }
        }
    }

    private fun nearestTime(target: Int, options: List<Int>): Int {
        if (options.isEmpty()) return target
        return options.minByOrNull { abs(it - target) } ?: target
    }
}