package com.example.eventix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvAppVersion: TextView
    private lateinit var layoutTermsOfUse: LinearLayout
    private lateinit var layoutPrivacyPolicy: LinearLayout
    private lateinit var layoutAppVersion: LinearLayout
    private lateinit var layoutRateApp: LinearLayout
    private lateinit var layoutShareApp: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_about)

        initializeViews()
        setupClickListeners()
        setupAppVersion()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        tvAppVersion = findViewById(R.id.tvAppVersion)
        layoutTermsOfUse = findViewById(R.id.layoutTermsOfUse)
        layoutPrivacyPolicy = findViewById(R.id.layoutPrivacyPolicy)
        layoutAppVersion = findViewById(R.id.layoutAppVersion)
        layoutRateApp = findViewById(R.id.layoutRateApp)
        layoutShareApp = findViewById(R.id.layoutShareApp)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        layoutTermsOfUse.setOnClickListener {
            showTermsOfUse()
        }

        layoutPrivacyPolicy.setOnClickListener {
            showPrivacyPolicy()
        }

        layoutAppVersion.setOnClickListener {
            showVersionInfo()
        }

        layoutRateApp.setOnClickListener {
            openPlayStore()
        }

        layoutShareApp.setOnClickListener {
            shareApp()
        }
    }

    private fun setupAppVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = "Versão ${packageInfo.versionName}"
        } catch (e: Exception) {
            tvAppVersion.text = "Versão 1.0.0"
        }
    }

    private fun showTermsOfUse() {
        val termsText = """
            TERMOS DE UTILIZAÇÃO DO EVENTIX

            1. ACEITAÇÃO DOS TERMOS
            Ao utilizar a aplicação Eventix, concorda com estes termos de utilização.

            2. DESCRIÇÃO DO SERVIÇO
            A Eventix é uma plataforma que conecta utilizadores a gestores de eventos profissionais para facilitar o planeamento e organização de eventos.

            3. RESPONSABILIDADES DO UTILIZADOR
            • Fornecer informações verdadeiras e precisas
            • Manter a confidencialidade das suas credenciais
            • Utilizar a aplicação de forma respeitosa
            • Não partilhar conteúdo inadequado ou ofensivo

            4. RESPONSABILIDADES DOS GESTORES
            • Prestar serviços profissionais de qualidade
            • Manter comunicação clara com os clientes
            • Cumprir prazos e compromissos assumidos
            • Respeitar a privacidade dos utilizadores

            5. PAGAMENTOS E CANCELAMENTOS
            • Os preços dos serviços são definidos pelos gestores
            • Cancelamentos devem seguir as políticas acordadas
            • A plataforma não é responsável por disputas financeiras entre utilizadores e gestores

            6. PRIVACIDADE
            • Os seus dados pessoais são protegidos
            • As conversas são privadas entre participantes
            • Não partilhamos informações com terceiros sem consentimento

            7. LIMITAÇÃO DE RESPONSABILIDADE
            • A aplicação é fornecida "como está"
            • Não garantimos disponibilidade contínua
            • Não somos responsáveis por ações de terceiros

            8. MODIFICAÇÕES
            Reservamo-nos o direito de alterar estes termos a qualquer momento.

            Data de vigência: Junho 2025
        """.trimIndent()

        showScrollableDialog("Termos de Utilização", termsText)
    }

    private fun showPrivacyPolicy() {
        val privacyText = """
            POLÍTICA DE PRIVACIDADE DO EVENTIX

            1. INFORMAÇÕES QUE RECOLHEMOS
            • Nome e endereço de email
            • Número de telemóvel (opcional)
            • Informações dos eventos criados
            • Mensagens enviadas na plataforma
            • Dados de utilização da aplicação

            2. COMO UTILIZAMOS AS INFORMAÇÕES
            • Para fornecer e melhorar os nossos serviços
            • Para facilitar a comunicação entre utilizadores
            • Para personalizar a experiência do utilizador
            • Para enviar notificações importantes
            • Para análise estatística anónima

            3. PARTILHA DE INFORMAÇÕES
            • Não vendemos os seus dados pessoais
            • Partilhamos apenas com gestores dos seus eventos
            • Podemos partilhar dados anónimos para estatísticas
            • Cumprimos ordens judiciais quando legalmente obrigatórias

            4. SEGURANÇA DOS DADOS
            • Utilizamos o Firebase/Google para armazenamento seguro
            • Implementamos medidas de segurança apropriadas
            • As conversas são encriptadas em trânsito
            • Acesso restrito aos dados por função

            5. OS SEUS DIREITOS
            • Aceder aos seus dados pessoais
            • Corrigir informações incorretas
            • Eliminar a sua conta e dados
            • Exportar os seus dados
            • Retirar consentimentos

            6. RETENÇÃO DE DADOS
            • Mantemos os dados enquanto a conta estiver ativa
            • Dados de eventos podem ser mantidos para histórico
            • Pode solicitar eliminação a qualquer momento
            • Alguns dados podem ser mantidos por obrigações legais

            7. COOKIES E TECNOLOGIAS SIMILARES
            • Utilizamos tecnologias para melhorar a experiência
            • Dados de utilização para otimização da aplicação
            • Pode controlar estas preferências nas definições

            8. ALTERAÇÕES À POLÍTICA
            Notificaremos sobre alterações significativas à política de privacidade.

            9. CONTACTO
            Para questões sobre privacidade: suporte@eventix.pt

            Data de vigência: Junho 2025
        """.trimIndent()

        showScrollableDialog("Política de Privacidade", privacyText)
    }

    private fun showVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName

            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val androidVersion = android.os.Build.VERSION.RELEASE
            val sdkVersion = android.os.Build.VERSION.SDK_INT
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

            val versionText = """
            INFORMAÇÕES DA VERSÃO

            Nome da Aplicação: Eventix
            Versão: $versionName
            Código da Versão: $versionCode
            Plataforma: Android $androidVersion (API $sdkVersion)
            Dispositivo: $deviceModel
            
            FUNCIONALIDADES PRINCIPAIS:
            • Criação e gestão de eventos
            • Sistema de convites entre utilizadores
            • Chat integrado com gestores
            • Calendário visual de eventos
            • Catálogo de serviços profissionais
            • Estados dinâmicos dos eventos
            • Notificações em tempo real
            
            © 2025 Eventix. Todos os direitos reservados.
        """.trimIndent()

            showScrollableDialog("Informações da Versão", versionText)

        } catch (e: Exception) {
            val fallbackText = """
            INFORMAÇÕES DA VERSÃO

            Nome da Aplicação: Eventix
            Versão: 1.0.0
            Plataforma: Android
            
            FUNCIONALIDADES PRINCIPAIS:
            • Criação e gestão de eventos
            • Sistema de convites entre utilizadores
            • Chat integrado com gestores
            • Calendário visual de eventos
            • Catálogo de serviços profissionais

            Desenvolvido para facilitar a organização dos seus eventos.
            
            © 2025 Eventix. Todos os direitos reservados.
        """.trimIndent()

            showScrollableDialog("Informações da Versão", fallbackText)
        }
    }

    private fun openPlayStore() {
        val packageName = packageName

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                startActivity(intent)
            } catch (e: Exception) {
                showSimpleDialog("Erro", "Não foi possível abrir a Play Store.")
            }
        }
    }

    private fun shareApp() {
        val shareText = """
            Experimenta a Eventix! 🎉
            
            A melhor aplicação para organizar os teus eventos com gestores profissionais.
            
            Descarrega já: https://play.google.com/store/apps/details?id=$packageName
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Eventix - Gestão de Eventos")
        }

        try {
            startActivity(Intent.createChooser(intent, "Partilhar Eventix"))
        } catch (e: Exception) {
            showSimpleDialog("Erro", "Não foi possível partilhar a aplicação.")
        }
    }

    private fun showScrollableDialog(title: String, content: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun showSimpleDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}