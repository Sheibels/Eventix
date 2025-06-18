package com.example.eventix

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private val TAG = "ForgotPassword"

    private lateinit var etEmailReset: EditText
    private lateinit var btnSendResetLink: AppCompatButton
    private lateinit var layoutBackToLogin: LinearLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.esqueceu_palavrapasse)

        auth = FirebaseAuth.getInstance()

        etEmailReset = findViewById(R.id.etEmailReset)
        btnSendResetLink = findViewById(R.id.btnSendResetLink)
        layoutBackToLogin = findViewById(R.id.layoutBackToLogin)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        intent.getStringExtra("email")?.let {
            etEmailReset.setText(it)
        }

        handlePasswordResetLink()

        setupListeners()

        Log.d(TAG, "ForgotPasswordActivity iniciada")
    }

    private fun handlePasswordResetLink() {
        val uri = intent.data
        if (uri != null && uri.toString().contains("/__/auth/action")) {
            Log.d(TAG, "Link de redefinição de palavra-passe recebido: $uri")

            val mode = uri.getQueryParameter("mode")
            Log.d(TAG, "Modo: $mode")

            if (mode == "resetPassword") {
                val oobCode = uri.getQueryParameter("oobCode")
                if (!oobCode.isNullOrEmpty()) {
                    Log.d(TAG, "Código de redefinição encontrado, redirecionando para ecrã de início de sessão")

                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun setupListeners() {
        btnSendResetLink.setOnClickListener {
            if (validateEmail()) {
                resetPassword()
            }
        }

        layoutBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateEmail(): Boolean {
        val email = etEmailReset.text.toString().trim()

        when {
            email.isEmpty() -> {
                etEmailReset.error = "Email é obrigatório"
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmailReset.error = "Email inválido"
                return false
            }
            else -> {
                etEmailReset.error = null
                return true
            }
        }
    }

    private fun resetPassword() {
        val email = etEmailReset.text.toString().trim().lowercase()

        showLoading(true)

        Log.d(TAG, "A tentar recuperar palavra-passe para: $email")

        sendResetEmail(email)
    }

    private fun sendResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                showLoading(false)

                if (task.isSuccessful) {
                    Log.d(TAG, "Solicitação de recuperação de palavra-passe processada")
                    showSuccessDialog(email)
                } else {
                    Log.e(TAG, "Erro ao processar recuperação de palavra-passe", task.exception)

                    when (task.exception?.message) {
                        "There is no user record corresponding to this identifier. The user may have been deleted." -> {
                            showEmailNotRegisteredDialog(email)
                        }
                    }
                }
            }
    }

    private fun showEmailNotRegisteredDialog(email: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Email Não Encontrado")
        builder.setMessage("O email $email não está registado no sistema. Deseja criar uma nova conta?")
        builder.setPositiveButton("Criar Conta") { _, _ ->
            val intent = Intent(this, SignupActivity::class.java)
            intent.putExtra("email", email)
            startActivity(intent)
            finish()
        }
        builder.setNegativeButton("Cancelar") { _, _ ->
        }
        builder.show()
    }

    private fun showSuccessDialog(email: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Email Enviado")
        builder.setMessage("Se $email estiver registado no sistema, um link para redefinição de palavra-passe será enviado.\n\n" +
                "Por favor, verifique o seu email e siga as instruções para criar uma nova palavra-passe.\n\n" +
                "Após clicar no link, poderá definir uma nova palavra-passe para aceder à aplicação.")
        builder.setPositiveButton("OK") { _, _ ->
            finish()
        }
        builder.setCancelable(false)
        builder.show()
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        btnSendResetLink.isEnabled = !show
    }
}