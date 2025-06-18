package com.example.eventix

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NotificationsActivity : AppCompatActivity() {

    private val TAG = "NotificationsActivity"

    private lateinit var btnBack: ImageButton
    private lateinit var switchGuestRequests: SwitchCompat
    private lateinit var switchWeekReminder: SwitchCompat
    private lateinit var switchDayReminder: SwitchCompat
    private lateinit var switchManagerMessages: SwitchCompat

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_notifications)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        initializeViews()
        setupClickListeners()
        loadNotificationSettings()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        switchGuestRequests = findViewById(R.id.switchGuestRequests)
        switchWeekReminder = findViewById(R.id.switchWeekReminder)
        switchDayReminder = findViewById(R.id.switchDayReminder)
        switchManagerMessages = findViewById(R.id.switchManagerMessages)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        switchGuestRequests.setOnCheckedChangeListener { _, isChecked ->
            updateNotificationSetting("guestRequests", isChecked)
        }

        switchWeekReminder.setOnCheckedChangeListener { _, isChecked ->
            updateNotificationSetting("weekReminder", isChecked)
        }

        switchDayReminder.setOnCheckedChangeListener { _, isChecked ->
            updateNotificationSetting("dayReminder", isChecked)
        }

        switchManagerMessages.setOnCheckedChangeListener { _, isChecked ->
            updateNotificationSetting("managerMessages", isChecked)
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
                        switchGuestRequests.isChecked = notifications["guestRequests"] as? Boolean ?: true
                        switchWeekReminder.isChecked = notifications["weekReminder"] as? Boolean ?: true
                        switchDayReminder.isChecked = notifications["dayReminder"] as? Boolean ?: true
                        switchManagerMessages.isChecked = notifications["managerMessages"] as? Boolean ?: true
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
        switchGuestRequests.isChecked = true
        switchWeekReminder.isChecked = true
        switchDayReminder.isChecked = true
        switchManagerMessages.isChecked = true

        val defaultSettings = hashMapOf(
            "guestRequests" to true,
            "weekReminder" to true,
            "dayReminder" to true,
            "managerMessages" to true
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
        val intent = android.content.Intent(this, LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}