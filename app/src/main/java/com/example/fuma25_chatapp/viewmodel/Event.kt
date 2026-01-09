package com.example.fuma25_chatapp.viewmodel

/**
 * One-time event wrapper for LiveData (e.g., Toasts, navigation, dialogs).
 */
class Event<out T>(private val content: T) {
    private var hasBeenHandled = false

    fun getContentIfNotHandled(): T? {
        if (hasBeenHandled) return null
        hasBeenHandled = true
        return content
    }

    fun peekContent(): T = content
}
