package com.example.eventix

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "MainActivity"
    private val REQUEST_NOTIFICATION_PERMISSION = 1001

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsLayout: LinearLayout
    private lateinit var carouselAdapter: CarouselAdapter
    private val sliderHandler = Handler(Looper.getMainLooper())
    private val sliderRunnable = Runnable { viewPager.currentItem = (viewPager.currentItem + 1) % 5 }

    private lateinit var btnCreateEvent: Button
    private lateinit var btnViewDetails: Button
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var btnMenu: ImageButton

    private lateinit var menuNotification: LinearLayout
    private lateinit var menuGuests: LinearLayout
    private lateinit var menuSettings: LinearLayout
    private lateinit var menuHelp: LinearLayout
    private lateinit var menuAbout: LinearLayout

    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        requestNotificationPermission()

        initializeViews()
        setupClickListeners()
        setupBottomNavigation()
        setupDrawerNavigation()
        checkEventReminders()

        NotificationListener.startListening(this)

        viewPager.post {
            setupCarousel()
        }
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
        viewPager = findViewById(R.id.viewPager)
        dotsLayout = findViewById(R.id.layoutDots)

        dotsLayout.visibility = View.VISIBLE

        btnCreateEvent = findViewById(R.id.btnCreateEvent)
        btnViewDetails = findViewById(R.id.btnViewDetails)
        fabAdd = findViewById(R.id.fabAdd)
        btnMenu = findViewById(R.id.btnMenu)

        menuNotification = findViewById(R.id.menuNotification)
        menuGuests = findViewById(R.id.menuGuests)
        menuSettings = findViewById(R.id.menuSettings)
        menuHelp = findViewById(R.id.menuHelp)
        menuAbout = findViewById(R.id.menuAbout)

        bottomNavigation = findViewById(R.id.bottomNavigation)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        setupInitialDots()
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

    private fun setupInitialDots() {
        dotsLayout.removeAllViews()

        for (i in 0 until 5) {
            val dot = ImageView(this)
            if (i == 0) {
                dot.setImageResource(R.drawable.active_dot)
            } else {
                dot.setImageResource(R.drawable.inactive_dot)
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            dotsLayout.addView(dot, params)
        }
    }

    private fun setupCarousel() {
        val carouselImages = listOf(
            R.drawable.carousel_image1,
            R.drawable.carousel_image2,
            R.drawable.carousel_image3,
            R.drawable.carousel_image4,
            R.drawable.carousel_image5
        )

        carouselAdapter = CarouselAdapter(carouselImages)
        viewPager.adapter = carouselAdapter

        viewPager.post {
            addDotsIndicator(0)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                addDotsIndicator(position)
                sliderHandler.removeCallbacks(sliderRunnable)
                sliderHandler.postDelayed(sliderRunnable, 5500)
            }
        })
    }

    private fun addDotsIndicator(position: Int) {
        val dots = arrayOfNulls<ImageView>(5)
        dotsLayout.removeAllViews()

        for (i in dots.indices) {
            dots[i] = ImageView(this)
            if (i == position) {
                dots[i]?.setImageResource(R.drawable.active_dot)
            } else {
                dots[i]?.setImageResource(R.drawable.inactive_dot)
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            dots[i]?.layoutParams = params
            dotsLayout.addView(dots[i], params)
        }

        dotsLayout.requestLayout()
        dotsLayout.invalidate()
    }

    private fun checkEventReminders() {
        val sharedPrefs = getSharedPreferences("EventixPrefs", Context.MODE_PRIVATE)
        val lastCheck = sharedPrefs.getLong("last_reminder_check", 0)
        val now = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000

        if (now - lastCheck > oneDayInMillis) {
            auth.currentUser?.let { user ->
                NotificationManager.sendEventReminderNotifications(this, user.uid)
                sharedPrefs.edit().putLong("last_reminder_check", now).apply()
            }
        }
    }

    private fun setupClickListeners() {
        btnCreateEvent.setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivity(intent)
        }

        btnViewDetails.setOnClickListener {
            val intent = Intent(this, EventDetailsActivity::class.java)
            startActivity(intent)
        }

        fabAdd.setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivity(intent)
        }

        menuNotification.setOnClickListener {
            val intent = Intent(this, NotificationsActivity::class.java)
            startActivity(intent)
        }

        menuGuests.setOnClickListener {
            val intent = Intent(this, GuestsActivity::class.java)
            intent.putExtra("from", "MainActivity")
            startActivity(intent)
        }

        menuSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        menuHelp.setOnClickListener {
            val intent = Intent(this, SupportActivity::class.java)
            startActivity(intent)
        }

        menuAbout.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
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
                    val intent = Intent(this, ProfileActivity::class.java)
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
            R.id.nav_guests -> {
                val intent = Intent(this, GuestsActivity::class.java)
                intent.putExtra("from", "MainActivity")
                startActivity(intent)
            }
            R.id.nav_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
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
        sliderHandler.postDelayed(sliderRunnable, 5500)
    }

    override fun onPause() {
        super.onPause()
        sliderHandler.removeCallbacks(sliderRunnable)
    }


    override fun onDestroy() {
        super.onDestroy()
        NotificationListener.stopListening()
        NotificationManager.clearRecentNotifications()
    }
}