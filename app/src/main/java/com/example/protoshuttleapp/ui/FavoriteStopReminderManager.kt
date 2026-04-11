package com.example.protoshuttleapp.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.data.ManagerScheduleEntryDoc
import com.example.protoshuttleapp.ui.settings.FavoriteStop
import com.example.protoshuttleapp.ui.settings.SettingStore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class FavoriteStopReminderManager(
    private val activity: AppCompatActivity
) {
    private val firebase = FirebaseRepo()
    private val settingStore = SettingStore(activity)

    private var favoritesListener: ListenerRegistration? = null
    private var scheduleListener: ListenerRegistration? = null
    private var tickerJob: Job? = null

    private val favoriteStops = mutableListOf<FavoriteStop>()
    private val scheduleEntries = mutableListOf<ManagerScheduleEntryDoc>()

    private val reminderPrefs =
        activity.getSharedPreferences("favorite_stop_reminders", Context.MODE_PRIVATE)

    fun start() {
        createNotificationChannel()
        favoriteStops.clear()
        favoriteStops.addAll(settingStore.getFavoriteStops())

        activity.lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) return@launch

            favoritesListener?.remove()
            scheduleListener?.remove()

            favoritesListener = firebase.listenFavoriteStops { docs ->
                favoriteStops.clear()
                favoriteStops.addAll(
                    docs.map {
                        FavoriteStop(
                            stopName = it.stopName,
                            timeMinutes = it.timeMinutes
                        )
                    }
                )
                checkAndNotify()
            }

            scheduleListener = firebase.listenManagerSchedule { entries ->
                scheduleEntries.clear()
                scheduleEntries.addAll(entries)
                checkAndNotify()
            }

            startTicker()
        }
    }

    fun stop() {
        favoritesListener?.remove()
        scheduleListener?.remove()
        favoritesListener = null
        scheduleListener = null

        tickerJob?.cancel()
        tickerJob = null
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = activity.lifecycleScope.launch {
            while (isActive) {
                checkAndNotify()
                delay(30_000L)
            }
        }
    }

    private fun checkAndNotify() {
        if (!settingStore.notifyBeforeFavoriteStopsOn) return
        if (favoriteStops.isEmpty()) return
        if (scheduleEntries.isEmpty()) return

        val nextFavoriteStop = findNextFavoriteScheduledStop() ?: return
        val now = LocalDateTime.now()
        val etaMinutes = Duration.between(now, nextFavoriteStop.whenDateTime).toMinutes()

        if (etaMinutes !in 1..15) return

        val key = "${nextFavoriteStop.whenDate}|${nextFavoriteStop.stopName}|${nextFavoriteStop.timeMinutes}"
        if (wasAlreadySent(key)) return

        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) return

        val title = "Favorite stop coming up"
        val body =
            "${nextFavoriteStop.stopName} is scheduled for ${formatMinutes(nextFavoriteStop.timeMinutes)} in about $etaMinutes min."

        val intent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            activity,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(activity, CHANNEL_ID)
            .setSmallIcon(R.mipmap.panther_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(activity).notify(key.hashCode(), notification)
        rememberSent(key)
    }

    private fun findNextFavoriteScheduledStop(): UpcomingFavoriteStop? {
        val favoriteKeys = favoriteStops
            .map { it.stopName.trim().lowercase(Locale.US) to it.timeMinutes }
            .toSet()

        if (favoriteKeys.isEmpty()) return null

        val now = LocalDateTime.now()
        val candidates = mutableListOf<UpcomingFavoriteStop>()

        for (dayOffset in 0..6) {
            val date = LocalDate.now().plusDays(dayOffset.toLong())
            val dayValue = date.dayOfWeek.toManagerDayValue()

            val sameDayEntries = scheduleEntries
                .filter { it.dayOfWeek.coerceIn(1, 7) == dayValue }
                .filter {
                    favoriteKeys.contains(
                        it.stopName.trim().lowercase(Locale.US) to it.timeMinutes
                    )
                }

            for (entry in sameDayEntries) {
                val eventDateTime = date.atTime(
                    LocalTime.of(entry.timeMinutes / 60, entry.timeMinutes % 60)
                )

                if (eventDateTime.isAfter(now)) {
                    candidates.add(
                        UpcomingFavoriteStop(
                            stopName = entry.stopName,
                            timeMinutes = entry.timeMinutes,
                            whenDate = date,
                            whenDateTime = eventDateTime
                        )
                    )
                }
            }
        }

        return candidates.minByOrNull { it.whenDateTime }
    }

    private fun wasAlreadySent(key: String): Boolean {
        val sent = reminderPrefs.getStringSet("sentReminderKeys", emptySet()).orEmpty()
        return sent.contains(key)
    }

    private fun rememberSent(key: String) {
        val current = reminderPrefs.getStringSet("sentReminderKeys", emptySet())
            .orEmpty()
            .toMutableSet()

        current.add(key)

        val cleaned = current.filterTo(mutableSetOf()) { saved ->
            val savedDate = saved.substringBefore("|", "")
            try {
                LocalDate.parse(savedDate) >= LocalDate.now().minusDays(2)
            } catch (_: Exception) {
                false
            }
        }

        reminderPrefs.edit().putStringSet("sentReminderKeys", cleaned).apply()
    }

    private fun formatMinutes(totalMinutes: Int): String {
        val hour24 = ((totalMinutes / 60) % 24 + 24) % 24
        val minute = ((totalMinutes % 60) + 60) % 60
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val raw = hour24 % 12) {
            0 -> 12
            else -> raw
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = ContextCompat.getSystemService(activity, NotificationManager::class.java)
            ?: return

        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Favorite Stop Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies students about favorite stops before arrival."
        }

        manager.createNotificationChannel(channel)
    }

    private fun java.time.DayOfWeek.toManagerDayValue(): Int {
        return when (this) {
            java.time.DayOfWeek.MONDAY -> 1
            java.time.DayOfWeek.TUESDAY -> 2
            java.time.DayOfWeek.WEDNESDAY -> 3
            java.time.DayOfWeek.THURSDAY -> 4
            java.time.DayOfWeek.FRIDAY -> 5
            java.time.DayOfWeek.SATURDAY -> 6
            java.time.DayOfWeek.SUNDAY -> 7
        }
    }

    private data class UpcomingFavoriteStop(
        val stopName: String,
        val timeMinutes: Int,
        val whenDate: LocalDate,
        val whenDateTime: LocalDateTime
    )

    companion object {
        private const val CHANNEL_ID = "favorite_stop_reminders"
    }
}