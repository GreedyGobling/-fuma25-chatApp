package com.example.fuma25_chatapp.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
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
    private var lastRoomsCount: Int = 0

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatRoomsAdapter
    private lateinit var fabCreateRoom: FloatingActionButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            goToLoginClearBackstack()
            return
        }

        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        fabCreateRoom = findViewById(R.id.fabCreateRoom)
        val btnAddFriend = findViewById<Button>(R.id.btnAddFriend)
        btnAddFriend.setOnClickListener {
            showAddFriendDialog()
        }

        setSupportActionBar(toolbar)

        val userId = auth.currentUser?.uid.orEmpty()

        adapter = ChatRoomsAdapter(
            currentUserId = userId,
            onClick = { room -> openChatRoom(room) },
            onDeleteRequested = { room -> showDeleteRoomDialog(room) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

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
                lastRoomsCount = rooms.size
                adapter.submitList(rooms)
            },
            onError = { msg ->
                // Only show the error if we have nothing to display
                if (lastRoomsCount == 0) {
                    toast("Error loading rooms: $msg")
                }
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

    private fun showDeleteRoomDialog(room: ChatRoom) {
        AlertDialog.Builder(this)
            .setTitle("Radera chattrum")
            .setMessage("Vill du radera \"${room.title}\"?\nAlla meddelanden i rummet raderas också.")
            .setNegativeButton("Avbryt", null)
            .setPositiveButton("Radera") { _, _ ->
                deleteRoom(room)
            }
            .show()
    }

    private fun deleteRoom(room: ChatRoom) {
        fabCreateRoom.isEnabled = false

        repository.deleteChatRoom(
            chatRoomId = room.id,
            onSuccess = {
                fabCreateRoom.isEnabled = true
                toast("Chattrum raderat")
            },
            onError = { msg ->
                fabCreateRoom.isEnabled = true
                toast("Kunde inte radera: $msg")
            }
        )
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

    private fun showCreateRoomDialog() {
        val userId = auth.currentUser?.uid ?: return

        val input = EditText(this).apply {
            hint = "Ex: Klasschatten"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        AlertDialog.Builder(this)
            .setTitle("Skapa chattrum")
            .setMessage("Skriv ett namn på chattrummet")
            .setView(input)
            .setNegativeButton("Avbryt", null)
            .setPositiveButton("Skapa") { _, _ ->
                val title = input.text?.toString()?.trim().orEmpty()
                val validatedTitle = validateRoomTitleOrNull(title)
                if (validatedTitle == null) {
                    toast("Skriv ett namn (minst 2 tecken).")
                    return@setPositiveButton
                }
                createRoom(userId, validatedTitle)
            }
            .show()
    }

    private fun validateRoomTitleOrNull(title: String): String? {
        val t = title.trim()
        if (t.length < 2) return null
        if (t.length > 40) return t.take(40)
        return t
    }

    private fun createRoom(userId: String, title: String) {
        // Prevent double taps
        fabCreateRoom.isEnabled = false

        repository.createChatRoom(
            title = title,
            creatorUserId = userId,
            onSuccess = { roomId ->
                fabCreateRoom.isEnabled = true
                toast("Chattrum skapat")

                // Open the room that was created
                openChatRoom(
                    ChatRoom(
                        id = roomId,
                        title = title,
                        createdBy = userId,
                        members = listOf(userId)
                    )
                )
            },
            onError = { msg ->
                fabCreateRoom.isEnabled = true
                toast("Kunde inte skapa rum: $msg")
            }
        )
    }
// function for showing dialog
    private fun showAddFriendDialog() {
        val input = EditText(this)
        input.hint = "Vännens e-post"

        AlertDialog.Builder(this)
            .setTitle("Lägg till vän")
            .setView(input)
            .setPositiveButton("Lägg till") { _, _ ->
                val email = input.text.toString().trim()
                val myUid = auth.currentUser?.uid ?: return@setPositiveButton

                repository.addFriendByEmail(myUid, email,
                    onSuccess = { toast("Vän tillagd!") },
                    onError = { msg -> toast(msg) }
                )
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private companion object {
        const val MENU_LOGOUT = 1001
    }
}
