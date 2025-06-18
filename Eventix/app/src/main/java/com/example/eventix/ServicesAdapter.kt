package com.example.eventix

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ServicesAdapter(
    private var services: MutableList<Servico>,
    private val selectedServiceIds: MutableSet<String>,
    private val onServiceSelected: (Servico, Boolean) -> Unit,
    private val onFavoriteClicked: (Servico) -> Unit
) : RecyclerView.Adapter<ServicesAdapter.ServiceViewHolder>() {

    class ServiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvServiceName: TextView = itemView.findViewById(R.id.tvServiceName)
        val tvServiceCompany: TextView = itemView.findViewById(R.id.tvServiceCompany)
        val tvServiceType: TextView = itemView.findViewById(R.id.tvServiceType)
        val tvServicePrice: TextView = itemView.findViewById(R.id.tvServicePrice)
        val tvServiceDescription: TextView = itemView.findViewById(R.id.tvServiceDescription)
        val tvServiceContact: TextView = itemView.findViewById(R.id.tvServiceContact)
        val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
        val cbServiceSelected: CheckBox = itemView.findViewById(R.id.cbServiceSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service, parent, false)
        return ServiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = services[position]

        holder.tvServiceName.text = service.nome
        holder.tvServiceCompany.text = service.empresa
        holder.tvServiceType.text = service.tipo
        holder.tvServicePrice.text = service.getPrecoFormatado()
        holder.tvServiceDescription.text = service.descricao
        holder.tvServiceContact.text = service.contacto

        if (service.favorito) {
            holder.btnFavorite.setImageResource(R.drawable.ic_favorite)
            holder.btnFavorite.setColorFilter(holder.itemView.context.getColor(R.color.brown))
        } else {
            holder.btnFavorite.setImageResource(R.drawable.ic_favorite_border)
            holder.btnFavorite.setColorFilter(holder.itemView.context.getColor(R.color.dark_gray))
        }

        val isSelected = selectedServiceIds.contains(service.id)
        holder.cbServiceSelected.setOnCheckedChangeListener(null)
        holder.cbServiceSelected.isChecked = isSelected

        holder.btnFavorite.setOnClickListener {
            onFavoriteClicked(service)
        }

        holder.cbServiceSelected.setOnCheckedChangeListener { _, isChecked ->
            onServiceSelected(service, isChecked)
        }

        holder.itemView.setOnClickListener {
            holder.cbServiceSelected.isChecked = !holder.cbServiceSelected.isChecked
        }
    }

    override fun getItemCount(): Int = services.size

    fun updateServices(newServices: List<Servico>) {
        services.clear()
        services.addAll(newServices)
        notifyDataSetChanged()
    }

    fun updateServiceSelection(serviceId: String, isSelected: Boolean) {
        val position = services.indexOfFirst { it.id == serviceId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }
}