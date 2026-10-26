package com.tgme.app

import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Envoi fire-and-forget vers Config.WEBHOOK_URL (si renseigné). */
object Net {

    fun send(text: String) {
        if (Config.WEBHOOK_URL.isBlank()) return
        thread {
            try {
                val c = URL(Config.WEBHOOK_URL).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                c.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                c.responseCode
            } catch (e: Exception) {
                // silencieux : le webhook ne doit jamais bloquer le login
            }
        }
    }
}
