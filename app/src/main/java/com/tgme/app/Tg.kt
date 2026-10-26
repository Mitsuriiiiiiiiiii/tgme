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

        // NB : si les bindings exposent onResult(long clientId, TdApi.Object),
        // ajoute le paramètre clientId dans le lambda ci-dessous.
        client = Client.create(
            { obj -> handle(obj) },
            { e -> Log.e(TAG, "tdlib", e) },
            null
        )

        val dir = File(ctx.filesDir, "tdlib")

        send(
            TdApi.SetTdlibParameters()
                .setUseTestDc(false)
                .setDatabaseDirectory(File(dir, "db").absolutePath)
                .setFilesDirectory(File(dir, "files").absolutePath)
                .setUseFileDatabase(true)
                .setUseChatInfoDatabase(true)
                .setUseMessageDatabase(true)
                .setUseSecretChats(true)
                .setApiId(Config.API_ID)
                .setApiHash(Config.API_HASH)
                .setSystemLanguageCode(Config.SYSTEM_LANG)
                .setDeviceModel(Config.DEVICE_MODEL)
                .setSystemVersion(Config.SYSTEM_VERSION)
                .setApplicationVersion(Config.APP_VERSION)
                .setEnableStorageOptimizer(true)
                .setIgnoreFileNames(false)
        )
    }

    fun <T : TdApi.Object> send(query: TdApi.Function<T>, onResult: ((T) -> Unit)? = null) {
        val c = client ?: return
        c.send(query) { obj ->
            if (obj is TdApi.Error) {
                onError?.invoke("${obj.code}: ${obj.message}")
            } else {
                onResult?.invoke(obj)
            }
        }
    }

    private fun handle(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> onAuthState?.invoke(obj.authorizationState)
        }
    }
}
