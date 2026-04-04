package com.example.protoshuttleapp.ui

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class MapsFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    companion object {
        private const val TAG = "MapsFragment"
        private const val REQ = 1001
        private const val ZOOM = 16.5f
        private val DEFAULT_CAMPUS = LatLng(28.0634, -80.6225)
        private const val DEFAULT_ZOOM = 15f
    }

    private var map: GoogleMap? = null
    private lateinit var fused: FusedLocationProviderClient

    private var userMarker: Marker? = null
    private var driverMarker: Marker? = null

    private val stopMarkers = mutableMapOf<String, Marker>()
    private val currentStops = mutableListOf<ManagerStopDoc>()

    private var locationCallback: LocationCallback? = null

    private val firebase = FirebaseRepo()
    private var liveListener: ListenerRegistration? = null
    private var stopsListener: ListenerRegistration? = null

    private var centeredOnDriverOnce = false
    private var showedDefaultCamera = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mf = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mf?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        googleMap.uiSettings.isCompassEnabled = true

        if (!showedDefaultCamera) {
            showedDefaultCamera = true
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CAMPUS, DEFAULT_ZOOM))
        }

        enableOrRequestUserLocation()
        startListeningToDriver()
        startListeningToStops()
    }

    private fun startListeningToDriver() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                Log.e(TAG, "Firebase offline; can't listen to driver location")
                return@launch
            }

            liveListener?.remove()
            liveListener = firebase.listenLiveDriverLocation(routeId = "main") { doc ->
                if (doc?.loc == null) return@listenLiveDriverLocation
                val googleMap = map ?: return@listenLiveDriverLocation

                val pos = LatLng(doc.loc.latitude, doc.loc.longitude)

                if (driverMarker == null) {
                    driverMarker = googleMap.addMarker(
                        MarkerOptions().position(pos).title("Driver")
                    )
                } else {
                    animateMarker(driverMarker!!, pos)
                }

                if (!centeredOnDriverOnce) {
                    centeredOnDriverOnce = true
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, ZOOM))
                }
            }
        }
    }

    private fun startListeningToStops() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                Log.e(TAG, "Firebase offline; can't listen to stop markers")
                return@launch
            }

            stopsListener?.remove()
            stopsListener = firebase.listenManagerStops { stops ->
                currentStops.clear()
                currentStops.addAll(stops)
                renderStopMarkers()
            }
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

        if (!centeredOnDriverOnce && userMarker == null && currentStops.isNotEmpty()) {
            if (currentStops.size == 1) {
                val only = currentStops.first()
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(only.latitude, only.longitude),
                        DEFAULT_ZOOM
                    )
                )
            } else {
                val boundsBuilder = LatLngBounds.Builder()
                currentStops.forEach { stop ->
                    boundsBuilder.include(LatLng(stop.latitude, stop.longitude))
                }
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 140)
                )
            }
        }
    }

    private fun enableOrRequestUserLocation() {
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
        } catch (e: SecurityException) {
            Log.e(TAG, "enable myLocation failed", e)
        }

        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) handleUserLocation(loc, animate = false)
        }

        startUserUpdates()
    }

    private fun startUserUpdates() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    handleUserLocation(loc, animate = true)
                }
            }
        }

        fused.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun stopUserUpdates() {
        locationCallback?.let { fused.removeLocationUpdates(it) }
    }

    private fun handleUserLocation(loc: Location, animate: Boolean) {
        val googleMap = map ?: return
        val here = LatLng(loc.latitude, loc.longitude)

        if (userMarker == null) {
            userMarker = googleMap.addMarker(MarkerOptions().position(here).title("You"))
        } else {
            if (animate) animateMarker(userMarker!!, here) else userMarker!!.position = here
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
            enableOrRequestUserLocation()
            startListeningToDriver()
            startListeningToStops()
        }
    }

    override fun onPause() {
        super.onPause()
        stopUserUpdates()

        liveListener?.remove()
        liveListener = null

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
            enableOrRequestUserLocation()
        }
    }
}