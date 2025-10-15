# SMS Webhook Android App

Application Android qui capture les SMS/MMS reçus et les envoie à un webhook Flask.

## Structure
- **webhook/** : Projet Android Studio (Kotlin).
  - app/src/main/java/com/example/smswebhook/ : Code source.
  - AndroidManifest.xml : Permissions et composants.
  - build.gradle.kts : Dépendances.
- **ecoute/** : Serveur Flask Python pour recevoir les données.

## Prérequis
- Android Studio.
- Appareil Android API 24+.
- Python 3+ avec Flask (`pip install flask Flask-Cors`).

## Création de l'Application
1. Ouvrir Android Studio, créer projet Empty Activity (Kotlin, minSdk 24).
2. Ajouter dossiers : receiver, service, data, util.
3. Copier fichiers Kotlin (MainActivity, WebhookService, SmsMmsReceiver, SmsDataExtractor, Env, Prefs, HttpClient).
4. Configurer themes.xml (parent Theme.AppCompat.Light.NoActionBar).
5. Mettre à jour manifest : permissions (READ_SMS, etc.), theme, cleartextTraffic=true.
6. Sync Gradle, build APK.
7. Installer sur téléphone via ADB ou Studio.
8. Lancer Flask : `python app.py` dans ecoute/ (host=0.0.0.0, port=5000).
9. Ouvrir app sur téléphone : accepter permissions.
10. Envoyer SMS/MMS : vérifier réception sur http://IP:5000.

## Tests
- Envoyer SMS : voir logs ADB.
- Dual-SIM : vérifier champ recipient.
- Redémarrer téléphone : service persiste.

Problèmes courants : permissions refusées, réseau WiFi, theme AppCompat.