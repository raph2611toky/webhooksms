package com.example.smswebhook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smswebhook.util.Env
import com.example.smswebhook.util.HttpClient
import com.example.smswebhook.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest

class WebhookService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification("Traitement du SMS entrant...")
        )

        if (intent?.action != ACTION_PROCESS_INCOMING_SMS) {
            stopServiceSafely(startId)
            return START_NOT_STICKY
        }

        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty().trim()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty().trim()
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)

        serviceScope.launch {
            try {
                processIncomingSms(
                    address = address,
                    body = body,
                    timestamp = timestamp,
                    subscriptionId = subscriptionId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erreur processIncomingSms: ${e.message}", e)
            } finally {
                stopServiceSafely(startId)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun processIncomingSms(
        address: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int
    ) {
        if (!prefs.isWebhookEnabled()) {
            Log.i(TAG, "Webhook désactivé, SMS ignoré")
            return
        }

        if (address.isBlank() || body.isBlank()) {
            Log.w(TAG, "Payload entrant invalide")
            return
        }

        val fingerprint = sha256("$address|$body|$timestamp")
        if (prefs.isRecentDuplicate(fingerprint)) {
            Log.w(TAG, "SMS dupliqué ignoré")
            return
        }
        prefs.saveLastInboundFingerprint(fingerprint, System.currentTimeMillis())

        val payload = JSONObject().apply {
            put("address", address)
            put("body", body)
            put("msg_type", "sms")
            put("date", timestamp)
            put("direction", "inbound")
            put("subscription_id", subscriptionId)
        }

        val headers = buildMap {
            if (Env.WEBHOOK_TOKEN.isNotBlank()) {
                put("X-Webhook-Token", Env.WEBHOOK_TOKEN)
            }
        }

        Log.i(TAG, "Envoi au backend: ${prefs.getWebhookUrl()}")

        val response = HttpClient.postJson(
            url = prefs.getWebhookUrl(),
            data = payload,
            headers = headers
        )

        val reply = response.optString("reply", "").trim()

        if (reply.isBlank()) {
            Log.i(TAG, "Aucune réponse SMS renvoyée par le backend")
            return
        }

        sendSms(
            phoneNumber = address,
            message = reply,
            subscriptionId = subscriptionId
        )
    }

    private fun sendSms(
        phoneNumber: String,
        message: String,
        subscriptionId: Int
    ) {
        try {
            val cleanPhone = phoneNumber.trim()
            val cleanMessage = message.trim()

            if (cleanPhone.isBlank() || cleanMessage.isBlank()) {
                Log.w(TAG, "sendSms annulé: numéro ou message vide")
                return
            }

            val smsManager = if (
                subscriptionId != -1 &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
            ) {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getDefault()
            }

            val parts = ArrayList(smsManager.divideMessage(cleanMessage))
            smsManager.sendMultipartTextMessage(
                cleanPhone,
                null,
                parts,
                null,
                null
            )

            Log.i(TAG, "Réponse SMS envoyée vers $cleanPhone")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur envoi SMS: ${e.message}", e)
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Webhook")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Webhook Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopServiceSafely(startId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelfResult(startId)
    }

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WebhookService"
        private const val CHANNEL_ID = "sms_webhook_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_PROCESS_INCOMING_SMS =
            "com.example.smswebhook.action.PROCESS_INCOMING_SMS"

        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_SUBSCRIPTION_ID = "extra_subscription_id"
    }
}