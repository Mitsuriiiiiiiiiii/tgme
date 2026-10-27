package com.tgme.app

import com.github.badoualy.telegram.api.TelegramApiStorage
import com.github.badoualy.telegram.mtproto.auth.AuthKey
import com.github.badoualy.telegram.mtproto.state.DataCenter
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class KotlogramStorage(private val dir: File) : TelegramApiStorage {
    private val authKeyFile = File(dir, "auth_key.dat")

    override var dataCenter: DataCenter? = null
    
    override var authKey: AuthKey? = null
        get() {
            if (field == null && authKeyFile.exists()) {
                try {
                    ObjectInputStream(authKeyFile.inputStream()).use { field = it.readObject() as AuthKey }
                } catch (e: Exception) { }
            }
            return field
        }
        set(value) {
            field = value
            if (value != null) {
                ObjectOutputStream(authKeyFile.outputStream()).use { it.writeObject(value) }
            }
        }
}
