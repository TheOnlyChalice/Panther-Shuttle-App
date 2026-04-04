package com.example.protoshuttleapp.data

data class ManagerStopDoc(
    var id: String = "",
    var stopName: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var updatedAt: Long = 0L
)