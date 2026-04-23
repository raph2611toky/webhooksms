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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HttpClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(Env.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(Env.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(Env.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun postJson(
        url: String,
        data: JSONObject,
        headers: Map<String, String> = emptyMap()
    ): JSONObject = suspendCancellableCoroutine { cont ->

        val body = data.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val builder = Request.Builder()
            .url(url)
            .post(body)

        headers.forEach { (key, value) ->
            if (value.isNotBlank()) {
                builder.addHeader(key, value)
            }
        }

        val request = builder.build()
        val call = client.newCall(request)

        cont.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) {
                    cont.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IOException("HTTP ${response.code}: $responseBody")
                            )
                        }
                        return
                    }

                    try {
                        val jsonResponse = if (responseBody.isBlank()) {
                            JSONObject()
                        } else {
                            JSONObject(responseBody)
                        }

                        if (cont.isActive) {
                            cont.resume(jsonResponse)
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IOException("Réponse JSON invalide: ${e.message}")
                            )
                        }
                    }
                }
            }
        })
    }
}