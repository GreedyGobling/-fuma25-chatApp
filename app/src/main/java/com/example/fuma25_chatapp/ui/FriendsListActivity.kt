package com.example.fuma25_chatapp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.data.User
import com.example.fuma25_chatapp.repository.FriendsRepository
import com.example.fuma25_chatapp.viewmodel.FriendsListViewModel
import com.example.fuma25_chatapp.viewmodel.FriendsListViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendsListActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val friendsRepo: FriendsRepository by lazy { FriendsRepository(auth, db) }

    private val viewModel: FriendsListViewModel by viewModels {
        FriendsListViewModelFactory(auth, db, friendsRepo)
    }

    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var recyclerView: RecyclerView

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

        // Observers
        viewModel.friends.observe(this) { list ->
            friendsAdapter.submitList(list)
        }

        viewModel.toastEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { toast(it) }
        }

        viewModel.incomingRequestEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { fromUid ->
                showSingleRequestDialog(fromUid)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stop()
    }

    /**
     * UI: resolve name/email from user-public and show accept/reject dialog.
     * (Data fetching could be moved into VM too, but this keeps VM simple and Activities still thin.)
     */
    private fun showSingleRequestDialog(fromUid: String) {
        db.collection("user-public").document(fromUid)
            .get()
            .addOnSuccessListener { doc ->
                val email = doc.getString("emailLower") ?: ""

                val displayName = doc.getString("name")?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@").takeIf { it.isNotBlank() }
                    ?: "Okänd"

                showAcceptRejectDialog(fromUid, displayName, email)
            }
            .addOnFailureListener {
                showAcceptRejectDialog(fromUid, fromUid, "")
            }
    }

    private fun showAcceptRejectDialog(fromUid: String, displayName: String, email: String) {
        val message = if (email.isBlank()) displayName else "$displayName\n$email"

        AlertDialog.Builder(this)
            .setTitle("Vänförfrågan")
            .setMessage(message)
            .setPositiveButton("Acceptera") { _, _ ->
                viewModel.acceptFriendRequest(fromUid)
                viewModel.markRequestDialogClosed()
            }
            .setNegativeButton("Neka") { _, _ ->
                viewModel.rejectFriendRequest(fromUid)
                viewModel.markRequestDialogClosed()
            }
            .setNeutralButton("Avbryt") { _, _ ->
                viewModel.markRequestDialogClosed()
            }
            .setOnDismissListener {
                viewModel.markRequestDialogClosed()
            }
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
