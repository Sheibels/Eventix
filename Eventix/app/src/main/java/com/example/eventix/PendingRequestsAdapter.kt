package com.example.eventix

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PendingRequestsAdapter(
    private var requests: MutableList<GuestRequest>,
    private val onAccept: (GuestRequest) -> Unit,
    private val onReject: (GuestRequest) -> Unit
) : RecyclerView.Adapter<PendingRequestsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivSenderAvatar: ImageView = view.findViewById(R.id.ivSenderAvatar)
        val tvSenderName: TextView = view.findViewById(R.id.tvSenderName)
        val tvSenderEmail: TextView = view.findViewById(R.id.tvSenderEmail)
        val btnAcceptRequest: Button = view.findViewById(R.id.btnAcceptRequest)
        val btnRejectRequest: Button = view.findViewById(R.id.btnRejectRequest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        holder.tvSenderName.text = request.nomeRemetente
        holder.tvSenderEmail.text = request.emailRemetente

        holder.btnAcceptRequest.setOnClickListener {
            onAccept(request)
        }

        holder.btnRejectRequest.setOnClickListener {
            onReject(request)
        }
    }

    override fun getItemCount() = requests.size

    fun updateRequests(newRequests: List<GuestRequest>) {
        requests.clear()
        requests.addAll(newRequests)
        notifyDataSetChanged()
    }
}