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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.fuma25_chatapp.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var googleButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Om redan inloggad -> hoppa direkt till MainActivity
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        googleButton = findViewById(R.id.googleButton)
        progress = findViewById(R.id.loginProgress)

        credentialManager = CredentialManager.create(this)

        loginButton.setOnClickListener { login() }
        registerButton.setOnClickListener { register() }
        googleButton.setOnClickListener { googleLogin() }
    }

    private fun login() {
        val (email, password) = readAndValidateInputs() ?: return
        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, "Inloggad", Toast.LENGTH_SHORT).show()
                goToMain()
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Login misslyckades: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun register() {
        val (email, password) = readAndValidateInputs() ?: return
        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, "Konto skapat!", Toast.LENGTH_SHORT).show()
                goToMain()
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Registrering misslyckades: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun googleLogin() {
        setLoading(true)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    this@LoginActivity,
                    request
                )
                handleResult(result)
            } catch (e: GetCredentialCancellationException) {
                setLoading(false)
                Toast.makeText(this@LoginActivity, "Inloggning avbruten", Toast.LENGTH_SHORT).show()
            } catch (e: NoCredentialException) {
                setLoading(false)
                Toast.makeText(this@LoginActivity, "Inga Google-konton på enheten", Toast.LENGTH_SHORT).show()
            } catch (e: GetCredentialException) {
                setLoading(false)
                Toast.makeText(this@LoginActivity, "Error!: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleResult(result: GetCredentialResponse) {
        if (result.credential is CustomCredential && result.credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                val idToken = googleIdTokenCredential.idToken
                authenticateWithFirebase(idToken)
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this, "Autentisering misslyckades: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            setLoading(false)
            Toast.makeText(this, "Ogiltigt inloggningsformat", Toast.LENGTH_SHORT).show()
        }
    }

    private fun authenticateWithFirebase(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(firebaseCredential)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, "Inloggad med Google", Toast.LENGTH_SHORT).show()
                goToMain()
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Misslyckades: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun readAndValidateInputs(): Pair<String, String>? {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()

        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Skriv en giltig e-post", Toast.LENGTH_SHORT).show()
            return null
        }

        if (password.length < 6) {
            Toast.makeText(this, "Lösenord måste vara minst 6 tecken", Toast.LENGTH_SHORT).show()
            return null
        }

        return email to password
    }

    private fun setLoading(isLoading: Boolean) {
        progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
        registerButton.isEnabled = !isLoading
        googleButton.isEnabled = !isLoading
        emailInput.isEnabled = !isLoading
        passwordInput.isEnabled = !isLoading
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}