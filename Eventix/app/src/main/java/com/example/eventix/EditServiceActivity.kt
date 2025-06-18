package com.example.eventix

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditServiceActivity : AppCompatActivity() {

    private val TAG = "EditService"

    private lateinit var btnBack: ImageButton
    private lateinit var etServiceName: EditText
    private lateinit var spinnerServiceType: Spinner
    private lateinit var etServiceCompany: EditText
    private lateinit var etServiceDescription: EditText
    private lateinit var etServiceContact: EditText
    private lateinit var etMinPrice: EditText
    private lateinit var etMaxPrice: EditText
    private lateinit var btnSaveService: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var serviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_edit_service)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            finish()
            return
        }

        RoleManager.isManager { isManager ->
            if (!isManager) {
                finish()
                return@isManager
            }
        }

        serviceId = intent.getStringExtra("SERVICE_ID") ?: ""
        if (serviceId.isEmpty()) {
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        setupSpinner()
        loadServiceData()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        etServiceName = findViewById(R.id.etServiceName)
        spinnerServiceType = findViewById(R.id.spinnerServiceType)
        etServiceCompany = findViewById(R.id.etServiceCompany)
        etServiceDescription = findViewById(R.id.etServiceDescription)
        etServiceContact = findViewById(R.id.etServiceContact)
        etMinPrice = findViewById(R.id.etMinPrice)
        etMaxPrice = findViewById(R.id.etMaxPrice)
        btnSaveService = findViewById(R.id.btnSaveService)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSaveService.setOnClickListener {
            updateService()
        }
    }

    private fun setupSpinner() {
        val serviceTypes = listOf(
            "Selecione o tipo",
            "Entretenimento",
            "Decoração & Ambientação",
            "Catering & Bebidas",
            "Fotografia & Vídeo",
            "Transporte & Logística",
            "Equipamentos & Estruturas",
            "Extras Especiais"
        )

        val adapter = ArrayAdapter(this, R.layout.spinner_item, serviceTypes)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerServiceType.adapter = adapter
    }

    private fun loadServiceData() {
        db.collection("servicos").document(serviceId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    etServiceName.setText(document.getString("nome") ?: "")
                    etServiceCompany.setText(document.getString("empresa") ?: "")
                    etServiceDescription.setText(document.getString("descricao") ?: "")
                    etServiceContact.setText(document.getString("contacto") ?: "")
                    etMinPrice.setText(document.getDouble("precoMinimo")?.toString() ?: "")
                    etMaxPrice.setText(document.getDouble("precoMaximo")?.toString() ?: "")

                    val serviceType = document.getString("tipo") ?: ""
                    val serviceTypes = listOf(
                        "Selecione o tipo",
                        "Entretenimento",
                        "Decoração & Ambientação",
                        "Catering & Bebidas",
                        "Fotografia & Vídeo",
                        "Transporte & Logística",
                        "Equipamentos & Estruturas",
                        "Extras Especiais"
                    )
                    val position = serviceTypes.indexOf(serviceType)
                    if (position >= 0) {
                        spinnerServiceType.setSelection(position)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao carregar dados do serviço", e)
                showAlertDialog("Erro", "Erro ao carregar dados do serviço.") {
                    finish()
                }
            }
    }

    private fun updateService() {
        if (!validateInputs()) {
            return
        }

        val serviceName = etServiceName.text.toString().trim()
        val serviceType = spinnerServiceType.selectedItem.toString()
        val serviceCompany = etServiceCompany.text.toString().trim()
        val serviceDescription = etServiceDescription.text.toString().trim()
        val serviceContact = etServiceContact.text.toString().trim()
        val minPrice = etMinPrice.text.toString().toDoubleOrNull() ?: 0.0
        val maxPrice = etMaxPrice.text.toString().toDoubleOrNull() ?: 0.0

        val serviceData = hashMapOf(
            "nome" to serviceName,
            "tipo" to serviceType,
            "empresa" to serviceCompany,
            "descricao" to serviceDescription,
            "contacto" to serviceContact,
            "precoMinimo" to minPrice,
            "precoMaximo" to maxPrice
        )

        btnSaveService.isEnabled = false
        btnSaveService.text = "A guardar..."

        db.collection("servicos").document(serviceId)
            .update(serviceData as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "Serviço atualizado com sucesso")
                showAlertDialog("Sucesso", "Serviço atualizado com sucesso!") {
                    setResult(RESULT_OK)
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao atualizar serviço", e)
                btnSaveService.isEnabled = true
                btnSaveService.text = "GUARDAR ALTERAÇÕES"
                showAlertDialog("Erro", "Erro ao atualizar serviço. Tente novamente.")
            }
    }

    private fun validateInputs(): Boolean {
        val serviceName = etServiceName.text.toString().trim()
        val serviceType = spinnerServiceType.selectedItem.toString()
        val serviceCompany = etServiceCompany.text.toString().trim()
        val serviceDescription = etServiceDescription.text.toString().trim()
        val serviceContact = etServiceContact.text.toString().trim()
        val minPriceStr = etMinPrice.text.toString().trim()
        val maxPriceStr = etMaxPrice.text.toString().trim()

        if (serviceName.isEmpty()) {
            etServiceName.error = "Nome do serviço é obrigatório"
            etServiceName.requestFocus()
            return false
        }

        if (serviceType == "Selecione o tipo") {
            showAlertDialog("Erro", "Selecione um tipo de serviço.")
            return false
        }

        if (serviceCompany.isEmpty()) {
            etServiceCompany.error = "Nome da empresa é obrigatório"
            etServiceCompany.requestFocus()
            return false
        }

        if (serviceDescription.isEmpty()) {
            etServiceDescription.error = "Descrição é obrigatória"
            etServiceDescription.requestFocus()
            return false
        }

        if (serviceContact.isEmpty()) {
            etServiceContact.error = "Contacto é obrigatório"
            etServiceContact.requestFocus()
            return false
        }

        if (minPriceStr.isEmpty()) {
            etMinPrice.error = "Preço mínimo é obrigatório"
            etMinPrice.requestFocus()
            return false
        }

        if (maxPriceStr.isEmpty()) {
            etMaxPrice.error = "Preço máximo é obrigatório"
            etMaxPrice.requestFocus()
            return false
        }

        val minPrice = minPriceStr.toDoubleOrNull()
        val maxPrice = maxPriceStr.toDoubleOrNull()

        if (minPrice == null || minPrice < 0) {
            etMinPrice.error = "Preço mínimo inválido"
            etMinPrice.requestFocus()
            return false
        }

        if (maxPrice == null || maxPrice < 0) {
            etMaxPrice.error = "Preço máximo inválido"
            etMaxPrice.requestFocus()
            return false
        }

        if (minPrice > maxPrice) {
            etMaxPrice.error = "Preço máximo deve ser maior que o mínimo"
            etMaxPrice.requestFocus()
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
}