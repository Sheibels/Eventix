package com.example.eventix

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private val TAG = "SignupActivity"

    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var signupButton: Button
    private lateinit var signinText: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.criar_conta)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        nameInput = findViewById(R.id.name_input)
        emailInput = findViewById(R.id.email_input)
        phoneInput = findViewById(R.id.phone_input)
        passwordInput = findViewById(R.id.password_input)
        confirmPasswordInput = findViewById(R.id.confirm_password_input)
        passwordLayout = findViewById(R.id.password_layout)
        confirmPasswordLayout = findViewById(R.id.confirm_password_layout)
        signupButton = findViewById(R.id.signup_button)
        signinText = findViewById(R.id.signin_text)

        intent.getStringExtra("email")?.let {
            emailInput.setText(it)
        }

        signupButton.setOnClickListener {
            validateAndSignup()
        }

        signinText.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun validateAndSignup() {
        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        if (name.isEmpty()) {
            nameInput.error = "Nome é obrigatório"
            nameInput.requestFocus()
            return
        }

        if (name.length < 2 || name.length > 30) {
            nameInput.error = "Nome deve ter entre 2 e 30 caracteres"
            nameInput.requestFocus()
            return
        }

        val nameRegex = Regex("^[\\p{L}\\s0-9]+$")
        if (!nameRegex.matches(name)) {
            nameInput.error = "Nome deve conter apenas letras, números e espaços"
            nameInput.requestFocus()
            return
        }

        if (email.isEmpty()) {
            emailInput.error = "Email é obrigatório"
            emailInput.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Insira um email válido"
            emailInput.requestFocus()
            return
        }

        if (phone.isEmpty()) {
            phoneInput.error = "Número de telemóvel é obrigatório"
            phoneInput.requestFocus()
            return
        }

        if (phone.length != 9) {
            phoneInput.error = "Número de telemóvel deve ter 9 dígitos"
            phoneInput.requestFocus()
            return
        }

        if (!phone.matches(Regex("^[0-9]{9}$"))) {
            phoneInput.error = "Número de telemóvel deve conter apenas dígitos"
            phoneInput.requestFocus()
            return
        }

        if (password.isEmpty()) {
            passwordInput.error = "Palavra-passe é obrigatória"
            passwordInput.requestFocus()
            return
        }

        if (password.length < 8) {
            passwordInput.error = "Palavra-passe deve ter 8 caracteres no mínimo"
            passwordInput.requestFocus()
            return
        }

        val hasUpperCase = password.contains(Regex("[A-Z]"))
        val hasLowerCase = password.contains(Regex("[a-z]"))
        val hasNumber = password.contains(Regex("[0-9]"))
        val hasSpecialChar = password.contains(Regex("[^A-Za-z0-9]"))

        if (!hasUpperCase) {
            passwordInput.error = "Palavra-passe deve conter pelo menos uma letra maiúscula"
            passwordInput.requestFocus()
            return
        }

        if (!hasLowerCase) {
            passwordInput.error = "Palavra-passe deve conter pelo menos uma letra minúscula"
            passwordInput.requestFocus()
            return
        }

        if (!hasNumber) {
            passwordInput.error = "Palavra-passe deve conter pelo menos um número"
            passwordInput.requestFocus()
            return
        }

        if (!hasSpecialChar) {
            passwordInput.error = "Palavra-passe deve conter pelo menos um carácter especial"
            passwordInput.requestFocus()
            return
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordInput.error = "Confirme a sua palavra-passe"
            confirmPasswordInput.requestFocus()
            return
        }

        if (password != confirmPassword) {
            confirmPasswordInput.error = "As palavras-passe não coincidem"
            confirmPasswordInput.requestFocus()
            return
        }

        checkNameAvailability(name) { isAvailable, suggestedName ->
            if (!isAvailable) {
                showNameAlreadyExistsDialog(name, suggestedName)
            } else {
                signupButton.isEnabled = false
                signupButton.text = "A registar..."
                createUserWithFirebaseAuth(email, password, name, phone)
            }
        }
    }

    private fun checkNameAvailability(name: String, callback: (Boolean, String) -> Unit) {
        db.collection("utilizadores")
            .whereEqualTo("nome", name)
            .get()
            .addOnSuccessListener { documentsUtilizadores ->
                if (!documentsUtilizadores.isEmpty) {
                    val suggestedName = generateUniqueName(name)
                    callback(false, suggestedName)
                } else {
                    db.collection("utilizadores_pendentes")
                        .whereEqualTo("nome", name)
                        .get()
                        .addOnSuccessListener { documentsPendentes ->
                            if (!documentsPendentes.isEmpty) {
                                val suggestedName = generateUniqueName(name)
                                callback(false, suggestedName)
                            } else {
                                callback(true, "")
                            }
                        }
                        .addOnFailureListener {
                            val suggestedName = generateUniqueName(name)
                            callback(false, suggestedName)
                        }
                }
            }
            .addOnFailureListener {
                val suggestedName = generateUniqueName(name)
                callback(false, suggestedName)
            }
    }

    private fun generateUniqueName(baseName: String): String {
        return "$baseName${(1000..9999).random()}"
    }

    private fun showNameAlreadyExistsDialog(originalName: String, suggestedName: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nome Indisponível")
        builder.setMessage("O nome \"$originalName\" já está a ser utilizado. Quer usar \"$suggestedName\" em alternativa?")

        builder.setPositiveButton("Sim") { _, _ ->
            nameInput.setText(suggestedName)
            val email = emailInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val password = passwordInput.text.toString()

            signupButton.isEnabled = false
            signupButton.text = "A registar..."
            createUserWithFirebaseAuth(email, password, suggestedName, phone)
        }

        builder.setNegativeButton("Não") { _, _ ->
            nameInput.error = "Introduza um nome diferente"
            nameInput.requestFocus()
        }

        builder.setCancelable(false)
        builder.show()
    }

    private fun createUserWithFirebaseAuth(email: String, password: String, name: String, phone: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    if (user != null) {
                        storeUserData(user.uid, name, email, phone)
                    } else {
                        signupButton.isEnabled = true
                        signupButton.text = "REGISTAR"
                        Log.e(TAG, "Erro ao obter utilizador após registo")
                    }
                } else {
                    signupButton.isEnabled = true
                    signupButton.text = "REGISTAR"
                    Log.e(TAG, "Falha no registo: ${task.exception?.message}")
                }
            }
    }

    private fun storeUserData(userId: String, name: String, email: String, phone: String) {
        val user = hashMapOf(
            "nome" to name,
            "email" to email,
            "telemovel" to phone,
            "eventosCriados" to 0,
            "eventosAtivos" to 0,
            "totalConvidados" to 0,
            "emailVerificado" to false
        )

        db.collection("utilizadores_pendentes").document(userId)
            .set(user)
            .addOnSuccessListener {
                Log.d(TAG, "Dados do utilizador guardados com sucesso em utilizadores_pendentes")

                val currentUser = auth.currentUser
                if (currentUser != null) {
                    currentUser.sendEmailVerification()
                        .addOnCompleteListener { verificationTask ->
                            signupButton.isEnabled = true
                            signupButton.text = "REGISTAR"

                            if (!verificationTask.isSuccessful) {
                                Log.e(TAG, "Falha ao enviar email de verificação: ${verificationTask.exception?.message}")
                            } else {
                                Log.d(TAG, "Verificação de email enviada com sucesso")

                                auth.signOut()

                                showVerificationEmailDialog(email)
                            }
                        }
                } else {
                    signupButton.isEnabled = true
                    signupButton.text = "REGISTAR"
                    Log.e(TAG, "Erro: Utilizador não encontrado")
                }
            }
            .addOnFailureListener { e ->
                signupButton.isEnabled = true
                signupButton.text = "REGISTAR"
                Log.e(TAG, "Erro ao guardar dados: ${e.message}")
            }
    }

    private fun showVerificationEmailDialog(email: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Verificação de Email")
        builder.setMessage("Foi enviado um link de verificação para $email.\n\n" +
                "Por favor, verifique o seu email e clique no link para ativar a sua conta.")
        builder.setPositiveButton("OK") { _, _ ->
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
        builder.setCancelable(false)
        builder.show()
    }
}