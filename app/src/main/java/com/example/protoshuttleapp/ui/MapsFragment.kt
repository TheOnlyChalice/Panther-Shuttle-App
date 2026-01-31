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
import com.example.protoshuttleapp.R
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlin.math.abs

class MapsFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    companion object {
        private const val TAG = "MapsFragment"
        private const val REQ = 1001
        private const val ZOOM_ROAD = 18f
    }

    private var map: GoogleMap? = null
    private lateinit var fused: FusedLocationProviderClient

    private var userMarker: Marker? = null
    private var locationCallback: LocationCallback? = null

    private var centeredOnce = false
    private var followUser = true

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

        enableOrRequest()
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
        } catch (e: SecurityException) {
            Log.e(TAG, "enable myLocation failed", e)
        }

        // Fast initial center if available
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
            .setMinUpdateDistanceMeters(2f) // only update if moved 2m+
            .build()

        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    handleLocation(loc, animate = true)
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

        if (userMarker == null) {
            userMarker = googleMap.addMarker(
                MarkerOptions().position(here).title("You")
            )
        } else {
            if (animate) {
                animateMarker(userMarker!!, here)
            } else {
                userMarker!!.position = here
            }
        }

        // Road/building-level camera
        if (followUser) {
            val bearing = if (loc.hasBearing()) loc.bearing else 0f
            val cam = CameraPosition.Builder()
                .target(here)
                .zoom(ZOOM_ROAD)
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
    }

    private fun animateMarker(marker: Marker, to: LatLng) {
        val from = marker.position
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 700
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
        if (map != null) enableOrRequest()
    }

    override fun onPause() {
        super.onPause()
        stopUpdates()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableOrRequest()
        }
    }
}
