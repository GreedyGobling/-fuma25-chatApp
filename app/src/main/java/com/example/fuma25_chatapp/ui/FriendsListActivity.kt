package com.example.fuma25_chatapp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.data.User
import com.example.fuma25_chatapp.repository.FriendsRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class FriendsListActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val friendsRepo: FriendsRepository by lazy { FriendsRepository(auth, db) }

    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var recyclerView: RecyclerView

    private var userListener: ListenerRegistration? = null

    private var lastRequestsSignature: String = ""
    private var requestsDialogShowing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends_list)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Mina vänner"
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerViewFriends)
        friendsAdapter = FriendsAdapter()

        recyclerView.adapter = friendsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.clipToPadding = false

        loadFriendsOnce()
    }

    override fun onStart() {
        super.onStart()
        startListeningToMyUserDoc()
    }

    override fun onStop() {
        super.onStop()
        userListener?.remove()
        userListener = null
        requestsDialogShowing = false
    }

    private fun startListeningToMyUserDoc() {
        val myUid = auth.currentUser?.uid
        if (myUid.isNullOrBlank()) {
            toast("Du är inte inloggad")
            finish()
            return
        }

        userListener?.remove()
        userListener = db.collection("users").document(myUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    toast(err.message ?: "Kunde inte lyssna på vänrequests")
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) return@addSnapshotListener

                val requests =
                    (snap.get("incoming_requests") as? List<*>)?.filterIsInstance<String>().orEmpty()

                // Keep friend list fresh too (useful after accept)
                loadFriendsOnce()

                val signature = requests.sorted().joinToString("|")

                // Show ONE dialog directly (no "list dialog" first)
                if (requests.isNotEmpty() && signature != lastRequestsSignature && !requestsDialogShowing) {
                    lastRequestsSignature = signature
                    requestsDialogShowing = true
                    showSingleRequestDialog(requests.first())
                }

                if (requests.isEmpty()) {
                    lastRequestsSignature = ""
                }
            }
    }

    /**
     * Show accept/reject directly for the first request,
     * and display the user's name/email (from user-public) instead of UID.
     */
    private fun showSingleRequestDialog(fromUid: String) {
        db.collection("user-public").document(fromUid)
            .get()
            .addOnSuccessListener { doc ->
                val email = doc.getString("emailLower") ?: ""

                // Avoid Elvis warning by using takeIf + fallback chain
                val displayName = doc.getString("name")?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@").takeIf { it.isNotBlank() }
                    ?: "Okänd"

                showAcceptRejectDialog(fromUid, displayName, email)
            }
            .addOnFailureListener {
                // Fallback if public profile not found
                showAcceptRejectDialog(fromUid, fromUid, "")
            }
    }

    private fun showAcceptRejectDialog(fromUid: String, displayName: String, email: String) {
        val message = if (email.isBlank()) displayName else "$displayName\n$email"

        AlertDialog.Builder(this)
            .setTitle("Vänförfrågan")
            .setMessage(message)
            .setPositiveButton("Acceptera") { _, _ ->
                lifecycleScope.launch {
                    try {
                        friendsRepo.acceptFriendRequest(fromUid)
                        toast("Ni är nu vänner ✅")
                        loadFriendsOnce()
                    } catch (e: Exception) {
                        toast(e.toString())
                    } finally {
                        requestsDialogShowing = false
                    }
                }
            }
            .setNegativeButton("Neka") { _, _ ->
                lifecycleScope.launch {
                    try {
                        friendsRepo.rejectFriendRequest(fromUid)
                        toast("Förfrågan nekad")
                    } catch (e: Exception) {
                        toast(e.toString())
                    } finally {
                        requestsDialogShowing = false
                    }
                }
            }
            .setNeutralButton("Avbryt") { _, _ ->
                requestsDialogShowing = false
            }
            .setOnDismissListener {
                requestsDialogShowing = false
            }
            .show()
    }

    private fun loadFriendsOnce() {
        val myUid = auth.currentUser?.uid
        if (myUid.isNullOrBlank()) return

        db.collection("users").document(myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val friendIds =
                    (snapshot.get("friends") as? List<*>)?.filterIsInstance<String>().orEmpty()

                if (friendIds.isEmpty()) {
                    friendsAdapter.submitList(emptyList())
                    return@addOnSuccessListener
                }

                // whereIn max 10
                val ids = friendIds.take(10)

                db.collection("user-public")
                    .whereIn(FieldPath.documentId(), ids)
                    .get()
                    .addOnSuccessListener { friendDocs ->
                        val friendsList = friendDocs.map { doc ->
                            User(
                                uid = doc.id,
                                name = doc.getString("name") ?: "Okänd",
                                email = doc.getString("emailLower") ?: ""
                            )
                        }
                        friendsAdapter.submitList(friendsList)
                    }
                    .addOnFailureListener { e ->
                        toast(e.message ?: "Kunde inte hämta vän-detaljer")
                    }
            }
            .addOnFailureListener { e ->
                toast(e.message ?: "Kunde inte hämta din vänlista")
            }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
