package com.tgme.app

import com.github.badoualy.telegram.api.TelegramApiStorage
import com.github.badoualy.telegram.mtproto.auth.AuthKey
import com.github.badoualy.telegram.mtproto.auth.TempAuthKey
import com.github.badoualy.telegram.mtproto.model.DataCenter
import com.github.badoualy.telegram.mtproto.model.MTSession
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class KotlogramStorage(private val dir: File) : TelegramApiStorage {

    private val authKeyFile = File(dir, "auth_key.dat")
    private val dcFile = File(dir, "dc.dat")

    override var session: MTSession? = null
    override var tempAuthKey: TempAuthKey? = null

    override var dataCenter: DataCenter? = null
        get() {
            if (field == null && dcFile.exists()) {
                try { ObjectInputStream(dcFile.inputStream()).use { field = it.readObject() as DataCenter } } catch (e: Exception) { }
            }
            return field
        }
        set(value) {
            field = value
            if (value != null) {
                ObjectOutputStream(dcFile.outputStream()).use { it.writeObject(value) }
            }
        }

    override var authKey: AuthKey? = null
        get() {
            if (field == null && authKeyFile.exists()) {
                try { ObjectInputStream(authKeyFile.inputStream()).use { field = it.readObject() as AuthKey } } catch (e: Exception) { }
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
