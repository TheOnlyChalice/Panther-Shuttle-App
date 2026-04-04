package com.example.protoshuttleapp.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

class FirebaseRepo {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun requireUid(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
    }

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
    // Favorites
    // -------------------------

    private fun favoriteDocId(stopName: String, timeMinutes: Int): String {
        val cleanStop = stopName.trim().replace("/", "_")
        return "${cleanStop}_$timeMinutes"
    }

    private fun favoriteIndexDocId(uid: String, stopName: String, timeMinutes: Int): String {
        return "${uid}_${favoriteDocId(stopName, timeMinutes)}"
    }

    suspend fun upsertFavoriteStop(stopName: String, timeMinutes: Int) {
        val uid = requireUid()
        val favoriteId = favoriteDocId(stopName, timeMinutes)
        val indexId = favoriteIndexDocId(uid, stopName, timeMinutes)
        val now = System.currentTimeMillis()

        val favoriteDoc = FavoriteStopDoc(
            stopName = stopName,
            timeMinutes = timeMinutes,
            updatedAt = now
        )

        val indexData = hashMapOf(
            "uid" to uid,
            "stopName" to stopName,
            "timeMinutes" to timeMinutes,
            "updatedAt" to now
        )

        val userFavoriteRef = db.collection("users")
            .document(uid)
            .collection("favorites")
            .document(favoriteId)

        val indexRef = db.collection("favoriteStopIndex")
            .document(indexId)

        val batch = db.batch()
        batch.set(userFavoriteRef, favoriteDoc)
        batch.set(indexRef, indexData)
        batch.commit().await()
    }

    suspend fun deleteFavoriteStop(stopName: String, timeMinutes: Int) {
        val uid = requireUid()
        val favoriteId = favoriteDocId(stopName, timeMinutes)
        val indexId = favoriteIndexDocId(uid, stopName, timeMinutes)

        val userFavoriteRef = db.collection("users")
            .document(uid)
            .collection("favorites")
            .document(favoriteId)

        val indexRef = db.collection("favoriteStopIndex")
            .document(indexId)

        val batch = db.batch()
        batch.delete(userFavoriteRef)
        batch.delete(indexRef)
        batch.commit().await()
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

    suspend fun rebuildFavoriteStopIndexForCurrentUser(favorites: List<FavoriteStopDoc>) {
        val uid = requireUid()

        val existing = db.collection("favoriteStopIndex")
            .whereEqualTo("uid", uid)
            .get()
            .await()

        val batch = db.batch()

        for (doc in existing.documents) {
            batch.delete(doc.reference)
        }

        for (fav in favorites) {
            val indexId = favoriteIndexDocId(uid, fav.stopName, fav.timeMinutes)
            val indexRef = db.collection("favoriteStopIndex").document(indexId)

            val indexData = hashMapOf(
                "uid" to uid,
                "stopName" to fav.stopName,
                "timeMinutes" to fav.timeMinutes,
                "updatedAt" to fav.updatedAt
            )

            batch.set(indexRef, indexData)
        }

        batch.commit().await()
    }

    suspend fun countIndexedFavoriteUsersForStopAroundTime(
        stopName: String,
        targetTimeMinutes: Int,
        windowMinutes: Int = 15
    ): Int {
        val snap = db.collection("favoriteStopIndex")
            .whereEqualTo("stopName", stopName)
            .get()
            .await()

        return snap.documents
            .filter { doc ->
                val time = (doc.getLong("timeMinutes") ?: -100000L).toInt()
                abs(time - targetTimeMinutes) <= windowMinutes
            }
            .mapNotNull { it.getString("uid") }
            .distinct()
            .size
    }

    suspend fun countFavoritesFor(stopName: String, timeMinutes: Int): Int {
        val query = db.collectionGroup("favorites")
            .whereEqualTo("stopName", stopName)
            .whereEqualTo("timeMinutes", timeMinutes)

        val agg = query.count()
        val result = agg.get(AggregateSource.SERVER).await()
        return result.count.toInt()
    }

    // -------------------------
    // Driver -> Student messages
    // -------------------------

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

    // -------------------------
    // Live driver location
    // -------------------------

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
            .document("driver")
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

    // -------------------------
    // Manager stops (Map tab)
    // -------------------------

    fun listenManagerStops(onUpdate: (List<ManagerStopDoc>) -> Unit): ListenerRegistration {
        return db.collection("stops")
            .orderBy("stopName")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("FirebaseRepo", "listenManagerStops failed", err)
                    return@addSnapshotListener
                }

                val list = snap?.toObjects(ManagerStopDoc::class.java) ?: emptyList()
                onUpdate(list)
            }
    }

    suspend fun createManagerStop(
        stopName: String,
        latitude: Double,
        longitude: Double
    ): String {
        val cleanName = stopName.trim()
        if (cleanName.isBlank()) throw IllegalArgumentException("Stop name can't be empty.")

        val docRef = db.collection("stops").document()
        val now = System.currentTimeMillis()

        val doc = ManagerStopDoc(
            id = docRef.id,
            stopName = cleanName,
            latitude = latitude,
            longitude = longitude,
            updatedAt = now
        )

        docRef.set(doc).await()
        return docRef.id
    }

    suspend fun updateManagerStop(
        stopId: String,
        stopName: String,
        latitude: Double,
        longitude: Double
    ) {
        val cleanName = stopName.trim()
        if (cleanName.isBlank()) throw IllegalArgumentException("Stop name can't be empty.")

        val stopRef = db.collection("stops").document(stopId)
        val now = System.currentTimeMillis()

        val scheduleSnap = db.collection("routeSchedule")
            .whereEqualTo("stopId", stopId)
            .get()
            .await()

        val batch = db.batch()

        batch.set(
            stopRef,
            ManagerStopDoc(
                id = stopId,
                stopName = cleanName,
                latitude = latitude,
                longitude = longitude,
                updatedAt = now
            )
        )

        for (doc in scheduleSnap.documents) {
            batch.update(
                doc.reference,
                mapOf(
                    "stopName" to cleanName,
                    "updatedAt" to now
                )
            )
        }

        batch.commit().await()
    }

    suspend fun deleteManagerStop(stopId: String) {
        val stopRef = db.collection("stops").document(stopId)

        val scheduleSnap = db.collection("routeSchedule")
            .whereEqualTo("stopId", stopId)
            .get()
            .await()

        val batch = db.batch()
        batch.delete(stopRef)

        for (doc in scheduleSnap.documents) {
            batch.delete(doc.reference)
        }

        batch.commit().await()
    }

    // -------------------------
    // Manager schedule (Schedule tab)
    // -------------------------

    fun listenManagerSchedule(onUpdate: (List<ManagerScheduleEntryDoc>) -> Unit): ListenerRegistration {
        return db.collection("routeSchedule")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("FirebaseRepo", "listenManagerSchedule failed", err)
                    return@addSnapshotListener
                }

                val list = snap?.toObjects(ManagerScheduleEntryDoc::class.java) ?: emptyList()
                onUpdate(list)
            }
    }

    suspend fun createManagerScheduleEntry(
        stopId: String,
        stopName: String,
        dayOfWeek: Int,
        timeMinutes: Int
    ): String {
        val cleanStopId = stopId.trim()
        val cleanStopName = stopName.trim()
        val cleanDay = dayOfWeek.coerceIn(1, 7)

        if (cleanStopId.isBlank()) throw IllegalArgumentException("Stop id can't be empty.")
        if (cleanStopName.isBlank()) throw IllegalArgumentException("Stop name can't be empty.")

        val docRef = db.collection("routeSchedule").document()
        val now = System.currentTimeMillis()

        val doc = ManagerScheduleEntryDoc(
            id = docRef.id,
            stopId = cleanStopId,
            stopName = cleanStopName,
            dayOfWeek = cleanDay,
            timeMinutes = timeMinutes,
            updatedAt = now
        )

        docRef.set(doc).await()
        return docRef.id
    }

    suspend fun updateManagerScheduleEntry(
        entryId: String,
        stopId: String,
        stopName: String,
        dayOfWeek: Int,
        timeMinutes: Int
    ) {
        val cleanStopId = stopId.trim()
        val cleanStopName = stopName.trim()
        val cleanDay = dayOfWeek.coerceIn(1, 7)

        if (cleanStopId.isBlank()) throw IllegalArgumentException("Stop id can't be empty.")
        if (cleanStopName.isBlank()) throw IllegalArgumentException("Stop name can't be empty.")

        val doc = ManagerScheduleEntryDoc(
            id = entryId,
            stopId = cleanStopId,
            stopName = cleanStopName,
            dayOfWeek = cleanDay,
            timeMinutes = timeMinutes,
            updatedAt = System.currentTimeMillis()
        )

        db.collection("routeSchedule")
            .document(entryId)
            .set(doc)
            .await()
    }

    suspend fun deleteManagerScheduleEntry(entryId: String) {
        db.collection("routeSchedule")
            .document(entryId)
            .delete()
            .await()
    }
}