package com.example.protoshuttleapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.protoshuttleapp.R
import com.google.android.material.button.MaterialButton

class RoleSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_select)

        val btnStudent = findViewById<MaterialButton>(R.id.btnRoleStudent)
        val btnDriver = findViewById<MaterialButton>(R.id.btnRoleDriver)
        val btnManager = findViewById<MaterialButton>(R.id.btnRoleManager)

        btnStudent.setOnClickListener {
            // Student side = your current app
            startActivity(Intent(this, MainActivity::class.java))
        }

        btnDriver.setOnClickListener {
            // Driver side = mock for now
            startActivity(Intent(this, DriverActivity::class.java))
        }

        btnManager.setOnClickListener {
            // Does nothing for now (as requested)
            Toast.makeText(this, "Manager side coming soon.", Toast.LENGTH_SHORT).show()
        }
    }
}
