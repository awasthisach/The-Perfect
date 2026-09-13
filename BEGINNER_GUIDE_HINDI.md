# VVF Smart Manager — शुरुआती गाइड (हिंदी)

## ऐप क्या है?

Offline-first Android ऐप: फाइल मैनेजर, एन्क्रिप्टेड Vault, सर्च/OCR, Google Drive बैकअप।

## हाल के सुधार

| समस्या | स्थिति |
|--------|--------|
| Drive HTTP 400 / 404 | ठीक (#115, #117) |
| Snapshot failed for database | ठीक (#118) |
| CodeQL workflow alerts | ठीक (#120) |

## APK

GitHub **Actions** → हरा CI run → Artifacts → `vvf-smartmanager-debug-apk`

## Google Drive

1. Android OAuth + SHA-1  
2. Web Client ID (`.env.example`)  
3. Drive API ऑन  

गलत SHA-1 = Code 10।

## बैकअप टेस्ट

Cloud → Drive Connected → **Start cloud backup**
