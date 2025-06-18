package com.example.eventix

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class EventDetailsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "EventDetailsActivity"
    private val EDIT_EVENT_REQUEST = 1001

    private lateinit var btnMenu: ImageButton
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private lateinit var btnMeusEventos: Button
    private lateinit var btnEventosOutros: Button
    private lateinit var spinnerEstado: Spinner
    private lateinit var rvEvents: RecyclerView
    private lateinit var tvTotalEvents: TextView
    private lateinit var tvNoEvents: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEstadoLabel: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var eventsAdapter: EventsAdapter

    private val allEvents = mutableListOf<EventDetail>()
    private val filteredEvents = mutableListOf<EventDetail>()
    private var currentFilterStatus = EventStatus.ALL
    private var currentOwnershipFilter = EventOwnershipFilter.MY_EVENTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_event_details)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        initializeViews()
        setupClickListeners()
        setupBottomNavigation()
        setupDrawerNavigation()
        setupRecyclerView()
        setupFilters()
        loadEvents()

        val eventId = intent.getStringExtra("EVENT_ID")
        val shouldScrollToEvent = intent.getBooleanExtra("SCROLL_TO_EVENT", false)
        if (eventId != null && shouldScrollToEvent) {
        }
    }

    private fun initializeViews() {
        btnMenu = findViewById(R.id.btnMenu)
        fabAdd = findViewById(R.id.fabAdd)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        btnMeusEventos = findViewById(R.id.btnMeusEventos)
        btnEventosOutros = findViewById(R.id.btnEventosOutros)
        spinnerEstado = findViewById(R.id.spinnerEstado)
        rvEvents = findViewById(R.id.rvEvents)
        tvTotalEvents = findViewById(R.id.tvTotalEvents)
        tvNoEvents = findViewById(R.id.tvNoEvents)
        progressBar = findViewById(R.id.progressBar)
        tvEstadoLabel = findViewById(R.id.tvEstadoLabel)
    }

    private fun setupDrawerNavigation() {
        navigationView.setNavigationItemSelectedListener(this)

        btnMenu.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                drawerLayout.openDrawer(GravityCompat.END)
            }
        }
    }

    private fun setupClickListeners() {
        fabAdd.setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivity(intent)
        }

        btnMeusEventos.setOnClickListener {
            selectOwnershipFilter(EventOwnershipFilter.MY_EVENTS)
        }

        btnEventosOutros.setOnClickListener {
            selectOwnershipFilter(EventOwnershipFilter.OTHERS_EVENTS)
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.navigation_events

        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_events -> {
                    true
                }
                R.id.navigation_calendar -> {
                    val intent = Intent(this, CalendarActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        eventsAdapter = EventsAdapter(
            filteredEvents,
            onEditClick = { event -> editEvent(event) },
            onDeleteClick = { event -> deleteEvent(event) },
            onMessageClick = { event -> messageManager(event) },
            onEventClick = { event -> viewEventDetails(event) },
            onStatusChangeClick = { event, newStatus -> changeEventStatus(event, newStatus) }
        )
        rvEvents.adapter = eventsAdapter
        rvEvents.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFilters() {
        selectOwnershipFilter(EventOwnershipFilter.MY_EVENTS)

        val statusOptions = EventStatus.values().map { it.displayName }
        val statusAdapter = ArrayAdapter(this, R.layout.spinner_item_category, statusOptions)
        statusAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_radius)
        spinnerEstado.adapter = statusAdapter

        spinnerEstado.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilterStatus = EventStatus.values()[position]
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun selectOwnershipFilter(filter: EventOwnershipFilter) {
        currentOwnershipFilter = filter

        when (filter) {
            EventOwnershipFilter.MY_EVENTS -> {
                btnMeusEventos.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brown)
                btnMeusEventos.setTextColor(ContextCompat.getColor(this, R.color.white))

                btnEventosOutros.backgroundTintList = ContextCompat.getColorStateList(this, R.color.light_gray)
                btnEventosOutros.setTextColor(ContextCompat.getColor(this, R.color.black))

                spinnerEstado.visibility = View.VISIBLE
                tvEstadoLabel.visibility = View.VISIBLE
            }
            EventOwnershipFilter.OTHERS_EVENTS -> {
                btnEventosOutros.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brown)
                btnEventosOutros.setTextColor(ContextCompat.getColor(this, R.color.white))

                btnMeusEventos.backgroundTintList = ContextCompat.getColorStateList(this, R.color.light_gray)
                btnMeusEventos.setTextColor(ContextCompat.getColor(this, R.color.black))

                spinnerEstado.visibility = View.GONE
                tvEstadoLabel.visibility = View.GONE
            }
        }

        applyFilters()
    }

    private fun loadEvents() {
        val currentUserId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        Log.d(TAG, "A carregar eventos para utilizador: $currentUserId")

        db.collection("eventos")
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                allEvents.clear()

                Log.d(TAG, "Total de eventos encontrados: ${documents.size()}")

                val eventsList = mutableListOf<EventDetail>()

                documents.forEach { document ->
                    val criador = document.getString("criador") ?: ""
                    val convidados = document.get("convidados") as? List<String> ?: emptyList()
                    val estado = document.getString("estado") ?: "ativo"

                    val isCreator = criador == currentUserId
                    val isInvited = convidados.contains(currentUserId)

                    val estadosVisiveis = listOf("ativo", "confirmado", "concluido", "cancelado")

                    if ((isCreator || isInvited) && estadosVisiveis.contains(estado)) {
                        val event = EventDetail(
                            id = document.id,
                            tipoEvento = document.getString("tipoEvento") ?: "",
                            data = document.getString("data") ?: "",
                            hora = document.getString("hora") ?: "",
                            localizacao = document.getString("localizacao") ?: "",
                            numeroParticipantes = document.getLong("numeroParticipantes")?.toInt() ?: 0,
                            convidados = convidados,
                            custoTotal = document.getDouble("custoTotal") ?: 0.0,
                            estado = estado,
                            criador = criador,
                            timestamp = document.getTimestamp("timestamp"),
                            servicos = document.get("servicos") as? List<String> ?: emptyList(),
                            servicosIds = document.get("servicosIds") as? List<String> ?: emptyList(),
                            nomesParticipantes = document.get("nomesParticipantes") as? List<String> ?: emptyList(),
                            localizacaoDetalhes = document.get("localizacaoDetalhes") as? Map<String, Any>,
                            isCreator = isCreator,
                            gestorAssociado = document.getString("gestorAssociado"),
                            isAssociatedToManager = false
                        )
                        eventsList.add(event)

                        Log.d(TAG, "Evento adicionado: ${event.id} - Papel: ${if (isCreator) "Criador" else "Convidado"} - Estado: ${event.estado}")
                    }
                }

                eventsList.sortByDescending { it.timestamp?.toDate() }
                allEvents.addAll(eventsList)

                Log.d(TAG, "Eventos carregados: ${allEvents.size}")
                applyFilters()

                val eventId = intent.getStringExtra("EVENT_ID")
                val shouldScrollToEvent = intent.getBooleanExtra("SCROLL_TO_EVENT", false)
                if (eventId != null && shouldScrollToEvent) {
                    eventsAdapter.scrollToEvent(eventId)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.w(TAG, "Erro ao carregar eventos", e)
            }
    }

    private fun applyFilters() {
        filteredEvents.clear()

        var eventsToFilter = allEvents.toList()

        when (currentOwnershipFilter) {
            EventOwnershipFilter.MY_EVENTS -> {
                eventsToFilter = eventsToFilter.filter { it.isCreator }
            }
            EventOwnershipFilter.OTHERS_EVENTS -> {
                eventsToFilter = eventsToFilter.filter { !it.isCreator }
            }
        }

        if (currentOwnershipFilter == EventOwnershipFilter.MY_EVENTS) {
            when (currentFilterStatus) {
                EventStatus.ALL -> {
                }
                else -> {
                    eventsToFilter = eventsToFilter.filter { it.getStatusEnum() == currentFilterStatus }
                }
            }
        }

        filteredEvents.addAll(eventsToFilter)
        eventsAdapter.notifyDataSetChanged()
        updateUI()
    }

    private fun updateUI() {
        val count = filteredEvents.size
        tvTotalEvents.text = if (count == 1) {
            "1 evento"
        } else {
            "$count eventos"
        }

        if (filteredEvents.isEmpty()) {
            tvNoEvents.visibility = View.VISIBLE
            rvEvents.visibility = View.GONE

            tvNoEvents.text = when {
                currentOwnershipFilter == EventOwnershipFilter.MY_EVENTS && currentFilterStatus == EventStatus.ALL ->
                    "Não criou nenhum evento ainda"
                currentOwnershipFilter == EventOwnershipFilter.OTHERS_EVENTS ->
                    "Não foi convidado para nenhum evento"
                currentFilterStatus != EventStatus.ALL ->
                    "Não tem eventos ${currentFilterStatus.displayName.lowercase()}"
                else -> "Não tem eventos"
            }
        } else {
            tvNoEvents.visibility = View.GONE
            rvEvents.visibility = View.VISIBLE
        }
    }

    private fun editEvent(event: EventDetail) {
        if (!event.canEdit()) {
            val message = if (!event.isCreator) {
                "Apenas o criador do evento pode editá-lo."
            } else {
                "Este evento não pode ser editado pois já foi confirmado."
            }
            showAlertDialog("Não é possível editar", message)
            return
        }

        val intent = Intent(this, EditEventActivity::class.java)
        intent.putExtra("EVENT_ID", event.id)
        startActivityForResult(intent, EDIT_EVENT_REQUEST)
    }

    private fun deleteEvent(event: EventDetail) {
        if (!event.canDelete()) {
            val message = if (!event.isCreator) {
                "Apenas o criador do evento pode eliminá-lo."
            } else {
                "Este evento não pode ser eliminado pois já foi confirmado."
            }
            showAlertDialog("Não é possível eliminar", message)
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Eliminar Evento")
        builder.setMessage("Tem a certeza que deseja eliminar o evento '${event.tipoEvento}'?\n\nEsta ação não pode ser desfeita.")

        builder.setPositiveButton("Eliminar") { _, _ ->
            performDeleteEvent(event)
        }

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun performDeleteEvent(event: EventDetail) {
        progressBar.visibility = View.VISIBLE

        db.collection("eventos").document(event.id)
            .delete()
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Log.d(TAG, "Evento eliminado com sucesso: ${event.id}")

                val wasActive = isEventActive(event.getStatusEnum())
                if (wasActive) {
                    updateUserEventStats(event.criador, decrementActive = true)
                }

                allEvents.removeAll { it.id == event.id }
                applyFilters()

                showAlertDialog("Sucesso", "Evento eliminado com sucesso!")
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.w(TAG, "Erro ao eliminar evento", e)
                showAlertDialog("Erro", "Erro ao eliminar evento. Tente novamente.")
            }
    }

    private fun messageManager(event: EventDetail) {
        if (event.gestorAssociado.isNullOrEmpty()) {
            showAlertDialog("Sem Gestor Associado", "Este evento ainda não tem um gestor associado.")
            return
        }

        val intent = Intent(this, MessagesActivity::class.java)
        intent.putExtra("EVENT_ID", event.id)
        intent.putExtra("OTHER_USER_ID", event.gestorAssociado)
        intent.putExtra("USER_ROLE", "creator")
        startActivity(intent)
    }

    private fun viewEventDetails(event: EventDetail) {
        if (!event.isCreator) {
            return
        }

        db.collection("eventos").document(event.id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val eventDetail = EventDetail(
                        id = document.id,
                        tipoEvento = document.getString("tipoEvento") ?: "",
                        data = document.getString("data") ?: "",
                        hora = document.getString("hora") ?: "",
                        localizacao = document.getString("localizacao") ?: "",
                        numeroParticipantes = document.getLong("numeroParticipantes")?.toInt() ?: 0,
                        convidados = document.get("convidados") as? List<String> ?: emptyList(),
                        custoTotal = document.getDouble("custoTotal") ?: 0.0,
                        estado = document.getString("estado") ?: "ativo",
                        criador = document.getString("criador") ?: "",
                        timestamp = document.getTimestamp("timestamp"),
                        servicos = document.get("servicos") as? List<String> ?: emptyList(),
                        servicosIds = document.get("servicosIds") as? List<String> ?: emptyList(),
                        nomesParticipantes = document.get("nomesParticipantes") as? List<String> ?: emptyList(),
                        localizacaoDetalhes = document.get("localizacaoDetalhes") as? Map<String, Any>,
                        isCreator = event.isCreator,
                        gestorAssociado = document.getString("gestorAssociado"),
                        isAssociatedToManager = false
                    )

                    mostrarDetalhesEvento(eventDetail)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao carregar detalhes do evento", e)
            }
    }

    private fun mostrarDetalhesEvento(event: EventDetail) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_event_details, null)

        try {
            dialogView.findViewById<TextView>(R.id.tvEventType)?.text = event.tipoEvento
            dialogView.findViewById<TextView>(R.id.tvEventDate)?.text = event.data
            dialogView.findViewById<TextView>(R.id.tvEventTime)?.text = event.hora
            dialogView.findViewById<TextView>(R.id.tvEventLocation)?.text = event.localizacao
            dialogView.findViewById<TextView>(R.id.tvEventParticipants)?.text = event.getParticipantsText()

            val statusTextView = dialogView.findViewById<TextView>(R.id.tvEventStatus)
            if (statusTextView != null) {
                statusTextView.text = event.getStatusEnum().displayName

                val statusBackground = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(android.graphics.Color.parseColor(event.getStatusEnum().colorResource))
                    cornerRadius = 20f
                }
                statusTextView.background = statusBackground
            }

            val cardCost = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardEventCost)
            if (event.isCreator && cardCost != null) {
                cardCost.visibility = View.VISIBLE
                dialogView.findViewById<TextView>(R.id.tvEventCost)?.text = event.getFormattedCost()
            } else {
                cardCost?.visibility = View.GONE
            }

            val cardManagerInfo = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardManagerInfo)
            if (!event.gestorAssociado.isNullOrEmpty() && cardManagerInfo != null) {
                cardManagerInfo.visibility = View.VISIBLE
                loadManagerInfoForDialog(event.gestorAssociado!!, dialogView)
                Log.d(TAG, "Mostrando informações do gestor: ${event.gestorAssociado}")
            } else {
                cardManagerInfo?.visibility = View.GONE
                Log.d(TAG, "Gestor associado: ${event.gestorAssociado}")
            }

            carregarConvidados(event, dialogView)

            val servicesList = dialogView.findViewById<TextView>(R.id.tvServicesList)
            if (servicesList != null) {
                if (event.servicos.isNotEmpty()) {
                    servicesList.text = event.servicos.joinToString("\n") { "• $it" }
                } else {
                    servicesList.text = "Nenhum serviço selecionado"
                }
            }

            val participantsList = dialogView.findViewById<TextView>(R.id.tvParticipantsList)
            if (participantsList != null) {
                if (event.nomesParticipantes.isNotEmpty()) {
                    participantsList.text = event.nomesParticipantes.joinToString("\n") { "• $it" }
                } else {
                    participantsList.text = "Nenhum participante listado"
                }
            }

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()

            val btnClose = dialogView.findViewById<android.widget.Button>(R.id.btnClose)
            btnClose?.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar diálogo de detalhes do evento", e)
            showSimpleEventDialog(event)
        }
    }

    private fun showSimpleEventDialog(event: EventDetail) {
        val message = """
        Tipo: ${event.tipoEvento}
        Data: ${event.data}
        Hora: ${event.hora}
        Local: ${event.localizacao}
        Participantes: ${event.getParticipantsText()}
        Papel: ${event.getEventRole()}
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Detalhes do Evento")
            .setMessage(message)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun loadManagerInfoForDialog(managerId: String, dialogView: View) {
        val tvManagerName = dialogView.findViewById<TextView>(R.id.tvManagerName)
        val tvManagerEmail = dialogView.findViewById<TextView>(R.id.tvManagerEmail)

        tvManagerName?.text = "A carregar..."
        tvManagerEmail?.text = "A carregar..."

        db.collection("utilizadores").document(managerId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val managerName = document.getString("nome") ?: "Gestor Desconhecido"
                    val managerEmail = document.getString("email") ?: "Email não disponível"

                    tvManagerName?.text = managerName
                    tvManagerEmail?.text = managerEmail

                    Log.d(TAG, "Informações do gestor carregadas: $managerName, $managerEmail")
                } else {
                    tvManagerName?.text = "Gestor não encontrado"
                    tvManagerEmail?.text = "Email não disponível"
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao carregar informações do gestor", e)
                tvManagerName?.text = "Erro ao carregar"
                tvManagerEmail?.text = "Erro ao carregar"
            }
    }

    private fun carregarConvidados(event: EventDetail, dialogView: View) {
        val tvGuestsList = dialogView.findViewById<TextView>(R.id.tvGuestsList)
        val tvGuestsCount = dialogView.findViewById<TextView>(R.id.tvGuestsCount)
        val cardGuests = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardGuests)

        if (event.convidados.isEmpty()) {
            cardGuests.visibility = View.GONE
            return
        }

        tvGuestsCount.text = "(${event.convidados.size})"
        tvGuestsList.text = "A carregar..."

        val guestNames = mutableListOf<String>()
        var processedCount = 0

        event.convidados.forEach { guestId ->
            db.collection("utilizadores").document(guestId)
                .get()
                .addOnSuccessListener { document ->
                    val guestName = document.getString("nome") ?: "Utilizador Desconhecido"
                    guestNames.add(guestName)
                    processedCount++

                    if (processedCount == event.convidados.size) {
                        tvGuestsList.text = guestNames.joinToString("\n") { "• $it" }
                    }
                }
                .addOnFailureListener {
                    guestNames.add("Utilizador Desconhecido")
                    processedCount++

                    if (processedCount == event.convidados.size) {
                        tvGuestsList.text = guestNames.joinToString("\n") { "• $it" }
                    }
                }
        }
    }

    private fun changeEventStatus(event: EventDetail, newStatus: EventStatus) {
        progressBar.visibility = View.VISIBLE

        EventStatusManager.updateEventStatus(
            eventId = event.id,
            newStatus = newStatus,
            creatorId = event.criador,
            onSuccess = {
                progressBar.visibility = View.GONE

                loadEvents()

                showAlertDialog("Sucesso", "Estado do evento alterado para '${newStatus.displayName}' com sucesso!")
            },
            onFailure = { exception ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Erro ao alterar estado do evento", exception)
                showAlertDialog("Erro", "Erro ao alterar estado do evento. Tente novamente.")
            }
        )
    }

    private fun isEventActive(status: EventStatus): Boolean {
        return status == EventStatus.PENDING || status == EventStatus.CONFIRMED
    }

    private fun updateUserEventStats(
        userId: String,
        incrementCreated: Boolean = false,
        incrementActive: Boolean = false,
        decrementActive: Boolean = false
    ) {
        EventStatusManager.updateUserEventStats(
            userId = userId,
            incrementCreated = incrementCreated,
            incrementActive = incrementActive,
            decrementActive = decrementActive
        )
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_notifications -> {
                val intent = Intent(this, NotificationsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_guests -> {
                val intent = Intent(this, GuestsActivity::class.java)
                intent.putExtra("from", "EventDetailsActivity")
                startActivity(intent)
            }
            R.id.nav_support -> {
                val intent = Intent(this, SupportActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_about -> {
                val intent = Intent(this, AboutActivity::class.java)
                startActivity(intent)
            }
        }

        drawerLayout.closeDrawer(GravityCompat.END)
        return true
    }

    private fun showAlertDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK", null)
        builder.show()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_EVENT_REQUEST && resultCode == RESULT_OK) {
            loadEvents()
        }
    }

    override fun onResume() {
        super.onResume()
        loadEvents()
    }
}