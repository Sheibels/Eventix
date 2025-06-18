package com.example.eventix

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ServicesSelectionActivity : AppCompatActivity() {

    private val TAG = "ServicesSelection"

    private lateinit var btnBack: ImageButton
    private lateinit var etSearchServices: EditText
    private lateinit var spinnerTipo: Spinner
    private lateinit var spinnerPreco: Spinner
    private lateinit var rvServices: RecyclerView
    private lateinit var tvSelectedCount: TextView
    private lateinit var tvTotalServices: TextView
    private lateinit var tvNoServices: TextView
    private lateinit var layoutConfirmation: LinearLayout
    private lateinit var tvTotalPrice: TextView
    private lateinit var btnConfirmServices: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var servicesAdapter: ServicesAdapter

    private val allServices = mutableListOf<Servico>()
    private val filteredServices = mutableListOf<Servico>()
    private val selectedServices = mutableListOf<Servico>()
    private val selectedServiceIds = mutableSetOf<String>()
    private val favoriteServices = mutableSetOf<String>()

    private var currentSearchQuery = ""
    private var currentTipoFiltro = TipoServico.TODOS
    private var currentPrecoFiltro = FiltroPreco.TODOS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_services_selection)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        receiveSelectedServices()

        initializeViews()
        setupClickListeners()
        setupRecyclerView()
        setupSpinners()
        setupSearchListener()
        setupBackPressedHandler()

        loadUserFavorites()
    }

    private fun receiveSelectedServices() {
        val preSelectedIds = intent.getStringArrayListExtra("SELECTED_SERVICES_IDS") ?: arrayListOf()
        val preSelectedNames = intent.getStringArrayListExtra("SELECTED_SERVICES_NAMES") ?: arrayListOf()

        selectedServiceIds.clear()
        selectedServiceIds.addAll(preSelectedIds)

        Log.d(TAG, "Serviços pré-selecionados recebidos: ${preSelectedIds.size}")
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                returnSelectedServices()
            }
        })
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        etSearchServices = findViewById(R.id.etSearchServices)
        spinnerTipo = findViewById(R.id.spinnerTipo)
        spinnerPreco = findViewById(R.id.spinnerPreco)
        rvServices = findViewById(R.id.rvServices)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        tvTotalServices = findViewById(R.id.tvTotalServices)
        tvNoServices = findViewById(R.id.tvNoServices)
        layoutConfirmation = findViewById(R.id.layoutConfirmation)
        tvTotalPrice = findViewById(R.id.tvTotalPrice)
        btnConfirmServices = findViewById(R.id.btnConfirmServices)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            returnSelectedServices()
        }

        btnConfirmServices.setOnClickListener {
            returnSelectedServices()
        }
    }

    private fun setupRecyclerView() {
        servicesAdapter = ServicesAdapter(
            filteredServices,
            selectedServiceIds,
            onServiceSelected = { service, isSelected ->
                handleServiceSelection(service, isSelected)
            },
            onFavoriteClicked = { service ->
                toggleFavorite(service)
            }
        )
        rvServices.adapter = servicesAdapter
        rvServices.layoutManager = LinearLayoutManager(this)
    }

    private fun setupSpinners() {
        val tiposAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_category,
            TipoServico.values().map { it.nomeExibicao }
        )
        tiposAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_radius)
        spinnerTipo.adapter = tiposAdapter

        val precosAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_radius,
            FiltroPreco.values().map { it.nomeExibicao }
        )
        precosAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_radius)
        spinnerPreco.adapter = precosAdapter

        spinnerTipo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTipoFiltro = TipoServico.values()[position]
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerPreco.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentPrecoFiltro = FiltroPreco.values()[position]
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearchListener() {
        etSearchServices.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s.toString().trim()
                applyFilters()
            }
        })
    }

    private fun handleServiceSelection(service: Servico, isSelected: Boolean) {
        if (isSelected) {
            selectedServiceIds.add(service.id)
            if (!selectedServices.any { it.id == service.id }) {
                selectedServices.add(service)
            }
        } else {
            selectedServiceIds.remove(service.id)
            selectedServices.removeAll { it.id == service.id }
        }
        updateSelectionUI()
    }

    private fun toggleFavorite(service: Servico) {
        val userId = auth.currentUser?.uid ?: return

        if (favoriteServices.contains(service.id)) {
            favoriteServices.remove(service.id)

            db.collection("utilizadores").document(userId)
                .update("servicosFavoritos", FieldValue.arrayRemove(service.id))
                .addOnSuccessListener {
                    Log.d(TAG, "Serviço removido dos favoritos: ${service.nome}")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Erro ao remover dos favoritos", e)
                    favoriteServices.add(service.id)
                }
        } else {
            favoriteServices.add(service.id)

            db.collection("utilizadores").document(userId)
                .update("servicosFavoritos", FieldValue.arrayUnion(service.id))
                .addOnSuccessListener {
                    Log.d(TAG, "Serviço adicionado aos favoritos: ${service.nome}")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Erro ao adicionar aos favoritos", e)
                    favoriteServices.remove(service.id)
                }
        }

        val serviceIndex = allServices.indexOfFirst { it.id == service.id }
        if (serviceIndex != -1) {
            allServices[serviceIndex] = allServices[serviceIndex].copy(
                favorito = favoriteServices.contains(service.id)
            )
        }

        applyFilters()
    }

    private fun updateSelectionUI() {
        if (selectedServices.isEmpty()) {
            tvSelectedCount.visibility = View.GONE
            layoutConfirmation.visibility = View.GONE
        } else {
            tvSelectedCount.visibility = View.VISIBLE
            tvSelectedCount.text = selectedServices.size.toString()
            layoutConfirmation.visibility = View.VISIBLE

            val totalPrice = calculateTotalPrice()
            tvTotalPrice.text = "${totalPrice.toInt()}€"
        }
    }

    private fun calculateTotalPrice(): Double {
        return selectedServices.sumOf { it.getPrecoMedio() }
    }

    private fun applyFilters() {
        filteredServices.clear()

        var servicesToFilter = allServices.toList()

        when (currentTipoFiltro) {
            TipoServico.TODOS -> {
            }
            TipoServico.FAVORITOS -> {
                servicesToFilter = servicesToFilter.filter { favoriteServices.contains(it.id) }
            }
            else -> {
                servicesToFilter = servicesToFilter.filter { it.tipo == currentTipoFiltro.nomeExibicao }
            }
        }

        if (currentSearchQuery.isNotEmpty()) {
            servicesToFilter = servicesToFilter.filter {
                it.nome.contains(currentSearchQuery, ignoreCase = true) ||
                        it.descricao.contains(currentSearchQuery, ignoreCase = true) ||
                        it.tipo.contains(currentSearchQuery, ignoreCase = true) ||
                        it.empresa.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        when (currentPrecoFiltro) {
            FiltroPreco.TODOS -> {
            }
            FiltroPreco.MAIS_BARATO -> {
                servicesToFilter = servicesToFilter.sortedBy { it.getPrecoMedio() }
            }
            FiltroPreco.MAIS_CARO -> {
                servicesToFilter = servicesToFilter.sortedByDescending { it.getPrecoMedio() }
            }
            FiltroPreco.ATE_100 -> {
                servicesToFilter = servicesToFilter.filter { it.precoMaximo <= 100.0 }
            }
            FiltroPreco.ATE_300 -> {
                servicesToFilter = servicesToFilter.filter { it.precoMaximo <= 300.0 }
            }
            FiltroPreco.ATE_500 -> {
                servicesToFilter = servicesToFilter.filter { it.precoMaximo <= 500.0 }
            }
            FiltroPreco.ACIMA_500 -> {
                servicesToFilter = servicesToFilter.filter { it.precoMinimo > 500.0 }
            }
        }

        servicesToFilter = servicesToFilter.map { service ->
            service.copy(favorito = favoriteServices.contains(service.id))
        }

        filteredServices.addAll(servicesToFilter)
        servicesAdapter.notifyDataSetChanged()

        updateServicesCountUI()
        updateNoServicesVisibility()
    }

    private fun updateServicesCountUI() {
        val count = filteredServices.size
        tvTotalServices.text = if (count == 1) {
            "1 serviço"
        } else {
            "$count serviços"
        }
    }

    private fun updateNoServicesVisibility() {
        if (filteredServices.isEmpty()) {
            tvNoServices.visibility = View.VISIBLE
            rvServices.visibility = View.GONE
        } else {
            tvNoServices.visibility = View.GONE
            rvServices.visibility = View.VISIBLE
        }
    }

    private fun loadUserFavorites() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("utilizadores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val favorites = document.get("servicosFavoritos") as? List<String> ?: emptyList()
                    favoriteServices.clear()
                    favoriteServices.addAll(favorites)
                    Log.d(TAG, "Favoritos carregados: ${favorites.size}")

                    loadServicesFromFirestore()
                } else {
                    db.collection("utilizadores").document(userId)
                        .update("servicosFavoritos", emptyList<String>())
                        .addOnSuccessListener {
                            Log.d(TAG, "Campo servicosFavoritos criado")
                            loadServicesFromFirestore()
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Erro ao criar campo servicosFavoritos", e)
                            loadServicesFromFirestore()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao carregar favoritos", e)
                loadServicesFromFirestore()
            }
    }

    private fun loadServicesFromFirestore() {
        progressBar.visibility = View.VISIBLE

        db.collection("servicos")
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                allServices.clear()

                documents.forEach { document ->
                    val service = Servico(
                        id = document.id,
                        nome = document.getString("nome") ?: "",
                        tipo = document.getString("tipo") ?: "",
                        precoMinimo = document.getDouble("precoMinimo") ?: 0.0,
                        precoMaximo = document.getDouble("precoMaximo") ?: 0.0,
                        contacto = document.getString("contacto") ?: "",
                        descricao = document.getString("descricao") ?: "",
                        empresa = document.getString("empresa") ?: "",
                        favorito = favoriteServices.contains(document.id)
                    )
                    allServices.add(service)
                }

                reconstructSelectedServices()

                Log.d(TAG, "Serviços carregados: ${allServices.size}")
                applyFilters()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.w(TAG, "Erro ao carregar serviços", e)
            }
    }

    private fun reconstructSelectedServices() {
        selectedServices.clear()
        selectedServiceIds.forEach { serviceId ->
            val service = allServices.find { it.id == serviceId }
            service?.let { selectedServices.add(it) }
        }
        updateSelectionUI()
        Log.d(TAG, "Serviços selecionados reconstruídos: ${selectedServices.size}")
    }

    private fun returnSelectedServices() {
        val intent = Intent()
        intent.putStringArrayListExtra("SELECTED_SERVICES_NAMES", ArrayList(selectedServices.map { it.nome }))
        intent.putStringArrayListExtra("SELECTED_SERVICES_IDS", ArrayList(selectedServices.map { it.id }))
        intent.putExtra("TOTAL_PRICE", calculateTotalPrice())
        setResult(RESULT_OK, intent)
        finish()
    }
}