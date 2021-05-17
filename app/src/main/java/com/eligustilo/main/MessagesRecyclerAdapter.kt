package com.eligustilo.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.eligustilo.hermez.Hermez

class MessageViewHolder(messageCell: View): RecyclerView.ViewHolder(messageCell){
    private var TAG = "MessageViewHolder"
    var sendingDevice: TextView = messageCell.findViewById(R.id.sending_device)
    var message: TextView = messageCell.findViewById(R.id.message)
}

class MessagesRecyclerAdapater(var messages: ArrayList<Hermez.HermezMessage>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val TAG = "MessagesRecyclerAdapater"

    fun updateMessages(updatedMessages: ArrayList<Hermez.HermezMessage>) {
        messages = updatedMessages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val messageCell = LayoutInflater.from(parent.context).inflate(R.layout.messages_cell, parent, false)
        return MessageViewHolder(messageCell)
    }

    override fun getItemCount(): Int {
        return messages.count()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val messageViewHolder = holder as MessageViewHolder
        val message = messages[position]
        messageViewHolder.sendingDevice.text = message.sendingDevice.name
        messageViewHolder.message.text = message.message
    }
}