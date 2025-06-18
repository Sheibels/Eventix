package com.example.eventix

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private val TAG = "LoginActivity"
    private val RC_SIGN_IN = 9001

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var btnGoogleSignIn: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvSignUp: TextView
    private lateinit var switchRemember: SwitchCompat

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var sharedPreferences: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.fazer_login)

        sharedPreferences = getSharedPreferences("EventixPrefs", Context.MODE_PRIVATE)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvSignUp = findViewById(R.id.tvSignUp)
        switchRemember = findViewById(R.id.switchRemember)

        val rememberMe = sharedPreferences.getBoolean("remember_me", false)
        switchRemember.isChecked = rememberMe

        handleAuthActionLink()

        setupListeners()
    }

    private fun handleAuthActionLink() {
        val uri = intent.data
        if (uri != null && uri.toString().contains("/__/auth/action")) {
            Log.d(TAG, "Auth link recebido: $uri")

            val mode = uri.getQueryParameter("mode")
            val oobCode = uri.getQueryParameter("oobCode")
            val email = uri.getQueryParameter("email")

            Log.d(TAG, "Modo de autenticação: $mode, Email: $email")

            when (mode) {
                "verifyEmail" -> {
                    if (!oobCode.isNullOrEmpty()) {
                        verifyEmail(oobCode, email)
                    }
                }
                "resetPassword" -> {
                    val intent = Intent(this, ForgotPasswordActivity::class.java)
                    intent.data = uri
                    startActivity(intent)
                }
            }
        }
    }

    private fun verifyEmail(oobCode: String, email: String?) {
        Log.d(TAG, "A verificar email com código: $oobCode")

        auth.applyActionCode(oobCode)
            .addOnSuccessListener {
                Log.d(TAG, "Email verificado com sucesso!")

                if (!email.isNullOrEmpty()) {
                    moveUserDataByEmail(email)
                } else {
                    showEmailVerifiedDialog()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao verificar email: ${e.message}")
            }
    }

    private fun moveUserDataByEmail(email: String) {
        db.collection("utilizadores_pendentes")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    val userId = document.id
                    val userData = document.data

                    if (userData != null) {
                        userData["emailVerificado"] = true

                        db.collection("utilizadores").document(userId)
                            .set(userData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Dados movidos para coleção utilizadores")

                                db.collection("utilizadores_pendentes").document(userId)
                                    .delete()
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Dados removidos de utilizadores_pendentes")
                                    }

                                showEmailVerifiedDialog()
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Erro ao mover dados: ${e.message}")
                                showEmailVerifiedDialog()
                            }
                    } else {
                        showEmailVerifiedDialog()
                    }
                } else {
                    checkIfAlreadyVerified(email)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao buscar utilizador: ${e.message}")
                showEmailVerifiedDialog()
            }
    }

    private fun checkIfAlreadyVerified(email: String) {
        db.collection("utilizadores")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    val userId = document.id

                    db.collection("utilizadores").document(userId)
                        .update("emailVerificado", true)
                        .addOnSuccessListener {
                            Log.d(TAG, "Estado de verificação atualizado")
                        }
                }
                showEmailVerifiedDialog()
            }
            .addOnFailureListener {
                showEmailVerifiedDialog()
            }
    }

    private fun showEmailVerifiedDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Email Verificado")
        builder.setMessage("O seu email foi verificado com sucesso! Agora pode iniciar sessão com a sua conta.")
        builder.setPositiveButton("OK") { _, _ ->
        }
        builder.setCancelable(false)
        builder.show()
    }

    private fun setupListeners() {
        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (validateInputs(email, password)) {
                signInWithEmailPassword(email, password)
            }
        }

        switchRemember.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("remember_me", isChecked).apply()
        }

        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        tvForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            if (etEmail.text.toString().isNotEmpty()) {
                intent.putExtra("email", etEmail.text.toString().trim())
            }
            startActivity(intent)
        }

        tvSignUp.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun signInWithEmailPassword(email: String, password: String) {
        btnSignIn.isEnabled = false
        btnSignIn.text = "A entrar..."

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")

                    val user = auth.currentUser
                    if (user != null) {
                        user.reload().addOnCompleteListener {
                            if (user.isEmailVerified) {
                                checkPendingVerification(user.uid)
                            } else {
                                btnSignIn.isEnabled = true
                                btnSignIn.text = "INICIAR SESSÃO"

                                showEmailNotVerifiedDialog(email)
                            }
                        }
                    } else {
                        btnSignIn.isEnabled = true
                        btnSignIn.text = "INICIAR SESSÃO"
                    }
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    btnSignIn.isEnabled = true
                    btnSignIn.text = "INICIAR SESSÃO"
                }
            }
    }

    private fun showEmailNotVerifiedDialog(email: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Email Não Verificado")
        builder.setMessage("O seu email ainda não foi verificado. Verifique a sua caixa de entrada e clique no link de verificação.\n\nDeseja que enviemos um novo link?")

        builder.setPositiveButton("Enviar novo link") { _, _ ->
            btnSignIn.isEnabled = false
            btnSignIn.text = "A enviar..."

            val user = auth.currentUser

            if (user != null) {
                user.sendEmailVerification()
                    .addOnCompleteListener { task ->
                        btnSignIn.isEnabled = true
                        btnSignIn.text = "INICIAR SESSÃO"

                        auth.signOut()
                    }
            } else {
                btnSignIn.isEnabled = true
                btnSignIn.text = "INICIAR SESSÃO"

                auth.signOut()
            }
        }

        builder.setNegativeButton("Cancelar") { _, _ ->
            auth.signOut()
        }

        builder.setCancelable(false)
        builder.show()
    }

    private fun checkPendingVerification(userId: String) {
        db.collection("utilizadores_pendentes").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userData = document.data
                    if (userData != null) {
                        userData["emailVerificado"] = true

                        db.collection("utilizadores").document(userId)
                            .set(userData)
                            .addOnSuccessListener {
                                db.collection("utilizadores_pendentes").document(userId)
                                    .delete()
                                    .addOnSuccessListener {
                                        val rememberMe = switchRemember.isChecked
                                        sharedPreferences.edit().putBoolean("remember_me", rememberMe).apply()

                                        getUserDataAndNavigate(userId)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w(TAG, "Erro ao excluir dados pendentes: ${e.message}")
                                        getUserDataAndNavigate(userId)
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Erro ao mover dados: ${e.message}")
                                getUserDataAndNavigate(userId)
                            }
                    } else {
                        getUserDataAndNavigate(userId)
                    }
                } else {
                    db.collection("utilizadores").document(userId)
                        .get()
                        .addOnSuccessListener { mainDocument ->
                            if (mainDocument.exists()) {
                                val rememberMe = switchRemember.isChecked
                                sharedPreferences.edit().putBoolean("remember_me", rememberMe).apply()

                                getUserDataAndNavigate(userId)
                            } else {
                                createDefaultUserProfile(userId)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Erro ao verificar coleção principal: ${e.message}")
                            createDefaultUserProfile(userId)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao verificar coleção pendentes: ${e.message}")
                db.collection("utilizadores").document(userId)
                    .get()
                    .addOnSuccessListener { mainDocument ->
                        if (mainDocument.exists()) {
                            getUserDataAndNavigate(userId)
                        } else {
                            createDefaultUserProfile(userId)
                        }
                    }
                    .addOnFailureListener { e2 ->
                        Log.w(TAG, "Erro ao verificar coleção principal: ${e2.message}")
                        createDefaultUserProfile(userId)
                    }
            }
    }

    private fun createDefaultUserProfile(userId: String) {
        val user = auth.currentUser
        if (user != null) {
            val name = user.displayName ?: ""
            val email = user.email ?: ""

            checkNameAvailability(name) { isAvailable, uniqueName ->
                val userData = hashMapOf(
                    "nome" to uniqueName,
                    "email" to email,
                    "telemovel" to "",
                    "eventosCriados" to 0,
                    "eventosAtivos" to 0,
                    "totalConvidados" to 0,
                    "emailVerificado" to true
                )

                db.collection("utilizadores").document(userId)
                    .set(userData)
                    .addOnSuccessListener {
                        val rememberMe = switchRemember.isChecked
                        sharedPreferences.edit().putBoolean("remember_me", rememberMe).apply()

                        getUserDataAndNavigate(userId)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Erro ao criar perfil padrão: ${e.message}")
                        navigateToMainActivity()
                    }
            }
        } else {
            btnSignIn.isEnabled = true
            btnSignIn.text = "INICIAR SESSÃO"
        }
    }

    private fun checkNameAvailability(name: String, callback: (Boolean, String) -> Unit) {
        if (name.isEmpty()) {
            callback(true, name)
            return
        }

        db.collection("utilizadores")
            .whereEqualTo("nome", name)
            .get()
            .addOnSuccessListener { documentsUtilizadores ->
                if (!documentsUtilizadores.isEmpty) {
                    val uniqueName = generateUniqueName(name)
                    callback(false, uniqueName)
                } else {
                    db.collection("utilizadores_pendentes")
                        .whereEqualTo("nome", name)
                        .get()
                        .addOnSuccessListener { documentsPendentes ->
                            if (!documentsPendentes.isEmpty) {
                                val uniqueName = generateUniqueName(name)
                                callback(false, uniqueName)
                            } else {
                                callback(true, name)
                            }
                        }
                        .addOnFailureListener {
                            val uniqueName = generateUniqueName(name)
                            callback(false, uniqueName)
                        }
                }
            }
            .addOnFailureListener {
                val uniqueName = generateUniqueName(name)
                callback(false, uniqueName)
            }
    }

    private fun generateUniqueName(baseName: String): String {
        return "$baseName${(1000..9999).random()}"
    }

    private fun signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)

        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")

                    val rememberMe = switchRemember.isChecked
                    sharedPreferences.edit().putBoolean("remember_me", rememberMe).apply()

                    val user = auth.currentUser

                    checkIfUserExistsInFirestore(user?.uid, account)
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                }
            }
    }

    private fun checkIfUserExistsInFirestore(userId: String?, account: GoogleSignInAccount) {
        if (userId == null) {
            return
        }

        db.collection("utilizadores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    getUserDataAndNavigate(userId)
                } else {
                    createUserInFirestore(userId, account)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error checking user document", e)
            }
    }

    private fun createUserInFirestore(userId: String, account: GoogleSignInAccount) {
        val name = account.displayName ?: ""
        val email = account.email ?: ""

        checkNameAvailability(name) { isAvailable, uniqueName ->
            val user = hashMapOf(
                "nome" to uniqueName,
                "email" to email,
                "telemovel" to "",
                "eventosCriados" to 0,
                "eventosAtivos" to 0,
                "totalConvidados" to 0,
                "emailVerificado" to true
            )

            db.collection("utilizadores").document(userId)
                .set(user)
                .addOnSuccessListener {
                    Log.d(TAG, "User data added successfully")
                    navigateToMainActivity()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error adding user data", e)
                    navigateToMainActivity()
                }
        }
    }

    private fun getUserDataAndNavigate(userId: String?) {
        if (userId == null) {
            navigateToMainActivity()
            return
        }

        db.collection("utilizadores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val nome = document.getString("nome") ?: ""
                    Log.d(TAG, "Bem-vindo, $nome!")
                }
                navigateToMainActivity()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error getting user data", e)
                navigateToMainActivity()
            }
    }

    private fun navigateToMainActivity() {
        RoleManager.checkUserRole { role ->
            val intent = when (role) {
                UserRole.GESTOR -> Intent(this, ManagerMainActivity::class.java)
                UserRole.UTILIZADOR -> Intent(this, MainActivity::class.java)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            etEmail.error = "Email é obrigatório"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Email inválido"
            isValid = false
        }

        if (password.isEmpty()) {
            etPassword.error = "Palavra-passe é obrigatória"
            isValid = false
        }

        return isValid
    }

    override fun onStart() {
        super.onStart()

        val rememberMe = sharedPreferences.getBoolean("remember_me", false)
        switchRemember.isChecked = rememberMe

        if (rememberMe && auth.currentUser != null) {
            navigateToMainActivity()
        }
    }
}