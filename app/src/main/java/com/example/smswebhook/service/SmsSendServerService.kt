package com.example.smswebhook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smswebhook.util.Env
import com.example.smswebhook.util.NetworkUtils

class SmsSendServerService : Service() {

    private var server: SmsSendHttpServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification(getNotificationText())
        )

        startSmsServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server == null) {
            startSmsServer()
        }

        return START_STICKY
    }

    private fun startSmsServer() {
        try {
            if (server != null) {
                Log.i(TAG, "Serveur SMS déjà actif")
                return
            }

            server = SmsSendHttpServer(
                appContext = applicationContext,
                port = Env.LOCAL_SMS_SERVER_PORT,
                webhookToken = Env.WEBHOOK_TOKEN
            )

            server?.start(SOCKET_READ_TIMEOUT, false)

            val url = NetworkUtils.buildLocalSmsSendUrl()
                ?: "http://IP_DU_PHONE:${Env.LOCAL_SMS_SERVER_PORT}${Env.LOCAL_SMS_SEND_PATH}"

            Log.i(TAG, "Serveur SMS démarré : $url")

        } catch (e: Exception) {
            Log.e(TAG, "Impossible de démarrer le serveur SMS: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            server?.stop()
            server = null
            Log.i(TAG, "Serveur SMS arrêté")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur arrêt serveur SMS: ${e.message}", e)
        }

        super.onDestroy()
    }

    private fun getNotificationText(): String {
        val url = NetworkUtils.buildLocalSmsSendUrl()

        return if (url != null) {
            "Écoute : $url"
        } else {
            "Serveur actif sur port ${Env.LOCAL_SMS_SERVER_PORT}"
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Send Server")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Send Server",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SmsSendServerService"
        private const val CHANNEL_ID = "sms_send_server_channel"
        private const val NOTIFICATION_ID = 2001
        private const val SOCKET_READ_TIMEOUT = 5000
    }
}