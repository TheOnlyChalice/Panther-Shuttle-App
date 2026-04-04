package com.example.protoshuttleapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.data.FirebaseRepo
import com.example.protoshuttleapp.ui.driver.DriverMainActivity
import com.example.protoshuttleapp.ui.manager.ManagerMainActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class RoleSelectActivity : AppCompatActivity() {

    private val firebase = FirebaseRepo()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_select)

        lifecycleScope.launch {
            val ok = firebase.ensureSignedIn()
            if (!ok) {
                Toast.makeText(
                    this@RoleSelectActivity,
                    "Firebase sign-in failed (offline?)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val btnStudent = findViewById<MaterialButton>(R.id.btnRoleStudent)
        val btnDriver = findViewById<MaterialButton>(R.id.btnRoleDriver)
        val btnManager = findViewById<MaterialButton>(R.id.btnRoleManager)

        btnStudent.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        btnDriver.setOnClickListener {
            startActivity(Intent(this, DriverMainActivity::class.java))
        }

        btnManager.setOnClickListener {
            startActivity(Intent(this, ManagerMainActivity::class.java))
        }
    }
}