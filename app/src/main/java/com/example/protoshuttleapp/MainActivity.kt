package com.example.protoshuttleapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.databinding.ActivityMainBinding
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomNav: BottomNavigationView

    private var notificationsBadge: BadgeDrawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If your activity_main.xml has a Toolbar with id "toolbar"
        // this enables the top-right 3-dot menu.
        setSupportActionBar(binding.toolbar)

        bottomNav = binding.navView

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        bottomNav.setupWithNavController(navController)

        // Setup badge (hidden by default)
        notificationsBadge = bottomNav.getOrCreateBadge(R.id.navigation_notify).apply {
            isVisible = false
        }

        // If you want to update badge immediately on launch:
        // updateNotificationBadge(NotificationStore.activeCount())
    }

    /**
     * Called by fragments (Notify/Home) to keep the badge accurate.
     */
    fun updateNotificationBadge(count: Int) {
        val badge = notificationsBadge ?: bottomNav.getOrCreateBadge(R.id.navigation_notify)
        notificationsBadge = badge

        if (count <= 0) {
            badge.isVisible = false
            badge.clearNumber()
        } else {
            badge.isVisible = true
            badge.number = count
        }
    }

    // --- Top-right menu (Switch Role) ---
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_switch_role -> {
                // Go back to the 3-button role select screen
                val i = Intent(this, RoleSelectActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(i)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
