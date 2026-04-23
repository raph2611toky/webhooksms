package com.example.smswebhook.util

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_webhook_prefs", Context.MODE_PRIVATE)

    fun saveLastSmsId(id: Long) = prefs.edit().putLong("last_sms_id", id).apply()
    fun getLastSmsId(): Long = prefs.getLong("last_sms_id", 0L)

    fun saveLastMmsId(id: Long) = prefs.edit().putLong("last_mms_id", id).apply()
    fun getLastMmsId(): Long = prefs.getLong("last_mms_id", 0L)

    fun saveWebhookUrl(url: String) = prefs.edit().putString("webhook_url", url).apply()

    fun getWebhookUrl(): String =
        prefs.getString("webhook_url", Env.WEBHOOK_URL) ?: Env.WEBHOOK_URL

    fun setWebhookEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("webhook_enabled", enabled).apply()

    fun isWebhookEnabled(): Boolean =
        prefs.getBoolean("webhook_enabled", true)

    fun saveLastInboundFingerprint(fingerprint: String, timestampMs: Long) {
        prefs.edit()
            .putString("last_inbound_fingerprint", fingerprint)
            .putLong("last_inbound_timestamp", timestampMs)
            .apply()
    }

    fun isRecentDuplicate(fingerprint: String, windowMs: Long = 120_000L): Boolean {
        val lastFingerprint = prefs.getString("last_inbound_fingerprint", null)
        val lastTimestamp = prefs.getLong("last_inbound_timestamp", 0L)
        val now = System.currentTimeMillis()

        return lastFingerprint == fingerprint && (now - lastTimestamp) <= windowMs
    }
}