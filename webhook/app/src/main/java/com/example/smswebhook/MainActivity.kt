package com.example.smswebhook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smswebhook.util.Env
import com.example.smswebhook.util.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var urlText: TextView
    private lateinit var timerText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var startTime = System.currentTimeMillis()

    private val updateRunnable = object : Runnable {
        override fun run() {
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            timerText.text = "Temps actif : ${elapsed}s"
            handler.postDelayed(this, 1000)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val allGranted = result.values.all { it }
            setupUI(allGranted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        urlText = findViewById(R.id.url_text)
        timerText = findViewById(R.id.timer_text)

        val prefs = Prefs(this)
        prefs.saveWebhookUrl(Env.WEBHOOK_URL)
        prefs.setWebhookEnabled(true)

        if (hasRequiredPermissions()) {
            setupUI(true)
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun setupUI(allGranted: Boolean) {
        val prefs = Prefs(this)

        statusText.text = if (allGranted) {
            "Webhook SMS prêt"
        } else {
            "Permissions manquantes"
        }

        urlText.text = "→ ${prefs.getWebhookUrl()}"
        startTime = System.currentTimeMillis()

        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.toTypedArray()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }
}