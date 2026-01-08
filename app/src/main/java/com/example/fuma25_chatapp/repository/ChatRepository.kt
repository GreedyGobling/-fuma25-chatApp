package com.example.fuma25_chatapp.repository

import com.example.fuma25_chatapp.data.ChatRoom
import com.example.fuma25_chatapp.data.Message
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class ChatRepository {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Listen to chat rooms where the user is a member.
     */
    fun listenToChatRooms(
        userId: String,
        onUpdate: (List<ChatRoom>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return db.collection("chat-rooms")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(mapFirestoreError(error))
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val rooms = snapshot.documents
                    .map { doc ->
                        val members =
                            (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()

                        ChatRoom(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            createdBy = doc.getString("createdBy") ?: "",
                            members = members,
                            createdAt = doc.getTimestamp("createdAt"),
                            lastMessage = doc.getString("lastMessage") ?: "",
                            lastMessageAt = doc.getTimestamp("lastMessageAt")
                        )
                    }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }

                onUpdate(rooms)
            }
    }

    /**
     * Listen to messages inside a chat room.
     */
    fun listenToMessages(
        chatRoomId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return db.collection("chat-rooms")
            .document(chatRoomId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(mapFirestoreError(error))
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val messages = snapshot.documents.map { doc ->
                    Message(
                        id = doc.id,
                        text = doc.getString("text") ?: "",
                        senderId = doc.getString("senderId") ?: "",
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }

                onUpdate(messages)
            }
    }

    /**
     * Send a message to a chat room.
     */
    fun sendMessage(
        chatRoomId: String,
        senderId: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            onError("Meddelandet är tomt")
            return
        }

        val message = mapOf(
            "text" to trimmed,
            "senderId" to senderId,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val roomRef = db.collection("chat-rooms").document(chatRoomId)

        roomRef.collection("messages")
            .add(message)
            .addOnSuccessListener {
                roomRef.update(
                    "lastMessage", trimmed,
                    "lastMessageAt", FieldValue.serverTimestamp()
                )
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(mapFirestoreError(e))
            }
    }

    /**
     * Create a new chat room.
     */
    fun createChatRoom(
        title: String,
        creatorUserId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) {
            onError("Titel saknas")
            return
        }

        val data = mapOf(
            "title" to trimmed,
            "members" to listOf(creatorUserId),
            "createdBy" to creatorUserId,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastMessage" to "",
            "lastMessageAt" to null
        )

        db.collection("chat-rooms")
            .add(data)
            .addOnSuccessListener { onSuccess(it.id) }
            .addOnFailureListener { e -> onError(mapFirestoreError(e)) }
    }

    /**
     * Delete a chat room and all messages.
     */
    fun deleteChatRoom(
        chatRoomId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val roomRef = db.collection("chat-rooms").document(chatRoomId)
        val messagesRef = roomRef.collection("messages")

        messagesRef.get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.delete(roomRef)

                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(mapFirestoreError(e)) }
            }
            .addOnFailureListener { e -> onError(mapFirestoreError(e)) }
    }

    /**
     * Find a friend by email (uses user-public collection).
     *
     * Notes:
     * - Prevent adding yourself.
     * - Use merge so the users/{uid} doc can be created if it doesn't exist yet.
     */
    fun addFriendByEmail(
        currentUserId: String,
        friendEmail: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val emailLower = friendEmail.trim().lowercase()
        if (emailLower.isBlank()) {
            onError("Skriv en e-postadress")
            return
        }

        db.collection("user-public")
            .whereEqualTo("emailLower", emailLower)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onError("Ingen användare hittades")
                    return@addOnSuccessListener
                }

                val friendUid = snapshot.documents.first().id

                if (friendUid == currentUserId) {
                    onError("Du kan inte lägga till dig själv")
                    return@addOnSuccessListener
                }

                // Create doc if missing + add friend uid without duplicates
                db.collection("users").document(currentUserId)
                    .set(
                        mapOf("friends" to FieldValue.arrayUnion(friendUid)),
                        SetOptions.merge()
                    )
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e ->
                        onError(e.message ?: "Kunde inte spara vän")
                    }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Sökning misslyckades")
            }
    }

    /**
     * Invite a friend to a chat room.
     */
    fun inviteFriendToRoom(
        chatRoomId: String,
        friendUid: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("chat-rooms").document(chatRoomId)
            .update("members", FieldValue.arrayUnion(friendUid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                onError(e.message ?: "Kunde inte bjuda in vän")
            }
    }

    private fun mapFirestoreError(e: Exception): String {
        return when ((e as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Åtkomst nekad (Firestore-regler)"
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Nätverksfel"
            else ->
                e.message ?: "Okänt fel"
        }
    }
}
