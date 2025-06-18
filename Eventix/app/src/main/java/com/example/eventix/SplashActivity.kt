package com.example.eventix

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var ivLogo: ImageView
    private lateinit var wellView: View
    private lateinit var dotsContainer: LinearLayout
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private lateinit var dot4: View
    private lateinit var dot5: View

    private val handler = Handler(Looper.getMainLooper())
    private var isPageReady = false
    private var isAnimationRunning = false
    private var animationCycles = 0
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        ivLogo = findViewById(R.id.ivLogo)
        wellView = findViewById(R.id.wellView)
        dotsContainer = findViewById(R.id.dotsContainer)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
        dot4 = findViewById(R.id.dot4)
        dot5 = findViewById(R.id.dot5)

        auth = FirebaseAuth.getInstance()

        startLogoAnimation()
        handler.post { prepareNextPage() }
    }

    private fun startLogoAnimation() {
        val logoAppear = ObjectAnimator.ofFloat(ivLogo, "alpha", 0f, 1f).apply {
            duration = 400
        }

        val logoJump = ObjectAnimator.ofFloat(ivLogo, "translationY", 100f, -50f).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
        }

        val logoSettle = ObjectAnimator.ofFloat(ivLogo, "translationY", -50f, 0f).apply {
            duration = 600
            interpolator = BounceInterpolator()
            startDelay = 600
        }

        val wellShrink = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 500
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                wellView.scaleX = value
                wellView.scaleY = value
                wellView.alpha = value
            }
            startDelay = 300
        }

        val logoAnimSet = AnimatorSet().apply {
            play(logoAppear).with(logoJump)
            play(logoSettle).after(logoJump)
            play(wellShrink).after(logoJump)
        }

        logoAnimSet.doOnEnd {
            handler.postDelayed({
                dotsContainer.visibility = View.VISIBLE
                startDotsAnimation()
            }, 200)
        }

        logoAnimSet.start()
    }

    private fun startDotsAnimation() {
        if (isAnimationRunning) return

        isAnimationRunning = true
        val dots = arrayOf(dot1, dot2, dot3, dot4, dot5)

        fun createSequenceAnimation(): AnimatorSet {
            val sequenceSet = AnimatorSet()
            val animations = ArrayList<Animator>()

            for (i in dots.indices) {
                val dotSet = AnimatorSet()

                val upAnim = ObjectAnimator.ofFloat(dots[i], "translationY", 0f, -20f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                }

                val downAnim = ObjectAnimator.ofFloat(dots[i], "translationY", -20f, 0f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                }

                dotSet.playSequentially(upAnim, downAnim)
                animations.add(dotSet)
            }

            sequenceSet.playSequentially(animations)
            return sequenceSet
        }

        fun runAnimationCycle() {
            if (isNavigating) return

            val sequenceAnim = createSequenceAnimation()

            sequenceAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animationCycles++

                    if (isPageReady && animationCycles >= 2 && !isNavigating) {
                        isNavigating = true
                        navigateToNextScreen()
                    } else if (!isNavigating) {
                        handler.postDelayed({ runAnimationCycle() }, 100)
                    }
                }
            })

            sequenceAnim.start()
        }

        runAnimationCycle()
    }

    private fun prepareNextPage() {
        handler.postDelayed({
            isPageReady = true
            if (animationCycles >= 2 && !isNavigating) {
                isNavigating = true
                navigateToNextScreen()
            }
        }, 2500)
    }

    private fun navigateToNextScreen() {
        val currentUser = auth.currentUser
        val sharedPreferences = getSharedPreferences("EventixPrefs", Context.MODE_PRIVATE)
        val rememberMe = sharedPreferences.getBoolean("remember_me", false)

        if (currentUser != null && rememberMe) {
            RoleManager.checkUserRole { role ->
                when (role) {
                    UserRole.GESTOR -> {
                        startActivity(Intent(this, ManagerMainActivity::class.java))
                    }
                    UserRole.UTILIZADOR -> {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                }
                finish()
            }
        } else {
            if (currentUser != null && !rememberMe) {
                auth.signOut()
            }
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}