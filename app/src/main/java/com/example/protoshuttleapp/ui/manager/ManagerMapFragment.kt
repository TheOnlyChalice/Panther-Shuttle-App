package com.example.protoshuttleapp.ui.manager

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.data.ManagerStopDoc
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ManagerMapFragment : Fragment(R.layout.fragment_manager_map), OnMapReadyCallback {

    private val firebase = FirebaseRepo()

    private var googleMap: GoogleMap? = null
    private var stopsListener: ListenerRegistration? = null
    private val currentStops = mutableListOf<ManagerStopDoc>()

    private var pendingAddStopName: String? = null
    private var pendingMoveStopId: String? = null

    private var hasShownDefaultCamera = false
    private var hasCenteredOnStops = false

    private lateinit var addMarkerButton: MaterialButton
    private lateinit var mapStatusText: TextView

    companion object {
        // Florida Tech area so the map opens near campus instead of the world view near 0,0
        private val DEFAULT_CAMPUS = LatLng(28.0634, -80.6225)
        private const val DEFAULT_ZOOM = 15f
        private const val SINGLE_STOP_ZOOM = 16.5f
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addMarkerButton = view.findViewById(R.id.addMarkerButton)
        mapStatusText = view.findViewById(R.id.mapStatusText)

        addMarkerButton.setOnClickListener {
            showAddStopNameDialog()
        }

        setupMapFragment()
        startStopListener()
    }

    override fun onDestroyView() {
        stopsListener?.remove()
        stopsListener = null
        googleMap = null
        super.onDestroyView()
    }

    private fun setupMapFragment() {
        val existing = childFragmentManager.findFragmentByTag("manager_map_child") as? SupportMapFragment
        val mapFragment = existing ?: SupportMapFragment.newInstance().also {
            childFragmentManager.commit {
                replace(R.id.managerMapContainer, it, "manager_map_child")
            }
        }
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMapToolbarEnabled = true
        map.uiSettings.isCompassEnabled = true

        map.setOnMapClickListener { latLng ->
            when {
                pendingMoveStopId != null -> moveExistingStop(latLng)
                pendingAddStopName != null -> createNewStop(latLng)
            }
        }

        map.setOnMarkerClickListener { marker ->
            val stopId = marker.tag as? String ?: return@setOnMarkerClickListener false
            val stop = currentStops.firstOrNull { it.id == stopId } ?: return@setOnMarkerClickListener false
            showStopOptionsDialog(stop)
            true
        }

        renderStops()
    }

    private fun startStopListener() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                mapStatusText.text = "Firebase sign-in failed."
                return@launch
            }

            stopsListener?.remove()
            stopsListener = firebase.listenManagerStops { stops ->
                currentStops.clear()
                currentStops.addAll(stops)
                renderStops()
            }
        }
    }

    private fun renderStops() {
        val map = googleMap ?: return

        map.clear()

        for (stop in currentStops) {
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(stop.latitude, stop.longitude))
                    .title(stop.stopName)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            marker?.tag = stop.id
        }

        if (currentStops.isEmpty()) {
            if (!hasShownDefaultCamera) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CAMPUS, DEFAULT_ZOOM))
                hasShownDefaultCamera = true
            }
            hasCenteredOnStops = false
            return
        }

        if (!hasCenteredOnStops) {
            if (currentStops.size == 1) {
                val stop = currentStops.first()
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(stop.latitude, stop.longitude),
                        SINGLE_STOP_ZOOM
                    )
                )
            } else {
                val boundsBuilder = LatLngBounds.Builder()
                currentStops.forEach { stop ->
                    boundsBuilder.include(LatLng(stop.latitude, stop.longitude))
                }
                map.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 140)
                )
            }
            hasCenteredOnStops = true
        }
    }

    private fun showAddStopNameDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Stop name"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add Stop Marker")
            .setMessage("Enter the stop name. After you press Continue, tap the map where the stop should be placed.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                val stopName = input.text?.toString()?.trim().orEmpty()
                if (stopName.isBlank()) {
                    Toast.makeText(requireContext(), "Stop name can't be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                pendingMoveStopId = null
                pendingAddStopName = stopName
                mapStatusText.text = "Tap the map to place \"$stopName\"."
            }
            .show()
    }

    private fun createNewStop(latLng: LatLng) {
        val stopName = pendingAddStopName ?: return
        pendingAddStopName = null
        mapStatusText.text = "Saving stop..."

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                mapStatusText.text = "Firebase sign-in failed."
                return@launch
            }

            try {
                firebase.createManagerStop(
                    stopName = stopName,
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, SINGLE_STOP_ZOOM))
                hasCenteredOnStops = true
                mapStatusText.text = "Added \"$stopName\"."
            } catch (e: Exception) {
                mapStatusText.text = "Failed to add stop: ${e.message}"
            }
        }
    }

    private fun moveExistingStop(latLng: LatLng) {
        val stopId = pendingMoveStopId ?: return
        val stop = currentStops.firstOrNull { it.id == stopId } ?: run {
            pendingMoveStopId = null
            mapStatusText.text = "Selected stop not found."
            return
        }

        pendingMoveStopId = null
        mapStatusText.text = "Updating stop location..."

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                mapStatusText.text = "Firebase sign-in failed."
                return@launch
            }

            try {
                firebase.updateManagerStop(
                    stopId = stop.id,
                    stopName = stop.stopName,
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, SINGLE_STOP_ZOOM))
                hasCenteredOnStops = true
                mapStatusText.text = "Moved \"${stop.stopName}\"."
            } catch (e: Exception) {
                mapStatusText.text = "Failed to move stop: ${e.message}"
            }
        }
    }

    private fun showStopOptionsDialog(stop: ManagerStopDoc) {
        val options = arrayOf("Rename Stop", "Move Marker", "Delete Stop")

        AlertDialog.Builder(requireContext())
            .setTitle(stop.stopName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameStopDialog(stop)
                    1 -> {
                        pendingAddStopName = null
                        pendingMoveStopId = stop.id
                        mapStatusText.text = "Tap the map to move \"${stop.stopName}\"."
                    }
                    2 -> showDeleteStopDialog(stop)
                }
            }
            .show()
    }

    private fun showRenameStopDialog(stop: ManagerStopDoc) {
        val input = EditText(requireContext()).apply {
            setText(stop.stopName)
            setSelection(stop.stopName.length)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Rename Stop")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isBlank()) {
                    Toast.makeText(requireContext(), "Stop name can't be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                mapStatusText.text = "Renaming stop..."

                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = firebase.ensureSignedIn()
                    if (!ok) {
                        mapStatusText.text = "Firebase sign-in failed."
                        return@launch
                    }

                    try {
                        firebase.updateManagerStop(
                            stopId = stop.id,
                            stopName = newName,
                            latitude = stop.latitude,
                            longitude = stop.longitude
                        )
                        mapStatusText.text = "Renamed to \"$newName\"."
                    } catch (e: Exception) {
                        mapStatusText.text = "Failed to rename stop: ${e.message}"
                    }
                }
            }
            .show()
    }

    private fun showDeleteStopDialog(stop: ManagerStopDoc) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Stop")
            .setMessage("Delete \"${stop.stopName}\"? Any schedule entries linked to this stop will also be deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                mapStatusText.text = "Deleting stop..."

                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = firebase.ensureSignedIn()
                    if (!ok) {
                        mapStatusText.text = "Firebase sign-in failed."
                        return@launch
                    }

                    try {
                        firebase.deleteManagerStop(stop.id)
                        mapStatusText.text = "Deleted \"${stop.stopName}\"."
                    } catch (e: Exception) {
                        mapStatusText.text = "Failed to delete stop: ${e.message}"
                    }
                }
            }
            .show()
    }
}