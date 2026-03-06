package com.example.protoshuttleapp.data

import com.google.firebase.firestore.GeoPoint

data class LiveDriverLocationDoc(
    val loc: GeoPoint? = null,
    val bearing: Double? = null,
    val speedMps: Double? = null,
    val updatedAt: Long = 0L
)