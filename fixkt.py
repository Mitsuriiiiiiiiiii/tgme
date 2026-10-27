#!/usr/bin/env python3
"""Reformate l'indentation d'un fichier .kt (4 espaces par niveau d'accolade).

Usage: python fix_kt.py <fichier.kt> [-w]
  -w : écrit dans le fichier (sinon affiche sur stdout)
"""
import sys, re

INDENT = "    "  # 4 espaces

def strip_strings_and_comments(line: str) -> str:
    """Retourne la ligne avec chaînes et commentaires remplacés par des espaces,
    pour ne compter que les vraies accolades structurelles."""
    out = []
    i = 0
    n = len(line)
    while i < n:
        c = line[i]
        # commentaire ligne
        if c == '/' and i + 1 < n and line[i+1] == '/':
            out.append(' ' * (n - i))
            break
        # commentaire bloc (simple, non imbriqué)
        if c == '/' and i + 1 < n and line[i+1] == '*':
            j = line.find('*/', i + 2)
            if j == -1:
                out.append(' ' * (n - i))
                break
            out.append(' ' * (j + 2 - i))
            i = j + 2
            continue
        # chaîne triple
        if line[i:i+3] == '"""':
            j = line.find('"""', i + 3)
            if j == -1:
                out.append(' ' * (n - i))
                break
            out.append(' ' * (j + 3 - i))
            i = j + 3
            continue
        # chaîne simple
        if c == '"':
            j = i + 1
            while j < n:
                if line[j] == '\\':
                    j += 2
                    continue
                if line[j] == '"':
                    j += 1
                    break
                # template ${...} : on garde tel quel (rare sur une ligne)
                j += 1
            out.append(' ' * (j - i))
            i = j
            continue
        # char
        if c == "'":
            j = i + 1
            while j < n:
                if line[j] == '\\':
                    j += 2
                    continue
                if line[j] == "'":
                    j += 1
                    break
                j += 1
            out.append(' ' * (j - i))
            i = j
            continue
        out.append(c)
        i += 1
    return ''.join(out)

def reformat(src: str) -> str:
    lines = src.splitlines()
    depth = 0
    out = []
    in_block_comment = False

    for raw in lines:
        stripped = raw.strip()

        # gestion bloc /* */ multi-lignes
        if in_block_comment:
            out.append(INDENT * depth + stripped if stripped else '')
            if '*/' in stripped:
                in_block_comment = False
            continue

        if stripped.startswith('/*'):
            out.append(INDENT * depth + stripped)
            if '*/' not in stripped:
                in_block_comment = True
            continue

        if not stripped:
            out.append('')
            continue

        # ligne qui commence par '}' → recule AVANT d'imprimer
        effective = strip_strings_and_comments(stripped)
        lead_close = effective.lstrip().startswith('}')

        line_depth = depth - 1 if lead_close else depth
        if line_depth < 0:
            line_depth = 0
        out.append(INDENT * line_depth + stripped)

        # met à jour la profondeur : net({) - net(})
        opens = effective.count('{')
        closes = effective.count('}')
        depth += opens - closes
        if depth < 0:
            depth = 0

    return '\n'.join(out) + ('\n' if src.endswith('\n') else '')

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    path = sys.argv[1]
    write = '-w' in sys.argv[2:]
    with open(path, 'r', encoding='utf-8') as f:
        src = f.read()
    fixed = reformat(src)
    if write:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(fixed)
        print(f"✅ {path} reformaté")
    else:
        sys.stdout.write(fixed)

if __name__ == '__main__':
    main()
