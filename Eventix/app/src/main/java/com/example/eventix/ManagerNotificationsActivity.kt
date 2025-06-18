package com.example.eventix

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManagerNotificationsActivity : AppCompatActivity() {

    private val TAG = "ManagerNotifications"

    private lateinit var btnBack: ImageButton
    private lateinit var switchUserMessages: SwitchCompat

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_manager_notifications)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        RoleManager.isManager { isManager ->
            if (!isManager) {
                redirectToLogin()
                return@isManager
            }
        }

        initializeViews()
        setupClickListeners()
        loadNotificationSettings()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        switchUserMessages = findViewById(R.id.switchUserMessages)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        switchUserMessages.setOnCheckedChangeListener { _, isChecked ->
            updateNotificationSetting("userMessages", isChecked)
        }
    }

    private fun loadNotificationSettings() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("utilizadores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val notifications = document.get("notificationSettings") as? Map<String, Any>
                    if (notifications != null) {
                        switchUserMessages.isChecked = notifications["userMessages"] as? Boolean ?: true
                    } else {
                        setDefaultNotificationSettings()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao carregar configurações de notificação", e)
                setDefaultNotificationSettings()
            }
    }

    private fun setDefaultNotificationSettings() {
        switchUserMessages.isChecked = true

        val defaultSettings = hashMapOf(
            "userMessages" to true
        )

        val userId = auth.currentUser?.uid ?: return
        db.collection("utilizadores").document(userId)
            .update("notificationSettings", defaultSettings)
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao definir configurações padrão", e)
            }
    }

    private fun updateNotificationSetting(setting: String, value: Boolean) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("utilizadores").document(userId)
            .update("notificationSettings.$setting", value)
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao atualizar configuração de notificação", e)
            }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}