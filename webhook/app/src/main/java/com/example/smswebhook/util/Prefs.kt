package com.example.smswebhook.util

import android.content.Context
import android.content.SharedPreferences
import java.net.URI

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_webhook_prefs", Context.MODE_PRIVATE)

    fun saveLastSmsId(id: Long) = prefs.edit().putLong("last_sms_id", id).apply()
    fun getLastSmsId(): Long = prefs.getLong("last_sms_id", 0L)

    fun saveLastMmsId(id: Long) = prefs.edit().putLong("last_mms_id", id).apply()
    fun getLastMmsId(): Long = prefs.getLong("last_mms_id", 0L)

    fun saveBackendConfig(host: String, port: String, portEnabled: Boolean) {
        prefs.edit()
            .putString("backend_host", sanitizeHostForStorage(host))
            .putString("backend_port", sanitizePortForStorage(port))
            .putBoolean("backend_port_enabled", portEnabled)
            .putBoolean("backend_config_initialized", true)
            .apply()
    }

    fun getBackendHost(): String =
        prefs.getString("backend_host", Env.DEFAULT_BACKEND_HOST) ?: Env.DEFAULT_BACKEND_HOST

    fun getBackendPort(): String =
        prefs.getString("backend_port", Env.DEFAULT_BACKEND_PORT.toString())
            ?: Env.DEFAULT_BACKEND_PORT.toString()

    fun isBackendPortEnabled(): Boolean =
        prefs.getBoolean("backend_port_enabled", Env.DEFAULT_BACKEND_PORT_ENABLED)

    fun getWebhookUrl(): String = buildWebhookUrl(
        hostInput = getBackendHost(),
        portInput = getBackendPort(),
        portEnabled = isBackendPortEnabled()
    )

    fun setSmsGatewayEnabled(enabled: Boolean) {
        val wasEnabled = isSmsGatewayEnabled()

        val editor = prefs.edit()
            .putBoolean("sms_gateway_enabled", enabled)

        // Quand on réactive le service, on mémorise l'heure exacte.
        // Les SMS reçus avant cette heure seront ignorés.
        if (enabled && !wasEnabled) {
            editor.putLong("sms_gateway_enabled_at", System.currentTimeMillis())
        }

        editor.apply()
    }

    fun isSmsGatewayEnabled(): Boolean =
        prefs.getBoolean("sms_gateway_enabled", true)

    fun getSmsGatewayEnabledAt(): Long =
        prefs.getLong("sms_gateway_enabled_at", 0L)

    fun shouldProcessMessage(timestampMs: Long): Boolean {
        if (!isSmsGatewayEnabled()) return false

        val enabledAt = getSmsGatewayEnabledAt()

        return enabledAt <= 0L || timestampMs >= enabledAt
    }

    // Compatibilité avec l'ancien nom utilisé dans le reste du projet.
    fun setWebhookEnabled(enabled: Boolean) =
        setSmsGatewayEnabled(enabled)

    fun isWebhookEnabled(): Boolean =
        isSmsGatewayEnabled()

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

    private fun buildWebhookUrl(
        hostInput: String,
        portInput: String,
        portEnabled: Boolean
    ): String {
        val normalizedInput = normalizeHostInput(hostInput)
        val uri = try {
            URI(normalizedInput)
        } catch (_: Exception) {
            URI("http://${Env.DEFAULT_BACKEND_HOST}")
        }

        val scheme = uri.scheme?.ifBlank { "http" } ?: "http"
        val host = uri.host
            ?: normalizedInput.substringAfter("://", normalizedInput)
                .substringBefore("/")
                .substringBefore(":")
                .ifBlank { Env.DEFAULT_BACKEND_HOST }

        val port = sanitizePortForStorage(portInput)
        val portPart = if (portEnabled && port.isNotBlank()) ":$port" else ""

        val basePath = (uri.rawPath ?: "")
            .removeSuffix(Env.WEBHOOK_PATH.trimEnd('/'))
            .trim('/')

        val finalPath = joinPaths(basePath, Env.WEBHOOK_PATH)

        return "$scheme://$host$portPart$finalPath"
    }

    private fun normalizeHostInput(value: String): String {
        var host = value.trim().ifBlank { Env.DEFAULT_BACKEND_HOST }

        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://$host"
        }

        return host.trimEnd('/')
    }

    private fun sanitizeHostForStorage(value: String): String {
        return value.trim()
            .removeSuffix(Env.WEBHOOK_PATH.trimEnd('/'))
            .trimEnd('/')
            .ifBlank { Env.DEFAULT_BACKEND_HOST }
    }

    private fun sanitizePortForStorage(value: String): String {
        val port = value.trim().toIntOrNull()

        return if (port != null && port in 1..65535) {
            port.toString()
        } else {
            Env.DEFAULT_BACKEND_PORT.toString()
        }
    }

    private fun joinPaths(basePath: String, childPath: String): String {
        val cleanBase = basePath.trim('/')
        val cleanChild = childPath.trim('/')

        return if (cleanBase.isBlank()) {
            "/$cleanChild/"
        } else {
            "/$cleanBase/$cleanChild/"
        }
    }
}
