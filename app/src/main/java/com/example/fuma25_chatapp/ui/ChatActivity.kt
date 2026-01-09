package com.example.fuma25_chatapp.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.repository.ChatRepository
import com.example.fuma25_chatapp.viewmodel.ChatViewModel
import com.example.fuma25_chatapp.viewmodel.ChatViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ChatActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val repo: ChatRepository by lazy { ChatRepository() }

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(auth, db, repo)
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var inviteButton: ImageButton
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

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = chatRoomTitle
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerViewMessages)
        messageInput = findViewById(R.id.editTextMessage)
        sendButton = findViewById(R.id.buttonSend)
        inviteButton = findViewById(R.id.btnInviteFriend)

        val currentUserId = auth.currentUser?.uid.orEmpty()
        adapter = MessagesAdapter(currentUserId)

        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerView.adapter = adapter

        // Observers
        viewModel.messages.observe(this) { messages ->
            adapter.submitList(messages)
            if (messages.isNotEmpty()) recyclerView.scrollToPosition(messages.size - 1)
        }

        viewModel.toastEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { toast(it) }
        }

        viewModel.inviteFriendsEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { friends ->
                showInviteFriendsDialog(friends)
            }
        }

        sendButton.setOnClickListener {
            viewModel.sendMessage(chatRoomId, messageInput.text.toString())
            messageInput.text.clear()
        }

        inviteButton.setOnClickListener {
            viewModel.loadFriendsForInvite()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startListening(chatRoomId)
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopListening()
    }

    private fun showInviteFriendsDialog(friends: List<com.example.fuma25_chatapp.data.User>) {
        val names = friends.map { it.name.ifBlank { "Okänd" } }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Bjud in vän")
            .setItems(names) { _, index ->
                val selected = friends[index]
                viewModel.inviteFriend(chatRoomId, selected.uid)
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_CHAT_ROOM_ID = "extra_chat_room_id"
        const val EXTRA_CHAT_ROOM_TITLE = "extra_chat_room_title"
    }
}
