package com.example.fuma25_chatapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fuma25_chatapp.data.User

class FriendsAdapter : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    private val friends = mutableListOf<User>()

    fun submitList(newList: List<User>) {
        friends.clear()
        friends.addAll(newList)
        notifyDataSetChanged() // update list
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]
        holder.nameText.text = friend.name
        holder.emailText.text = friend.email
    }

    override fun getItemCount(): Int = friends.size

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(android.R.id.text1)
        val emailText: TextView = itemView.findViewById(android.R.id.text2)
    }
}