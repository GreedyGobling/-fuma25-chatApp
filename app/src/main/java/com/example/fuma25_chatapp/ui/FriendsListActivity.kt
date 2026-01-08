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

    private lateinit var friendsAdapter: FriendsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends_list)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewFriends)
        friendsAdapter = FriendsAdapter()
        recyclerView.adapter = friendsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadFriends()
    }

    private fun loadFriends() {
        val db = FirebaseFirestore.getInstance()
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val friendIds = (snapshot.get("friends") as? List<*>)?.filterIsInstance<String>().orEmpty()

                if (friendIds.isEmpty()) {
                    Toast.makeText(this, "Din vänlista är tom", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                db.collection("user-public")
                    .whereIn(FieldPath.documentId(), friendIds)
                    .get()
                    .addOnSuccessListener { friendDocs ->
                        val friendsList = friendDocs.mapNotNull { doc ->
                            User(
                                uid = doc.id,
                                name = doc.getString("name") ?: "Okänd",
                                email = doc.getString("emailLower") ?: "" // Mappar emailLower till email
                            )
                        }

                        friendsAdapter.submitList(friendsList)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Kunde inte hämta detaljer: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Kunde inte hämta din vänlista", Toast.LENGTH_SHORT).show()
            }
    }
}