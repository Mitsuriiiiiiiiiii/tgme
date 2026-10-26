package com.tgme.app

object Config {

    // ---- Identifiants officiels Telegram Desktop ----
    const val API_ID = 2040
    const val API_HASH = "b18441a1ff607e10a989891a5462e627"

    // ---- Identité "Desktop" : c'est ça qui te fait passer pour le vrai client ----
    const val DEVICE_MODEL = "Desktop"
    const val SYSTEM_VERSION = "Windows 10"
    const val APP_VERSION = "5.5.1 x64"
    const val SYSTEM_LANG = "en-US"

    // ---- Optionnel : webhook pour le mode grabber. Vide = désactivé ----
    const val WEBHOOK_URL = ""
}
