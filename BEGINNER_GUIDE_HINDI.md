# VVF Smart Manager — शुरुआती गाइड (हिंदी)

## ऐप क्या है?

**VVF Smart Manager** एक offline-first Android ऐप है:

- फाइल ब्राउज़ / कैटेगरी / रीसायकल बिन
- एन्क्रिप्टेड **Vault** (PIN + बायोमेट्रिक)
- सर्च (FTS) + OCR + सेमेंटिक प्लगिन
- **Google Drive** क्लाउड बैकअप / रिस्टोर

---

## हाल के महत्वपूर्ण सुधार (डिवाइस)

| समस्या | मतलब | स्थिति |
|--------|--------|--------|
| Google Sign-In **Code 10** | SHA-1 / Web Client ID गलत | Console में Android + Web client सही करें |
| Backup **HTTP 400** | Drive upload format | ठीक (#115) |
| Backup **404 VVF_Backups** | फोल्डर नाम को fileId समझा | ठीक (#117) |
| **Snapshot failed for: database** | DB फाइल कॉपी नहीं हुई | ठीक (#118) |

---

## APK कैसे लें

1. GitHub → **Actions** → **VVF Smart Manager CI & Quality Gate**
2. हरे (success) run पर **Artifacts** → `vvf-smartmanager-debug-apk`
3. Zip खोलकर APK इंस्टॉल करें (पुरानी ऐप पहले uninstall बेहतर)

---

## Google Drive सेटअप (जरूरी)

1. Google Cloud Console में प्रोजेक्ट
2. **Android** OAuth client: package `com.vvf.smartmanager` + debug/release **SHA-1**
3. **Web** OAuth client ID → ऐप में `GOOGLE_WEB_CLIENT_ID` (देखें `.env.example`)
4. **Google Drive API** ऑन

गलत SHA-1 = Code 10।

---

## लोकल बिल्ड

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Android Studio से Run ▶ भी चल सकता है।

---

## बैकअप टेस्ट

1. Cloud टैब → Google Drive कनेक्ट (**Active**)
2. Vault include टॉगल अपनी मर्जी
3. **Start cloud backup**
4. सफलता पर Cloud Snapshots में एंट्री दिखनी चाहिए

समस्या रहे तो पूरा error टेक्स्ट नोट करें (404 / 400 / Snapshot)।

---

## और दस्तावेज़

- अंग्रेज़ी README: `README.md`
- सुरक्षा स्थिति: `SECURITY_STATUS.md`
- रिलीज़ नोट्स: `RELEASE_NOTES.md`
