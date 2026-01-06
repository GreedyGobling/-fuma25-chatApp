package com.example.fuma25_chatapp.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ChatActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val chatRepository: ChatRepository by lazy { ChatRepository() }

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

        // Guard: must be signed in
        if (auth.currentUser == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        chatRoomId = intent.getStringExtra(EXTRA_CHAT_ROOM_ID).orEmpty()
        chatRoomTitle = intent.getStringExtra(EXTRA_CHAT_ROOM_TITLE) ?: "Chat"

        // Guard: must have room id
        if (chatRoomId.isBlank()) {
            Toast.makeText(this, "Missing chatRoomId", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        title = chatRoomTitle

        recyclerView = findViewById(R.id.recyclerViewMessages)
        messageInput = findViewById(R.id.editTextMessage)
        sendButton = findViewById(R.id.buttonSend)

        val currentUserId = auth.currentUser?.uid.orEmpty()
        adapter = MessagesAdapter(currentUserId)

        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // newest messages at the bottom
        }
        recyclerView.adapter = adapter

        sendButton.setOnClickListener { sendMessage() }
    }

    override fun onStart() {
        super.onStart()

        // If user got signed out while app was backgrounded
        if (auth.currentUser == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        startMessagesListener()
    }

    override fun onStop() {
        super.onStop()
        messagesListener?.remove()
        messagesListener = null
    }

    private fun startMessagesListener() {
        // Avoid double listeners
        if (messagesListener != null) return

        messagesListener = chatRepository.listenToMessages(chatRoomId) { messages ->
            adapter.submitList(messages)

            if (messages.isNotEmpty()) {
                recyclerView.post {
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun sendMessage() {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val text = messageInput.text.toString().trim()
        if (text.isBlank()) return

        sendButton.isEnabled = false

        chatRepository.sendMessage(
            chatRoomId = chatRoomId,
            senderId = userId,
            text = text,
            onSuccess = {
                messageInput.text.clear()
                sendButton.isEnabled = true
            },
            onError = { e ->
                sendButton.isEnabled = true
                Toast.makeText(this, "Could not send: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    companion object {
        const val EXTRA_CHAT_ROOM_ID = "extra_chat_room_id"
        const val EXTRA_CHAT_ROOM_TITLE = "extra_chat_room_title"
    }
}