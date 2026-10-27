// Dans MainActivity.kt

private var currentDc = 2 // DC par défaut

// ... dans onCreate ...
Tg.init(this, currentDc)
client = Tg.getClient()
// ...

private fun showPhone(ready: Boolean) {
    // ...
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
                // 🔥 GESTION DE LA MIGRATION DE DC
                val msg = e.message ?: ""
                if (msg.contains("PHONE_MIGRATE_")) {
                    val newDc = msg.substringAfter("PHONE_MIGRATE_").takeWhile { it.isDigit() }.toIntOrNull()
                    if (newDc != null && newDc in 1..5) {
                        runOnUiThread { toast("Redirection vers le DC$newDc...") }
                        currentDc = newDc
                        Tg.init(this@MainActivity, currentDc) // On recrée le client sur le bon DC
                        client = Tg.getClient()
                        
                        // On relance l'envoi du code sur le nouveau DC
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
                
                // Autre erreur
                runOnUiThread { 
                    toast("Erreur: ${e.message}")
                    btn.isEnabled = true
                }
            }
        }
    }
}
