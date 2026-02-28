package com.example.protoshuttleapp.ui

import android.content.Context

class DismissedNotificationStore(context: Context) {

    private val prefs = context.getSharedPreferences("dismissed_notifications", Context.MODE_PRIVATE)
    private val key = "dismissed_ids"

    fun load(): MutableSet<String> {
        val set = prefs.getStringSet(key, emptySet()) ?: emptySet()
        return set.toMutableSet()
    }

    fun save(ids: Set<String>) {
        prefs.edit().putStringSet(key, ids.toSet()).apply()
    }

    fun add(id: Long) {
        val ids = load()
        ids.add(id.toString())
        save(ids)
    }

    fun contains(id: Long): Boolean {
        return load().contains(id.toString())
    }

    fun clear() {
        prefs.edit().remove(key).apply()
    }
}