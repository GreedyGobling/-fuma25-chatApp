package com.example.fuma25_chatapp.model

data class ChatRoom(
    val id: String = "",
    val members: List<String> = emptyList(),
    val title: String = ""
)
