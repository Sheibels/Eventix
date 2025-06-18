package com.example.eventix

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManagerServicesActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "ManagerServices"

    private lateinit var btnMenu: ImageButton
    private lateinit var btnAddService: Button
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private lateinit var etSearchServices: EditText
    private lateinit var spinnerTipo: Spinner
    private lateinit var rvServices: RecyclerView
    private lateinit var tvTotalServices: TextView
    private lateinit var tvNoServices: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var servicesAdapter: ManagerServicesAdapter

    private val allServices = mutableListOf<Servico>()
    private val filteredServices = mutableListOf<Servico>()

    private var currentSearchQuery = ""
    private var currentTipoFiltro = TipoServicoManager.TODOS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_manager_services)

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
        setupSpinners()
        setupSearchListener()

        loadServicesFromFirestore()
    }

    private fun initializeViews() {
        btnMenu = findViewById(R.id.btnMenu)
        btnAddService = findViewById(R.id.btnAddService)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        etSearchServices = findViewById(R.id.etSearchServices)
        spinnerTipo = findViewById(R.id.spinnerTipo)
        rvServices = findViewById(R.id.rvServices)
        tvTotalServices = findViewById(R.id.tvTotalServices)
        tvNoServices = findViewById(R.id.tvNoServices)
        progressBar = findViewById(R.id.progressBar)
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
        btnAddService.setOnClickListener {
            val intent = Intent(this, CreateServiceActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        servicesAdapter = ManagerServicesAdapter(
            filteredServices,
            onEditClick = { service -> editService(service) },
            onDeleteClick = { service -> deleteService(service) }
        )
        rvServices.adapter = servicesAdapter
        rvServices.layoutManager = LinearLayoutManager(this)
    }

    private fun setupSpinners() {
        // Usar TipoServicoManager em vez de TipoServico para remover "Favoritos"
        val tiposAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_category,
            TipoServicoManager.values().map { it.nomeExibicao }
        )
        tiposAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_radius)
        spinnerTipo.adapter = tiposAdapter

        spinnerTipo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTipoFiltro = TipoServicoManager.values()[position]
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

    private fun applyFilters() {
        filteredServices.clear()

        var servicesToFilter = allServices.toList()

        when (currentTipoFiltro) {
            TipoServicoManager.TODOS -> {
                // Não aplica filtro
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
                        empresa = document.getString("empresa") ?: ""
                    )
                    allServices.add(service)
                }

                Log.d(TAG, "Serviços carregados: ${allServices.size}")
                applyFilters()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.w(TAG, "Erro ao carregar serviços", e)
            }
    }

    private fun editService(service: Servico) {
        val intent = Intent(this, EditServiceActivity::class.java)
        intent.putExtra("SERVICE_ID", service.id)
        startActivity(intent)
    }

    private fun deleteService(service: Servico) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Eliminar Serviço")
        builder.setMessage("Tem a certeza que deseja eliminar o serviço '${service.nome}'?")

        builder.setPositiveButton("Eliminar") { _, _ ->
            performDeleteService(service)
        }

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun performDeleteService(service: Servico) {
        progressBar.visibility = View.VISIBLE

        db.collection("servicos").document(service.id)
            .delete()
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Log.d(TAG, "Serviço eliminado com sucesso: ${service.id}")

                allServices.removeAll { it.id == service.id }
                applyFilters()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.w(TAG, "Erro ao eliminar serviço", e)
            }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.navigation_services

        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    val intent = Intent(this, ManagerMainActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_services -> {
                    true
                }
                R.id.navigation_events -> {
                    val intent = Intent(this, ManagerEventsActivity::class.java)
                    startActivity(intent)
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
        loadServicesFromFirestore()
    }
}