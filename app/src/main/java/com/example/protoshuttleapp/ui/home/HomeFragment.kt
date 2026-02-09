package com.example.protoshuttleapp.ui.home

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.ui.MainActivity
import com.example.protoshuttleapp.ui.NotificationStore
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var latestContainer: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        latestContainer = view.findViewById(R.id.latestContainer)

        // Buttons: switch bottom-nav tabs instead of navigating manually
        view.findViewById<View>(R.id.btnViewLiveMap).setOnClickListener {
            switchToTab(R.id.navigation_map)
        }

        view.findViewById<View>(R.id.btnViewFullSchedule).setOnClickListener {
            switchToTab(R.id.navigation_schedule)
        }

        refreshLatestNotifications()
    }

    private fun switchToTab(menuItemId: Int) {
        // BottomNavigationView id in activity_main.xml is navView (NOT nav_view)
        val bottomNav =
            (activity as? MainActivity)?.findViewById<BottomNavigationView>(R.id.navView)

        bottomNav?.selectedItemId = menuItemId
    }

    private fun refreshLatestNotifications() {
        latestContainer.removeAllViews()

        val latest = NotificationStore.latestActive(limit = 2)

        if (latest.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "• No notifications yet"
                textSize = 14f
            }
            latestContainer.addView(tv)
            return
        }

        for (n in latest) {
            val tv = TextView(requireContext()).apply {
                text = "• ${n.title} — ${n.message}"
                textSize = 14f
            }
            latestContainer.addView(tv)
        }
    }
}
