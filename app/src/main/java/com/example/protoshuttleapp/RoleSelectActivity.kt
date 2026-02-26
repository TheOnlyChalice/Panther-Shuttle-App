package com.example.protoshuttleapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.protoshuttleapp.R
import com.example.protoshuttleapp.ui.driver.DriverMainActivity
import com.google.android.material.button.MaterialButton

class RoleSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_select)

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
            Toast.makeText(this, "Manager side coming soon.", Toast.LENGTH_SHORT).show()
        }
    }
}