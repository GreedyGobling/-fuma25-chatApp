package com.example.fuma25_chatapp.repository

import com.example.fuma25_chatapp.data.ChatRoom
import com.example.fuma25_chatapp.data.Message
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ChatRepository {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun listenToChatRooms(
        userId: String,
        onUpdate: (List<ChatRoom>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return db.collection("chat-rooms")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Firestore error")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

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
        onUpdate: (List<Message>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return db.collection("chat-rooms")
            .document(chatRoomId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Firestore error")
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
                // Optional: update last message metadata
                roomRef.update(
                    mapOf(
                        "lastMessage" to trimmed,
                        "lastMessageAt" to FieldValue.serverTimestamp()
                    )
                )
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Could not send message")
            }
    }

    // Creates a new chat room in Firestore
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
                onError(e.message ?: "Could not create chat room")
            }
    }
}
