package com.example.protoshuttleapp.ui.manager

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.ui.MainActivity
import com.example.protoshuttleapp.ui.RoleSelectActivity
import com.example.protoshuttleapp.ui.driver.DriverMainActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class ManagerMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.managerToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Manager"

        val bottomNav = findViewById<BottomNavigationView>(R.id.managerBottomNav)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.managerFragmentContainer, ManagerMapFragment())
                .commit()

            bottomNav.selectedItemId = R.id.nav_manager_map
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_manager_map -> {
                    val current = supportFragmentManager.findFragmentById(R.id.managerFragmentContainer)
                    if (current !is ManagerMapFragment) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.managerFragmentContainer, ManagerMapFragment())
                            .commit()
                    }
                    true
                }

                R.id.nav_manager_schedule -> {
                    val current = supportFragmentManager.findFragmentById(R.id.managerFragmentContainer)
                    if (current !is ManagerScheduleFragment) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.managerFragmentContainer, ManagerScheduleFragment())
                            .commit()
                    }
                    true
                }

                else -> false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.manager_overflow_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_student_side -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return true
            }

            R.id.menu_driver_side -> {
                startActivity(Intent(this, DriverMainActivity::class.java))
                finish()
                return true
            }

            R.id.menu_role_select -> {
                startActivity(Intent(this, RoleSelectActivity::class.java))
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}