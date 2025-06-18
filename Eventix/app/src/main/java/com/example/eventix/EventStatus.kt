package com.example.eventix

enum class EventStatus(val displayName: String, val colorResource: String) {
    ALL("Todos", "#757575"),
    PENDING("Por Confirmar", "#FF9800"),
    CONFIRMED("Confirmado", "#4CAF50"),
    COMPLETED("Concluído", "#9C27B0"),
    CANCELLED("Cancelado", "#F44336")
}

enum class EventOwnershipFilter(val displayName: String) {
    MY_EVENTS("Os Meus Eventos"),
    OTHERS_EVENTS("Eventos de Outros")
}

data class EventDetail(
    val id: String = "",
    val tipoEvento: String = "",
    val data: String = "",
    val hora: String = "",
    val localizacao: String = "",
    val numeroParticipantes: Int = 0,
    val convidados: List<String> = emptyList(),
    val custoTotal: Double = 0.0,
    val estado: String = "ativo",
    val criador: String = "",
    val timestamp: com.google.firebase.Timestamp? = null,
    val servicos: List<String> = emptyList(),
    val servicosIds: List<String> = emptyList(),
    val nomesParticipantes: List<String> = emptyList(),
    val localizacaoDetalhes: Map<String, Any>? = null,
    val isCreator: Boolean = false,
    val gestorAssociado: String? = null,
    val isAssociatedToManager: Boolean = false
) {
    fun getStatusEnum(): EventStatus {
        return when (estado.lowercase().trim()) {
            "ativo" -> EventStatus.PENDING
            "confirmado" -> EventStatus.CONFIRMED
            "concluído", "concluido" -> EventStatus.COMPLETED
            "cancelado" -> EventStatus.CANCELLED
            else -> EventStatus.PENDING
        }
    }

    fun canEdit(): Boolean {
        return getStatusEnum() == EventStatus.PENDING && isCreator
    }

    fun canDelete(): Boolean {
        return getStatusEnum() == EventStatus.PENDING && isCreator
    }

    fun getFormattedCost(): String {
        return if (custoTotal > 0) {
            "${custoTotal.toInt()}€"
        } else {
            "Gratuito"
        }
    }

    fun getParticipantsText(): String {
        return if (numeroParticipantes == 1) {
            "1 participante"
        } else {
            "$numeroParticipantes participantes"
        }
    }

    fun getEventRole(): String {
        return if (isCreator) "Criador" else "Convidado"
    }

    fun isAssociatedToManager(managerId: String): Boolean {
        return gestorAssociado == managerId
    }

    fun isAvailableForAssociation(): Boolean {
        return gestorAssociado.isNullOrEmpty()
    }

    fun isActiveEvent(): Boolean {
        return when (getStatusEnum()) {
            EventStatus.PENDING, EventStatus.CONFIRMED -> true
            EventStatus.COMPLETED, EventStatus.CANCELLED -> false
            EventStatus.ALL -> false
        }
    }

    fun getStatusDescription(): String {
        return when (getStatusEnum()) {
            EventStatus.PENDING -> "Aguarda confirmação do gestor"
            EventStatus.CONFIRMED -> "Confirmado e aprovado"
            EventStatus.COMPLETED -> "Evento realizado com sucesso"
            EventStatus.CANCELLED -> "Evento cancelado"
            EventStatus.ALL -> "Todos os estados"
        }
    }
}