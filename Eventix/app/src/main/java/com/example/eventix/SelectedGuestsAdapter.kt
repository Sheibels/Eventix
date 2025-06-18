package com.example.eventix

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SelectedGuestsAdapter(
    private val guests: MutableList<Guest>,
    private val onRemoveGuest: (Guest) -> Unit
) : RecyclerView.Adapter<SelectedGuestsAdapter.SelectedGuestViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedGuestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_guest, parent, false)
        return SelectedGuestViewHolder(view)
    }

    override fun onBindViewHolder(holder: SelectedGuestViewHolder, position: Int) {
        holder.bind(guests[position])
    }

    override fun getItemCount(): Int = guests.size

    inner class SelectedGuestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvGuestName: TextView = itemView.findViewById(R.id.tvGuestName)
        private val tvGuestEmail: TextView = itemView.findViewById(R.id.tvGuestEmail)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemove)

        fun bind(guest: Guest) {
            tvGuestName.text = guest.nome
            tvGuestEmail.text = guest.email

            btnRemove.setOnClickListener {
                onRemoveGuest(guest)
            }
        }
    }
}