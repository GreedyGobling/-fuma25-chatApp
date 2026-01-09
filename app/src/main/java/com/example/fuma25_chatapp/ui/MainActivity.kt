package com.example.fuma25_chatapp.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.data.ChatRoom
import com.example.fuma25_chatapp.repository.ChatRepository
import com.example.fuma25_chatapp.repository.FriendsRepository
import com.example.fuma25_chatapp.viewmodel.MainViewModel
import com.example.fuma25_chatapp.viewmodel.MainViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val chatRepo: ChatRepository by lazy { ChatRepository() }
    private val friendsRepo: FriendsRepository by lazy { FriendsRepository(auth, db) }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(db, chatRepo)
    }

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

        setSupportActionBar(toolbar)

        val userId = auth.currentUser?.uid.orEmpty()

        adapter = ChatRoomsAdapter(
            currentUserId = userId,
            onClick = { room -> openChatRoom(room) },
            onDeleteRequested = { room -> showDeleteRoomDialog(room) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnAddFriend).setOnClickListener { showAddFriendDialog() }
        findViewById<Button>(R.id.btnFriendsList).setOnClickListener {
            startActivity(Intent(this, FriendsListActivity::class.java))
        }
        fabCreateRoom.setOnClickListener { showCreateRoomDialog() }

        // Observers
        viewModel.rooms.observe(this) { rooms ->
            adapter.submitList(rooms)
        }

        viewModel.toastEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { toast(it) }
        }

        viewModel.friendRequestsCountEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { count ->
                toast("Du har $count vänförfrågan/förfrågningar! 👥")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val userId = auth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            goToLoginClearBackstack()
            return
        }
        viewModel.start(userId)
    }

    override fun onStop() {
        super.onStop()
        viewModel.stop()
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
                fabCreateRoom.isEnabled = false
                viewModel.deleteRoom(room.id)
                fabCreateRoom.isEnabled = true
            }
            .show()
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

                fabCreateRoom.isEnabled = false
                viewModel.createRoom(userId, validatedTitle) { roomId ->
                    fabCreateRoom.isEnabled = true
                    openChatRoom(
                        ChatRoom(
                            id = roomId,
                            title = validatedTitle,
                            createdBy = userId,
                            members = listOf(userId)
                        )
                    )
                }
            }
            .show()
    }

    private fun validateRoomTitleOrNull(title: String): String? {
        val t = title.trim()
        if (t.length < 2) return null
        if (t.length > 40) return t.take(40)
        return t
    }

    /**
     * Friend request by email:
     * - lookup user-public by emailLower
     * - get uid (doc id)
     * - send request via FriendsRepository (still OK to keep here)
     *
     * (Bonus: can be moved into a ViewModel too, but this is already thin UI logic)
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
