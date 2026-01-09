package com.example.fuma25_chatapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fuma25_chatapp.data.User
import com.example.fuma25_chatapp.repository.FriendsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class FriendsListViewModel(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val friendsRepo: FriendsRepository
) : ViewModel() {

    // ----- UI state -----

    private val _friends = MutableLiveData<List<User>>(emptyList())
    val friends: LiveData<List<User>> = _friends

    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    private val _incomingRequestEvent = MutableLiveData<Event<String>>()
    val incomingRequestEvent: LiveData<Event<String>> = _incomingRequestEvent

    // ----- Internal state -----

    private var userListener: ListenerRegistration? = null
    private var lastRequestsSignature: String = ""
    private var requestDialogOpen: Boolean = false

    // ----- Lifecycle -----

    fun start() {
        val myUid = auth.currentUser?.uid
        if (myUid.isNullOrBlank()) {
            _toastEvent.postValue(Event("Du är inte inloggad"))
            return
        }

        startListeningToUserDoc(myUid)
        loadFriendsOnce(myUid)
    }

    fun stop() {
        userListener?.remove()
        userListener = null
        requestDialogOpen = false
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    // ----- Firestore listeners -----

    private fun startListeningToUserDoc(myUid: String) {
        userListener?.remove()

        userListener = db.collection("users")
            .document(myUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _toastEvent.postValue(
                        Event(err.message ?: "Kunde inte lyssna på vänförfrågningar")
                    )
                    return@addSnapshotListener
                }

                if (snap == null || !snap.exists()) return@addSnapshotListener

                val requests =
                    (snap.get("incoming_requests") as? List<*>)?.filterIsInstance<String>().orEmpty()

                // Keep friends list fresh (after accept)
                loadFriendsOnce(myUid)

                val signature = requests.sorted().joinToString("|")

                if (
                    requests.isNotEmpty() &&
                    signature != lastRequestsSignature &&
                    !requestDialogOpen
                ) {
                    lastRequestsSignature = signature
                    requestDialogOpen = true
                    _incomingRequestEvent.postValue(Event(requests.first()))
                }

                if (requests.isEmpty()) {
                    lastRequestsSignature = ""
                }
            }
    }

    // ----- Friends list -----

    fun loadFriendsOnce(myUid: String = auth.currentUser?.uid.orEmpty()) {
        if (myUid.isBlank()) return

        db.collection("users")
            .document(myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val friendIds =
                    (snapshot.get("friends") as? List<*>)?.filterIsInstance<String>().orEmpty()

                if (friendIds.isEmpty()) {
                    _friends.postValue(emptyList())
                    return@addOnSuccessListener
                }

                // whereIn max 10
                val ids = friendIds.take(10)

                db.collection("user-public")
                    .whereIn(FieldPath.documentId(), ids)
                    .get()
                    .addOnSuccessListener { docs ->
                        val list = docs.map { doc ->
                            User(
                                uid = doc.id,
                                name = doc.getString("name") ?: "Okänd",
                                email = doc.getString("emailLower") ?: ""
                            )
                        }
                        _friends.postValue(list)
                    }
                    .addOnFailureListener { e ->
                        _toastEvent.postValue(
                            Event(e.message ?: "Kunde inte hämta vän-detaljer")
                        )
                    }
            }
            .addOnFailureListener { e ->
                _toastEvent.postValue(
                    Event(e.message ?: "Kunde inte hämta din vänlista")
                )
            }
    }

    // ----- Friend request actions -----

    fun acceptFriendRequest(fromUid: String) {
        viewModelScope.launch {
            try {
                friendsRepo.acceptFriendRequest(fromUid)
                _toastEvent.postValue(Event("Ni är nu vänner ✅"))
            } catch (e: Exception) {
                _toastEvent.postValue(Event(e.toString()))
            } finally {
                requestDialogOpen = false
            }
        }
    }

    fun rejectFriendRequest(fromUid: String) {
        viewModelScope.launch {
            try {
                friendsRepo.rejectFriendRequest(fromUid)
                _toastEvent.postValue(Event("Förfrågan nekad"))
            } catch (e: Exception) {
                _toastEvent.postValue(Event(e.toString()))
            } finally {
                requestDialogOpen = false
            }
        }
    }

    fun markRequestDialogClosed() {
        requestDialogOpen = false
    }
}
