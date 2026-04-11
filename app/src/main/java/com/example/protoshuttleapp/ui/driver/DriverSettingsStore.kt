package com.example.protoshuttleapp.ui.driver

import android.content.Context

class DriverSettingStore(context: Context) {

    private val prefs = context.getSharedPreferences("panther_driver_settings", Context.MODE_PRIVATE)

    var shareLiveLocationOn: Boolean
        get() = prefs.getBoolean("shareLiveLocationOn", true)
        set(value) = prefs.edit().putBoolean("shareLiveLocationOn", value).apply()
}