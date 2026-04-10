package com.example.protoshuttleapp.data

data class DriverMessageDoc(
    /**
     * Audience tags that should see this message.
     *
     * Examples:
     * - ["ALL"] for a broadcast
     * - ["Panther Bay"] for all students who favorite that stop
     * - ["STOP_TIME::panther bay::495"] for students who favorite Panther Bay at 8:15 AM
     */
    val audience: List<String> = listOf("ALL"),

    val stopName: String = "",
    val timeMinutes: Int = 0,

    val title: String = "",
    val message: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
)