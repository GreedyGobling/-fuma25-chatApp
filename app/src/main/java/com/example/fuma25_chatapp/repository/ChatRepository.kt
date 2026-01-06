package com.example.fuma25_chatapp.repository

import com.example.fuma25_chatapp.data.ChatRoom
import com.example.fuma25_chatapp.data.Message
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()

    fun listenToChatRooms(
        userId: String,
        onUpdate: (List<ChatRoom>) -> Unit
    ): ListenerRegistration {
        return db.collection("chat-rooms")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val rooms = snapshot.documents.map { doc ->
                    val membersAny = doc.get("members") as? List<*>
                    val members = membersAny?.filterIsInstance<String>() ?: emptyList()

                    ChatRoom(
                        id = doc.id,
                        members = members,
                        title = doc.getString("title") ?: ""
                    )
                }

                onUpdate(rooms)
            }
    }

    fun listenToMessages(
        chatRoomId: String,
        onUpdate: (List<Message>) -> Unit
    ): ListenerRegistration {
        return db.collection("chat-rooms")
            .document(chatRoomId)
            .collection("messages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

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
     * Sends a new message to the given chat room.
     * Uses server time for createdAt to ensure consistent ordering across devices.
     */
    fun sendMessage(
        chatRoomId: String,
        senderId: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            onError(IllegalArgumentException("Message is blank"))
            return
        }

        val message = mapOf(
            "text" to trimmed,
            "senderId" to senderId,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("chat-rooms")
            .document(chatRoomId)
            .collection("messages")
            .add(message)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }
}
