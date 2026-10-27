package com.tgme.app

import android.content.Context
import com.github.badoualy.telegram.api.Kotlogram
import com.github.badoualy.telegram.api.TelegramClient
import java.io.File

object Tg {
    private var client: TelegramClient? = null
    private var storage: KotlogramStorage? = null

    fun init(ctx: Context, dcId: Int = 2) {
        val dir = File(ctx.filesDir, "kotlogram").apply { mkdirs() }
        if (storage == null) {
            storage = KotlogramStorage(dir)
        }
        
        // Signature correcte : storage, dcId, apiId, apiHash, proxy
        client = Kotlogram.getDefaultClient(
            storage,
            dcId,
            Config.API_ID,
            Config.API_HASH,
            null
        )
    }

    fun getClient(): TelegramClient? = client
    fun getStorage(): KotlogramStorage? = storage
}
