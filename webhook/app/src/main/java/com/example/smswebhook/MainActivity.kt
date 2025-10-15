package com.example.smswebhook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smswebhook.service.WebhookService
import com.example.smswebhook.util.Prefs
import com.example.smswebhook.util.Env

class MainActivity : AppCompatActivity() {

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) {
            startServiceCompat()
        }
        Prefs(this).saveWebhookUrl(Env.WEBHOOK_URL)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.INTERNET,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.READ_PHONE_STATE
            // Enlève FOREGROUND_SERVICE_DATA_SYNC si pas supporté sur API 24
        )

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startServiceCompat()
            Prefs(this).saveWebhookUrl(Env.WEBHOOK_URL)
            finish()
        } else {
            requestPermission.launch(permissions)
        }
    }

    private fun startServiceCompat() {
        val intent = Intent(this, WebhookService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26+
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}