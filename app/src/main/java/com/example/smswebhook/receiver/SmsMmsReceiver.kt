package com.example.smswebhook.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.example.smswebhook.data.SmsDataExtractor
import com.example.smswebhook.service.WebhookService

class SmsMmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        try {
            val extractor = SmsDataExtractor()
            val incomingSms = extractor.extractIncomingSms(intent) ?: return

            val serviceIntent = Intent(context, WebhookService::class.java).apply {
                action = WebhookService.ACTION_PROCESS_INCOMING_SMS
                putExtra(WebhookService.EXTRA_ADDRESS, incomingSms.address)
                putExtra(WebhookService.EXTRA_BODY, incomingSms.body)
                putExtra(WebhookService.EXTRA_TIMESTAMP, incomingSms.timestamp)
                putExtra(WebhookService.EXTRA_SUBSCRIPTION_ID, incomingSms.subscriptionId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.i(TAG, "SMS entrant capturé depuis ${incomingSms.address}")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur BroadcastReceiver SMS: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "SmsMmsReceiver"
    }
}