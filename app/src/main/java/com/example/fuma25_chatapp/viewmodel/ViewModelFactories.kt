package com.example.fuma25_chatapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.fuma25_chatapp.repository.ChatRepository
import com.example.fuma25_chatapp.repository.FriendsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainViewModelFactory(
    private val db: FirebaseFirestore,
    private val chatRepo: ChatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(db, chatRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatViewModelFactory(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val chatRepo: ChatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(auth, db, chatRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class FriendsListViewModelFactory(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val friendsRepo: FriendsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FriendsListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FriendsListViewModel(auth, db, friendsRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
