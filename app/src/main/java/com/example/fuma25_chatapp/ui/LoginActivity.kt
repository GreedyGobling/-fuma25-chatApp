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
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException

class LoginActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        progress = findViewById(R.id.loginProgress)

        // Om redan inloggad -> hoppa direkt till MainActivity
        if (auth.currentUser != null) {
            goToMainClearBackstack()
            return
        }

        loginButton.setOnClickListener { login() }
        registerButton.setOnClickListener { register() }
    }

    private fun login() {
        val (email, password) = readAndValidateInputs() ?: return
        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                setLoading(false)
                toast("Inloggad ✅")
                goToMainClearBackstack()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast(mapLoginError(e))
            }
    }

    private fun register() {
        val (email, password) = readAndValidateInputs() ?: return
        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                setLoading(false)
                toast("Konto skapat ✅")
                goToMainClearBackstack()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast(mapRegisterError(e))
            }
    }

    private fun readAndValidateInputs(): Pair<String, String>? {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()

        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Skriv en giltig e-postadress")
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

    private fun goToMainClearBackstack() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun mapLoginError(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidUserException ->
                "Inget konto hittades för den e-posten. Tryck på Skapa konto."
            is FirebaseAuthInvalidCredentialsException ->
                "Fel e-post eller lösenord."
            is FirebaseNetworkException ->
                "Ingen internetanslutning. Försök igen."
            else ->
                "Login misslyckades: ${e.message ?: "okänt fel"}"
        }
    }

    private fun mapRegisterError(e: Exception): String {
        return when (e) {
            is FirebaseAuthUserCollisionException ->
                "Det finns redan ett konto med den e-posten. Tryck på Logga in istället."
            is FirebaseNetworkException ->
                "Ingen internetanslutning. Försök igen."
            else ->
                "Registrering misslyckades: ${e.message ?: "okänt fel"}"
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
