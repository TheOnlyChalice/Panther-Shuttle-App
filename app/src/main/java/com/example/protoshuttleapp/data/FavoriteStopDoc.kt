package com.example.protoshuttleapp.data

data class FavoriteStopDoc(
    val stopName: String = "",
    val timeMinutes: Int = 0,   // minutes since midnight
    val updatedAt: Long = System.currentTimeMillis()
)