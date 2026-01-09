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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.data.ChatRoom
import com.example.fuma25_chatapp.repository.ChatRepository
import com.example.fuma25_chatapp.repository.FriendsRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val repository: ChatRepository by lazy { ChatRepository() }
    private val friendsRepo: FriendsRepository by lazy { FriendsRepository(auth, db) }

    private var roomsListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null

    private var lastRoomsCount: Int = 0
    private var lastRequestsCount: Int = 0

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

        // Send FRIEND REQUEST (not auto-add)
        findViewById<Button>(R.id.btnAddFriend).setOnClickListener {
            showAddFriendDialog()
        }

        // Open friends list activity
        findViewById<Button>(R.id.btnFriendsList).setOnClickListener {
            startActivity(Intent(this, FriendsListActivity::class.java))
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
        startListeningToFriendRequests(userId)
    }

    override fun onStop() {
        super.onStop()
        roomsListener?.remove()
        roomsListener = null

        requestsListener?.remove()
        requestsListener = null
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
                if (lastRoomsCount == 0) {
                    toast("Error loading rooms: $msg")
                }
            }
        )
    }

    /**
     * Listen for incoming friend requests and notify the user.
     * This is NOT a push notification; it updates while the app is open.
     */
    private fun startListeningToFriendRequests(userId: String) {
        requestsListener?.remove()
        requestsListener = db.collection("users").document(userId)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                if (snap == null || !snap.exists()) return@addSnapshotListener

                val requests =
                    (snap.get("incoming_requests") as? List<*>)?.filterIsInstance<String>().orEmpty()

                val count = requests.size

                // Only show a toast when it increases (avoid spam)
                if (count > 0 && count > lastRequestsCount) {
                    toast("Du har $count vänförfrågan/förfrågningar! 👥")
                }

                lastRequestsCount = count
            }
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
        val intent = Intent(this@MainActivity, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
        fabCreateRoom.isEnabled = false

        repository.createChatRoom(
            title = title,
            creatorUserId = userId,
            onSuccess = { roomId ->
                fabCreateRoom.isEnabled = true
                toast("Chattrum skapat")

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

    /**
     * Friend request by email:
     * - lookup user-public by emailLower
     * - get uid (doc id)
     * - send request (incoming_requests) via FriendsRepository
     */
    private fun showAddFriendDialog() {
        val input = EditText(this).apply {
            hint = "Vännens e-post"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(this)
            .setTitle("Skicka vänförfrågan")
            .setView(input)
            .setPositiveButton("Skicka") { _, _ ->
                val emailLower = input.text?.toString()?.trim().orEmpty().lowercase()
                if (emailLower.isBlank()) {
                    toast("Skriv en e-post")
                    return@setPositiveButton
                }
                sendFriendRequestByEmail(emailLower)
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun sendFriendRequestByEmail(emailLower: String) {
        val myUid = auth.currentUser?.uid ?: return

        db.collection("user-public")
            .whereEqualTo("emailLower", emailLower)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    toast("Ingen användare hittades med den e-posten")
                    return@addOnSuccessListener
                }

                val targetUid = snap.documents.first().id

                if (targetUid == myUid) {
                    toast("Du kan inte lägga till dig själv 😄")
                    return@addOnSuccessListener
                }

                lifecycleScope.launch {
                    try {
                        friendsRepo.sendFriendRequest(targetUid)
                        toast("Vänförfrågan skickad 📩")
                    } catch (e: Exception) {
                        toast(e.toString())
                    }
                }
            }
            .addOnFailureListener { e ->
                toast(e.toString())
            }
    }

    private companion object {
        const val MENU_LOGOUT = 1001
    }
}
