package com.example.fuma25_chatapp

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.repository.ChatRepository
import com.example.fuma25_chatapp.ui.MessagesAdapter
import com.google.firebase.auth.FirebaseAuth

class ChatActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val chatRepository: ChatRepository by lazy { ChatRepository() }

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton

    private lateinit var adapter: MessagesAdapter

    // Temporary hardcoded chat room (we will improve this later)
    private val chatRoomId = "global_chat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.recyclerViewMessages)
        messageInput = findViewById(R.id.editTextMessage)
        sendButton = findViewById(R.id.buttonSend)

        adapter = MessagesAdapter(auth.currentUser?.uid ?: "")

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        listenForMessages()

        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun listenForMessages() {
        chatRepository.listenToMessages(chatRoomId) { messages ->
            adapter.submitList(messages)
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        val userId = auth.currentUser?.uid ?: return

        if (text.isEmpty()) return

        chatRepository.sendMessage(
            chatRoomId = chatRoomId,
            senderId = userId,
            text = text
        )

        messageInput.text.clear()
    }
}
