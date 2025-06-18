package com.example.eventix

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch

class ProfileActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "ProfileActivity"

    private lateinit var btnMenu: ImageButton
    private lateinit var btnEditName: ImageButton
    private lateinit var btnEditPhone: ImageButton
    private lateinit var btnLogout: Button
    private lateinit var btnDeleteAccount: Button
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvEventsCreated: TextView
    private lateinit var tvActiveEvents: TextView
    private lateinit var tvTotalGuests: TextView

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_profile)

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
        loadProfileData()
    }

    private fun initializeViews() {
        btnMenu = findViewById(R.id.btnMenu)
        btnEditName = findViewById(R.id.btnEditName)
        btnEditPhone = findViewById(R.id.btnEditPhone)
        btnLogout = findViewById(R.id.btnLogout)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)
        fabAdd = findViewById(R.id.fabAdd)

        bottomNavigation = findViewById(R.id.bottomNavigation)

        tvName = findViewById(R.id.tvName)
        tvEmail = findViewById(R.id.tvEmail)
        tvPhone = findViewById(R.id.tvPhone)
        tvEventsCreated = findViewById(R.id.tvEventsCreated)
        tvActiveEvents = findViewById(R.id.tvActiveEvents)
        tvTotalGuests = findViewById(R.id.tvTotalGuests)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
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
        btnEditName.setOnClickListener {
            showEditNameDialog()
        }

        btnEditPhone.setOnClickListener {
            showEditPhoneDialog()
        }

        btnLogout.setOnClickListener {
            showConfirmationDialog(
                "Terminar sessão",
                "Tem a certeza que deseja terminar a sua sessão?",
                "Terminar"
            ) {
                performLogout()
            }
        }

        btnDeleteAccount.setOnClickListener {
            showConfirmationDialog(
                "Eliminar conta",
                "Tem a certeza que deseja eliminar a sua conta? Esta ação não pode ser desfeita e irá remover todos os seus convites e ligações de convidados.",
                "Eliminar"
            ) {
                deleteUserAccount()
            }
        }

        fabAdd.setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showEditNameDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Editar Nome")

        val input = EditText(this)
        input.setText(tvName.text.toString())
        builder.setView(input)

        builder.setPositiveButton("Guardar", null)
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = input.text.toString().trim()
            if (validateName(newName)) {
                checkNameAvailability(newName) { isAvailable, suggestedName ->
                    if (isAvailable || newName == tvName.text.toString()) {
                        updateUserName(newName)
                        dialog.dismiss()
                    } else {
                        showNameAlreadyExistsDialog(newName, suggestedName, dialog)
                    }
                }
            }
        }
    }

    private fun showNameAlreadyExistsDialog(originalName: String, suggestedName: String, parentDialog: AlertDialog) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nome Indisponível")
        builder.setMessage("O nome \"$originalName\" já está a ser utilizado. Quer usar \"$suggestedName\" em alternativa?")

        builder.setPositiveButton("Sim") { _, _ ->
            updateUserName(suggestedName)
            parentDialog.dismiss()
        }

        builder.setNegativeButton("Não") { _, _ ->
        }

        builder.show()
    }

    private fun checkNameAvailability(name: String, callback: (Boolean, String) -> Unit) {
        if (name == tvName.text.toString()) {
            callback(true, "")
            return
        }

        val userId = auth.currentUser?.uid ?: ""

        db.collection("utilizadores")
            .whereEqualTo("nome", name)
            .get()
            .addOnSuccessListener { documentsUtilizadores ->
                if (!documentsUtilizadores.isEmpty) {
                    var nameInUse = false
                    for (doc in documentsUtilizadores) {
                        if (doc.id != userId) {
                            nameInUse = true
                            break
                        }
                    }

                    if (nameInUse) {
                        val suggestedName = generateUniqueName(name)
                        callback(false, suggestedName)
                    } else {
                        callback(true, "")
                    }
                } else {
                    db.collection("utilizadores_pendentes")
                        .whereEqualTo("nome", name)
                        .get()
                        .addOnSuccessListener { documentsPendentes ->
                            if (!documentsPendentes.isEmpty) {
                                var nameInUse = false
                                for (doc in documentsPendentes) {
                                    if (doc.id != userId) {
                                        nameInUse = true
                                        break
                                    }
                                }

                                if (nameInUse) {
                                    val suggestedName = generateUniqueName(name)
                                    callback(false, suggestedName)
                                } else {
                                    callback(true, "")
                                }
                            } else {
                                callback(true, "")
                            }
                        }
                        .addOnFailureListener {
                            callback(true, "")
                        }
                }
            }
            .addOnFailureListener {
                callback(true, "")
            }
    }

    private fun generateUniqueName(baseName: String): String {
        return "$baseName${(1000..9999).random()}"
    }

    private fun showEditPhoneDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Editar Telemóvel")

        val input = EditText(this)
        input.setText(tvPhone.text.toString())
        builder.setView(input)

        builder.setPositiveButton("Guardar", null)
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newPhone = input.text.toString().trim()
            if (validatePhone(newPhone)) {
                updateUserPhone(newPhone)
                dialog.dismiss()
            }
        }
    }

    private fun validateName(name: String): Boolean {
        if (name.isEmpty()) {
            Log.d(TAG, "Nome é obrigatório")
            return false
        }

        if (name.length < 2 || name.length > 30) {
            Log.d(TAG, "Nome deve ter entre 2 e 30 caracteres")
            return false
        }

        val nameRegex = Regex("^[\\p{L}\\s0-9]+$")
        if (!nameRegex.matches(name)) {
            Log.d(TAG, "Nome deve conter apenas letras, números e espaços")
            return false
        }

        return true
    }

    private fun validatePhone(phone: String): Boolean {
        if (phone.isEmpty()) {
            Log.d(TAG, "Número de telemóvel é obrigatório")
            return false
        }

        if (phone.length != 9) {
            Log.d(TAG, "Número de telemóvel deve ter 9 dígitos")
            return false
        }

        if (!phone.matches(Regex("^[0-9]{9}$"))) {
            Log.d(TAG, "Número de telemóvel deve conter apenas dígitos")
            return false
        }

        return true
    }

    private fun updateUserName(newName: String) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("utilizadores").document(userId)
                .update("nome", newName)
                .addOnSuccessListener {
                    tvName.text = newName
                    Log.d(TAG, "Nome atualizado com sucesso")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Erro ao atualizar nome", e)
                }
        }
    }

    private fun updateUserPhone(newPhone: String) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("utilizadores").document(userId)
                .update("telemovel", newPhone)
                .addOnSuccessListener {
                    tvPhone.text = newPhone
                    Log.d(TAG, "Telemóvel atualizado com sucesso")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Erro ao atualizar telemóvel", e)
                }
        }
    }

    private fun deleteUserAccount() {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            Log.d(TAG, "Iniciando processo de eliminação de conta...")

            val batch = db.batch()
            var operationsCount = 0

            db.collection("pedidos_convite")
                .whereEqualTo("remetente", userId)
                .get()
                .addOnSuccessListener { documents ->
                    documents.forEach { document ->
                        batch.delete(document.reference)
                        operationsCount++
                        Log.d(TAG, "Adicionado para eliminação: pedido enviado ${document.id}")
                    }

                    db.collection("pedidos_convite")
                        .whereEqualTo("destinatario", userId)
                        .get()
                        .addOnSuccessListener { documents2 ->
                            documents2.forEach { document ->
                                batch.delete(document.reference)
                                operationsCount++
                                Log.d(TAG, "Adicionado para eliminação: pedido recebido ${document.id}")
                            }

                            db.collection("convidados")
                                .whereEqualTo("utilizador1", userId)
                                .get()
                                .addOnSuccessListener { documents3 ->
                                    documents3.forEach { document ->
                                        batch.delete(document.reference)
                                        operationsCount++
                                        Log.d(TAG, "Adicionado para eliminação: convidado como utilizador1 ${document.id}")
                                    }

                                    db.collection("convidados")
                                        .whereEqualTo("utilizador2", userId)
                                        .get()
                                        .addOnSuccessListener { documents4 ->
                                            documents4.forEach { document ->
                                                batch.delete(document.reference)
                                                operationsCount++
                                                Log.d(TAG, "Adicionado para eliminação: convidado como utilizador2 ${document.id}")
                                            }

                                            val userRef = db.collection("utilizadores").document(userId)
                                            batch.delete(userRef)
                                            operationsCount++
                                            Log.d(TAG, "Adicionado para eliminação: documento do utilizador")

                                            if (operationsCount > 0) {
                                                batch.commit()
                                                    .addOnSuccessListener {
                                                        Log.d(TAG, "Todos os documentos relacionados eliminados com sucesso ($operationsCount operações)")

                                                        auth.currentUser?.delete()
                                                            ?.addOnSuccessListener {
                                                                Log.d(TAG, "Conta de autenticação eliminada com sucesso")
                                                                showAlertDialog("Sucesso", "Conta eliminada com sucesso.") {
                                                                    redirectToLogin()
                                                                }
                                                            }
                                                            ?.addOnFailureListener { e ->
                                                                Log.w(TAG, "Erro ao eliminar conta de autenticação: ", e)
                                                                showAlertDialog("Erro", "Erro ao eliminar conta de autenticação.") {
                                                                    redirectToLogin()
                                                                }
                                                            }
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.w(TAG, "Erro ao executar batch de eliminação: ", e)
                                                        showAlertDialog("Erro", "Erro ao eliminar dados da conta.")
                                                    }
                                            } else {
                                                db.collection("utilizadores").document(userId)
                                                    .delete()
                                                    .addOnSuccessListener {
                                                        auth.currentUser?.delete()
                                                            ?.addOnSuccessListener {
                                                                Log.d(TAG, "Conta eliminada com sucesso")
                                                                showAlertDialog("Sucesso", "Conta eliminada com sucesso.") {
                                                                    redirectToLogin()
                                                                }
                                                            }
                                                            ?.addOnFailureListener { e ->
                                                                Log.w(TAG, "Erro ao eliminar conta: ", e)
                                                                showAlertDialog("Erro", "Erro ao eliminar conta.")
                                                            }
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.w(TAG, "Erro ao eliminar documento do utilizador: ", e)
                                                        showAlertDialog("Erro", "Erro ao eliminar documento do utilizador.")
                                                    }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.w(TAG, "Erro ao procurar convidados como utilizador2: ", e)
                                            showAlertDialog("Erro", "Erro ao procurar dados relacionados.")
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Erro ao procurar convidados como utilizador1: ", e)
                                    showAlertDialog("Erro", "Erro ao procurar dados relacionados.")
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Erro ao procurar pedidos como destinatário: ", e)
                            showAlertDialog("Erro", "Erro ao procurar dados relacionados.")
                        }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Erro ao procurar pedidos como remetente: ", e)
                    showAlertDialog("Erro", "Erro ao procurar dados relacionados.")
                }
        }
    }

    private fun loadProfileData() {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            db.collection("utilizadores").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val userName = document.getString("nome") ?: ""
                        val userEmail = document.getString("email") ?: ""
                        val userPhone = document.getString("telemovel") ?: ""
                        val eventsCreated = document.getLong("eventosCriados")?.toInt() ?: 0
                        val activeEvents = document.getLong("eventosAtivos")?.toInt() ?: 0

                        tvName.text = userName
                        tvEmail.text = userEmail
                        tvPhone.text = userPhone
                        tvEventsCreated.text = eventsCreated.toString()
                        tvActiveEvents.text = activeEvents.toString()

                        loadGuestsCount(userId)

                        Log.d(TAG, "Dados do perfil carregados com sucesso")
                    } else {
                        Log.d(TAG, "Documento do utilizador não encontrado")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d(TAG, "Erro ao carregar dados: ", exception)
                }
        }
    }

    private fun loadGuestsCount(userId: String) {
        db.collection("convidados")
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.equalTo("utilizador1", userId),
                    com.google.firebase.firestore.Filter.equalTo("utilizador2", userId)
                )
            )
            .get()
            .addOnSuccessListener { documents ->
                val guestsCount = documents.size()
                tvTotalGuests.text = guestsCount.toString()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao carregar número de convidados", e)
                tvTotalGuests.text = "0"
            }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.navigation_profile

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
                    val intent = Intent(this, CalendarActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_profile -> {
                    true
                }
                else -> false
            }
        }
    }

    private fun showConfirmationDialog(
        title: String,
        message: String,
        positiveButtonText: String,
        onConfirm: () -> Unit
    ) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(positiveButtonText) { dialog, _ ->
            onConfirm()
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
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

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_notifications -> {
                val intent = Intent(this, NotificationsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_guests -> {
                val intent = Intent(this, GuestsActivity::class.java)
                intent.putExtra("from", "ProfileActivity")
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

    private fun performLogout() {
        val sharedPreferences = getSharedPreferences("EventixPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("remember_me", false).apply()

        auth.signOut()
        Log.d(TAG, "Sessão terminada")
        redirectToLogin()
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
}