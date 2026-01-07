package com.example.fuma25_chatapp.data

data class User(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val friends: List<String> = emptyList()
)
