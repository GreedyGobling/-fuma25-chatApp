package com.example.fuma25_chatapp.repository

import com.example.fuma25_chatapp.data.ChatRoom
import com.example.fuma25_chatapp.data.Message
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ChatRepository {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Listen to chat rooms where the user is a member.
     * Rooms are sorted locally by createdAt (newest first),
     * so no Firestore index is required.
     */
    fun listenToChatRooms(
        userId: String,
        onUpdate: (List<ChatRoom>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {

        return db.collection("chat-rooms")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, error ->

                if (error != null && snapshot == null) {
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
                    // Sort locally: newest first
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }

                onUpdate(rooms)
            }
    }

    /**
     * Listen to messages inside a chat room.
     * Messages are ordered oldest -> newest.
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
            onError("Message is empty")
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
                    mapOf(
                        "lastMessage" to trimmed,
                        "lastMessageAt" to FieldValue.serverTimestamp()
                    )
                )
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(mapFirestoreError(e))
            }
    }

    /**
     * Create a new chat room.
     * The creator is automatically added as a member.
     */
    fun createChatRoom(
        title: String,
        creatorUserId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) {
            onError("Title is empty")
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
            .addOnSuccessListener { docRef ->
                onSuccess(docRef.id)
            }
            .addOnFailureListener { e ->
                onError(mapFirestoreError(e))
            }
    }

    /**
     * Delete a chat room:
     * 1) delete all messages in /chat-rooms/{roomId}/messages
     * 2) delete the room document
     *
     * NOTE: Client-side deletion is OK for small rooms.
     * Firestore batch limit is 500 operations.
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

                // Delete all message documents
                snapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }

                // Delete the room itself
                batch.delete(roomRef)

                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(mapFirestoreError(e)) }
            }
            .addOnFailureListener { e ->
                onError(mapFirestoreError(e))
            }
    }

    private fun mapFirestoreError(e: Exception): String {
        val code = (e as? FirebaseFirestoreException)?.code
        return when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "PERMISSION_DENIED (blocked by Firestore rules)."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "UNAVAILABLE (network/server issue)."
            else ->
                e.message ?: "Unknown Firestore error"
        }
    }

    // 1. Find a friend
    fun addFriendByEmail(currentUserId: String, friendEmail: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("users")
            .whereEqualTo("email", friendEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onError("Ingen användare hittades med den e-posten")
                    return@addOnSuccessListener
                }
                val friendUid = snapshot.documents.first().id

                // Update friendlist
                db.collection("users").document(currentUserId)
                    .update("friends", FieldValue.arrayUnion(friendUid))
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(e.message ?: "Kunde inte spara vän") }
            }
            .addOnFailureListener { e -> onError(e.message ?: "Sökning misslyckades") }
    }

    // 2. invite friend to specifik chatroom
    fun inviteFriendToRoom(chatRoomId: String, friendUid: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("chat-rooms").document(chatRoomId)
            .update("members", FieldValue.arrayUnion(friendUid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Kunde inte bjuda in vän") }
    }
}
