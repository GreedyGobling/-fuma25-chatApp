package com.example.fuma25_chatapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.model.ChatRoom
import com.example.fuma25_chatapp.repository.ChatRepository
import com.example.fuma25_chatapp.ui.ChatRoomsAdapter
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
        // We use setClassName so this compiles even if ChatActivity is not created yet
        val intent = Intent().apply {
            setClassName(packageName, "$packageName.ChatActivity")
            putExtra(EXTRA_CHAT_ROOM_ID, room.id)
            putExtra(EXTRA_CHAT_ROOM_TITLE, room.title)
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "ChatActivity not found yet. Create ChatActivity to open chats.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_CHAT_ROOM_ID = "extra_chat_room_id"
        const val EXTRA_CHAT_ROOM_TITLE = "extra_chat_room_title"
    }
}
