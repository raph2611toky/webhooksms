package com.example.smswebhook.util

import android.content.Context
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Base64
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class SmsServer(private val context: Context) : NanoHTTPD("0.0.0.0", 8800) {

    private val AUTH_USERNAME = "Admin"
    private val AUTH_PASSWORD = "Password1234"
    private val TAG = "SmsServer"

    init {
        start(SOCKET_READ_TIMEOUT, false)
        Log.d(TAG, "SMS Server started on port 8800")
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/api/sendsms" || session.method != Method.POST) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Endpoint not found"
            )
        }

        // Check Basic Authentication
        val authHeader = session.headers["authorization"]
        if (!isAuthenticated(authHeader)) {
            val response = newFixedLengthResponse(
                Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized"
            )
            response.addHeader("WWW-Authenticate", "Basic realm=\"SMS Server\"")
            return response
        }

        return try {
            // Parse JSON payload
            val json = JSONObject(session.inputStream.bufferedReader().use { it.readText() })
            val recipientPhone = json.optString("recipient_phone")
            val textMessage = json.optString("text_message")
            val sourcePhone = json.optString("source_phone")

            if (recipientPhone.isEmpty() || textMessage.isEmpty()) {
                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_JSON,
                    JSONObject().put("error", "Missing recipient_phone or text_message").toString()
                )
            } else {
                // Send SMS and get result
                val result = sendSms(recipientPhone, textMessage, sourcePhone)
                newFixedLengthResponse(
                    Response.Status.OK, MIME_JSON,
                    JSONObject().put("success", result.success)
                        .put("message", result.message)
                        .toString()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing request: ${e.message}")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_JSON,
                JSONObject().put("error", "Internal server error").toString()
            )
        }
    }

    private fun isAuthenticated(authHeader: String?): Boolean {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false
        }
        val base64Credentials = authHeader.substring(6)
        val credentials = String(Base64.decode(base64Credentials, Base64.DEFAULT), StandardCharsets.UTF_8)
        val (username, password) = credentials.split(":", limit = 2)
        return username == AUTH_USERNAME && password == AUTH_PASSWORD
    }

    private data class SendResult(val success: Boolean, val message: String)

    private fun sendSms(recipientPhone: String, textMessage: String, sourcePhone: String): SendResult {
        return try {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList ?: emptyList()

            // Find SIM by source_phone or use the first available SIM
            var selectedSubId: Int? = null
            if (sourcePhone.isNotEmpty()) {
                for (subInfo in activeSubscriptions) {
                    val phoneNumber = subInfo.number
                    if (phoneNumber == sourcePhone) {
                        selectedSubId = subInfo.subscriptionId
                        break
                    }
                }
            }

            // If no matching source_phone or source_phone not provided, use first SIM
            if (selectedSubId == null && activeSubscriptions.isNotEmpty()) {
                selectedSubId = activeSubscriptions[0].subscriptionId
            }

            if (selectedSubId == null) {
                return SendResult(false, "No active SIM found")
            }

            // Send SMS using SmsManager
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                SmsManager.getSmsManagerForSubscriptionId(selectedSubId)
            } else {
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(recipientPhone, null, textMessage, null, null)
            SendResult(true, "SMS sent successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error: ${e.message}")
            SendResult(false, "Permission denied for sending SMS")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS: ${e.message}")
            SendResult(false, "Failed to send SMS: ${e.message}")
        }
    }

    companion object {
        const val MIME_JSON = "application/json"
    }
}