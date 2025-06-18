package com.example.eventix

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messages: MutableList<Message>,
    private val currentUserId: String
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDateSeparator: TextView = itemView.findViewById(R.id.tvDateSeparator)
        val layoutMessage: LinearLayout = itemView.findViewById(R.id.layoutMessage)
        val cardMessage: CardView = itemView.findViewById(R.id.cardMessage)
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val ivReadStatus: ImageView = itemView.findViewById(R.id.ivReadStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val context = holder.itemView.context

        val isSentByMe = message.senderId == currentUserId

        holder.tvMessage.text = message.message
        holder.tvTime.text = message.getFormattedTime()

        if (shouldShowDateSeparator(position)) {
            holder.tvDateSeparator.visibility = View.VISIBLE
            holder.tvDateSeparator.text = getDateSeparatorText(message)
        } else {
            holder.tvDateSeparator.visibility = View.GONE
        }

        if (isSentByMe) {
            holder.layoutMessage.gravity = android.view.Gravity.END
            holder.cardMessage.setCardBackgroundColor(context.getColor(R.color.brown))
            holder.tvMessage.setTextColor(context.getColor(R.color.white))
            holder.tvTime.setTextColor(context.getColor(android.R.color.white))

            if (message.read) {
                holder.ivReadStatus.visibility = View.VISIBLE
                holder.ivReadStatus.setColorFilter(context.getColor(android.R.color.white))
            } else {
                holder.ivReadStatus.visibility = View.VISIBLE
                holder.ivReadStatus.setColorFilter(context.getColor(android.R.color.darker_gray))
            }
        } else {
            holder.layoutMessage.gravity = android.view.Gravity.START
            holder.cardMessage.setCardBackgroundColor(context.getColor(R.color.light_gray))
            holder.tvMessage.setTextColor(context.getColor(R.color.black))
            holder.tvTime.setTextColor(context.getColor(R.color.dark_gray))
            holder.ivReadStatus.visibility = View.GONE
        }
    }

    private fun shouldShowDateSeparator(position: Int): Boolean {
        if (position == 0) return true

        val currentMessage = messages[position]
        val previousMessage = messages[position - 1]

        val currentDate = currentMessage.timestamp?.toDate()
        val previousDate = previousMessage.timestamp?.toDate()

        if (currentDate == null || previousDate == null) return false

        val currentCal = Calendar.getInstance().apply { time = currentDate }
        val previousCal = Calendar.getInstance().apply { time = previousDate }

        return currentCal.get(Calendar.DAY_OF_YEAR) != previousCal.get(Calendar.DAY_OF_YEAR) ||
                currentCal.get(Calendar.YEAR) != previousCal.get(Calendar.YEAR)
    }

    private fun getDateSeparatorText(message: Message): String {
        return message.timestamp?.let { timestamp ->
            val date = timestamp.toDate()
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val messageCal = Calendar.getInstance().apply { time = date }

            when {
                isSameDay(messageCal, today) -> "Hoje"
                isSameDay(messageCal, yesterday) -> "Ontem"
                else -> {
                    val formatter = SimpleDateFormat("dd 'de' MMMM", Locale("pt", "PT"))
                    formatter.format(date)
                }
            }
        } ?: ""
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages.sortedBy { it.timestamp?.toDate() })
        notifyDataSetChanged()
    }
}