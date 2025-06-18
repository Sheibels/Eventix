package com.example.eventix

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null,
    val read: Boolean = false
) {
    fun getFormattedTime(): String {
        return timestamp?.let {
            val date = it.toDate()
            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "PT"))
            formatter.format(date)
        } ?: ""
    }

    fun getFormattedDate(): String {
        return timestamp?.let {
            val date = it.toDate()
            val formatter = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "PT"))
            formatter.format(date)
        } ?: ""
    }

    fun isToday(): Boolean {
        return timestamp?.let {
            val messageDate = java.util.Calendar.getInstance().apply { time = it.toDate() }
            val today = java.util.Calendar.getInstance()

            messageDate.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                    messageDate.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
        } ?: false
    }
}

data class Conversation(
    val id: String = "",
    val eventId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Timestamp? = null,
    val lastMessageSender: String = ""
)