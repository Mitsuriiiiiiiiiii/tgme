# tgme

Client de login Telegram autonome (TDLib / MTProto), qui se fait passer pour
**Telegram Desktop** via ses identifiants officiels.

## Pourquoi l'identité Desktop

L'app envoie `api_id = 2040` + les chaînes `device_model` / `system_version` /
`application_version` de Desktop. Telegram voit donc une connexion qui ressemble
à celle du vrai client, ce qui réduit les flags sur les IP datacenter.

## Procédure

1. **Régénérer le keystore** (supprime l'ancien) :

keytool -genkeypair -v -keystore keystore.jks -alias tgme 
-keyalg RSA -keysize 2048 -validity 10000 
-storepass tgme1234 -keypass tgme1234 
-dname "CN=tgme, OU=dev, O=tgme, L=Paris, C=FR"


Les mots de passe doivent correspondre à ceux de `gradle.properties`.

2. Commit `keystore.jks`, `gradle.properties`, puis push sur `main`.
3. Le workflow produit l'APK signé dans **Actions → Artifacts → tgme-apk**.
4. Installer l'APK, entrer le numéro, puis lire le code **dans l'app Telegram
   déjà connectée sur un autre appareil**.

## Réglages

Tout est dans `app/src/main/java/com/tgme/app/Config.kt`.

Pour changer de version Desktop :
https://github.com/telegramdesktop/tdesktop/releases

## Points sensibles

- **API_ID_PUBLISHED_FLOOD** : la doc Telegram prévient que les api_id officiels
  sont limités côté serveur. Si tu l'obtiens, bascule sur ton propre api_id.
- L'api_id de Desktop est partagé par tout le monde : tu n'as pas les limites de
  flood « élargies » réservées au numéro ayant enregistré l'api_id.
- La première connexion télécharge des données TDLib (compte quelques secondes).
