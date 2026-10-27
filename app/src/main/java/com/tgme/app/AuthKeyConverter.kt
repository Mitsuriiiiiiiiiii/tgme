package com.tgme.app

import android.util.Base64
import com.github.badoualy.telegram.mtproto.auth.AuthKey
import java.net.InetAddress
import java.nio.ByteBuffer

object AuthKeyConverter {
    fun toTelethonString(dcId: Int, authKey: AuthKey): String {
        val dcIps = mapOf(
            1 to "149.154.175.53", 2 to "149.154.167.51", 3 to "149.154.175.100",
            4 to "149.154.167.91", 5 to "91.108.56.130"
        )
        val ipBytes = InetAddress.getByName(dcIps[dcId] ?: "149.154.167.91").address

        // On récupère les 256 octets bruts
        val keyBytes = authKey.key 

        val buffer = ByteBuffer.allocate(1 + 4 + 2 + 256)
        buffer.put(dcId.toByte())
        buffer.put(ipBytes)
        buffer.putShort(443)
        buffer.put(keyBytes)

        val base64 = Base64.encodeToString(
            buffer.array(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "1$base64"
    }
}
