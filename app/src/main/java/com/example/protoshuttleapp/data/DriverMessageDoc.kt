package com.example.protoshuttleapp.data

data class DriverMessageDoc(
    /**
     * Audience tags that should see this message.
     *
     * Example:
     * - ["ALL"] for a broadcast message
     * - ["Campus"] for a stop-specific message
     */
    val audience: List<String> = listOf("ALL"),

    // For stop-specific messages this will be set; for ALL it can be blank.
    val stopName: String = "",

    // Kept for future expansion; not required for this feature
    val timeMinutes: Int = 0,

    val title: String = "",
    val message: String = "",

    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Millis timestamp for client-side 24h expiry.
     * (Later: enable Firestore TTL on this field if you want auto-deletion.)
     */
    val expiresAt: Long = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
)