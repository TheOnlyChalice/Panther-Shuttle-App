package com.example.protoshuttleapp.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var favoriteStopReminderManager: FavoriteStopReminderManager

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // no-op; reminders will work automatically once permission is granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        bottomNav = binding.navView

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        bottomNav.setupWithNavController(navController)

        notificationsBadge = bottomNav.getOrCreateBadge(R.id.navigation_notify).apply {
            isVisible = false
        }

        requestNotificationsPermissionIfNeeded()

        favoriteStopReminderManager = FavoriteStopReminderManager(this)
        favoriteStopReminderManager.start()
    }

    override fun onDestroy() {
        if (::favoriteStopReminderManager.isInitialized) {
            favoriteStopReminderManager.stop()
        }
        super.onDestroy()
    }

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_switch_role -> {
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

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationsPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}