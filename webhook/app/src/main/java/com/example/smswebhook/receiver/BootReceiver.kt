package com.example.smswebhook.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smswebhook.util.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = Prefs(context)
            Log.i(TAG, "Boot completed. Webhook URL = ${prefs.getWebhookUrl()}")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}