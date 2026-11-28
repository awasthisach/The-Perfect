# VVF Smart Manager — सरल हिंदी में परियोजना की स्थिति

## यह ऐप किस काम के लिए बन रहा है?

इस ऐप के दो मुख्य काम होंगे:

1. **Semantic Search:** फाइल का नाम याद न हो, तब भी उसके अंदर लिखी बात या अर्थ के आधार पर दस्तावेज़ खोजना।
2. **Duplicate Finder:** फोन, Memory Card और Google Drive में एक जैसी या बहुत मिलती-जुलती फाइलें पहचानना।

Encrypted Vault और सामान्य File Manager सहायक सुविधाएँ हैं।

## किन जगहों की फाइलें लक्ष्य में हैं?

- फोन की Internal Storage — Android जितनी अनुमति देता है।
- Memory Card — आप जिस फ़ोल्डर की अनुमति देंगे, केवल वही।
- Google Drive का My Drive और “Shared with me”।
- तीनों जगहों के बीच भी duplicate मिलान।

Shared Drives अलग सुविधा है; अभी शुरुआती दायरे में शामिल नहीं है।

## आपकी गोपनीयता का नियम

AI और document-content analysis फोन पर ही चलना चाहिए। आपकी फाइल का टेक्स्ट, OCR टेक्स्ट या embedding किसी cloud AI सेवा को नहीं भेजना है।

किसी फाइल को ऐप अपने-आप delete या move नहीं करेगा। हर ऐसी कार्रवाई से पहले आपकी स्पष्ट अनुमति जरूरी होगी।

## अभी क्या अधूरा है?

यह महत्वपूर्ण है: ऊपर लिखी बातें हमारा तय किया हुआ लक्ष्य हैं, अभी उपलब्ध सभी सुविधाओं का दावा नहीं।

- मौजूदा semantic plugin अभी neural AI model नहीं, बल्कि deterministic hash-based similarity इस्तेमाल करता है।
- दस्तावेज़ों का पूरा टेक्स्ट semantic indexing के लिए अभी तैयार नहीं है।
- Google Drive का मौजूदा `drive.file` scope पूरे पुराने Drive डेटा तक अपने-आप पहुँच नहीं देता।
- My Drive और Shared with me की पूरी indexing अभी प्रमाणित नहीं है।
- Memory Card का पूरा SAF scan/resume अभी प्रमाणित नहीं है।
- फोन, SD Card और Drive का एकीकृत duplicate engine अभी प्रमाणित नहीं है।
- पुराने remote embedding client को local-only नीति के अनुरूप तकनीकी रूप से अलग करना बाकी है।

इसलिए अभी इसे पूर्ण या production-ready ऐप न मानें।

## काम किस क्रम में होगा?

1. README, repository description और सभी दस्तावेज़ों को एक ही सही product scope पर लाना।
2. Phone, SD Card और Drive के लिए एक साझा source-aware index बनाना।
3. Drive authorization और Shared-with-me listing ठीक करना।
4. दस्तावेज़ का टेक्स्ट/OCR सुरक्षित तरीके से फोन पर index करना।
5. Local neural model और semantic search जोड़ना।
6. तीनों स्रोतों के बीच exact और near-duplicate पहचानना।
7. Offline, privacy, performance और वास्तविक Android device पर परीक्षण करना।

## उपयोगी लिंक

- [मुख्य README](README.md)
- [उत्पाद का निश्चित दायरा](PRODUCT_SCOPE.md)
- [तकनीकी योजना](TECHNICAL_DOCUMENTATION.md)
- [Release स्थिति](RELEASE_NOTES.md)
- [PR #225 — अभी Draft / NO-SHIP](https://github.com/awasthisach/The-Perfect/pull/225)
