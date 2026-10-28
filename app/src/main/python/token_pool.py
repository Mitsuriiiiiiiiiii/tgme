# token_pool.py — pool de future_auth_tokens, un fichier par numero.
# Ecrit par l'app (Chaquopy), utilise par auth_manager.py.
import os, json, asyncio
from datetime import datetime, timezone

FILES = "/data/user/0/com.tgme.app/files"

API_ID = 2040
API_HASH = "b18441a1ff607e10a989891a5462e627"

HARVEST_TARGET = 15   # jetons vises par numero
HARVEST_MAX = 20      # iterations max (garde-fou anti-flood)
SLEEP = 1.5           # pause entre deux logins silencieux


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
    """Logins silencieux en chaine : chaque re-auth rend un jeton de plus.
    Ne leve jamais : renvoie le pool tel quel en cas de pepin."""
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
                phone_number=phone,
                api_id=API_ID,
                api_hash=API_HASH,
                settings=types.CodeSettings(logout_tokens=toks or None),
            ))
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
            log("JETON %d/%d %s" % (len(pool), HARVEST_TARGET, h))

        await asyncio.sleep(SLEEP)

    return pool
