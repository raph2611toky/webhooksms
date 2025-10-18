package com.example.smswebhook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.example.smswebhook.data.SmsDataExtractor
import com.example.smswebhook.util.HttpClient
import com.example.smswebhook.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.smswebhook.util.Env
import org.json.JSONObject

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
            val smsList = extractor.getNewSmsMessages()
            val mmsList = extractor.getNewMmsMessages()
            val allMessages = smsList + mmsList
            val webhookUrl = prefs.getWebhookUrl() ?: Env.WEBHOOK_URL

            allMessages.forEach { jsonMessage ->
                jsonMessage.put("msg_type", if (smsList.contains(jsonMessage)) "sms" else "mms")
                try {
                    val response = HttpClient.postJson(webhookUrl, jsonMessage)
                    val chatResponse = response.optString("message", "")
                    val recipientPhone = response.optString("recipient_phone", "")
                    val subId = jsonMessage.optInt("sub_id", -1)

                    if (chatResponse.isNotEmpty() && recipientPhone.isNotEmpty()) {
                        sendSms(recipientPhone, chatResponse, subId)
                    }
                } catch (e: Exception) {
                    // Log error but continue processing other messages
                    android.util.Log.e("WebhookService", "Failed to process message: ${e.message}")
                }
            }
        }
        return START_STICKY
    }

    private fun sendSms(phoneNumber: String, message: String, subId: Int) {
        try {
            val smsManager = if (subId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                SmsManager.getDefault()
            }
            // Split message if it's too long (SMS has a 160-character limit per part)
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            android.util.Log.i("WebhookService", "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            android.util.Log.e("WebhookService", "Failed to send SMS: ${e.message}")
        }
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