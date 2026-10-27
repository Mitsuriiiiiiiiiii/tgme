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
        
        val dc = when (dcId) {
            1 -> Kotlogram.PROD_DC1
            2 -> Kotlogram.PROD_DC2
            3 -> Kotlogram.PROD_DC3
            4 -> Kotlogram.PROD_DC4
            5 -> Kotlogram.PROD_DC5
            else -> Kotlogram.PROD_DC2
        }
        
        client = Kotlogram.getDefaultClient(storage, dc)
    }

    fun getClient(): TelegramClient? = client
    fun getStorage(): KotlogramStorage? = storage
}
