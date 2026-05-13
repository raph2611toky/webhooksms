package com.example.smswebhook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smswebhook.service.SmsSendServerService
import com.example.smswebhook.util.Env
import com.example.smswebhook.util.NetworkUtils
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

            refreshUrlsText()

            handler.postDelayed(this, 3000)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val allGranted = result.values.all { it }

            setupUI(allGranted)

            if (allGranted) {
                startSmsSendServer()
            }
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
            startSmsSendServer()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun setupUI(allGranted: Boolean) {
        statusText.text = if (allGranted) {
            "Webhook SMS prêt + serveur d'envoi actif"
        } else {
            "Permissions manquantes"
        }

        startTime = System.currentTimeMillis()

        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)

        refreshUrlsText()
    }

    private fun refreshUrlsText() {
        val prefs = Prefs(this)

        val localSmsUrl = NetworkUtils.buildLocalSmsSendUrl()
            ?: "IP du téléphone introuvable. Vérifiez le Wi-Fi."

        val allIps = NetworkUtils.getLocalIpv4Addresses()
            .joinToString(", ")
            .ifBlank { "Aucune IP détectée" }

        urlText.text =
            "Entrant Android → Django :\n" +
            "${prefs.getWebhookUrl()}\n\n" +
            "Sortant Django → Android :\n" +
            "$localSmsUrl\n\n" +
            "IP détectée(s) : $allIps"
    }

    private fun startSmsSendServer() {
        val intent = Intent(this, SmsSendServerService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
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