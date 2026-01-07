package com.example.fuma25_chatapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.data.ChatRoom
import com.example.fuma25_chatapp.repository.ChatRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val repository: ChatRepository by lazy { ChatRepository() }

    private var roomsListener: ListenerRegistration? = null

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatRoomsAdapter
    private lateinit var fabCreateRoom: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If user is not logged in, go directly to login screen
        if (auth.currentUser == null) {
            goToLoginClearBackstack()
            return
        }

        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        fabCreateRoom = findViewById(R.id.fabCreateRoom)

        setSupportActionBar(toolbar)

        adapter = ChatRoomsAdapter { room ->
            openChatRoom(room)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // FAB creates a new chat room
        fabCreateRoom.setOnClickListener {
            showCreateRoomDialog()
        }
    }

    override fun onStart() {
        super.onStart()

        val userId = auth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            goToLoginClearBackstack()
            return
        }

        // Start listening for chat rooms where user is a member
        startListeningToRooms(userId)
    }

    override fun onStop() {
        super.onStop()
        roomsListener?.remove()
        roomsListener = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_LOGOUT, 0, "Logga ut")
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

    private fun startListeningToRooms(userId: String) {
        roomsListener?.remove()

        roomsListener = repository.listenToChatRooms(
            userId = userId,
            onUpdate = { rooms ->
                adapter.submitList(rooms)
            },
            onError = { msg ->
                toast("Error loading rooms: $msg")
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
        toast("Utloggad")
        goToLoginClearBackstack()
    }

    private fun goToLoginClearBackstack() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // Shows dialog where user enters chat room title
    private fun showCreateRoomDialog() {
        val userId = auth.currentUser?.uid ?: return

        val input = EditText(this).apply {
            hint = "Ex: Klasschatten"
        }

        AlertDialog.Builder(this)
            .setTitle("Skapa chattrum")
            .setMessage("Skriv ett namn på chattrummet")
            .setView(input)
            .setNegativeButton("Avbryt", null)
            .setPositiveButton("Skapa") { _, _ ->
                val title = input.text.toString().trim()
                createRoom(userId, title)
            }
            .show()
    }

    // Creates chat room in Firestore and opens it
    private fun createRoom(userId: String, title: String) {
        repository.createChatRoom(
            title = title,
            creatorUserId = userId,
            onSuccess = { roomId ->
                toast("Chattrum skapat")
                openChatRoom(
                    ChatRoom(
                        id = roomId,
                        title = title,
                        members = listOf(userId)
                    )
                )
            },
            onError = { msg ->
                toast("Kunde inte skapa rum: $msg")
            }
        )
    }

    private companion object {
        const val MENU_LOGOUT = 1001
    }
}
