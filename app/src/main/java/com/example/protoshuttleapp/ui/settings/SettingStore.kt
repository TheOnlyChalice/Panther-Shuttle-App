package com.example.protoshuttleapp.ui.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SettingStore(context: Context) {

    private val prefs = context.getSharedPreferences("panther_settings", Context.MODE_PRIVATE)

    // --- Existing-ish toggles (keep these names stable) ---
    var useMiles: Boolean
        get() = prefs.getBoolean("useMiles", true)
        set(value) = prefs.edit().putBoolean("useMiles", value).apply()

    var serviceAlertsOn: Boolean
        get() = prefs.getBoolean("serviceAlertsOn", true)
        set(value) = prefs.edit().putBoolean("serviceAlertsOn", value).apply()

    var stopApproachingOn: Boolean
        get() = prefs.getBoolean("stopApproachingOn", true)
        set(value) = prefs.edit().putBoolean("stopApproachingOn", value).apply()

    // --- Favorite Stops (stop + time), stored permanently ---
    fun getFavoriteStops(): List<FavoriteStop> {
        val raw = prefs.getString("favoriteStopsJson", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<FavoriteStop>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val stop = obj.optString("stopName", "")
                val time = obj.optInt("timeMinutes", 8 * 60) // default 8:00 AM
                if (stop.isNotBlank()) out.add(FavoriteStop(stopName = stop, timeMinutes = time))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setFavoriteStops(items: List<FavoriteStop>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("stopName", item.stopName)
            obj.put("timeMinutes", item.timeMinutes)
            arr.put(obj)
        }
        prefs.edit().putString("favoriteStopsJson", arr.toString()).apply()
    }
}
