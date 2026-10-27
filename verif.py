import asyncio
from telethon import TelegramClient
from telethon.sessions import StringSession
from telethon.errors import AuthKeyUnregisteredError

API_ID = 2040
API_HASH = "b18441a1ff607e10a989891a5462e627"
SESSION = open('/data/data/com.termux/files/home/tgme/session.txt').read().strip()

# toutes les IPs connues de DC4 (Telegram en a plusieurs)
DC4_IPS = ["149.154.167.91", "149.154.167.92", "149.154.167.50", "149.154.167.51"]

async def try_ip(ip):
    # reconstruit la session avec cette IP
    import base64, struct
    raw = base64.urlsafe_b64decode(SESSION[1:] + '=' * (-len(SESSION[1:]) % 4))
    dc = raw[0]
    key = raw[7:]
    new_raw = struct.pack('>B', dc) + bytes(map(int, ip.split('.'))) + struct.pack('>H', 443) + key
    new_sess = '1' + base64.urlsafe_b64encode(new_raw).decode()

    c = TelegramClient(StringSession(new_sess), API_ID, API_HASH, connection_retries=0)
    try:
        print(f"→ {ip} ", end="", flush=True)
        await asyncio.wait_for(c.connect(), timeout=20)
        if await c.is_user_authorized():
            me = await c.get_me()
            print(f"✅ {me.first_name} (ID {me.id})")
            return True
        print("❌ non autorisé")
    except asyncio.TimeoutError:
        print("⏱️ timeout")
    except AuthKeyUnregisteredError:
        print("❌ AUTH_KEY_UNREGISTERED")
    except Exception as e:
        print(f"⚠️  {type(e).__name__}")
    finally:
        await c.disconnect()
    return False

async def main():
    for ip in DC4_IPS:
        if await try_ip(ip):
            return
        await asyncio.sleep(2)   # petit délai entre essais

asyncio.run(main())
