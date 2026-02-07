package com.example.protoshuttleapp.ui.home

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.ui.MainActivity
import com.example.protoshuttleapp.ui.NotificationStore

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var latestContainer: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        latestContainer = view.findViewById(R.id.latestContainer)

        // Buttons: switch bottom-nav tabs instead of navigating manually
        view.findViewById<View>(R.id.btnViewLiveMap).setOnClickListener {
            // Switch to Map tab
            (activity as? MainActivity)?.findViewById<View>(R.id.nav_view)?.let { navView ->
                // nav_view is the BottomNavigationView in activity_main.xml
                // selecting the item keeps the bottom-nav system consistent
                (navView as com.google.android.material.bottomnavigation.BottomNavigationView)
                    .selectedItemId = R.id.navigation_map
            }
        }

        view.findViewById<View>(R.id.btnViewFullSchedule).setOnClickListener {
            // Switch to Schedule tab
            (activity as? MainActivity)?.findViewById<View>(R.id.nav_view)?.let { navView ->
                (navView as com.google.android.material.bottomnavigation.BottomNavigationView)
                    .selectedItemId = R.id.navigation_schedule
            }
        }

        refreshLatestNotifications()
    }

    override fun onResume() {
        super.onResume()
        refreshLatestNotifications()
    }

    private fun refreshLatestNotifications() {
        // Update badge count too
        (activity as? MainActivity)?.updateNotificationBadge(NotificationStore.activeCount())

        latestContainer.removeAllViews()

        val latest = NotificationStore.latestActive(limit = 2)
        if (latest.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "• No notifications yet"
                textSize = 14f
                setTextColor(0xFF555555.toInt())
            }
            latestContainer.addView(tv)
            return
        }

        for (n in latest) {
            val tv = TextView(requireContext()).apply {
                text = "• ${n.title}: ${n.message}"
                textSize = 14f
                setTextColor(0xFF333333.toInt())
            }
            latestContainer.addView(tv)
        }
    }
}
