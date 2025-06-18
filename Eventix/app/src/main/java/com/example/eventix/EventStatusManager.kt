package com.example.eventix

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object EventStatusManager {
    private const val TAG = "EventStatusManager"
    private val db = FirebaseFirestore.getInstance()

    fun updateEventStatus(
        eventId: String,
        newStatus: EventStatus,
        creatorId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("eventos").document(eventId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentStateString = document.getString("estado") ?: "ativo"
                    val currentStatus = mapDatabaseStateToEnum(currentStateString)

                    val newStateString = mapEnumToDatabaseState(newStatus)

                    val updates = mapOf(
                        "estado" to newStateString,
                        "estadoEvento" to newStateString
                    )

                    db.collection("eventos").document(eventId)
                        .update(updates)
                        .addOnSuccessListener {
                            if (currentStatus != newStatus) {
                                updateUserStatsForStatusChange(creatorId, currentStatus, newStatus)
                            }
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            onFailure(e)
                        }
                } else {
                    val error = Exception("Evento não encontrado")
                    onFailure(error)
                }
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    private fun updateUserStatsForStatusChange(
        userId: String,
        oldStatus: EventStatus,
        newStatus: EventStatus
    ) {
        val wasActive = isEventActive(oldStatus)
        val isNowActive = isEventActive(newStatus)

        val updates = mutableMapOf<String, Any>()

        when {
            !wasActive && isNowActive -> {
                updates["eventosAtivos"] = FieldValue.increment(1)
            }
            wasActive && !isNowActive -> {
                updates["eventosAtivos"] = FieldValue.increment(-1)
            }
        }

        if (updates.isNotEmpty()) {
            db.collection("utilizadores").document(userId)
                .update(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Estatísticas atualizadas: $updates")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Erro ao atualizar estatísticas", e)
                }
        }
    }

    private fun isEventActive(status: EventStatus): Boolean {
        return when (status) {
            EventStatus.PENDING -> true
            EventStatus.CONFIRMED -> true
            EventStatus.COMPLETED -> false
            EventStatus.CANCELLED -> false
            EventStatus.ALL -> false
        }
    }

    private fun mapEnumToDatabaseState(status: EventStatus): String {
        return when (status) {
            EventStatus.PENDING -> "ativo"
            EventStatus.CONFIRMED -> "confirmado"
            EventStatus.COMPLETED -> "concluido"
            EventStatus.CANCELLED -> "cancelado"
            EventStatus.ALL -> "ativo"
        }
    }

    private fun mapDatabaseStateToEnum(databaseState: String): EventStatus {
        return when (databaseState.lowercase().trim()) {
            "ativo" -> EventStatus.PENDING
            "confirmado" -> EventStatus.CONFIRMED
            "concluido", "concluído" -> EventStatus.COMPLETED
            "cancelado" -> EventStatus.CANCELLED
            else -> EventStatus.PENDING
        }
    }

    fun updateUserEventStats(
        userId: String,
        incrementCreated: Boolean = false,
        incrementActive: Boolean = false,
        decrementActive: Boolean = false,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val updates = mutableMapOf<String, Any>()

        if (incrementCreated) {
            updates["eventosCriados"] = FieldValue.increment(1)
        }

        if (incrementActive) {
            updates["eventosAtivos"] = FieldValue.increment(1)
        }

        if (decrementActive) {
            updates["eventosAtivos"] = FieldValue.increment(-1)
        }

        if (updates.isNotEmpty()) {
            db.collection("utilizadores").document(userId)
                .update(updates)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    onFailure(e)
                }
        } else {
            onSuccess()
        }
    }

    fun syncInconsistentEventStates() {
        Log.d(TAG, "Iniciando sincronização de estados inconsistentes...")

        db.collection("eventos")
            .get()
            .addOnSuccessListener { documents ->
                val batch = db.batch()
                var updatedCount = 0

                documents.forEach { document ->
                    val estadoEvento = document.getString("estadoEvento")
                    val estado = document.getString("estado")

                    if (estadoEvento != null && estadoEvento != estado) {
                        val eventRef = db.collection("eventos").document(document.id)
                        batch.update(eventRef, "estado", estadoEvento)
                        updatedCount++
                        Log.d(TAG, "Sincronizando evento ${document.id}: $estado -> $estadoEvento")
                    }
                }

                if (updatedCount > 0) {
                    batch.commit()
                        .addOnSuccessListener {
                            Log.d(TAG, "Sincronização concluída: $updatedCount eventos atualizados")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Erro na sincronização", e)
                        }
                } else {
                    Log.d(TAG, "Nenhuma inconsistência encontrada")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar eventos para sincronização", e)
            }
    }
}