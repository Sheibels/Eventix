package com.example.eventix

import com.google.firebase.Timestamp

data class NotificationItem(
    val id: String = "",
    val userId: String = "",
    val type: NotificationType = NotificationType.GUEST_REQUEST,
    val title: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null,
    val isRead: Boolean = false,
    val data: Map<String, Any> = emptyMap()
)

enum class NotificationType(val value: String) {
    GUEST_REQUEST("guest_request"),
    EVENT_REMINDER_WEEK("event_reminder_week"),
    EVENT_REMINDER_DAY("event_reminder_day"),
    MANAGER_MESSAGE("manager_message"),
    USER_MESSAGE("user_message")
}

data class NotificationSettings(
    val guestRequests: Boolean = true,
    val weekReminder: Boolean = true,
    val dayReminder: Boolean = true,
    val managerMessages: Boolean = true,
    val userMessages: Boolean = true
)