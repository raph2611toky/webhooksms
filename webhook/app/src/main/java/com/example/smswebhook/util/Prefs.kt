package com.example.smswebhook.util

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sms_webhook_prefs", Context.MODE_PRIVATE)

    fun saveLastSmsId(id: Long) = prefs.edit().putLong("last_sms_id", id).apply()
    fun getLastSmsId(): Long = prefs.getLong("last_sms_id", 0L)

    fun saveLastMmsId(id: Long) = prefs.edit().putLong("last_mms_id", id).apply()
    fun getLastMmsId(): Long = prefs.getLong("last_mms_id", 0L)

    fun saveWebhookUrl(url: String) = prefs.edit().putString("webhook_url", url).apply()
    fun getWebhookUrl(): String? = prefs.getString("webhook_url", Env.WEBHOOK_URL)
}