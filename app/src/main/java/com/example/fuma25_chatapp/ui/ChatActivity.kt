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
import com.google.firebase.firestore.FieldPath
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
    private var chatRoomTitle: String = "Chatt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatRoomId = intent.getStringExtra(EXTRA_CHAT_ROOM_ID) ?: ""
        chatRoomTitle = intent.getStringExtra(EXTRA_CHAT_ROOM_TITLE) ?: "Chatt"

        if (chatRoomId.isBlank()) {
            toast("Saknar chattrums-ID")
            finish()
            return
        }

        title = chatRoomTitle

        recyclerView = findViewById(R.id.recyclerViewMessages)
        messageInput = findViewById(R.id.editTextMessage)
        sendButton = findViewById(R.id.buttonSend)

        findViewById<ImageButton>(R.id.btnInviteFriend).setOnClickListener {
            showInviteFriendDialog()
        }

        val currentUserId = auth.currentUser?.uid.orEmpty()
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
            chatRoomId,
            onUpdate = { messages ->
                adapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            },
            onError = { toast(it) }
        )
    }

    override fun onStop() {
        super.onStop()
        messagesListener?.remove()
    }

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return

        val user = auth.currentUser ?: return

        val currentUserName = when {
            !user.displayName.isNullOrBlank() -> user.displayName
            !user.email.isNullOrBlank() -> user.email!!.substringBefore("@")
            else -> "Användare"
        }

        val message = com.example.fuma25_chatapp.data.Message(
            text = text,
            senderId = user.uid,
            senderName = currentUserName!!,
            createdAt = com.google.firebase.Timestamp.now()
        )

        repository.sendMessage(chatRoomId, message,
            onSuccess = { messageInput.text.clear() },
            onError = { toast(it) }
        )
    }

    private fun showInviteFriendDialog() {
        val myUid = auth.currentUser?.uid ?: return

        FirebaseFirestore.getInstance().collection("users").document(myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val friendIds =
                    (snapshot.get("friends") as? List<*>)?.filterIsInstance<String>().orEmpty()

                if (friendIds.isEmpty()) {
                    toast("Du har inga vänner ännu")
                    return@addOnSuccessListener
                }

                FirebaseFirestore.getInstance()
                    .collection("user-public")
                    .whereIn(FieldPath.documentId(), friendIds.take(10))
                    .get()
                    .addOnSuccessListener { docs ->
                        val names = docs.map { it.getString("name") ?: "Okänd" }.toTypedArray()
                        val uids = docs.map { it.id }

                        AlertDialog.Builder(this)
                            .setTitle("Bjud in vän")
                            .setItems(names) { _, index ->
                                inviteFriend(uids[index])
                            }
                            .setNegativeButton("Avbryt", null)
                            .show()
                    }
            }
    }

    private fun inviteFriend(friendUid: String) {
        repository.inviteFriendToRoom(
            chatRoomId,
            friendUid,
            onSuccess = { toast("Vän tillagd i rummet") },
            onError = { toast(it) }
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
