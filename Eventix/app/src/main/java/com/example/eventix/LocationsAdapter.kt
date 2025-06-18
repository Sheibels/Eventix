package com.example.eventix

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LocationsAdapter(
    private val locations: MutableList<LocalSelecionado>,
    private val onLocationClick: (LocalSelecionado) -> Unit
) : RecyclerView.Adapter<LocationsAdapter.LocationViewHolder>() {

    private var selectedPosition = -1

    class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvLocationName)
        val tvAddress: TextView = itemView.findViewById(R.id.tvLocationAddress)
        val tvCategory: TextView = itemView.findViewById(R.id.tvLocationCategory)
        val tvDistance: TextView = itemView.findViewById(R.id.tvLocationDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val location = locations[position]
        val currentPosition = holder.adapterPosition

        holder.tvName.text = location.nome
        holder.tvAddress.text = location.endereco
        holder.tvCategory.text = location.categoria
        holder.tvDistance.text = String.format("%.1f km", location.distancia)

        if (currentPosition == selectedPosition) {
            holder.itemView.setBackgroundResource(R.drawable.selected_location_background)
        } else {
            holder.itemView.setBackgroundResource(R.drawable.location_item_background)
        }

        holder.itemView.setOnClickListener {
            val clickedPosition = holder.adapterPosition
            if (clickedPosition != RecyclerView.NO_POSITION) {
                val previousPosition = selectedPosition
                selectedPosition = clickedPosition

                if (previousPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(previousPosition)
                }
                notifyItemChanged(selectedPosition)

                onLocationClick(location)
            }
        }
    }

    override fun getItemCount() = locations.size

    fun updateLocations(newLocations: List<LocalSelecionado>) {
        locations.clear()
        locations.addAll(newLocations)
        selectedPosition = -1
        notifyDataSetChanged()
    }

    fun getSelectedLocation(): LocalSelecionado? {
        return if (selectedPosition >= 0 && selectedPosition < locations.size) {
            locations[selectedPosition]
        } else null
    }
}