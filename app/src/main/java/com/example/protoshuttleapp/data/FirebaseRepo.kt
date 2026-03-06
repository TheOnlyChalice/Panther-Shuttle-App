package com.example.protoshuttleapp.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.GeoPoint

class FirebaseRepo {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun requireUid(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
    }

    // ✅ Don’t crash if Auth fails
    suspend fun ensureSignedIn(): Boolean {
        if (auth.currentUser != null) return true
        return try {
            auth.signInAnonymously().await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Anonymous sign-in failed", e)
            false
        }
    }

    // -------------------------
    // Favorites (existing)
    // -------------------------

    private fun favoriteDocId(stopName: String, timeMinutes: Int): String {
        val cleanStop = stopName.trim().replace("/", "_")
        return "${cleanStop}_$timeMinutes"
    }

    suspend fun upsertFavoriteStop(stopName: String, timeMinutes: Int) {
        val uid = requireUid()
        val docId = favoriteDocId(stopName, timeMinutes)

        val doc = FavoriteStopDoc(
            stopName = stopName,
            timeMinutes = timeMinutes,
            updatedAt = System.currentTimeMillis()
        )

        db.collection("users")
            .document(uid)
            .collection("favorites")
            .document(docId)
            .set(doc)
            .await()
    }

    suspend fun deleteFavoriteStop(stopName: String, timeMinutes: Int) {
        val uid = requireUid()
        val docId = favoriteDocId(stopName, timeMinutes)

        db.collection("users")
            .document(uid)
            .collection("favorites")
            .document(docId)
            .delete()
            .await()
    }

    fun listenFavoriteStops(onUpdate: (List<FavoriteStopDoc>) -> Unit): ListenerRegistration {
        val uid = requireUid()
        return db.collection("users")
            .document(uid)
            .collection("favorites")
            .addSnapshotListener { snap, _ ->
                val list = snap?.toObjects(FavoriteStopDoc::class.java) ?: emptyList()
                onUpdate(list)
            }
    }

    // -------------------------
    // Driver -> Student messages
    // -------------------------

    /**
     * Driver -> Students notification.
     * - targetStopName == null  => broadcast to everyone (audience=["ALL"])
     * - targetStopName != null  => only students who favorited that stop (audience=[stopName])
     */
    suspend fun sendDriverNotification(
        targetStopName: String?,
        title: String,
        message: String,
        timeMinutes: Int = 0
    ) {
        val cleanStop = targetStopName?.trim().orEmpty()
        val audience = if (cleanStop.isBlank()) listOf("ALL") else listOf(cleanStop)

        val now = System.currentTimeMillis()
        val doc = DriverMessageDoc(
            audience = audience,
            stopName = cleanStop,
            timeMinutes = timeMinutes,
            title = title,
            message = message,
            createdAt = now,
            expiresAt = now + 24L * 60L * 60L * 1000L
        )

        db.collection("driverMessages")
            .add(doc)
            .await()
    }

    /**
     * Student-side listener that fetches:
     * - broadcast messages (audience contains "ALL")
     * - stop-specific messages (audience contains one of the student's favorited stops)
     *
     * NOTE: Firestore's array-contains-any supports up to 10 tags.
     */
    fun listenDriverMessagesForAudience(
        audienceTags: List<String>,
        limit: Long = 50,
        onUpdate: (List<DriverMessageDoc>) -> Unit
    ): ListenerRegistration {
        val tags = audienceTags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
            .ifEmpty { listOf("ALL") }

        return db.collection("driverMessages")
            .whereArrayContainsAny("audience", tags)
            .orderBy("createdAt")
            .limitToLast(limit)
            .addSnapshotListener { snap, err ->

                // ✅ If Firestore throws (missing index / permission denied),
                // do NOT clear the UI — just log and keep the last list.
                if (err != null) {
                    Log.e("FirebaseRepo", "listenDriverMessagesForAudience failed", err)
                    return@addSnapshotListener
                }

                if (snap == null) return@addSnapshotListener

                val list = snap.toObjects(DriverMessageDoc::class.java)
                val now = System.currentTimeMillis()
                val active = list.filter { it.expiresAt <= 0L || it.expiresAt > now }

                onUpdate(active.sortedByDescending { it.createdAt })
            }
    }

    // (Optional existing count method)
    suspend fun countFavoritesFor(stopName: String, timeMinutes: Int): Int {
        val query = db.collectionGroup("favorites")
            .whereEqualTo("stopName", stopName)
            .whereEqualTo("timeMinutes", timeMinutes)

        val agg = query.count()
        val result = agg.get(AggregateSource.SERVER).await()
        return result.count.toInt()
    }
    suspend fun setLiveDriverLocation(
        routeId: String = "main",
        latitude: Double,
        longitude: Double,
        bearing: Float? = null,
        speedMps: Float? = null
    ) {
        val doc = LiveDriverLocationDoc(
            loc = GeoPoint(latitude, longitude),
            bearing = bearing?.toDouble(),
            speedMps = speedMps?.toDouble(),
            updatedAt = System.currentTimeMillis()
        )

        db.collection("routes")
            .document(routeId)
            .collection("live")
            .document("driver") // single driver doc for now
            .set(doc)
            .await()
    }

    fun listenLiveDriverLocation(
        routeId: String = "main",
        onUpdate: (LiveDriverLocationDoc?) -> Unit
    ): ListenerRegistration {
        return db.collection("routes")
            .document(routeId)
            .collection("live")
            .document("driver")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("FirebaseRepo", "listenLiveDriverLocation failed", err)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                onUpdate(snap.toObject(LiveDriverLocationDoc::class.java))
            }
    }
}