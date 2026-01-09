package com.example.fuma25_chatapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.fuma25_chatapp.data.ChatRoom
import com.example.fuma25_chatapp.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MainViewModel(
    private val db: FirebaseFirestore,
    private val chatRepo: ChatRepository
) : ViewModel() {

    private val _rooms = MutableLiveData<List<ChatRoom>>(emptyList())
    val rooms: LiveData<List<ChatRoom>> = _rooms

    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    private val _friendRequestsCountEvent = MutableLiveData<Event<Int>>()
    val friendRequestsCountEvent: LiveData<Event<Int>> = _friendRequestsCountEvent

    private var roomsListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null

    private var lastRoomsCount: Int = 0
    private var lastRequestsCount: Int = 0

    fun start(userId: String) {
        startRoomsListener(userId)
        startFriendRequestsListener(userId)
    }

    fun stop() {
        roomsListener?.remove()
        roomsListener = null

        requestsListener?.remove()
        requestsListener = null
    }

    private fun startRoomsListener(userId: String) {
        roomsListener?.remove()
        roomsListener = chatRepo.listenToChatRooms(
            userId = userId,
            onUpdate = { list ->
                lastRoomsCount = list.size
                _rooms.postValue(list)
            },
            onError = { msg ->
                if (lastRoomsCount == 0) {
                    _toastEvent.postValue(Event("Error loading rooms: $msg"))
                }
            }
        )
    }

    private fun startFriendRequestsListener(userId: String) {
        requestsListener?.remove()
        requestsListener = db.collection("users").document(userId)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                if (snap == null || !snap.exists()) return@addSnapshotListener

                val requests =
                    (snap.get("incoming_requests") as? List<*>)?.filterIsInstance<String>().orEmpty()

                val count = requests.size

                // Only notify when count increases (avoid spam)
                if (count > 0 && count > lastRequestsCount) {
                    _friendRequestsCountEvent.postValue(Event(count))
                }

                lastRequestsCount = count
            }
    }

    fun createRoom(
        userId: String,
        title: String,
        onCreated: (roomId: String) -> Unit
    ) {
        chatRepo.createChatRoom(
            title = title,
            creatorUserId = userId,
            onSuccess = { roomId ->
                _toastEvent.postValue(Event("Chattrum skapat"))
                onCreated(roomId)
            },
            onError = { msg ->
                _toastEvent.postValue(Event("Kunde inte skapa rum: $msg"))
            }
        )
    }

    fun deleteRoom(chatRoomId: String) {
        chatRepo.deleteChatRoom(
            chatRoomId = chatRoomId,
            onSuccess = {
                _toastEvent.postValue(Event("Chattrum raderat"))
            },
            onError = { msg ->
                _toastEvent.postValue(Event("Kunde inte radera: $msg"))
            }
        )
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
