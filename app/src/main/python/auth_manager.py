import asyncio
import traceback
import os
from telethon import TelegramClient
from telethon.sessions import StringSession

API_ID = 2040
API_HASH = "b18441a1ff607e10a989891a5462e627"

LOG_PATH = "/data/user/0/com.tgme.app/files/python_log.txt"
HASH_PATH = "/data/user/0/com.tgme.app/files/phone_code_hash.txt"

def log(msg):
    try:
        with open(LOG_PATH, "a") as f:
            f.write(msg + "\n")
            f.flush()
    except Exception:
        pass

async def _login_async(phone, code, password):
    log(f"=== START phone={phone} code={'oui' if code else 'non'} pass={'oui' if password else 'non'}")
    
    # On garde une session en mémoire (StringSession vide) pour ce processus
    client = TelegramClient(
        StringSession(), API_ID, API_HASH,
        connection_retries=1,
        timeout=20,
    )
    try:
        await client.connect()
        log(f"Connecté. Autorisé={await client.is_user_authorized()}")
        
        if not await client.is_user_authorized():
            if not code:
                log("Envoi du code request...")
                result = await client.send_code_request(phone)
                # On sauvegarde le hash pour le prochain appel
                with open(HASH_PATH, "w") as f:
                    f.write(result.phone_code_hash)
                log(f"Code envoyé OK. Hash sauvegardé: {result.phone_code_hash}")
                return "NEED_CODE"
            
            # On récupère le hash sauvegardé
            if os.path.exists(HASH_PATH):
                with open(HASH_PATH, "r") as f:
                    phone_code_hash = f.read().strip()
                log(f"Hash récupéré: {phone_code_hash}")
            else:
                phone_code_hash = None
                log("ATTENTION: Aucun hash trouvé, on tente sans.")
            
            try:
                log("Sign in avec code et hash...")
                await client.sign_in(phone, code, phone_code_hash=phone_code_hash)
            except Exception as e:
                log(f"sign_in exception: {type(e).__name__}: {e}")
                if "SessionPasswordNeeded" in type(e).__name__ or "password" in str(e).lower():
                    if not password:
                        return "NEED_PASSWORD"
                    await client.sign_in(password=password)
                else:
                    raise
        
        me = await client.get_me()
        final = client.session.save()
        log(f"SUCCÈS user={me.first_name}")
        # On nettoie le hash une fois la connexion réussie
        if os.path.exists(HASH_PATH):
            os.remove(HASH_PATH)
        return f"OK|{me.first_name or 'User'}|{final}"
    
    except Exception as e:
        tb = traceback.format_exc()
        log(f"ERREUR:\n{tb}")
        return f"ERROR|{type(e).__name__}: {e}"
    finally:
        try: await client.disconnect()
        except: pass

def process_login(phone, code="", password=""):
    try:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        return loop.run_until_complete(_login_async(phone, code, password))
    except Exception as e:
        tb = traceback.format_exc()
        log(f"FATAL:\n{tb}")
        return f"ERROR|FATAL: {e}"
