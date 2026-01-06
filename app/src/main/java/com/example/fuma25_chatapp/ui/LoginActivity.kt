package com.example.fuma25_chatapp.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.fuma25_chatapp.ui.MainActivity
import com.example.fuma25_chatapp.R
import com.google.android.gms.common.SignInButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnGoogle: SignInButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogle = findViewById(R.id.btnGoogleSignIn)

        // Google Sign-In is not implemented in this MVP.
        btnGoogle.visibility = View.GONE

        // If already signed in, go straight to MainActivity.
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (!isValidEmail(email)) {
                toast("Please enter a valid email.")
                return@setOnClickListener
            }
            if (password.length < 6) {
                toast("Password must be at least 6 characters.")
                return@setOnClickListener
            }

            setLoading(true)

            // MVP flow:
            // 1) Try to sign in
            // 2) If user doesn't exist -> create account
            signInOrRegister(email, password)
        }
    }

    private fun signInOrRegister(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Signed in successfully
                ensureUserDocument()
            }
            .addOnFailureListener { e ->
                when (e) {
                    is FirebaseAuthInvalidUserException -> {
                        // User not found -> auto-register (MVP convenience)
                        createAccount(email, password)
                    }
                    is FirebaseAuthInvalidCredentialsException -> {
                        // Wrong password / invalid credentials
                        setLoading(false)
                        toast("Wrong password or invalid credentials.")
                    }
                    else -> {
                        setLoading(false)
                        toast(e.message ?: "Login failed.")
                    }
                }
            }
    }

    private fun createAccount(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Account created successfully
                ensureUserDocument()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast(e.message ?: "Registration failed.")
            }
    }

    private fun ensureUserDocument() {
        val user = auth.currentUser
        if (user == null) {
            setLoading(false)
            toast("No authenticated user.")
            return
        }

        // Create/merge a user profile document in Firestore.
        // Using merge makes this safe to call on every login.
        val userDoc = mapOf(
            "email" to (user.email ?: ""),
            "name" to "", // We can add a name field later if we want.
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(user.uid)
            .set(userDoc, SetOptions.merge())
            .addOnSuccessListener {
                setLoading(false)
                goToMain()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast(e.message ?: "Could not create user profile.")
            }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        btnLogin.isEnabled = !isLoading
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}