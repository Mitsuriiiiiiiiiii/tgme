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
        
        // On passe le chiffre du DC (1 à 5) directement
        client = Kotlogram.getDefaultClient(
            storage,
            dcId,
            null // Pas de proxy
        )
    }

    fun getClient(): TelegramClient? = client
    fun getStorage(): KotlogramStorage? = storage
}
