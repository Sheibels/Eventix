package com.example.eventix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SupportActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var layoutContactSupport: LinearLayout
    private lateinit var rvFaq: RecyclerView
    private lateinit var faqAdapter: FaqAdapter

    private val faqList = listOf(
        FaqItem(
            "Como criar um evento?",
            "Para criar um evento, toque no botão '+' na página principal. Preencha as informações básicas como tipo de evento, data, hora, localização e número de participantes. Depois selecione os serviços desejados e convide outros utilizadores se necessário."
        ),
        FaqItem(
            "Como funciona o sistema de gestores?",
            "Os gestores são profissionais especializados em organização de eventos. Após criar um evento, um gestor pode associar-se ao seu evento e oferece serviços profissionais. O gestor confirma o evento e vocês podem comunicar através do chat integrado."
        ),
        FaqItem(
            "Como convidar outros utilizadores?",
            "No separador lateral, aceda a 'Convidados' para ver a sua lista de contactos na aplicação. Pode enviar pedidos de convite a outros utilizadores. Uma vez aceites, podem ser convidados para os seus eventos."
        ),
        FaqItem(
            "Quando posso enviar mensagens?",
            "As mensagens ficam disponíveis após o evento ser confirmado pelo gestor. Não é possível enviar mensagens enquanto o evento estiver 'Por Confirmar'. Cada evento tem o seu próprio chat para manter as conversas organizadas."
        ),
        FaqItem(
            "Posso editar um evento depois de criado?",
            "Sim, mas apenas enquanto o evento estiver 'Por Confirmar'. Após ser confirmado pelo gestor, não é possível editar os detalhes do evento para manter a integridade do planeamento."
        ),
        FaqItem(
            "Como funciona a selecção de serviços?",
            "Na criação do evento, pode escolher múltiplos serviços profissionais como fotografia, catering, música, etc. Cada serviço tem um preço definido pelos gestores. O custo total é calculado automaticamente."
        ),
        FaqItem(
            "As minhas conversas são privadas?",
            "Sim, todas as conversas são privadas entre si e o gestor do evento. Cada evento tem o seu chat próprio, e apenas os participantes do evento podem aceder às mensagens."
        ),
        FaqItem(
            "Como posso ver eventos de outros utilizadores?",
            "No separador 'Eventos', pode alternar entre 'Os Meus Eventos' e 'Eventos de Outros'. Nos eventos de outros, vê apenas as informações básicas desse evento."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_support)

        initializeViews()
        setupClickListeners()
        setupRecyclerView()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        layoutContactSupport = findViewById(R.id.layoutContactSupport)
        rvFaq = findViewById(R.id.rvFaq)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        layoutContactSupport.setOnClickListener {
            openEmailApp()
        }
    }

    private fun setupRecyclerView() {
        faqAdapter = FaqAdapter(faqList)
        rvFaq.adapter = faqAdapter
        rvFaq.layoutManager = LinearLayoutManager(this)
    }

    private fun openEmailApp() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("suporte@eventix.pt"))
            putExtra(Intent.EXTRA_SUBJECT, "Eventix - Pedido de Suporte")
            putExtra(Intent.EXTRA_TEXT, "Olá,\n\nPreciso de ajuda com:\n\n\nDispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nVersão Android: ${android.os.Build.VERSION.RELEASE}\n\nDescreva o seu problema:")
        }

        try {
            startActivity(Intent.createChooser(intent, "Enviar email"))
        } catch (e: Exception) {
            showSimpleDialog("Erro", "Não foi possível abrir a aplicação de email. Pode contactar-nos em: suporte@eventix.pt")
        }
    }

    private fun showSimpleDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}

data class FaqItem(
    val question: String,
    val answer: String,
    var isExpanded: Boolean = false
)