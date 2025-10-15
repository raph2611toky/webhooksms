package com.example.smswebhook.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.example.smswebhook.service.WebhookService

class SmsMmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            // val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) // OK API 19

            val serviceIntent = Intent(context, WebhookService::class.java)
            serviceIntent.putExtra("trigger", "sms_received")
            val subId = intent.getIntExtra("subscription", -1)
            if (subId != -1) {
                serviceIntent.putExtra("subscription_id", subId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26+
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        // if (intent.action == "android.provider.Telephony.WAP_PUSH_RECEIVED" &&
        //     intent.type == "application/vnd.wap.mms-message") {
        // }
    }
}