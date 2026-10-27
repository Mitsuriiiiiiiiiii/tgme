package com.tgme.app

import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object BinlogReader {

    // Doit être EXACTEMENT la même clé que dans Tg.kt
    private val DB_KEY = byteArrayOf(
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
    0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
    0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
    )

    data class AuthKey(val dcId: Int, val key: ByteArray)

    fun extractAuthKey(file: File): AuthKey? {
        if (!file.exists()) return null
        val raw = file.readBytes()
        if (raw.size < 32) return null

        // TDLib utilise AES-CTR. L'IV est stocké dans les 16 premiers octets.
        val iv = raw.copyOfRange(0, 16)
        val encryptedData = raw.copyOfRange(16, raw.size)

        try {
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            val keySpec = SecretKeySpec(DB_KEY, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decrypted = cipher.doFinal(encryptedData)

            return parseBinlog(decrypted)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseBinlog(data: ByteArray): AuthKey? {
        // On cherche les structures : [BLOG] [Taille Nom] [Nom] [Taille Valeur] [Valeur]
        var i = 0
        while (i <= data.size - 8) {
            // Vérifier le magic "BLOG"
            if (data[i] == 0x42.toByte() && data[i+1] == 0x4C.toByte() &&
            data[i+2] == 0x4F.toByte() && data[i+3] == 0x47.toByte()) {

                var p = i + 4
                val nameLen = readIntLE(data, p); p += 4
                if (nameLen in 1..128 && p + nameLen + 4 <= data.size) {
                    val name = String(data, p, nameLen, Charsets.US_ASCII); p += nameLen
                    val valLen = readIntLE(data, p); p += 4

                    if (valLen in 256..1024 && p + valLen <= data.size) {
                        val value = data.copyOfRange(p, p + valLen)

                        // Si c'est une clé d'authentification
                        if (name.startsWith("auth_key")) {
                            var dcId = 2
                            if (name.contains("{")) {
                                dcId = name.substringAfter("{").substringBefore("}").toIntOrNull() ?: 2
                            }
                            // La clé de 256 octets est généralement à la fin de la valeur
                            val key = value.copyOfRange(value.size - 256, value.size)
                            return AuthKey(dcId, key)
                        }
                    }
                    i = p + valLen
                    continue
                }
            }
            i++
        }
        return null
    }

    private fun readIntLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
        ((b[off+1].toInt() and 0xFF) shl 8) or
        ((b[off+2].toInt() and 0xFF) shl 16) or
        ((b[off+3].toInt() and 0xFF) shl 24)
    }
}
