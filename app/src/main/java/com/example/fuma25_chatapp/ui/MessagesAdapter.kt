package com.example.fuma25_chatapp.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.R
import com.example.fuma25_chatapp.data.Message

class MessagesAdapter(
    private val currentUserId: String
) : ListAdapter<Message, MessagesAdapter.MessageViewHolder>(Diff) {

    private object Diff : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        return if (msg.senderId == currentUserId) VIEW_TYPE_ME else VIEW_TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_ME) {
            R.layout.item_message_me
        } else {
            R.layout.item_message_other
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        val isMe = message.senderId == currentUserId
        holder.bind(message, isMe)
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val textSenderName: TextView? = itemView.findViewById(R.id.textSenderName)

        fun bind(message: Message, isMe: Boolean) {
            textMessage.text = message.text
            textSenderName?.text = message.senderName

            val density = itemView.resources.displayMetrics.density
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(Color.parseColor("#1E1E24")) // Bakgrundsfärg på bubblan
                setStroke((2 * density).toInt(), getUserColor(message.senderId)) // 2dp ram
            }

            textMessage.background = drawable
        }

        private fun getUserColor(userId: String): Int {
            val hash = userId.hashCode()
            val hue = Math.abs(hash % 360).toFloat()
            return Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.9f))
        }
    }

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_OTHER = 2
    }
}