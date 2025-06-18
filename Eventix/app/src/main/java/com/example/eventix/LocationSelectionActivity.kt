package com.example.eventix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class LocationSelectionActivity : AppCompatActivity() {

    private val TAG = "LocationSelection"
    private val LOCATION_PERMISSION_REQUEST = 1001
    private val SEARCH_DELAY = 1000L

    private lateinit var mapView: MapView
    private lateinit var etSearch: EditText
    private lateinit var btnMyLocation: ImageButton
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerRadius: Spinner
    private lateinit var rvLocations: RecyclerView
    private lateinit var cardResults: androidx.cardview.widget.CardView
    private lateinit var btnCloseResults: ImageButton
    private lateinit var btnCancel: Button
    private lateinit var btnConfirm: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationsAdapter: LocationsAdapter

    private var currentLocation: GeoPoint? = null
    private var selectedLocation: LocalSelecionado? = null
    private var searchMarkers = mutableListOf<Marker>()

    private var searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", 0))

        setContentView(R.layout.activity_location_selection)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initializeViews()
        setupMap()
        setupSpinners()
        setupRecyclerView()
        setupClickListeners()

        checkLocationPermission()
    }

    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        etSearch = findViewById(R.id.etSearch)
        btnMyLocation = findViewById(R.id.btnMyLocation)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerRadius = findViewById(R.id.spinnerRadius)
        rvLocations = findViewById(R.id.rvLocations)
        cardResults = findViewById(R.id.cardResults)
        btnCloseResults = findViewById(R.id.btnCloseResults)
        btnCancel = findViewById(R.id.btnCancel)
        btnConfirm = findViewById(R.id.btnConfirm)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val portugalCenter = GeoPoint(39.5, -8.0)
        mapView.controller.setZoom(7.0)
        mapView.controller.setCenter(portugalCenter)

        mapView.setScrollableAreaLimitLatitude(42.2, 36.8, 0)
        mapView.setScrollableAreaLimitLongitude(-9.7, -6.0, 0)

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { point ->
                    if (isPointInPortugal(point)) {
                        handleMapClick(point)
                    } else {
                        Toast.makeText(this@LocationSelectionActivity,
                            "Selecione um local dentro de Portugal", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }

        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(0, mapEventsOverlay)
    }

    private fun setupSpinners() {
        val categories = CategoriaLocal.values().map { it.nomeExibicao }
        val categoryAdapter = ArrayAdapter(this, R.layout.spinner_item_category, categories)
        categoryAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_radius)
        spinnerCategory.adapter = categoryAdapter

        val radiusOptions = listOf("1 km", "2 km", "5 km", "10 km", "20 km", "30 km")
        val radiusAdapter = ArrayAdapter(this, R.layout.spinner_item_radius, radiusOptions)
        radiusAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_radius)
        spinnerRadius.adapter = radiusAdapter

        spinnerRadius.setSelection(0)
    }

    private fun setupRecyclerView() {
        locationsAdapter = LocationsAdapter(mutableListOf()) { location ->
            selectLocation(location)
        }
        rvLocations.adapter = locationsAdapter
        rvLocations.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        btnMyLocation.setOnClickListener {
            getCurrentLocation()
        }

        btnCloseResults.setOnClickListener {
            cardResults.visibility = View.GONE
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnConfirm.setOnClickListener {
            confirmLocationSelection()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()

                searchRunnable?.let { searchHandler.removeCallbacks(it) }

                if (query.length >= 3) {
                    searchRunnable = Runnable {
                        searchLocation(query)
                    }
                    searchHandler.postDelayed(searchRunnable!!, SEARCH_DELAY)
                } else if (query.isEmpty()) {
                    clearSearchResults()
                }
            }
        })

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentLocation?.let { searchNearbyPlaces(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerRadius.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentLocation?.let { searchNearbyPlaces(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        progressBar.visibility = View.VISIBLE

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            progressBar.visibility = View.GONE

            location?.let {
                val geoPoint = GeoPoint(it.latitude, it.longitude)

                if (isPointInPortugal(geoPoint)) {
                    currentLocation = geoPoint

                    mapView.controller.setZoom(15.0)
                    mapView.controller.setCenter(geoPoint)

                    addCurrentLocationMarker(geoPoint)

                    searchNearbyPlaces(geoPoint)
                } else {
                    Toast.makeText(this,
                        "Localização atual fora de Portugal. Centrando no país.",
                        Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "Não foi possível obter a localização atual", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Erro ao obter localização", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCurrentLocationMarker(location: GeoPoint) {
        val marker = Marker(mapView).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Minha Localização"
            snippet = "Você está aqui"
        }
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    private fun handleMapClick(point: GeoPoint) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                val response = LocationAPIClient.nominatim.reverse(
                    point.latitude,
                    point.longitude
                )

                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    response.body()?.let { result ->
                        val customLocation = LocalSelecionado(
                            id = "custom_${System.currentTimeMillis()}",
                            nome = "Local Personalizado",
                            categoria = "Personalizado",
                            endereco = result.displayName,
                            latitude = point.latitude,
                            longitude = point.longitude
                        )

                        selectLocation(customLocation)
                        addLocationMarker(point, customLocation.nome)
                    }
                } else {
                    Toast.makeText(this@LocationSelectionActivity,
                        "Erro ao obter informações do local", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Log.e(TAG, "Erro no geocoding reverso", e)
                Toast.makeText(this@LocationSelectionActivity,
                    "Erro de conexão", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchLocation(query: String) {
        Log.d(TAG, "Iniciando pesquisa para: $query")

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                val response = LocationAPIClient.nominatim.search(
                    query = query
                )

                progressBar.visibility = View.GONE

                Log.d(TAG, "Resposta da API - Sucesso: ${response.isSuccessful}, Código: ${response.code()}")

                if (response.isSuccessful) {
                    response.body()?.let { results ->
                        Log.d(TAG, "Resultados encontrados: ${results.size}")

                        if (results.isNotEmpty()) {
                            val referencePoint = currentLocation ?: GeoPoint(39.5, -8.0)

                            val locations = results.take(10).mapNotNull { result ->
                                val address = result.address
                                val country = address?.country?.lowercase()

                                if (country == "portugal" || country == "pt") {
                                    val latitude = result.latitude.toDouble()
                                    val longitude = result.longitude.toDouble()

                                    val distance = LocationUtils.calculateDistance(
                                        referencePoint.latitude, referencePoint.longitude,
                                        latitude, longitude
                                    )

                                    LocalSelecionado(
                                        id = result.placeId.toString(),
                                        nome = extractLocationName(result.displayName),
                                        categoria = "Pesquisa",
                                        endereco = result.displayName,
                                        latitude = latitude,
                                        longitude = longitude,
                                        distancia = distance
                                    )
                                } else {
                                    null
                                }
                            }.sortedBy { it.distancia }

                            Log.d(TAG, "Locais processados em Portugal: ${locations.size}")

                            if (locations.isNotEmpty()) {
                                showSearchResults(locations)

                                val firstResult = GeoPoint(
                                    locations.first().latitude,
                                    locations.first().longitude
                                )
                                mapView.controller.setZoom(15.0)
                                mapView.controller.setCenter(firstResult)

                                addSearchMarkers(locations)
                            } else {
                                Toast.makeText(this@LocationSelectionActivity,
                                    "Nenhum resultado encontrado em Portugal para '$query'", Toast.LENGTH_SHORT).show()
                            }

                        } else {
                            Log.d(TAG, "Nenhum resultado encontrado para: $query")
                            Toast.makeText(this@LocationSelectionActivity,
                                "Nenhum resultado encontrado para '$query'", Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        Log.e(TAG, "Resposta vazia da API")
                        Toast.makeText(this@LocationSelectionActivity,
                            "Resposta inválida do servidor", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Erro na API - Código: ${response.code()}, Mensagem: ${response.message()}")

                    when (response.code()) {
                        429 -> {
                            Toast.makeText(this@LocationSelectionActivity,
                                "Muitas pesquisas. Aguarde um momento e tente novamente.", Toast.LENGTH_LONG).show()
                        }
                        403 -> {
                            Toast.makeText(this@LocationSelectionActivity,
                                "Acesso negado. Verifique sua conexão.", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(this@LocationSelectionActivity,
                                "Erro na pesquisa (${response.code()}). Tente novamente.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Log.e(TAG, "Exceção na pesquisa: ${e.message}", e)

                when (e) {
                    is java.net.UnknownHostException -> {
                        Toast.makeText(this@LocationSelectionActivity,
                            "Sem conexão com a internet", Toast.LENGTH_SHORT).show()
                    }
                    is java.net.SocketTimeoutException -> {
                        Toast.makeText(this@LocationSelectionActivity,
                            "Tempo limite excedido. Tente novamente.", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this@LocationSelectionActivity,
                            "Erro de conexão: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun searchNearbyPlaces(center: GeoPoint) {
        val selectedCategory = CategoriaLocal.values()[spinnerCategory.selectedItemPosition]
        val radiusText = spinnerRadius.selectedItem.toString()
        val radius = radiusText.replace(" km", "").toDouble()

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                val query = LocationAPIClient.buildOverpassQuery(
                    center.latitude,
                    center.longitude,
                    radius,
                    selectedCategory
                )

                val response = LocationAPIClient.overpass.searchPOIs(query)

                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    response.body()?.let { overpassResponse ->
                        val locations = overpassResponse.elements.mapNotNull { element ->
                            val lat = if (element.latitude != 0.0) element.latitude else element.center?.lat ?: 0.0
                            val lon = if (element.longitude != 0.0) element.longitude else element.center?.lon ?: 0.0

                            if (lat == 0.0 || lon == 0.0) return@mapNotNull null

                            val point = GeoPoint(lat, lon)
                            if (!isPointInPortugal(point)) return@mapNotNull null

                            element.tags?.let { tags ->
                                val name = tags["name"] ?: tags["brand"] ?: "Local sem nome"
                                if (name == "Local sem nome") return@mapNotNull null

                                val amenity = tags["amenity"] ?: tags["tourism"] ?: tags["leisure"] ?: "Outro"

                                val distance = LocationUtils.calculateDistance(
                                    center.latitude, center.longitude,
                                    lat, lon
                                )

                                LocalSelecionado(
                                    id = element.id.toString(),
                                    nome = name,
                                    categoria = getCategoryDisplayName(amenity),
                                    endereco = buildAddress(tags),
                                    latitude = lat,
                                    longitude = lon,
                                    telefone = tags["phone"] ?: "",
                                    website = tags["website"] ?: "",
                                    distancia = distance
                                )
                            }
                        }.sortedBy { it.distancia }

                        if (locations.isNotEmpty()) {
                            showSearchResults(locations)
                            addLocationMarkers(locations)
                        } else {
                            Toast.makeText(this@LocationSelectionActivity,
                                "Nenhum ${selectedCategory.nomeExibicao.lowercase()} encontrado nesta área",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@LocationSelectionActivity,
                        "Erro ao pesquisar locais próximos", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Log.e(TAG, "Erro na pesquisa de locais próximos", e)
                Toast.makeText(this@LocationSelectionActivity,
                    "Erro de conexão", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isPointInPortugal(point: GeoPoint): Boolean {
        val lat = point.latitude
        val lon = point.longitude

        val minLat = 36.8
        val maxLat = 42.2
        val minLon = -9.7
        val maxLon = -6.0

        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon
    }

    private fun extractLocationName(displayName: String): String {
        val parts = displayName.split(",")
        return when {
            parts.size >= 2 -> {
                "${parts[0].trim()}, ${parts[1].trim()}"
            }
            else -> parts[0].trim()
        }
    }

    private fun getCategoryDisplayName(amenity: String): String {
        return when (amenity.lowercase()) {
            "restaurant" -> "Restaurante"
            "hotel" -> "Hotel"
            "cafe" -> "Café"
            "bar" -> "Bar"
            "park", "garden" -> "Parque"
            "attraction" -> "Atração Turística"
            else -> amenity.replaceFirstChar { it.uppercase() }
        }
    }

    private fun buildAddress(tags: Map<String, String>): String {
        val parts = mutableListOf<String>()

        tags["addr:street"]?.let { parts.add(it) }
        tags["addr:housenumber"]?.let { parts.add(it) }
        tags["addr:city"]?.let { parts.add(it) }

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            "Endereço não disponível"
        }
    }

    private fun showSearchResults(locations: List<LocalSelecionado>) {
        locationsAdapter.updateLocations(locations)
        if (locations.isNotEmpty()) {
            cardResults.visibility = View.VISIBLE
        }
    }

    private fun addLocationMarkers(locations: List<LocalSelecionado>) {
        searchMarkers.forEach { mapView.overlays.remove(it) }
        searchMarkers.clear()

        locations.forEach { location ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(location.latitude, location.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = location.nome
                snippet = "${location.categoria} - ${location.endereco}"

                setOnMarkerClickListener { marker, _ ->
                    selectLocation(location)
                    true
                }
            }

            mapView.overlays.add(marker)
            searchMarkers.add(marker)
        }

        mapView.invalidate()
    }

    private fun addSearchMarkers(locations: List<LocalSelecionado>) {
        searchMarkers.forEach { mapView.overlays.remove(it) }
        searchMarkers.clear()

        locations.forEach { location ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(location.latitude, location.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = location.nome
                snippet = location.endereco

                setOnMarkerClickListener { _, _ ->
                    selectLocation(location)
                    true
                }
            }

            mapView.overlays.add(marker)
            searchMarkers.add(marker)
        }

        mapView.invalidate()
    }

    private fun addLocationMarker(location: GeoPoint, name: String) {
        searchMarkers.forEach { mapView.overlays.remove(it) }
        searchMarkers.clear()

        val marker = Marker(mapView).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = name
        }

        mapView.overlays.add(marker)
        searchMarkers.add(marker)
        mapView.invalidate()
    }

    private fun selectLocation(location: LocalSelecionado) {
        selectedLocation = location
        btnConfirm.isEnabled = true

        val geoPoint = GeoPoint(location.latitude, location.longitude)
        mapView.controller.setCenter(geoPoint)

        Toast.makeText(this, "Local selecionado: ${location.nome}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmLocationSelection() {
        selectedLocation?.let { location ->
            val intent = Intent().apply {
                putExtra("SELECTED_LOCATION_NAME", location.nome)
                putExtra("SELECTED_LOCATION_ADDRESS", location.endereco)
                putExtra("SELECTED_LOCATION_CATEGORY", location.categoria)
                putExtra("SELECTED_LOCATION_LATITUDE", location.latitude)
                putExtra("SELECTED_LOCATION_LONGITUDE", location.longitude)
                putExtra("SELECTED_LOCATION_PHONE", location.telefone)
                putExtra("SELECTED_LOCATION_WEBSITE", location.website)
            }
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    private fun clearSearchResults() {
        locationsAdapter.updateLocations(emptyList())
        cardResults.visibility = View.GONE

        searchMarkers.forEach { mapView.overlays.remove(it) }
        searchMarkers.clear()
        mapView.invalidate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocation()
                } else {
                    Toast.makeText(this,
                        "Permissão de localização necessária para melhor experiência",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()

        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }
}