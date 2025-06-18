package com.example.eventix

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class GuestsActivity : AppCompatActivity() {

    private val TAG = "GuestsActivity"

    companion object {
        const val MAX_GUESTS_PER_USER = 250
        const val MAX_PENDING_REQUESTS_PER_USER = 50
    }

    private lateinit var etSearchGuests: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnBack: ImageButton

    private lateinit var rvSearchResults: RecyclerView
    private lateinit var rvPendingRequests: RecyclerView
    private lateinit var rvGuests: RecyclerView

    private lateinit var tvPendingCount: TextView
    private lateinit var tvGuestsCount: TextView
    private lateinit var tvNoPendingRequests: TextView
    private lateinit var tvNoGuests: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private lateinit var pendingRequestsAdapter: PendingRequestsAdapter
    private lateinit var guestsAdapter: GuestsAdapter

    private var previousActivity: String = "MainActivity"
    private var currentGuestsCount = 0
    private var currentPendingRequestsCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_guests)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        previousActivity = intent.getStringExtra("from") ?: "MainActivity"

        initializeViews()
        setupClickListeners()
        setupRecyclerViews()
        loadData()
        setupBackPressedHandler()
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })
    }

    private fun initializeViews() {
        etSearchGuests = findViewById(R.id.etSearchGuests)
        btnSearch = findViewById(R.id.btnSearch)
        btnBack = findViewById(R.id.btnBack)

        rvSearchResults = findViewById(R.id.rvSearchResults)
        rvPendingRequests = findViewById(R.id.rvPendingRequests)
        rvGuests = findViewById(R.id.rvGuests)

        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvGuestsCount = findViewById(R.id.tvGuestsCount)
        tvNoPendingRequests = findViewById(R.id.tvNoPendingRequests)
        tvNoGuests = findViewById(R.id.tvNoGuests)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            navigateBack()
        }

        btnSearch.setOnClickListener {
            searchUsers()
        }
    }

    private fun setupRecyclerViews() {
        searchResultsAdapter = SearchResultsAdapter(mutableListOf()) { user ->
            sendGuestRequest(user)
        }
        rvSearchResults.adapter = searchResultsAdapter
        rvSearchResults.layoutManager = LinearLayoutManager(this)

        pendingRequestsAdapter = PendingRequestsAdapter(mutableListOf(),
            onAccept = { request -> acceptGuestRequest(request) },
            onReject = { request -> rejectGuestRequest(request) }
        )
        rvPendingRequests.adapter = pendingRequestsAdapter
        rvPendingRequests.layoutManager = LinearLayoutManager(this)

        guestsAdapter = GuestsAdapter(mutableListOf()) { guest ->
            removeGuest(guest)
        }
        rvGuests.adapter = guestsAdapter
        rvGuests.layoutManager = LinearLayoutManager(this)
    }

    private fun loadData() {
        loadPendingRequests()
        loadGuests()
    }

    private fun searchUsers() {
        val searchTerm = etSearchGuests.text.toString().trim()
        if (searchTerm.isEmpty()) {
            rvSearchResults.visibility = View.GONE
            return
        }

        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("utilizadores")
            .whereGreaterThanOrEqualTo("nome", searchTerm)
            .whereLessThanOrEqualTo("nome", searchTerm + '\uf8ff')
            .get()
            .addOnSuccessListener { documents ->
                val users = mutableListOf<User>()
                for (document in documents) {
                    if (document.id != currentUserId) {
                        val user = User(
                            id = document.id,
                            nome = document.getString("nome") ?: "",
                            email = document.getString("email") ?: ""
                        )
                        users.add(user)
                    }
                }
                searchResultsAdapter.updateUsers(users)
                rvSearchResults.visibility = if (users.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao procurar utilizadores", e)
                rvSearchResults.visibility = View.GONE
            }
    }

    private fun sendGuestRequest(user: User) {
        val currentUser = auth.currentUser ?: return
        val currentUserId = currentUser.uid

        if (currentPendingRequestsCount >= MAX_PENDING_REQUESTS_PER_USER) {
            showAlertDialog(
                "Limite Atingido",
                "Já atingiu o limite máximo de $MAX_PENDING_REQUESTS_PER_USER pedidos pendentes. " +
                        "Aguarde que alguns sejam aceites ou rejeitados antes de enviar novos pedidos."
            )
            return
        }

        checkIfAlreadyGuests(currentUserId, user.id) { areGuests ->
            if (areGuests) {
                showAlertDialog("Informação", "Este utilizador já é seu convidado.")
                return@checkIfAlreadyGuests
            }

            checkExistingRequest(currentUserId, user.id) { hasRequest ->
                if (hasRequest) {
                    showAlertDialog("Informação", "Já enviou um pedido para este utilizador.")
                    return@checkExistingRequest
                }

                checkUserPendingRequestsCount(user.id) { destinatarioCount ->
                    if (destinatarioCount >= MAX_PENDING_REQUESTS_PER_USER) {
                        showAlertDialog(
                            "Limite do Destinatário",
                            "Este utilizador já atingiu o limite máximo de pedidos pendentes. " +
                                    "Tente novamente mais tarde."
                        )
                        return@checkUserPendingRequestsCount
                    }

                    db.collection("utilizadores").document(currentUserId)
                        .get()
                        .addOnSuccessListener { document ->
                            val currentUserName = document.getString("nome") ?: ""
                            val currentUserEmail = document.getString("email") ?: ""

                            val request = hashMapOf(
                                "remetente" to currentUserId,
                                "destinatario" to user.id,
                                "nomeRemetente" to currentUserName,
                                "nomeDestinatario" to user.nome,
                                "emailRemetente" to currentUserEmail,
                                "emailDestinatario" to user.email,
                                "timestamp" to FieldValue.serverTimestamp(),
                                "estado" to "pendente"
                            )

                            db.collection("pedidos_convite")
                                .add(request)
                                .addOnSuccessListener {
                                    showAlertDialog("Sucesso", "Pedido de convite enviado!")
                                    loadPendingRequests()

                                    NotificationManager.sendGuestRequestNotification(
                                        context = this@GuestsActivity,
                                        senderId = currentUserId,
                                        receiverId = user.id,
                                        senderName = currentUserName
                                    )
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Erro ao enviar pedido", e)
                                    showAlertDialog("Erro", "Erro ao enviar pedido de convite.")
                                }
                        }
                }
            }
        }
    }

    private fun checkUserPendingRequestsCount(userId: String, callback: (Int) -> Unit) {
        db.collection("pedidos_convite")
            .whereEqualTo("destinatario", userId)
            .whereEqualTo("estado", "pendente")
            .get()
            .addOnSuccessListener { documents ->
                callback(documents.size())
            }
            .addOnFailureListener {
                callback(0)
            }
    }

    private fun loadPendingRequests() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("pedidos_convite")
            .whereEqualTo("destinatario", currentUserId)
            .whereEqualTo("estado", "pendente")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Erro ao carregar pedidos", e)
                    return@addSnapshotListener
                }

                val requests = mutableListOf<GuestRequest>()
                snapshots?.forEach { document ->
                    val request = GuestRequest(
                        id = document.id,
                        remetente = document.getString("remetente") ?: "",
                        destinatario = document.getString("destinatario") ?: "",
                        nomeRemetente = document.getString("nomeRemetente") ?: "",
                        nomeDestinatario = document.getString("nomeDestinatario") ?: "",
                        emailRemetente = document.getString("emailRemetente") ?: "",
                        emailDestinatario = document.getString("emailDestinatario") ?: "",
                        estado = document.getString("estado") ?: ""
                    )
                    requests.add(request)
                }

                requests.sortByDescending { it.id }

                currentPendingRequestsCount = requests.size
                pendingRequestsAdapter.updateRequests(requests)
                tvPendingCount.text = "$currentPendingRequestsCount/$MAX_PENDING_REQUESTS_PER_USER"
                tvNoPendingRequests.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun loadGuests() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("convidados")
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.equalTo("utilizador1", currentUserId),
                    com.google.firebase.firestore.Filter.equalTo("utilizador2", currentUserId)
                )
            )
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Erro ao carregar convidados", e)
                    return@addSnapshotListener
                }

                val guests = mutableListOf<Guest>()
                snapshots?.forEach { document ->
                    val utilizador1 = document.getString("utilizador1") ?: ""
                    val utilizador2 = document.getString("utilizador2") ?: ""
                    val nomeUtilizador1 = document.getString("nomeUtilizador1") ?: ""
                    val nomeUtilizador2 = document.getString("nomeUtilizador2") ?: ""
                    val emailUtilizador1 = document.getString("emailUtilizador1") ?: ""
                    val emailUtilizador2 = document.getString("emailUtilizador2") ?: ""

                    val guest = if (utilizador1 == currentUserId) {
                        Guest(
                            id = document.id,
                            userId = utilizador2,
                            nome = nomeUtilizador2,
                            email = emailUtilizador2
                        )
                    } else {
                        Guest(
                            id = document.id,
                            userId = utilizador1,
                            nome = nomeUtilizador1,
                            email = emailUtilizador1
                        )
                    }
                    guests.add(guest)
                }

                guests.sortByDescending { it.id }

                currentGuestsCount = guests.size
                guestsAdapter.updateGuests(guests)
                tvGuestsCount.text = "$currentGuestsCount/$MAX_GUESTS_PER_USER"
                tvNoGuests.visibility = if (guests.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun acceptGuestRequest(request: GuestRequest) {
        if (currentGuestsCount >= MAX_GUESTS_PER_USER) {
            showAlertDialog(
                "Limite Atingido",
                "Já atingiu o limite máximo de $MAX_GUESTS_PER_USER convidados. " +
                        "Remova alguns convidados antes de aceitar novos pedidos."
            )
            return
        }

        checkUserGuestsCount(request.remetente) { remetenteCount ->
            if (remetenteCount >= MAX_GUESTS_PER_USER) {
                showAlertDialog(
                    "Limite do Remetente",
                    "O utilizador que enviou o pedido já atingiu o limite máximo de convidados. " +
                            "O pedido será rejeitado automaticamente."
                )
                rejectGuestRequest(request)
                return@checkUserGuestsCount
            }

            checkIfAlreadyGuests(request.remetente, request.destinatario) { areGuests ->
                if (areGuests) {
                    showAlertDialog("Informação", "Este utilizador já é seu convidado.")
                    return@checkIfAlreadyGuests
                }

                db.collection("pedidos_convite").document(request.id)
                    .update("estado", "aceite")
                    .addOnSuccessListener {
                        val guestConnection = hashMapOf(
                            "utilizador1" to request.remetente,
                            "utilizador2" to request.destinatario,
                            "nomeUtilizador1" to request.nomeRemetente,
                            "nomeUtilizador2" to request.nomeDestinatario,
                            "emailUtilizador1" to request.emailRemetente,
                            "emailUtilizador2" to request.emailDestinatario,
                            "timestamp" to FieldValue.serverTimestamp()
                        )

                        db.collection("convidados")
                            .add(guestConnection)
                            .addOnSuccessListener {
                                Log.d(TAG, "Pedido aceite com sucesso")
                                reloadPendingRequests()
                                reloadGuests()
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Erro ao criar ligação de convidados", e)
                                showAlertDialog("Erro", "Erro ao aceitar pedido de convite.")
                                db.collection("pedidos_convite").document(request.id)
                                    .update("estado", "pendente")
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Erro ao atualizar estado do pedido", e)
                        showAlertDialog("Erro", "Erro ao aceitar pedido de convite.")
                    }
            }
        }
    }

    private fun checkUserGuestsCount(userId: String, callback: (Int) -> Unit) {
        db.collection("convidados")
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.equalTo("utilizador1", userId),
                    com.google.firebase.firestore.Filter.equalTo("utilizador2", userId)
                )
            )
            .get()
            .addOnSuccessListener { documents ->
                callback(documents.size())
            }
            .addOnFailureListener {
                callback(0)
            }
    }

    private fun rejectGuestRequest(request: GuestRequest) {
        db.collection("pedidos_convite").document(request.id)
            .update("estado", "rejeitado")
            .addOnSuccessListener {
                Log.d(TAG, "Pedido rejeitado com sucesso")
                reloadPendingRequests()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao rejeitar pedido", e)
                showAlertDialog("Erro", "Erro ao rejeitar pedido de convite.")
            }
    }

    private fun removeGuest(guest: Guest) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Remover Convidado")
        builder.setMessage("Tem a certeza que deseja remover ${guest.nome} dos seus convidados?")
        builder.setPositiveButton("Remover") { _, _ ->
            db.collection("convidados").document(guest.id)
                .delete()
                .addOnSuccessListener {
                    Log.d(TAG, "Convidado removido com sucesso")
                    reloadGuests()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Erro ao remover convidado", e)
                    showAlertDialog("Erro", "Erro ao remover convidado.")
                }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun reloadPendingRequests() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("pedidos_convite")
            .whereEqualTo("destinatario", currentUserId)
            .whereEqualTo("estado", "pendente")
            .get()
            .addOnSuccessListener { snapshots ->
                val requests = mutableListOf<GuestRequest>()
                snapshots?.forEach { document ->
                    val request = GuestRequest(
                        id = document.id,
                        remetente = document.getString("remetente") ?: "",
                        destinatario = document.getString("destinatario") ?: "",
                        nomeRemetente = document.getString("nomeRemetente") ?: "",
                        nomeDestinatario = document.getString("nomeDestinatario") ?: "",
                        emailRemetente = document.getString("emailRemetente") ?: "",
                        emailDestinatario = document.getString("emailDestinatario") ?: "",
                        estado = document.getString("estado") ?: ""
                    )
                    requests.add(request)
                }

                requests.sortByDescending { it.id }

                currentPendingRequestsCount = requests.size
                pendingRequestsAdapter.updateRequests(requests)
                tvPendingCount.text = "$currentPendingRequestsCount/$MAX_PENDING_REQUESTS_PER_USER"
                tvNoPendingRequests.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun reloadGuests() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("convidados")
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.equalTo("utilizador1", currentUserId),
                    com.google.firebase.firestore.Filter.equalTo("utilizador2", currentUserId)
                )
            )
            .get()
            .addOnSuccessListener { snapshots ->
                val guests = mutableListOf<Guest>()
                snapshots?.forEach { document ->
                    val utilizador1 = document.getString("utilizador1") ?: ""
                    val utilizador2 = document.getString("utilizador2") ?: ""
                    val nomeUtilizador1 = document.getString("nomeUtilizador1") ?: ""
                    val nomeUtilizador2 = document.getString("nomeUtilizador2") ?: ""
                    val emailUtilizador1 = document.getString("emailUtilizador1") ?: ""
                    val emailUtilizador2 = document.getString("emailUtilizador2") ?: ""

                    val guest = if (utilizador1 == currentUserId) {
                        Guest(
                            id = document.id,
                            userId = utilizador2,
                            nome = nomeUtilizador2,
                            email = emailUtilizador2
                        )
                    } else {
                        Guest(
                            id = document.id,
                            userId = utilizador1,
                            nome = nomeUtilizador1,
                            email = emailUtilizador1
                        )
                    }
                    guests.add(guest)
                }

                guests.sortByDescending { it.id }

                currentGuestsCount = guests.size
                guestsAdapter.updateGuests(guests)
                tvGuestsCount.text = "$currentGuestsCount/$MAX_GUESTS_PER_USER"
                tvNoGuests.visibility = if (guests.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun checkIfAlreadyGuests(userId1: String, userId2: String, callback: (Boolean) -> Unit) {
        db.collection("convidados")
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.and(
                        com.google.firebase.firestore.Filter.equalTo("utilizador1", userId1),
                        com.google.firebase.firestore.Filter.equalTo("utilizador2", userId2)
                    ),
                    com.google.firebase.firestore.Filter.and(
                        com.google.firebase.firestore.Filter.equalTo("utilizador1", userId2),
                        com.google.firebase.firestore.Filter.equalTo("utilizador2", userId1)
                    )
                )
            )
            .get()
            .addOnSuccessListener { documents ->
                callback(!documents.isEmpty)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    private fun checkExistingRequest(senderId: String, receiverId: String, callback: (Boolean) -> Unit) {
        db.collection("pedidos_convite")
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.and(
                        com.google.firebase.firestore.Filter.equalTo("remetente", senderId),
                        com.google.firebase.firestore.Filter.equalTo("destinatario", receiverId),
                        com.google.firebase.firestore.Filter.equalTo("estado", "pendente")
                    ),
                    com.google.firebase.firestore.Filter.and(
                        com.google.firebase.firestore.Filter.equalTo("remetente", receiverId),
                        com.google.firebase.firestore.Filter.equalTo("destinatario", senderId),
                        com.google.firebase.firestore.Filter.equalTo("estado", "pendente")
                    )
                )
            )
            .get()
            .addOnSuccessListener { documents ->
                callback(!documents.isEmpty)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    private fun showAlertDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK", null)
        builder.show()
    }

    private fun navigateBack() {
        when (previousActivity) {
            "MainActivity" -> {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
            "ProfileActivity" -> {
                val intent = Intent(this, ProfileActivity::class.java)
                startActivity(intent)
            }
            "CreateEventActivity" -> {
                val intent = Intent(this, CreateEventActivity::class.java)
                startActivity(intent)
            }
            "CalendarActivity" -> {
                val intent = Intent(this, CalendarActivity::class.java)
                startActivity(intent)
            }
            "EventDetailsActivity" -> {
                val intent = Intent(this, EventDetailsActivity::class.java)
                startActivity(intent)
            }
        }
        finish()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}