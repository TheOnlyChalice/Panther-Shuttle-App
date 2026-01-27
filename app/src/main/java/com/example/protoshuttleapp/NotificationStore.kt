package com.example.protoshuttleapp.ui

object NotificationStore {

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    private val notifications = mutableListOf<NotificationItem>()

    /** Remove >24h old and return active list. */
    fun getActiveNotifications(): List<NotificationItem> {
        val now = System.currentTimeMillis()
        notifications.removeAll { now - it.timestamp >= DAY_MILLIS }
        return notifications.toList()
    }

    fun addNotification(item: NotificationItem) {
        notifications.add(0, item) // newest at top
    }

    fun dismissNotification(item: NotificationItem) {
        notifications.removeAll { it.id == item.id }
    }

    fun activeCount(): Int = getActiveNotifications().size
}
