package com.example.eventix

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class MessagesActivity : AppCompatActivity() {

    private val TAG = "MessagesActivity"

    private lateinit var btnBack: ImageButton
    private lateinit var tvOtherUserName: TextView
    private lateinit var tvEventInfo: TextView
    private lateinit var rvMessages: RecyclerView
    private lateinit var tvNoMessages: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var messageAdapter: MessageAdapter

    private var eventId: String = ""
    private var otherUserId: String = ""
    private var userRole: String = ""
    private var conversationId: String = ""
    private var messagesListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_messages)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            finish()
            return
        }

        eventId = intent.getStringExtra("EVENT_ID") ?: ""
        otherUserId = intent.getStringExtra("OTHER_USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: ""

        if (eventId.isEmpty() || otherUserId.isEmpty()) {
            finish()
            return
        }

        conversationId = generateConversationId(auth.currentUser!!.uid, otherUserId, eventId)

        initializeViews()
        setupClickListeners()
        setupRecyclerView()
        loadOtherUserInfo()
        loadEventInfo()
        loadMessages()
        createConversationIfNotExists()

        NotificationListener.setInMessagesActivity(true, conversationId)
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        tvOtherUserName = findViewById(R.id.tvOtherUserName)
        tvEventInfo = findViewById(R.id.tvEventInfo)
        rvMessages = findViewById(R.id.rvMessages)
        tvNoMessages = findViewById(R.id.tvNoMessages)
        progressBar = findViewById(R.id.progressBar)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            sendMessage()
        }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(mutableListOf(), auth.currentUser!!.uid)
        rvMessages.adapter = messageAdapter

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        rvMessages.layoutManager = layoutManager
    }

    private fun generateConversationId(userId1: String, userId2: String, eventId: String): String {
        val usersPart = if (userId1 < userId2) {
            "${userId1}_${userId2}"
        } else {
            "${userId2}_${userId1}"
        }
        return "${usersPart}_${eventId}"
    }

    private fun loadOtherUserInfo() {
        db.collection("utilizadores").document(otherUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("nome") ?: "Utilizador"
                    tvOtherUserName.text = name
                } else {
                    tvOtherUserName.text = "Utilizador"
                }
            }
            .addOnFailureListener {
                tvOtherUserName.text = "Utilizador"
            }
    }

    private fun loadEventInfo() {
        db.collection("eventos").document(eventId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val eventType = document.getString("tipoEvento") ?: "Evento"
                    val eventDate = document.getString("data") ?: ""
                    tvEventInfo.text = "Evento: $eventType ($eventDate)"
                } else {
                    tvEventInfo.text = "Evento"
                }
            }
            .addOnFailureListener {
                tvEventInfo.text = "Evento"
            }
    }

    private fun createConversationIfNotExists() {
        val conversationRef = db.collection("conversas").document(conversationId)

        conversationRef.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    val conversation = hashMapOf(
                        "eventId" to eventId,
                        "participants" to listOf(auth.currentUser!!.uid, otherUserId),
                        "lastMessage" to "",
                        "lastMessageTime" to null,
                        "lastMessageSender" to ""
                    )

                    conversationRef.set(conversation)
                        .addOnSuccessListener {
                            Log.d(TAG, "Conversa criada: $conversationId")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Erro ao criar conversa", e)
                        }
                }
            }
    }

    private fun loadMessages() {
        progressBar.visibility = View.VISIBLE

        messagesListener = db.collection("conversas")
            .document(conversationId)
            .collection("mensagens")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                progressBar.visibility = View.GONE

                if (e != null) {
                    Log.w(TAG, "Erro ao carregar mensagens", e)
                    return@addSnapshotListener
                }

                val messages = mutableListOf<Message>()
                snapshots?.forEach { document ->
                    val message = Message(
                        id = document.id,
                        senderId = document.getString("senderId") ?: "",
                        receiverId = document.getString("receiverId") ?: "",
                        message = document.getString("message") ?: "",
                        timestamp = document.getTimestamp("timestamp"),
                        read = document.getBoolean("read") ?: false
                    )
                    messages.add(message)
                }

                messageAdapter.updateMessages(messages)

                if (messages.isEmpty()) {
                    tvNoMessages.visibility = View.VISIBLE
                    rvMessages.visibility = View.GONE
                } else {
                    tvNoMessages.visibility = View.GONE
                    rvMessages.visibility = View.VISIBLE
                    rvMessages.scrollToPosition(messages.size - 1)
                }

                markMessagesAsRead(messages)
            }
    }

    private fun markMessagesAsRead(messages: List<Message>) {
        val currentUserId = auth.currentUser?.uid ?: return
        val batch = db.batch()
        var hasUnreadMessages = false

        messages.forEach { message ->
            if (message.receiverId == currentUserId && !message.read) {
                val messageRef = db.collection("conversas")
                    .document(conversationId)
                    .collection("mensagens")
                    .document(message.id)

                batch.update(messageRef, "read", true)
                hasUnreadMessages = true
            }
        }

        if (hasUnreadMessages) {
            batch.commit()
                .addOnSuccessListener {
                    Log.d(TAG, "Mensagens marcadas como lidas")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Erro ao marcar mensagens como lidas", e)
                }
        }
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) return

        val currentUserId = auth.currentUser?.uid ?: return

        btnSend.isEnabled = false
        etMessage.isEnabled = false

        val messageData = hashMapOf(
            "senderId" to currentUserId,
            "receiverId" to otherUserId,
            "message" to messageText,
            "timestamp" to Timestamp.now(),
            "read" to false
        )

        db.collection("conversas")
            .document(conversationId)
            .collection("mensagens")
            .add(messageData)
            .addOnSuccessListener { documentRef ->
                updateLastMessage(messageText, currentUserId)

                db.collection("utilizadores").document(currentUserId)
                    .get()
                    .addOnSuccessListener { document ->
                        val senderName = document.getString("nome") ?: "Utilizador"

                        NotificationManager.sendMessageNotification(
                            context = this@MessagesActivity,
                            senderId = currentUserId,
                            receiverId = otherUserId,
                            senderName = senderName,
                            eventId = eventId
                        )
                    }

                etMessage.text.clear()
                btnSend.isEnabled = true
                etMessage.isEnabled = true
            }
            .addOnFailureListener { e ->
                btnSend.isEnabled = true
                etMessage.isEnabled = true
            }
    }

    private fun updateLastMessage(message: String, senderId: String) {
        val conversationRef = db.collection("conversas").document(conversationId)

        val updates = hashMapOf<String, Any>(
            "lastMessage" to message,
            "lastMessageTime" to Timestamp.now(),
            "lastMessageSender" to senderId
        )

        conversationRef.update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Última mensagem atualizada")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao atualizar última mensagem", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
        NotificationListener.setInMessagesActivity(false)
    }

    override fun onPause() {
        super.onPause()
        NotificationListener.setInMessagesActivity(false)
    }

    override fun onResume() {
        super.onResume()
        NotificationListener.setInMessagesActivity(true, conversationId)
    }
}