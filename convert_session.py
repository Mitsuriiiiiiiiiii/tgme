import struct, base64

s = open('/data/data/com.termux/files/home/tgme/session.txt').read().strip()
raw = base64.urlsafe_b64decode(s[1:] + '=' * (-len(s[1:]) % 4))
dc_id = raw[0]
auth_key = raw[7:]

# Pyrogram V1 : >BI?256sQ?  = 1 + 4 + 1 + 256 + 8 + 1 = 271 octets
pyro_raw = struct.pack('>BI?256sQ?', dc_id, 2040, False, auth_key, 0, False)
print(f"packed: {len(pyro_raw)} octets (attendu 271)")

pyro = base64.urlsafe_b64encode(pyro_raw).decode('ascii')
open('/data/data/com.termux/files/home/tgme/session_pyro.txt', 'w').write(pyro)
print(f"✅ session pyro: {len(pyro)} chars")
print(f"   début: {pyro[:20]}…")
