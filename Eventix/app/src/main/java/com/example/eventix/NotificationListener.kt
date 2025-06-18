package com.example.eventix

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object NotificationListener {
    private const val TAG = "NotificationListener"
    private var notificationListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var isInMessagesActivity = false
    private var currentConversationId: String? = null
    private val processedNotifications = mutableSetOf<String>()

    fun startListening(context: Context) {
        val currentUserId = auth.currentUser?.uid ?: return

        Log.d(TAG, "Iniciando listener para utilizador: $currentUserId")

        notificationListener = db.collection("notifications")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Erro no listener de notificações", e)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val document = change.document
                        val notificationId = document.id

                        if (processedNotifications.contains(notificationId)) {
                            Log.d(TAG, "Notificação já processada: $notificationId")
                            return@forEach
                        }

                        processedNotifications.add(notificationId)

                        val type = document.getString("type") ?: ""
                        val title = document.getString("title") ?: ""
                        val message = document.getString("message") ?: ""
                        val data = document.get("data") as? Map<String, Any> ?: emptyMap()

                        Log.d(TAG, "Nova notificação recebida: $type - $title (ID: $notificationId)")

                        when (type) {
                            NotificationType.GUEST_REQUEST.value -> {
                                val senderName = data["senderName"] as? String ?: "Alguém"
                                NotificationService.sendGuestRequestNotification(context, senderName)
                                markNotificationAsRead(notificationId)
                            }
                            NotificationType.EVENT_REMINDER_WEEK.value -> {
                                val eventType = data["eventType"] as? String ?: "Evento"
                                val eventDate = data["eventDate"] as? String ?: ""
                                NotificationService.sendEventReminderNotification(context, eventType, eventDate, 7)
                                markNotificationAsRead(notificationId)
                            }
                            NotificationType.EVENT_REMINDER_DAY.value -> {
                                val eventType = data["eventType"] as? String ?: "Evento"
                                val eventDate = data["eventDate"] as? String ?: ""
                                NotificationService.sendEventReminderNotification(context, eventType, eventDate, 1)
                                markNotificationAsRead(notificationId)
                            }
                            NotificationType.MANAGER_MESSAGE.value, NotificationType.USER_MESSAGE.value -> {
                                val eventId = data["eventId"] as? String ?: ""
                                val senderId = data["senderId"] as? String ?: ""
                                val conversationId = generateConversationId(senderId, currentUserId, eventId)

                                if (!isInMessagesActivity || currentConversationId != conversationId) {
                                    val senderName = data["senderName"] as? String ?: "Utilizador"
                                    val isManager = type == NotificationType.MANAGER_MESSAGE.value
                                    NotificationService.sendMessageNotification(context, senderName, eventId, isManager)
                                } else {
                                    Log.d(TAG, "Utilizador está na conversa - não enviando notificação")
                                }
                                markNotificationAsRead(notificationId)
                            }
                        }
                    }
                }
            }
    }

    private fun generateConversationId(userId1: String, userId2: String, eventId: String): String {
        val usersPart = if (userId1 < userId2) {
            "${userId1}_${userId2}"
        } else {
            "${userId2}_${userId1}"
        }
        return "${usersPart}_${eventId}"
    }

    fun setInMessagesActivity(inMessages: Boolean, conversationId: String? = null) {
        isInMessagesActivity = inMessages
        currentConversationId = conversationId
        Log.d(TAG, "Estado das mensagens alterado: inMessages=$inMessages, conversationId=$conversationId")
    }

    private fun markNotificationAsRead(notificationId: String) {
        db.collection("notifications").document(notificationId)
            .update("isRead", true)
            .addOnSuccessListener {
                Log.d(TAG, "Notificação marcada como lida: $notificationId")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao marcar notificação como lida", e)
            }
    }

    fun stopListening() {
        notificationListener?.remove()
        notificationListener = null
        isInMessagesActivity = false
        currentConversationId = null
        processedNotifications.clear()
        Log.d(TAG, "Listener de notificações parado")
    }

    fun clearProcessedNotifications() {
        processedNotifications.clear()
        Log.d(TAG, "Cache de notificações processadas limpo")
    }
}