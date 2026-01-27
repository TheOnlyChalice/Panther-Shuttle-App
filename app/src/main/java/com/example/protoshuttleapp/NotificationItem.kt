package com.example.protoshuttleapp.ui

data class NotificationItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
