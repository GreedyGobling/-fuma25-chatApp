package com.example.fuma25_chatapp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore

class FriendsListActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends_list)

        recyclerView = findViewById(R.id.recyclerViewFriends)
        friendsAdapter = FriendsAdapter()

        recyclerView.adapter = friendsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.clipToPadding = false

        loadFriends()
    }

    private fun loadFriends() {
        val myUid = auth.currentUser?.uid
        if (myUid.isNullOrBlank()) {
            toast("Du är inte inloggad")
            finish()
            return
        }

        db.collection("users").document(myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val friendIds =
                    (snapshot.get("friends") as? List<*>)?.filterIsInstance<String>().orEmpty()

                if (friendIds.isEmpty()) {
                    toast("Din vänlista är tom")
                    friendsAdapter.submitList(emptyList())
                    return@addOnSuccessListener
                }

                // whereIn supports max 10 values -> limit to 10 for now
                if (friendIds.size > 10) {
                    toast("Visar bara de 10 första vännerna (Firestore-begränsning).")
                }

                db.collection("user-public")
                    .whereIn(FieldPath.documentId(), friendIds.take(10))
                    .get()
                    .addOnSuccessListener { friendDocs ->
                        val friendsList = friendDocs.map { doc ->
                            User(
                                uid = doc.id,
                                name = doc.getString("name") ?: "Okänd",
                                // Mapping emailLower to email display
                                email = doc.getString("emailLower") ?: ""
                            )
                        }

                        friendsAdapter.submitList(friendsList)
                    }
                    .addOnFailureListener { e ->
                        toast(e.message ?: "Kunde inte hämta detaljer")
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
