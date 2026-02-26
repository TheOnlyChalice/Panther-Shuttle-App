package com.example.protoshuttleapp.ui.driver

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.ui.RoleSelectActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class DriverMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_main)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarDriver)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Driver"

        val navHost =
            supportFragmentManager.findFragmentById(R.id.driver_nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.driverBottomNav)
        bottomNav.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Uses the same menu_main.xml you already have (Switch Role)
        menuInflater.inflate(R.menu.menu_driver, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_switch_role -> {
                val i = Intent(this, RoleSelectActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}