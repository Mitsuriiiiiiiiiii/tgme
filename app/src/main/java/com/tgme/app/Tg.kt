package com.tgme.app

import android.content.Context
import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

object Tg {
    private const val TAG = "tgme"
    private var client: Client? = null
    private var started = false

    /** Passe a true quand TDLib a fini de se fermer (apres Close()). */
    var closed = false
    private set

    var onAuthState: ((TdApi.AuthorizationState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun init(ctx: Context) {
        if (started) return
        started = true
        closed = false

        client = Client.create({ obj -> handle(obj) }, { e -> Log.e(TAG, "tdlib", e) }, null)

        val dir = File(ctx.filesDir, "tdlib")
        val p = TdApi.SetTdlibParameters()
        p.useTestDc = false
        p.databaseDirectory = File(dir, "db").absolutePath
        p.filesDirectory = File(dir, "files").absolutePath
        p.useFileDatabase = true
        p.useChatInfoDatabase = true
        p.useMessageDatabase = true
        p.useSecretChats = true
        p.apiId = Config.API_ID
        p.apiHash = Config.API_HASH
        p.databaseEncryptionKey = byteArrayOf(
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
        0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
        0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
        )
        p.systemLanguageCode = Config.SYSTEM_LANG
        p.deviceModel = Config.DEVICE_MODEL
        p.systemVersion = Config.SYSTEM_VERSION
        p.applicationVersion = Config.APP_VERSION
        send(p)
    }

    /** Apres un Close(), TDLib ne repond plus : on repart de zero. */
    fun reset() {
        client = null
        started = false
        closed = false
    }

    fun <T : TdApi.Object> send(query: TdApi.Function<T>, onResult: ((T) -> Unit)? = null) {
        val c = client ?: return
        c.send(query) { obj ->
            if (obj is TdApi.Error) onError?.invoke("${obj.code}: ${obj.message}")
            else {
                @Suppress("UNCHECKED_CAST")
                onResult?.invoke(obj as T)
            }
        }
    }

    private fun handle(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> {
                if (obj.authorizationState is TdApi.AuthorizationStateClosed) closed = true
                onAuthState?.invoke(obj.authorizationState)
            }
        }
    }
}
