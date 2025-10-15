package com.example.smswebhook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.smswebhook.data.SmsDataExtractor
import com.example.smswebhook.util.HttpClient
import com.example.smswebhook.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.smswebhook.util.Env

class WebhookService : Service() {

    private lateinit var extractor: SmsDataExtractor
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        extractor = SmsDataExtractor(this)
        prefs = Prefs(this)
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "webhook_channel")
            .setContentTitle("SMS/MMS Webhook Actif")
            .setContentText("Surveillance en background")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.IO).launch {
            // Get nouveaux SMS et MMS
            val smsList = extractor.getNewSmsMessages()
            val mmsList = extractor.getNewMmsMessages()
            val allMessages = smsList + mmsList

            val webhookUrl = prefs.getWebhookUrl() ?: Env.WEBHOOK_URL

            allMessages.forEach { jsonMessage ->
                // Ajoute type pour distinction
                jsonMessage.put("msg_type", if (smsList.contains(jsonMessage)) "sms" else "mms")
                HttpClient.postJson(webhookUrl, jsonMessage)
            }
        }
        return START_STICKY // Persiste
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "webhook_channel",
                "Webhook Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}