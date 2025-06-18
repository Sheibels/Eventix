package com.example.eventix

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class ManagerEventsAdapter(
    private var events: MutableList<EventDetail>,
    private val onAssociateClick: (EventDetail) -> Unit,
    private val onStatusChangeClick: (EventDetail, EventStatus) -> Unit,
    private val onEventClick: (EventDetail) -> Unit,
    private val onMessageClick: (EventDetail) -> Unit
) : RecyclerView.Adapter<ManagerEventsAdapter.EventViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvEventType: TextView = itemView.findViewById(R.id.tvEventType)
        val tvEventStatus: TextView = itemView.findViewById(R.id.tvEventStatus)
        val tvEventDate: TextView = itemView.findViewById(R.id.tvEventDate)
        val tvEventTime: TextView = itemView.findViewById(R.id.tvEventTime)
        val tvEventLocation: TextView = itemView.findViewById(R.id.tvEventLocation)
        val tvEventParticipants: TextView = itemView.findViewById(R.id.tvEventParticipants)
        val tvEventCost: TextView = itemView.findViewById(R.id.tvEventCost)
        val tvCreatorName: TextView = itemView.findViewById(R.id.tvCreatorName)
        val tvCreatorEmail: TextView = itemView.findViewById(R.id.tvCreatorEmail)
        val tvCreatorPhone: TextView = itemView.findViewById(R.id.tvCreatorPhone)
        val btnAssociate: Button = itemView.findViewById(R.id.btnAssociate)
        val btnChangeStatus: Button = itemView.findViewById(R.id.btnChangeStatus)
        val btnMessageCreator: ImageButton = itemView.findViewById(R.id.btnMessageCreator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manager_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        holder.tvEventType.text = event.tipoEvento
        holder.tvEventDate.text = event.data
        holder.tvEventTime.text = event.hora
        holder.tvEventLocation.text = event.localizacao
        holder.tvEventParticipants.text = event.getParticipantsText()
        holder.tvEventCost.text = event.getFormattedCost()

        loadCreatorInfo(event.criador, holder)

        if (event.isAssociatedToManager) {
            val status = event.getStatusEnum()
            holder.tvEventStatus.text = status.displayName
            holder.tvEventStatus.visibility = View.VISIBLE

            val statusBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor(status.colorResource))
                cornerRadius = 20f
            }
            holder.tvEventStatus.background = statusBackground

            holder.tvEventStatus.setOnClickListener {
                if (status != EventStatus.COMPLETED) {
                    showStatusChangeDialog(holder.itemView.context, event)
                }
            }

            holder.btnAssociate.visibility = View.GONE

            if (status == EventStatus.COMPLETED) {
                holder.btnChangeStatus.visibility = View.GONE
                holder.btnMessageCreator.visibility = View.VISIBLE
                holder.btnMessageCreator.setOnClickListener {
                    onMessageClick(event)
                }
            } else {
                holder.btnChangeStatus.visibility = View.VISIBLE
                holder.btnChangeStatus.setOnClickListener {
                    showStatusChangeDialog(holder.itemView.context, event)
                }

                val isPending = status == EventStatus.PENDING
                if (isPending) {
                    holder.btnMessageCreator.visibility = View.GONE
                } else {
                    holder.btnMessageCreator.visibility = View.VISIBLE
                    holder.btnMessageCreator.setOnClickListener {
                        onMessageClick(event)
                    }
                }
            }
        } else {
            holder.tvEventStatus.visibility = View.GONE
            holder.btnAssociate.visibility = View.VISIBLE
            holder.btnChangeStatus.visibility = View.GONE
            holder.btnMessageCreator.visibility = View.GONE

            holder.btnAssociate.setOnClickListener {
                onAssociateClick(event)
            }
        }

        holder.itemView.setOnClickListener {
            onEventClick(event)
        }
    }

    private fun loadCreatorInfo(creatorId: String, holder: EventViewHolder) {
        db.collection("utilizadores").document(creatorId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("nome") ?: "Nome não disponível"
                    val email = document.getString("email") ?: "Email não disponível"
                    val phone = document.getString("telemovel") ?: ""

                    holder.tvCreatorName.text = name
                    holder.tvCreatorEmail.text = email

                    if (phone.isNotEmpty()) {
                        holder.tvCreatorPhone.text = phone
                        holder.tvCreatorPhone.visibility = View.VISIBLE
                    } else {
                        holder.tvCreatorPhone.visibility = View.GONE
                    }
                } else {
                    holder.tvCreatorName.text = "Utilizador não encontrado"
                    holder.tvCreatorEmail.text = "Email não disponível"
                    holder.tvCreatorPhone.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                holder.tvCreatorName.text = "Erro ao carregar informações"
                holder.tvCreatorEmail.text = "Email não disponível"
                holder.tvCreatorPhone.visibility = View.GONE
            }
    }

    override fun getItemCount(): Int = events.size

    private fun showStatusChangeDialog(context: android.content.Context, event: EventDetail) {
        if (!event.isAssociatedToManager) return

        val currentStatus = event.getStatusEnum()
        val availableStatuses = mutableListOf<EventStatus>()

        when (currentStatus) {
            EventStatus.PENDING -> {
                availableStatuses.addAll(listOf(
                    EventStatus.CONFIRMED,
                    EventStatus.CANCELLED
                ))
            }
            EventStatus.CONFIRMED -> {
                availableStatuses.addAll(listOf(
                    EventStatus.COMPLETED,
                    EventStatus.CANCELLED
                ))
            }
            EventStatus.COMPLETED -> {
                return
            }
            EventStatus.CANCELLED -> {
                availableStatuses.add(EventStatus.PENDING)
            }
            EventStatus.ALL -> {
                return
            }
        }

        if (availableStatuses.isEmpty()) {
            return
        }

        val statusNames = availableStatuses.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Alterar Estado do Evento")
            .setItems(statusNames) { _, which ->
                val selectedStatus = availableStatuses[which]
                onStatusChangeClick(event, selectedStatus)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}