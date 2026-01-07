package com.example.fuma25_chatapp.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChatActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val repository: ChatRepository by lazy { ChatRepository() }

    private var messagesListener: ListenerRegistration? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var adapter: MessagesAdapter

    private lateinit var chatRoomId: String
    private var chatRoomTitle: String = "Chat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatRoomId = intent.getStringExtra(EXTRA_CHAT_ROOM_ID) ?: ""
        chatRoomTitle = intent.getStringExtra(EXTRA_CHAT_ROOM_TITLE) ?: "Chat"

        if (chatRoomId.isBlank()) {
            toast("Missing chatRoomId")
            finish()
            return
        }

        title = chatRoomTitle

        recyclerView = findViewById(R.id.recyclerViewMessages)
        messageInput = findViewById(R.id.editTextMessage)
        sendButton = findViewById(R.id.buttonSend)

        // Här kopplar vi knappen som finns i din XML
        val btnInvite = findViewById<ImageButton>(R.id.btnInviteFriend)
        btnInvite.setOnClickListener {
            showInviteFriendDialog()
        }

        val currentUserId = auth.currentUser?.uid.orEmpty()
        if (currentUserId.isBlank()) {
            toast("Not signed in")
            finish()
            return
        }

        adapter = MessagesAdapter(currentUserId)

        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = adapter

        sendButton.setOnClickListener { sendMessage() }
    }

    override fun onStart() {
        super.onStart()
        messagesListener?.remove()
        messagesListener = repository.listenToMessages(
            chatRoomId = chatRoomId,
            onUpdate = { messages ->
                adapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    recyclerView.post {
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            },
            onError = { msg ->
                toast("Messages error: $msg")
            }
        )
    }

    override fun onStop() {
        super.onStop()
        messagesListener?.remove()
        messagesListener = null
    }

    private fun sendMessage() {
        val userId = auth.currentUser?.uid.orEmpty()
        val text = messageInput.text?.toString()?.trim().orEmpty()

        if (userId.isBlank()) {
            toast("Not signed in")
            return
        }
        if (text.isBlank()) return

        sendButton.isEnabled = false

        repository.sendMessage(
            chatRoomId = chatRoomId,
            senderId = userId,
            text = text,
            onSuccess = {
                sendButton.isEnabled = true
                messageInput.text?.clear()
            },
            onError = { msg ->
                sendButton.isEnabled = true
                toast("Send error: $msg")
            }
        )
    }

    // Funktion för att visa dialogen med vänner
    private fun showInviteFriendDialog() {
        val myUid = auth.currentUser?.uid ?: return

        FirebaseFirestore.getInstance().collection("users").document(myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                // Vi hämtar listan på UID:n från ditt User-objekt i Firestore
                val friendIds = (snapshot.get("friends") as? List<*>)?.filterIsInstance<String>().orEmpty()

                if (friendIds.isEmpty()) {
                    toast("Du har inga vänner än. Lägg till vänner i huvudmenyn!")
                    return@addOnSuccessListener
                }

                // Vi hämtar namnen på de personerna så vi kan visa dem i en lista
                FirebaseFirestore.getInstance().collection("users")
                    .whereIn("uid", friendIds)
                    .get()
                    .addOnSuccessListener { friendDocs ->
                        val friendNames = friendDocs.map { it.getString("name") ?: "Okänd" }.toTypedArray()
                        val friendUids = friendDocs.map { it.id }

                        AlertDialog.Builder(this)
                            .setTitle("Bjud in till chatten")
                            .setItems(friendNames) { _, which ->
                                val selectedFriendUid = friendUids[which]
                                inviteFriendToRoom(selectedFriendUid)
                            }
                            .setNegativeButton("Avbryt", null)
                            .show()
                    }
            }
    }

    private fun inviteFriendToRoom(friendUid: String) {
        repository.inviteFriendToRoom(
            chatRoomId = chatRoomId,
            friendUid = friendUid,
            onSuccess = { toast("Vän tillagd i rummet!") },
            onError = { msg -> toast("Fel: $msg") }
        )
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_CHAT_ROOM_ID = "extra_chat_room_id"
        const val EXTRA_CHAT_ROOM_TITLE = "extra_chat_room_title"
    }
}