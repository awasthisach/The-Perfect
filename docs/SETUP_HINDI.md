# VVF Smart Manager — आसान सेटअप गाइड (हिंदी)

यह गाइड **सिर्फ वही** बताती है जो अभी आपके खाते / फ़ोन से करना ज़रूरी है।
ऐप का मुख्य कोड (Explorer, Search, Vault, Index, lag-fix) **main** पर पहले से ठीक है।

**ऐप पैकेज नाम:** `com.vvf.smartmanager`

---

## चरण 1 — APK इंस्टॉल करके जाँच (सबसे पहले)

1. फ़ोन पर Chrome से यह पेज खोलें:  
   https://github.com/awasthisach/The-Perfect/actions/runs/33994361898
2. नीचे **Artifacts** में **`vvf-smartmanager-debug-apk`** पर टैप करके ZIP डाउनलोड करें।
3. ZIP खोलें → अंदर `.apk` फ़ाइल इंस्टॉल करें।  
   (अगर ब्लॉक हो: Settings → Allow install from this source)
4. ऐप खोलें और जाँचें:
   - **All files** / सभी फ़ाइलें अनुमति **Allow** करें
   - Explorer में फ़ोल्डर/फ़ाइलें दिखें
   - 1–2 मिनट रुकें (index बनता है)
   - Search में कुछ लिखें → नतीजे आएँ
   - Vault में PIN सेट → Unlock (फ्रीज़ न हो)

**ठीक है** → चरण 2 पर जाएँ।  
**खराब है** → स्क्रीनशॉट भेजें।

---

## चरण 2 — Google Drive कनेक्ट (Cloud के लिए ज़रूरी)

बिना इसके Cloud Sign-In काम नहीं करेगा। कंप्यूटर या फ़ोन ब्राउज़र से करें।

### 2A. Google Cloud प्रोजेक्ट

1. https://console.cloud.google.com/ खोलें और अपने Google खाते से लॉगिन करें।
2. ऊपर **Select project** → **New Project** → नाम: `VVF Smart Manager` → Create।
3. बाएँ मेनू → **APIs & Services** → **Library**।
4. खोजें **Google Drive API** → **Enable**।

### 2B. OAuth सहमति स्क्रीन

1. **APIs & Services** → **OAuth consent screen**।
2. User type: **External** → Create।
3. App name: `VVF Smart Manager`  
   User support email: अपना ईमेल  
   Developer contact: अपना ईमेल → Save।
4. Scopes: जरूरत हो तो बाद में; अभी Save and Continue चला सकते हैं।
5. Test users: अपना Gmail जोड़ें (जब ऐप Testing mode में हो)।

### 2C. Android OAuth Client ID

1. **APIs & Services** → **Credentials** → **+ Create credentials** → **OAuth client ID**।
2. Application type: **Android**।
3. Name: `VVF Android`।
4. Package name: `com.vvf.smartmanager`
5. SHA-1:
   - अगर आपके पास कंप्यूटर + Android Studio है:  
     Terminal में:
     ```text
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
     **SHA1:** वाली लाइन कॉपी करें।
   - अगर SHA-1 नहीं निकाल पा रहे: मुझसे / डेवलपर से debug SHA-1 मँगवाएँ।
6. Create → **Client ID** कॉपी करके सुरक्षित रखें।

### 2D. Client ID ऐप में लगाना

Client ID मिलते ही चैट में भेज दें (सिर्फ Client ID, पासवर्ड नहीं)।  
मैं कोड/कॉन्फ़िग में wire करके नया APK CI से बनवा दूँगा।

---

## चरण 3 — रिलीज़ साइनिंग (Play Store / असली यूज़र के लिए)

Debug APK रोज़मर्रा टेस्ट के लिए ठीक है। असली रिलीज़ के लिए:

1. एक **release keystore** बनवाएँ (कंप्यूटर पर एक बार):
   ```text
   keytool -genkey -v -keystore vvf-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias vvf
   ```
2. पासवर्ड और `.jks` फ़ाइल **बहुत सुरक्षित** रखें (बैकअप)।
3. GitHub repo → **Settings → Secrets and variables → Actions** में डालें:
   - `KEYSTORE_PATH` या workflow के अनुसार base64 keystore
   - `STORE_PASSWORD`
   - `KEY_ALIAS` (जैसे `vvf`)
   - `KEY_PASSWORD`
4. Release workflow चलाएँ (`.github/workflows/release.yml`) या बताएँ — मैं trigger / डॉक अपडेट कर सकता हूँ।

---

## चरण 4 — Play Console (जब रिलीज़ APK तैयार हो)

1. https://play.google.com/console/ → Developer account (एक बार शुल्क)।
2. Create app → नाम, भाषा, मुफ़्त/पेड।
3. Privacy policy URL (वेब पेज ज़रूरी)।
4. Data safety फ़ॉर्म भरें।
5. Internal testing track पर AAB/APK अपलोड → टेस्टर्स जोड़ें।

---

## क्या कोड/CI पहले से हो चुका है

| काम | स्थिति |
|-----|--------|
| Explorer अनुमति + UI reload | ✅ main |
| Search index + lag fix | ✅ main |
| Semantic bounded search | ✅ main |
| Vault PIN बिना UI freeze | ✅ main |
| Junk / OCR background wire | ✅ main |
| Debug APK CI artifact | ✅ |
| Google Client ID wire | ⏳ आपके Client ID पर |
| Release keystore | ⏳ आपके secrets पर |
| Play listing | ⏳ आपके Play खाते पर |

---

## एक नज़र में — आपको क्या करना है

1. **आज:** चरण 1 (APK जाँच)।  
2. **अगला:** चरण 2 (Google Cloud → Client ID) → Client ID चैट में भेजें।  
3. **फिर:** चरण 3–4 जब स्टोर पर जाना हो।

संदेह हो तो सिर्फ लिखें: «चरण 1 में Explorer खाली है» या «Client ID यह है: …» — उसी हिसाब से अगला काम होगा।
