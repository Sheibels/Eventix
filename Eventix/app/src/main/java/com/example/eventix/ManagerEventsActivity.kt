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
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManagerEventsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "ManagerEvents"

    private lateinit var btnMenu: ImageButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private lateinit var btnEventosNaoAssociados: Button
    private lateinit var btnMeusEventos: Button
    private lateinit var spinnerEstado: Spinner
    private lateinit var rvEvents: RecyclerView
    private lateinit var tvTotalEvents: TextView
    private lateinit var tvNoEvents: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEstadoLabel: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var managerEventsAdapter: ManagerEventsAdapter

    private val allEvents = mutableListOf<EventDetail>()
    private val filteredEvents = mutableListOf<EventDetail>()
    private var currentFilterStatus = EventStatus.ALL
    private var currentAssociationFilter = EventAssociationFilter.UNASSOCIATED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_manager_events)

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
        setupBottomNavigation()
        setupDrawerNavigation()
        setupRecyclerView()
        setupFilters()

        loadEvents()
    }

    private fun initializeViews() {
        btnMenu = findViewById(R.id.btnMenu)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        btnEventosNaoAssociados = findViewById(R.id.btnEventosNaoAssociados)
        btnMeusEventos = findViewById(R.id.btnMeusEventos)
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
        btnEventosNaoAssociados.setOnClickListener {
            selectAssociationFilter(EventAssociationFilter.UNASSOCIATED)
        }

        btnMeusEventos.setOnClickListener {
            selectAssociationFilter(EventAssociationFilter.MY_EVENTS)
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.navigation_events

        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    val intent = Intent(this, ManagerMainActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_services -> {
                    val intent = Intent(this, ManagerServicesActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_events -> {
                    true
                }
                R.id.navigation_profile -> {
                    val intent = Intent(this, ManagerProfileActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        managerEventsAdapter = ManagerEventsAdapter(
            filteredEvents,
            onAssociateClick = { event -> associateEvent(event) },
            onStatusChangeClick = { event, newStatus -> changeEventStatus(event, newStatus) },
            onEventClick = { event -> viewEventDetails(event) },
            onMessageClick = { event -> messageCreator(event) }
        )
        rvEvents.adapter = managerEventsAdapter
        rvEvents.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFilters() {
        selectAssociationFilter(EventAssociationFilter.UNASSOCIATED)

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

    private fun selectAssociationFilter(filter: EventAssociationFilter) {
        currentAssociationFilter = filter

        when (filter) {
            EventAssociationFilter.UNASSOCIATED -> {
                btnEventosNaoAssociados.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brown)
                btnEventosNaoAssociados.setTextColor(ContextCompat.getColor(this, R.color.white))

                btnMeusEventos.backgroundTintList = ContextCompat.getColorStateList(this, R.color.light_gray)
                btnMeusEventos.setTextColor(ContextCompat.getColor(this, R.color.black))

                spinnerEstado.visibility = View.GONE
                tvEstadoLabel.visibility = View.GONE
            }
            EventAssociationFilter.MY_EVENTS -> {
                btnMeusEventos.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brown)
                btnMeusEventos.setTextColor(ContextCompat.getColor(this, R.color.white))

                btnEventosNaoAssociados.backgroundTintList = ContextCompat.getColorStateList(this, R.color.light_gray)
                btnEventosNaoAssociados.setTextColor(ContextCompat.getColor(this, R.color.black))

                spinnerEstado.visibility = View.VISIBLE
                tvEstadoLabel.visibility = View.VISIBLE
            }
        }

        applyFilters()
    }

    private fun loadEvents() {
        progressBar.visibility = View.VISIBLE

        db.collection("eventos")
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                allEvents.clear()

                val eventsList = mutableListOf<EventDetail>()
                val currentManagerId = auth.currentUser?.uid

                documents.forEach { document ->
                    val gestorAssociado = document.getString("gestorAssociado")
                    val estado = document.getString("estado") ?: "ativo"

                    if (estado != "eliminado") {
                        val event = EventDetail(
                            id = document.id,
                            tipoEvento = document.getString("tipoEvento") ?: "",
                            data = document.getString("data") ?: "",
                            hora = document.getString("hora") ?: "",
                            localizacao = document.getString("localizacao") ?: "",
                            numeroParticipantes = document.getLong("numeroParticipantes")?.toInt() ?: 0,
                            convidados = document.get("convidados") as? List<String> ?: emptyList(),
                            custoTotal = document.getDouble("custoTotal") ?: 0.0,
                            estado = estado,
                            criador = document.getString("criador") ?: "",
                            timestamp = document.getTimestamp("timestamp"),
                            servicos = document.get("servicos") as? List<String> ?: emptyList(),
                            servicosIds = document.get("servicosIds") as? List<String> ?: emptyList(),
                            nomesParticipantes = document.get("nomesParticipantes") as? List<String> ?: emptyList(),
                            localizacaoDetalhes = document.get("localizacaoDetalhes") as? Map<String, Any>,
                            isCreator = false,
                            gestorAssociado = gestorAssociado,
                            isAssociatedToManager = gestorAssociado == currentManagerId
                        )
                        eventsList.add(event)

                        Log.d(TAG, "Evento carregado: ${event.id}")
                        Log.d(TAG, "- Estado: ${event.estado}")
                        Log.d(TAG, "- Gestor: $gestorAssociado")
                        Log.d(TAG, "- Associado: ${event.isAssociatedToManager}")
                    }
                }

                eventsList.sortByDescending { it.timestamp?.toDate() }
                allEvents.addAll(eventsList)

                Log.d(TAG, "Total de eventos carregados: ${allEvents.size}")
                applyFilters()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.w(TAG, "Erro ao carregar eventos", e)
            }
    }

    private fun applyFilters() {
        filteredEvents.clear()

        var eventsToFilter = allEvents.toList()

        Log.d(TAG, "Aplicando filtros - Total eventos: ${eventsToFilter.size}")
        Log.d(TAG, "Filtro associação: $currentAssociationFilter")
        Log.d(TAG, "Filtro estado: $currentFilterStatus")

        when (currentAssociationFilter) {
            EventAssociationFilter.UNASSOCIATED -> {
                eventsToFilter = eventsToFilter.filter {
                    val isUnassociated = it.gestorAssociado.isNullOrEmpty()
                    Log.d(TAG, "Evento ${it.id}: gestorAssociado=${it.gestorAssociado}, isUnassociated=$isUnassociated")
                    isUnassociated
                }
            }
            EventAssociationFilter.MY_EVENTS -> {
                eventsToFilter = eventsToFilter.filter {
                    val isAssociated = it.isAssociatedToManager
                    Log.d(TAG, "Evento ${it.id}: isAssociatedToManager=$isAssociated")
                    isAssociated
                }

                if (currentFilterStatus != EventStatus.ALL) {
                    eventsToFilter = eventsToFilter.filter { event ->
                        val eventStatus = getEventStatusFromDatabase(event.estado)
                        val matches = eventStatus == currentFilterStatus
                        Log.d(TAG, "Evento ${event.id}: estadoBD=${event.estado}, statusEnum=$eventStatus, filtro=$currentFilterStatus, matches=$matches")
                        matches
                    }
                }
            }
        }

        Log.d(TAG, "Eventos após filtro: ${eventsToFilter.size}")

        filteredEvents.addAll(eventsToFilter)
        managerEventsAdapter.notifyDataSetChanged()
        updateUI()
    }

    private fun getEventStatusFromDatabase(estadoBD: String): EventStatus {
        return when (estadoBD.lowercase()) {
            "ativo" -> EventStatus.PENDING
            "confirmado" -> EventStatus.CONFIRMED
            "concluido", "concluído" -> EventStatus.COMPLETED
            "cancelado" -> EventStatus.CANCELLED
            else -> EventStatus.PENDING
        }
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
                currentAssociationFilter == EventAssociationFilter.UNASSOCIATED ->
                    "Não há eventos por associar"
                currentAssociationFilter == EventAssociationFilter.MY_EVENTS && currentFilterStatus == EventStatus.ALL ->
                    "Não tem eventos associados"
                currentFilterStatus != EventStatus.ALL ->
                    "Não tem eventos ${currentFilterStatus.displayName.lowercase()}"
                else -> "Não há eventos"
            }
        } else {
            tvNoEvents.visibility = View.GONE
            rvEvents.visibility = View.VISIBLE
        }
    }

    private fun associateEvent(event: EventDetail) {
        val currentManagerId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE

        Log.d(TAG, "Associando evento ${event.id} ao gestor $currentManagerId")

        db.collection("eventos").document(event.id)
            .update("gestorAssociado", currentManagerId)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Log.d(TAG, "Evento ${event.id} associado com sucesso")

                val eventIndex = allEvents.indexOfFirst { it.id == event.id }
                if (eventIndex != -1) {
                    allEvents[eventIndex] = allEvents[eventIndex].copy(
                        gestorAssociado = currentManagerId,
                        isAssociatedToManager = true
                    )
                }

                applyFilters()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.w(TAG, "Erro ao associar evento", e)
            }
    }

    private fun changeEventStatus(event: EventDetail, newStatus: EventStatus) {
        progressBar.visibility = View.VISIBLE

        Log.d(TAG, "=== INÍCIO MUDANÇA DE ESTADO ===")
        Log.d(TAG, "Evento ID: ${event.id}")
        Log.d(TAG, "Estado atual: ${event.estado}")
        Log.d(TAG, "Novo status solicitado: $newStatus")

        EventStatusManager.updateEventStatus(
            eventId = event.id,
            newStatus = newStatus,
            creatorId = event.criador,
            onSuccess = {
                progressBar.visibility = View.GONE
                Log.d(TAG, "Estado alterado com sucesso!")

                val eventIndex = allEvents.indexOfFirst { it.id == event.id }
                if (eventIndex != -1) {
                    val newDatabaseState = when (newStatus) {
                        EventStatus.PENDING -> "ativo"
                        EventStatus.CONFIRMED -> "confirmado"
                        EventStatus.COMPLETED -> "concluido"
                        EventStatus.CANCELLED -> "cancelado"
                        EventStatus.ALL -> "ativo"
                    }

                    allEvents[eventIndex] = allEvents[eventIndex].copy(estado = newDatabaseState)
                    Log.d(TAG, "Evento local atualizado para estado: $newDatabaseState")
                }

                applyFilters()
                Log.d(TAG, "=== FIM MUDANÇA DE ESTADO ===")
            },
            onFailure = { exception ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Erro ao alterar estado do evento", exception)
            }
        )
    }

    private fun messageCreator(event: EventDetail) {
        val intent = Intent(this, MessagesActivity::class.java)
        intent.putExtra("EVENT_ID", event.id)
        intent.putExtra("OTHER_USER_ID", event.criador)
        intent.putExtra("USER_ROLE", "manager")
        startActivity(intent)
    }

    private fun viewEventDetails(event: EventDetail) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manager_event_details, null)

        dialogView.findViewById<TextView>(R.id.tvEventType).text = event.tipoEvento
        dialogView.findViewById<TextView>(R.id.tvEventDate).text = event.data
        dialogView.findViewById<TextView>(R.id.tvEventTime).text = event.hora
        dialogView.findViewById<TextView>(R.id.tvEventLocation).text = event.localizacao
        dialogView.findViewById<TextView>(R.id.tvEventParticipants).text = event.getParticipantsText()
        dialogView.findViewById<TextView>(R.id.tvEventCost).text = event.getFormattedCost()

        val statusTextView = dialogView.findViewById<TextView>(R.id.tvEventStatus)
        val eventStatus = getEventStatusFromDatabase(event.estado)
        statusTextView.text = eventStatus.displayName

        loadCreatorInfoForDialog(event.criador, dialogView)

        val servicesList = dialogView.findViewById<TextView>(R.id.tvServicesList)
        if (event.servicos.isNotEmpty()) {
            servicesList.text = event.servicos.joinToString("\n") { "• $it" }
        } else {
            servicesList.text = "Nenhum serviço selecionado"
        }

        val participantsList = dialogView.findViewById<TextView>(R.id.tvParticipantsList)
        if (event.nomesParticipantes.isNotEmpty()) {
            participantsList.text = event.nomesParticipantes.joinToString("\n") { "• $it" }
        } else {
            participantsList.text = "Nenhum participante listado"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<android.widget.Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadCreatorInfoForDialog(creatorId: String, dialogView: View) {
        val tvCreatorName = dialogView.findViewById<TextView>(R.id.tvCreatorName)
        val tvCreatorEmail = dialogView.findViewById<TextView>(R.id.tvCreatorEmail)
        val tvCreatorPhone = dialogView.findViewById<TextView>(R.id.tvCreatorPhone)
        val layoutCreatorPhone = dialogView.findViewById<LinearLayout>(R.id.layoutCreatorPhone)

        tvCreatorName.text = "Carregando..."
        tvCreatorEmail.text = "Carregando..."
        layoutCreatorPhone.visibility = View.GONE

        db.collection("utilizadores").document(creatorId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("nome") ?: "Nome não disponível"
                    val email = document.getString("email") ?: "Email não disponível"
                    val phone = document.getString("telemovel")

                    tvCreatorName.text = name
                    tvCreatorEmail.text = email

                    if (!phone.isNullOrEmpty()) {
                        tvCreatorPhone.text = phone
                        layoutCreatorPhone.visibility = View.VISIBLE
                    } else {
                        layoutCreatorPhone.visibility = View.GONE
                    }

                    Log.d(TAG, "Informações do criador carregadas no diálogo: $name, $email")
                } else {
                    tvCreatorName.text = "Utilizador não encontrado"
                    tvCreatorEmail.text = "Email não disponível"
                    layoutCreatorPhone.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao carregar informações do criador no diálogo", e)
                tvCreatorName.text = "Erro ao carregar"
                tvCreatorEmail.text = "Erro ao carregar"
                layoutCreatorPhone.visibility = View.GONE
            }
    }

    private fun showAlertDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_notifications -> {
                val intent = Intent(this, NotificationsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }
        }

        drawerLayout.closeDrawer(GravityCompat.END)
        return true
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

    override fun onResume() {
        super.onResume()
        loadEvents()
    }
}

enum class EventAssociationFilter(val displayName: String) {
    UNASSOCIATED("Eventos Não Associados"),
    MY_EVENTS("Os Meus Eventos")
}