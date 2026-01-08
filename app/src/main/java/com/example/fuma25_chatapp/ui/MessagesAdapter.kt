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

    // DiffUtil makes list updates smooth and avoids full refresh
    private object Diff : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            // Prefer stable Firestore document id
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
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
        holder.bind(getItem(position))
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val textSenderName: TextView? = itemView.findViewById(R.id.textSenderName)

        fun bind(message: Message) {
            textMessage.text = message.text


            textSenderName?.text = message.senderName

            if (viewType == VIEW_TYPE_ME) {
                textMessage.setBackgroundResource(R.drawable.bubble_me)
            } else {
                textMessage.setBackgroundResource(R.drawable.bubble_other)

            // Create bubble with color outline based on user ID
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * itemView.resources.displayMetrics.density // 18dp
                setColor(0xFF1E1E24.toInt()) // c_surface color
                setStroke(
                    (1 * itemView.resources.displayMetrics.density).toInt(), // 1dp stroke
                    getUserColor(message.senderId)
                )

            }
            textMessage.background = drawable
        }

        private fun getUserColor(userId: String): Int {

            val hash = userId.hashCode()
            val hue = (hash and 0xFFFF) % 360f

            val saturation = 0.7f + ((hash shr 16) and 0xFF) / 255f * 0.3f // 0.7-1.0
            val value = 0.8f + ((hash shr 8) and 0xFF) / 255f * 0.2f // 0.8-1.0

            return Color.HSVToColor(floatArrayOf(hue, saturation, value))
        }
    }

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_OTHER = 2
    }
}
