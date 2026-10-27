import struct, base64, ipaddress, sys, zlib

# Mapping des IPs Telegram par DC
IPS = {1:"149.154.175.53", 2:"149.154.167.51", 3:"149.154.175.100",
       4:"149.154.167.91", 5:"91.108.56.130"}

def parse_tdlib_binlog(filepath):
    with open(filepath, 'rb') as f:
        data = f.read()

    print(f"Taille du fichier: {len(data)} octets")
    keys = []
    
    # On cherche la structure "auth_key" de TDLib
    # Format typique: [Taille(4)] [Nom de la clé, ex: auth_key{4}] [Taille valeur(4)] [Valeur]
    # On va chercher directement les chaînes "auth_key"
    import re
    for match in re.finditer(b'auth_key', data):
        start = match.start()
        # Remonter pour voir la taille de la chaîne
        if start >= 4:
            name_len = struct.unpack('<I', data[start-4:start])[0]
            if name_len > 8 and name_len < 50:
                # Lire le nom complet
                name = data[start:start+name_len].decode('ascii', errors='ignore')
                # Le DC est souvent dans le nom, ex: "auth_key{4}"
                dc_id = 2
                if '{' in name:
                    try: dc_id = int(name.split('{')[1].split('}')[0])
                    except: pass
                
                # Juste après le nom, il y a la taille de la valeur
                val_start = start + name_len
                if val_start + 4 <= len(data):
                    val_len = struct.unpack('<I', data[val_start:val_start+4])[0]
                    if val_len == 256 or val_len == 260:
                        val_data = data[val_start+4 : val_start+4+val_len]
                        if val_len == 260:
                            dc_id = struct.unpack('<I', val_data[:4])[0]
                            key = val_data[4:]
                        else:
                            key = val_data
                        
                        # Vérification que c'est bien une clé (pas que des zéros)
                        if key.count(0) < 50:
                            keys.append((dc_id, key))
                            print(f"✅ Vraie clé trouvée ! DC={dc_id}, Taille={len(key)}")

    # Déduplication
    unique_keys = {}
    for dc, k in keys:
        unique_keys[k] = dc
        
    return unique_keys

def make_session(dc, key):
    payload = struct.pack('>B', dc) + ipaddress.ip_address(IPS[dc]).packed + struct.pack('>H', 443) + key
    return '1' + base64.urlsafe_b64encode(payload).decode()

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python extract_strict.py /chemin/td.binlog.dec")
        sys.exit(1)
        
    keys = parse_tdlib_binlog(sys.argv[1])
    
    if not keys:
        print("❌ Aucune vraie clé TDLib trouvée. Le fichier n'est peut-être pas bien déchiffré.")
    else:
        print(f"\n🎉 {len(keys)} clé(s) unique(s) trouvée(s).")
        for k, dc in keys.items():
            print(f"\n--- Session Telethon pour DC{dc} ---")
            print(make_session(dc, k))
