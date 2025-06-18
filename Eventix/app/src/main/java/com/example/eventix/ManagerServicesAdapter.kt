package com.example.eventix

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ManagerServicesAdapter(
    private var services: MutableList<Servico>,
    private val onEditClick: (Servico) -> Unit,
    private val onDeleteClick: (Servico) -> Unit
) : RecyclerView.Adapter<ManagerServicesAdapter.ServiceViewHolder>() {

    class ServiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvServiceName: TextView = itemView.findViewById(R.id.tvServiceName)
        val tvServiceCompany: TextView = itemView.findViewById(R.id.tvServiceCompany)
        val tvServiceType: TextView = itemView.findViewById(R.id.tvServiceType)
        val tvServicePrice: TextView = itemView.findViewById(R.id.tvServicePrice)
        val tvServiceDescription: TextView = itemView.findViewById(R.id.tvServiceDescription)
        val tvServiceContact: TextView = itemView.findViewById(R.id.tvServiceContact)
        val btnEditService: Button = itemView.findViewById(R.id.btnEditService)
        val btnDeleteService: Button = itemView.findViewById(R.id.btnDeleteService)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manager_service, parent, false)
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

        holder.btnEditService.setOnClickListener {
            onEditClick(service)
        }

        holder.btnDeleteService.setOnClickListener {
            onDeleteClick(service)
        }
    }

    override fun getItemCount(): Int = services.size

    fun updateServices(newServices: List<Servico>) {
        services.clear()
        services.addAll(newServices)
        notifyDataSetChanged()
    }
}