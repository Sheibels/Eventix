package com.example.eventix

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "CalendarActivity"

    private lateinit var btnMenu: ImageButton
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private lateinit var btnPreviousMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var tvMonthYear: TextView
    private lateinit var calendarGrid: GridLayout

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val calendar = Calendar.getInstance()
    private val currentDate = Calendar.getInstance()
    private val eventDates = mutableSetOf<String>()
    private val eventsMap = mutableMapOf<String, MutableList<CalendarEvent>>()

    private var eventsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_calendar)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        calendar.time = Date()
        currentDate.time = Date()

        initializeViews()
        setupClickListeners()
        setupBottomNavigation()
        setupDrawerNavigation()

        loadEventsFromFirestore()
        generateCalendar()
    }

    private fun initializeViews() {
        btnMenu = findViewById(R.id.btnMenu)
        fabAdd = findViewById(R.id.fabAdd)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        btnPreviousMonth = findViewById(R.id.btnPreviousMonth)
        btnNextMonth = findViewById(R.id.btnNextMonth)
        tvMonthYear = findViewById(R.id.tvMonthYear)
        calendarGrid = findViewById(R.id.calendarGrid)
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
        btnPreviousMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            generateCalendar()
        }

        btnNextMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            generateCalendar()
        }

        fabAdd.setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.navigation_calendar

        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_events -> {
                    val intent = Intent(this, EventDetailsActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_calendar -> {
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

    private fun generateCalendar() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("pt", "PT"))
        tvMonthYear.text = monthFormat.format(calendar.time).replaceFirstChar { it.uppercase() }

        calendarGrid.removeAllViews()

        val firstDayOfMonth = Calendar.getInstance().apply {
            time = calendar.time
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val lastDayOfMonth = Calendar.getInstance().apply {
            time = calendar.time
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        var dayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK) - 2
        if (dayOfWeek < 0) dayOfWeek = 6

        for (i in 0 until dayOfWeek) {
            addEmptyDay()
        }

        for (day in 1..lastDayOfMonth.get(Calendar.DAY_OF_MONTH)) {
            val isToday = calendar.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) &&
                    calendar.get(Calendar.MONTH) == currentDate.get(Calendar.MONTH) &&
                    day == currentDate.get(Calendar.DAY_OF_MONTH)

            val dateString = String.format("%02d/%02d/%04d",
                day,
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
            )
            val hasEvents = eventDates.contains(dateString)

            addCalendarDay(day, isToday, hasEvents, dateString)
        }
    }

    private fun addEmptyDay() {
        val emptyView = View(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 80
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(1, 1, 1, 1)
            }
        }
        calendarGrid.addView(emptyView)
    }

    private fun addCalendarDay(day: Int, isToday: Boolean, hasEvents: Boolean, dateString: String) {
        val dayView = layoutInflater.inflate(R.layout.calendar_day_item, null)

        val tvDay = dayView.findViewById<TextView>(R.id.tvDay)
        val eventIndicator = dayView.findViewById<View>(R.id.eventIndicator)

        tvDay.text = day.toString()

        if (isToday) {
            tvDay.setBackgroundResource(R.drawable.calendar_today_background)
            tvDay.setTextColor(getColor(R.color.white))
        } else {
            tvDay.background = null
            tvDay.setTextColor(getColor(R.color.black))
        }

        eventIndicator.visibility = if (hasEvents) View.VISIBLE else View.GONE

        dayView.setOnClickListener {
            if (hasEvents) {
                showEventsForDay(dateString)
            }
        }

        val layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(1, 1, 1, 1)
        }
        dayView.layoutParams = layoutParams

        calendarGrid.addView(dayView)
    }

    private fun loadEventsFromFirestore() {
        if (eventsLoaded) {
            Log.d(TAG, "Eventos já carregados, a saltar carregamento")
            return
        }

        val currentUserId = auth.currentUser?.uid ?: return

        eventDates.clear()
        eventsMap.clear()

        Log.d(TAG, "A carregar eventos para utilizador: $currentUserId")

        val processedEventIds = mutableSetOf<String>()

        db.collection("eventos")
            .get()
            .addOnSuccessListener { documents ->

                var totalEventsProcessed = 0

                documents.forEach { document ->
                    val eventId = document.id

                    if (processedEventIds.contains(eventId)) {
                        Log.d(TAG, "Evento $eventId já processado, a saltar...")
                        return@forEach
                    }

                    val criador = document.getString("criador") ?: ""
                    val convidados = document.get("convidados") as? List<String> ?: emptyList()
                    val estado = document.getString("estado") ?: "ativo"

                    val isCreator = criador == currentUserId
                    val isInvited = convidados.contains(currentUserId)

                    val estadosVisiveis = listOf("ativo", "confirmado")

                    if ((isCreator || isInvited) && estadosVisiveis.contains(estado)) {
                        processedEventIds.add(eventId)
                        processEvent(document, isCreator)
                        totalEventsProcessed++
                        Log.d(TAG, "Evento processado: $eventId - Criador: $isCreator - Estado: $estado")
                    }
                }

                eventsLoaded = true
                Log.d(TAG, "Eventos carregados: ${eventDates.size} datas com eventos")
                Log.d(TAG, "Total de eventos processados: $totalEventsProcessed")
                generateCalendar()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao carregar eventos", e)
                generateCalendar()
            }
    }

    private fun processEvent(document: com.google.firebase.firestore.DocumentSnapshot, isCreator: Boolean) {
        val data = document.getString("data") ?: ""
        val hora = document.getString("hora") ?: ""
        val tipoEvento = document.getString("tipoEvento") ?: ""
        val localizacao = document.getString("localizacao") ?: ""
        val criador = document.getString("criador") ?: ""
        val gestorAssociado = document.getString("gestorAssociado")

        if (data.isNotEmpty()) {
            eventDates.add(data)

            val event = CalendarEvent(
                id = document.id,
                tipoEvento = tipoEvento,
                localizacao = localizacao,
                data = data,
                hora = hora,
                criador = criador,
                isCreator = isCreator,
                gestorAssociado = gestorAssociado
            )

            if (!eventsMap.containsKey(data)) {
                eventsMap[data] = mutableListOf()
            }
            eventsMap[data]?.add(event)
        }
    }

    private fun showEventsForDay(dateString: String) {
        val events = eventsMap[dateString] ?: return

        Log.d(TAG, "A mostrar ${events.size} eventos para o dia $dateString")

        loadCreatorNames(events) { eventsWithNames ->
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Eventos - $dateString")

            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_events_list, null)
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvEventsList)

            val eventsAdapter = DayEventsAdapter(eventsWithNames) { event ->
                if (event.isCreator) {
                    showEventDetails(event)
                }
            }

            recyclerView.adapter = eventsAdapter
            recyclerView.layoutManager = LinearLayoutManager(this)

            builder.setView(dialogView)
            builder.setNegativeButton("Fechar", null)
            builder.show()
        }
    }

    private fun showEventDetails(event: CalendarEvent) {
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
                        gestorAssociado = document.getString("gestorAssociado")
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

            val tvEventRole = dialogView.findViewById<TextView>(R.id.tvEventRole)
            tvEventRole?.text = event.getEventRole()

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
            } else {
                cardManagerInfo?.visibility = View.GONE
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

                    Log.d(TAG, "Informações do gestor carregadas no diálogo: $managerName, $managerEmail")
                } else {
                    tvManagerName?.text = "Gestor não encontrado"
                    tvManagerEmail?.text = "Email não disponível"
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao carregar informações do gestor no diálogo", e)
                tvManagerName?.text = "Erro ao carregar"
                tvManagerEmail?.text = "Erro ao carregar"
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

    private fun carregarConvidados(event: EventDetail, dialogView: View) {
        val tvGuestsList = dialogView.findViewById<TextView>(R.id.tvGuestsList)
        val tvGuestsCount = dialogView.findViewById<TextView>(R.id.tvGuestsCount)
        val cardGuests = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardGuests)

        if (event.convidados.isEmpty() || cardGuests == null) {
            cardGuests?.visibility = View.GONE
            return
        }

        tvGuestsCount?.text = "(${event.convidados.size})"
        tvGuestsList?.text = "A carregar..."

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
                        tvGuestsList?.text = guestNames.joinToString("\n") { "• $it" }
                    }
                }
                .addOnFailureListener {
                    guestNames.add("Utilizador Desconhecido")
                    processedCount++

                    if (processedCount == event.convidados.size) {
                        tvGuestsList?.text = guestNames.joinToString("\n") { "• $it" }
                    }
                }
        }
    }

    private fun loadCreatorNames(events: List<CalendarEvent>, callback: (List<CalendarEvent>) -> Unit) {
        val eventsWithNames = mutableListOf<CalendarEvent>()
        var processedCount = 0

        if (events.isEmpty()) {
            callback(emptyList())
            return
        }

        events.forEach { event ->
            if (event.isCreator) {
                eventsWithNames.add(event.copy(creatorName = "Você"))
                processedCount++
                if (processedCount == events.size) {
                    callback(eventsWithNames.sortedBy { it.hora })
                }
            } else {
                db.collection("utilizadores").document(event.criador)
                    .get()
                    .addOnSuccessListener { document ->
                        val creatorName = document.getString("nome") ?: "Utilizador Desconhecido"
                        eventsWithNames.add(event.copy(creatorName = creatorName))
                        processedCount++
                        if (processedCount == events.size) {
                            callback(eventsWithNames.sortedBy { it.hora })
                        }
                    }
                    .addOnFailureListener {
                        eventsWithNames.add(event.copy(creatorName = "Utilizador Desconhecido"))
                        processedCount++
                        if (processedCount == events.size) {
                            callback(eventsWithNames.sortedBy { it.hora })
                        }
                    }
            }
        }
    }

    fun reloadEvents() {
        eventsLoaded = false
        loadEventsFromFirestore()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_notifications -> {
                val intent = Intent(this, NotificationsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_guests -> {
                val intent = Intent(this, GuestsActivity::class.java)
                intent.putExtra("from", "CalendarActivity")
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
        currentDate.time = Date()

        if (intent.getBooleanExtra("RELOAD_EVENTS", false)) {
            Log.d(TAG, "A recarregar eventos por solicitação")
            reloadEvents()
            intent.removeExtra("RELOAD_EVENTS")
        }
    }
}

data class CalendarEvent(
    val id: String,
    val tipoEvento: String,
    val localizacao: String,
    val data: String,
    val hora: String,
    val criador: String,
    val isCreator: Boolean,
    val creatorName: String = "",
    val gestorAssociado: String? = null
)