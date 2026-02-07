package com.example.protoshuttleapp.ui

/**
 * Simple in-memory notification store.
 *
 * Rules:
 * - Dismissed notifications are removed forever (for this app run).
 * - Notifications auto-expire after 24 hours.
 *
 * (If you want "forever even after app restarts", next step would be SharedPreferences/Room.)
 */
object NotificationStore {

    private val items: MutableList<NotificationItem> = mutableListOf()

    fun add(item: NotificationItem) {
        // newest first
        items.add(0, item)
        cleanup()
    }

    fun dismiss(id: Long) {
        items.find { it.id == id }?.dismissed = true
        cleanup()
    }

    fun allActive(): List<NotificationItem> {
        cleanup()
        return items.filter { !it.dismissed && !it.isExpired() }
    }

    fun latestActive(limit: Int): List<NotificationItem> {
        return allActive().take(limit)
    }

    fun activeCount(): Int = allActive().size

    private fun cleanup() {
        items.removeAll { it.dismissed || it.isExpired() }
    }
}
