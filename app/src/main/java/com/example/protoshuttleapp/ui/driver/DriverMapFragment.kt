package com.example.protoshuttleapp.ui.driver

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.data.ManagerStopDoc
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlin.math.abs

class DriverMapFragment : Fragment(R.layout.fragment_driver_map), OnMapReadyCallback {

    companion object {
        private const val REQ = 2001
        private const val ZOOM = 17.5f
        private const val PUBLISH_EVERY_MS = 2500L
        private val DEFAULT_CAMPUS = LatLng(28.0634, -80.6225)
        private const val DEFAULT_ZOOM = 15f
    }

    private var map: GoogleMap? = null
    private lateinit var fused: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val firebase = FirebaseRepo()
    private var firebaseReady = false
    private var stopsListener: ListenerRegistration? = null

    private var driverMarker: Marker? = null
    private val stopMarkers = mutableMapOf<String, Marker>()
    private val currentStops = mutableListOf<ManagerStopDoc>()

    private var centeredOnce = false
    private var lastPublishAt = 0L

    private lateinit var status: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        status = view.findViewById(R.id.broadcastStatus)
        fused = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mf = childFragmentManager.findFragmentById(R.id.driverMap) as? SupportMapFragment
        mf?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CAMPUS, DEFAULT_ZOOM))

        viewLifecycleOwner.lifecycleScope.launch {
            firebaseReady = firebase.ensureSignedIn()
            status.text = if (firebaseReady) "Broadcasting location ✓" else "Firebase offline"
            if (firebaseReady) {
                startListeningToStops()
            }
        }

        enableOrRequest()
    }

    private fun startListeningToStops() {
        stopsListener?.remove()
        stopsListener = firebase.listenManagerStops { stops ->
            currentStops.clear()
            currentStops.addAll(stops)
            renderStopMarkers()
        }
    }

    private fun renderStopMarkers() {
        val googleMap = map ?: return

        val incomingIds = currentStops.map { it.id }.toSet()

        val toRemove = stopMarkers.keys.filter { it !in incomingIds }
        for (id in toRemove) {
            stopMarkers.remove(id)?.remove()
        }

        for (stop in currentStops) {
            val position = LatLng(stop.latitude, stop.longitude)
            val existing = stopMarkers[stop.id]

            if (existing == null) {
                val marker = googleMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(stop.stopName)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
                if (marker != null) {
                    stopMarkers[stop.id] = marker
                }
            } else {
                existing.position = position
                existing.title = stop.stopName
            }
        }
    }

    private fun enableOrRequest() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ)
            return
        }

        try {
            map?.isMyLocationEnabled = true
        } catch (_: SecurityException) {
        }

        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) handleLocation(loc, animate = false)
        }

        startUpdates()
    }

    private fun startUpdates() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    handleLocation(loc, animate = true)

                    val now = System.currentTimeMillis()
                    if (firebaseReady && now - lastPublishAt >= PUBLISH_EVERY_MS) {
                        lastPublishAt = now
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                firebase.setLiveDriverLocation(
                                    routeId = "main",
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    bearing = if (loc.hasBearing()) loc.bearing else null,
                                    speedMps = if (loc.hasSpeed()) loc.speed else null
                                )
                            } catch (e: Exception) {
                                status.text = "Broadcast failed (offline?)"
                            }
                        }
                    }
                }
            }
        }

        fused.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun stopUpdates() {
        locationCallback?.let { fused.removeLocationUpdates(it) }
    }

    private fun handleLocation(loc: Location, animate: Boolean) {
        val googleMap = map ?: return
        val here = LatLng(loc.latitude, loc.longitude)

        if (driverMarker == null) {
            driverMarker = googleMap.addMarker(
                MarkerOptions().position(here).title("Driver (You)")
            )
        } else {
            if (animate) animateMarker(driverMarker!!, here) else driverMarker!!.position = here
        }

        val bearing = if (loc.hasBearing()) loc.bearing else 0f
        val cam = CameraPosition.Builder()
            .target(here)
            .zoom(ZOOM)
            .bearing(if (abs(bearing) > 0.1f) bearing else googleMap.cameraPosition.bearing)
            .tilt(45f)
            .build()

        if (!centeredOnce) {
            centeredOnce = true
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cam))
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cam))
        }
    }

    private fun animateMarker(marker: Marker, to: LatLng) {
        val from = marker.position
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 600
        anim.addUpdateListener { va ->
            val t = va.animatedValue as Float
            val lat = (to.latitude - from.latitude) * t + from.latitude
            val lng = (to.longitude - from.longitude) * t + from.longitude
            marker.position = LatLng(lat, lng)
        }
        anim.start()
    }

    override fun onResume() {
        super.onResume()
        if (map != null) {
            enableOrRequest()
            if (firebaseReady) {
                startListeningToStops()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopUpdates()
        stopsListener?.remove()
        stopsListener = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableOrRequest()
        } else {
            Toast.makeText(requireContext(), "Location permission required for driver tracking.", Toast.LENGTH_SHORT).show()
        }
    }
}