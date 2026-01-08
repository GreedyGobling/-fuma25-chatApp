package com.example.fuma25_chatapp.data

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val createdAt: Timestamp? = null,
    val senderName: String = "",
)
