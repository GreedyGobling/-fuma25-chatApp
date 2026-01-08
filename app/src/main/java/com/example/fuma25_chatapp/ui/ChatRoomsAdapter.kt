package com.example.fuma25_chatapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.data.ChatRoom

class ChatRoomsAdapter(
    private val currentUserId: String,
    private val onClick: (ChatRoom) -> Unit,
    private val onDeleteRequested: (ChatRoom) -> Unit
) : ListAdapter<ChatRoom, ChatRoomsAdapter.ChatRoomViewHolder>(Diff) {

    // DiffUtil makes room list updates smooth and avoids full refresh
    private object Diff : DiffUtil.ItemCallback<ChatRoom>() {
        override fun areItemsTheSame(oldItem: ChatRoom, newItem: ChatRoom): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatRoom, newItem: ChatRoom): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_room, parent, false)
        return ChatRoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        holder.bind(
            room = getItem(position),
            currentUserId = currentUserId,
            onClick = onClick,
            onDeleteRequested = onDeleteRequested
        )
    }

    class ChatRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val titleText: TextView = itemView.findViewById(R.id.textTitle)
        private val membersText: TextView = itemView.findViewById(R.id.textMembers)
        private val youBadge: TextView = itemView.findViewById(R.id.textYouBadge)
        private val deleteHint: TextView = itemView.findViewById(R.id.textDeleteHint)

        fun bind(
            room: ChatRoom,
            currentUserId: String,
            onClick: (ChatRoom) -> Unit,
            onDeleteRequested: (ChatRoom) -> Unit
        ) {
            // Show Swedish UI text; only comments are in English.
            titleText.text = room.title.ifBlank { itemView.context.getString(R.string.untitled_chat) }
            membersText.text = itemView.context.getString(R.string.members_format, room.members.size)

            // Only the creator can delete the room
            val canDelete = room.createdBy == currentUserId && currentUserId.isNotBlank()

            youBadge.visibility = if (canDelete) View.VISIBLE else View.GONE
            deleteHint.visibility = if (canDelete) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onClick(room) }

            // Attach long-press only if deletion is allowed
            itemView.setOnLongClickListener(
                if (canDelete) {
                    View.OnLongClickListener {
                        onDeleteRequested(room)
                        true
                    }
                } else {
                    null
                }
            )
        }
    }
}
