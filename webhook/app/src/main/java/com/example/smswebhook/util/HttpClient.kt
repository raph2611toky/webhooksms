package com.example.smswebhook.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HttpClient {
    private val client = OkHttpClient()

    suspend fun postJson(url: String, data: JSONObject): JSONObject = suspendCancellableCoroutine { cont ->
        val body = data.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        try {
                            val jsonResponse = JSONObject(responseBody ?: "{}")
                            cont.resume(jsonResponse)
                        } catch (e: Exception) {
                            cont.resumeWithException(IOException("Failed to parse response: ${e.message}"))
                        }
                    } else {
                        cont.resumeWithException(IOException("Request failed with code ${response.code}"))
                    }
                }
            }
        })
    }
}