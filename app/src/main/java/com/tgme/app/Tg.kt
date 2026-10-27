package com.tgme.app

import android.content.Context
import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

/**
 * Singleton TDLib. Une seule instance, un seul flux d'updates.
 * L'app ne fait rien "en dur" : tout est piloté par les updates de Telegram.
 */
object Tg {

    private const val TAG = "tgme"
    private var client: Client? = null
    private var started = false

    /** Notifié à chaque changement d'état d'autorisation. */
    var onAuthState: ((TdApi.AuthorizationState) -> Unit)? = null

    /** Notifié sur erreur TDLib. */
    var onError: ((String) -> Unit)? = null

    fun init(ctx: Context) {
        if (started) return
        started = true

        client = Client.create(
            { obj -> handle(obj) },
            { e -> Log.e(TAG, "tdlib", e) },
            null
        )

        // Les bindings TDLib n'exposent pas de setters fluents : on affecte les champs.
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
        p.systemLanguageCode = Config.SYSTEM_LANG
        p.deviceModel = Config.DEVICE_MODEL
        p.systemVersion = Config.SYSTEM_VERSION
        p.applicationVersion = Config.APP_VERSION

        send(p)
    }

    fun <T : TdApi.Object> send(query: TdApi.Function<T>, onResult: ((T) -> Unit)? = null) {
        val c = client ?: return
        c.send(query) { obj ->
            if (obj is TdApi.Error) {
                onError?.invoke("${obj.code}: ${obj.message}")
            } else {
                @Suppress("UNCHECKED_CAST")
                onResult?.invoke(obj as T)
            }
        }
    }

    private fun handle(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> onAuthState?.invoke(obj.authorizationState)
        }
    }
}
