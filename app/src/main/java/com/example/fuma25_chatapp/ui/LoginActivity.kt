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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var googleButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val existingUser = auth.currentUser
        if (existingUser != null) {
            lifecycleScope.launch {
                try {
                    ensureUserDocs()
                } catch (_: Exception) {
                    // ignore, we still go to main
                }
                goToMain()
            }
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
                lifecycleScope.launch {
                    try {
                        ensureUserDocs()
                        setLoading(false)
                        toast("Inloggad ✅")
                        goToMain()
                    } catch (e: Exception) {
                        setLoading(false)
                        toast(e.toString())
                    }
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast(authErrorSv(e))
            }
    }

    private fun register() {
        val (email, password) = readAndValidateInputs() ?: return
        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                lifecycleScope.launch {
                    try {
                        ensureUserDocs()
                        setLoading(false)
                        toast("Konto skapat ✅")
                        goToMain()
                    } catch (e: Exception) {
                        setLoading(false)
                        toast(e.toString())
                    }
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                val msg = when (e) {
                    is FirebaseAuthUserCollisionException ->
                        "Det finns redan ett konto med den e-posten. Tryck på Logga in istället."
                    else ->
                        "Registrering misslyckades."
                }
                toast(msg)
            }
    }

    /**
     * Ensures Firestore docs exist WITHOUT overwriting existing arrays.
     *
     * Important:
     * - Only create friends/incoming_requests arrays the FIRST time the doc is created.
     * - If the doc already exists, update only stable fields (uid/email), keep arrays intact.
     */
    private suspend fun ensureUserDocs() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val email = user.email.orEmpty()
        val emailLower = email.lowercase()
        val nameFromEmail = if (email.contains("@")) email.substringBefore("@") else "User"

        // Ensure displayName exists
        if (user.displayName.isNullOrBlank()) {
            val profileUpdates = userProfileChangeRequest { displayName = nameFromEmail }
            user.updateProfile(profileUpdates).await()
        }

        val userRef = db.collection("users").document(uid)

        // Check if users/{uid} exists
        val snap = userRef.get().await()

        if (!snap.exists()) {
            // First time: create with empty arrays
            userRef.set(
                mapOf(
                    "uid" to uid,
                    "email" to emailLower,
                    "friends" to emptyList<String>(),
                    "incoming_requests" to emptyList<String>()
                ),
                SetOptions.merge()
            ).await()
        } else {
            // Already exists: do NOT overwrite arrays
            userRef.set(
                mapOf(
                    "uid" to uid,
                    "email" to emailLower
                ),
                SetOptions.merge()
            ).await()
        }

        // Public user doc can be merged every time
        db.collection("user-public").document(uid)
            .set(
                mapOf(
                    "emailLower" to emailLower,
                    "name" to (user.displayName ?: nameFromEmail)
                ),
                SetOptions.merge()
            )
            .await()
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
                val result = credentialManager.getCredential(this@LoginActivity, request)
                handleResult(result)
            } catch (e: GetCredentialCancellationException) {
                setLoading(false)
                toast("Inloggning avbruten")
            } catch (e: NoCredentialException) {
                setLoading(false)
                toast("Inga Google-konton på enheten")
            } catch (e: GetCredentialException) {
                setLoading(false)
                toast("Error: ${e.message}")
            }
        }
    }

    private fun handleResult(result: GetCredentialResponse) {
        if (result.credential is CustomCredential &&
            result.credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(result.credential.data)
                val idToken = googleIdTokenCredential.idToken
                authenticateWithFirebase(idToken)
            } catch (e: Exception) {
                setLoading(false)
                toast("Autentisering misslyckades: ${e.message}")
            }
        } else {
            setLoading(false)
            toast("Ogiltigt inloggningsformat")
        }
    }

    private fun authenticateWithFirebase(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(firebaseCredential)
            .addOnSuccessListener {
                lifecycleScope.launch {
                    try {
                        ensureUserDocs()
                        setLoading(false)
                        toast("Inloggad med Google ✅")
                        goToMain()
                    } catch (e: Exception) {
                        setLoading(false)
                        toast(e.toString())
                    }
                }
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Misslyckades: ${it.message}")
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
        googleButton.isEnabled = !isLoading
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

    private fun authErrorSv(e: Exception): String {
        return when (e.message) {
            "The supplied auth credential is incorrect, malformed or has expired." ->
                "Fel e-post eller lösenord."
            "There is no user record corresponding to this identifier. The user may have been deleted." ->
                "Det finns inget konto med den e-posten."
            "The password is invalid or the user does not have a password." ->
                "Fel e-post eller lösenord."
            "A network error (such as timeout, interrupted connection or unreachable host) has occurred." ->
                "Nätverksfel. Kontrollera internetanslutningen."
            else ->
                "Inloggning misslyckades."
        }
    }
}

