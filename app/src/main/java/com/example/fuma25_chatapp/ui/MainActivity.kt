package com.example.fuma25_chatapp.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.data.ChatRoom
import com.example.fuma25_chatapp.repository.ChatRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val chatRepository: ChatRepository by lazy { ChatRepository() }

    private var roomsListener: ListenerRegistration? = null

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatRoomsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ChatRoomsAdapter { room ->
            openChatRoom(room)
        }
        recyclerView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Not signed in -> go to login screen
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Start listening to chat rooms in real time
        roomsListener?.remove()
        roomsListener = chatRepository.listenToChatRooms(currentUser.uid) { rooms ->
            Log.d(TAG, "Chat rooms updated: ${rooms.size}")
            adapter.submitList(rooms)
        }
    }

    override fun onStop() {
        super.onStop()
        // Always remove listeners to avoid memory leaks
        roomsListener?.remove()
        roomsListener = null
    }

    private fun openChatRoom(room: ChatRoom) {
        // Open ChatActivity and pass the selected chat room data
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_CHAT_ROOM_ID, room.id)
            putExtra(ChatActivity.EXTRA_CHAT_ROOM_TITLE, room.title)
        }
        startActivity(intent)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}