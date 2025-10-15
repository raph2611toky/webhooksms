package com.example.smswebhook.data

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.smswebhook.util.Prefs
import org.json.JSONObject
import android.Manifest.permission.READ_PHONE_STATE

class SmsDataExtractor(val context: Context, private val subscriptionId: Int = -1) {

    private val prefs = Prefs(context)
    private val contentResolver: ContentResolver = context.contentResolver

    private val SMS_URI = Uri.parse("content://sms")
    private val MMS_URI = Uri.parse("content://mms")
    private val MMS_PART_URI = Uri.parse("content://mms/part")

    @SuppressLint("MissingPermission")
    private fun getRecipientInfo(subId: Int): String {
        if (subId == -1) return "Unknown SIM"

        val permissionCheck = ContextCompat.checkSelfPermission(context, READ_PHONE_STATE)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.w("SmsDataExtractor", "Permission READ_PHONE_STATE non accordée")
            return "SIM Permission Denied"
        }

        try {
            val subManager: SubscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val telephonyManager: TelephonyManager = context.getSystemService(TelephonyManager::class.java)
            val subInfo: SubscriptionInfo? = subManager.getActiveSubscriptionInfo(subId)

            return if (subInfo != null) {
                val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    telephonyManager.createForSubscriptionId(subId).line1Number
                } else {
                    telephonyManager.line1Number
                }

                val slotIndex = subInfo.simSlotIndex
                val fallbackSlot = "SIM Slot $slotIndex"

                if (!number.isNullOrEmpty()) {
                    number
                } else {
                    Log.w("SmsDataExtractor", "Numéro SIM vide pour subId $subId, fallback à $fallbackSlot")
                    fallbackSlot
                }
            } else {
                Log.w("SmsDataExtractor", "Aucune info SIM pour subId $subId")
                "SIM Unknown"
            }
        } catch (e: SecurityException) {
            Log.e("SmsDataExtractor", "SecurityException malgré check: ${e.message}")
            return "SIM Permission Denied"
        } catch (e: Exception) {
            Log.e("SmsDataExtractor", "Erreur récupération SIM info: ${e.message}")
            return "SIM Error"
        }
    }

    fun getNewSmsMessages(): List<JSONObject> {
        val lastId = prefs.getLastSmsId()
        val selection = "_id > ?"
        val selectionArgs = arrayOf(lastId.toString())
        val cursor = contentResolver.query(SMS_URI, null, selection, selectionArgs, "_id ASC") ?: return emptyList()

        val messages = mutableListOf<JSONObject>()
        cursor.use {
            while (it.moveToNext()) {
                val json = mapSmsCursorToJson(it)
                json.put("msg_type", "sms")
                messages.add(json)
                prefs.saveLastSmsId(it.safeGetLong("_id"))
            }
        }
        return messages
    }

    fun getNewMmsMessages(): List<JSONObject> {
        val lastId = prefs.getLastMmsId()
        val selection = "_id > ?"
        val selectionArgs = arrayOf(lastId.toString())
        val cursor = contentResolver.query(MMS_URI, null, selection, selectionArgs, "_id ASC") ?: return emptyList()

        val messages = mutableListOf<JSONObject>()
        cursor.use {
            while (it.moveToNext()) {
                val mmsJson = mapMmsCursorToJson(it)
                mmsJson.put("msg_type", "mms")
                val parts = getMmsParts(it.safeGetLong("_id"))
                mmsJson.put("parts", parts)
                messages.add(mmsJson)
                prefs.saveLastMmsId(it.safeGetLong("_id"))
            }
        }
        return messages
    }

    private fun mapSmsCursorToJson(cursor: Cursor): JSONObject {
        return JSONObject().apply {
            put("_id", cursor.safeGetLong("_id"))
            put("thread_id", cursor.safeGetLong("thread_id"))
            put("address", cursor.safeGetString("address"))
            put("body", cursor.safeGetString("body"))
            put("date", cursor.safeGetLong("date"))
            put("date_sent", cursor.safeGetLong("date_sent"))
            put("type", cursor.safeGetInt("type"))
            put("status", cursor.safeGetInt("status"))
            put("read", cursor.safeGetInt("read"))
            put("seen", cursor.safeGetInt("seen"))
            put("locked", cursor.safeGetInt("locked"))
            put("protocol", cursor.safeGetInt("protocol"))
            put("reply_path_present", cursor.safeGetInt("reply_path_present"))
            put("service_center", cursor.safeGetString("service_center"))
            put("subject", cursor.safeGetString("subject"))
            put("creator", cursor.safeGetString("creator"))
            put("sub_id", cursor.safeGetInt("sub_id"))
        }
    }

    private fun mapMmsCursorToJson(cursor: Cursor): JSONObject {
        return JSONObject().apply {
            put("_id", cursor.safeGetLong("_id"))
            put("thread_id", cursor.safeGetLong("thread_id"))
            put("date", cursor.safeGetLong("date") * 1000)
            put("msg_box", cursor.safeGetInt("msg_box"))
            put("read", cursor.safeGetInt("read"))
            put("seen", cursor.safeGetInt("seen"))
            put("sub", cursor.safeGetString("sub"))
            put("ct_t", cursor.safeGetString("ct_t"))
            put("m_cls", cursor.safeGetString("m_cls"))
            put("m_type", cursor.safeGetInt("m_type"))
            put("tr_id", cursor.safeGetString("tr_id"))
            put("locked", cursor.safeGetInt("locked"))
            put("sub_id", cursor.safeGetInt("sub_id"))
        }
    }

    private fun getMmsParts(mmsId: Long): List<JSONObject> {
        val selection = "mid = ?"
        val selectionArgs = arrayOf(mmsId.toString())
        val cursor = contentResolver.query(MMS_PART_URI, null, selection, selectionArgs, null) ?: return emptyList()
        val parts = mutableListOf<JSONObject>()
        cursor.use {
            while (it.moveToNext()) {
                parts.add(JSONObject().apply {
                    put("part_id", it.safeGetLong("_id"))
                    put("ct", it.safeGetString("ct"))
                    put("name", it.safeGetString("name"))
                    put("chset", it.safeGetString("chset"))
                    put("text", it.safeGetString("text"))
                    put("data_location", it.safeGetString("cl"))
                })
            }
        }
        return parts
    }
}

fun Cursor.safeGetInt(columnName: String): Int {
    val index = getColumnIndex(columnName)
    return if (index != -1) getInt(index) else 0
}

fun Cursor.safeGetLong(columnName: String): Long {
    val index = getColumnIndex(columnName)
    return if (index != -1) getLong(index) else 0L
}

fun Cursor.safeGetString(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index != -1) getString(index) else null
}