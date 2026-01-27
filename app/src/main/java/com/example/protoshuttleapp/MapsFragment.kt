package com.example.protoshuttleapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.protoshuttleapp.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class MapsFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    private var googleMap: GoogleMap? = null
    private var userMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Find or create the SupportMapFragment inside the container
        val existing = childFragmentManager.findFragmentById(R.id.map_container)
        val mapFragment: SupportMapFragment = if (existing is SupportMapFragment) {
            existing
        } else {
            val mf = SupportMapFragment.newInstance()
            childFragmentManager.beginTransaction()
                .replace(R.id.map_container, mf)
                .commitNow()
            mf
        }

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        enableMyLocation()
    }

    private fun enableMyLocation() {
        val ctx = requireContext()
        val hasFine = ActivityCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ActivityCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }

        try {
            // This can throw SecurityException if permissions are weird, so we guard it
            googleMap?.isMyLocationEnabled = true
        } catch (_: SecurityException) {
            // If something goes wrong, just skip the blue dot; don't crash
        }

        showCurrentLocationMarker()
    }

    private fun showCurrentLocationMarker() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                val map = googleMap ?: return@addOnSuccessListener
                if (location != null) {
                    val here = LatLng(location.latitude, location.longitude)

                    if (userMarker == null) {
                        userMarker = map.addMarker(
                            MarkerOptions()
                                .position(here)
                                .title("You are here")
                        )
                    } else {
                        userMarker!!.position = here
                    }

                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(here, 16f))
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                enableMyLocation()
            }
        }
    }
}
