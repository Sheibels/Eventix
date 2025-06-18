package com.example.eventix

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GuestsAdapter(
    private var guests: MutableList<Guest>,
    private val onRemoveClick: (Guest) -> Unit
) : RecyclerView.Adapter<GuestsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivGuestAvatar: ImageView = view.findViewById(R.id.ivGuestAvatar)
        val tvGuestName: TextView = view.findViewById(R.id.tvGuestName)
        val tvGuestEmail: TextView = view.findViewById(R.id.tvGuestEmail)
        val btnRemoveGuest: Button = view.findViewById(R.id.btnRemoveGuest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_guest, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val guest = guests[position]
        holder.tvGuestName.text = guest.nome
        holder.tvGuestEmail.text = guest.email

        holder.btnRemoveGuest.setOnClickListener {
            onRemoveClick(guest)
        }
    }

    override fun getItemCount() = guests.size

    fun updateGuests(newGuests: List<Guest>) {
        guests.clear()
        guests.addAll(newGuests)
        notifyDataSetChanged()
    }
}