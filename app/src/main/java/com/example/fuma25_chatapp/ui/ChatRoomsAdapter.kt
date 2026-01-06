package com.example.fuma25_chatapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.model.ChatRoom

class ChatRoomsAdapter(
    private val onClick: (ChatRoom) -> Unit
) : RecyclerView.Adapter<ChatRoomsAdapter.ChatRoomViewHolder>() {

    private val items = mutableListOf<ChatRoom>()

    fun submitList(newItems: List<ChatRoom>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_room, parent, false)
        return ChatRoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class ChatRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val titleText: TextView = itemView.findViewById(R.id.textTitle)
        private val membersText: TextView = itemView.findViewById(R.id.textMembers)

        fun bind(room: ChatRoom, onClick: (ChatRoom) -> Unit) {
            titleText.text = room.title.ifBlank { "Untitled chat" }
            membersText.text = "Members: ${room.members.size}"

            itemView.setOnClickListener { onClick(room) }
        }
    }
}
