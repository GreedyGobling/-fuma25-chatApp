package com.example.fuma25_chatapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.fuma25_chatapp.data.Message
import com.example.fuma25_chatapp.data.User
import com.example.fuma25_chatapp.repository.ChatRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChatViewModel(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val chatRepo: ChatRepository
) : ViewModel() {

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    // For invite UI
    private val _inviteFriendsEvent = MutableLiveData<Event<List<User>>>()
    val inviteFriendsEvent: LiveData<Event<List<User>>> = _inviteFriendsEvent

    private var messagesListener: ListenerRegistration? = null

    fun startListening(chatRoomId: String) {
        messagesListener?.remove()
        messagesListener = chatRepo.listenToMessages(
            chatRoomId = chatRoomId,
            onUpdate = { list -> _messages.postValue(list) },
            onError = { msg -> _toastEvent.postValue(Event(msg)) }
        )
    }

    fun stopListening() {
        messagesListener?.remove()
        messagesListener = null
    }

    fun sendMessage(chatRoomId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val user = auth.currentUser ?: return

        val currentUserName = when {
            !user.displayName.isNullOrBlank() -> user.displayName
            !user.email.isNullOrBlank() -> user.email!!.substringBefore("@")
            else -> "Användare"
        } ?: "Användare"

        val message = Message(
            id = "",
            text = trimmed,
            senderId = user.uid,
            senderName = currentUserName,
            createdAt = Timestamp.now()
        )

        chatRepo.sendMessage(
            chatRoomId = chatRoomId,
            message = message,
            onSuccess = { /* UI clears input */ },
            onError = { msg -> _toastEvent.postValue(Event(msg)) }
        )
    }

    fun loadFriendsForInvite() {
        val myUid = auth.currentUser?.uid ?: return

        db.collection("users").document(myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val friendIds =
                    (snapshot.get("friends") as? List<*>)?.filterIsInstance<String>().orEmpty()

                if (friendIds.isEmpty()) {
                    _toastEvent.postValue(Event("Du har inga vänner ännu"))
                    return@addOnSuccessListener
                }

                val ids = friendIds.take(10) // whereIn max 10

                db.collection("user-public")
                    .whereIn(FieldPath.documentId(), ids)
                    .get()
                    .addOnSuccessListener { docs ->
                        val friends = docs.map { doc ->
                            User(
                                uid = doc.id,
                                name = doc.getString("name") ?: "Okänd",
                                email = doc.getString("emailLower") ?: ""
                            )
                        }
                        _inviteFriendsEvent.postValue(Event(friends))
                    }
                    .addOnFailureListener { e ->
                        _toastEvent.postValue(Event(e.message ?: "Kunde inte hämta vänner"))
                    }
            }
            .addOnFailureListener { e ->
                _toastEvent.postValue(Event(e.message ?: "Kunde inte hämta din vänlista"))
            }
    }

    fun inviteFriend(chatRoomId: String, friendUid: String) {
        chatRepo.inviteFriendToRoom(
            chatRoomId = chatRoomId,
            friendUid = friendUid,
            onSuccess = { _toastEvent.postValue(Event("Vän tillagd i rummet")) },
            onError = { msg -> _toastEvent.postValue(Event(msg)) }
        )
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
