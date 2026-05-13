package com.example.smswebhook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smswebhook.service.SmsSendServerService
import com.example.smswebhook.util.NetworkUtils
import com.example.smswebhook.util.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var urlText: TextView
    private lateinit var timerText: TextView
    private lateinit var settingsButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var startTime = System.currentTimeMillis()

    private val updateRunnable = object : Runnable {
        override fun run() {
            val prefs = Prefs(this@MainActivity)

            if (prefs.isSmsGatewayEnabled()) {
                val activeSince = prefs.getSmsGatewayEnabledAt()
                    .takeIf { it > 0L }
                    ?: startTime

                val elapsed = (System.currentTimeMillis() - activeSince) / 1000
                timerText.text = "Temps actif : ${elapsed}s"
            } else {
                timerText.text = "Service désactivé"
            }

            refreshStatusText(hasRequiredPermissions())
            refreshUrlsText()

            handler.postDelayed(this, 3000)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val allGranted = result.values.all { it }

            setupUI(allGranted)

            if (allGranted && Prefs(this).isSmsGatewayEnabled()) {
                startSmsSendServer()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        urlText = findViewById(R.id.url_text)
        timerText = findViewById(R.id.timer_text)
        settingsButton = findViewById(R.id.settings_button)

        settingsButton.setOnClickListener {
            showBackendSettingsDialog()
        }

        if (hasRequiredPermissions()) {
            setupUI(true)

            if (Prefs(this).isSmsGatewayEnabled()) {
                startSmsSendServer()
            }
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun setupUI(allGranted: Boolean) {
        refreshStatusText(allGranted)

        val prefs = Prefs(this)
        startTime = prefs.getSmsGatewayEnabledAt()
            .takeIf { it > 0L }
            ?: System.currentTimeMillis()

        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)

        refreshUrlsText()
    }

    private fun refreshStatusText(allGranted: Boolean) {
        val prefs = Prefs(this)

        statusText.text = when {
            !allGranted -> "Permissions manquantes"
            !prefs.isSmsGatewayEnabled() -> "Service SMS désactivé"
            else -> "Webhook SMS prêt + serveur d'envoi actif"
        }
    }

    private fun showBackendSettingsDialog() {
        val prefs = Prefs(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }

        val serviceSwitch = Switch(this).apply {
            text = "Activer le service, les écoutes et les envois SMS"
            isChecked = prefs.isSmsGatewayEnabled()
            setPadding(0, 0, 0, dp(8))
        }

        val serviceHelpText = TextView(this).apply {
            text = "Si ce bouton est désactivé, aucun SMS entrant ne sera envoyé au backend et aucun SMS sortant ne sera envoyé. À la réactivation, seuls les nouveaux SMS seront traités."
            setTextColor(Color.DKGRAY)
            textSize = 13f
            setPadding(0, 0, 0, dp(14))
        }

        val helpText = TextView(this).apply {
            text = "Définis ici la destination du backend Django. Le port peut être activé ou désactivé selon ton lien."
            setTextColor(Color.DKGRAY)
            textSize = 14f
            setPadding(0, 0, 0, dp(12))
        }

        val hostInput = EditText(this).apply {
            hint = "Ex: 192.168.0.117 ou https://backend.com"
            setText(prefs.getBackendHost())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }

        val portSwitch = Switch(this).apply {
            text = "Utiliser un port personnalisé"
            isChecked = prefs.isBackendPortEnabled()
            setPadding(0, dp(12), 0, dp(4))
        }

        val portInput = EditText(this).apply {
            hint = "Ex: 8000"
            setText(prefs.getBackendPort())
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            isEnabled = portSwitch.isChecked
        }

        portSwitch.setOnCheckedChangeListener { _, checked ->
            portInput.isEnabled = checked
            portInput.alpha = if (checked) 1f else 0.45f
        }

        portInput.alpha = if (portSwitch.isChecked) 1f else 0.45f

        container.addView(serviceSwitch)
        container.addView(serviceHelpText)
        container.addView(helpText)
        container.addView(labelText("Host / lien backend"))
        container.addView(hostInput)
        container.addView(portSwitch)
        container.addView(labelText("Port"))
        container.addView(portInput)

        AlertDialog.Builder(this)
            .setTitle("Paramètres")
            .setView(container)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Appliquer") { _, _ ->
                prefs.saveBackendConfig(
                    host = hostInput.text.toString(),
                    port = portInput.text.toString(),
                    portEnabled = portSwitch.isChecked
                )

                prefs.setSmsGatewayEnabled(serviceSwitch.isChecked)

                if (serviceSwitch.isChecked) {
                    if (hasRequiredPermissions()) {
                        startSmsSendServer()
                    } else {
                        permissionLauncher.launch(requiredPermissions())
                    }
                } else {
                    stopSmsSendServer()
                }

                setupUI(hasRequiredPermissions())

                val message = if (serviceSwitch.isChecked) {
                    "Service activé. Seuls les nouveaux SMS seront traités."
                } else {
                    "Service désactivé."
                }

                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun labelText(value: String): TextView {
        return TextView(this).apply {
            text = value
            setTextColor(Color.BLACK)
            textSize = 13f
            gravity = Gravity.START
            setPadding(0, dp(10), 0, 0)
        }
    }

    private fun refreshUrlsText() {
        val prefs = Prefs(this)

        val localSmsUrl = NetworkUtils.buildLocalSmsSendUrl()
            ?: "IP du téléphone introuvable. Vérifiez le Wi-Fi."

        val allIps = NetworkUtils.getLocalIpv4Addresses()
            .joinToString(", ")
            .ifBlank { "Aucune IP détectée" }

        val serviceStatus = if (prefs.isSmsGatewayEnabled()) {
            "activé"
        } else {
            "désactivé"
        }

        val portStatus = if (prefs.isBackendPortEnabled()) {
            "activé : ${prefs.getBackendPort()}"
        } else {
            "désactivé"
        }

        urlText.text =
            "Service SMS : $serviceStatus\n\n" +
            "Entrant Android → Django :\n" +
            "${prefs.getWebhookUrl()}\n" +
            "Port backend : $portStatus\n\n" +
            "Sortant Django → Android :\n" +
            "$localSmsUrl\n\n" +
            "IP détectée(s) : $allIps"
    }

    private fun startSmsSendServer() {
        if (!Prefs(this).isSmsGatewayEnabled()) {
            stopSmsSendServer()
            return
        }

        val intent = Intent(this, SmsSendServerService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSmsSendServer() {
        stopService(Intent(this, SmsSendServerService::class.java))
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }
}
