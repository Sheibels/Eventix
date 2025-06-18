package com.example.eventix

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

object NotificationManager {

    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "NotificationManager"
    private val recentNotifications = mutableMapOf<String, Long>()

    fun sendGuestRequestNotification(context: Context, senderId: String, receiverId: String, senderName: String) {
        val notificationKey = "guest_${senderId}_${receiverId}"
        val now = System.currentTimeMillis()
        val lastSent = recentNotifications[notificationKey] ?: 0

        if (now - lastSent < 5000) {
            Log.d(TAG, "Notificação de pedido de convidado enviada recentemente - ignorando duplicata")
            return
        }

        recentNotifications[notificationKey] = now

        shouldSendNotification(receiverId, "guestRequests") { shouldSend ->
            if (shouldSend) {
                val notification = hashMapOf(
                    "userId" to receiverId,
                    "type" to NotificationType.GUEST_REQUEST.value,
                    "title" to "Novo pedido de convidado",
                    "message" to "$senderName enviou-lhe um pedido de convite",
                    "timestamp" to Timestamp.now(),
                    "isRead" to false,
                    "data" to mapOf(
                        "senderId" to senderId,
                        "senderName" to senderName
                    )
                )

                db.collection("notifications")
                    .add(notification)
                    .addOnSuccessListener {
                        Log.d(TAG, "Notificação de pedido de convidado criada na BD")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Erro ao criar notificação de pedido de convidado", e)
                    }
            }
        }
    }

    fun sendEventReminderNotifications(context: Context, userId: String) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance()

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = dateFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 6)
        val nextWeek = dateFormat.format(calendar.time)

        db.collection("utilizadores").document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                val notifications = userDoc.get("notificationSettings") as? Map<String, Any>
                val weekReminder = notifications?.get("weekReminder") as? Boolean ?: true
                val dayReminder = notifications?.get("dayReminder") as? Boolean ?: true

                if (weekReminder || dayReminder) {
                    db.collection("eventos")
                        .whereEqualTo("criador", userId)
                        .whereIn("estado", listOf("ativo", "confirmado"))
                        .get()
                        .addOnSuccessListener { documents ->
                            documents.forEach { document ->
                                val eventDate = document.getString("data") ?: ""
                                val eventType = document.getString("tipoEvento") ?: ""
                                val eventId = document.id

                                when (eventDate) {
                                    nextWeek -> {
                                        if (weekReminder) {
                                            sendEventReminderNotification(context, userId, eventType, eventDate, 7, eventId)
                                        }
                                    }
                                    tomorrow -> {
                                        if (dayReminder) {
                                            sendEventReminderNotification(context, userId, eventType, eventDate, 1, eventId)
                                        }
                                    }
                                }
                            }
                        }
                }
            }
    }

    private fun sendEventReminderNotification(context: Context, userId: String, eventType: String, eventDate: String, daysUntil: Int, eventId: String) {
        val notificationKey = "event_${eventId}_${daysUntil}_${userId}"
        val now = System.currentTimeMillis()
        val lastSent = recentNotifications[notificationKey] ?: 0

        if (now - lastSent < 60000) {
            Log.d(TAG, "Notificação de lembrete de evento enviada recentemente - ignorando duplicata")
            return
        }

        recentNotifications[notificationKey] = now

        val type = if (daysUntil == 7) NotificationType.EVENT_REMINDER_WEEK else NotificationType.EVENT_REMINDER_DAY
        val title = if (daysUntil == 7) "Evento em 1 semana" else "Evento amanhã"
        val message = "O seu evento '$eventType' é em $eventDate"

        val notification = hashMapOf(
            "userId" to userId,
            "type" to type.value,
            "title" to title,
            "message" to message,
            "timestamp" to Timestamp.now(),
            "isRead" to false,
            "data" to mapOf(
                "eventId" to eventId,
                "eventType" to eventType,
                "eventDate" to eventDate,
                "daysUntil" to daysUntil
            )
        )

        db.collection("notifications")
            .add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Notificação de lembrete de evento criada na BD")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao criar notificação de lembrete de evento", e)
            }
    }

    fun sendMessageNotification(context: Context, senderId: String, receiverId: String, senderName: String, eventId: String) {
        val notificationKey = "message_${senderId}_${receiverId}_${eventId}"
        val now = System.currentTimeMillis()
        val lastSent = recentNotifications[notificationKey] ?: 0

        if (now - lastSent < 2000) {
            Log.d(TAG, "Notificação de mensagem enviada recentemente - ignorando duplicata")
            return
        }

        if (senderId == receiverId) {
            return
        }

        recentNotifications[notificationKey] = now

        getUserRole(senderId) { senderRole ->
            val settingKey = if (senderRole == "gestor") "managerMessages" else "userMessages"
            val notificationType = if (senderRole == "gestor") NotificationType.MANAGER_MESSAGE else NotificationType.USER_MESSAGE

            shouldSendNotification(receiverId, settingKey) { shouldSend ->
                if (shouldSend) {
                    val title = if (senderRole == "gestor") "Nova mensagem do gestor" else "Nova mensagem do utilizador"
                    val message = "$senderName enviou-lhe uma mensagem"

                    val notification = hashMapOf(
                        "userId" to receiverId,
                        "type" to notificationType.value,
                        "title" to title,
                        "message" to message,
                        "timestamp" to Timestamp.now(),
                        "isRead" to false,
                        "data" to mapOf(
                            "senderId" to senderId,
                            "senderName" to senderName,
                            "eventId" to eventId
                        )
                    )

                    db.collection("notifications")
                        .add(notification)
                        .addOnSuccessListener {
                            Log.d(TAG, "Notificação de mensagem criada na BD para userId: $receiverId")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Erro ao criar notificação de mensagem", e)
                        }
                }
            }
        }
    }

    private fun shouldSendNotification(userId: String, settingKey: String, callback: (Boolean) -> Unit) {
        db.collection("utilizadores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                val notifications = document.get("notificationSettings") as? Map<String, Any>
                val enabled = notifications?.get(settingKey) as? Boolean ?: true
                callback(enabled)
            }
            .addOnFailureListener {
                callback(true)
            }
    }

    private fun getUserRole(userId: String, callback: (String) -> Unit) {
        db.collection("utilizadores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                val role = document.getString("role") ?: "utilizador"
                callback(role)
            }
            .addOnFailureListener {
                callback("utilizador")
            }
    }

    fun clearRecentNotifications() {
        recentNotifications.clear()
        Log.d(TAG, "Cache de notificações recentes limpo")
    }
}