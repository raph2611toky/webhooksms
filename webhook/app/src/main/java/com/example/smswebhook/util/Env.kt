package com.example.smswebhook.util

object Env {
    /*
     * Ancien endpoint Django.
     * Android envoie ici les SMS entrants capturés.
     */
    const val WEBHOOK_URL = "http://192.168.0.117:8000/api/chatbot/sms/webhook/"

    /*
     * Token utilisé dans les deux sens :
     * - Android -> Django pour les SMS entrants
     * - Django -> Android pour demander l'envoi d'un SMS
     */
    const val WEBHOOK_TOKEN = "HZZFQWVathzihNNidlnNFmzmAWjgxqRdprWkMgujvwyPyxNBBMgGCTpyWLjYKucHtGUSyRcaVgLiaGPONOpoBxDHSNDAjPqBiaMNyjMnnXHlVZkFUwoJspuqsWMKBBHRgpqLfsTaWlnYXEDUnJDdAKDLHbiELlSfFoRKqNtTYlxBHzqogcoFIpuclnUTKgyXZNKZSTVDYcUtAjnwVAoedRhjdwYbYopQUifEAdSgfUOKIcDHhazBTimCqF"

    /*
     * Nouveau serveur local Android.
     * Le téléphone écoute sur toutes les interfaces : 0.0.0.0:8000
     * L'URL publique est construite dynamiquement avec NetworkUtils.
     */
    const val LOCAL_SMS_SERVER_PORT = 8000
    const val LOCAL_SMS_SEND_PATH = "/sms/webhook/send/"

    const val CONNECT_TIMEOUT_SECONDS = 20L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
}