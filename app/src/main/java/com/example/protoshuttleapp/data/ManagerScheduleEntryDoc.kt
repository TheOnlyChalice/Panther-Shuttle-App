package com.example.protoshuttleapp.data

data class ManagerScheduleEntryDoc(
    var id: String = "",
    var stopId: String = "",
    var stopName: String = "",
    var dayOfWeek: Int = 1, // 1 = Monday ... 7 = Sunday
    var timeMinutes: Int = 0,
    var updatedAt: Long = 0L
)