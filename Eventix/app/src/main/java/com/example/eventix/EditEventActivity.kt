package com.example.eventix

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditEventActivity : AppCompatActivity() {

    private val TAG = "EditEventActivity"
    private val LOCATION_SELECTION_REQUEST = 1002
    private val SERVICES_SELECTION_REQUEST = 1003

    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView

    private lateinit var spinnerEventTypes: Spinner
    private lateinit var etCustomEventType: EditText
    private lateinit var layoutLocation: LinearLayout
    private lateinit var tvSelectedLocation: TextView
    private lateinit var layoutDate: LinearLayout
    private lateinit var layoutTime: LinearLayout
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var btnMinus: ImageButton
    private lateinit var btnPlus: ImageButton
    private lateinit var tvParticipantsCount: TextView
    private lateinit var layoutParticipantsNames: LinearLayout
    private lateinit var layoutSelectGuests: LinearLayout
    private lateinit var tvSelectedGuests: TextView
    private lateinit var rvSelectedGuests: RecyclerView
    private lateinit var layoutSelectServices: LinearLayout
    private lateinit var tvSelectedServices: TextView
    private lateinit var layoutServicesCost: LinearLayout
    private lateinit var tvTotalCost: TextView
    private lateinit var btnSaveEvent: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var eventTypesAdapter: ArrayAdapter<String>
    private lateinit var selectedGuestsAdapter: SelectedGuestsAdapter

    private val selectedGuests = mutableListOf<Guest>()
    private val selectedServices = mutableListOf<String>()
    private val selectedServicesNames = mutableListOf<String>()
    private val selectedServicesIds = mutableListOf<String>()
    private val participantsEditTexts = mutableListOf<EditText>()

    private var selectedEventType: String = ""
    private var selectedLocation: String = ""
    private var selectedLocationData: LocalSelecionado? = null
    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var participantsCount: Int = 10
    private var totalServicesCost: Double = 0.0

    private val calendar = Calendar.getInstance()

    private var eventId: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private var autoRepeatRunnable: Runnable? = null
    private var isAutoRepeating = false
    private var currentDelay = 300L
    private val minDelay = 30L
    private val accelerationFactor = 0.75

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_edit_event)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        eventId = intent.getStringExtra("EVENT_ID") ?: ""
        if (eventId.isEmpty()) {
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        setupRecyclerViews()
        loadEventTypes()

        loadEventData()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        tvTitle = findViewById(R.id.tvTitle)

        spinnerEventTypes = findViewById(R.id.spinnerEventTypes)
        etCustomEventType = findViewById(R.id.etCustomEventType)
        layoutLocation = findViewById(R.id.layoutLocation)
        tvSelectedLocation = findViewById(R.id.tvSelectedLocation)
        layoutDate = findViewById(R.id.layoutDate)
        layoutTime = findViewById(R.id.layoutTime)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvSelectedTime = findViewById(R.id.tvSelectedTime)
        btnMinus = findViewById(R.id.btnMinus)
        btnPlus = findViewById(R.id.btnPlus)
        tvParticipantsCount = findViewById(R.id.tvParticipantsCount)
        layoutParticipantsNames = findViewById(R.id.layoutParticipantsNames)
        layoutSelectGuests = findViewById(R.id.layoutSelectGuests)
        tvSelectedGuests = findViewById(R.id.tvSelectedGuests)
        rvSelectedGuests = findViewById(R.id.rvSelectedGuests)
        layoutSelectServices = findViewById(R.id.layoutSelectServices)
        tvSelectedServices = findViewById(R.id.tvSelectedServices)
        layoutServicesCost = findViewById(R.id.layoutServicesCost)
        tvTotalCost = findViewById(R.id.tvTotalCost)
        btnSaveEvent = findViewById(R.id.btnSaveEvent)

        tvTitle.text = "Editar Evento"
        btnSaveEvent.text = "GUARDAR ALTERAÇÕES"
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        spinnerEventTypes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedItem = parent?.getItemAtPosition(position).toString()

                if (selectedItem == "Tipo de evento personalizado") {
                    etCustomEventType.visibility = View.VISIBLE
                    selectedEventType = ""
                } else {
                    etCustomEventType.visibility = View.GONE
                    etCustomEventType.text.clear()
                    selectedEventType = selectedItem
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        layoutLocation.setOnClickListener {
            val intent = Intent(this, LocationSelectionActivity::class.java)
            startActivityForResult(intent, LOCATION_SELECTION_REQUEST)
        }

        layoutDate.setOnClickListener {
            showDatePicker()
        }

        layoutTime.setOnClickListener {
            showTimePicker()
        }

        setupAutoRepeatButton(btnMinus, false)
        setupAutoRepeatButton(btnPlus, true)

        layoutSelectGuests.setOnClickListener {
            showGuestsSelectionDialog()
        }

        layoutSelectServices.setOnClickListener {
            val intent = Intent(this, ServicesSelectionActivity::class.java)
            if (selectedServicesIds.isNotEmpty()) {
                intent.putStringArrayListExtra("SELECTED_SERVICES_IDS", ArrayList(selectedServicesIds))
                intent.putStringArrayListExtra("SELECTED_SERVICES_NAMES", ArrayList(selectedServicesNames))
            }
            startActivityForResult(intent, SERVICES_SELECTION_REQUEST)
        }

        btnSaveEvent.setOnClickListener {
            saveEvent()
        }
    }

    private fun setupAutoRepeatButton(button: ImageButton, isIncrement: Boolean) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isIncrement) {
                        incrementParticipants()
                    } else {
                        decrementParticipants()
                    }
                    startAutoRepeat(isIncrement)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopAutoRepeat()
                    true
                }
                else -> false
            }
        }
    }

    private fun startAutoRepeat(isIncrement: Boolean) {
        if (isAutoRepeating) return

        isAutoRepeating = true
        currentDelay = 500L

        autoRepeatRunnable = object : Runnable {
            override fun run() {
                if (isAutoRepeating) {
                    if (isIncrement) {
                        incrementParticipants()
                    } else {
                        decrementParticipants()
                    }

                    if (currentDelay > minDelay) {
                        currentDelay = (currentDelay * accelerationFactor).toLong()
                        if (currentDelay < minDelay) currentDelay = minDelay
                    }

                    handler.postDelayed(this, currentDelay)
                }
            }
        }

        handler.postDelayed(autoRepeatRunnable!!, 150L)
    }

    private fun stopAutoRepeat() {
        isAutoRepeating = false
        autoRepeatRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun incrementParticipants() {
        if (participantsCount < 1000) {
            participantsCount++
            tvParticipantsCount.text = participantsCount.toString()
            generateParticipantsFields()
        }
    }

    private fun decrementParticipants() {
        if (participantsCount > 1) {
            participantsCount--
            tvParticipantsCount.text = participantsCount.toString()
            generateParticipantsFields()
        }
    }

    private fun generateParticipantsFields() {
        val existingNames = mutableMapOf<Int, String>()
        participantsEditTexts.forEachIndexed { index, editText ->
            val name = editText.text.toString().trim()
            if (name.isNotEmpty()) {
                existingNames[index] = name
            }
        }

        layoutParticipantsNames.removeAllViews()
        participantsEditTexts.clear()

        for (i in 1..participantsCount) {
            val editText = EditText(this).apply {
                hint = "Nome do participante $i"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 16
                }
                setBackgroundResource(R.drawable.edit_text_background)
                setPadding(48, 48, 48, 48)
                textSize = 16f
                setTextColor(resources.getColor(R.color.black, null))
                setHintTextColor(resources.getColor(R.color.dark_gray, null))

                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                        android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME

                filters = arrayOf(android.text.InputFilter { source, start, end, dest, dstart, dend ->
                    val regex = Regex("[a-zA-ZÀ-ÿĀ-žḀ-ỿ\\s'-]*")
                    if (source.toString().matches(regex)) {
                        null
                    } else {
                        ""
                    }
                })

                existingNames[i - 1]?.let { existingName ->
                    setText(existingName)
                }
            }

            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updateFieldValidation(editText)
                }
            })

            participantsEditTexts.add(editText)
            layoutParticipantsNames.addView(editText)
        }
    }

    private fun updateFieldValidation(editText: EditText) {
        val text = editText.text.toString().trim()
        if (text.isEmpty()) {
            editText.setBackgroundResource(R.drawable.edit_text_error_background)
        } else {
            editText.setBackgroundResource(R.drawable.edit_text_background)
        }
    }

    private fun setupRecyclerViews() {
        selectedGuestsAdapter = SelectedGuestsAdapter(selectedGuests) { guest ->
            removeGuestFromEvent(guest)
        }
        rvSelectedGuests.adapter = selectedGuestsAdapter
        rvSelectedGuests.layoutManager = LinearLayoutManager(this)
    }

    private fun loadEventTypes() {
        val predefinedTypes = mutableListOf(
            "Selecione o tipo de evento",
            "Aniversário",
            "Casamento",
            "Batizado",
            "Evento Empresarial",
            "Festa Temática",
            "Festa de Finalista",
            "Refeição de Convívio",
            "Tipo de evento personalizado"
        )

        eventTypesAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            predefinedTypes
        )
        eventTypesAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerEventTypes.adapter = eventTypesAdapter
    }

    private fun loadEventData() {
        db.collection("eventos").document(eventId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val tipoEvento = document.getString("tipoEvento") ?: ""
                    if (tipoEvento.startsWith("Personalizado:")) {
                        val customType = tipoEvento.removePrefix("Personalizado: ")
                        spinnerEventTypes.setSelection(eventTypesAdapter.getPosition("Tipo de evento personalizado"))
                        etCustomEventType.setText(customType)
                        etCustomEventType.visibility = View.VISIBLE
                    } else {
                        val position = eventTypesAdapter.getPosition(tipoEvento)
                        if (position >= 0) {
                            spinnerEventTypes.setSelection(position)
                        }
                    }

                    selectedLocation = document.getString("localizacao") ?: ""
                    tvSelectedLocation.text = selectedLocation
                    tvSelectedLocation.setTextColor(resources.getColor(R.color.black, null))

                    val localizacaoDetalhes = document.get("localizacaoDetalhes") as? Map<String, Any>
                    localizacaoDetalhes?.let { detalhes ->
                        selectedLocationData = LocalSelecionado(
                            nome = detalhes["nome"] as? String ?: "",
                            endereco = detalhes["endereco"] as? String ?: "",
                            categoria = detalhes["categoria"] as? String ?: "",
                            latitude = detalhes["latitude"] as? Double ?: 0.0,
                            longitude = detalhes["longitude"] as? Double ?: 0.0,
                            telefone = detalhes["telefone"] as? String ?: "",
                            website = detalhes["website"] as? String ?: ""
                        )
                    }

                    selectedDate = document.getString("data") ?: ""
                    selectedTime = document.getString("hora") ?: ""
                    tvSelectedDate.text = selectedDate
                    tvSelectedTime.text = selectedTime
                    tvSelectedDate.setTextColor(resources.getColor(R.color.black, null))
                    tvSelectedTime.setTextColor(resources.getColor(R.color.black, null))

                    participantsCount = document.getLong("numeroParticipantes")?.toInt() ?: 10
                    tvParticipantsCount.text = participantsCount.toString()

                    val nomesParticipantes = document.get("nomesParticipantes") as? List<String> ?: emptyList()
                    generateParticipantsFields()

                    nomesParticipantes.forEachIndexed { index, nome ->
                        if (index < participantsEditTexts.size) {
                            participantsEditTexts[index].setText(nome)
                        }
                    }

                    val convidadosIds = document.get("convidados") as? List<String> ?: emptyList()
                    loadExistingGuests(convidadosIds)

                    selectedServicesNames.clear()
                    selectedServicesIds.clear()
                    selectedServicesNames.addAll(document.get("servicos") as? List<String> ?: emptyList())
                    selectedServicesIds.addAll(document.get("servicosIds") as? List<String> ?: emptyList())
                    totalServicesCost = document.getDouble("custoTotal") ?: 0.0

                    updateServicesDisplay()
                    updateServicesCostDisplay()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao carregar evento", e)
                showAlertDialog("Erro", "Erro ao carregar dados do evento.")
                finish()
            }
    }

    private fun loadExistingGuests(guestIds: List<String>) {
        if (guestIds.isEmpty()) return

        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("convidados")
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.equalTo("utilizador1", currentUserId),
                    com.google.firebase.firestore.Filter.equalTo("utilizador2", currentUserId)
                )
            )
            .get()
            .addOnSuccessListener { documents ->
                selectedGuests.clear()

                documents.forEach { document ->
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

                    if (guestIds.contains(guest.userId)) {
                        selectedGuests.add(guest)
                    }
                }

                updateGuestsDisplay()
            }
    }

    private fun showDatePicker() {
        val themedContext = ContextThemeWrapper(this, R.style.CustomDatePickerStyle)

        val datePickerDialog = DatePickerDialog(
            themedContext,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                selectedDate = dateFormat.format(calendar.time)
                tvSelectedDate.text = selectedDate
                tvSelectedDate.setTextColor(resources.getColor(R.color.black, null))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val themedContext = ContextThemeWrapper(this, R.style.CustomTimePickerStyle)

        val timePickerDialog = TimePickerDialog(
            themedContext,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)

                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                selectedTime = timeFormat.format(calendar.time)
                tvSelectedTime.text = selectedTime
                tvSelectedTime.setTextColor(resources.getColor(R.color.black, null))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )

        timePickerDialog.show()
    }

    private fun showGuestsSelectionDialog() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("convidados")
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.equalTo("utilizador1", currentUserId),
                    com.google.firebase.firestore.Filter.equalTo("utilizador2", currentUserId)
                )
            )
            .get()
            .addOnSuccessListener { documents ->
                val availableGuests = mutableListOf<Guest>()

                documents.forEach { document ->
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

                    if (!selectedGuests.any { it.userId == guest.userId }) {
                        availableGuests.add(guest)
                    }
                }

                if (availableGuests.isEmpty()) {
                    showAlertDialog("Sem convidados", "Não tem convidados disponíveis para adicionar ao evento.")
                    return@addOnSuccessListener
                }

                showGuestsDialog(availableGuests)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao carregar convidados", e)
                showAlertDialog("Erro", "Erro ao carregar lista de convidados.")
            }
    }

    private fun showGuestsDialog(availableGuests: List<Guest>) {
        val guestNames = availableGuests.map { it.nome }.toTypedArray()
        val checkedItems = BooleanArray(guestNames.size) { false }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Selecionar Convidados")
        builder.setMultiChoiceItems(guestNames, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }

        builder.setPositiveButton("Adicionar") { _, _ ->
            checkedItems.forEachIndexed { index, isChecked ->
                if (isChecked) {
                    selectedGuests.add(availableGuests[index])
                }
            }
            updateGuestsDisplay()
        }

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun removeGuestFromEvent(guest: Guest) {
        selectedGuests.remove(guest)
        updateGuestsDisplay()
    }

    private fun updateGuestsDisplay() {
        if (selectedGuests.isEmpty()) {
            tvSelectedGuests.text = "Selecionar convidados"
            tvSelectedGuests.setTextColor(resources.getColor(R.color.dark_gray, null))
            rvSelectedGuests.visibility = View.GONE
        } else {
            val guestsText = if (selectedGuests.size == 1) {
                "1 convidado adicionado"
            } else {
                "${selectedGuests.size} convidados adicionados"
            }

            tvSelectedGuests.text = guestsText
            tvSelectedGuests.setTextColor(resources.getColor(R.color.black, null))
            rvSelectedGuests.visibility = View.VISIBLE
        }

        selectedGuestsAdapter.notifyDataSetChanged()
    }

    private fun updateServicesDisplay() {
        if (selectedServicesNames.isEmpty()) {
            tvSelectedServices.text = "Selecionar serviços"
            tvSelectedServices.setTextColor(resources.getColor(R.color.dark_gray, null))
        } else {
            val servicesText = if (selectedServicesNames.size == 1) {
                "1 serviço selecionado"
            } else {
                "${selectedServicesNames.size} serviços selecionados"
            }

            tvSelectedServices.text = servicesText
            tvSelectedServices.setTextColor(resources.getColor(R.color.black, null))
        }
    }

    private fun updateServicesCostDisplay() {
        tvTotalCost.text = String.format("%.2f €", totalServicesCost)
    }

    private fun getParticipantsNames(): List<String> {
        return participantsEditTexts.map { editText ->
            editText.text.toString().trim()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            LOCATION_SELECTION_REQUEST -> {
                if (resultCode == RESULT_OK && data != null) {
                    val locationName = data.getStringExtra("SELECTED_LOCATION_NAME") ?: ""
                    val locationAddress = data.getStringExtra("SELECTED_LOCATION_ADDRESS") ?: ""
                    val locationCategory = data.getStringExtra("SELECTED_LOCATION_CATEGORY") ?: ""
                    val latitude = data.getDoubleExtra("SELECTED_LOCATION_LATITUDE", 0.0)
                    val longitude = data.getDoubleExtra("SELECTED_LOCATION_LONGITUDE", 0.0)
                    val phone = data.getStringExtra("SELECTED_LOCATION_PHONE") ?: ""
                    val website = data.getStringExtra("SELECTED_LOCATION_WEBSITE") ?: ""

                    selectedLocationData = LocalSelecionado(
                        nome = locationName,
                        endereco = locationAddress,
                        categoria = locationCategory,
                        latitude = latitude,
                        longitude = longitude,
                        telefone = phone,
                        website = website
                    )

                    selectedLocation = "$locationName - $locationAddress"
                    tvSelectedLocation.text = selectedLocation
                    tvSelectedLocation.setTextColor(resources.getColor(R.color.black, null))
                }
            }
            SERVICES_SELECTION_REQUEST -> {
                if (resultCode == RESULT_OK && data != null) {
                    val servicesNames = data.getStringArrayListExtra("SELECTED_SERVICES_NAMES") ?: arrayListOf()
                    val servicesIds = data.getStringArrayListExtra("SELECTED_SERVICES_IDS") ?: arrayListOf()
                    val totalPrice = data.getDoubleExtra("TOTAL_PRICE", 0.0)

                    selectedServicesNames.clear()
                    selectedServicesNames.addAll(servicesNames)

                    selectedServicesIds.clear()
                    selectedServicesIds.addAll(servicesIds)

                    totalServicesCost = totalPrice

                    updateServicesDisplay()
                    updateServicesCostDisplay()
                }
            }
        }
    }

    private fun saveEvent() {
        if (!validateEventData()) {
            return
        }

        val customType = etCustomEventType.text.toString().trim()
        val finalEventType = if (customType.isNotEmpty() && etCustomEventType.visibility == View.VISIBLE) {
            "Personalizado: $customType"
        } else {
            selectedEventType
        }

        val participantsNames = getParticipantsNames()

        val eventData = hashMapOf<String, Any>(
            "tipoEvento" to finalEventType,
            "localizacao" to selectedLocation,
            "data" to selectedDate,
            "hora" to selectedTime,
            "numeroParticipantes" to participantsCount,
            "nomesParticipantes" to participantsNames,
            "convidados" to selectedGuests.map { it.userId },
            "servicos" to selectedServicesNames,
            "servicosIds" to selectedServicesIds,
            "custoTotal" to totalServicesCost
        )

        selectedLocationData?.let { location ->
            eventData["localizacaoDetalhes"] = hashMapOf(
                "nome" to location.nome,
                "endereco" to location.endereco,
                "categoria" to location.categoria,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "telefone" to location.telefone,
                "website" to location.website
            )
        }

        btnSaveEvent.isEnabled = false
        btnSaveEvent.text = "A guardar..."

        db.collection("eventos").document(eventId)
            .update(eventData)
            .addOnSuccessListener {
                Log.d(TAG, "Evento atualizado com sucesso")

                showAlertDialog("Sucesso", "Evento editado com sucesso!") {
                    val intent = Intent(this@EditEventActivity, EventDetailsActivity::class.java)
                    intent.putExtra("EVENT_ID", eventId)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao atualizar evento", e)
                btnSaveEvent.isEnabled = true
                btnSaveEvent.text = "GUARDAR ALTERAÇÕES"
                showAlertDialog("Erro", "Erro ao atualizar evento. Tente novamente.")
            }
    }

    private fun validateEventData(): Boolean {
        if (selectedEventType.isEmpty() && etCustomEventType.text.toString().trim().isEmpty()) {
            showAlertDialog("Erro", "Selecione um tipo de evento.")
            return false
        }

        if (selectedEventType == "Selecione o tipo de evento") {
            showAlertDialog("Erro", "Selecione um tipo de evento válido.")
            return false
        }

        if (selectedLocation.isEmpty() || selectedLocation == "Selecionar localização") {
            showAlertDialog("Erro", "Selecione a localização do evento.")
            return false
        }

        if (selectedDate.isEmpty()) {
            showAlertDialog("Erro", "Selecione a data do evento.")
            return false
        }

        if (selectedTime.isEmpty()) {
            showAlertDialog("Erro", "Selecione a hora do evento.")
            return false
        }

        val emptyFields = mutableListOf<Int>()
        participantsEditTexts.forEachIndexed { index, editText ->
            val name = editText.text.toString().trim()
            if (name.isEmpty()) {
                emptyFields.add(index + 1)
                updateFieldValidation(editText)
            }
        }

        if (emptyFields.isNotEmpty()) {
            showAlertDialog("Erro", "Preencha todos os nomes dos participantes.")
            return false
        }

        val participantsNames = getParticipantsNames()
        val duplicateNames = participantsNames.groupingBy { it.lowercase() }
            .eachCount()
            .filter { it.value > 1 }
            .keys

        if (duplicateNames.isNotEmpty()) {
            showAlertDialog("Erro", "Há nomes duplicados: ${duplicateNames.joinToString(", ")}. Cada participante deve ter um nome único.")
            return false
        }

        if (selectedServicesIds.isEmpty()) {
            showAlertDialog("Erro", "Deve selecionar pelo menos 1 serviço para o evento.")
            return false
        }

        return true
    }

    private fun showAlertDialog(title: String, message: String, onDismiss: (() -> Unit)? = null) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
            onDismiss?.invoke()
        }
        builder.show()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRepeat()
    }
}