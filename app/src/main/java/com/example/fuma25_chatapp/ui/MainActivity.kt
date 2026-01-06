package com.example.fuma25_chatapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
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
    private val repository: ChatRepository by lazy { ChatRepository() }

    private var roomsListener: ListenerRegistration? = null

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatRoomsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect if not authenticated
        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)

        setSupportActionBar(toolbar)

        adapter = ChatRoomsAdapter { room ->
            openChatRoom(room)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()

        // If the user got signed out while the app was in background
        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        startListeningToRooms()
    }

    override fun onStop() {
        super.onStop()
        // Remove Firestore listener to avoid memory leaks
        roomsListener?.remove()
        roomsListener = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_LOGOUT, 0, "Logout")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_LOGOUT -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startListeningToRooms() {
        if (roomsListener != null) return

        val userId = auth.currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            goToLogin()
            return
        }

        roomsListener = repository.listenToChatRooms(
            userId = userId,
            onUpdate = { rooms: List<ChatRoom> ->
                adapter.submitList(rooms)
            }
        )
    }

    private fun openChatRoom(room: ChatRoom) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_CHAT_ROOM_ID, room.id)
            putExtra(ChatActivity.EXTRA_CHAT_ROOM_TITLE, room.title)
        }
        startActivity(intent)
    }

    private fun logout() {
        auth.signOut()
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private companion object {
        const val MENU_LOGOUT = 1001
    }
}
