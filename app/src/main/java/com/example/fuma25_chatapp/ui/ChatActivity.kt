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

        chatRoomId = intent.getStringExtra(EXTRA_CHAT_ROOM_ID) ?: ""
        chatRoomTitle = intent.getStringExtra(EXTRA_CHAT_ROOM_TITLE) ?: "Chat"

        if (chatRoomId.isBlank()) {
            Toast.makeText(this, "Missing chatRoomId", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        title = chatRoomTitle

        recyclerView = findViewById(R.id.recyclerViewMessages)
        messageInput = findViewById(R.id.editTextMessage)
        sendButton = findViewById(R.id.buttonSend)

        val currentUserId = auth.currentUser?.uid ?: ""
        adapter = MessagesAdapter(currentUserId)

        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Keeps the newest messages at the bottom
        }
        recyclerView.adapter = adapter

        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    override fun onStart() {
        super.onStart()

        // Start listening to messages in real time
        messagesListener?.remove()
        messagesListener = chatRepository.listenToMessages(chatRoomId) { messages ->
            adapter.submitList(messages)

            if (messages.isNotEmpty()) {
                recyclerView.post {
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Always remove listeners to avoid memory leaks
        messagesListener?.remove()
        messagesListener = null
    }

    private fun sendMessage() {
        val userId = auth.currentUser?.uid ?: return
        val text = messageInput.text.toString().trim()

        if (text.isBlank()) return

        chatRepository.sendMessage(
            chatRoomId = chatRoomId,
            senderId = userId,
            text = text
        )

        messageInput.text.clear()
    }

    companion object {
        const val EXTRA_CHAT_ROOM_ID = "extra_chat_room_id"
        const val EXTRA_CHAT_ROOM_TITLE = "extra_chat_room_title"
    }
}