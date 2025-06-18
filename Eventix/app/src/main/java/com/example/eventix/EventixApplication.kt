package com.example.eventix

import android.app.Application

class EventixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationService.createNotificationChannel(this)
    }
}