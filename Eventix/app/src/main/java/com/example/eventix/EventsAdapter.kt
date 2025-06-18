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

class EventsAdapter(
    private var events: MutableList<EventDetail>,
    private val onEditClick: (EventDetail) -> Unit,
    private val onDeleteClick: (EventDetail) -> Unit,
    private val onMessageClick: (EventDetail) -> Unit,
    private val onEventClick: (EventDetail) -> Unit,
    private val onStatusChangeClick: ((EventDetail, EventStatus) -> Unit)? = null
) : RecyclerView.Adapter<EventsAdapter.EventViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvEventType: TextView = itemView.findViewById(R.id.tvEventType)
        val tvEventStatus: TextView = itemView.findViewById(R.id.tvEventStatus)
        val tvEventDate: TextView = itemView.findViewById(R.id.tvEventDate)
        val tvEventTime: TextView = itemView.findViewById(R.id.tvEventTime)
        val tvEventLocation: TextView = itemView.findViewById(R.id.tvEventLocation)
        val tvEventParticipants: TextView = itemView.findViewById(R.id.tvEventParticipants)
        val tvEventCost: TextView = itemView.findViewById(R.id.tvEventCost)
        val tvEventRole: TextView = itemView.findViewById(R.id.tvEventRole)
        val btnEditEvent: Button = itemView.findViewById(R.id.btnEditEvent)
        val btnDeleteEvent: Button = itemView.findViewById(R.id.btnDeleteEvent)
        val btnMessageManager: ImageButton = itemView.findViewById(R.id.btnMessageManager)
        val dividerView: View = itemView.findViewById(R.id.dividerView)
        val cardManagerInfo: androidx.cardview.widget.CardView = itemView.findViewById(R.id.cardManagerInfo)
        val tvManagerName: TextView = itemView.findViewById(R.id.tvManagerName)
        val tvManagerEmail: TextView = itemView.findViewById(R.id.tvManagerEmail)
        val tvManagerPhone: TextView = itemView.findViewById(R.id.tvManagerPhone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_details, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        holder.tvEventType.text = event.tipoEvento
        holder.tvEventDate.text = event.data
        holder.tvEventTime.text = event.hora
        holder.tvEventLocation.text = event.localizacao
        holder.tvEventParticipants.text = event.getParticipantsText()

        holder.tvEventRole.text = event.getEventRole()
        holder.tvEventRole.setTextColor(
            if (event.isCreator) {
                holder.itemView.context.getColor(R.color.brown)
            } else {
                holder.itemView.context.getColor(R.color.dark_gray)
            }
        )

        if (event.isCreator) {
            holder.tvEventCost.text = event.getFormattedCost()
            holder.tvEventCost.visibility = View.VISIBLE

            val status = event.getStatusEnum()
            holder.tvEventStatus.text = status.displayName
            holder.tvEventStatus.visibility = View.VISIBLE

            val statusBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor(status.colorResource))
                cornerRadius = 20f
            }
            holder.tvEventStatus.background = statusBackground

            if (status != EventStatus.COMPLETED) {
                holder.tvEventStatus.setOnClickListener {
                    showStatusChangeDialog(holder.itemView.context, event)
                }
            } else {
                holder.tvEventStatus.setOnClickListener(null)
            }

            holder.dividerView.visibility = View.VISIBLE
            holder.btnEditEvent.visibility = View.VISIBLE
            holder.btnDeleteEvent.visibility = View.VISIBLE
            holder.btnMessageManager.visibility = View.VISIBLE

            if (event.canEdit()) {
                holder.btnEditEvent.isEnabled = true
                holder.btnEditEvent.alpha = 1.0f
                holder.btnEditEvent.text = "EDITAR"
            } else {
                holder.btnEditEvent.isEnabled = false
                holder.btnEditEvent.alpha = 0.5f
                holder.btnEditEvent.text = "BLOQUEADO"
            }

            if (event.canDelete()) {
                holder.btnDeleteEvent.isEnabled = true
                holder.btnDeleteEvent.alpha = 1.0f
                holder.btnDeleteEvent.text = "ELIMINAR"
            } else {
                holder.btnDeleteEvent.isEnabled = false
                holder.btnDeleteEvent.alpha = 0.5f
                holder.btnDeleteEvent.text = "BLOQUEADO"
            }

            val isPending = event.getStatusEnum() == EventStatus.PENDING
            val isCompleted = event.getStatusEnum() == EventStatus.COMPLETED

            if (isPending) {
                holder.btnMessageManager.isEnabled = false
                holder.btnMessageManager.alpha = 0.5f
            } else {
                holder.btnMessageManager.isEnabled = true
                holder.btnMessageManager.alpha = 1.0f
            }

        } else {
            holder.tvEventCost.visibility = View.GONE
            holder.tvEventStatus.visibility = View.GONE
            holder.dividerView.visibility = View.GONE
            holder.btnEditEvent.visibility = View.GONE
            holder.btnDeleteEvent.visibility = View.GONE
            holder.btnMessageManager.visibility = View.GONE
        }

        if (!event.gestorAssociado.isNullOrEmpty()) {
            holder.cardManagerInfo.visibility = View.VISIBLE
            loadManagerInfo(event.gestorAssociado!!, holder)
        } else {
            holder.cardManagerInfo.visibility = View.GONE
        }

        if (event.isCreator) {
            holder.btnEditEvent.setOnClickListener {
                if (event.canEdit()) {
                    onEditClick(event)
                }
            }

            holder.btnDeleteEvent.setOnClickListener {
                if (event.canDelete()) {
                    onDeleteClick(event)
                }
            }

            holder.btnMessageManager.setOnClickListener {
                val isPending = event.getStatusEnum() == EventStatus.PENDING
                if (isPending) {
                    android.app.AlertDialog.Builder(holder.itemView.context)
                        .setTitle("Funcionalidade Indisponível")
                        .setMessage("Não é possível enviar mensagens ao gestor enquanto o evento estiver por confirmar.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    onMessageClick(event)
                }
            }

            holder.itemView.setOnClickListener {
                onEventClick(event)
            }
        } else {
            holder.itemView.setOnClickListener(null)
        }
    }

    private fun loadManagerInfo(managerId: String, holder: EventViewHolder) {
        db.collection("utilizadores").document(managerId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("nome") ?: "Gestor Desconhecido"
                    val email = document.getString("email") ?: "Email não disponível"
                    val phone = document.getString("telemovel") ?: ""

                    holder.tvManagerName.text = name
                    holder.tvManagerEmail.text = email

                    if (phone.isNotEmpty()) {
                        holder.tvManagerPhone.text = phone
                        holder.tvManagerPhone.visibility = View.VISIBLE
                    } else {
                        holder.tvManagerPhone.visibility = View.GONE
                    }
                } else {
                    holder.tvManagerName.text = "Gestor não encontrado"
                    holder.tvManagerEmail.text = "Email não disponível"
                    holder.tvManagerPhone.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                holder.tvManagerName.text = "Erro ao carregar informações"
                holder.tvManagerEmail.text = "Email não disponível"
                holder.tvManagerPhone.visibility = View.GONE
            }
    }

    override fun getItemCount(): Int = events.size

    fun updateEvents(newEvents: List<EventDetail>) {
        events.clear()
        events.addAll(newEvents)
        notifyDataSetChanged()
    }

    fun scrollToEvent(eventId: String) {
        val position = events.indexOfFirst { it.id == eventId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    private fun showStatusChangeDialog(context: android.content.Context, event: EventDetail) {
        if (!event.isCreator) return

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
                onStatusChangeClick?.invoke(event, selectedStatus)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}