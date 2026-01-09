package com.example.fuma25_chatapp.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FriendsRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun sendFriendRequest(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (targetUid == myUid) return

        db.collection("users")
            .document(targetUid)
            .set(
                mapOf("incoming_requests" to FieldValue.arrayUnion(myUid)),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun acceptFriendRequest(fromUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (fromUid == myUid) return

        val batch = db.batch()
        val myRef = db.collection("users").document(myUid)
        val otherRef = db.collection("users").document(fromUid)

        batch.set(
            myRef,
            mapOf("friends" to FieldValue.arrayUnion(fromUid)),
            SetOptions.merge()
        )
        batch.set(
            otherRef,
            mapOf("friends" to FieldValue.arrayUnion(myUid)),
            SetOptions.merge()
        )
        batch.set(
            myRef,
            mapOf("incoming_requests" to FieldValue.arrayRemove(fromUid)),
            SetOptions.merge()
        )

        batch.commit().await()
    }

    suspend fun rejectFriendRequest(fromUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (fromUid == myUid) return

        db.collection("users")
            .document(myUid)
            .set(
                mapOf("incoming_requests" to FieldValue.arrayRemove(fromUid)),
                SetOptions.merge()
            )
            .await()
    }
}
