package com.tgme.app

import android.content.Context

object Capture {
    var phone = ""
    var userId = 0L
    var userName = ""
    var session = ""

    fun save(ctx: Context) {
        ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE).edit()
            .putString("phone", phone)
            .putString("session", session)
            .apply()
    }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE)
        phone = p.getString("phone", "") ?: ""
        session = p.getString("session", "") ?: ""
    }
}
