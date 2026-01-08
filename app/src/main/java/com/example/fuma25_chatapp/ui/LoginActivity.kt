package com.example.fuma25_chatapp.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.fuma25_chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LoginActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val existingUser = auth.currentUser
        if (existingUser != null) {
            val uid = existingUser.uid
            val email = existingUser.email.orEmpty()

            if (uid.isNotBlank() && email.isNotBlank()) {
                ensureUserDocs(uid, email)
            }

            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        progress = findViewById(R.id.loginProgress)

        loginButton.setOnClickListener { login() }
        registerButton.setOnClickListener { register() }
    }

    private fun login() {
        val (email, password) = readAndValidateInputs() ?: return
        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                val userEmail = result.user?.email.orEmpty()

                if (uid.isNotBlank() && userEmail.isNotBlank()) {
                    ensureUserDocs(uid, userEmail)
                }

                setLoading(false)
                toast("Inloggad ✅")
                goToMain()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast("Inloggning misslyckades: ${e.message}")
            }
    }

    private fun register() {
        val (email, password) = readAndValidateInputs() ?: return
        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                val userEmail = result.user?.email.orEmpty()

                if (uid.isNotBlank() && userEmail.isNotBlank()) {
                    ensureUserDocs(uid, userEmail)
                }

                setLoading(false)
                toast("Konto skapat ✅")
                goToMain()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                val msg = when (e) {
                    is FirebaseAuthUserCollisionException ->
                        "Det finns redan ett konto med den e-posten."
                    else -> e.message ?: "Registrering misslyckades."
                }
                toast(msg)
            }
    }

    private fun ensureUserDocs(uid: String, email: String) {
        val emailLower = email.lowercase()
        val nameFromEmail = email.substringBefore("@")

        val user = auth.currentUser
        if (user?.displayName.isNullOrBlank()) {
            val profileUpdates = userProfileChangeRequest {
                displayName = nameFromEmail
            }
            user?.updateProfile(profileUpdates)
        }

        db.collection("users").document(uid)
            .set(mapOf("uid" to uid, "email" to emailLower), SetOptions.merge())

        db.collection("user-public").document(uid)
            .set(
                mapOf(
                    "emailLower" to emailLower,
                    "name" to nameFromEmail
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { e ->
                toast("Kunde inte uppdatera profil: ${e.message}")
            }
    }

    private fun readAndValidateInputs(): Pair<String, String>? {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()

        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Skriv en giltig e-post")
            return null
        }
        if (password.length < 6) {
            toast("Lösenord måste vara minst 6 tecken")
            return null
        }
        return email to password
    }

    private fun setLoading(isLoading: Boolean) {
        progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
        registerButton.isEnabled = !isLoading
        emailInput.isEnabled = !isLoading
        passwordInput.isEnabled = !isLoading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}