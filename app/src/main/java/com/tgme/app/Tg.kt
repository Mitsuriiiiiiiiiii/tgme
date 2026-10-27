package com.tgme.app

import android.content.Context
import com.github.badoualy.telegram.api.Kotlogram
import com.github.badoualy.telegram.api.TelegramClient
import java.io.File

object Tg {
    private var client: TelegramClient? = null
    private var storage: KotlogramStorage? = null

    // On prend le DC2 par défaut (le plus courant), mais on peut passer n'importe quel DC (1 à 5)
    fun init(ctx: Context, dcId: Int = 2) {
        val dir = File(ctx.filesDir, "kotlogram").apply { mkdirs() }
        
        // On garde le même stockage pour ne pas perdre la clé si on change de DC
        if (storage == null) {
            storage = KotlogramStorage(dir)
        }
        
        // Note : getDefaultClient ne prend pas de Context dans Kotlogram officiel
        client = Kotlogram.getDefaultClient(
            storage,
            when (dcId) {
                1 -> Kotlogram.PROD_DC1
                2 -> Kotlogram.PROD_DC2
                3 -> Kotlogram.PROD_DC3
                4 -> Kotlogram.PROD_DC4
                5 -> Kotlogram.PROD_DC5
                else -> Kotlogram.PROD_DC2
            }
        )
    }

    fun getClient(): TelegramClient? = client
    fun getStorage(): KotlogramStorage? = storage
}
