package com.example.protoshuttleapp.ui

/**
 * Represents an in-app notification stored in memory (and optionally persisted later).
 */
data class NotificationItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val message: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    var dismissed: Boolean = false
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val twentyFourHoursMillis = 24L * 60L * 60L * 1000L
        return (nowMillis - createdAtMillis) >= twentyFourHoursMillis
    }
}
