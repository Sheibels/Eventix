package com.example.eventix

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManagerMainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "ManagerMainActivity"
    private val REQUEST_NOTIFICATION_PERMISSION = 1001

    private lateinit var btnManageServices: Button
    private lateinit var btnManageEvents: Button
    private lateinit var btnMenu: ImageButton

    private lateinit var menuNotification: LinearLayout
    private lateinit var menuSettings: LinearLayout

    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_manager_main)

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

        requestNotificationPermission()

        initializeViews()
        setupClickListeners()
        setupBottomNavigation()
        setupDrawerNavigation()

        syncEventStatesOnStartup()
        NotificationListener.startListening(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    NotificationService.createNotificationChannel(this)
                }
            }
        }
    }

    private fun initializeViews() {
        btnManageServices = findViewById(R.id.btnManageServices)
        btnManageEvents = findViewById(R.id.btnManageEvents)
        btnMenu = findViewById(R.id.btnMenu)

        menuNotification = findViewById(R.id.menuNotification)
        menuSettings = findViewById(R.id.menuSettings)

        bottomNavigation = findViewById(R.id.bottomNavigation)

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
        btnManageServices.setOnClickListener {
            val intent = Intent(this, ManagerServicesActivity::class.java)
            startActivity(intent)
        }

        btnManageEvents.setOnClickListener {
            val intent = Intent(this, ManagerEventsActivity::class.java)
            startActivity(intent)
        }

        menuNotification.setOnClickListener {
            val intent = Intent(this, ManagerNotificationsActivity::class.java)
            startActivity(intent)
        }

        menuSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.navigation_home

        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    true
                }
                R.id.navigation_services -> {
                    val intent = Intent(this, ManagerServicesActivity::class.java)
                    startActivity(intent)
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

    private fun syncEventStatesOnStartup() {
        val sharedPrefs = getSharedPreferences("EventixManagerPrefs", Context.MODE_PRIVATE)
        val hasSyncedStates = sharedPrefs.getBoolean("has_synced_event_states", false)

        if (!hasSyncedStates) {
            Log.d(TAG, "Primeira execução do gestor - sincronizando estados de eventos...")

            EventStatusManager.syncInconsistentEventStates()

            sharedPrefs.edit()
                .putBoolean("has_synced_event_states", true)
                .apply()

            Log.d(TAG, "Sincronização de estados concluída")
        } else {
            Log.d(TAG, "Estados de eventos já sincronizados anteriormente")
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_notifications -> {
                val intent = Intent(this, ManagerNotificationsActivity::class.java)
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

    override fun onDestroy() {
        super.onDestroy()
        NotificationListener.stopListening()
        NotificationManager.clearRecentNotifications()
    }
}