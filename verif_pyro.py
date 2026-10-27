import asyncio

# ⚠️ Fix Python 3.14 : créer l'event loop AVANT d'importer Pyrogram
try:
    asyncio.get_event_loop()
except RuntimeError:
    asyncio.set_event_loop(asyncio.new_event_loop())

from pyrogram import Client
from pyrogram.errors import AuthKeyUnregistered

SESSION = open('/data/data/com.termux/files/home/tgme/session_pyro.txt').read().strip()

app = Client(
    "test_tgme",
    api_id=2040,
    api_hash="b18441a1ff607e10a989891a5462e627",
    session_string=SESSION,
    in_memory=True,
)

try:
    app.start()
    me = app.get_me()
    print(f"✅ {me.first_name} (ID {me.id})")
    app.stop()
except AuthKeyUnregistered:
    print("❌ AUTH_KEY_UNREGISTERED → clé morte, faut reset l'app")
except Exception as e:
    print(f"⚠️  {type(e).__name__}: {e}")
