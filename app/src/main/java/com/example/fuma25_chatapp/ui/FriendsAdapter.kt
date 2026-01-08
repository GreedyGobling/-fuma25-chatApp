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
import com.example.fuma25_chatapp.data.User

class FriendsAdapter : ListAdapter<User, FriendsAdapter.FriendViewHolder>(Diff) {

    private object Diff : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.textFriendName)
        private val emailText: TextView = itemView.findViewById(R.id.textFriendEmail)
        private val avatarText: TextView? = itemView.findViewById(R.id.tvFriendAvatar)

        fun bind(user: User) {
            val displayName = if (!user.name.isNullOrBlank()) user.name else user.email.substringBefore("@")
            nameText.text = displayName
            emailText.text = user.email

            avatarText?.let {
                it.text = displayName.take(1).uppercase()

                val background = it.background as? GradientDrawable
                background?.setColor(getUserColor(user.uid))
            }
        }

        private fun getUserColor(userId: String): Int {
            val hash = userId.hashCode()
            val hue = Math.abs(hash % 360).toFloat()
            return Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.9f))
        }
    }
}