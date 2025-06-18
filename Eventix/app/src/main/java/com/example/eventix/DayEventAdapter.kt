package com.example.eventix

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DayEventsAdapter(
    private val events: List<CalendarEvent>,
    private val onEventClick: (CalendarEvent) -> Unit
) : RecyclerView.Adapter<DayEventsAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvEventTime: TextView = itemView.findViewById(R.id.tvEventTime)
        val tvEventType: TextView = itemView.findViewById(R.id.tvEventType)
        val tvEventLocation: TextView = itemView.findViewById(R.id.tvEventLocation)
        val tvEventCreator: TextView = itemView.findViewById(R.id.tvEventCreator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        holder.tvEventTime.text = event.hora
        holder.tvEventType.text = event.tipoEvento
        holder.tvEventLocation.text = event.localizacao
        holder.tvEventCreator.text = "Criado por: ${event.creatorName}"

        if (event.isCreator) {
            holder.tvEventCreator.setTextColor(holder.itemView.context.getColor(R.color.brown))
        } else {
            holder.tvEventCreator.setTextColor(holder.itemView.context.getColor(R.color.dark_gray))
        }

        holder.itemView.setOnClickListener {
            onEventClick(event)
        }
    }

    override fun getItemCount() = events.size
}