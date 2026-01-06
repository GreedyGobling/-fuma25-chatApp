package com.example.fuma25_chatapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.model.Message

class MessagesAdapter(
    private val currentUserId: String
) : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<Message>()

    fun submitList(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)

        fun bind(message: Message) {
            textMessage.text = message.text
        }
    }
}
