package com.example.fuma25_chatapp.data

import com.google.firebase.Timestamp

data class ChatRoom(
    val id: String = "",
    val title: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)
