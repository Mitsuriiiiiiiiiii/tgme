package com.tgme.app

import com.github.badoualy.telegram.mtproto.auth.AuthKey
import com.github.badoualy.telegram.mtproto.state.AbsMTProtoState
import com.github.badoualy.telegram.mtproto.state.ConnectionInfo
import com.github.badoualy.telegram.api.TelegramApiStorage
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class KotlogramStorage(private val dir: File) : TelegramApiStorage {
    private val authKeyFile = File(dir, "auth_key.dat")
    private var authKey: AuthKey? = null

    init {
        if (authKeyFile.exists()) {
            ObjectInputStream(authKeyFile.inputStream()).use { authKey = it.readObject() as AuthKey }
        }
    }

    override fun saveAuthKey(authKey: AuthKey) {
        this.authKey = authKey
        ObjectOutputStream(authKeyFile.outputStream()).use { it.writeObject(authKey) }
    }

    override fun loadAuthKey(): AuthKey? = authKey

    // Méthodes d'état réseau (on laisse vide pour ce test, Kotlogram gérera)
    override fun saveState(state: AbsMTProtoState) {}
    override fun loadState(): AbsMTProtoState? = null
    override fun saveConnectionInfo(info: ConnectionInfo) {}
    override fun loadConnectionInfo(): ConnectionInfo? = null
}
