package com.example.protoshuttleapp.ui.driver

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.protoshuttleapp.R
import com.google.android.material.switchmaterial.SwitchMaterial

class DriverSettingsFragment : Fragment(R.layout.fragment_driver_settings) {

    private lateinit var store: DriverSettingStore
    private lateinit var shareLiveLocationSwitch: SwitchMaterial

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        store = DriverSettingStore(requireContext())
        shareLiveLocationSwitch = view.findViewById(R.id.shareLiveLocationSwitch)

        shareLiveLocationSwitch.isChecked = store.shareLiveLocationOn

        shareLiveLocationSwitch.setOnCheckedChangeListener { _, isChecked ->
            store.shareLiveLocationOn = isChecked
        }
    }
}