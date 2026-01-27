package com.example.protoshuttleapp.ui.login

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.example.protoshuttleapp.R

/**
 * Minimal, compile-safe LoginFragment.
 * Uses the fragment XML only; no view binding or ViewModel wiring.
 */
class LoginFragment : Fragment(R.layout.fragment_login) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No-op: remove old references (e.g., "welcome") that caused compile errors.
    }
}
