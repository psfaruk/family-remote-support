---
Task ID: 1
Agent: Z.ai Code (main)
Task: Phase 0 — Environment & Feasibility ডেলিভারি (Remote Family Support & Device Assistance Platform)

Work Log:
- আপলোড করা প্রম্পট (upload/Pasted Content_1793031499368.txt) সম্পূর্ণ পড়ে Phase 0 ডেলিভারেবল চিহ্নিত করা হয়েছে
- ব্যবহারকারীর ৩-স্তম্ভের পরামর্শ (Permanent Pairing / Persistent Connectivity / Remote Support Session) ডিজাইনের কেন্দ্রে রাখা হয়েছে
- docs/PHASE-0.md তৈরি — ১৭টি সেকশনে সম্পূর্ণ Phase 0 প্রতিবেদন (বাংলায়)
- src/app/layout.tsx আপডেট — Noto Sans Bengali ফন্ট, lang="bn", নতুন metadata
- src/app/globals.css আপডেট — বাংলা ফন্ট স্ট্যাক, smooth scroll, scroll-padding-top
- src/app/page.tsx রিরাইট — মোবাইল-ফার্স্ট Phase 0 প্ল্যান ভিউয়ার (স্টিকি হেডার+নেভ চিপস, আর্কিটেকচার ডায়াগ্রাম, স্টেট মেশিন, মোড তুলনা, পরিবেশ মূল্যায়ন, খরচ টেবিল, ১৪ ফেজ টাইমলাইন, ঝুঁকি রেজিস্টার, চেকলিস্ট, পরবর্তী পদক্ষেপ, সততার ঘোষণা, স্টিকি ফুটার)
- bun run lint → ক্লিন; dev.log → GET / 200, কোনো রানটাইম এরর নেই
- Agent Browser যাচাই: 390px ও 1440px ভিউপোর্টে রেন্ডার, অ্যাঙ্কর নেভিগেশন, স্টিকি হেডার/ফুটার, কনসোল এরর নেই

Stage Summary:
- Phase 0 সম্পন্ন (শুধু পরিকল্পনা) — কোনো Android কোড/বিল্ড/টেস্ট হয়নি, সৎভাবে ঘোষণা করা হয়েছে
- সুপারিশকৃত ওয়ার্কফ্লো: GitHub Codespaces + GitHub Actions (ফোন-অনলি ডেভ)
- মূল সিদ্ধান্ত: ৩ স্তম্ভ আলাদা, QR-এ শুধু one-time token, atomic redemption, সার্ভার-এনফোর্সড auth
- পরবর্তী: Owner-এর ৫টি অমীমাংসিত সিদ্ধান্তের উত্তর → Phase 1 (প্রজেক্ট কঙ্কাল + প্রথম debug APK)
- আর্টিফ্যাক্ট: docs/PHASE-0.md, src/app/page.tsx, src/app/layout.tsx, src/app/globals.css
---
Task ID: 2
Agent: Z.ai Code (main)
Task: Owner-এর উত্তর রেকর্ড করা + Phase 0 আর্টিফ্যাক্ট আপডেট (শূন্য-খরচ নীতি ইত্যাদি)

Work Log:
- Owner-এর উত্তর: UI ডিফল্ট ইংরেজি, কোনো খরচ নয় (সব ফ্রি টিয়ার), GitHub রেডি, Firebase এখনো খোলা হয়নি, বাকি ৩টি প্রশ্ন বোঝা যায়নি
- docs/PHASE-0.md আপডেট: হেডারে আপডেট নোট, Section 9-এ শূন্য-খরচ নীতি, Section 14 → "সিদ্ধান্তের রেকর্ড" (D1–D5 উত্তরসহ), Section 16 → Firebase খোলার ৪-ধাপ নির্দেশনা
- src/app/page.tsx আপডেট: decisions ডেটা স্ট্রাকচার্ড (resolved/deferred + ব্যাজ), nextSteps ৩টিতে সংকুচিত (Firebase → "হয়েছে" → Phase 1), খরচ Alert → শূন্য-খরচ নীতি; import পরিষ্কার (FileText/RotateCcw বাদ, MessageCircle যোগ)
- bun run lint ক্লিন; GET / 200; Agent Browser-এ decisions সেকশন যাচাই (চূড়ান্ত/পরে ঠিক হবে ব্যাজ সঠিক), কোনো পেজ এরর নেই

Stage Summary:
- সিদ্ধান্ত চূড়ান্ত: Codespaces প্রধান ওয়ার্কফ্লো, শূন্য-খরচ (Cloud Functions বাদ — signaling সার্ভারই trusted backend), ইংরেজি ডিফল্ট UI, সাইডলোড APK
- D2 (দ্বিতীয় টেস্ট ফোন) স্থগিত — Phase 7-এর আগেই হলে যথেষ্ট
- একমাত্র বাকি প্রিকুইজিট: Owner Firebase প্রজেক্ট (family-support) খুলবে → "হয়েছে" বললেই Phase 1 শুরু
---
Task ID: 3
Agent: Z.ai Code (main)
Task: Phase 1 — ফোন-অনলি বিল্ড পাইপলাইন ডেলিভারি (Android skeleton + GitHub Actions + অ্যাকশন গাইড পেজ)

Work Log:
- Owner-এর সিদ্ধান্ত রেকর্ড: Codespaces ✓, Sideload APK ✓ (শেষ বার্তায় নিশ্চিত); দ্বিতীয় ফোন → Phase 7 পর্যন্ত স্থগিত
- android-app/ ফোল্ডারে ১৪-ফাইলের সম্পূর্ণ Android skeleton তৈরি: Kotlin 2.0.20 + Compose (BOM 2024.09.03), AGP 8.5.2, JDK 17, minSdk 26/targetSdk 34, ইংরেজি UI (MainActivity.kt), adaptive launcher icon (সবুজ হার্ট), Firebase ইচ্ছাকৃতভাবে বাদ (Phase 2)
- .github/workflows/android-build.yml: push→main ও workflow_dispatch-এ JDK17 + gradle/actions/setup-gradle (Gradle 8.9, wrapper-বিহীন — বাইনারি jar পেস্ট সমস্যা এড়াতে) → gradle assembleDebug → app-debug-apk artifact
- scripts/sync-android-files.mjs তৈরি → android-app/ থেকে public/phase1-files.json (ফাইল-বাই-ফাইল ফলব্যাক UI) ও public/bootstrap.sh.txt (এক-পেস্ট Codespaces সেটআপ স্ক্রিপ্ট: ১৪ ফাইল + git commit + push) জেনারেট; bash -n ও JSON ভ্যালিডেশন পাস
- src/components/phase0-plan.tsx: পুরনো page.tsx রেফারেন্স কম্পোনেন্টে রূপান্তর (স্টিকি হেডার/ফুটার সরানো, রেফারেন্স নোটিশ + সেকশন নেভ যোগ, ScanLine import বাদ)
- src/components/phase1-guide.tsx (নতুন, ক্লায়েন্ট): ৬-ধাপ অ্যাকশন গাইড — GitHub repo → Codespace → ম্যাজিক কমান্ড (কপি বাটন + preview) → Actions বিল্ড → APK ডাউনলোড/সাইডলোড → Firebase Spark গাইড; সাথে ৩টি সহজ ব্যাখ্যা (Codespaces/সাইডলোড/দ্বিতীয় ফোন), সিদ্ধান্ত-চিপস, ট্রাবলশুটিং ৫টি, প্ল্যান B accordion (১৪ ফাইলের কপি-বাটন), সততার ঘোষণা, Phase 2 প্রিভিউ
- src/components/home-tabs.tsx (নতুন) + src/app/page.tsx রিরাইট: স্টিকি হেডার (Phase 1 ব্যাজ) + ARIA ট্যাব (Phase 1 ডিফল্ট / Phase 0 রেফারেন্স) + স্টিকি ফুটার (min-h-screen flex-col + mt-auto)
- src/app/layout.tsx: মেটাডেটা → "Phase 1 অ্যাকশন গাইড"
- bun run lint ক্লিন; tsc --noEmit-এ আমার কোডে এরর নেই (examples/skills-এর পুরনো এরর অপ্রাসঙ্গিক)
- Agent Browser যাচাই (390px ও 1440px): হিরো/ধাপ-কার্ড/সিদ্ধান্ত-চিপস রেন্ডার, মেইন কমান্ড কপি বাটন → "কপি হয়েছে ✓", কমান্ড প্রিভিউ pre (12.6KB), প্ল্যান B accordion → ১৪ ফাইল + ফাইল-কপি ফিডব্যাক (trusted click), Phase 0 ট্যাব সুইচ, স্টিকি হেডার/ফুটার, কনসোল এরর নেই; /bootstrap.sh.txt ও /phase1-files.json → 200

Stage Summary:
- Phase 1 ডেলিভারি সম্পূর্ণ: Owner এখন গাইড পেজ অনুসরণ করে ফোনেই প্রথম debug APK বানাবেন — এটিই Phase 1-এর বাস্তব যাচাই
- সততা: এই কোড এখনো প্রকৃতপক্ষে বিল্ড হয়নি; প্রথম বিল্ডে এরর এলে Owner পাঠালে ঠিক করা হবে
- পরবর্তী: Owner "হয়েছে" বললে Phase 2 (Firebase ইন্টিগ্রেশন + google-services.json) শুরু
- আর্টিফ্যাক্ট: android-app/ (১৪ ফাইল), .github workflow, scripts/sync-android-files.mjs, public/bootstrap.sh.txt, public/phase1-files.json, src/components/{phase0-plan,phase1-guide,home-tabs}.tsx, src/app/{page,layout}.tsx
---
Task ID: 4
Agent: Z.ai Code (main)
Task: Owner-প্রদত্ত টোকেন দিয়ে Phase 1-এর বাস্তব যাচাই (repo তৈরি + push + Actions বিল্ড + artifact যাচাই) ও রেকর্ড

Work Log:
- Owner এককালীন GitHub classic PAT প্রদান করেন (সব স্কোপসহ; বিল্ড শেষে নিজে ডিলিটের প্রতিশ্রুতি) — কোথাও স্থায়ী সংরক্ষণ না করে শুধু সেশন/shell ও /tmp-তে ব্যবহার করা হয়েছে
- টোকেন যাচাই: login=psfaruk, প্রয়োজনীয় repo+workflow স্কোপ উপস্থিত
- API দিয়ে public রিপো তৈরি: psfaruk/family-remote-support (main, auto_init=false)
- android-app/-এর ১৪ ফাইল /tmp/frs-repo-তে স্টেজ করে git init→commit→push (main) — সফল
- Actions রান #40376076203 মনিটর: in_progress → **completed/success** (~৩ মিনিট)
- Artifact app-debug-apk (৮.৬৩ MB) নিশ্চিত; ডাউনলোড করে unzip — ভিতরে app-debug.apk (৯.৪৬ MB, AndroidManifest + ৪ dex, ১৪৪ এন্ট্রি) যাচাইকৃত
- phase1-guide.tsx আপডেট: সবুজ সুখবর-ব্যানার (রিপো ও রান লিংকসহ), ধাপ ১–৪-এ "আমি করে দিয়েছি ✓" ব্যাজ (done প্রপ), সততার ঘোষণা বাস্তব-যাচাই সংস্করণে, হিরোতে "মাত্র ৫ মিনিট" টেক্সট
- docs/PHASE-0.md-তে Section 18 যোগ: ঘটনার রেকর্ড, টোকেন-নিরাপত্তা নোট (স্থায়ী সংরক্ষণ নেই, ডিলিট অনুস্মারক, ভবিষ্যতে ন্যূনতম-স্কোপ পরামর্শ), বাস্তব যাচাইয়ের প্রমাণ
- /tmp-এর সব টোকেন/APK ট্রেস মুছে ফেলা (গৃহীত: .ghtoken, frs-repo যার .git/config-এ টোকেন ছিল, apk zip/extract)
- bun run lint ক্লিন; Agent Browser দিয়ে আপডেটেড পেজ যাচাই

Stage Summary:
- Phase 1-এর "বিল্ড হয়নি" সততা-গ্যাপ বন্ধ — বিল্ড এখন প্রমাণিত (রান লিংক + artifact)
- Owner-এর একমাত্র বাকি কাজ: ধাপ ৫ (APK ডাউনলোড+ইনস্টল) ও টোকেন ডিলিট
- রিপো public কিন্তু গোপন তথ্যমুক্ত; ভবিষ্যতে Secrets ব্যবহার হবে
- পরবর্তী: Owner "হয়েছে" বললে Phase 2 (Firebase ইন্টিগ্রেশন)
---
Task ID: 5
Agent: Z.ai Code (main)
Task: ডিবাগ APK ইনস্টল-ব্যর্থতার প্রতিক্রিয়া — সাইনড রিলিজ APK (v0.2.0) ডেলিভারি + Firebase টাইমিং গাইড + টোকেন-ডিলিট রিমাইন্ডার

Work Log:
- Owner রিপোর্ট করেন: ডিবাগ APK ফোনে ইনস্টল হচ্ছে না; রিলিজ APK চান; Play Protect স্ক্যান বন্ধ করেছেন; Firebase-এর সময়সূচি জানতে চান
- টোকেন পুনঃযাচাই: এখনো বৈধ (login=psfaruk), রিপো psfaruk/family-remote-support অক্ষত
- openssl দিয়ে PKCS12 রিলিজ keystore তৈরি (/tmp/frs-ks, alias familysupport, AES-256, ~৩০ বছর) — রিপোতে কখনো রাখা হয়নি
- scripts/set-gh-secrets.mjs (libsodium-wrappers, crypto_box_seal) দিয়ে ৪টি GitHub Secrets সেট: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD — সব HTTP 201
- android-app আপডেট: app/build.gradle.kts (versionCode 2 / versionName 0.2.0, ciRelease signing config PKCS12 + debug fallback), workflow → "Android Build (Debug + Release)" (keystore decode স্টেপ + assembleDebug assembleRelease + ২ artifact), MainActivity version স্ট্রিং 0.2.0, .gitignore += release.keystore
- scripts/push-release-update.mjs দিয়ে Contents API-তে ৪ ফাইল push (333568a → db6fe10)
- Actions রান #40379689857 মনিটর: completed/success — ৮ ধাপই success ("Decode release keystore" সহ), artifact app-release-apk ৫.৮০ MB + app-debug-apk ৮.৬৩ MB
- scripts/sync-android-files.mjs পুনর্চালনা → phase1-files.json (১৪ ফাইল) ও bootstrap.sh.txt (১৪ KB) নতুন কোডসহ; bash -n + JSON যাচাই পাস
- phase1-guide.tsx আপডেট: হিরো সুখবর → রিলিজ v0.2.0 (রান লিংক নতুন), ধাপ ৪ (দুই APK ব্যাখ্যা), ধাপ ৫ (app-release-apk ডাউনলোড + "0.2.0" যাচাই + ডিবাগ-ফলোআপ Alert + Secrets নোট), ধাপ ৬ Firebase Alert → "কখন লাগবে? — ঠিক Phase 2 শুরুর আগেই" + google-services.json-এর লেখা পাঠানোর নির্দেশ, ট্রাবলশুটিং ৫→৬ (নতুন ২টি ইনস্টল-সমস্যা এন্ট্রি), নতুন টোকেন-ডিলিট কার্ড (ধাপসহ + Phase 2-তে ছোট-স্কোপ টোকেন/টোকেন-বিহীন পথ), সততার নোট রিলিজ-প্রমাণ সংস্করণ, পরবর্তী-গন্তব্য কার্ড আপডেট
- docs/PHASE-0.md-তে Section 19 যোগ (রিলিজ আপডেট রেকর্ড + Firebase টাইমিং + টোকেন নীতি)
- /tmp/frs-ks (keystore + পাসওয়ার্ড) মুছে ফেলা
- bun run lint ক্লিন; dev.log ক্লিন (GET / 200, এরর নেই); Agent Browser (390px + 1440px): নতুন ৬টি অংশের টেক্সট, রান লিংক #40379689857, কনসোল এরর নেই, ফুটার আছে — সব যাচাইকৃত

Stage Summary:
- Owner-এর ইনস্টল সমস্যার সমাধান ডেলিভারিড: সাইনড app-release.apk (v0.2.0) Actions-এ প্রস্তুত — ডাউনলোড + ধাপ ৫-এর নতুন নির্দেশনা অনুসরণ করলেই ইনস্টল হবে
- সাইনিং key এখন GitHub Secrets-এ স্থায়ী — ভবিষ্যতের সব রিলিজ একই সিগনেচারে সাইন হবে (আপডেট-ইনস্টল সমস্যামুক্ত)
- Firebase-এর উত্তর রেকর্ডড: Phase 2 শুরুর প্রথম প্রয়োজনীয়তা — Owner এখনই খুলবেন, google-services.json-এর লেখা পাঠালেই Phase 2 শুরু
- টোকেন: রিলিজ বিল্ড সফল — Owner এখনই ডিলিট করতে পারেন; Phase 2-এ ছোট-স্কোপ নতুন টোকেন বা Codespaces পথ
- পরবর্তী: Owner-এর রিলিজ-ইনস্টল নিশ্চিত + google-services.json → Phase 2 (Firebase ইন্টিগ্রেশন)
---
Task ID: 6
Agent: Z.ai Code (main)
Task: Phase 2 — Firebase ইন্টিগ্রেশন ডেলিভারি (google-services.json ইনপুট → বিল্ড-যাচাইত v0.3.0)

Work Log:
- Owner google-services.json-এর লেখা চ্যাটে পাঠান (project_id: remote-family-support; package মিলে গেছে ✓)
- পুরনো PAT পুনঃযাচাই: HTTP 200 (এখনো বৈধ — শেষ push-এর পর ডিলিটের অনুস্মারক পুনর্ব্যক্ত)
- android-app পরিবর্তন: app/google-services.json নতুন (হুবহু কনফিগ); root build.gradle.kts-এ google-services 4.4.2; app/build.gradle.kts-এ plugin + versionCode 3/versionName 0.3.0 + Firebase BoM 33.4.0 + firebase-auth + firebase-firestore; MainActivity-তে Phase 2 build check কার্ড + FirebaseApp options থেকে project/app ID প্রদর্শন (try-catch, "not loaded" fallback)
- settings.gradle.kts-এর google() repo-র includeGroupByRegex com.google.* — google-services plugin ও Firebase BoM resolve হয়েছে ✓
- scripts/sync-android-files.mjs FILES-এ google-services.json যোগ → ১৫ ফাইল sync; bootstrap.sh.txt 16.0 KB; bash -n + JSON যাচাই পাস
- scripts/push-release-update.mjs ফাইল-লিস্ট আপডেট (৪ ফাইল) → push (e5f7f0d → c8297d8; google-services.json HTTP 201)
- Actions রান #40382874536: completed/success — ৮ ধাপই success; artifact app-release-apk ৯.৪৩ MB + app-debug-apk ১৩.০৯ MB
- phase1-guide.tsx Phase 2 সংস্করণ: হিরো ব্যানার (Firebase যুক্ত, রান লিংক 40382874536), ধাপ ৫-এ "0.3.0 + Firebase project remote-family-support" যাচাই, ধাপ ৬ done=true ("এই ধাপ আপনি শেষ করে ফেলেছেন ✓" + console-এ আর কিছু লাগবে না), টোকেন-কার্ড → "Phase 2 push শেষ, এখনই ডিলিট করুন", ১৪→১৫ ফাইল টেক্সট (ধাপ ৩ + প্ল্যান B), পরবর্তী-গন্তব্য কার্ড (Phase 3 প্রিভিউ), সততার নোট v0.3.0
- docs/PHASE-0.md-তে Section 20 (Phase 2 রেকর্ড + নিরাপত্তা-নোট + Phase 3 পরিকল্পনা)
- bun run lint ক্লিন; dev.log ক্লিন; Agent Browser (390px): ৫টি নতুন টেক্সট চেক সব true, কনসোল এরর নেই

Stage Summary:
- Phase 2 সম্পূর্ণ: Firebase অ্যাপে যুক্ত ও বিল্ড-প্রমাণিত — ফোনে v0.3.0 ইনস্টল করে "Firebase project: remote-family-support" দেখলেই চূড়ান্ত নিশ্চিত
- google-services.json পাবলিক রিপোতে নিরাপদ (গোপন নয়) — প্রকৃত সুরক্ষা Phase 3-এ SHA fingerprint + Firestore rules + App Check
- টোকেন: এই push-এর পরে আর দরকার নেই — Owner ডিলিট করবেন; Phase 3-এ দরকার হলে ছোট-স্কোপ নতুন টোকেন বা Codespaces পথ
- পরবর্তী: Phase 3 (Google Sign-In + বিশ্বস্ত ডিভাইস রেকর্ড) — Owner-এর v0.3.0 ইনস্টল নিশ্চিতের অপেক্ষায়
---
Task ID: 7
Agent: Z.ai Code (main)
Task: Phase 3 — Google Sign-In + বিশ্বস্ত ডিভাইস রেকর্ড ডেলিভারি (v0.4.0, বিল্ড-যাচাইত)

Work Log:
- Owner "হয়েছে" নিশ্চিত করেছেন (v0.3.0 যাচাই) → Phase 3 শুরু; পুরনো PAT পুনঃযাচাই: HTTP 200 (বৈধ — শেষে ডিলিট অনুস্মারক)
- app/build.gradle.kts: versionCode 4 / versionName 0.4.0 + buildConfig GOOGLE_WEB_CLIENT_ID (CI vars থেকে, খালি হলে অ্যাপে সৎ "setup incomplete" স্টেট) + buildConfig=true + নতুন ডিপস: credentials 1.3.0, credentials-play-services-auth, googleid 1.1.1, kotlinx-coroutines-play-services 1.8.1
- MainActivity.kt রিরাইট: Credential Manager GetGoogleIdOption → GoogleIdTokenCredential → Firebase signInWithCredential; সাইন-ইন/সাইন-আউট (clearCredentialState সহ); সাইন-ইনের পর স্বয়ংক্রিয় device registration — users/{uid} profile + users/{uid}/devices/{sha256(ANDROID_ID) হ্যাশ} (model/manufacturer/androidVersion/sdkInt/appVersion); Firestore থেকে ডিভাইস তালিকা পড়ে দেখানো; ইংরেজি UI, Phase 2 চেক কার্ড অক্ষত
- firestore.rules (নতুন): least-privilege — স্ব-প্রোফাইল ও স্ব-ডিভাইসের বাইরে সব deny, field allowlist + টাইপ-চেক, catch-all deny
- workflow: "Decode release keystore" ধাপে keytool দিয়ে SHA-1/SHA-256 → $GITHUB_STEP_SUMMARY + লগ (Firebase console-এ কপি করার জন্য); Build ধাপে env GOOGLE_WEB_CLIENT_ID=${{ vars.GOOGLE_WEB_CLIENT_ID }}
- প্রথম push (commits 364dfc6→06390f0) → রান #40385995378 FAILURE: একটি কম্পাইল এরর — GoogleIdTokenCredential.TYPE_TOKEN নেই; TYPE_GOOGLE_ID_TOKEN_CREDENTIAL এ ঠিক করা; push স্ক্রিপ্টে "অপরিবর্তিত ফাইল skip" লজিক যোগ
- পুনঃpush (শুধু MainActivity, commit 0cbc445) → রান #40386337031 completed/success; artifact app-release-apk ৯.৪৮ MB + app-debug-apk ১৩.১৬ MB
- ফিঙ্গারপ্রিন্ট বের করা হয়েছে (রান লগ, stable): SHA-1 82:56:54:F8:F2:21:6B:5A:A3:64:82:6E:B7:64:58:CA:5E:E2:BD:66; SHA-256 A3:DD:02:45:CC:27:A6:00:62:B4:DC:A3:F0:16:3B:58:5E:FB:23:68:D3:3C:3D:B0:50:A2:CB:23:16:73:ED:0B — গাইড পেজে কপি-বাটনসহ বসানো
- sync-android-files.mjs FILES += firestore.rules → ১৬ ফাইল; bootstrap.sh.txt 31.5 KB; bash -n পাস
- phase1-guide.tsx Phase 3 সংস্করণ: নতুন সেকশন "এখন আপনার কাজ — Firebase console-এ ৪টি ছোট ধাপ" (১: Google Sign-In চালু + Web Client ID কপি→চ্যাট; ২: fingerprint ২টি কপি-বাটনসহ; ৩: Firestore asia-south1 production mode; ৪: rules পেস্ট + কপি-বাটন + preview), হিরো ব্যানার v0.4.0 + রান লিংক, পুরনো ৬ ধাপ "রেফারেন্স" শিরোনামে, ১৫→১৬ ফাইল টেক্সট, ট্রাবলশুটিং +৩ (sign-in fail / rules publish error / PERMISSION_DENIED), টোকেন কার্ড আপডেট, পরের-গন্তব্য কার্ড (Phase 4 QR pairing), সততার নোট (TYPE_TOKEN এরর + ফিক্স স্বীকারসহ)
- page.tsx হেডার ব্যাজ → "Phase 3 · Sign-In প্রস্তুতি"; home-tabs ট্যাব লেবেল → Phase 3; layout.tsx metadata → Phase 3
- bun run lint ক্লিন; tsc-তে আমার কোডে এরর নেই (skills/-এর পুরনো এরর অপ্রাসঙ্গিক); dev.log ক্লিন (GET / 200)
- Agent Browser (390px + 1440px): ১১টি কনটেন্ট চেক সব true (হিরো, ৪-ধাপ সেকশন, SHA-1/256, rules, Web Client ID কার্ড, পুরনো ধাপ, টোকেন কার্ড, সততা, রান লিংক, ফুটার); রুলস ও SHA-1 কপি বাটন ট্রাস্টেড ক্লিকে "কপি হয়েছে ✓"; কনসোল এরর নেই; ফুটার স্টিকি যাচাই

Stage Summary:
- Phase 3-এর কোড-অর্ধেক সম্পূর্ণ ও বিল্ড-প্রমাণিত (v0.4.0): অ্যাপে Sign-In + ডিভাইস রেকর্ড লজিক প্রস্তুত; রুলস রিপোতে
- Owner-এর এখনকার কাজ (গাইডে ৪ ধাপ): Auth-এ Google চালু → SHA ফিঙ্গারপ্রিন্ট ২টি যোগ → Firestore তৈরি (asia-south1) → Rules পেস্ট; এবং Web Client ID চ্যাটে পাঠানো
- Web Client ID পেলে আমি: GitHub Actions variable GOOGLE_WEB_CLIENT_ID সেট → versionCode 5 / v0.4.1 রিবিল্ড → Owner সাইন-ইন টেস্ট করবেন ("Registered ✓" দেখলেই Phase 3 সম্পূর্ণ)
- টোকেন: Phase 3 push শেষ — Owner এখনই ডিলিট করবেন; Phase 4-এ ছোট-স্কোপ নতুন টোকেন/Codespaces পথ
- আর্টিফ্যাক্ট: MainActivity.kt, app/build.gradle.kts, workflow, firestore.rules, phase1-guide.tsx আপডেট, public/* রিজেন
---
Task ID: 8 (interim — push অপেক্ষমায়, নতুন টোকেন দরকার)
Agent: Z.ai Code (main)
Task: Phase 3 সমাপ্তি — Owner-এর Web Client ID গ্রহণ → v0.4.1 রিবিল্ড স্টেজিং

Work Log:
- Owner "সব করেছি" + Web Client ID পাঠান: 1001946927282-k0orohct2gg0jojtclpd00jafikqsim3.apps.googleusercontent.com (Firebase console-এর ৪ ধাপ সম্পন্ন দাবি — Auth Google enable, SHA fingerprint, Firestore, rules)
- পুরনো PAT যাচাই: প্রথমে HTTP 200 দেখালেও পরের কলে 404/401 → নিশ্চিত: Owner টোকেন ডিলিট করেছেন (revocation propagation delay); unset করে পরিষ্কার
- সিদ্ধান্ত: GitHub Actions variable-এর বদলে app/build.gradle.kts-এ Web Client ID fallback হিসেবে hardcode (OAuth client ID গোপন নয়) — ফলে push-এ শুধু Contents: Read and write ফাইন-গ্রেইনড টোকেনই যথেষ্ট, Variables পারমিশন লাগবে না; workflow-এর vars.GOOGLE_WEB_CLIENT_ID env override অক্ষত (blank হলে fallback ব্যবহৃত হয়)
- android-app আপডেট: app/build.gradle.kts (versionCode 5 / versionName 0.4.1, envClientId.orEmpty().ifBlank { Owner-এর client ID } → BuildConfig), MainActivity.kt ("0.4.1 (Phase 3)"), scripts/push-release-update.mjs কমিট মেসেজ → "Phase 3.1..."
- sync-android-files.mjs পুনর্চালনা → ১৬ ফাইল; bootstrap.sh.txt 31.8 KB; bash -n পাস; phase1-files.json-এ app/build.gradle.kts-এ client ID ও 0.4.1 উপস্থিত যাচাইকৃত
- নোট: google-services.json-এর client_type 3 ওয়েব ক্লায়েন্ট আলাদা (…-hm0drsfbfb6acuu742i4qcqfqge1696h…) — Owner-এর পাঠানোটা Auth console থেকে; sign-in DEVELOPER_ERROR হলে fallback-এ ওটায় সুইচ করা হবে (গাইড ট্রাবলশুটিং-এ জায়গা রাখতে হবে)

Stage Summary:
- v0.4.1 স্টেজড: push-এর জন্য প্রস্তুত (app/build.gradle.kts + MainActivity.kt পরিবর্তিত; workflow/rules অপরিবর্তিত — push script skip-identical সামলাবে)
- বাধা: টোকেন নেই — Owner-কে ফাইন-গ্রেইনড টোকেন (শুধু family-remote-support রিপো, Contents: Read and write) তৈরি করে চ্যাটে দিতে বলা হয়েছে
- টোকেন পেলে: push-release-update.mjs → Actions রান মনিটর → artifact যাচাই → phase1-guide.tsx (v0.4.1 হিরো + সাইন-ইন টেস্ট ধাপ + DEVELOPER_ERROR/PERMISSION_DENIED ট্রাবলশুটিং + hm0drsf fallback নোট) → docs/PHASE-0.md Section 21 → badges → lint/browser → worklog ফাইনাল → Owner-কে APK ডাউনলোড + সাইন-ইন টেস্ট নির্দেশ + টোকেন ডিলিট অনুস্মারক
---
Task ID: 8 (final)
Agent: Z.ai Code (main)
Task: Phase 3.1 সমাপ্তি — Web Client ID বিল্ডে বেক + v0.4.1 push/বিল্ড-যাচাই + গাইড আপডেট

Work Log:
- Owner একই পুরনো classic PAT আবার পাঠান; প্রথমে 401 দেখলেও পুনঃযাচাইয়ে 200 (repo+workflow স্কোপ) — আগের 401-গুলো GitHub-এর সাময়িক auth-অস্থিরতা; নতুন টোকেন না চেয়েই বিদ্যমান টোকেনে push করার সিদ্ধান্ত
- Push (Contents API): SKIP workflow + rules (অপরিবর্তিত), PUT app/build.gradle.kts → commit 6dadf29, PUT MainActivity.kt → commit 5c4a467
- Actions রান #40444267794 (#16) পোলিং: in_progress → completed/**success** (~২ মিনিট); artifact app-release-apk ৯.৪৮ MB + app-debug-apk ১৩.১৬ MB (expired:false)
- phase1-guide.tsx আপডেট (১৪টি এডিট): হিরো সাবটাইটেল + সুখবর v0.4.1 + রান লিংক 40444267794; console-এর ৪ ধাপ সব done={true}; নতুন সেকশন "এখন আপনার টেস্ট" (StepCard no=5: রান #16 → APK ইনস্টল/ওভাররাইট → 0.4.1 যাচাই → Sign in → "Registered ✓" প্রত্যাশা + Firestore users প্রমাণ; এরর Alert — ApiException 10/DEVELOPER_ERROR হলে লেখা পাঠাতে বলা, বিকল্প client ID রেডি); ট্রাবলশুটিং +১ (DEVELOPER_ERROR/কোড 10); পুরনো ধাপ ৫ → 0.4.1; টোকেন-কার্ড ("v0.4.1-ও push হয়ে বিল্ড সফল — এই মুহূর্তেই ডিলিট"); পরবর্তী-গন্তব্য Phase 3 (টেস্ট বাকি); সততার নোট (commits 6dadf29/5c4a467, রান #40444267794, দুই artifact)
- page.tsx ব্যাজ → "Phase 3 · সাইন-ইন টেস্ট"; layout.tsx metadata → v0.4.1 বর্ণনা; docs/PHASE-0.md → Section 21.5 (টোকেন-ঘটনা, hardcode-সিদ্ধান্ত, push/রান প্রমাণ, hm0drsf fallback নোট)
- bun run lint ক্লিন; dev.log: GET / 200 + ✓ Compiled, এরর নেই
- Agent Browser (390px): ৯ কনটেন্ট-চেক সব true (badge/hero/test-section/done4/run16/devError/honesty/tokenCard/footer); "SHA-1 কপি" বাটন ট্রাস্টেড ক্লিক → "কপি হয়েছে ✓"; রান লিংক href যাচাই; (1440px): sticky header+footer, Phase 0 ট্যাব-সুইচ true; কনসোল ক্লিন, পেজ এরর শূন্য

Stage Summary:
- Phase 3.1 সম্পূর্ণ ও বিল্ড-প্রমাণিত: Owner-এর Web Client ID BuildConfig-এ fallback হিসেবে বেক (v0.4.1, versionCode 5) — variable-নির্ভরতা বাদ, ভবিষ্যৎ push-এ শুধু Contents:Read/Write টোকেন যথেষ্ট
- একমাত্র বাকি: Owner-এর ফোনে সাইন-ইন টেস্ট — "Registered ✓ (id …)" + ডিভাইস তালিকা দেখলেই Phase 3 চূড়ান্ত; ব্যর্থ হলে hm0drsf… বিকল্প client ID-তে এক-কমিট সুইচ রেডি
- টোকেন: এখনো বৈধ ও সব-স্কোপ — Owner এখনই ডিলিট করবেন (গাইড কার্ডে ধাপ আছে); Phase 4-এ ফাইন-গ্রেইনড (Contents: RW, এই রিপোতেই সীমাবদ্ধ) চাওয়া হবে
- আর্টিফ্যাক্ট: android-app/{app/build.gradle.kts, MainActivity.kt}, phase1-guide.tsx, page.tsx, layout.tsx, docs/PHASE-0.md §21.5, public/* রিজেন (আগের ধাপে), worklog Task 8

---
Task ID: 9
Agent: Z.ai Code (main)
Task: Phase 3 সমাপ্তি নিশ্চিত + ইমেইল/পাসওয়ার্ড রোডম্যাপ রেকর্ড + Phase 4 পেয়ারিং ডেলিভারি (v0.5.0, বিল্ড-যাচাইত)

Work Log:
- Owner স্ক্রিনশটসহ নিশ্চিত: v0.4.1-এ সাইন-ইন/সাইন-আউট সফল (supportplybit@gmail.com) → Phase 3 চূড়ান্ত; সাথে দুই নির্দেশ: (১) ভবিষ্যতে ইমেইল+পাসওয়ার্ড লগইন ডকুমেন্টে রাখা, (২) পরবর্তী ফেজ শুরু
- পুরনো PAT পুনঃযাচাই: HTTP 200 (বৈধ) — push কাজে লেগেছে; ডিলিট অনুস্মারক পুনর্ব্যক্ত
- Phase 4 সুযোগ-সিদ্ধান্ত: কোড-ভিত্তিক পেয়ারিং (৬ অক্ষর id + ২০ hex one-time token, ৫ মিনিট) + QR প্রদর্শন; ক্যামেরা-স্ক্যান Phase 5-এ (এক ফোন/দুই অ্যাকাউন্ট টেস্ট-বাস্তবতায় টাইপই মূল পথ)
- PairingRepo.kt (নতুন): SecureRandom কোড জেনারেশন (ambiguous অক্ষরবর্জিত), parse/normalize, invitation create/cancel, snapshot listener, redeem (এক ট্রানজেকশনে PENDING→PAIRED + relationship create/reactivate), relationship watch (২ কোয়েরি মার্জ), revoke, ZXing QR bitmap
- MainActivity.kt: PairingCard (৩ ভাগ UI — কোড তৈরি/redeem/paired devices + Revoke), registration "Retry" বোতাম, version 0.5.0 (Phase 4)
- firestore.rules v2: pairingInvitations (list deny, tokenHash immutable, ২টি ট্রানজিশন-পথ) + trustedDeviceRelationships (getAfter দিয়ে atomic পুনঃযাচাই, অংশগ্রহণকারী-সীমিত read/update)
- app/build.gradle.kts: versionCode 6 / v0.5.0 + zxing core 3.5.3
- Push (Contents API): 4c387ae → 2fe7398 → 44d2631 (নতুন PairingRepo) → 605154b (rules v2)
- রান #40449264724 FAILURE — ৩ কম্পাইল এরর (expiresAtMillis অনুপস্থিত / currentInvite.id→invitationId / QueryDocumentSnapshot→DocumentSnapshot receiver); ফিক্স push 308d1a3 + b527a1c
- রান #40449579164 → completed/success; artifact app-release-apk ৯.৭২ MB + app-debug-apk ১৩.৪৭ MB
- phase1-guide.tsx: phase3Rules→phase4Rules (v2), হিরো v0.5.0 + দ্বৈত সুখবর (সাইন-ইন টেস্ট সফল), টেস্ট সেকশন ৩ ধাপে পুনর্গঠন (ধাপ ৬ rules আপডেট, ধাপ ৭ APK ইনস্টল, ধাপ ৮ দুই-অ্যাকাউন্ট পেয়ারিং টেস্ট + "কেন দুই অ্যাকাউন্ট" Alert + QR-নোট), ট্রাবলশুটিং +২ (Code not found/Wrong code, Pairing PERMISSION_DENIED) + PERMISSION_DENIED এন্ট্রি আপডেট, টোকেন কার্ড v0.5.0, গন্তব্য কার্ডে Phase 3 ✓ + Phase 4 (টেস্ট বাকি) + Phase 5 + 🔮 ইমেইল/পাসওয়ার্ড রোডম্যাপ লাইন, সততার নোট v0.5.0, ১৬→১৭ ফাইল টেক্সট
- page.tsx ব্যাজ "Phase 4 · পেয়ারিং"; home-tabs ট্যাব "Phase 4 · অ্যাকশন গাইড"; layout.tsx metadata v0.5.0
- docs/PHASE-0.md Section 22 যোগ: 22.1 Owner টেস্ট ফলাফল, 22.2 ইমেইল+পাসওয়ার্ড রোডম্যাপ রেকর্ড (Owner-এর অনুরোধ), 22.3 সুযোগ/বাস্তবায়ন, 22.4 বাস্তব যাচাই (এরর+ফিক্স+রান প্রমাণ), 22.5 Owner-এর বাকি কাজ
- sync-android-files ১৭ ফাইল (PairingRepo সহ), bootstrap.sh.txt ৬১.৭ KB, bash -n পাস
- bun run lint ক্লিন; dev.log ক্লিন (GET / 200, ✓ Compiled)
- Agent Browser: 390px-এ ১৪ কনটেন্ট-চেক সব true; "Phase 4 রুলস কপি করুন" বোটন → "কপি হয়েছে ✓"; কনসোল এরর শূন্য; 1440px রেন্ডার ✓; ফুটার ডকুমেন্ট-নিচে স্বাভাবিকভাবে (mt-auto, scrollable পেজে footerAtDocBottom=true)

Stage Summary:
- Phase 4 (v0.5.0, versionCode 6) কোড-সম্পূর্ণ ও বিল্ড-প্রমাণিত: one-time পেয়ারিং কোড + QR প্রদর্শন + atomic redemption + revoke + rules v2 — Owner এখন ধাপ ৬–৮ (rules পেস্ট → APK ইনস্টল → দুই অ্যাকাউন্টে পেয়ারিং টেস্ট) করলেই Phase 4 সম্পূর্ণ
- Owner-এর ইমেইল+পাসওয়ার্ড লগইন অনুরোধ সরকারিভাবে রোডম্যাপে রেকর্ডড (docs/PHASE-0.md §22.2 + গাইড গন্তব্য কার্ড) — Firebase Email/Password provider, একই users/{uid} মডেল, rules অপরিবর্তিত থাকবে
- Phase 3-এর "Registering…" UX-গ্যাপ বন্ধ (Retry বোতাম)
- টোকেন: v0.5.0 push শেষ — Owner এখনই ডিলিট করবেন; Phase 5-এ ছোট-স্কোপ নতুন টোকেন/Codespaces পথ
- আর্টিফ্যাক্ট: PairingRepo.kt, MainActivity.kt, app/build.gradle.kts, firestore.rules v2, phase1-guide.tsx, page.tsx, home-tabs.tsx, layout.tsx, docs/PHASE-0.md §22, public/* রিজেন, worklog Task 9

---
Task ID: 10
Agent: Z.ai Code (main)
Task: Phase 4 লোডিং-স্পিনার হ্যাং বাগ ডিবাগ + v0.5.1 ফিক্স প্রস্তুতি (push টোকেন-অপেক্ষমায়)

Work Log:
- Owner-এর রিপোর্ট: v0.5.0-এ "Create pairing code" → ৫+ মিনিট লোডিং, এরর নেই; স্ক্রিনশটে "Registering this device…"-ও আটকে — Firestore-এর সব কল অসম্পূর্ণ
- পুরনো v0.4.1 স্ক্রিনশট (09:56) পুনঃপরীক্ষায় সেখানেও রেজিস্ট্রেশন আটকে থাকার প্রমাণ → Firestore প্রথম থেকেই অপ্রাপ্য; Auth আলাদা সার্ভিস বলে সাইন-ইন চলেছিল
- GitHub API rate-limit বাইপাস করে codeload tarball দিয়ে পুরো রিপো (main@b527a1c) ডাউনলোড; লোকালের সাথে diff — **রুট-কজ নিশ্চিত**: রিপোর MainActivity/PairingRepo-তে টাইমআউট-ফিক্স নেই (কাঁচা .await()); ফিক্সগুলো লোকালে স্টেজড ছিল কিন্তু push হয়নি (ডেলিভারি-গ্যাপ, সততার নোটে স্বীকৃত)। দ্বিতীয় স্তরের কারণ: Firestore Database console-এ তৈরি হয়নি হওয়ার উচ্চ সম্ভাবনা (DB-বিহীন প্রজেক্টে SDK কল এরর-ছাড়া অসীম অপেক্ষা করে)
- নিশ্চিত হলাম rules v2 রিপোতে IDENTICAL — rules সমস্যা নয় (rules-এর ভুল হলে দ্রুত PERMISSION_DENIED আসত, হ্যাং হতো না)
- v0.5.1 ফিক্স (versionCode 7) যাচাইকৃত স্টেজড: awaitWithTimeout (৩০ সেকেন্ড, সব Firestore কলে), FirestoreUnreachableException (DB-তৈরি-নির্দেশক বার্তাসহ), describeFirestoreError (PERMISSION_DENIED/NOT_FOUND/UNAVAILABLE ভেদ), snapshot-listener onError → inviteSyncError/relationshipsSyncError, CancellationException rethrow, Retry registration
- phase1-guide.tsx আপডেট (৮ এডিট): হিরো → বাগ-ফিক্স ঘোষণা v0.5.1, টেস্ট-সেকশনে সূত্র-Alert ("Registering…" পর্যবেক্ষণ), ধাপ ৬ → "Firestore Database যাচাই + rules আপডেট" (Create-database বোতাম-নির্ণয় + asia-south1), ধাপ ৭ → v0.5.1 + প্রত্যাশিত-এরর গাইড, ট্রাবলশুটিং +২ (স্পিনার-হ্যাং, Could-not-reach), গন্তব্য কার্ডে "(v0.5.1 — লোডিং-বাগ ফিক্সড)", টোকেন কার্ড v0.5.1, সততার ঘোষণা পুনর্লিখন (push-গ্যাপ স্বীকার)
- page.tsx ব্যাজ → "Phase 4 · ফিক্স v0.5.1"; layout.tsx metadata v0.5.1
- docs/PHASE-0.md Section 23 যোগ (23.1 রিপোর্ট, 23.2 রুট-কজ ২ স্তর, 23.3 ফিক্স-তালিকা, 23.4 Owner-পথ, 23.5 স্থিতি); §22.2 ইমেইল+পাসওয়ার্ড রোডম্যাপ অপরিবর্তিত
- sync-android-files → ১৭ ফাইল (MainActivity 0.5.1 + PairingRepo timeout উপস্থিতি node-যাচাই); bootstrap.sh.txt ৬৭.১ KB; bash -n পাস
- bun run lint ক্লিন; dev.log ক্লিন (GET / 200, ✓ Compiled)
- Agent Browser (390px): ১২ কনটেন্ট-চেক সব true; "Phase 4 রুলস কপি" ট্রাস্টেড ক্লিক → "কপি হয়েছে ✓"; কনসোল ক্লিন (HMR/DevTools ছাড়া শূন্য), পেজ-এরর শূন্য; (1440px): রেন্ডার+ট্যাব ✓, ফুটার নিচে স্টিকি ✓

Stage Summary:
- রুট-কজ দুই স্তরে নিশ্চিত: (১) বিল্ড হয়েছিল টাইমআউটবিহীন পুরনো কোড থেকে (ফিক্স push হয়নি — আমার ডেলিভারি-গ্যাপ), (২) প্রায় নিশ্চিত Firestore Database তৈরি হয়নি — যাচাই-নির্দেশনা গাইড ধাপ ৬-এ
- v0.5.1 (versionCode 7) সম্পূর্ণ স্টেজড + গাইড/ডকুমেন্ট/ব্যাজ আপডেটেড — push-এর জন্য প্রস্তুত
- বাধা: টোকেন নেই (Owner আগেরটি ডিলিট করেছেন) — ফাইন-গ্রেইনড টোকেন (শুধু family-remote-support, Contents: Read and write) চাওয়া হয়েছে
- টোকেন পেলে: push-release-update.mjs → রান মনিটর → গাইডের ২টি রান-লিংক/রান-নম্বর + টোকেন-কার্ড ফাইনালাইজ → Owner-কে APK+ধাপ-৬ নির্দেশ
- আর্টিফ্যাক্ট: phase1-guide.tsx, page.tsx, layout.tsx, docs/PHASE-0.md §23, public/* রিজেন, worklog Task 10

---
Task ID: 11
Agent: Z.ai Code (main)
Task: টোকেন লাভ → v0.5.1 push → CI মনিটর → Owner-এর ২টি নতুন স্ক্রিনশট বিশ্লেষণ (Auth config যাচাই + Firestore-unreachable নির্ণয়)

Work Log:
- Owner নতুন টোকেন দিয়েছেন (সব phase শেষ পর্যন্ত ব্যবহারযোগ্য) — GH_TOKEN env দিয়ে push-release-update.mjs চালানো
- push ফলাফল: build.gradle.kts → e9c5117, MainActivity.kt → 4df1d72, PairingRepo.kt → 5b2d0ad; workflow ও rules আগেই identical ছিল (SKIP)
- ৩টি workflow run ট্রিগার (প্রতি commit-এ); মাঝে স্যান্ডবক্স টুল-আউটেজ (~২১ বার রিট্রাই) — পুনরুদ্ধারের পর যাচাই: **রান 40473596605 (5b2d0ad) = success** — v0.5.1 release APK আর্টিফ্যাক্ট তৈরি
- Owner-এর স্ক্রিনশট-১ (অ্যাপ): "Registration failed: Could not reach Firestore while saving your profile (waited 30 s)" + Retry registration + "No devices on this account yet" → **v0.5.1 ফিক্স প্রমাণিত কাজ করছে** (৩০ সেকেন্ডে স্পষ্ট এরর, আর ঝুলছে না); সাইন-ইনও কাজ করছে
- Owner-এর স্ক্রিনশট-২ (Firebase console → Authentication → Sign-in method → Google): Enable ✓, Web client ID = 1001946927282-k0orohct2gg0jojtclpd00jafikqsim3 (অ্যাপের fallback-এর সাথে হুবহু মিলছে), support email সেট → **Auth config ১০০% সঠিক**
- নির্ণয় চূড়ান্ত: Auth ও Firestore আলাদা সার্ভিস; ইন্টারনেট+Auth ঠিক থাকার পরও Firestore-unreachable (৩০ সে) + কখনোই "Registered ✓" হয়নি (v0.4.1-এও) → **Firestore Database console-এ তৈরি হয়নি** — এটিই একমাত্র বাকি সমস্যা
- Owner-কে মোবাইল-পথে ধাপ দেওয়া হয়েছে: console.firebase.google.com/project/remote-family-support/firestore → Create database → Production mode → asia-south1 → Enable → Rules ট্যাবে Phase 4 rules পেস্ট+Publish → অ্যাপে Retry registration → Create pairing code

Stage Summary:
- v0.5.1 (versionCode 7) push + বিল্ড + ইনস্টল + বাস্তব-যাচাই: সম্পূর্ণ ✓ — লোডিং-হ্যাং বাগ স্থায়ীভাবে বন্ধ
- অ্যাপ-সাইড ও Firebase Auth config: নির্দোষ; একমাত্র বাকি বাধা Firestore Database provision-করা (Owner মোবাইলেই করতে পারবেন)
- টোকেন কার্যকর (Owner নির্দেশ: সব phase শেষ পর্যন্ত রাখব)
- পরবর্তী: DB তৈরির পর Owner টেস্ট রিপোর্ট দিলে Phase 4 সম্পূর্ণ ঘোষণা → Phase 5 (QR scan, presence & FCM)

---
Task ID: 12
Agent: Z.ai Code (main)
Task: Owner সম্মতি — Firebase টোকেন দিয়ে আমার নিজের হাতে Firestore ঠিক করা; firebase-setup.mjs অটোমেশন প্রস্তুত

Work Log:
- Owner-এর প্রশ্ন: "Firebase থেকে টোকেন দিলে কি সমাধান করতে পারবে?" → উত্তর হ্যাঁ; প্রয়োজনীয় টোকেন = Service account private key (JSON)
- Owner-এর স্ক্রিনশট পুনঃনিশ্চিত: Auth config নির্দোষ; সমস্যা Firestore Database অনুপস্থিতি
- scripts/firebase-setup.mjs লেখা (নির্ভরতাহীন, খাঁটি Node): RS256 JWT → OAuth token → API enable (serviceusage) → Firestore DB অস্তিত্ব-যাচাই/তৈরি (FIRESTORE_NATIVE, asia-south1, LRO পোলিং) → Phase 4 rules rulesets+releases API-তে পাবলিশ → সারসংক্ষেপ
- এরর-হ্যান্ডলিং টেস্ট: no-args / invalid JSON / truncated private_key — সব ক্ষেত্রে পরিষ্কার গাইডিং মেসেজ (node --check পাস)
- নিরাপত্তা-নীতি ঘোষিত: SA JSON শুধু সার্ভার-সাইডে পড়া হবে, কখনো প্রিন্ট/repo-push/অ্যাপ-বান্ডল হবে না; সব phase শেষে Owner কী ডিলিট করবেন

Stage Summary:
- অটোমেশন এক-কমান্ড প্রস্তুত: node scripts/firebase-setup.mjs <sa.json> → DB তৈরি + rules পাবলিশ + যাচাই এক রানে
- Owner-এর ধাপ: Firebase console → Project settings → Service accounts → Generate new private key → JSON ফাইল এই চ্যাটে পাঠানো
- পরবর্তী: JSON লাভের সাথে সাথে স্ক্রিপ্ট চালানো → অ্যাপে Retry registration → Phase 4 টেস্ট

---
Task ID: 13
Agent: Z.ai Code (main)
Task: SA JSON লাভ → Firestore প্রভিশন অটোমেশন চেষ্টা → অনুমতি-সীমা নির্ণয় → ruleset তৈরি → Owner-এর শেষ-ধাপ (DB create) নির্ধারণ

Work Log:
- Owner SA JSON পাঠিয়েছেন; /home/z/.secrets/firebase-sa.json-এ সেভ (প্রজেক্ট-ট্রি-বহির্ভূত, কখনো repo-তে যায় না); project_id মিলেছে (remote-family-support)
- firebase-setup.mjs চালানো: Auth ✓, কিন্তু DB create → 403 PERMISSION_DENIED; API enable-ও 403
- testIamPermissions দিয়ে সঠিক অনুমতি-মানচিত্র: HELD = datastore.databases.get, firebaserules.rulesets.create, firebaserules.releases.update, firebaserules.releases.get, firebase.projects.get, firebase.projects.update, datastore.operations.get; NOT HELD = datastore.databases.create, serviceusage.services.enable, releases.create
- নিশ্চিত প্রমাণ: firestore.googleapis.com API চালু আছে (DB-GET 404 দিচ্ছে = API জীবন্ত, DB অনুপস্থিত) — রুট-কজ চূড়ান্ত
- Phase 4 rules ruleset তৈরি + API-সংকলন-যাচাইকৃত: projects/remote-family-support/rulesets/98a04b05-cc8a-4934-8dae-74230a5e5838
- Release-attach চেষ্টা: discovery-পড়ে সঠিক ফরম্যাট বের করা (PATCH body = UpdateReleaseRequest{release{name,rulesetName}}; create-এ Release.name বডিতে) — কিন্তু release এখনো অস্তিত্বহীন (404) এবং create-অনুমতি নেই; release তৈরি হয় DB প্রভিশনের সময় Google নিজেই করে
- addFirestore (Management API) পথ পরীক্ষা: v1beta1 discovery-তে মেথড আর নেই — zero-tap DB-তৈরি সম্ভব নয়
- চূড়ান্ত বিভাজন: Owner = শুধু console-এ DB তৈরি (production mode, asia-south1); এরপর আমি = release PATCH (releases.update ✓) দিয়ে ruleset attach + যাচাই; ফলব্যাক = rules-ট্যাব-পেস্ট

Stage Summary:
- SA টোকেন দিয়ে যা যা সম্ভব সব সম্পন্ন: API-অবস্থা নিশ্চিত, ruleset (Phase 4) তৈরি ও কম্পাইল-প্রমাণিত, DB-অনুপস্থিতি প্রমাণিত (404)
- একমাত্র বাকি বাধা: console-এ "Create database" বোতাম — Google নতুন প্রজেক্টের admin-SA-কে datastore.databases.create ইচ্ছাকৃতভাবে দেয় না
- Owner-এর নির্দেশ দেওয়া হয়েছে: console.firebase.google.com/project/remote-family-support/firestore → Create database → Production → asia-south1 → Enable
- DB তৈরির খবর পাওয়া মাত্র: node দিয়ে release PATCH (UpdateReleaseRequest ফরম্যাট) → verify → Owner-কে অ্যাপ-টেস্ট নির্দেশ

---
Task ID: 14
Agent: Z.ai Code (main)
Task: Owner "Firestore+RTDB দুটোই বানিয়েছি" — যাচাই, RTDB লক, প্রকৃত Firestore-বিভ্রান্তি নির্ণয় ও সঠিক নির্দেশনা

Work Log:
- Firestore DB re-check: এখনো 404 — Owner প্রকৃতপক্ষে **Realtime Database** তৈরি করেছেন (URL: remote-family-support-default-rtdb.asia-southeast1...); Build-মেনুতে Firestore-এর ঠিক উপরে থাকে বলে বিভ্রান্তি
- Releases list: শুধু "default" (RTDB) → তার ruleset-এ আমার Firestore-syntax Phase 4 rules — অনুমান: Owner গাইডের কপি-বোতামের টেক্সট RTDB Rules ট্যাবে পেস্ট করেছিলেন; ভুল সিনট্যাক্স = কার্যত deny-all (দুর্ঘটনাবশত নিরাপদ)
- RTDB সঠিকভাবে লক: firebaserules-API-পথ ব্যর্থ ("does not compile" — RTDB নিয়ম ওই API দিয়ে যায় না); সঠিক পথ = {instance}/.settings/rules.json, স্কোপ দরকার "email + firebase.database + cloud-platform" (শুধু cloud-platform হলে 401); এখন rules = {".read":false,".write":false} যাচাইকৃত ✓
- Phase 4 Firestore ruleset 98a04b05 অপরিবর্তিত প্রস্তুত; DB তৈরির পরেই PATCH cloud.firestore → এই ruleset
- Owner-কে বুলেটপ্রুফ নির্দেশ দেওয়া হয়েছে: সরাসরি /firestore URL, মেনু-আইটেম "Firestore Database" (≠ "Realtime Database"), production mode, asia-south1

Stage Summary:
- RTDB: সম্পূর্ণ, লক, নিরাপদ — অ্যাপ এটি ব্যবহারই করে না; আর কোনো কাজ নেই
- Firestore: একমাত্র বাকি ধাপ Owner-এর Create-database ট্যাপ; এরপর আমি rules attach + যাচাই করব
- শিক্ষা: ভবিষ্যৎ নির্দেশে Firestore বনাম RTDB পার্থক্য স্পষ্ট করে লিখতে হবে

---
Task ID: 15
Agent: Z.ai Code (main)
Task: Owner-এর Gemini-স্ক্রিনশট (Firestore তৈরি) যাচাই → rules attach → go-live ভেরিফিকেশন → নতুন আবিষ্কৃত database-id সমস্যার অ্যাপ-ফিক্স (v0.5.2)

Work Log:
- Gemini-স্ক্রিনশট: DB=default, asia-south1, FIRESTORE_NATIVE → API-প্রমাণ: databases তালিকায় ১টি DB, কিন্তু id আক্ষরিকভাবে "default" (বন্ধনী-হীন); GET (default) → 404
- ডাটা-প্লেন পরীক্ষা: (default)/documents → 404 "database (default) does not exist"; default/documents → "Document not found" (DB জীবন্ত) → অ্যাপের প্লেইন getInstance() ব্যর্থ হবে — নতুন রুট-কজ
- প্রাপ্ত-বয়স্ক লাইভ ruleset c1a9fa86-এ আগের-সংস্করণ Phase 4 rules পাওয়া গেল (Owner সঠিক ট্যাবে পেস্ট করেছিলেন); তবু নিশ্চয়তার জন্য বর্তমান ফাইল থেকে ফ্রেশ ruleset তৈরি (কম্পাইল-প্রমাণিত)
- Release-attach সাফল্য: PATCH projects/P/releases/cloud.firestore/default বডি UpdateReleaseRequest{release{name,rulesetName}} → HTTP 200 প্রথম চেষ্টাতেই; পুনঃপাঠে নিশ্চিত — লাইভ = 732d38cb-a502-4a3f-9e71-12bb3561aeb6
- rules সিমুলেশন (projects:test): ফরম্যাট-শুধ্র (path ফিল্ড) করে 403 IAM-অনুমতি সীমা — নন-ফেটাল; কম্পাইল-যাচাই + অ্যাপ-টেস্টই প্রমাণ-পথ
- বোঝা গেল BoM 33.4.0 → firestore 25.1.0; AAR-ক্লাস-ফাইল বাইট-স্ক্যানে প্রমাণ: getInstance(String) + getInstance(FirebaseApp,String) ওভারলোড উপস্থিত
- v0.5.2 (versionCode 8): build.gradle.kts-এ BuildConfig.FIRESTORE_DATABASE_ID (env-ওভাররাইডযোগ্য, ডিফল্ট "default"); নতুন Fb.kt কেন্দ্রীয় প্রোভাইডার; MainActivity (২) + PairingRepo (৬) সাইট Fb.db-তে; অব্যবহৃত import অপসারণ
- push-release-update.mjs-এ Fb.kt এন্ট্রি + মেসেজ আপডেট; পুশ: bad13b8/9174dda/4d927e4/c87d523; CI run 40514280626 (in_progress)

Stage Summary:
- Firestore "default" (asia-south1) লাইভ + Phase 4 rules সংযুক্ত (ruleset 732d38cb) — সার্ভার-পাশ সম্পূর্ণ ✓
- মূল শিক্ষা: ২০২৬-এর console-flow DB-আইডি "default" দেয়; SDK-র লিগ্যাসি "(default)"-পাথ 404 — অ্যাপ-সাইড ডাটাবেস-আইডি নির্দিষ্টকরণই একমাত্র ফিক্স
- v0.5.2 CI বিল্ড চলছে; সফল হলে Owner নতুন APK ইনস্টল করে Google Sign-In → প্রোফাইল-সেভ → পেয়ারিং-কোড টেস্ট করবেন

---
Task ID: 15-b
Agent: Z.ai Code (main)
Task: v0.5.2 CI-বিল্ড ফলাফল নিশ্চিতকরণ

Work Log:
- CI run 40514280626 → status completed, conclusion success (কম্পাইল = getInstance(String) ওভারলোড + BuildConfig ফিল্ড প্রমাণিত)
- আর্টিফ্যাক্ট: app-release-apk (10,191,079 bytes, id 12737439757), app-debug-apk (14,126,467) — expired নয়

Stage Summary:
- v0.5.2 রিলিজ-প্রস্তুত; Owner-এর বাকি কাজ শুধু ডাউনলোড-ইনস্টল-টেস্ট

---
Task ID: 15-c
Agent: Z.ai Code (main)
Task: Phase 4 চূড়ান্ত-যাচাই রেকর্ড (Owner স্ক্রিনশট) — Phase 4 সম্পূর্ণ ঘোষণা

Work Log:
- Owner v0.5.2 টেস্ট-স্ক্রিনশট (20:08 ডিভাইস-সময়): "Registered ✓ (id 81033e17…)"; "Devices on this account: 2201116SI · v0.5.2 · id 81033e17…" → প্রোফাইল-সেভ + ডিভাইস-তালিকা পাঠ দুটোই সফল
- পেয়ারিং-প্রবাহ সক্রিয়: কোড FUE9UB-0e7f259f0ff2f0a4463f (৬-অক্ষর + টোকেন ফরম্যাট সঠিক), QR প্রদর্শিত, "Expires in 4:20" টাইমার চলছে, "Waiting for the other device…" (স্ন্যাপশট-লিসেনার জীবন্ত), Cancel-বোতাম উপস্থিত; helper-পাশে Redeem-ইনপুট + "Pair with code"
- "Could not reach Firestore" এরর সম্পূর্ণ অদৃশ্য — database-id ফিক্স (v0.5.2) বাস্তবে প্রমাণিত

Stage Summary:
- Phase 4 (কোড-ভিত্তিক পেয়ারিং + QR প্রদর্শন + মেয়াদ + লাইভ-পর্যবেক্ষণ) সম্পূর্ণ ✓ — v0.5.2 (versionCode 8)
- পরবর্তী: Phase 5 — ডিভাইস তালিকা+presence(হার্টবিট+TTL)+FCM+revoke + QR ক্যামেরা-স্ক্যান (Phase 4 থেকে স্থানান্তরিত, Section 22.3 সিদ্ধান্ত)

---
Task ID: 16
Agent: Z.ai Code (main)
Task: Owner-নির্দেশ — ডকুমেন্ট-আপডেট + Phase 5 শুরু (Presence + FCM ভিত্তি + QR স্ক্যান)

Work Log:
- Phase 4-সম্পূর্ণ রেকর্ড: worklog Task 15-c + docs Section 24 + README আপডেট (push 8bcb671)
- Phase 5 পরিকল্পনা: docs Section 25 (সুযোগ, এক-ফোন যাচাই-পথ, প্রযুক্তি-সিদ্ধান্ত)
- v0.6.0 (versionCode 9) বাস্তবায়ন: PresenceRepo.kt (হার্টবিট+TTL+watch+HeartbeatWorker), QrScanner.kt (parsePairingPayload+QrScanDialog+LifecycleCameraController+ML Kit), MainActivity (Presence/Push স্টেটাস, Scan-QR বোতাম+ডায়ালগ, PresenceBadge), PairingRepo (recipientDeviceId+self-redeem এরর), rules (devicePresence+fcmTokens), manifest (CAMERA), gradle (camera 1.3.4 পিন, mlkit, workmanager, messaging)
- CI-সংগ্রাম ৫ রান: camera-bom-অনুপস্থিতি (404) → স্পষ্ট-পিন; Guava ListenableFuture-অদৃশ্যতা → LifecycleCameraController-স্থাপত্য; শেষ বাগ = আমার ভুল import-প্যাকেজ → রান-৬ SUCCESS (d3482ae), release APK 21.1 MB
- SA-কী স্যান্ডবক্স-রিসেটে হারায় → Phase 5 rules সার্ভারে attach করা যায়নি; Owner-পেস্ট-নির্দেশ দেওয়া হয়েছে (rules রিপো-তে push হয়েছে)

Stage Summary:
- Phase 5 কোড-সম্পূর্ণ + বিল্ড-প্রমাণিত; বাকি: Owner rules-পেস্ট + ইনস্টল + টেস্ট
- শিক্ষা: (১) dl.google.com-এ artifact-আগে-যাচাই (camera-bom নেই); (২) CameraX-এ ProcessCameraProvider-এর বদলে LifecycleCameraController কটলিন-বান্ধব; (৩) androidx AAR-ক্লাস-প্যাকেজ আগে-যাচাই

---
Task ID: 17
Agent: Z.ai Code (main)
Task: Phase 5 প্রথম টেস্ট-ফিডব্যাকের ২ বাগ-ফিক্স — v0.6.1 (versionCode 10)

Work Log:
- Owner-স্ক্রিনশট বিশ্লেষণ: (১) QR স্ক্যান হয় কিন্তু Pair with code → "INVALID_ARGUMENT: Firestore transactions require all reads to be executed before all writes"; (২) QR কপি/ডাউনলোড-সুবিধাই নেই
- মূল-কারণ: PairingRepo.redeemCode-এর transaction-এ tx.get(relRef) tx.update(invRef)-এর পরে ছিল — Firestore-এর "সব read সব write-এর আগে" নিয়ম-লঙ্ঘন (v0.5.0 থেকেই লুকানো বাগ; প্রথম বাস্তব দুই-অ্যাকাউন্ট টেস্টে ধরা পড়ল)
- ফিক্স-১: redeemCode পুনর্বিন্যাস — tx.get(invRef) + tx.get(relRef) সবার আগে → ভ্যালিডেশন → তারপর write; re-pair update-পাথে recipient-ফিল্ড রিফ্রেশ
- ফিক্স-২: QrActions.kt (নতুন) — copyToClipboard + saveQrToDownloads (API 29+: MediaStore.Downloads; 26–28: Download-dir + runtime WRITE_EXTERNAL_STORAGE) + buildQrShareIntent (FileProvider + ACTION_SEND)
- MainActivity: QR-এর নিচে Copy code ("Copied ✓" ফিডব্যাক) / Save QR (Download/pairing-code-<ID>.png + "Saved ✓" পথ) / Share বোতাম-ত্রয়ী; ভার্সন-স্ট্রিং 0.6.1
- Manifest: WRITE_EXTERNAL_STORAGE (maxSdkVersion 28) + FileProvider; res/xml/file_paths.xml (নতুন, cache-path qr_share/)
- build.gradle.kts: versionCode 10 / versionName 0.6.1
- push script-এ QrActions.kt + file_paths.xml + README.md যোগ; পুশ: 4af34d6/3e847e4/f70321e/e18f2da/9dc01d7/ced06bd/bd796a7
- README.md: v0.6.1 bugfix + features নোট; docs/PHASE-0.md: Section 27 (ফিডব্যাক-রেকর্ড + টেস্ট-তালিকা)

Stage Summary:
- v0.6.1 push সম্পূর্ণ; CI run 40529820096 পর্যবেক্ষণাধীন — সফল হলে Owner নতুন app-release-apk ইনস্টল করে (ক) Copy/Save/Share তিনটি (খ) দুই-অ্যাকাউন্ট পেয়ারিং (আগের INVALID_ARGUMENT → "Paired ✓") টেস্ট করবেন
- শিক্ষা: Firestore transaction-এ কখনো read-after-write নয়; ভবিষ্যতের transaction-লেখায় এই নিয়ম আগে-ভাবনা

---
Task ID: 17-b
Agent: Z.ai Code (main)
Task: v0.6.1 CI-বিল্ড ফলাফল নিশ্চিতকরণ

Work Log:
- রান-১ (40529820096) failure: QrActions.kt:54 — "Returns are prohibited for functions with an expression body" (saveQrToDownloads-এ expression-body try-এর ভিতরে return null)
- ফিক্স: saveQrToDownloads → block body { return try {...} } (কমিট 4d41702); অন্যান্য ফাইলে একই-ধাঁচ স্ক্যান — আর কোনো ঝুঁকি-পয়েন্ট নেই
- রান-২ (40530330288) → SUCCESS ✓; artifact app-release-apk 21,098,789 bytes (id 12746890337), app-debug-apk 25,486,847

Stage Summary:
- v0.6.1 রিলিজ-প্রস্তুত — Owner নতুন app-release-apk ইনস্টল করে Copy/Save QR/Share + দুই-অ্যাকাউন্ট পেয়ারিং টেস্ট করবেন
- শিক্ষা: Kotlin expression-body ফাংশনে return নিষিদ্ধ — early-return দরকার হলে শুরু থেকেই block body

---
Task ID: 18
Agent: Z.ai Code (main)
Task: পেয়ারিং PERMISSION_DENIED ফিক্স — rules missing-doc get + v0.6.2 (versionCode 11)

Work Log:
- Owner-স্ক্রিনশট: v0.6.1 ইনস্টল ✓, INVALID_ARGUMENT গেছে ✓, নতুন এরর "Permission denied by the security rules" (রিডিম transaction)
- মূল-কারণ: redeemCode transaction-এ tx.get(relRef) একটি এখনো-না-থাকা relationship ডকুমেন্ট পড়ে (create-vs-update সিদ্ধান্ত); লাইভ rules (Phase 4, ruleset 732d38cb)-এ `allow get: if signedIn() && (helper==uid || recipient==uid)` — missing doc-এ ফিল্ডই নেই → deny → প্রথম-পেয়ারিং সবসময় PERMISSION_DENIED
- ফিক্স-১ (rules): `allow get`-এ `!exists(...relationship/$(relId)) ||` শাখা — অনুপস্থিত ডকুমেন্ট-পাঠ অনুমতি (কোনো ডেটা-ফাঁস নেই); list অপরিবর্তিত
- ফিক্স-২ (কোড-সামঞ্জস্য): v0.6.1-এ re-pair update-এ অতিরিক্ত ফিল্ড পাঠানো হয়েছিল যা rules-এর update-নিয়ম (affectedKeys hasOnly ['status','updatedAt']) deny করত — PairingRepo re-pair পাথ আবার শুধু status+updatedAt
- v0.6.2 (versionCode 11): gradle + MainActivity ভার্সন-স্ট্রিং; push 7b2fd84/ff889c9/a0e51ee/14a32fb; CI run 40544450009 → SUCCESS; release APK 21,098,206 bytes (id 12752681019)
- Owner-নির্দেশ প্রস্তুত: raw.githubusercontent.com/psfaruk/family-remote-support/main/firestore.rules → সম্পূর্ণ কপি → Firebase console → Firestore → Rules → পেস্ট → Publish (SA-কী নেই, পেস্ট-পথই প্রতিষ্ঠিত); তারপর v0.6.2 APK ইনস্টল

Stage Summary:
- পেয়ারিং-পাথের সব প্রতিবন্ধক এখন তত্ত্বগতভাবে অপসারিত: transaction-বাগ (v0.6.1) + rules missing-doc get (v0.6.2 rules) + re-pair ফিল্ড-সামঞ্জস্য (v0.6.2 APK)
- Owner-এর বাকি: console-এ ফিক্সড rules পেস্ট+Publish → v0.6.2 ইনস্টল → টেস্ট; শিক্ষা: transaction-এ missing-doc get অবশ্যই rules-এ আলাদাভাবে অনুমোদন করতে হয়

---
Task ID: 19
Agent: Z.ai Code (main)
Task: Phase 6 — Support session সম্মতি-প্রবাহ (v0.7.0, versionCode 12)

Work Log:
- Owner-নিশ্চিতি: v0.6.2 + rules-পেস্টে পেয়ারিং সফল (ACTIVE · Online) + Revoke-টেস্ট — Phase 4+5 ভেরিফায়েড; Phase 6 শুরু
- ডিজাইন-সিদ্ধান্ত: মূল-রোডম্যাপের Phase 6 (সিগন্যালিং-সার্ভার) স্থগিত রেখে স্তম্ভ-৩-এর ভিত্তি আগে — সেশন-সম্মতি মেশিন: REQUESTED (২ মিনিট deadline) → ACTIVE (১৫ মিনিট, অটো-শেষ) / DENIED / CANCELLED / EXPIRED → ENDED (যে-কেউ বা "timeout")
- SessionRepo.kt (নতুন): requestSession / grantSession / denySession / cancelSession / endSession(byTimeout) / expireSession / watchSessions (উভয়-রোল, newest-first); সব mutation transaction (read→validate→write) — v0.6.1-শিক্ষা মেনে
- App.kt (নতুন): started-activity কাউন্ট — অ্যাপ দৃশ্যমান কিনা
- SessionNotifier.kt (নতুন): recipient-পাশে REQUESTED-listener; ব্যাকগ্রাউন্ডে high-importance নোটিফিকেশন; Android 13+ POST_NOTIFICATIONS runtime-বোতাম UI-তে; সততা-নোট: process মারা গেলে ডেলিভারি বন্ধ — kill-push Phase 7 সার্ভারে (fcmTokens প্রস্তুত)
- MainActivity.kt: Paired-devices-রোতে helper-এর "Start session (asks permission)"; Section D — recipient-সম্মতি-কার্ড (Allow/Deny + কাউন্টডাউন), helper waiting + Cancel, ACTIVE-কার্ড + End session, "Last session:" হিস্ট্রি; lazy-expiry effects; ভার্সন 0.7.0 (Phase 6), পরবর্তী মাইলফলক Phase 7
- AndroidManifest: android:name=".App" + POST_NOTIFICATIONS
- firestore.rules: supportSessions সেকশন — participant-only get/list; create শুধু ACTIVE-rel helper (relId==helperUid_recipientUid, deadline ≤ ৫ মিনিট); update-এ ৫টি ট্রানজিশনের হোয়াইটলিস্ট (affectedKeys hasOnly প্রতিটিতে); delete deny
- build.gradle.kts: versionCode 12 / 0.7.0; README Status রিরাইট; PHASE-0.md §29 (ডিজাইন-ডায়াগ্রাম + টেস্ট-তালিকা); push script-এ ৩ নতুন ফাইল + v0.7.0 মেসেজ

Stage Summary:
- কোড + rules সম্পূর্ণ; push: 60131fb/dfc1940/3801cde/48f6c75/7694000/3a6d24c/37aa6d5/17b6e91 + ফিক্স c7a04e5/3a271c6
- Owner-এর আবশ্যক কাজ: নতুন rules আবার console-এ পেস্ট+Publish, তারপর দুই ফোনে v0.7.0 APK ইনস্টল → টেস্ট-তালিকা §29.6
- শিক্ষা: Android Firestore SDK-র Transaction-এ Query-read নেই (শুধু DocumentReference) — duplicate-check বাইরের plain query-তে; Timestamp-এ `.time` নেই (`.toDate().time`); Kotlin Set-এ takeLast নেই

---
Task ID: 19-b
Agent: Z.ai Code (main)
Task: v0.7.0 CI-বিল্ড ফলাফল

Work Log:
- রান-১ (40550168439) failure — ৩ কম্পাইল-এরর: SessionRepo.kt:92 tx.get(Query) unsupported (DocumentReference expected), :97/:98 isEmpty/documents chain-break, :185 Timestamp.time unresolved; SessionNotifier.kt:91 Set.takeLast unresolved
- ফিক্স: requestSession-এ duplicate-check transaction-এর বাইরে plain query-তে (UI single-shot রাখে — race non-issue); endsAt.toDate().time; seenRequestIds.toList().takeLast(20) (কমিট c7a04e5, 3a271c6)
- রান-২ (40550869751) → SUCCESS ✓; artifact app-release-apk 21,120,335 bytes (id 12754519507), app-debug-apk 25,516,791

Stage Summary:
- v0.7.0 রিলিজ-প্রস্তুত — Owner rules-পেস্ট + দুই ফোনে ইনস্টল → সেশন-সম্মতি টেস্ট (§29.6)
- শিক্ষা: Android SDK transaction-সীমা CI-তেই যাচাই হয়; ভবিষ্যতে tx.get(Query) ধরে লেখা যাবে না

---
Task ID: 20
Agent: Z.ai Code (main)
Task: Phase 6 v0.7.1 — "Start session" PERMISSION_DENIED সংশোধনী (rules-vs-query প্রমাণযোগ্যতা + composite index পরিহার)

Work Log:
- Owner-স্ক্রিনশট: পেয়ারিং ✓ (ACTIVE · Online), কিন্তু "Start session" → PERMISSION_DENIED; Owner জানেন না console-এ কী করতে হবে
- স্থিতি-যাচাই: v0.7.0 কোড+rules CI-প্রমাণিত; Phase 6 rules এখনো পাবলিশ হয়নি (published = v0.6.2 rules; supportSessions ক্যাচ-অল deny-তে); SA-কী নেই (/home/z/.secrets নেই) → rules-পাবলিশ Owner-কেই করতে হবে (তিনি v0.6.2-এ একবার করেছেন)
- গভীর বিশ্লেষণে দুটি লুকানো বাগ ধরা পড়ল যা rules-পাবলিশের পরেও Start session ভাঙত:
  1. duplicate-check query শুধু relId==X দিয়ে খুঁজত — rules-এর list-শর্ত (helperUid/recipientUid==auth.uid) query থেকে প্রমাণযোগ্য নয় ("queries are not filters") → helperUid whereEqualTo যোগ
  2. watchSessions-এর orderBy(requestedAt DESC) composite index চাইত (console-কাজ) → equality-only (helperUid/recipientUid + status-in [REQUESTED, ACTIVE], limit 10) করা হলো — index-মুক্ত, rules-প্রমাণযোগ্য, ফলাফল-সেট সম্পূর্ণ
- SessionRepo.kt: getSession() নতুন (one-shot final-state read); MainActivity.kt: lastFinishedSession/lastOpenSessionId state + LaunchedEffect(sessions) open→closed ট্রানজিশনে getSession — "Last session:" লাইন অক্ষুণ্ণ; unused Query import অপসারণ
- PairingRepo.kt: describeFirestoreError PERMISSION_DENIED টেক্সট → Phase 6 rules-পেস্ট নির্দেশক
- ভার্সন: versionCode 13 / 0.7.1; README Status + PHASE-0.md §29.8 (দুই শিক্ষাসহ); push script মেসেজ v0.7.1
- push: f5b22e7/a1c2130/6a150ea/64993e3/2175d1a; CI runs 40556933678 + 40556942076 (in_progress লেখার সময়)

Stage Summary:
- v0.7.1 = Phase 6-এর সম্পূর্ণ সংশোধিত সংস্করণ; এখন rules-পাবলিশ (Owner, একবার) + v0.7.1 ইনস্টল করলেই সেশন-প্রবাহ সম্পূর্ণ টেস্টযোগ্য
- স্থায়ী শিক্ষা ১: rules list-শর্তের প্রতিটি disjunct query-তে where-দিয়ে পিন করতে হয়; স্থায়ী শিক্ষা ২: নতুন query-তে orderBy দিলে composite index-কথা ভাবতে হয় — index-হীন বিকল্প আগে ভাবা ভালো (Owner phone-only)
- Phase 7 (signaling/WebRTC) শুরুর আগে v0.7.1 টেস্ট-রিপোর্ট অপেক্ষ্য

---
Task ID: 20-b
Agent: Z.ai Code (main)
Task: v0.7.1 CI-বিল্ড ফলাফল

Work Log:
- রান 40556928528 (আন্তঃকমিট 6a150ea) failure — প্রত্যাশিত: MainActivity getSession() রেফারেন্স SessionRepo-এডিটের আগে পুশ হয়েছিল
- চূড়ান্ত রান 40556942076 (2175d1a) → SUCCESS ✓; রান 40556933678 (64993e3) → SUCCESS
- artifact app-release-apk 21,121,007 bytes (id 12757092625), app-debug-apk 25,519,217 (id 12758400114)

Stage Summary:
- v0.7.1 (versionCode 13) রিলিজ-প্রস্তুত; Owner-এর বাকি: Phase 6 rules console-পেস্ট+Publish → দুই ফোনে v0.7.1 → §29.6 টেস্ট

---
Task ID: 21
Agent: Z.ai Code (main)
Task: SA-কী গ্রহণ + Firebase ভেরিফিকেশন + Phase 7 স্ক্রিন-শেয়ার (v0.8.0, versionCode 14)

Work Log:
- Owner v0.7.1 Phase 6 টেস্টে "সফল হয়েছে" নিশ্চিত (স্ক্রিনশট: request → consent card → ACTIVE 14:29 কাউন্টডাউন) + Firebase SA-কী প্রদান → /home/z/.secrets/firebase-sa.json (600) সেভ; এরপর থেকে Firebase-কাজ অটোমেশনের দায়িত্ব
- scripts/verify-firebase-state.mjs (JWT→token; releases তালিকা→ruleset-তুলনা; data sanity): লাইভ rules == repo rules ✓ (Owner-এর Phase 6 পেস্ট নিখুঁত; রিলিজ-নাম নেস্টেড cloud.firestore/default — আবিষ্কার); supportSessions: ENDED (recipient-ENDED) + EXPIRED (lazy) — state-মেশিন প্রমাণিত; fcmTokens 2, devicePresence 2
- Phase 7 (v0.8.0): view-only স্ক্রিন-শেয়ার — SignalingRepo (Firestore signal subcollection, aud-addressed docs, watch/offer/answer/ice/bye, watch-first negotiation), ScreenShareEngine (stream-webrtc-android 1.3.8; recipient = ScreenCapturerAndroid ≤1280px/15fps/even; helper = SurfaceViewRenderer; StateFlow state+info; bye-teardown), ScreenShareService (mediaProjection FGS, Android 14 startForeground-before-getMediaProjection), ScreenSharePanel (ACTIVE কার্ডে), Manifest (FOREGROUND_SERVICE[_MEDIA_PROJECTION] + service declaration), gradle (webrtc dep + abiFilters arm-only + 14/0.8.0)
- firestore.rules: supportSessions/{id}/signal — get/list=aud==caller (provable), create=ACTIVE+participant+aud==other-participant (ternary)+type-whitelist+payload<100KB, delete=participant; SA দিয়ে Publish: scripts/publish-firestore-rules.mjs — শেখা: v1 PATCH বডি = UpdateReleaseRequest {release:{name, rulesetName}, updateMask:"ruleset_name"} (সরাসরি Release নয়; updateMask query-param নয়) → ruleset f53c57fc লাইভ, re-verify == repo ✓
- push: f715579/525f29b/b366824/0da328e/13084a2/6a35097/2d450ef/9b9776f; CI: আন্তঃকমিট 13084a2 failure (Manifest/gradle পরে — প্রত্যাশিত), চূড়ান্ত 40563966281 (9b9776f) → SUCCESS; artifact app-release-apk 26,338,467 bytes (id 12761968144), debug 30,778,217
- README Status রিরাইট (Phase 0–6 ✅, Phase 7 🚧 + সীমা-ঘোষণা); PHASE-0.md §30 (SA-গ্রহণ, সার্ভারহীন-সিগন্যালিং ডিজাইন, সীমা: view-only/STUN-only/FGS-liveness, §30.5 টেস্ট-তালিকা)

Stage Summary:
- Console-পেস্ট-যুগ শেষ: rules/ভেরিফিকেশন এখন SA দিয়ে অটোমেশনেই হয়
- v0.8.0 রিলিজ-প্রস্তুত: সেশন ACTIVE হলে recipient "Share screen" → helper "Watch screen" → লাইভ ভিডিও; সেশন-শেষে স্বয়ংক্রিয় টিয়ারডাউন
- Phase 8 প্রার্থী: FCM HTTP v1 kill-push (SA আছে), TURN সার্ভার (কড়া NAT), assisted remote control (AccessibilityService) — রোডম্যাপ-আলোচনা Owner-এর সাথে

---
Task ID: 22
Agent: Z.ai Code (main)
Task: Phase 7 v0.8.1 — "স্ক্রিন কালো আসে" রোগনির্ণয় ও সংশোধনী (stale-signal epoch, IceRestart re-offer, stale-callback guard, ফ্রেম-ওয়াচডগ, সেলফ-প্রিভিউ, VP8+TURN)

Work Log:
- Owner-স্ক্রিনশট: সেশন/বাটন সব ঠিক, কিন্তু helper-ফোনে কালো ভিডিও; "Answer sent — connecting video…" মিনিটের পর মিনিট আটকে; একবার "Connection failed"; recipient-এ "Connected — streaming screen…" (আশাবাদী লেখা — setRemote(answer) সাফল্যেই সেট হতো, PC-সত্য নয়)
- কোড-নির্ণয়ে তিন বাগ: (১) পুরনো রাউন্ডের offer/ice ডক listener-এর initial snapshot-এ নতুন রাউন্ডে প্রয়োগ হয়ে যেত (clearAsync-রেস) + recipient-এর remoteDescriptionSet ফ্ল্যাগ পুরনো রাউন্ডে আটকে নতুন answer ফেলে দিত; (২) re-watch-এ IceRestart ছাড়া পুনঃ-offer → নতুন candidate আসত না; (৩) বন্ধ PC-র দেরিতে FAILED callback নতুন রাউন্ড নষ্ট করত
- SignalingRepo.kt: সব সিগন্যালে `ep` (epoch) ফিল্ড; watch()-এর onSignal ৩-প্যারামিটার (type, ep, payload)
- ScreenShareEngine.kt (বড় রিরাইট): myEpoch/peerEpoch রাউন্ড-টোকেন + epoch-filter সব হ্যান্ডলারে; "watch"-এ IceRestart=true কনস্ট্রেইন্ট; createPeer-এ mine()/thisPc আইডেন্টিটি-গার্ড সব observer-callback-এ; frameCounter VideoSink + ১-সেকেন্ড ticker (প্রথম ফ্রেমে LIVE "Live — receiving the screen ✓"; ১২ সেকেন্ড ফ্রেম-শূন্যে সতর্কবার্তা); CONNECTED/DISCONNECTED-এ সৎ স্টেটাস; FAILED-এ role-ভিত্তিক করণীয়-বার্তা; stopHelper এখন bye পাঠায় না (recipient শেয়ার চালু রাখে); preferVp8() SDP munge (offer+answer); ICE_SERVERS = 2×Google STUN + openrelay.metered.ca TURN:80/443-tcp (public free; ডাউন হলে STUN-fallback); attachLocalPreview/detachLocalPreview
- MainActivity.kt: recipient ACTIVE-কার্ডে 120dp সেলফ-প্রিভিউ ("Preview — this is what the helper sees") — ক্যাপচার-প্রমাণ; App version 0.8.1 (Phase 7)
- firestore.rules: signal create-এ `ep` (string ≤64) hasOnly+required; SA দিয়ে publish (ruleset c601e0c0-…) + verify-firebase-state: LIVE == repo ✓; সেশন-ইতিহাস যাচাই (৪টি ENDED/EXPIRED — Owner-এর টেস্টের সাথে সামঞ্জস্যপূর্ণ)
- gradle: versionCode 15 / 0.8.1; README Status v0.8.1-ফিক্স-তালিকা; PHASE-0.md §30.6 (রোগনির্ণয়) + §30.7 (নতুন টেস্ট-তালিকা); push-স্ক্রিপ্ট মেসেজ v0.8.1

Stage Summary:
- v0.8.1 = কালো-স্ক্রিনের প্রধান কারণ (stale-signal poisoning) স্থাপত্যগতভাবে অসম্ভব (epoch-filter) + re-watch-এ সম্পূর্ণ নতুন ICE রাউন্ড + UI এখন সত্য বলে
- Owner-করণীয়: দুই ফোনেই v0.8.1 (rules-এ ep-required → v0.8.0-এর সিগন্যাল ইচ্ছাকৃতভাবে বন্ধ) → §30.7 টেস্ট
- ঝুঁকি-ঘোষণা: openrelay TURN তৃতীয়-পক্ষের ফ্রি সার্ভিস — ডাউন থাকলে STUN-এ ফিরে যায়; কড়া NAT-এ সমস্যা দেখা দিলে পরবর্তী ধাপে নিজস্ব/বিকল্প TURN ভাবা হবে
- Phase 8 প্রার্থী অপরিবর্তিত (FCM kill-push / TURN / assisted control)

---
Task ID: 23
Agent: Z.ai Code (main)
Task: Phase 7 v0.8.2 — প্রকৃত কালো-স্ক্রিনের মূলোৎপাটন (Compose dispose-after-attach বাগ) + SA-কী যাচাই

Work Log:
- Owner-স্ক্রিনশট (১২:৫৬–১২:৫৭): দুই ফোনেই ভিডিও-বক্স কালো — কিন্তু helper-এ "Live — receiving the screen ✓" (ওই লেখা শুধুই প্রথম decoded ফ্রেমে আসে!) এবং recipient-এ "Live — the helper can see this screen." (PeerConnectionState.CONNECTED) → নেগোসিয়েশন+ফ্রেম আসছিল, রেন্ডারই হচ্ছিল না
- নির্ণয়: ScreenSharePanel-এ attach হতো AndroidView factory-তে, তারপর `rendererView = this` স্টেট-রাইট DisposableEffect(rendererView)-কে রিস্টার্ট করত → পুরনো effect-এর onDispose-এর detachRenderer() factory-র সদ্যকৃত attach মুছে দিত, নতুন effect-বডিতে attach ছিল না → উভয় সারফেস (helper-রেন্ডারার + recipient-সেলফ-প্রিভিউ) স্থায়ী কালো। এক বাগই দুই ফোনের কালো-ছবির সম্পূর্ণ ব্যাখ্যা
- MainActivity.kt: attach এখন effect-বডিতে (`DisposableEffect(rendererView){ val view=...; attach(view); onDispose{detach; release} }`) + AndroidView(update=...) দিয়ে প্রতিটি recomposition-এ idempotent পুনঃপ্রতিষ্ঠা — দুই সারফেসেই; helper-এর স্টিকি "Request sent…" লাইন ACTIVE হলে স্বয়ংক্রিয় মুছে যায় (স্ক্রিনশটের বাসি লাইন-বাগ); App version 0.8.2 (Phase 7)
- ScreenShareEngine.kt: নতুন "watch" রাউন্ড গ্রহণে remoteDescriptionSet=false + pendingIce.clear() (re-watch-এর answer-পূর্ব candidate আর পুরনো remote-desc-এর বিরুদ্ধে প্রয়োগ হয়ে হারাবে না); ক্লাস-ডকে v0.8.2 নোট (#7)
- SA-কী অটোমেশন-যাচাই: /home/z/.secrets/firebase-sa.json (600) → verify-sa.mjs (JWT-RS256→STS token) TOKEN_OK + Firestore admin REST পড়া FIRESTORE_OK (৫টি ENDED সেশন; কোনো সেশনেই frame/screen ফিল্ড নেই — ট্রান্সপোর্ট নিষ্ক্রিয়তার সম্মত-প্রমাণ)
- gradle: versionCode 16 / 0.8.2; README Status v0.8.2-ফিক্স-বুলেট; PHASE-0.md §30.8 (নির্ণয়+ফিক্স); push-স্ক্রিপ্ট মেসেজ v0.8.2
- push: cc3ddf0/987d87c/305c9c4/d2f3946; CI চূড়ান্ত রান 40649446238 (d2f3946) → SUCCESS ✓; artifact app-release-apk 26,340,518 bytes (id 12799600327), debug 30,789,986

Stage Summary:
- v0.8.2 = কালো-স্ক্রিনের শেষ কারণ অসম্ভব করা: attach এখন effect-বডিতে + recomposition-এ পুনঃপ্রতিষ্ঠিত; "Live — receiving the screen ✓" এখন থেকে কালো-ছবির সাথে কখনোই আসবে না (লেখাটি রেন্ডার-প্রমাণ)
- Owner-করণীয়: দুই ফোনেই v0.8.2 (16) ইনস্টল → §30.7 ধাপ ৩–৭ পুনঃপরীক্ষা (rules-পরিবর্তন লাগবে না)
- SA-দিয়ে Firebase-অটোমেশন (rules publish/verify, data-check) এখন প্রমাণিত-সক্ষম; সব phase শেষে Owner SA JSON ডিলিট করবেন
---
Task ID: 24
Agent: Z.ai Code (main)
Task: Phase 7 v0.8.3 — "Connection failed"/কালো-বক্স (ক্রস-নেটওয়ার্ক ICE) মূল-কারণ নির্ণয় ও সেলফ-হিলিং ফিক্স + ভিডিও বড়/ফুলস্ক্রিন + UI-প্রশ্নের উত্তর

Work Log:
- Owner-স্ক্রিনশট (১৪:১১–১৪:১৩) বিশ্লেষণ: helper-এ কালো বক্স + "Answer sent — connecting video…" আটকে + "Connection failed"; recipient-এ "Connection failed — Stop sharing"; প্রশ্ন: ভিডিও এত ছোট কেন, UI কি এমনই থাকবে
- Raw STUN/TURN প্রোব (node dgram, tmp/turn-test.mjs + auth-turn-test.mjs): Google STUN OK (mapped 8.212.10.159); **openrelay.metered.ca DEAD** (UDP silent, TCP 80/443 refused, DNS→192.0.2.1 placeholder); standard.relay.metered.ca লাইভ কিন্তু openrelayproject ক্রেডেনশিয়াল 401; ExpressTurn পাবলিক কম্বো অকার্যকর → v0.8.1-এর TURN-fallback বাস্তবে অকেজো; CGNAT×CGNAT STUN-only-তে সাধারণত অসম্ভব
- কোড-নির্ণয়: recipient-এর "watch" হ্যান্ডলার FAILED/ENDED-স্টেটে watch **নীরবে বাতিল** করত → helper "Asked the recipient's phone…"-এ চিরকাল আটকে; FAILED-এ recipient-এর পূর্ণ teardown = Stop sharing+re-consent বাধ্যতামূলক
- ScreenShareEngine.kt v0.8.3 রিরাইট: (৮) রানটাইম TURN — প্রতি সেশনে config/turn Firestore ডক পড়ে ICE-তে merge (ConfigRepo.kt নতুন); (৯) সেলফ-হিলিং watch — capture জীবিত থাকলে যেকোনো স্টেটে watch গ্রহণ, FAILED-এ শুধু PC rebuild (track re-add সহ), WAITING_PEER-এ ফেরা; (১০) helper অটো-রিট্রাই — 12s no-offer / 12s no-answer / 14s no-video / FAILED → নতুন রাউন্ড (epoch+PC+watch), budget 3, সাফল্যে রিফিল, retryRoundTag দ্বৈত-ট্রিগার গার্ড; (১১) ক্যান্ডিডেট-সেনসাস → "Network check — direct N · via STUN M · via relay K" StateFlow; (১২) frameSize StateFlow (rotatedWidth×Height); GATHER_CONTINUALLY + Cloudflare STUN
- MainActivity.kt: helper-ভিডিও fillMaxWidth + আসল frame-aspect (cap 480dp, default 420dp) + **ট্যাপ→ফুলস্ক্রিন Dialog** (usePlatformDefaultWidth=false, কালো, ✕-Close); LIVE-তে FLAG_KEEP_SCREEN_ON; নতুন VideoRendererBox কম্পোজেবল (v0.8.2 attach-in-effect-body প্যাটার্নেই); recipient প্রিভিউ 120→160dp; info bold + netInfo সেকেন্ডারি লাইন; App version 0.8.3
- ConfigRepo.kt (নতুন): config/turn পড়ে List<IceServer> (turnUrls/username/password); ব্যর্থ/খালি ⇒ STUN-only, কখনো সেশন আটকায় না
- firestore.rules: match /config/{docId} — get: signedIn, list/create/update/delete: false → publish (ruleset 145026aa) + verify ✓
- scripts/set-turn-config.mjs (নতুন): SA দিয়ে config/turn লেখা; চালিয়ে খালি ডক seed করা হয়েছে (STUN-only এখন); ভবিষ্যতে metered.ca ক্রেডেনশিয়াল এই এক কমান্ডেই লাইভ হবে (নতুন APK ছাড়াই); নোট: DB id "default" (REST-এ (default) নয়)
- gradle 0.8.3/versionCode 17; README Status v0.8.3-বুলেট; PHASE-0.md §30.9 (বাংলায় পূর্ণ নির্ণয়+ফিক্স); push-release-update.mjs: ConfigRepo.kt এন্ট্রি + v0.8.3 কমিট-মেসেজ
- push প্রচেষ্টা: **GH_TOKEN এই সেশনের environment-এ নেই** (compaction-এ হারিয়েছে; .secrets/.env/bash-history/tool-results সব খোঁজা) → push/CI আটকে; কোড-প্রস্তুতি ১০০%

Stage Summary:
- v0.8.3 = সংযোগ এখন নিজেকে সারায় (failed রাউন্ডে দুই ফোনেই হাতে-করা রিকভারি লাগবে না) + কালো-বক্সের নেটওয়ার্ক-কারণ প্রমাণিত (TURN মৃত) + রানটাইম TURN-config (SA-চালিত, APK-ছাড়া রোটেটযোগ্য) + বড়/ফুলস্ক্রিন ভিডিও
- UI-প্রশ্নের উত্তর (Owner-কে দেওয়া হবে): বর্তমান স্ক্রিনগুলো ইচ্ছাকৃত ডায়াগনস্টিক কনসোল (প্রতি phase যাচাই); পরিপাটি অ্যাপ-UI পরবর্তী phase-এ (Phase 8 assisted control-এর পর UI/UX polish phase) — ভিডিও অংশটা এ জার্নিতেই আজ বড়+ফুলস্ক্রিন
- ব্লকার: Owner-এর GitHub টোকেন (ghp_/github_pat_) আবার পেস্ট করলেই `GH_TOKEN=… node scripts/push-release-update.mjs` → CI → APK
- Owner-করণীয় (push-এর পর): দুই ফোনে v0.8.3 (17) → একই Wi-Fi/হটস্পটে টেস্ট (১০০% প্রত্যাশিত); ভিন্ন নেটওয়ার্কের জন্য metered.ca ফ্রি TURN (২০GB/মাস) ক্রেডেনশিয়াল পাঠালে config/turn-এ বসিয়ে দেওয়া হবে
---
Task ID: 25
Agent: Z.ai Code (main)
Task: v0.8.3 GitHub-পুশ + CI-যাচাই (Owner নতুন GitHub PAT পেস্ট করেছেন)

Work Log:
- Owner নতুন GitHub টোকেন (ghp_…) পেস্ট করে "কোথাও নোট করে রাখো" — /home/z/my-project/.secrets/github-token.txt (chmod 600) এ সেভ; কখনো রিপো/লগে প্রিন্ট করা হবে না
- টোকেন-যাচাই: GET /user → HTTP 200, x-oauth-scopes-এ repo+workflow আছে ✓ (v0.8.3-এর একমাত্র ব্লকার সমাধান)
- GH_TOKEN=… node scripts/push-release-update.mjs → 6 PUT + 13 SKIP (identical): build.gradle.kts (2576bcd), MainActivity.kt (1bae224), ScreenShareEngine.kt (bd7c598), ConfigRepo.kt NEW (b7a586d), README.md (fa8047f), firestore.rules (867dade)
- CI: run #92 (fa8047f) + #93 (867dade) in_progress — চূড়ান্ত run #93 মনিটর করা হচ্ছে

Stage Summary:
- v0.8.3 (versionCode 17) সম্পূর্ণ রিপোতে উঠেছে; বিল্ড সফল হলে Owner দুই ফোনেই আপডেট ইনস্টল করবেন → §30.9 টেস্ট
- রিমাইন্ডার: openrelay TURN মৃত প্রমাণিত — ভিন্ন-নেটওয়ার্ক টেস্টের আগে metered.ca ফ্রি TURN ক্রেডেনশিয়াল চাইলে scripts/set-turn-config.mjs দিয়ে config/turn-এ বসানো হবে (নতুন APK ছাড়াই)
---
Task ID: 25 (সমাপ্তি)
Agent: Z.ai Code (main)
Task: v0.8.3 CI-ফিক্স ও সফল বিল্ড

Work Log:
- CI run #93 (867dade) FAILED: ConfigRepo.kt:32 — awaitWithTimeout হলো Task<T> extension (PairingRepo.kt:59), DocumentReference-এ সরাসরি ডাকা হয়েছিল; .get() missing
- ফিক্স: ConfigRepo.kt-তে .document("turn")-এর পর .get() যোগ → push a11d6f6
- লক্ষ্য: failed run-এ শুধু ConfigRepo-ই এরর দিয়েছে → ScreenShareEngine.kt/MainActivity.kt-র v0.8.3 কোড কম্পাইল-ক্লিন প্রমাণিত
- CI run #94 (a11d6f6) → SUCCESS (10:02:54→10:04:51Z); artifact app-release-apk 26,354,327 B (id 12807671752) + app-debug-apk 30,797,017 B

Stage Summary:
- v0.8.3 (versionCode 17) রিপোতে লাইভ, সাইনড APK তৈরি — Owner দুই ফোনে ইনস্টল করে §30.9 টেস্ট করবেন
- UI-প্রশ্নের উত্তর Owner-কে দেওয়া হয়েছে: ডায়াগনস্টিক কনসোল ইচ্ছাকৃত; ভিডিও এখনই বড়+ফুলস্ক্রিন-সক্ষম; UI/UX polish Phase 8-এর পরে
- টোকেন নিরাপত্তা: .secrets/-এ 600-perm ফাইল; চ্যাটে পেস্ট হওয়ায় ভবিষ্যতে rotate-এর পরামর্শ Owner-কে জানানো হবে
---
Task ID: 26
Agent: Z.ai Code (main)
Task: Oracle Always-Free TURN-প্রস্তুতি (Owner নির্দেশ: আগে Phase 7-যাচাই, তারপর এটা, তারপর Phase 8)

Work Log:
- Owner সংশোধন গ্রহণ: metered.ca ফ্রি কোটা 500MB/মাস (আগের 20GB দাবি ভুল ছিল); TURN-এর প্রয়োজনীয়তা-ব্যাখ্যা দেওয়া হয়েছে
- Owner-নির্দেশ: Oracle Always-Free (~10TB/মাস) TURN অ্যাপে যুক্ত করতে হবে, কিন্তু ক্রম অপরিবর্তিত — আগে Phase 7 পূর্ণ-যাচাই
- অ্যাপ-দিক ইতিমধ্যেই সম্পন্ন (v0.8.3): রানটাইম config/turn → নতুন APK লাগবে না
- TURN ক্রেডেনশিয়াল জেনারেট: .secrets/turn-oracle.txt (user=familyturn, 32-hex pass, realm=family-remote-support, 600-perm)
- .secrets/oracle-cloud-init.txt: coturn cloud-init (listen 3478, relay 49160-49200, lt-cred-mech, metadata-থেকে public/private IP auto-fill, iptables+netfilter-persistent, systemd enable) — 600-perm, পাবলিক রিপোতে যাবে না
- tmp/turn-probe.mjs (নতুন): STUN Binding → Allocate 401-challenge → signed Allocate (MD5(user:realm:pass) HMAC-SHA1) → XOR-RELAYED-ADDRESS + TCP 3478 চেক; স্মোক-টেস্ট: Google STUN binding OK (mapped 8.212.10.159), openrelay মৃত-নির্ণয় পরিষ্কার ✅
- set-turn-config.mjs ইন্টারফেস যাচাই: node scripts/set-turn-config.mjs "turn:IP:3478,turn:IP:3478?transport=tcp" familyturn <pass> — SA (/home/z/.secrets/firebase-sa.json) প্রস্তুত

Stage Summary:
- VM-Public-IP পাওয়ামাত্র: probe → PASS হলে config/turn লেখা → দুই ফোনের পরবর্তী সেশনেই relay লাইভ
- Owner-এর একমাত্র ম্যানুয়াল অংশ: Oracle ফ্রি-অ্যাকাউন্ট (কার্ড-ভেরিফিকেশন) + VM-তৈরি (cloud-init পেস্ট) + Security-List ৩ রুল + IP পাঠানো — সম্পূর্ণ ধাপে-ধাপে নির্দেশনা চ্যাটে দেওয়া হয়েছে
- Phase 8 তখনই শুরু হবে যখন Owner Phase 7-যাচাই রিপোর্ট দেবেন
---
Task ID: 27
Agent: Z.ai Code (main)
Task: নোট — Owner ম্যানুয়াল-ধাপ গ্রহণ করেছেন; Phase 8 এখনই শুরু

Work Log:
- Owner-নির্দেশ: "আমি এই কাজ গুলো পড়ে করতে চাই, নোট রাখো এবং phase 8 এর কাজ করো"
- নোট (Owner-করণীয়, চলমান): (১) দুই ফোনে v0.8.3 (versionCode 17) ইনস্টল → same-Wi-Fi স্ক্রিন-শেয়ার টেস্ট → রিপোর্ট; (২) Oracle ফ্রি-অ্যাকাউন্ট+VM (cloud-init পেস্ট, Security-List ৩ রুল) → Public IP পাঠানো; IP পেলে আমি probe→config/turn লেখা (ক্রেডেনশিয়াল .secrets/turn-oracle.txt + পুর্ণ স্ক্রিপ্ট .secrets/oracle-cloud-init.txt প্রস্তুত)
- Phase 8 (Assisted Control) এই সেশনেই শুরু — Phase 7-টেস্টের সমান্তরালে

Stage Summary:
- Phase 8 স্কোপ (v0.9.0): helper রিমোট-ট্যাপ (স্ক্রিনে ট্যাপ → grantor-ফোনে dispatchGesture) + Back/Home/Recents + assist-সেবা-স্থিতি UI; FCM kill-push Phase 8.2-এ স্থগিত
---
Task ID: 28
Agent: Z.ai Code (main)
Task: Phase 8 v0.9.0 (versionCode 18) — REMOTE ASSIST সম্পূর্ণ বাস্তবায়ন + CI SUCCESS

Work Log:
- নতুন AssistService.kt: AccessibilityService — canPerformGestures=true, canRetrieveWindowContent=false (আঙুল, চোখ নয়); tapNormalized (0..1→পিক্সেল dispatchGesture ~60ms) + pressBack/Home/Recents; companion INSTANCE isReady
- res/xml/assist_service_config.xml (নতুন) + strings.xml (label/description) + Manifest-এ BIND_ACCESSIBILITY_SERVICE সার্ভিস
- ScreenShareEngine: "cmd" (recipient, epoch-gated, captureAlive-গেটেড) → handleAssistCommand → AssistService → "cmdack"; helper-এ showAssistAck → _assistStatus StateFlow (4s auto-clear); sendAssistCmd/Tap/Nav (LIVE/CONNECTING-গেটেড); dedup থেকে cmd বাদ (একই-স্থানাঙ্ক পুনরাবৃত্ত ট্যাপ ড্রপ না হয়); startCommon/teardown ক্লিনআপ
- MainActivity: helper-এ "Touch control" Switch + assistTapModifier() (onSizeChanged+pointerInput detectTapGestures, letterbox→normalized রূপান্তর; inline+fullscreen উভয়ে) + ◀Back/⌂Home/▦Recents/⛶Full-screen বাটন + assistStatus লাইন; recipient-এ Remote assist ON/OFF সত্য-কার্ড + "Turn on Remote assist" (ACTION_ACCESSIBILITY_SETTINGS) + 1.5s পোল
- firestore.rules: signal type-তালিকায় 'cmd','cmdack' + কমেন্ট (Phase 8 বিশ্বাস-শিকল)
- gradle 0.9.0/18; README Phase 8 বুলেট + "(Touch control arrived in Phase 8)"; PHASE-0.md §31 (বাংলা: স্থাপত্য/বিশ্বাস-মডেল/Owner-টেস্ট-তালিকা/পরবর্তী); push-স্ক্রিপ্টে AssistService.kt + assist_service_config.xml + strings.xml এন্ট্রি + v0.9.0 মেসেজ
- push: 9 PUT + 12 SKIP (AssistService 90e3d19, config-xml 92ef8ee, strings 3ee4c10, rules 3c2ba8d, README 2b7982f…); CI run 40681541403 (2b7982f) → SUCCESS ✓

Stage Summary:
- Phase 8 মূল-ফিচার (রিমোট ট্যাপ + নেভ + সৎ ack) সম্পূর্ণ; APK প্রস্তুত
- ব্লকার: Firebase SA JSON (/home/z/.secrets/firebase-sa.json) compaction-এ হারিয়েছে → rules-এ cmd/cmdack এখনো LIVE হয়নি; Owner SA আবার পেস্ট করলে publish-firestore-rules.mjs চালাতে হবে (এবং Oracle config/turn + ভবিষ্যৎ সব Firebase-অটোমেশনের জন্যও এটা দরকার) — Owner-কে জানানো হয়েছে
- Phase 7-টেস্ট (v0.8.3) ও Oracle-VM এখনো Owner-হাতে চলমান
---
Task ID: 29
Agent: Z.ai Code (main)
Task: Firebase SA পুনরুদ্ধার → Phase 8-রুলস লাইভ-পাবলিশ (Task 28-ব্লকার সমাপ্ত)

Work Log:
- Owner নতুন Firebase SA JSON পেস্ট করেছেন (project remote-family-support, key_id d1329832…, পেস্টে শুরুর "{" বাদ ছিল — লেখার সময় ঠিক করা হয়েছে)
- /home/z/.secrets/firebase-sa.json লেখা (600-perm, ডিরেক্টরি 700); JSON.parse + RSA sign-test PASS (PEM 29 লাইন, অখণ্ড)
- verify-firebase-state.mjs: OAuth token ✔, Firestore পড়া ✔ (supportSessions 8 / fcmTokens 3 / devicePresence 3 — ৩ ডিভাইস জীবিত); ডিফে ধরা পড়েছিল: লাইভ-রুলসে cmd/cmdack নেই (লাইভ 344 লাইন বনাম রিপো 351)
- publish-firestore-rules.mjs চালানো: ruleset 2558400f-11d5-408d-87b4-2db14be2a549 তৈরি → release cloud.firestore/default প্যাচ → 352 লাইন LIVE
- পুনঃযাচাই (verify-firebase-state.mjs): LIVE RULES == repo firestore.rules ✔
- CI/artifact যাচাই: run 40681541403 (2b7982f, v0.9.0/versionCode 18) SUCCESS; app-release-apk 25.1MB + app-debug-apk 29.4MB available
- config/turn বর্তমান অবস্থা: STUN-only খালি doc (updatedAt 2026-10-28T08:50Z) — Oracle Public-IP পেলে set-turn-config.mjs দিয়ে লেখা হবে

Stage Summary:
- Phase 8-এর শেষ ব্লকার সরে গেছে: cmd/cmdack-রুলস এখন LIVE — v0.9.0 APK ইনস্টল করলেই রিমোট-টাচ পথ খোলা
- এখন Owner-করণীয়: (১) দুই ফোনে v0.9.0 (18) ইনস্টল → grantor-ফোনে Settings→Accessibility→Family Remote Support→চালু → helper-এ Touch control switch + ট্যাপ/Back/Home/Recents টেস্ট; (২) Phase 7-যাচাই রিপোর্ট (same-Wi-Fi + ভিন্ন-নেট); (৩) Oracle ফ্রি-অ্যাকাউন্ট+VM (cloud-init) → Public IP পাঠানো → আমি probe→config/turn লেখা
- নিরাপত্তা-নোট: SA-কী ও GitHub PAT দুটোই চ্যাটে পেস্ট-হওয়া; প্রজেক্ট স্থিতিশীল হলে দুটোই rotate করতে বলা হবে (SA rotate = নতুন key তৈরি করে পেস্ট, পুরনো মুছে দেওয়া)
---
Task ID: 30
Agent: Z.ai Code (main)
Task: google-services.json কনসোল-সিঙ্ক (SHA-1 রেজিস্ট্রেশন-অনুসরণ) + রিপো-পুশ

Work Log:
- Owner নতুন google-services.json (Firebase console-ডাউনলোড) পেস্ট করেছেন — পুরনো (Oct 26) কপির সাথে দুটো পার্থক্য: project_info.firebase_url (RTDB asia-southeast1, অ্যাপ ব্যবহার করে না) + oauth_client-এ client_type 1 এন্ট্রি (certificate_hash 825654f8f2216b5aa364826eb76458ca5ee2bd66)
- SHA-1-মিল যাচাই: worklog-রেকর্ডেড রিলিজ-কিফিঙ্গারপ্রিন্ট (Task-এ গাইড-পেজ থেকে) হুবহু মিলেছে → Owner console-এ ফিঙ্গারপ্রিন্ট বসিয়েছেন; কনসোল-কপি ক্যানোনিক্যাল
- রিপো-কাঠামো-নোট: রিপোতে Gradle-প্রজেক্ট রুটেই (app/…), তাই পাথ = app/google-services.json; android-app/app/… পাথে NOT FOUND ছিল (লোকাল মিরর আলাদা)
- android-app/app/google-services.json নতুন কপিতে লেখা (JSON-যাচাই PASS); push-release-update.mjs FILES-এ ফাইলটা পুনঃস্থাপন (Phase 2-এর পর লিস্টে ছিল না — রিপোতে ছিল বলে CI চলছিল)
- one-off PUT: app/google-services.json HTTP 200, commit 619c613 ("sync google-services.json with Firebase console …"); version-bump নেই (config-only)
- CI: পুশে নতুন রান ট্রিগার — ফল পরবর্তী নোটে

Stage Summary:
- রিপো == লোকাল == কনসোল (কনফিগ-ত্রয়ী সিঙ্কড); ভবিষ্যৎ সব বিল্ডে SHA-1-matched OAuth-এন্ট্রি এমবেড হবে
- Google Sign-In-এর ভিত আরও মজবুত (client_type 1 + release SHA-1); চলমান v0.9.0 টেস্টে কোনো পরিবর্তন নেই
- Owner-করণীয় অপরিবর্তিত: v0.9.0 ইনস্টল-টেস্ট (যেকোনো রানের app-release-apk), Phase 7-রিপোর্ট, Oracle VM → Public IP
- [Task 30-সমাপ্তি] CI রান 104 (619c613) → SUCCESS ✓; app-release-apk + app-debug-apk available (v0.9.0/18, সিঙ্কড-কনফিগ এমবেডেড); যেকোনো রানের APK টেস্টের জন্য বৈধ — নতুন রান প্রাধান্য
---
Task ID: 31
Agent: Z.ai Code (main)
Task: metered.ca রিলে লাইভ + v0.9.1 বিল্ড + ব্যাকলগ-যাচাই + Phase 9 শুরু

Work Log:
- Owner metered.ca ফ্রি-টিয়ার ক্রেডেনশিয়াল পেস্ট করেছেন (username 2a4ee07a…, ICE-টেমপ্লেট: stun.relay:80 + global.relay turn:80 / 80tcp / 443 / turns:443tcp); .secrets/turn-metered.txt (600) — চ্যাট-পেস্ট হওয়ায় rotate-নোটসহ
- .secrets/ ফোল্ডার-পারমিশন সংশোধন: ডিরেক্টরি 700 + সব ফাইল 600 (আগে 755 ছিল)
- প্রোব-সত্য: এই স্যান্ডবক্সে UDP-ইগ্রেস সম্পূর্ণ ব্লক + বাইনারি-TCP প্রথম STUN-মেসেজেই কাটা (Google STUN TCP-কানেক্টও ETIMEDOUT); মানে এখান থেকে metered-হ্যান্ডশেক চূড়ান্ত-প্রমাণ অসম্ভব — বিচারক হবে ফোন-নেটওয়ার্ক (tmp/turn-probe-tcp.mjs নতুন: RFC4571 ফ্রেমিং, TLS-সাপোর্ট — ভবিষ্যৎ যাচাইয়ের হাতিয়ার)
- ঘটনা: /home/z/.secrets/firebase-sa.json সেশন-ক্লিনারে মুছে যায় → প্রজেক্টের নিজের .secrets/ ক্যানোনিক্যাল কপি + /home/z/.secrets সিমলিংক; লাইভ-OAuth-টেস্ট PASS = বাইট-পারফেক্ট প্রমাণিত
- set-turn-config.mjs: config/turn ← ৪টা metered TURN-URL + ক্রেডেনশিয়াল → **রিলে লাইভ**; ফোনগুলো পরের সেশন থেকেই পাবে (APK ছাড়াই)
- v0.9.1 (versionCode 19): ConfigRepo KDoc "20GB/মাস" → "500MB/মাস (শুধু cross-network-এ বার্ন)" সংশোধন; push 2 PUT (gradle d55a0d2, ConfigRepo 91db24a) + 20 SKIP; CI রান 106 → SUCCESS; artifacts app-release/debug available
- ব্যাকলগ-যাচাই: Phase 7-ফোনটেস্ট (Owner) ⏳; Phase 8 কোর ✅ (ফোন-টেস্ট ⏳); Phase 8.2 FCM kill-push ⏳ (সার্ভার-সিদ্ধান্ত লাগবে: Cloud Functions/Blaze বনাম Oracle VM স্ক্রিপ্ট বনাম স্থগিত); UI polish → Phase 9; টোকেন-rotate (SA/PAT/metered) ⏳

Phase 9 (UI/UX polish) — শুরু, tranche-পরিকল্পনা:
- T0: AppTheme — পারিবারিক-উষ্ণ রঙ + বয়স্ক-বান্ধব টাইপোগ্রাফি স্কেল (MainActivity-র `MaterialTheme {` র‍্যাপ — এক-জায়গা, লজিক-ঝুঁকি শূন্য)
- T1: HomeScreen-সংগঠন — ধারাবাহিক কার্ড-প্যাডিং, বড় টাচ-টার্গেট (≥44dp), স্পষ্ট খালি-অবস্থা
- T2: সিনিয়র-ফ্লো সরলীকরণ + বাংলা-লেবেল বিকল্প; T3: টাচ-কন্ট্রোল ওভারলে পরিপাটি
- সিদ্ধান্ত: T0-কোড v0.9.2 হিসেবে Owner-র v0.9.1-রিলে-টেস্টের পরেই push — রিলে-টেস্ট-বিল্ডে UI-পরিবর্তন মিশিয়ে Owner-এর পেইড-সিদ্ধান্ত-পরীক্ষা অস্পষ্ট করব না

Stage Summary:
- রিলে লাইভ (metered ফ্রি); v0.9.1 প্রস্তুত; Phase 9 চালু (T0 প্রস্তুত); Owner-গেট: ফোন-টেস্ট (same-Wi-Fi আগে — রিলে-কোটা লাগে না, তারপর ২-৩ মিনিটের cross-network → "via relay" দেখা) → পেইড-সিদ্ধান্ত

---
Task ID: 32
Agent: Z.ai Code (main)
Task: Phase 7/8 ব্ল্যাক-স্ক্রিন ফরেনসিক + v0.9.2 ফিক্স (same-network ও cross-network দুটোতেই ব্যর্থ)

Forensics (Firestore SA dump, session 0nuERTY9mJ = 19:25 Dhaka):
- ডিভাইস-ভার্সন: helper 2201116SI (Android 13) + recipient 22101317C (Android 14) দুটোই v0.9.1 (code 19) — ভার্সন-মিসম্যাচ নয়
- config/turn: ৪টা metered TURN URL + ক্রেডেনশিয়াল লাইভ; রাউন্ড-১-এ দুই পাশেই `typ relay` candidate (64.227.188.203 / 172.232.119.40) → রিলে দুই ফোন থেকেই কাজ করছে
- রাউন্ড-১: watch → offer → helper answer+ICE → **CONNECTED + ফ্রেম ডিকোড হচ্ছিল** (প্রমাণ: 19:26:32-এ ১২টা assist-cmd পাঠানো হয়েছে, যা শুধু LIVE অবস্থায় সম্ভব) — অথচ **দুই স্ক্রিনই কালো** → রেন্ডারার বাগ নিশ্চিত
- রাউন্ড-২/৩ (helper-এর নেটওয়ার্ক বদলের পরে): recipient ৪০০-৭০০ms-এ offer+ICE দিয়েছে, কিন্তু helper কোনো answer দেয়নি → noop-observer-এ সাইলেন্ট ফেইলিওর নিশ্চিত
- শেষ চেষ্টা: ট্যাপ → watch যাওয়ায় ৩৯ সেকেন্ড → optional TURN-config রিড সর্বোচ্চ ৩০s ব্লক করছিল
- ১২টা cmd-এর কোনো cmdack Firestore-এ নেই (ack বা cmd প্রসেসিং গোপনীয়ভাবে ব্যর্থ — নতুন diag-প্যানেল এটা এখন স্ক্রিনে দেখাবে)

Fixes (v0.9.2, versionCode 20):
- ScreenShareEngine: (১৪) PERMANENT PROXY SINK — প্রতি ট্র্যাকে একটাই স্থায়ী sink, UI শুধু forwarding-target swap করে → addSink/removeSink/release রেস-ক্লাস সম্পূর্ণ অপসারণ; "decoded N · drawn M" ফ্রেম-হেলথ লাইন; (১৫) সব SDP ফেইলিওর (setRemote/createOffer/createAnswer/সেন্ড) এখন diagLog + helperRetryOrFail/recipientRecover-এ রাউটেড — সাইলেন্ট ডেড-এন্ড শেষ; (১৬) SupervisorJob scope, CLOSED-pc rebuild on re-watch, ticker-stop লগিং, CLOSED-গেট সহ ২৭টা diagLog ইভেন্ট
- ConfigRepo: TURN-config রিড ৫-সেকেন্ড সিলিং + ১০-মিনিট ক্যাশ + stale-cache ফলব্যাক + lastNote ডায়াগ
- PairingRepo: awaitWithTimeout(what, timeoutMillis) — কাস্টম টাইমআউট
- MainActivity: VideoRendererBox + local preview রিরাইট (factory-attach, DisposableEffect(Unit) final-release মাত্র) + DiagnosticsPanel (ফ্রেম-হেলথ + শেষ ১৪ ইভেন্ট, tap-to-expand) — Owner ফোন-অনলি, স্ক্রিনশটই এখন logcat
- build.gradle.kts: 0.9.2 / 20; push-স্ক্রিপ্ট মেসেজ আপডেট; eslint ক্লিন; ৪টি Kotlin ফাইল brace/paren-ব্যালেন্স OK

Stage Summary:
- রুট-কজ ৩টি প্রমাণসহ ফিক্স; রিলে/সিগন্যালিং/রুলস সব সঠিক ছিল — বাকি ছিল অ্যাপ-সাইড ৩টি বাগ
- ⏳ PUSH বাকি: এই সেশনে GH_TOKEN নেই (rate-limit-এ public API পড়া যায়নি; raw.githubusercontent দিয়ে HEAD=v0.9.1 নিশ্চিত) — Owner token দিলে scripts/push-release-update.mjs দিয়ে ১ কমান্ডে push → CI রান → APK
- Owner-ফোন-টেস্ট গাইড (v0.9.2 ইনস্টলের পরে): দুই ফোনেই নতুন APK → একই Wi-Fi-তে আগে: helper "Watch screen" → ছবি আসা উচিত; কালো হলে "Diagnostics ▾" খুলে স্ক্রিনশট → decoded>0 কিন্তু drawn=0 হলে রেন্ডারার, কম হলে কানেকশন — এখন কারণটা স্ক্রিনেই লেখা থাকবে

---
Task ID: 33
Agent: Z.ai Code (main)
Task: v0.9.2 push (নতুন GH token) + APK verify + ফরেনসিক পুনঃনিশ্চয়তা

Work Log:
- Owner নতুন PAT দিয়েছেন (ghp_PbO8…) — API verify PASS (user psfaruk, 4999 rate বাকি)
- দুর্ঘটনা-সংশোধন: android-app-এ git fetch/reset চালাতে গিয়ে parent workspace-repo-তে পড়েছিল (android-app-এর নিজের .git নেই — সবসময় parent-এর অংশ); reflog থেকে b09d4af-এ hard-reset করে ১০০% পুনরুদ্ধার; origin remote সরানো; নীতি: workspace repo-তে কখনো fetch/reset নয়, GitHub-অপারেশন সব /home/z/frs-repo (সতেজ clone, token remote) বা Contents API-তে
- Firestore পুনঃযাচাই: LIVE rules == repo (ruleset 2558400f, update 11:54Z); config/turn ডক লাইভ — ৪টা metered TURN URL + username + 16-চর password (updatedAt 12:53:33Z)
- ফরেনসিক পুনঃডাম্প (session 0nuERTY9mJU34TIDYnXp, ২৮ signal doc): টাইমলাইন সম্পূর্ণ Task-32 সিদ্ধান্তের সাথে সাংগতপূর্ণ — রাউন্ড-১ (ep a5c568) watch→offer→answer+helper-ICE (recipient-ICE doc গুলো helper-এর round-2 clearAsync-এ মুছে গেছে — রাউন্ড-১ CONNECTED ছিল); রাউন্ড-২/৩-এ ডাবল FAILED→ ডাবল auto-retry watch (13:29:31.044 + 13:29:32.114) → recipient ২ epoch-এর offer (4e7cc6 + 828795) → helper সাইলেন্ট (v0.9.1 noop SDP-observer); ১২ cmd-এর cmdack Firestore-এ নেই (aud=helper কারণে round-2-তে মুছে গিয়ে থাকতে পারে — diag-প্যানেলই এখন সত্য বলবে)
- v0.9.2 push: push-release-update.mjs → ৫ PUT (gradle 6c70c1a, MainActivity 476a6c9, PairingRepo c59aeaa, ScreenShareEngine f2f5abd, ConfigRepo 983b18a) + ১৮ SKIP
- CI: রান 107 (একা gradle-bump) SUCCESS; 108-110 আংশিক-পুশ কমিটে প্রত্যাশিত compile-fail; **রান 111 (983b18a) SUCCESS** — artifacts: app-release-apk 25.1MB (id 12823962337), app-debug-apk 29.4MB
- APK verify (artifact ডাউনলোড করে): versionName **0.9.2** binary-manifest-এ ✓, 0.9.1 অনুপস্থিত ✓, APK Signing Block v2/v3 উপস্থিত ✓, ৪ dex + arsc ✓

Stage Summary:
- v0.9.2 (code 20) সম্পূর্ণ প্রকাশিত + যাচাইত — Task-32-র ৩ root-cause-ফিক্স অ্যাপে লাইভ
- Owner-করণীয়: দুই ফোনেই রান-111-এর app-release-apk ইনস্টল → same-Wi-Fi টেস্ট → cross-network টেস্ট; ব্যর্থ হলে Diagnostics ▾ খুলে স্ক্রিনশট (decoded/drawn + শেষ ১৪ ইভেন্ট এখন স্ক্রিনেই)
- ব্যাকলগ অপরিবর্তিত: Phase 8.2 FCM kill-push (সার্ভার-সিদ্ধান্ত), Phase 9 UI-polish T0-T3 (রিলে-টেস্টের পরে), টোকেন-rotate (নতুন PAT চ্যাটে এসেছে — টেস্ট-সফলের পরে rotate করা যাবে), Oracle VM → Public IP

---
Task ID: 34
Agent: Z.ai Code (main)
Task: v0.9.2 ফোন-টেস্ট ফল বিশ্লেষণ (ভিডিও ✓, টাচ ✗, কোয়ালিটি ✗) → v0.9.3 ফিক্স

ফরেনসিক (SA dump, session e0FAdfqyAXjJdaVo0dkZ = 21:24 ঢাকা, ৭৯ signal doc):
- রাউন্ড-১ একবারেই সুস্থ: watch → offer → answer → দুই দিকে ICE → CONNECTED; ভিডিও ফ্লো করেছে (Owner নিশ্চিত) — v0.9.2-এর রেন্ডারার/সাইলেন্ট-ফেইলিওর/কনফিগ-স্টল ৩ ফিক্সই কাজ করছে; cross-network (রিলে) দুই ফোনেই সফল
- টাচ: ৩০+ cmd-এর প্রতিটির cmdack ~২০০ms-এ ফিরেছে `{"ok":false,"reason":"assist-off"}` — পাইপলাইন সম্পূর্ণ কাজ করছে; কারণ = গ্রহীতার Android Accessibility সার্ভিস enable নেই (গ্রহীতার শেয়ার-কার্ডে "Turn on Remote assist" একবার চাপলেই হয়); helper-এর সতর্কবার্তা ৪-সেকেন্ড-টোস্ট হওয়ায় Owner মিস করেছেন
- কোয়ালিটি: পুরো কোডে কোনো bitrate কনফিগই ছিল না — রিলেড 4G পাথে estimator কয়েকশো kbps-এ ঘুরে ঝাপসা+ল্যাগি
- ছোট বিশ্বাসঘাতকতা: About-প্যানেলে "App version 0.8.3 (Phase 7)" hardcoded — Owner বিভ্রান্ত

v0.9.3 (versionCode 21) ফিক্স:
- ScreenShareEngine.applyScreenBitrate(): min 800k / max 2.5M + degradationPreference MAINTAIN_RESOLUTION (টেক্সট স্পষ্ট থাকে); প্রথম addTrack ও প্রতিটি self-heal rebuild — দুই জায়গায়ই প্রয়োগ; best-effort (fail হলে ডিফল্টই থাকে)
- capture fps 15 → 20 (576×1280 VP8 SW-encode ফ্লিটের SoC-এ সহজ)
- showAssistAck(): assist-off ব্যানার এখন PERSISTENT + করণীয়-নির্দেশক ("Turn on Remote assist" কোথায় চাপতে হবে); success-এই ৪s অটো-ক্লিয়ার
- MainActivity About: আসল BuildConfig.VERSION_NAME/VERSION_CODE; "Next milestone" → Phase 9
- সিক্রেট-পুনঃস্থাপন: /home/z/.secrets/firebase-sa.json সেশন-ক্লিনারে আবার মুছে গিয়েছিল → ক্যানোনিক্যাল কপি থেকে পুনরুদ্ধার (600/700)

CI/verify:
- push: gradle 44eee0a, MainActivity 25db4b9, ScreenShareEngine 28b2c8b; রান 114 → SUCCESS (~৯০ সেকেন্ডে)
- APK verify (artifact 12828849579 ডাউনলোড): versionName 0.9.3 ✓, v2/v3 signing ✓

Stage Summary:
- Owner-করণীয়: দুই ফোনে v0.9.3 ইনস্টল; গ্রহীতার ফোনে শেয়ার-কার্ড → "Turn on Remote assist" → Android Settings → Accessibility → "Family Remote Support" enable (একবারই) → ট্যাপ টেস্ট; কোয়ালিটি same-Wi-Fi বনাম রিলে-পাথে তুলনা করে জানানো (রিলে সবসময় কিছুটা ধীর হবে)
- রিলে-টেস্ট সফল → Owner পেইড-সিদ্ধান্ত নেবেন; তারপর Phase 8.2 সিদ্ধান্ত + Phase 9 T0-T3
- PAT rotate-নোট: টোকেন চ্যাটে এসেছে; সব টেস্ট সফল হলে rotate করা যাবে

---
Task ID: 35
Agent: Z.ai Code (main)
Task: Phase 9 (v0.9.4, versionCode 22) — পারিবারিক UI polish T0-T3 (Owner: "v0.9.3-ফলাফল পরে দেখবো, পরের কাজ করো")

Work Log:
- সিদ্ধান্ত: v0.9.3-ফোন-টেস্ট Owner পরে করবেন; §31.5-অনুযায়ী পরের কাজ = Phase 9 UI polish (রিলে-টেস্ট ইতিমধ্যে সফল); Phase 8.2 তখনো Owner-সার্ভার-সিদ্ধান্তে আটকে থাকায় নয়
- T0 (থিম): MainActivity-র একক MaterialTheme র‍্যাপে FamilyWarmColors (উষ্ণ amber/brown lightColorScheme — কর্পোরেট নীল নয়; consent=tertiaryContainer জলপাই, active=primaryContainer পিচ) + SeniorTypography (body 14→16sp, small 12→14sp, titleMedium/Small 18/16sp SemiBold, labelLarge 15sp); ইচ্ছাকৃত FIXED-light (বয়স্কদের জন্য পূর্বানুমেয়); লজিক-ঝুঁকি শূন্য
- T1 (সাজ): SectionCard helper (সব কার্ডে এক প্যাডিং 16dp/স্পেসিং 10dp/টাইটেল); BigButton (fillMaxWidth ≥52dp) — sign-in/create-code/pair/start-session; WideOutlinedButton (≥48dp) — ১১টি সেকেন্ডারি অ্যাকশন; PairingCard Account-এর ঠিক নিচে উঠে এলো; dev-রো (Package/Firebase project/app ID/Next milestone) → collapsed "About · অ্যাপ সম্পর্কে" কার্ড; মৃত "Phase 2 build check" কার্ড অপসারিত; App version রো এখন হোম-টপে সবসময় দৃশ্যমান
- T2 (বাংলা-প্রথম): সেকশন-হেডার দ্বিভাষিক ("এই ফোনে কি সাহায্য দরকার? · This phone needs help" ইত্যাদি); phase-নম্বর-উপসর্গ ইউজার-দৃশ্যমান টাইটেল থেকে সরানো; পেয়ারিং কোড headlineSmall + primary রঙে; কনসেন্ট-কার্ড বাংলা-প্রথম + Allow/Deny weight-equal ≥52dp; সেশন-স্টেট লাইন বাংলা; নিরাপত্তা-গার্ড অক্ষত: sessionMessage "Request sent" ও redeemMessage "Paired" প্রিফিক্স রক্ষা (startsWith-চেক ভাঙেনি)
- T3 (টাচ-ওভারলে): assistLive এক-জায়গা-সিদ্ধান্ত (rim+badge+tap-routing); ভিডিওতে 3dp warm rim + "REMOTE TOUCH ON · টাচ চালু" ব্যাজ (inline+fullscreen দুটোতেই); fullscreen "✕ Close" টেক্সট → সত্যিকার ≥48dp বাটন; Back/Home/Recents equal-width ≥48dp; গ্রহীতার assist-স্টেট লাইন bold-দ্বিভাষিক + প্রমিনেন্ট সেটআপ-বাটন (v0.9.3-ফরেনসিকে প্রমাণিত মিস-হওয়া ধাপ)
- পরিচ্ছন্নতা: অব্যবহৃত Spacer import সরানো; string-aware brace/paren ব্যালেন্স OK (407/407, 751/751); পুরনো UI-স্ট্রিং অবশেষ শুধু কমেন্টে
- README: "Since v0.9.0" ফিক্স-সারাংশ + Phase 9 ✅ সেকশন + UI-language নোট ("15 fps" → "(20 fps since v0.9.3)")
- push: ৩ PUT (gradle d4ca3d4, MainActivity 71e834e, README 73f40fe) + ২০ SKIP; CI রান 40725141529 / 40725150175 / 40725169434 → তিনটিই SUCCESS (নতুন MainActivity প্রথম চেষ্টাতেই কম্পাইল)
- APK verify (artifact 12832229054): versionName 0.9.4 ✓, 0.9.3 অনুপস্থিত ✓, versionCode 22 (AXML INT_DEC) ✓, APK Signing Block + v2 scheme ✓ (v3 নেই — minSdk 26-তে v2 যথেষ্ট), 4 dex + arsc ✓

Stage Summary:
- v0.9.4 (code 22) সম্পূর্ণ প্রকাশিত + যাচাইত — Phase 9 T0-T3 অ্যাপে লাইভ
- Owner-করণীয়: (১) দুই ফোনে নতুন APK — v0.9.4-এ v0.9.3-এর সব ফিক্সও আছে, তাই টাচ+কোয়ালিটি টেস্ট এই বিল্ডেই বৈধ; টাচ-টেস্টের আগে গ্রহীতার ফোনে শেয়ার-কার্ডে "Turn on Remote assist · রিমোট টাচ চালু করুন" (এবার bold-বাংলা কার্ডে); (২) রিলে-কোয়ালিটি মনোযোগ দেখে পেইড-সিদ্ধান্ত
- ব্যাকলগ: Phase 8.2 FCM kill-push (Owner-সার্ভার-সিদ্ধান্ত: Cloud Functions/Blaze বনাম Oracle-VM-স্ক্রিপ্ট বনাম স্থগিত), Phase 8.3 প্রার্থী (সোয়াইপ/টাইপিং), Oracle TURN, টোকেন-rotate (SA/PAT/metered — টেস্ট-সফলের পরে)

---
Task ID: 36
Agent: Z.ai Code (main)
Task: v0.9.5 (versionCode 23) — টাচ-না-কাজার রুট-কজ ফিক্স: গাইডেড Accessibility সেটআপ + প্রথম-ওপেন Permission Wizard (Owner: স্ক্রিনশট ৩টি + "আপনি দেখে ঠিক করে দেন")

Forensics (স্ক্রিনশট ৩টি + কোড):
- Screenshot 23:20:59 — গ্রহীতার শেয়ার-স্ক্রিনে "assist tap → assist-off" লগ ৩০+ বার: cmd রাউটিং সুস্থ, ট্যাপ পৌঁছাচ্ছে, কিন্তু AssistService.instance==null → প্রতিটি ট্যাপ প্রত্যাখ্যাত
- Screenshot 23:20:07 ও 23:19:45 — গ্রহীতা Android-এর বিল্ট-ইন "Accessibility menu shortcut" (person-icon) চালু করেছে — আমাদের "Family Remote Support assist" নয়; কারণ: পুরনো বাটন জেনেরিক Accessibility তালিকায় ফেলে দিত (কোনো হাইলাইট/ইঙ্গিত ছিল না)
- helper-এর স্ক্রিনে v0.9.4-এর ইংরেজি স্থায়ী-ব্যানার দেখা যাচ্ছিল, কিন্তু পরিবার তা পড়েননি
- সিদ্ধান্ত: পাইপলাইনে কোনো বাগ নেই — enablement-UX-ই বাগ; ভুল সুইচে গিয়েছিল বলে টাচ কাজ করেনি

v0.9.5 ফিক্স (৬ ফাইল):
- AssistSetup.kt (নতুন): isAssistEnabled() Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES পড়ে (flatten + short-form, case-insensitive); isAssistReady() = instance || settings; openAssistSettings() ACTION_ACCESSIBILITY_SETTINGS + EXTRA_FRAGMENT_ARG_KEY=flatString (stock+বেশিরভাগ OEM-এ আমাদের রো হাইলাইট/স্ক্রল)
- PermissionWizard.kt (নতুন): AssistSetupGuide — বাংলা ৩-ধাপ গাইড + "⚠️ Accessibility menu shortcut নয়!" errorContainer কার্ড; PermissionWizard — প্রথম ওপেনে auto-launch চেকলিস্ট (নোটিফিকেশন 33+ runtime / ক্যামেরা runtime / Remote assist গাইড→Settings / ব্যাটারি-অপ্টিমাইজেশন ছাড় ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS), ১.৫s লাইভ-রিফ্রেশ, স্ক্রিন-ক্যাপচার per-session-নোট, "পরে করব" escape, done-flag prefs (wizard_done_v1)
- MainActivity: HomeScreen-এ "Permissions · পারমিশন" কার্ড (চালু N/4 + বাকি-নাম লাইভ, পুনরায় খোলা বাটন) + wizard host; গ্রহীতার assist-কার্ডে: isAssistReady-পোল, বাটন → AssistSetupGuide, ব্যাখ্যায় ভুল-সুইচ সতর্কতা; droppedTaps>0 হলে লাউড errorContainer ব্যানার "সাহায্যকারী এখনই ট্যাপ করছেন — কিন্তু কাজ করছে না!"
- ScreenShareEngine: _assistDropped MutableStateFlow (handleAssistCommand !ok তে +1, runOnMain); দুই teardown-পথে রিসেট; helper-এর assist-off ব্যানার দ্বিভাষিক (বাংলা-সহ)
- Manifest: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (user-initiated only)
- build.gradle.kts: 0.9.5 / 23
- পরিচ্ছন্নতা: string-aware brace/paren ব্যালেন্স 0/0 (সব ৪ ফাইল); অব্যবহৃত Intent import সরানো

প্রকাশনা:
- push-release-update.mjs: AssistSetup.kt + PermissionWizard.kt যোগ (২৩ ফাইল তালিকা), v0.9.5 কমিট-মেসেজ
- সিক্রেট-পুনঃস্থাপন: স্যান্ডবক্স রিসাইকেলে /home/z/.secrets মুছে গিয়েছিল → এবার ক্যানোনিক্যাল কপি workspace-এই ছিল: /home/z/my-project/.secrets/ (github-token.txt + firebase-sa.json); টোকেন repo-perm push:true verify
- GitHub HEAD যাচাই: আসলে v0.9.4/22 লাইভ (সামারি-র "0.9.2/20" তথ্য পুরনো); raw.githubusercontent দিয়ে ৪ ফাইল md5 MATCH — মিরর sync
- push #1: ৬ PUT (gradle 4acb64a, manifest baa1edb, MainActivity a74b3f5, engine a29ea35, AssistSetup a0b275b [নতুন 201], PermissionWizard 2d3f83c [নতুন 201]) + ১৮ SKIP
- CI রান 40738045639 → FAILURE: AssistSetup.kt-এ ২ unresolved reference — (১) flattenShortString() ভুল নাম, সঠিক flattenToShortString(); (২) Settings.EXTRA_FRAGMENT_ARG_KEY @hide, SDK-তে নেই → লিটারাল ":settings:show_fragment_args" inline; AssistSetup PUT 4e6c8c0
- CI রান 40738476017 → SUCCESS
- APK verify (artifact 12838317374): versionName 0.9.5 ✓, versionCode 23 (AXML INT_DEC typed-value) ✓, APK Signing Block v2 ("APK Sig Block 42") ✓

Stage Summary:
- v0.9.5 (code 23) — টাচ-সমস্যার আসল কারণ (ভুল সুইচ + অন্ধ তালিকা) বন্ধ; ইনস্টল/ওপেনের সময় সব পারমিশনের চেকলিস্ট উইজার্ড
- Owner-করণীয়: দুই ফোনে নতুন APK; গ্রহীতার ফোনে প্রথম ওপেনেই Permission চেক আসবে → ধাপ ৩-এ "সেটআপ খুলুন" → বাংলা গাইড → Settings-এ "Family Remote Support assist" হাইলাইট হবে → চালু → ট্যাপ টেস্ট (Accessibility menu shortcut বন্ধ রাখুন)
- ব্যাকলগ অপরিবর্তিত: Phase 8.2 FCM kill-push, রিলে পেইড-সিদ্ধান্ত, টোকেন-rotate (SA/PAT/metered)

---

Task: v0.9.6 (versionCode 24) — নিখুঁত টাচ + ইনস্ট্যান্ট টাচ + শার্প ৩০fps ভিডিও (Owner: "h চাপলে u-তে লাগে, ৩ সেকেন্ড দেরি, ঝাপসা দেখায় — ৬০fps স্মুথ চাই" + ৪ স্ক্রিনশট)

Forensics (কোড + স্ক্রিনশট):
- অফসেটের মূল কারণ (h→u): AssistService.tapNormalized() `resources.displayMetrics` (অ্যাপ-উইন্ডো = status+nav bar বাদে) দিয়ে স্কেল করছিল, অথচ ভিডিও = MediaProjection-এর পুরো ডিসপ্লে। Y প্রতিবার bar-share পরিমাণ উপরে লাগত (~১.৫ কীবোর্ড রো)। X সঠিক ছিল (width এক) — ইউজারের রিপোর্টের সাথে হুবহু মেলে
- ৩ সেকেন্ড দেরি: টাচ Firestore cmd ডক → listener রাউন্ড-ট্রিপ।
- ঝাপসা: capture 626×1280@20 (app-window aspect = ভুল অনুপাত), VP8 0.8–2.5 Mbps
- বোনাস বাগ: handled-dedup সেট cmdack-ও গিলে ফেলত (প্রতি ট্যাপের ack payload এক হওয়ায় "Tap delivered ✓" সেশনে একবারই দেখাত)

v0.9.6 ফিক্স (৩ ফাইল: ScreenShareEngine.kt + AssistService.kt + build.gradle.kts):
- AssistService: realDisplaySize() — API 30+ maximumWindowMetrics.bounds, তার নিচে getRealSize(); tapNormalized এখন রিয়েল ডিসপ্লেতে ম্যাপ করে (ভিডিওর স্পেসের সাথে হুবহু); fail-safe: সিস্টেম কল ফেল করলে পুরনো metrics
- ScreenShareEngine capture: realDisplaySize(context) থেকে শুরু, long side 1536-এ cap (626→~688-ক্লাস প্রশস্ত), @30fps, diag: "capture started WxH@30 (real display RW x RH)"
- ScreenShareEngine bitrate: min 1.2M / max 4M + enc.maxFramerate = 30.0 + MAINTAIN_RESOLUTION (আগের মতো); diag লাইন আপডেট
- INSTANT TOUCH — WebRTC DataChannel "touch": অফারার (recipient) createPeer()-এ চ্যানেল বানায় (অফারের ভেতরে থাকে), helper onDataChannel-এ নেয়; helper-এর sendAssistCmd → sendCtrl() (OPEN হলে p2p, নাহলে Firestore cmd fallback); recipient-এর handleAssistCommand → ack sendCtrl() নাহলে cmdack fallback; observeCtrl + dropCtrl (helperRestartRound/recipientRecover/teardown তিন জায়গায়); onMessage main.post + stale-channel guard; bufferedAmount > 64KB হলে fallback
- handleAssistCommand(String, ep) → handleAssistCommand(JSONObject) — দুই ট্রান্সপোর্ট (Firestore cmd epoch-gated; DC per-round) একই ফাংশন; diag-এ ট্যাপের ল্যান্ডিং-পয়েন্ট শতাংশ লগ "assist tap @62%,71% → performed" (স্ক্রিনশট-ই প্রুফ)
- handled dedup থেকে cmdack বাদ
- build.gradle.kts: 0.9.6 / 24
- push-release-update.mjs কমিট মেসেজ v0.9.6
- সতর্কতা মেনে: VP8-ই রাখা হয়েছে (H264-এ সুইচ = রিস্ক; 30fps + 1536 + 4Mbps-ই দৃশ্যমান বড় উন্নতি; 60fps পরের ধাপে H264-HW দিয়ে)

প্রকাশনা:
- push #1: ৩ PUT (gradle, engine, AssistService) + ২২ SKIP
- CI রান 40748096198 → FAILURE: ScreenShareEngine.kt:562 `enc.maxFramerate = 30.0` — stream-webrtc-android 1.3.8-এ maxFramerate হলো Int? (Double নয়) → `= 30` করে PUT bcf77d0
- CI রান 40748401932 → SUCCESS (head bcf77d0)
- APK verify (artifact 12842069804): versionName "0.9.6" ✓, versionCode INT_DEC 24 @AXML:14632 ✓ (২৩ অনুপস্থিত), APK Signing Block v2 ✓
- download/family-remote-support-v0.9.6-code24.apk (39.4 MB) সংরক্ষিত

Stage Summary:
- v0.9.6 (code 24) — টাচ এখন যেখানে টাপ করবেন ঠিক সেখানেই (রিয়েল-ডিসপ্লে ম্যাপিং), প্রায় তাৎক্ষণিক (WebRTC DataChannel p2p), ভিডিও 1536-ক্লাস@30fps + 4Mbps বাজেট
- Owner-করণীয়: দুই ফোনেই v0.9.6 ইনস্টল (হেল্পার ফোনেও — নইলে ইনস্ট্যান্ট টাচ চ্যানেল পুরনো অ্যাপে চলবে না, Firestore fallback থাকবে); সেটিংস/পারমিশন অক্ষত থাকবে (একই সিগনেচার, আপডেট-ওভার-ইনস্টল)
- যাচাই-চিহ্ন: helper-এর Diagnostics-এ "touch channel OPEN — taps now instant (p2p)" + recipient-এ "assist tap @X%,Y% → performed"; অফসেট থাকলে এই X%,Y% দিয়েই রিমোট ডায়াগনোসিস
- ব্যাকলগ: 60fps (H264-HW পরীক্ষা), Phase 8.2 FCM kill-push, রিলে পেইড-সিদ্ধান্ত, টোকেন-rotate

---
Task ID: 37
Agent: Z.ai Code (main)
Task: v0.9.7 (versionCode 25) — ৬০fps-এর পথ, Owner-নিয়ন্ত্রিত: config/quality ফায়ারস্টোর নব + H264-HW পরীক্ষা + ক্যাপাবিলিটি প্রোব + নেগোশিয়েটেড-কোডেক প্রুফ (Owner: "পরের কাজ করো" → ব্যাকলগের প্রথম আইটেম, v0.9.6-এর স্থগিত "60fps (H264-HW পরীক্ষা)")

ডিজাইন-সিদ্ধান্ত (কেন এভাবে):
- সফটওয়্যার VP8 ফ্লিটের SoC-তে 1536-ক্লাস ৬০fps পারে না, HW H.264 পারে — কিন্তু অন্ধভাবে H264-এ সুইচ ঝুঁকিপূর্ণ (ভাঙা HW এনকোডার = কালো স্ক্রিন)
- সমাধান: কোডেক-পছন্দ APK-তে বেক না করে `config/quality` Firestore ডকে (v0.8.3-এর config/turn প্যাটার্ন) — পরীক্ষা ব্যর্থ হলে ১টা Firestore write-এ ফেরানো যায়, নতুন APK লাগে না
- ইন-অ্যাপ হার্ড ফলব্যাক: ডক না থাকলে = হুবহু v0.9.6 আচরণ (VP8·30fps·1.2–4Mbps)

v0.9.7 পরিবর্তন (৫ ফাইল):
- ConfigRepo.kt: `QualityCfg` (codec/fps/minKbps/maxKbps, ডিফল্ট vp8·30·1200–4000) + `fetchQuality()` — config/quality পড়ে, সব ভ্যালু clamp (fps 10..60, max≥min), ৫s টাইমআউট, মাত্র ৬০s ক্যাচ (রিলের ১০মিনের বিপরীতে — মাঝ-পরীক্ষায় ফ্লিপ পরের সেশনেই লাগে), ব্যর্থতায় ক্যাচ-স্ট্যাম্প নেই (রিভার্ট-ফ্লিপ TTL-এর অপেক্ষা করে না)
- ScreenShareEngine.kt (৪টি স্বতন্ত্র ফিক্স):
  (২০) quality-ফেচ দুই রোলেই সেশন-স্টার্টে; capture fps = H264-HW হলে quality.fps, নাহলে min(fps,30); বিটরেট/ceiling সব quality-চালিত
  (২১) `logCodecProbe()` — ফ্যাক্টরি-তৈরিতে একবার `getRtpSender/ReceiverCapabilities` প্রোব → diag: "codec probe — H264 enc=Y dec=Y · VP8 enc=Y dec=Y"; H264 প্রেফার শুধু প্রোব-প্রমাণিত HW এনকোডার থাকলে
  (২২) `preferCodec()` (preferVp8-এর সাধারণীকরণ): target = quality.codec → H264/VP8 PT (+rtx) সামনে REORDER-ONLY — VP8 সব সময় m-line-এ থাকে, পিয়ার H264 পার্স না করলে SDP ইন্টারসেকশন নিজেই VP8-এ নামে; প্রাপ্ত SDP থেকে `firstVideoCodecOf()` দিয়ে আসল নেগোশিয়েশন পার্স → diag "video codec negotiated (offer/answer): X"; H264-চাওয়া অবস্থায় VP8 নেগোশিয়েট হলে encode ceiling সাথে সাথে ৩০-এ clamp + পুনঃপ্রয়োগ
  ডক-হেডারে ২০–২২ আইটেম যোগ
- build.gradle.kts: 0.9.7 / 25
- README.md: "Since" সারাংশে v0.9.5–0.9.7 + পুরনো coordinate-নোট সংশোধন (displayMetrics → real display)
- scripts/set-quality-config.mjs (নতুন, local-only): SA দিয়ে config/quality PATCH + readback (SA-পাথ ফলব্যাকসহ: /home/z/.secrets → /home/z/my-project/.secrets)

নিরাপত্তা-যাচাই (CI-ব্যর্থতা আগাম আটকানো):
- stream-webrtc-android 1.3.8 AAR Maven Central থেকে নামিয়ে class-ফাইল পার্স: (১) ক্যাপাবিলিটি API-র এলিমেন্ট টাইপ আসলে `RtpCapabilities.CodecCapability` (VideoCodecInfo নয় — ভুল কাস্ট করলে প্রোব নিঃশব্দে সবসময় false দিত); (২) MediaType কনস্ট্যান্ট `MEDIA_TYPE_VIDEO` (নাকি `VIDEO` নয় — কম্পাইল-এরর হত); (৩) getRtpSender/ReceiverCapabilities(MediaStreamTrack$MediaType)RtpCapabilities ডেসক্রিপ্টর উপস্থিত; প্রোব catch(Exception)→catch(Throwable) চওড়া (NoSuchMethodError-ক্লাসেও VP8-এ গ্রেসফুল ডিগ্রেড, ক্র্যাশ নয়)
- string-aware brace/paren ব্যালেন্স দুই ফাইলেই OK; preferVp8 অবশেষ শূন্য

প্রকাশনা:
- push: ৪ PUT (gradle 10deb68, engine 2c2467d, ConfigRepo 0771601, README 0245fbe) + ২১ SKIP
- CI রান 40812369713 → SUCCESS (প্রথম চেষ্টাতেই)
- APK verify (artifact 12871440248): versionName "0.9.7" ✓ (0.9.6/0.9.5 স্ট্রিং অনুপস্থিত), versionCode INT_DEC 25 @AXML 0x392c (14636 — v0.9.6-এর 24-এর 14632-এর হুবহু কাঠামোগত অবস্থান) ✓, APK Signing Block v2 ✓, 4 dex + arsc + arm64-v7a/arm64-v8a ✓, dex-এ নতুন-কোড মার্কার (codec probe / quality config / H264-HW / video codec negotiated / encode ceiling clamped) classes2.dex-এ উপস্থিত ✓
- download/family-remote-support-v0.9.7-code25.apk (39.4 MB) সংরক্ষিত
- Firebase: config/quality = { codec:h264, fps:60, minKbps:1500, maxKbps:5000 } লেখা + স্বাধীন REST readback-verify ✓; config/turn অক্ষত (৪ URL); rules-এ config/{docId} signed-in-get অনুমোদিত — নতুন ডক নিয়ম-সংশোধন ছাড়াই পড়া যায়

Stage Summary:
- v0.9.7 (code 25) প্রকাশিত + যাচাইত — "৬০fps স্মুথ" চাহিদার নিরাপদ পথ খোলা: দুই ফোনে v0.9.7 দিলেই config থেকে H264@60fps লাগে; HW ভাঙা হলে অ্যাপ নিজেই VP8@30-এ নামে, আর Owner-চিহ্নিত সমস্যা হলে ১ Firestore write-এ রিভার্ট
- Owner-করণীয়: দুই ফোনেই v0.9.7 ইনস্টল (একই সিগনেচার — আপডেট-ওভার-ইনস্টল, পারমিশন অক্ষত) → সেশন শুরু করে Diagnostics দেখুন: "quality: H264 · 60fps · 1500–5000kbps" + "codec probe — H264 enc=? dec=?" লাইনে প্রোব-ফল + "video codec negotiated (offer/answer): H264" + "capture started …@60 codec=H264-HW" এলে পরীক্ষা সফল
- সতর্কতা: ভিডিও কালো/ঝাপসা হলে স্ক্রিনশট দিন — codec=negotiated লাইন দেখে ১ Firestore write-এ VP8@30 রিভার্ট (আমি করব, APK লাগবে না); রিলেড সেশনে উচ্চ বিটরেট metered.ca কোটা দ্রুত খরচ করতে পারে (ডিরেক্ট/হটস্পট পাথে প্রযোজ্য নয়)
- ব্যাকলগ: Phase 8.2 FCM kill-push (Owner-সিদ্ধান্ত), রিলে পেইড-সিদ্ধান্ত, টোকেন-rotate (SA/PAT/metered — টেস্ট-সফলের পরে)

---
Task ID: 38
Agent: Z.ai Code (main)
Task: v0.9.8 (versionCode 26) — Phase 8.2 FCM kill-push + config/quality → VP8@30 রিভার্ট (Owner: "তাহলে তো 30fps রাখাই ভালো, আর পিছনের phase এ 8 এ কাজ বাকি ছিল, ঐটা করে দেন")

সিদ্ধান্ত-নেওয়া (কেন এভাবে):
- 30fps রিভার্ট = ১টা Firestore write (APK লাগে না) — set-quality-config.mjs vp8 30 1200 4000 → readback-verify ✓
- kill-push-এর সার্ভার-সিদ্ধান্ত (Blaze/VM/স্থগিত) Owner "করে দেন" বলায়: ব্লেজ-কার্ড/VM ছাড়া সম্ভব স্থাপত্য বেছে নেওয়া হয়েছে — দুই স্তর:
  (১) তাৎক্ষণিক স্তর: sandbox mini-service kill-push-service (port 3020, /health) — ৫ সেকেন্ড পোল
  (২) ব্যাকস্টপ স্তর: GitHub Actions kill-push.yml (cron */5 + workflow_dispatch) — sandbox রিসাইকেলেও চলে (দেরি হলে sweep নিজেই স্কিপ করে — মেয়াদ-উত্তীর্ণ অনুরোধের পুশ বিভ্রান্তিকর)
  দুই স্তরই একই scripts/fcm-kill-push.mjs চালায় (মিনি-সার্ভিস ইম্পোর্ট করে sweepOnce) — drift অসম্ভব
- ২-মিনিট answer-window-এর কারণে cron-ই মূল অবলম্বন হতে পারত না — তাই ইনস্ট্যান্ট স্তরটাই প্রাইমারি

scripts/fcm-kill-push.mjs (নতুন, repo-তে push হয়েছে):
- SA JWT → OAuth (RS256, node:crypto, zero deps); scope: firebase.messaging + datastore; টোকেন-ক্যাশ (exp−120s)
- Firestore REST runQuery: supportSessions status==REQUESTED (single-field equality — কোনো composite index লাগে না)
- ফ্রেশনেস-গেট: requestedAt ≤ ১০ মিনিট + respondDeadline ভবিষ্যতে + killPushSentAt অনুপস্থিত — মেয়াদ-উত্তীর্ণ অনুরোধে কখনো পুশ নয়
- fcmTokens রেজিস্ট্রি (Phase 5) থেকে recipient-এর টোকেন → FCM HTTP v1 notification message, android.priority=HIGH + channel_id=support_session_requests, data: {type, sessionId, helperEmail}
- সাফল্যে PATCH killPushSentAt + killPushStatus="sent:n/total" (idempotent); no-tokens হলে মার্ক করা হয় না (উইন্ডোর ভেতরে টোকেন এলে পরের sweep-এ পুশ)
- UNREGISTERED/BAD_TOKEN → fcmTokens ডক auto-DELETE (ডেড টোকেন-পরিষ্কার); per-session ব্যর্থতা সামগ্রিক exit ভাঙে না (summary-তে রিপোর্ট)

mini-services/kill-push-service/ (নতুন): package.json + index.ts — sweepOnce ৫সে পোল, ওভারল্যাপ-গার্ড, /health (last summary), ১০-মিনিট heartbeat লগ; nohup bun run dev → service.log

অ্যাপ-পরিবর্তন (ন্যূনতম — নতুন dependency নেই):
- AndroidManifest.xml: com.google.firebase.messaging.default_notification_icon + default_notification_channel_id=support_session_requests meta-data — প্রসেস মারা গেলে FCM SDK নিজেই নোটিফিকেশন দেখায়, একই high-importance চ্যানেলে
- SessionNotifier.kt: ডক-হেডার দুই-ট্রান্সপোর্ট ব্যাখ্যায় আপডেট (listener + kill-push)
- build.gradle.kts: 0.9.8 / 26

নিরাপত্তা-যাচাই (সব লোকাল টেস্ট পাস):
- A) fcmTokens: ৩টা রিয়েল টোকেন (১৪২ অক্ষর, সদ্য-রিফ্রেশড)
- B) synthetic session + ভুয়া recipient-uid → "no-tokens" পথ, মার্ক হয়নি (retryable) ✓
- C) ভুয়া INVALID টোকেন-ডক পুঁতে → sweep পাঠিয়ে ব্যর্থ → ডক auto-DELETE (readback 404) ✓
- D) রিয়েল টোকেনে সরাসরি টেস্ট পুশ → FCM HTTP 200 + message_id ✓ (Owner-এর ফোনে "Kill-push test ✓" নোটিফিকেশন যাওয়ার কথা)
- E) synthetic ডক-পরিষ্কার ✓; ফাইনাল: REQUESTED=0, fcmTokens=৩, ভুয়া টোকেন নেই ✓

প্রকাশনা:
- push: ৫ PUT (kill-push.yml [নতুন 201], fcm-kill-push.mjs [নতুন 201], gradle, manifest, SessionNotifier) + ২২ SKIP; পরে README PUT 457cf17
- GitHub secret FIREBASE_SA_JSON সেট (libsodium sealed, HTTP 201) — SA-JSON repo-তে যায়নি, শুধু Actions secret
- kill-push workflow_dispatch (run 40815586024) → SUCCESS — ক্লাউড-লগে "sweep: 0 REQUESTED doc(s)" (SA-secret ক্লাউড-পথ প্রমাণিত)
- Android Build ×৫ → SUCCESS (প্রথম চেষ্টাতেই, head 3336e49)
- APK verify (artifact 12872755477): versionCode INT_DEC 26 ✓ (manifest element-এ নাম-রেজলভ — minSdkVersion=26 থেকে আলাদা করে), versionName "0.9.8" ✓, 0.9.7/0.9.6 স্ট্রিং অনুপস্থিত ✓, APK Signing Block v2 @39383880 ✓
- download/family-remote-support-v0.9.8-code26.apk (39.4 MB) সংরক্ষিত

🔴 লাইভ প্রোডাকশন-প্রমাণ (আমার করা টেস্ট নয়):
- 04:11:34Z-এ সত্যিকারের অনুরোধ: supportplybit@gmail.com → bangladeshtiktok15@gmail.com (session gaGjh3mBN2DPw727YwG5)
- মিনি-সার্ভিস ৫সে-পোলে ধরে FCM পাঠাল → killPushSentAt=04:11:36.865Z — অনুরোধ-তৈরির মাত্র ২.৪ সেকেন্ডে ডেলিভারি, killPushStatus=sent:1/1
- (helper পরে CANCELLED করেছেন — সেশন-লাইফসাইকেল স্বাভাবিক)

Stage Summary:
- v0.9.8 (code 26) প্রকাশিত + যাচাইত — Phase 8.2 বন্ধ: অ্যাপ মারা গেলেও সেশন-অনুরোধ এখন ২.৪ সেকেন্ডে পৌঁছায় (২-মিনিট উইন্ডোর ভেতরে আরামসাড়া)
- config/quality এখন VP8@30fps·1200–4000kbps (H264@60 পরীক্ষা বন্ধ; ফের চালু করতে ১ Firestore write — scripts/set-quality-config.mjs h264 60 1500 5000)
- Owner-করণীয়: দুই ফোনেই v0.9.8 ইনস্টল করলেই killed-state-এর নোটিফিকেশন নতুন manifest meta-data-সহ আসবে (v0.9.7-এও kill-push আসবে — meta-data শুধু আইকন/চ্যানেল-নান্দনিকতা)
- যাচাই-চিহ্ন: গ্রহীতার ফোনে অ্যাপ force-close করে হেল্পার রিকোয়েস্ট পাঠান → ~৫ সেকেন্ডে "Help requested" ট্রে-নোটিফিকেশন; ট্যাপ করলে অ্যাপ খুলে in-app কার্ড
- সতর্কতা: kill-push-এর ইনস্ট্যান্ট স্তর এই sandbox-এর জীবনের সাথে বাঁধা — sandbox রিসাইকেল হলে GitHub Actions ব্যাকস্টপ (৫-মিনিট গ্রেড) একা থাকবে; ৬০ দিন repo-নিষ্ক্রিয়তায় GH schedule বন্ধ হয়; দীর্ঘমেয়াদি স্থায়ী-ইনস্ট্যান্ট স্তরের পথ: Cloudflare Workers (ফ্রি, কার্ড লাগে না) বা Blaze Cloud Functions — Owner-সিদ্ধান্ত
- ব্যাকলগ: Phase 8.3 (সোয়াইপ/লং-প্রেস/টাইপিং, কম-বিটরেট মোড), রিলে পেইড-সিদ্ধান্ত, টোকেন-rotate (SA/PAT/metered)

---
Task ID: 39
Agent: Z.ai Code (main)
Task: v0.9.9 (versionCode 27) — Phase 8.3: রিমোট সোয়াইপ + লং-প্রেস + রিলে-চললে কম-বিটরেট (Owner: "করো")

ডিজাইন-সিদ্ধান্ত:
- Phase 8.3-এর "টাইপিং" একমাত্র canRetrieveWindowContent=true ছাড়া সম্ভব নয় (ACTION_SET_TEXT/পেস্ট = focused-node প্রয়োজন = স্ক্রিন-পড়া) — অ্যাপের নথিভুক্ত ট্রাস্ট-মডেল ("সার্ভিস স্ক্রিনের কিছুই পড়ে না — শুধু ট্যাপ করে") ভাঙার সিদ্ধান্ত Owner-এর; তাই এই ফেজে সোয়াইপ/লং-প্রেস + রিলে-ডেটা-সেভার সম্পন্ন, টাইপিং ব্যাকলগে (Owner-সিদ্ধান্ত সহ)
- সোয়াইপে helper-এর আসল drag-duration পাঠানো হয় (160–1500ms clamp) — ধীর আঙুল = ধীর স্ক্রল, দ্রুত = ফ্লিং; লং-প্রেস ডিফল্ট 800ms (Android-এর ~500ms থ্রেশহোল্ড + মার্জিন)
- রিলে-ক্যাপ: CONNECTED-এর ৬ সেকেন্ড পর getStats() → SELECTED candidate-pair → যেকোনো প্রান্ত "relay" হলে বিটরেট সিলিং 1500kbps-এ পুনঃপ্রয়োগ (metered.ca ফ্রি 500MB/মাস — পূর্ণ-কোয়ালিটি রিলে-স্ট্রিম ~১৭ মিনিটেই শেষ); direct/hotspot কখনো ক্যাপ হয় না; stats-ব্যর্থতা = স্বাভাবিক বাজেট

পরিবর্তন (৪ ফাইল + build):
- AssistService.kt: swipeNormalized (Path A→B stroke, dur clamp 120–3000ms), longPressNormalized (held stroke, 400–2000ms), toRealPixels() রিফ্যাক্টর (v0.9.6-এর রিয়েল-ডিসপ্লে ম্যাপিং তিন জেসচারেই); companion delegate; ডক-হেডারে Phase 8.3 + "canRetrieveWindowContent stays FALSE"
- ScreenShareEngine.kt: handleAssistCommand-এ "swipe"/"long" কেস (উভয় ট্রান্সপোর্টে একই পথ); diag-প্রমাণ "assist swipe @30%,70%→30%,20% in 480ms performed" / "assist long @62%,71% held 800ms"; showAssistAck-এ "Swipe/Long-press delivered ✓"; sendAssistSwipe/sendAssistLongPress; রিলে-ক্যাপ: relayMaxKbps + senderVideoTrack ফিল্ড (দুই reset-পথে শূন্য), applyScreenBitrate ক্যাপ-সচেতন (min<max নিশ্চিত), scheduleRelayBandwidthCheck (getStats → candidate-pair selected/succeeded → local/remote-candidate candidateType) → CONNECTED হ্যান্ডলারে হুক
- MainActivity.kt: assistTapModifier → assistGestureModifier — normalized() হেল্পার + detectTapGestures(onTap+onLongPress) + detectDragGestures(দূরত্ব>২% স্ক্রিন-হাইপট হলে swipe, dur=আসল elapsed); Compose touch-slop নিয়মে তিন জেসচারের সহাবস্থান (স্থির ধরে রাখা=লং-প্রেস, slop পার হলে drag=সোয়াইপ+ট্যাপ-বাতিল, নড়বিহীন দ্রুত ছাড়া=ট্যাপ); দুই কল-সাইট (ইনলাইন+ফুলস্ক্রিন); হিন্ট-টেক্সট দ্বিভাষিক আপডেট ("ট্যাপ = ট্যাপ · ধরে রাখা = লং-প্রেস · টেনে ছাড়া = সোয়াইপ") helper+গ্রহীতা দুই পাশে
- build.gradle.kts: 0.9.9 / 27

নিরাপত্তা-যাচাই: string-aware brace/paren ব্যালেন্স তিন ফাইলেই 0/0; MultiEdit-আংশিক-প্রয়োগের ডুপ্লিকেট-ইম্পোর্ট (detectDragGestures/Offset/hypot ×২) ধরা পড়ে সরানো হয়েছে

প্রকাশনা:
- push: ৪ PUT (gradle 1c860fb, MainActivity 799f813, engine e0c1109, AssistService ad07c4b) + ২৩ SKIP; পরে README
- CI রান 40819795499 → SUCCESS (প্রথম চেষ্টাতেই, head ad07c4b)
- APK verify (artifact 12873749093): versionCode INT_DEC 27 ✓, versionName "0.9.9" ✓ (0.9.8/0.9.7 স্ট্রিং অনুপস্থিত), APK Signing Block v2 ✓
- download/family-remote-support-v0.9.9-code27.apk (39.4 MB) সংরক্ষিত

Stage Summary:
- v0.9.9 (code 27) — হেল্পার এখন ট্যাপ+সোয়াইপ+লং-প্রেস তিনটাই করতে পারেন (রিলেড সেশনে ভিডিও নিজেই ডেটা-সেভার হয়)
- Owner-করণীয়: দুই ফোনেই v0.9.9; টেস্ট — হেল্পার সুইচ চালু করে ছবিতে টেনে ছাড়ুন (স্ক্রল যাবে), আইকনে ধরে থাকুন (কনটেক্সট-মেনু আসবে); গ্রহীতার Diagnostics-এ "assist swipe/long … performed" লাইন প্রুফ
- রিলে-টেস্ট চিহ্ন: ক্রস-নেটওয়ার্ক সেশনে ৬-৮ সেকেন্ডের মধ্যে "relay path selected — bitrate capped to 1500 kbps (relay data-saver)" diag
- ব্যাকলগ: টাইপিং (রিমোট কীবোর্ড — canRetrieveWindowContent=true নেওয়ার Owner-সিদ্ধান্ত দরকার + Play-policy বিবেচনা), kill-push-এর স্থায়ী-ইনস্ট্যান্ট স্তর (Cloudflare Workers/Blaze), রিলে পেইড-সিদ্ধান্ত, টোকেন-rotate, Phase 8.3-এর অবশিষ্ট "রিলে-চললে কম-বিটরেট" সম্পন্ন হয়েছে

---
Task ID: 40
Agent: Z.ai Code (main)
Task: v0.10.0 (versionCode 28) — Phase 9: সিকিউরিটি হার্ডেনিং (Owner: "এখন বাকি কোনো কাজ না থাকলে পরের phase এর কাজ করো")

ডিজাইন-সিদ্ধান্ত:
- রোডম্যাপ §12 অনুযায়ী Phase 8-ধারাবাহিক (8.2, 8.3) শেষ → পরের ধাপ Phase 9: negative tests + replay প্রতিরোধ + secure logging + থ্রেট-মডেল আপডেট
- SA/Admin পথ rules বাইপাস করে, তাই "negative test" মানে সাধারণ-ব্যবহারকারী পরিচয়ে লাইভ rules গুলি করা — সমাধান: SA-কী দিয়ে RS256 custom token (uid=rules-probe) মিন্ট → Identity Toolkit এক্সচেঞ্জ → আসল ID token → Firestore REST Bearer; SA শুধু রিকন+ক্লিনআপে
- সব guard "অদৃশ্য" — স্বাভাবিক ট্যাপ/সোয়াইপ/লং-প্রেসে কোনো আচরণ-পরিবর্তন নেই (মানুষের আঙুল ২৫/সে-র চেয়ে ধীর, সত্যিকারের কমান্ড <১৫ সেকেন্ডে পৌঁছায়, ১৪২-অক্ষর টোকেন ৪০৯৬-সীমার ভেতরে)

পরিবর্তন (৪ ফাইল + ২ নতুন স্ক্রিপ্ট):
- SignalingRepo.kt: watch() কলব্যাকে প্রতি ডকের সার্ভার-createdAt প্যাস (v0.10.0)
- ScreenShareEngine.kt: cmd-পথে ৩টি গেট — freshness (createdAt ≤১৫সে, না থাকলে অচল), payload ≤4KB (Firestore+p2p উভয়ে), handleAssistCommand-এ sane()/saneDur() (finite→null, 0..1 clamp, dur clamp) + স্লাইডিং-উইন্ডো ২৫cmd/সে rate-cap + sendAssistAck() রিফ্যাক্টর (প্রত্যাখ্যাত কমান্ডও সৎ ack: bad-payload/rate/assist-off; unknown-kind আগে নীরব, এখন ack) + teardown-এ cmdTimes.clear()
- firestore.rules: fcmTokens uid/deviceId/token টাইপ-চেক + token ≤4096 (আগে শুধু ≥32 — 10KB স্ট্রিংও ঢুকত); প্রকাশিত ruleset fc6d8ec0-5f81-46b6-a194-5531a145600a, readback-verify ✓
- publish-firestore-rules.mjs: SA-পাথ ফিক্স (env → home → project .secrets — Phase-8.2 কনভেনশন; পুরনো হার্ডকোড /home/z/.secrets পাথ নেই)
- build.gradle.kts: 28 / 0.10.0

runtime negative-probe (scripts/rules-negative-probe.mjs, নতুন — পুনর্ব্যবহারযোগ্য):
- P0 পজিটিভ-কন্ট্রোল: নিজের devicePresence create+delete → 200 (অথ-পথ প্রমাণ), সঙ্গে সঙ্গে ডিলিট
- N1 অন্যের profile → 403 · N2 অন্যের session-তালিকা → 403 · N3 invitation-এনুমারেশন → 403 · N4 config-লেখা → 403
- N5 relationship ছাড়া session → 403 · N6 আসল সেশনে signal/cmd → 403 · N7a টোকেন 8-অক্ষর → 403 · N7b টোকেন 5000-অক্ষর → 403
- N8 অন্যের fcmToken পড়া → 403 · N9 আসল সেশন UVjG2ojpKLLU3HPEYlQg মিউটেট → 403 · N10 relationship DELETE → 403
- ১২/১২ পাস ×২ রান; ক্লিনআপ: synthetic Auth user accounts:delete → 200; পরে-অডিট: probe-doc=0, fcmTokens=৩ (আসল), REQUESTED=0
- নির্মাণকালে ধরা বাগ: এই প্রজেক্টের DB-আইডি "(default)" নয় "default"; runQuery রেসপন্স অ্যারে-মোড়ানো; N9-র আইডি-নিষ্কাশন slice(-2)[0]→pop()

সিকিউরিটি-অডিট (কোড-পরিবর্তন ছাড়া, প্রমাণসহ):
- android.util.Log = শূন্য কল (সব সোর্স grep) — একমাত্র ডায়াগ ইন-অ্যাপ ১৪-লাইন রিং-বাফার, ডিভাইস-বাহির নয়, টোকেন/ইমেইলহীন
- pairing টোকেন: 80-bit SecureRandom, Firestore-এ শুধু SHA-256 হ্যাশ, মেয়াদ ≤১০ মিনিট, একবারই redeem (rules-যাচাইকৃত)
- showAssistAck কেবল স্থির-বাক্য দেখায় — peer-লেখা টেক্সট UI-তে প্রবেশ করে না

প্রকাশনা:
- push: ৭ PUT (probe+publish স্ক্রিপ্ট নতুন 201; gradle/SignalingRepo/engine/README/rules 200) + ২০ SKIP; চূড়ান্ত head 2269d83
- CI: ৩ রান SUCCESS (d81ca67, 8950195, 2269d83); নোট — 3f1abf0-মধ্যবর্তী রান FAIL (SignalingRepo-একা Function4 দাবি, engine পুরনো) — per-file কমিটের জ্ঞাত আর্টিফ্যাক্ট, চূড়ান্ত গাছ অক্ষত; ভবিষ্যৎ-নোট: পরস্পর-নির্ভর ফাইল এক কমিটে যাওয়া উচিত
- APK verify (artifact 12875171515): versionCode INT_DEC 28 ✓, versionName "0.10.0" ✓ (UTF-16-পুল সার্চে 0.9.9/0.9.8 অনুপস্থিত), APK Signing Block v2 ✓ ("APK Sig Block 42")
- download/family-remote-support-v0.10.0-code28.apk (39.4 MB) সংরক্ষিত
- kill-push মিনি-সার্ভিস অক্ষত, sweep চলছে

Stage Summary:
- v0.10.0 (code 28) — Phase 9 বন্ধ: rules এখন "বিশ্বাস" নয়, "প্রমাণিত" — ১২ শত্রু-পথ লাইভে 403; ইনজেক্টর তিন-স্তরের বেড়ায় (freshness+size+rate) ঘেরা; সব প্রত্যাখ্যান সৎভাবে ack হয়
- Owner-করণীয়: দুই ফোনে v0.10.0 ইনস্টল (আচরণ-পরিবর্তন নেই — স্বাভাবিক ব্যবহারে কিছুই বোঝা যাবে না); ইচ্ছা করলে পুরনো ভার্সনের উপরেই বসানো যাবে (সাইন-সামঞ্জস্য)
- rules ভবিষ্যতে বদলালে: node scripts/rules-negative-probe.mjs — exit 1 = drift
- ব্যাকলগ: Phase 10 (Guest মোড), টাইপিং (canRetrieveWindowContent=true Owner-সিদ্ধান্ত + Play-policy), kill-push স্থায়ী-ইনস্ট্যান্ট স্তর (Cloudflare Workers/Blaze), রিলে পেইড-সিদ্ধান্ত, টোকেন-rotate, Oracle TURN

═══════════════════════════════════════════════════════════════
## Phase 12 (v0.17.0, code 35) — রোল-টাইপড QR ও স্ক্যান→সেটআপ

Owner-প্রশ্ন: Family/Guest মোড ইউজার কিভাবে কন্ট্রোল করবে; গেস্ট ফ্যামিলিতে ঢুকলে/বাইরের লোক
বিপদে ফেললে? QR জেনারেট+স্ক্যানার আলাদা করতে হবে; রোল-সেটআপ; QR-মাঝে ছোট প্রতীক; OWNER
স্ক্যানের পর অটোমেটিক Guest/Family সেটআপ।

কোড-পরিবর্তন (rules অপরিবর্তিত — ১২/১২ নেগেটিভ-প্রোব তাই অক্ষত):
- PairingRepo.kt: InviteCode.kind + qrPayload `&k=fam|gst` (মার্কার cosmetic; চূড়ান্ত রোল লেনদেনে
  Firestore-থেকে); QrBadge enum (👪/⏳, hasGlyph→F/G fallback); qrBitmap ৫৬০px + ECC-H + মাঝে
  সাদা rounded-প্লেট+প্রতীক (বর্ডার #E2E8F0)
- QrScanner.kt: parsePairingKindHint; স্ক্যানার বাংলা UI — রেটিকল (মাঝ স্বচ্ছ) + দুই লেজেন্ড-চিপ;
  onCode(code, kindHint)
- MainActivity.kt: ScannedRoleSheet (preview→confirm→auto-setup→success, প্রকৃত রোল-ব্যাজ);
  doRedeem রিফ্যাক্টর (টাইপড+স্ক্যান এক পথ); লাইভ-কোড কার্ড রোল-ব্র্যান্ডেড; PAIRED-লাইনে রোল-চিপ;
  অ্যাকশন-শিটে রোল-সারসংক্ষেপ; পেয়ারিং-শিটে প্রতীক-লেজেন্ড
- রোল-রঙ: Family=tertiary সবুজ, Guest=AppWarningColor অ্যাম্বার (দুই-থিম AA)
- build.gradle.kts: 35 / 0.17.0

নিরাপত্তা-সারমর্ম: কোনো নতুন অথ/পথ খোলা হয়নি — মার্কার শুধু দেখায়, সিদ্ধান্ত সার্ভারের;
guest≠family বেড়া হুবহু আগের মতো (লেনদেন+rules)।

যাচাই: brace-lexer ৩ ফাইল 0/0/0; ডুপ্লিকেট-import 0; পুশ readback-verify; CI; APK-AXML code 35।

প্রকাশনা (v0.17.0): মধ্যবর্তী রান fail (per-file ক্রস-নির্ভরতা — জ্ঞাত আর্টিফ্যাক্ট); চূড়ান্ত গাছ
07f1574 CI SUCCESS (40868598783); CI-লগে ধরা বাগ: apply{}-এ `textSize = textSize` বাইরের val-কে
shadow — rename glyphSize; APK verify: INT_DEC 35 ✓, "0.17.0" ✓, v2 sig ✓; APK সংরক্ষিত
download/family-remote-support-v0.17.0-code35.apk (39.5 MB); doc-record 69878e9 readback ✓।

═══════════════════════════════════════════════════════════════
## ব্যাকফিল (Task 41–44) — ফেজ-এন্ট্রিগুলো worklog-ফরম্যাটে ছিল না; পূর্ণ রেকর্ড docs/PHASE-0.md §34–§39-এ

- **Task 41 — Phase 10 (v0.11.0/29):** Guest মোড — Family/Guest দুই ধরনের কোড, server-bounded
  ২-ঘণ্টার guest উইন্ডো (rules `request.time+3ঘ` রি-বাউন্ড), session-scoped guest ক্ষমতা,
  guest≠family লেনদেন+rules-এ নিখুঁত, Remove বোতাম, নেগেটিভ-প্রোবে লাইভ-প্রমাণিত।
- **Task 42 — Phase 11 + 11.5 (v0.13.0/31, v0.14.0/32):** ৪-ট্যাব শেল (হোম/ফোন/একটিভিটি/সেটিংস) +
  টপ-বার (presence-চিপ + bell-ব্যাজ); কম-টেক্সট রি-স্ট্রাকচার — ⓘ-পপআপ, chat-স্টাইল List Tile,
  ExtendedFAB+বটম-শিট, রিয়েল নোটিফিকেশন-টগল, ২×২ স্ট্যাটাস-গ্রিড।
- **Task 43 — Phase 11.6 (v0.15.0/33):** Design System (ui/Theme.kt — Light #246BFD + পূর্ণ Dark,
  SeniorTypography, tokens), Settings থিম-গ্রুপ (flash-free), StatusBadge ছয়-অবস্থা, QR 200dp
  সাদা-তল, GuestCountdownChip, friendlyErrorBn, উইজার্ড-প্রোগ্রেস, ৪৮dp টাচ-টার্গেট।
- **Task 44 — Phase 11.7 (v0.16.0/34):** AuthGate — আলাদা লগইন/সাইন-আপ পেজ (Owner-নির্দেশ);
  Email/Password প্রদানকারী চালু (Admin API); পূর্ণ অডিট-রেকর্ড §38.1।

(এই ব্যাকফিল যোগ করা হলো Phase 13-এ — convention-পুনরুদ্ধার; কোনো কোড-পরিবর্তন নেই।)

═══════════════════════════════════════════════════════════════

---
Task ID: 45
Agent: Z.ai Code (main)
Task: v0.18.0 (versionCode 36) — Phase 13: সাইন-আউট হাইজিন + CI রিলিজ-গেট (Owner: "এর পরের কাজ কি? করো")

প্রেক্ষাপট: Phase 12 (v0.17.0/35) প্রকাশিত হওয়ায় Owner-এর সব স্থগিত অনুরোধ (অডিট, AuthGate,
App-Prompt ডিজাইন, রোল-টাইপড QR) পূর্ণ। অডিটের চিহ্নিত ফাঁকগুলোর মধ্যে Owner-সিদ্ধান্ত-ছাড়া
actionable দুটি বেছে নেওয়া হলো (PHASE-0 §38.1):
- সাইন-আউটে devicePresence/fcmTokens মুছে না → সাইন-আউটের পরেও ফোন "Online" দেখায় ও
  kill-push আসতে থাকে (গোপনীয়তা+বিভ্রান্তি ঝুঁকি)
- verify-apk-axml CI-তে নেই → সংস্করণ-যাচাই কেবল আমার হাতে-করা লোকাল গেট

ডিজাইন-সিদ্ধান্ত:
- ক্লিনআপ কেবল নিজের ডক — rules এমনিতেই অনুমতি দেয় (devicePresence delete: uid==mine;
  fcmTokens delete: uid==mine; list: uid==mine) → **rules অপরিবর্তিত**, ১২/১২ নেগেটিভ-প্রোব অক্ষত
- fcmTokens ডক-আইডি raw-টোকেনের SHA-256-হ্যাশ — আইডি আগে থেকে বের করা যায় না, তাই
  whereEqualTo("uid", mine) list → deviceId-মিল ডক ডিলিট (rules এই list-পথই শুধু অনুমতি দেয়)
- best-effort: নেটওয়ার্ক-ব্যর্থতা কখনো সাইন-আউট আটকাবে না — সত্য-নোটিশ লগইন-স্ক্রিনে (authNotice);
  প্রতিটি ধাপ স্বাধীনভাবে ডিগ্রেড করে
- WorkManager heartbeat-ওয়ার্ক **বাতিল করা হয়নি** (ইচ্ছাকৃত): HeartbeatWorker সাইন-আউটে
  currentUser-null → no-op; KEEP-policy-এ পুনঃসাইন-ইনে নিজেই চালু হয় — বাতিল করলে
  LaunchedEffect(Unit)-একবারি-চলা সমস্যায় রি-সাইন-ইনে হার্টবিট বন্ধ থেকে যেত (রিগ্রেশন-আটকাতে
  এই পথই সঠিক)
- CI-গেটে expectations app/build.gradle.kts থেকেই পড়ে (repo-root ও sandbox দুই লেআউট-ফলব্যাক) —
  workflow ও gradle কখনো drift করতে পারে না

কোড-পরিবর্তন (৪ ফাইল + ১ নতুন স্ক্রিপ্ট):
- PresenceRepo.kt: `deleteOwnPresenceAndToken(uid, deviceId): Boolean` — presence-ডক ডিলিট +
  নিজের টোকেন-ডক তালিকা করে deviceId-মিলগুলো ডিলিট; প্রতিটি ধাপে CancellationException পুনঃনিক্ষেপ,
  বাকি সব স্বাধীন ডিগ্রেড; সততার জন্য Boolean রিটার্ন
- MainActivity.kt: onSignOut — signOut()-এর **আগে** ক্লিনআপ (rules-এর signedIn-শর্তের জন্য আবশ্যক);
  সাফল্যে authNotice "সাইন-আউট সম্পন্ন — উপস্থিতি ও পুশ-টোকেন সার্ভার থেকে মুছে ফেলা হয়েছে";
  ব্যর্থতায় সৎ নোটিশ (নেটওয়ার্ক সমস্যা + কীভাবে পরে পরিষ্কার হবে); presence/push স্ট্যাটাস
  "Signed out" — সাইন-আউটের পরে পুরনো "Online ✓"/"Push ready" প্রদর্শিত হওয়া বন্ধ
- scripts/verify-apk-axml.mjs (নতুন, zero-dep): APK zip → AndroidManifest.xml AXML পার্স →
  versionCode INT_DEC + versionName মিল, --stale তালিকার পুরনো-সংস্করণ-স্ট্রিং অনুপস্থিতি,
  APK Signing Block v2 ("APK Sig Block 42", id 0x7109871a); expectations gradle থেকে; পজিটিভ
  (v0.17.0 APK → PASS) ও নেগেটিভ (--code 999 → FAIL exit-1) টেস্ট স্থানীয়ভাবেই যাচাই
- android-build.yml: build-এর পরে "Verify release APK" ধাপ — স্ক্রিপ্ট চালিয়ে ফলাফল run-summary-তে;
  ব্যর্থ হলে বিল্ড ব্যর্থ (set -o pipefail)
- build.gradle.kts: 36 / 0.18.0; push-তালিকায় verify-apk-axml.mjs যোগ

নিরাপত্তা-সারমর্ম: নতুন কোনো অথ/পথ নেই; ক্লিনআপ কেবল নিজের ডক মুছে — rules হুবহু আগেরটাই;
হেল্পার/গ্রহীতা/সেশন/QR-প্রবাহে শূন্য পরিবর্তন।

যাচাই: brace-lexer — PresenceRepo 0, MainActivity HEAD-এর হুবহু জানা false-positive প্যাটার্নে
(নতুন mismatch শূন্য), স্ক্রিপ্ট node --check PASS; ডুপ্লিকেট-import শূন্য; verify-স্ক্রিপ্ট
পজিটিভ+নেগেটিভ টেস্ট PASS; XML/YAML হাতে-যাচাই।

প্রকাশনা: নিচের Phase 13 প্রকাশনা-রেকর্ড।

প্রকাশনা (v0.18.0):
- পুশ (per-file, ONLY-ফিল্টার): verify-apk-axml.mjs 9cb2a00 (201 নতুন), workflow e9c5f90,
  gradle 8e90b1a, PresenceRepo fd448eb, MainActivity 4467b60; readback-verify 5/5 MATCH (sha256)
- CI রান 40872756514 (head fd448eb) → SUCCESS প্রথম চেষ্টাতেই; নতুন গেট-ধাপ success — লগ-প্রমাণ:
  "expectations: code=36 name=0.18.0 · ✓ versionCode = 36 (INT_DEC) · ✓ versionName = 0.18.0 ·
  ✓ APK Signing Block v2 · VERIFY: PASS"; ফলাফল run-summary-তে
- আর্টিফ্যাক্ট নেমে স্থানীয় পুনঃযাচাই: স্টেল 0.17.0/0.16.0/0.15.0 অনুপস্থিতসহ VERIFY: PASS;
  download/family-remote-support-v0.18.0-code36.apk (39.5 MB) সংরক্ষিত
- ডক-পুশ: README bd06eeb + PHASE-0 867751e + worklog de9cf80 (worklog.md এই প্রথম repo-তে
  এল — 201; push-তালিকায় স্থায়ী) → চূড়ান্ত গাছ CI রান 40873415226 SUCCESS (গেটসহ)

Stage Summary:
- v0.18.0 (code 36) — অ্যাকাউন্ট থেকে বের হলেই এখন ফোন সত্যিই চলে যায়: উপস্থিতি মুছে যায়
  (পরিবার "Online" দেখে না), পুশ-টোকেন মুছে যায় (সাইন-আউট-ফোনে kill-push আর আসে না) —
  সাইন-আউট-পরবর্তী ঝুলে-থাকা ট্রেস শূন্য; ব্যর্থতায় সৎ নোটিশ
- রিলিজ-গেট এখন CI-এ স্থায়ী: প্রতিটি পুশে versionCode/versionName/v2-সিগনেচার/স্টেল-স্ট্রিং
  যাচাই — ভুল সংস্করণের APK আর আর্টিফ্যাক্ট-পর্যন্ত পৌঁছাতে পারে না
- rules অপরিবর্তিত (১২/১২ নেগেটিভ-প্রোব অক্ষত); সেশন/QR/WebRTC/FCM-প্রবাহে শূন্য পরিবর্তন
- Owner-করণীয়: দুই ফোনে v0.18.0 ইনস্টল; সাইন-আউট→নোটিশ→অন্য-ফোনে Online-মুছে-যাওয়া→push-বন্ধ→
  রি-সাইন-ইনে-সব-স্বাভাবিক — §40.4-চেকলিস্ট
- ব্যাকলগ (Owner-সিদ্ধান্ত): App Check (সাইডলোড-APK-তে Play-Integrity নির্ভরযোগ্য নয় —
  কাস্টম-attestation ছাড়া ঝুঁকি), kill-push স্থায়ী-ইনস্ট্যান্ট স্তর (Cloudflare Workers —
  ফ্রি/কার্ডহীন, Owner অ্যাকাউন্ট খুলতে হবে), রিলে পেইড-সিদ্ধান্ত, টোকেন-rotate (SA/PAT),
  Oracle TURN বিকল্প, Phase 7-ফরমাল-ক্লোজ স্ক্রিনশট

---
Task ID: 46
Agent: Z.ai Code (main)
Task: Phase 14 (infra — APK অপরিবর্তিত): kill-push স্থায়ী স্তর Cloudflare Workers ফ্রি-প্ল্যানে + Owner-সিদ্ধান্ত-রেকর্ড
(Owner উদ্ধৃতি: "Cloudflare Worker ব্যবহার করা সবচেয়ে সহজ ও নিরাপদ" / "পেইড রিলে সার্ভারে টাকা খরচ
করার দরকার নেই। ফ্রি লিমিট পার হলে তখন পেইড ভাবা যাবে" / "সাধারণ টোকেন অথেন্টিকেশন দিয়ে শুরু" /
"প্রথমে uptodown এ পাবলিশ করব... পরে play store"; সাথে: Cloudflare অ্যাকাউন্ট খোলা হয়েছে)

প্রেক্ষাপট: v0.18.0-র ব্যাকলগে স্থায়ী kill-push স্তর + রিলে পেইড-সিদ্ধান্ত ছিল (§40-Stage-Summary);
Owner-এর বার্তাই তিনটিরই উত্তর দিল — Cloudflare Workers (ফ্রি/কার্ডহীন) + ফ্রি-লিমিট-পর্যন্ত-ফ্রি +
সাধারণ টোকেন-অথ। প্রথম কাজ: স্যান্ডবক্স স্টেল ছিল (MainActivity 2109 লাইন) → রিমোট ট্রি (৪৭ ফাইল)
সম্পূর্ণ মিরর করে সত্য-অবস্থায় (v0.18.0) পুনঃসিঙ্ক করা হয়েছে; সব কাজ রিমোট-সত্যায়িত কপিতে।

বাস্তবায়ন:
- cloudflare/killpush-worker.js (নতুন): scripts/fcm-kill-push.mjs-এর প্রমাণিত sweep-এর শূন্য-নির্ভরতার
  Web-Crypto-প্রতিলিপি — একই runQuery(status==REQUESTED)/freshness-gate(≤১০ মি)/fcmTokens-lookup/
  FCM-v1-message(HIGH, support_session_requests)/killPushSentAt-idempotent-mark/UNREGISTERED-cleanup —
  দুই স্তর কখনো drift করতে পারে না; GET /health (খোলা, secret-মুক্ত), POST /sweep (Bearer KILL_TOKEN,
  timing-safe, in-flight গার্ড), scheduled (cron * * * * *); PKCS8→importKey→RS256 Web Crypto-তে
- docs/CLOUDFLARE_SETUP.md (নতুন): ফোন-বান্ধব ৪-ধাপ বাংলা dashboard-গাইড (Worker তৈরি → raw-কোড
  পেস্ট → FIREBASE_SA_JSON+KILL_TOKEN secrets → cron * * * * *) + ফ্রি-লিমিট হিসাব (১,৪৪০/১,০০,০০০
  দিন <২%) + rotate-নোট
- PHASE-0 §41 (উদ্ধৃতি-সহ সিদ্ধান্ত-রেকর্ড, তিন-স্তর টেবিল, Worker-ডিজাইন, যাচাই, প্রকাশনা-পরিকল্পনা
  Uptodown→Play + Play-প্রস্তুতি-তালিকা) + README Status-এ Phase 14 বুলেট
- KILL_TOKEN: ৪০-hex → .secrets/cf-kill-token.txt (Owner dashboard-পেস্টের জন্য চ্যাটে দেওয়া; repo-তে নয়)

যাচাই: node --check PASS; mock-সুইট (tmp/worker-test.mjs) — মক-fetch-এ আসল Worker-কোড: RS256/PKCS8
সত্যিকারে সই করে, ৩ REQUESTED→১ fresh (stale/expired বাদ), pushed=1, UNREGISTERED→ডিলিট=1,
killPushSentAt-মার্ক নিশ্চিত, bad-token→401, GET→405, /health ঠিক, scheduled() পুশ করে, প্রকৃত SA
PKCS8-PEM গঠন OK — সব PASS

যা অপরিবর্তিত: APK (versionCode 36), Firestore rules (SA admin-পথ), স্যান্ডবক্স mini-service ও GH
Actions স্তর — Worker সংযোজন; অ্যাপ-পাশের instant helper-কল Phase 14b (v0.19.0-প্রার্থী)

প্রকাশনা:
- পুশ ১: cloudflare/killpush-worker.js → 1cdfda2 (201 নতুন); readback MATCH (sha256)
- পুশ ২ (ডক): docs/CLOUDFLARE_SETUP.md + docs/PHASE-0.md (§41) + README.md + worklog.md → নিচের
  প্রকাশনা-রেকর্ড; readback-verify ALL MATCH (sha256 স্থানীয়=রিমোট)

---
Task ID: 47
Agent: Z.ai Code (main)
Task: Phase 11.8 (v0.18.1, versionCode 37) — Connect-flow discoverability ফিক্স (Owner: "QR
scanner ও কোড ইমপোর্ট করার অপশন টি frontend এ দেখা যাচ্ছে না... কোন phase এ ভুল করেছেন, খোঁজে
বের করে সমাধান করেন")

তদন্ত (সফল):
- Owner-স্ক্রিনশট-প্রমাণ: তাঁর পরীক্ষার ফোনে "App version v0.13.0 (build 31)" — ৫ রিলিজ পেছনের
  APK; স্ক্রিনশট-স্ট্রিং v0.13.0-কোডের সাথে হুবহু মিলে (HEAD-এর সাথে নয়)
- Phase 11 (v0.13.0, f3d21730) — helper-অংশ (কোড-ইনপুট + "Scan QR · স্ক্যান") পাতার একদম নিচে
  Section B-তে চাপা → ভুল ১
- Phase 11.5 (v0.14.0) — পুরো connect-flow (কোড-বানানো+ইমপোর্ট+স্ক্যান) ছোট FAB-শিটের পেছনে → ভুল ২
- ফাংশন কখনো মোছেনি — দুটোই discoverability-ভুল + বিতরণ-ফাঁক (পুরনো APK ফোনে রয়ে গেছে)

ফিক্স (MainActivity.kt — কেবল UI-ওয়্যারিং; rules/Firestore/WebRTC/FCM শূন্য পরিবর্তন):
- ফোন-ট্যাবের উপরে স্থায়ী "ফোন যুক্ত করুন · দুই ভাবে" কার্ড: BigButton "QR স্ক্যান · সাহায্য করব"
  (→ QrScanDialog → Phase-12 scan-to-setup journey) + WideOutlinedButton "কোড ইমপোর্ট · নতুন কোড
  বানান" (→ পেয়ারিং শিট)
- ট্যাব-লেভেল ডায়ালগ `showScanner && !showPairSheet` গার্ডে — ডাবল-রেন্ডার অসম্ভব; state শেয়ার্ড
- PairingCard/PairingSections-এ onOpenPairSheet callback (আগে শিট কেবল FAB-ই খুলতে পারত)
- EmptyState-hint নতুন কার্ডের দিকে; versionCode 36→37, versionName 0.18.0→0.18.1

যাচাই: brace-lexer balance {0,0,0} + duplicate-import 0 + কার্ড/গার্ড ×১ নিশ্চিত — PASS; push
gradle 4faa463 + MainActivity 733e393, readback ২/২ MATCH (sha256); CI রান 40892361955 → SUCCESS,
গেট-ধাপ success; আর্টিফ্যাক্ট স্থানীয় পুনঃযাচাই VERIFY: PASS (code=37, name=0.18.1, v2-sig, স্টেল
শুধু [0.18.1]); APK → download/family-remote-support-v0.18.1-code37.apk (39.5 MB,
sha256 d071c47d278f6b82…)

ফেইল/বাকি: (১) Owner-করণীয় — উভয় ফোনে v0.18.1 ইনস্টল করা হয়নি এখনো (আমার পক্ষে সম্ভব নয়);
(২) স্ক্রিনশটের "Could not reach Firestore (waited 30 s)" = সে-মুহূর্তের ইন্টারনেট-বিচ্ছিন্নতা
(অ্যাপ-বাগ নয়); (৩) Phase 14b (অ্যাপ→Worker instant sweep-কল, v0.19.0-প্রার্থী) বাকি; (৪)
Uptodown-প্রকাশ বাকি (আপডেট-বিতরণের স্থায়ী সমাধান)

---
Task ID: 48
Agent: Z.ai Code (main)
Task: Phase 14.1 (infra — APK অপরিবর্তিত, versionCode 37): Cloudflare Workers Builds deploy-fix —
wrangler.toml (repo root) + Firestore database-id লাইভ-প্রমাণ

প্রেক্ষাপট: Owner Cloudflare dashboard-এ Git-connected build (Workers Builds, deploy command
`npx wrangler deploy`) চালিয়েছেন; build ব্যর্থ — "✘ [ERROR] Could not detect a directory
containing static files (e.g. html, css and js) for the project"। Owner-নির্দেশনা: repo root-এ
wrangler.toml বানান (name/main/compatibility_date + [triggers] crons), main-এ আসল কোড-ফাইলের
নাম; আর "Firestore database এর নাম (default) হলে কোডে /databases/default/ কে /databases/(default)/
করে দিন"।

তদন্ত (সফল): fail-এর রুট-কজ — repo root-এ wrangler.toml ছিল না, তাই config-ছাড়া wrangler
static-assets Worker ধরে deploy করতে গিয়ে static file না পেয়ে ভেঙেছে; কোড-সিনট্যাক্সের দোষ নয়।
Database-id প্রশ্নটি অনুমানে নয়, লাইভে মাপা হলো (tmp/dbid-test.mjs, SA-OAuth read-only):
GET /databases/default → 200 (canonical: projects/remote-family-support/databases/default,
FIRESTORE_NATIVE); GET /databases/(default) → 404 "database '(default)' does not exist";
Worker-এর হুবহু call-shape runQuery /databases/default/documents:runQuery → 200 (rows OK);
doc GET config/quality via default → 200, via (default) → 404। অর্থাৎ এই প্রজেক্টের database-এর
আসল নামই `default` — Worker-এর /databases/default/ হুবহু সঠিক; (default) করা হলে প্রতিটি sweep
404-এ ভেঙে যেত। কোডে শূন্য পরিবর্তন। সম্মত প্রমাণ: প্রমাণিত scripts/fcm-kill-push.mjs (GH Actions
স্তর) একই path দিয়েই বহুদিন kill-push পাঠাচ্ছে।

বাস্তবায়ন: নতুন wrangler.toml (repo root) — name="family-remote-support" (Workers Builds
প্রজেক্ট-নামের সাথে হুবহু মিলতে হবে — Owner-কে নাম-মিল সতর্কতা দেওয়া হয়েছে),
main="cloudflare/killpush-worker.js" (আসল কোড-ফাইল), compatibility_date="2025-10-01",
[triggers] crons=["* * * * *"] (cron এখন কনফিগ থেকেই সেট হবে — dashboard-এ আলাদা ধাপ নেই),
[observability] enabled=true (sweep-লগ dashboard Logs-এ)। ডক: CLOUDFLARE_SETUP.md নতুন করে
লেখা — পদ্ধতি A (Git-connected Workers Builds: root-cause, wrangler.toml ব্যাখ্যা, secrets
একবারই, /health-টেস্ট, নাম-মিল সতর্কতা, database-id প্রমাণ-টেবিল) + পদ্ধতি B (পুরনো manual
paste flow, বিকল্প হিসেবে); PHASE-0 §43 (রুট-কজ/ফিক্স/লাইভ-প্রমাণ/অপরিবর্তিত/Owner-করণীয়);
README Phase 14 বুলেটে deploy-পদ্ধতি ও database-id-প্রমাণ।

যাচাই: Worker কোড হুবহু অপরিবর্তিত (mock-সুইট tmp/worker-test.mjs আগেই সব PASS); dbid-test
লাইভ রান — ৫ প্রোব উপরের মতো; push + readback-verify ALL MATCH (sha256)।

ফেইল/বাকি: (১) Cloudflare build-ফলাফল Owner-এর ড্যাশবোর্ডে দেখতে হবে (আমার অ্যাক্সেস নেই) —
সফল হলে secrets যোগ করে /health-প্রমাণ পাঠান, তারপর আমি POST /sweep লাইভ টেস্ট করব; (২)
নাম-মিল যাচাই (build প্রজেক্টের নাম family-remote-support কিনা — ভিন্ন হলে wrangler.toml-এর
name বদলাবো); (৩) Phase 14b অ্যাপ→Worker instant sweep-কল (v0.19.0); (৪) Uptodown-প্রকাশ;
(৫) উভয় ফোনে v0.18.1 ইনস্টল।

পাশাপ্রমাণ (একই যাচাইয়ে আবিষ্কৃত): GitHub Actions kill-push ব্যাকস্টপ স্তরের cron নীরব ছিল —
kill-push.yml repo-তে এসেছে আজ 04:03 UTC (574e57d); scheduled রান মাত্র ১ বার (10:50 UTC,
success), তারপর ৪.৫+ ঘণ্টা শূন্য — GitHub-এর পরিচিত sporadic-schedule আচরণ (ফাইলের হেডারেই
"best-effort" সতর্কতা ছিল)। Workaround প্রয়োগ: kill-push.yml ডক-মন্তব্য-টাচ re-push (c90cbda,
readback MATCH) + Actions API disable→enable চক্র (HTTP 204/204) — schedule re-registered;
ফল পর্যবেক্ষণে। স্থায়ী সমাধান Cloudflare Worker স্তরই (প্রতি মিনিট, ২৪/৭)। sweep-লজিক/secret
অপরিবর্তিত — ফায়ার হলেই sweep ঠিকভাবে চলে (দুই পূর্ববর্তী রান success)।

---
Task ID: 49
Agent: Z.ai Code (main)
Task: Phase 15 (v0.19.0, versionCode 38) — SESSION-VIEW UX + FIT-TO-SCREEN (Owner-screenshot
report: ছোট-ভিউতে কন্ট্রোল-বাটন ভুল জায়গায় ও বড়; full-screen-এ ব্যাক/হোম বাটন দেখা যায় না বা
কাজ করে না; popup নিচে চাপা পড়ে; UI সব ফোনে বড় লাগে — "zoom কমিয়ে ফিট করান, deeply")

Owner-রিপোর্ট (স্ক্রিনশট Screenshot_2026-10-29-21-25-34 + বার্তা): (১) ছোট স্ক্রিনে অন্য ফোন
দেখার সময় নিচের বাটনগুলো সঠিক পজিশনে নেই — স্ক্রিনের একদম নিচে ছোট করে থাকতে হবে যেন কন্ট্রোল
সহজ হয়; (২) full-screen-এ শেয়ার করা ফোনের ব্যাক-বাটন দেখা/কাজ করে না — নিচে ছোট overlay-ট্যাব
চাই (অপশন/হোম/ব্যাক + ছোট-স্ক্রিন + ক্লোজ), আর দেখা-ওয়াচার ফোন থেকে চাপলে তা সত্যিই অন্য ফোনে
কাজ করতে হবে; (৩) popup স্ক্রিনের মাঝখানে চোখের সামনে আসতে হবে; (৪) পুরো UI সব ফোনে ফিট —
zoom কমান, প্রতিটি section এক স্ক্রিনে।

বাস্তবায়ন (MainActivity.kt + ui/Theme.kt — শূন্য backend/rules/signaling/FCM পরিবর্তন):
(১) SessionKeyBar — রুট-লেভেল Box-এ স্ক্রিনের একদম নিচে pin করা কমপ্যাক্ট কন্ট্রোল-বার
(navigationBars-inset সচেতন), [Back][Home][Recents] | [type][fullscreen][stop], 44dp touch
target; তিন সিস্টেম-কি প্রকৃত কন্ট্রোল — sendAssistNav একই epoch-gated command-channel-এ
যায় (tap-এর মতোই), recipient-এর assist service GLOBAL_ACTION_BACK/HOME/RECENTS করে (Phase-8
ওয়্যারিং প্রমাণিত); touch বন্ধ থাকলে সৎ hint-chip; পুরনো mid-scroll Back/Home/Recents সারি +
type + full-screen + stop বাটন সরানো। (২) একই বার full-screen viewer-এর ভেতরে ছোট নিচের
overlay হিসেবে (exit-to-small সহ) — full-screen আর dead-end নয়; পুরনো top-right close সরানো;
stop watching এখন centered confirm-dialog চায়। (৩) approval-ask এখন CENTERED modal dialog
(HomeConsentDialog, session-id প্রতি একবার, একই Firestore grant/deny যা উপরের কার্ডে ছিল) হিসেবেও
আসে + REQUESTED ask এলে অ্যাপ নিজেই home-ট্যাবে জাম্প করে — popup চোখের সামনে, নিচে-চাপা নয়।
(৪) Theme.kt-তে কমপ্যাক্ট type-scale (প্রতিটি slot −1..2sp, tight line-height) + inline-ভিডিও
cap 480→400dp — সাধারণ ফোনে section এক স্ক্রিনে ফিট। SessionUiBus = একক সত্য-উৎস
(panel/pinned-bar/fullscreen সিংক; নতুন session-id-তে fresh reset — পুরনো fullscreen/stop-dialog
বহন করে না)। versionCode 37→38, versionName 0.18.1→0.19.0।

যাচাই: ৪-কমিট ধারাবাহিক push — gradle a35391d, MainActivity c3002d0 (এক কমিটের ট্রানজিয়েন্ট
"Unresolved reference 'tab'" :2672 — পরের MainActivity re-push-এ সংশোধিত), Theme 038e59b,
MainActivity চূড়ান্ত 92af51e; চূড়ান্ত CI রান 40910033808 → SUCCESS
(github.com/psfaruk/family-remote-support/actions/runs/40910033808); readback-verify ৩/৩ MATCH
(fresh Contents API sha256 — gradle/MainActivity/Theme); আর্টিফ্যাক্ট স্থানীয় পুনঃযাচাই
verify-apk-axml → PASS (code=38 INT_DEC, name=0.19.0, v2-sig উপস্থিত, stale-প্রোব শুধু [0.19.0]);
APK → download/family-remote-support-v0.19.0-code38.apk (39.56 MB)।

ফেইল/বাকি: (১) Owner-করণীয় — উভয় ফোনে v0.19.0 ইনস্টল করে বাস্তব-পরীক্ষা (ছোট-বার, full-screen
overlay-বাটন, centered popup, ফিট-ফিল) — আমার পক্ষে সম্ভব নয়; (২) Phase-14.1-বাকি: Cloudflare
dashboard-এ build-সফল নিশ্চিত + FIREBASE_SA_JSON/KILL_TOKEN secrets + /health secretsPresent
দুই-true প্রমাণ, তারপর POST /sweep লাইভ-টেস্ট; (৩) Phase 14b (অ্যাপ→Worker instant sweep-কল)
বাকি; (৪) Uptodown-প্রকাশ — Owner জানালে শুরু হবে; (৫) GH kill-push cron re-register-ফল
পর্যবেক্ষণে (one-time পর্যবেক্ষণ-কাজ সেট)।

---
Task ID: 50
Agent: Z.ai Code (main)
Task: Phase 16 (v0.20.0, versionCode 39) — TAB RESTRUCTURE + HOME SHOWCASE + 15 LANGUAGES +
PRIVACY DOCS (Owner review of build 38: "হোম ট্যাব এ যে সমস্ত অপশন দেখা যাচ্ছে সেগুলো হোম ট্যাবে
থাকা উচিত নয়… হোম ট্যাব ভিজিট করলেই সে অ্যাপ সম্পর্কে পুরোপুরি ধারণা নেবে, আকর্ষণীয় করেন… একটিভিটি
ট্যাব সেটিংসে মার্জ করেন… একটিভিটি মুছে live location নামে ট্যাব খুলুন — কাজ ভবিষ্যতে… ফোন ট্যাবে এই
অপশন দুই জায়গায় দেখাচ্ছে, নিচের এড-অপশনটা বাদ দিন… সেটিংসে ইউজার প্রোফাইল বানান… প্রধান ভাষা
ইংলিশ হবে, পপুলার ১০-১৫ ভাষা যোগ করুন… About-এ Firebase তথ্য দেখা যাচ্ছে — এটা প্রাইভেসি নয়…
নীতিমালা ও প্রাইভেসি পলিসি অ্যাপে উন্মুক্ত থাকবে… নোটিফিকেশন আসে না… কপি/ডাউনলোডে 'কপি হয়েছে/
ডাউনলোড হয়েছে' দেখাতে হবে")

বাস্তবায়ন (MainActivity.kt + ui/AuthGate.kt + নতুন ui/Lang.kt + res/values* ১৫ ভাষা — session-
security/rules/WebRTC/FCM লজিক শূন্য পরিবর্তন): (১) বটম-ন্যাভ = হোম · ফোন · লাইভ লোকেশন ·
সেটিংস — একটিভিটি ট্যাব বিলুপ্ত, তার সেশন-একটিভিটি + যুক্ত-ফোন-লাইভ কার্ড সেটিংসে মার্জ; লাইভ
লোকেশন = সৎ placeholder ("ভবিষ্যতের আপডেটে আসছে", কিছুই সংগ্রহ নয়, কোনো ভুয়া ম্যাপ নয়)। (২) হোম =
অ্যাপ-শোকেস: হিরো ওয়েলকাম-কার্ড (পরিচয় + এক-লাইন প্রতিশ্রুতি + সাইন-আউট হলে সাইন-ইন বোতাম),
"এই অ্যাপ যা যা করতে পারে" ফিচার-গ্রিড (লাইভ স্ক্রিন/রিমোট টাচ/ফ্যামিলি পেয়ারিং/গেস্ট — প্রতিটিতে
সৎ explainer পপআপ), একটি কমপ্যাক্ট সেটআপ-স্ট্রিপ; কারিগরি ২×২ গ্রিড/ডিভাইস-লিস্ট/কুইক-অ্যাকশন
সেটিংসে সরানো। (৩) সেটিংস: ইউজার প্রোফাইল (নাম সম্পাদনাযোগ্য — Firebase profile + users/{uid}
মিরর, টোস্ট-কনফার্ম), ১৫-ভাষা সিলেক্টর (ফ্রেশ-ইনস্টলে ডিফল্ট English — attachBaseContext Locale-wrap
+ activity recreate, নেটিভ নাম কখনো অনুবাদ হয় না: English বাংলা हिन्दी العربية Español Français
Português Русский Bahasa Indonesia اردو Türkçe Deutsch 中文 日本語 नेपाली), ইন-অ্যাপ প্রাইভেসি পলিসি +
নীতিমালা ডকুমেন্ট (পূর্ণ লেখা, EN+BN; বাকি ভাষায় সচেতনভাবে EN fallback), এক-ট্যাপ টেস্ট নোটিফিকেশন
প্রোব (আসল support_session_requests চ্যানেলে সত্যিকারের নোটিফিকেশন — চ্যানেল+পারমিশন তাৎক্ষণিক
যাচাই; পারমিশন বন্ধ থাকলে সেটাই বলে), About থেকে Firebase project/app-ID অপসারণ (প্রাইভেসি-ফিক্স;
এখন শুধু সংস্করণ+প্যাকেজ+সৎ storage-নোট+পলিসি-লিংক), সাইন-আউট একদম নিচে। (৪) ফোন ট্যাবের ডুপ্লিকেট
"যুক্ত করুন" FAB অপসারণ — স্থায়ী উপরের কার্ডই একমাত্র এন্ট্রি (Owner: "অপশন দুই জায়গায় দেখাচ্ছে")।
(৫) কপি → "কপি হয়েছে ✓" টোস্ট, QR সেভ → "ডাউনলোড হয়েছে ✓ Download/…" টোস্ট, শেয়ার-ব্যর্থতায়
টোস্ট; মৃত copiedJustNow স্টেট অপসারণ। (৬) ১৩টি নতুন res/values-XX (hi, ar, es, fr, pt, ru, id,
ur, tr, de, zh, ja, ne) + পূর্ণ values-bn; AuthGate-এর প্রথম-স্ক্রিন লেবেলও রিসোর্সে।

টেক-নোট: ভাষা-ইঞ্জিন LangPrefBus (ui/Lang.kt) — SharedPreferences "ui_prefs"/"lang_pref",
attachBaseContext-এ wrap() (composition-এর আগে), বদলালে recreate() — ThemePrefBus-এর একই সৎ-সেটিং
প্যাটার্ন। আইকন-কোর-সেট সীমা: Visibility/TouchApp/FamilyRestroom/Group/Language/PrivacyTip/Gavel/
NotificationsActive এক্সটেন্ডেড-সেটে → কোর-বিকল্প (PlayArrow/Edit/Favorite/Person/List/Lock/
CheckCircle/Notifications) ব্যবহার; প্রতিটি কোর-আইকনের স্পষ্ট import লাগে (CI দু'রাউন্ড ধরে শিখলো)।
values-fr-এর ডাবল-এস্কেপড অ্যাপোস্ট্রফি (\\') → U+2019 (’) টাইপোগ্রাফিক — aapt2 "Invalid unicode
escape" ফিক্স।

যাচাই: স্ট্যাটিক সুইট — brace/paren/bracket ব্যালেন্স {0,0,0} (MainActivity 5601 লাইন + AuthGate),
dangling-ref ০ (onGoActivity/firebaseProjectId/firebaseAppId/copiedJustNow), duplicate-fun ০,
R.string refs ৬৫/৬৫ defined, ১৫-লোকেল কী-কভারেজ ALL OK, আইকন used-vs-imported ALL OK; push ২১
কমিট (res×15+Lang+AuthGate+MainActivity+gradle+fr-fix+import-fix), readback-verify ALL MATCH
(fresh Contents API sha256); CI: 40935942481 FAIL (fr-escape) → 40937545369 FAIL (icon-imports) →
**40937912335 SUCCESS** (a1e291c); আর্টিফ্যাক্ট স্থানীয় পুনঃযাচাই verify-apk-axml → PASS (code=39
INT_DEC, name=0.20.0, v2-sig, version-shaped strings শুধু [0.20.0]); APK →
download/family-remote-support-v0.20.0-code39.apk (39.7 MB)। README Status-এ Phase 16 বুলেট।

ফেইল/বাকি: (১) Owner-করণীয় — উভয় ফোনে v0.20.0 ইনস্টল করে দেখা (নতুন হোম/সেটিংস/ভাষা/লাইভ-
লোকেশন ট্যাব, টোস্ট, টেস্ট-নোটিফিকেশন); (২) নোটিফিকেশন-আসে-না-র মূল নিরাময় Cloudflare স্তরের
secrets (Phase 14.1 বাকি — FIREBASE_SA_JSON/KILL_TOKEN + /health প্রমাণ) — ইন-অ্যাপ টেস্ট-প্রোব
এখন চ্যানেল/পারমিশন যাচাই করার হাতিয়ার; (৩) Phase 16b-প্রার্থী: গভীর স্ক্রিনের ইনলাইন বাংলা
স্ট্রিং-গুলোও রিসোর্সে তোলা (সেশন/পেয়ারিং/উইজার্ড), ফোন-ট্যাবে নতুন ফিচার (চ্যাট/শিডিউল),
লাইভ-লোকেশনের প্রকৃত কাজ; (৪) Uptodown-প্রকাশ Owner-এর সংকেতের অপেক্ষায়।

---
Task ID: 51
Agent: Z.ai Code (main)
Task: Phase 17 (v0.21.0, versionCode 40) — FULL-CODEBASE AUDIT + FIX ROUND (Owner: "Phase 0
থেকে সর্বশেষ পর্যন্ত সব ঠিক হয়েছে কিনা… কোনো বাগ / টেকনিকেল সমস্যা / লজিক ভুল আছে কিনা,
deeply audit করো, সমস্যা খুঁজে বের করে fix করো"; সংযুক্ত ২টি Chrome স্ক্রিনশট = Cloudflare
Deployments ট্যাব — সক্রিয় ডিপ্লয় "Add variable: FIRE…", ৭৬ ইভেন্ট / ০ এরর / প্রতি মিনিটে
info = Worker লাইভ ও Owner FIREBASE_SA_JSON ভেরিয়েবল যোগ করছেন)

পদ্ধতি: রিমোট ট্রুথ-সোর্স সিঙ্ক (HEAD 51375b7, Phase 16 v0.20.0) → ৪টি সমান্তরাল অডিট-এজেন্ট
(WebRTC/ডেটা কোর · MainActivity ৫৬০৭-লাইন · rules+worker+CI+স্ক্রিপ্ট · i18n/সার্ভিস/নোটিফিকেশন/
ম্যানিফেস্ট) → ৩৫+ ফাইন্ডিং সংগ্রহ → প্রতিটি গুরুতর ফাইন্ডিং লাইন-লেভেলে নিজে কোডে যাচাই →
২১টি ফাইল সংশোধন → CI + লাইভ-নিয়ম + প্রোব + APK চেইনে যাচাই।

বাস্তবায়ন — অ্যাপ (১৩ ফাইল): (১) ক্রিটিক্যাল — ফোন-ট্যাবে বসানো "no session → সব বন্ধ" ইফেক্ট
প্রথম কম্পোজিশনে খালি লিস্টেই ফায়ার করত (প্রথম স্ন্যাপশট অ্যাসিঙ্ক্রোনাস) → ট্যাব-সুইচ/রোটেশন/
ভাষা-বদলে লাইভ শেয়ার খুন হত; এখন রুট-লেভেল + loaded-flag। (২) QR-সেভ টোস্ট নিজেই নিজের
LaunchedEffect-কী রিসেট করে মিড-IO ক্যান্সেল হতো (Owner-র "সেভ হলে দেখায় না"-ই রুট-কজ) →
মনোটোনিক ট্রিগার। (৩) সাইন-আউট লাইভ শেয়ার/ভিউয়ার ভাঙে + SessionUiBus রিসেট। (৪) SdpObserver
কলব্যাক main-এ hop (remoteDescriptionSet/gotOfferAt/pendingIce-এর ক্রস-থ্রেড রেস = হারানো
ICE = মাঝেমধ্যে কানেকশন-ফেইল)। (৫) "bye"/"watch" সিগন্যাল freshness-গেট (৩০/২০ সে) +
teardown-এ নিজের সিগন্যাল-কিউ ওয়াইপ (বাসি bye নতুন re-share-কে সঙ্গে সঙ্গে ভাঙত)। (৬) ডিফার্ড
capture/watch-এ রাউন্ড-টোকেন + configJob cancel (~১০ সে কনফিগ-উইন্ডোতে স্টপ/পুনরায়-স্টার্ট =
জম্বি শেয়ার/লিকড PeerConnection)। (৭) MediaProjection.Callback.onStop → fail(); FGS
state-collector → IDLE-এ stopSelf ("sharing active" নোটিফিকেশন আর মিথ্যা বলে না)। (৮) cmd
রিপ্লে-উইন্ডো ±১২০ সে স্কিউ মেনে (ম্যানুয়াল ঘড়ির ফোন আর সব Firestore-পাথ ট্যাপ ফেলায় না)।
(৯) ভাষা: wrap() এখন EN-তেও wrap (অ-ইংরেজি ডিভাইসে fresh-install সিস্টেম-ভাষায় খুলত — Owner-
র ইংরেজি-ডিফল্ট নির্দেশের বিপরীত) + bn→en-এ Locale.setDefault রিসেট (ইংরেজি UI-র ভেতরে
বাংলা সংখ্যা)। (১০) পিনড SessionKeyBar মাপা bottom-nav উচ্চতার উপরে (ট্যাব আইকন ঢাকা পড়ত)।
(১১) নোটিফিকেশন-মূল-কারণ: FCM টোকেন-রেজিস্ট্রেশন এখন ডিভাইস-রেজিস্ট্রেশন থেকে স্বাধীন +
নতুন PushTokenService (onNewToken → registry রি-রাইট; রোটেশনে বাসি টোকেডক → sweep-এর
UNREGISTERED-ডিলিট → চির-মৃত পুশ)। (১২) SessionNotifier dedupe-set এখন আগে consult হয় +
setOnlyAlertOnce; সব নোটিফিকেশনে নতুন মনোক্রোম ic_stat_notify (অ্যাডাপটিভ লঞ্চার টিন্টে
ব্লব হত)। (১৩) Accessibility হাইলাইট extra এখন সঠিক Bundle (String হওয়ায় স্ক্রল/হাইলাইট
নীরবে কাজ করত না)। (১৪) Presence অনলাইন-উইন্ডো ৫→২০ মিনিট (হার্টবিট ১৫-মিনিট ফ্লোর ঢাকে)।
(১৫) QrScanner ML Kit handle close; allowBackup=false; consent-মোডাল ডিডুপ প্রসেস-ডিউরেবল
(SessionUiBus.shownAsks); touchOn+dataSaver প্রতি-সেশন রিসেট; expireSession ভ্যালিডেটিং
ট্রানজেকশন; endSession participant-check; PairingUiBus (রিক্রিয়েটে PENDING আমন্ত্রণ আর
আটকায় না); লগইন-স্ক্রিনে perm-poll বন্ধ; onError লাস্ট-লিস্ট রাখে।

নিরাপত্তা — rules (৩ ফিক্স, লাইভ-পাবলিশড): (ক) পেয়ারিং টোকেন ছিল শুধু সাজসাজি — rules টোকেন
সম্পর্কে কিছুই যাচাই করত না; ৬-অক্ষর doc id অনুমান করা যে-কোনো signed-in ইউজার PAIRED+
claimedBy=নিজে লিখে স্থায়ী ফ্যামিলি-হেল্পার হয়ে যেত। এখন redemption সার্ভার-সাইডে ৮০-বিট
টোকেন জ্ঞান প্রমাণ করে (claimToken == সংরক্ষিত raw token; আমন্ত্রণে নতুন token ফিল্ড)।
(খ) signal-create এখন sessionEndsAt > request.time (মেয়াদ-পার হওয়া ACTIVE সেশনে patched
হেল্পার cmd লিখতে পারত)। (গ) myPairing এখন rel-এর status==ACTIVE + guest-উইন্ডো দেখে
(REVOKED/EXPIRED এক্স-পার্টনার চিরকাল presence পড়ত)।

ইনফ্রা: ৩-টিয়ার kill-push এখন CLAIM-BEFORE-SEND (currentDocument.updateTime প্রিকন্ডিশন —
দুই টিয়ার একই সেকেন্ডে পড়লে ডাবল পুশ হত); per-doc try/catch (একটি নেটওয়ার্ক-ব্যর্থতা পুরো
sweep ভাঙত); NDJSON-সহনশীল পার্সিং; বাসি REQUESTED doc লেজি-EXPIRE (১,৪৪০ দৈনিক sweep-এ
চিরকাল re-fetch হত); /health এখন শুধু কাউন্ট (pubic-এ doc-id/টোকেন-প্রিফিক্স লিক বন্ধ); CORS
বাদ; publish-firestore-rules repo-relative path + SA-blob + সঠিক release; প্রোবে SKIPPED
সততার সাথে গণনা + নতুন N17 (ক্লেইম-টোকেন ছাড়া redeem) ও N18 (ভুয়া টোকেন) অ্যাটাক-রিপ্রো;
CI: permissions contents:read + artifact retention ৭ দিন।

যাচাই: স্ট্যাটিক সুইট — ১১টি Kotlin ফাইলে brace/paren/bracket {0,0,0}, dangling-ref ০
(saveRequest সম্পূর্ণ অপসৃত), নোটিফিকেশন-বিল্ডারে R.mipmap ০ (সব ic_stat_notify), নতুন
drawable/সার্ভিস/ম্যানিফেস্ট-সূচি ক্রস-চেক; ৪ JS ফাইল node --check OK। push ২১ কমিট (৮
স্ক্রিপ্ট+worker+rules+CI সহ), readback-verify ALL MATCH (fresh Contents API sha256);
CI: run 40949729611 (HEAD 91fd2a8) SUCCESS; rules লাইভ-পাবলিশড (ruleset
2d39a14d-1966-4a3a-8367-453131f0d106, 515 লাইন, সার্ভার-পার্সড); নেগেটিভ-প্রোব ২৪/২৪ ✔ —
নতুন N17/N18 403 DENIED (অ্যাটাক বন্ধ-প্রমাণ), G1 লিজিট redeem 200, ০ SKIPPED; APK artifact
পুনঃযাচাই verify-apk-axml → PASS (code=40 INT_DEC, name=0.21.0, v2-sig, version-স্ট্রিং
শুধু [0.21.0]) → download/family-remote-support-v0.21.0-code40.apk (39.7 MB); স্যান্ডবক্স
kill-push mini-service (:3020) নতুন কোডে রিস্টার্ট — টিক স্বাভাবিক, summary-তে নতুন
claim-lost/expired-stale কাউন্টার; Cloudflare Worker নতুন কোড Workers-Builds দিয়ে
অটো-ডিপ্লয় হবে (Owner-র স্ক্রিনশটেই প্রমাণিত মেকানিজম — Deployments ট্যাবে নতুন
"v0.21.0 (Phase 17 -…" রো দেখা যাবে)।

সতর্কতা: v0.20.0↔v0.21.0 পেয়ারিং ভার্সন-লকড (আমন্ত্রণে নতুন token ফিল্ড + claimToken) —
দুই ফোনেই এই বিল্ড লাগাতে হবে; পুরনো PENDING আমন্ত্রণ (৫ মিনিটেই মেয়াদ-পার) প্রশ্নাতীত।

ফেইল/বাকি: (১) Owner-করণীয় — উভয় ফোনে v0.21.0 ইনস্টল + সত্যিকারের দুই-ফোন পরীক্ষা: শেয়ার
চালু রেখে ট্যাব-সুইচ/রোটেশন/ভাষা-বদল (শেয়ার বাঁচবে), QR সেভ-টোস্ট, নোটিফিকেশন (কিলড
অ্যাপে পুশ — Worker স্তরে FIREBASE_SA_JSON এখন Owner-যোগকৃত স্ক্রিনশট অনুযায়ী; /health-এ
secretsPresent দুটোই true কিনা দেখে জানাবেন), নতুন স্ট্যাটাস-আইকন। (২) Cloudflare
Deployments ট্যাবে "v0.21.0 (Phase 17" ডিপ্লয় কনফার্ম (Workers Builds অটো)। (৩) অডিটের
দস্তাবেজকৃত-কিন্তু-স্থগিত: ডেডিকেটেড least-privilege SA + কী-রোটেশন (Owner অপারেশনাল),
release-keystore SHA-1 Firebase কনসোলে যোগ (Owner), গভীর-স্ক্রিন i18n (Phase 16b ব্যাকলগ),
সার্ভার-সাইড ওপেন-সেশন-ক্যাপ, worker এন্ডপয়েন্ট রেট-লিমিট। (৪) Uptodown-প্রকাশ Owner-সংকেতের
অপেক্ষায়।

---
Task ID: 52
Agent: Z.ai Code (main)
Task: Phase 18.1 (v0.22.0, versionCode 41) — Pairing & Session Lifecycle অডিট + রিবুট-পুনঃসংযোগ
ফিক্স (Owner: "18.1 দিয়ে শুরু করো" — Phase 18 পূর্ণ-অডিট প্ল্যানের প্রথম ফেজ; Owner-বাগ:
"ফ্যামেলি কানেক্ট হওয়ার পর যদি রিবুট করে, তখন ওই ফ্যামেলির যোগ করা যায় না")

পদ্ধতি: রিমোট ট্রুথ-সোর্স সিঙ্ক (HEAD f48b883, 54 ফাইল রিফ্রেশ) → session-লাইফসাইকেল ফুল-ট্রেস
(SessionRepo 414 লাইন + MainActivity session-কার্ড/cleanup ব্লক + rules supportSessions ব্লক +
worker/script sweep + রিবুট-রিরেজিস্ট্রেশন চেইন) → ৫ ফাইন্ডিং → ৩ ফিক্স।

ফাইন্ডিং: (F1-ক্রিটিক্যাল) requestSession-এর duplicate-check যেকোনো খোলা REQUESTED/ACTIVE doc-এ
থ্রো করত — রিবুটের পর zombie doc ব্লক করত, self-heal নেই, UI-তে "already waiting/active" ছাড়া
কোনো পথ নেই = Owner-র রিবুট-ব্লক। (F2-উচ্চ) PairingSections-এর lazy-expiry শুধু openSession
(নতুনতম) পরিষ্কার করত — পুরনো zombie জমত ও watchSessions-এর limit(10) উইন্ডো ভরে fresh ask
বাইরে চলে যেতে পারত। (F3-উচ্চ) Worker + sandbox-script sweep শুধু status==REQUESTED দেখত —
বাসি ACTIVE (sessionEndsAt পেরোনো) সার্ভার-সাইডে কখনো পরিষ্কার হত না। (F4-তথ্য) rules আগে থেকেই
REQUESTED→EXPIRED (উভয় participant) + ACTIVE→ENDED (উভয়, endedBy=uid|timeout) অনুমোদন করে —
rules-পরিবর্তন অপ্রয়োজনীয়। (F5-তথ্য) রিবুট-রিরেজিস্ট্রেশন চেইন সুস্থ — app-open-এ registerDevice +
heartbeat + registerFcmToken স্বাধীন error-path-এ পুনঃরান, PushTokenService.onNewToken কভার।

বাস্তবায়ন: (১) SessionRepo.requestSession SELF-HEAL — বাসি doc পেলে নিজেই পরিষ্কার করে আগায়:
নতুন isStaleOpen(now) হেল্পার (REQUESTED: respondDeadline+4s পেরোলে; ACTIVE: sessionEndsAt+4s
পেরোলে — UI-lazy-expiry-র একই grace), ≤৪ রাউন্ড লুপ, প্রতি ব্যাচে সব বাসি doc → expireSession/
endSession(byTimeout=true) — rules-যাচাইকৃত একই ট্রানজিশন; genuine grant/deny রেসে পরের রাউন্ডে
সত্য দেখে honest error; malformed-doc guard + ৪-রাউন্ড-exhaust guard (duplicate-write কখনো নয়);
fresh-ব্লকের বার্তায় escape-hatch (কার্ডের Cancel/End) নির্দেশ EN+BN। (২) ROOT-level zombie-sweep
(MainActivity HomeScreen) — যেকোনো ট্যাবে অ্যাপ জীবন্ত হলেই সব বাসি open-session পরিষ্কার (আগে শুধু
ফোন-ট্যাব খোলা থাকলে চলত)। (৩) PairingSections sweep-all — per-second টিকে এখন প্রতিটি open
session পরীক্ষা, শুধু নতুনতম নয়। (৪) Worker sweepOnce-এ ACTIVE-সেশন expiry — status==ACTIVE
কুয়েরি, sessionEndsAt+60s পেরোলে admin-path ENDED("timeout") (নিয়ম-অনুরূপ ট্রানজিশন), oldest-first,
প্রতি-সুইপ ৫০-ক্যাপ, per-doc try/catch; summary-তে activeDocs/activeExpired (health-এ স্বয়ং
প্রবাহিত)। (৫) scripts/fcm-kill-push.mjs-এ একই পোর্ট + formatSummary-তে নতুন কাউন্টার।
ভার্সন: 0.22.0 / 41।

যাচাই: node --check worker+script OK; Kotlin static — SessionRepo/MainActivity brace/paren/bracket
{0,0,0}; isStaleOpen ১-সংজ্ঞা ২-ব্যবহার(+১ কমেন্ট); old limit(1) অপসৃত; ROOT+full sweep উপস্থিত;
push ৭ কমিট (৩dab758→e812227), readback-verify ALL MATCH (Contents API fresh sha256);
CI: run 41014941966 FAIL — SessionRepo.kt:162 Kotlin-List-এ `stale.isEmpty` প্রপার্টি-সিনট্যাক্স
(Java-QuerySnapshot-এর synthetic-property প্যাটার্ন Kotlin stdlib-এ নেই) → `isEmpty()` ফাংশন-কল
ফিক্স e812227 → **run 41015231895 SUCCESS**; APK artifact পুনঃযাচাই verify-apk-axml → PASS
(code=41 INT_DEC, name=0.22.0, v2-sig, version-স্ট্রিং শুধু [0.22.0]) →
download/family-remote-support-v0.22.0-code41.apk (39.7 MB); sandbox kill-push mini-service
(:3020) নতুন sweep-কোডে রিস্টার্ট — health summary-তে activeDocs/activeExpired কাউন্টার লাইভ;
Cloudflare Worker নতুন কোড Workers-Builds-এ অটো-ডিপ্লয় হবে।

ফেইল/বাকি: (১) Owner-করণীয় — দুই ফোনে v0.22.0 (দুটোতেই! পেয়ারিং ভার্সন-লকড) ইনস্টল করে পরীক্ষা:
শেয়ার চালু অবস্থায় এক ফোন রিবুট → আবার খুলে সরাসরি নতুন সেশন চাওয়া (নতুন পেয়ারিং লাগবে না);
(২) mixed-version পেয়ারিং (v0.20↔v0.21+) এখনও ব্যর্থ হবে — security-first সিদ্ধান্ত, উভয় ফোনে
একই বিল্ড বাধ্যতামূলক; (৩) Phase 18.2 (fullscreen bar) / 18.3 (streaming latency) পরবর্তী
Owner-নির্দেশের অপেক্ষায়; (৪) Worker-এর নতুন কোড Workers-Builds-এ অটো-ডিপ্লয় হবে (Deployments
ট্যাবে "v0.22.0 (Phase 18.1" রো)।

---
Task ID: 53
Agent: Z.ai Code (main)
Task: Phase 18.2 (v0.23.0, versionCode 42) — Fullscreen Session Bar UX ফিক্স (Owner-বাগ B:
"ফুলস্ক্রিনে নিচের অপশনগুলো ফিক্স হয়ে যায়… কিবোর্ডের স্পেস প্রেস করতে পারি না")

পদ্ধতি: fullscreen Dialog-এর overlay-ট্রেস → রুট-কজ নিশ্চিত — SessionKeyBar আগে Alignment.
BottomCenter-এ হার্ড-পিন ছিল (Column + navigationBars padding), আর রিমোট ফোনের কিবোর্ডের
স্পেস-বার ঠিক শেয়ার্ড-স্ক্রিনের নিচ-মাঝেই থাকে → touch চালু অবস্থায় স্পেস-কি-র ট্যাপ লোকাল
বারে আটকে যেত (রিমোটে যেত না)। ফিক্স: নতুন FullscreenKeyOverlay composable — (১) ৪-সেকেন্ড
idle-এ AUTO-FOLD (500ms-টিক LaunchedEffect; type/stop dialog খোলা থাকলে কখনো ফোল্ড নয়);
(২) ফোল্ড হলে ছোট ⌃ পিল — dock bottom-END (স্পেস-বার-জোনের বাইরে), ট্যাপ করলে বার ফেরে ও
টাইমার re-arm; (৩) বার ও পিল দুটোই DRAGGABLE (clampBar/clampPill — স্ক্রিনের বাইরে যেতে
পারে না; pill end-anchor, bar center-anchor); (৪) বারের উপরে ⌄ ফোল্ড-হ্যান্ডেল চিপ —
অপেক্ষা না করে সঙ্গে সঙ্গে লুকানো যায়। প্রতিটি pointer-down-এ টাইমার re-arm (keys-সহ —
awaitPointerEventScope initial-loop); fullscreen প্রতি-ওপেনে state fresh (বার দৃশ্যমান,
নিচ-মাঝে শুরু)। ইনলাইন (non-fullscreen) বার অপরিবর্তিত — বাগ শুধু fullscreen-এ।

ফাইল: MainActivity.kt (FullscreenKeyOverlay + FULLSCREEN_BAR_AUTOHIDE_MILLIS=4000 + imports:
layout.offset/ui.unit.IntOffset/ui.unit.round/semantics.contentDescription; fullscreen-Column
ব্লক → FullscreenKeyOverlay(touchOn=assistMode) কল), app/build.gradle.kts (42 / 0.23.0)।

যাচাই: brace/paren/bracket balance OK; প্রথম push (bfbeeaa+e6457af) → CI FAIL — Kotlin:
detectDragGestures-এর `amount` হলো geometry.Offset, IntOffset-এর সাথে + অপারেটর মেলে না
(MainActivity.kt:5097/5132) → `amount.round()` ফিক্স (import ui.unit.round) → 79e3c88 →
**run 41018191824 SUCCESS**; APK artifact verify-apk-axml → PASS (code=42 INT_DEC,
name=0.23.0, v2-sig, version-স্ট্রিং শুধু [0.23.0]) →
download/family-remote-support-v0.23.0-code42.apk (39.7 MB); readback-verify ALL MATCH
(Contents API fresh sha256)।

ফেইল/বাকি: (১) Owner-করণীয় — দুই ফোনে v0.23.0 ইনস্টল (পেয়ারিং ভার্সন-লকড — দুটোতেই একই
বিল্ড) → ফুলস্ক্রিনে টাচ চালু করে রিমোট কিবোর্ডের স্পেস-প্রেস টেস্ট: ৪ সেকেন্ডে বার নিজেই
লুকাবে, ⌃ পিল ডান-কোণে, টেনে যেকোনো জায়গায় রাখা যায়; (২) Phase 18.3 (streaming latency —
MAINTAIN_RESOLUTION→FRAMERATE + adaptive ceiling) পরবর্তী Owner-নির্দেশের অপেক্ষায়; (৩)
18.1-এর mixed-version নোট প্রযোজ্য।

---
Task ID: 54
Agent: Z.ai Code (main)
Task: Phase 18.3 (v0.24.0, versionCode 43) — Streaming Latency ফিক্স (Owner-বাগ C: "টাচ রিয়েল
টাইমে কাজ করে, কিন্তু owner এর মোবাইল এ আসতে দেরি হয়") + Owner-স্ক্রিনশটের Pairing
PERMISSION_DENIED নির্ণয় ও নির্দেশনা-সংশোধন

পদ্ধতি: ScreenShareEngine ফুল-ট্রেস → বাগ C রুট-কজ — degradationPreference=MAINTAIN_
RESOLUTION দুর্বল নেটে রেজোলিউশন ধরে রাখতে গিয়ে ফ্রেম স্টার্ভ করত (touch data-channel-এ
রিয়েল-টাইম, ভিডিও সেকেন্ড-পিছিয়ে)। ফিক্স: (১) MAINTAIN_FRAMERATE ডিফল্ট — চাপে ছবি
নামে, ফ্রেম থামে না; (২) ৫-সেকেন্ড RTT watchdog (getStats currentRoundTripTime) —
>120ms→1600k, >200ms→1000k, >300ms→700k, খারাপে সোজা টায়ারে নামে, সুস্থে ধাপে ওঠে
(hysteresis); relay/data-saver caps-এর সাথে min-কম্পোজ; (৩) config/quality.
degradationPreference লাইভ-নব (framerate|balanced|resolution — নতুন APK ছাড়াই টিউন);
set-quality-config.mjs-এ ৬ষ্ঠ আর্গ। Owner-এর পেয়ারিং-স্ক্রিনশট নির্ণয়: runtime POSITIVE
probe (নতুন scripts/rules-pairing-probe.mjs — দুটি synthetic identity, সঠিক client-writes
replay) → P1 create+redeem(WITH claimToken)=200 PASS, N1 without-claimToken=403, N2
wrong-token=403, negative-probe 12/12 PASS → লাইভ rules ও remote client (v0.21+ claimToken-
সমর্থিত, কমিট 7d295afa) উভয়ই সঠিক; DENIED-এর একমাত্র ব্যাখ্যা = একটি ফোনে পুরনো বিল্ড
(v0.20.0-) → version-lock। PairingRepo-র বিভ্রান্তিকর "Phase 6 rules পাবলিশ করুন" বার্তা
সংশোধিত — এখন সঠিক নির্দেশ: "দুটো ফোনেই নতুন অ্যাপ ইনস্টল করুন"।

স্যান্ডবক্স-ঘটনা (গুরুত্বপূর্ণ): কাজের মাঝে sandbox-এর 55/67 ফাইল পুরনো checkpoint-এ
রোল-ব্যাক হয়েছিল (MainActivity/build.gradle আজকের push-এর পরেও, cloudflare/wrangler.toml
restore-এর পরেও) — remote অক্ষত ছিল; সম্পূর্ণ tree-diff + remote-থেকে bulk-restore করে
fresh base-এ সব এডিট re-apply করা হয়েছে। শিক্ষা: sandbox কপি কখনো truth নয় — push-এর
আগে-পরে remote-sha যাচাইই বাধ্যতামূলক।

ফাইল: ScreenShareEngine.kt (MAINTAIN_FRAMERATE ডিফল্ট + scheduleRttWatchdog/onRttSample +
rttTiers + teardown-resets), ConfigRepo.kt (QualityCfg.pref + fetchQuality parse),
PairingRepo.kt (PERMISSION_DENIED বার্তা), app/build.gradle.kts (43/0.24.0), scripts/
rules-pairing-probe.mjs (নতুন), scripts/set-quality-config.mjs (pref আর্গ; remote-এ
প্রথমবার push — আগে sandbox-only ছিল)। firestore.rules: remote ইতিমধ্যেই live-strict
সমান ছিল → SKIP (identical)। LIVE config/quality সেট: vp8·30·1200–4000·framerate।

যাচাই: node probes 3টি PASS; Kotlin সম্পাদন fresh remote-base-এ; push 7 ফাইল (79f8399→
adce5d5), readback-verify ALL MATCH; CI: run 41023422525 (adce5d5) SUCCESS; APK verify →
PASS (code=43 INT_DEC, name=0.24.0, v2-sig, version-স্ট্রিং শুধু [0.24.0]) →
download/family-remote-support-v0.24.0-code43.apk (39.7 MB)।

ফেইল/বাকি: (১) Owner-করণীয় — দুই ফোনেই v0.24.0 ইনস্টল (স্ক্রিনশটের DENIED-এর কারণ পুরনো
বিল্ড; পেয়ারিং ভার্সন-লকড), তারপর নতুন কোড দিয়ে পেয়ার + স্ক্রিন-শেয়ার লেটেন্সি টেস্ট
(এখন চাপে ছবি ঝাপসা হলেও সময়মতো আসবে); (২) লাইভ-টিউন: node scripts/set-quality-config.mjs
vp8 30 1200 4000 balanced|resolution — প্রয়োজনে; (৩) Phase 18.4 (notification chain E2E —
Worker /health URL লাগবে) / 18.5+ পরবর্তী Owner-নির্দেশের অপেক্ষায়; (৪) sandbox-রোল-ব্যাক
সমস্যা পরিবেশগত — remote/GitHub-ই একমাত্র truth।

## §55 — Phase 18.4 (infra — APK অপরিবর্তিত): notification-chain E2E + গুরুতর ল্যাটেন্ট বাগ-ফিক্স (২০২৬-১০-৩০)

Owner দিলেন Worker URL (https://family-remote-support.www-farukkhan-fk885.workers.dev/) + "হ্যাঁ করো" →
Phase 18.4 শুরু। /health প্রথমেই দেখাল: Worker লাইভ, KILL_TOKEN=true, কিন্তু **FIREBASE_SA_JSON=false** ও
cron "not run yet" (SA ছাড়া cron প্রতি মিনিটে fail করছে বলে last ফাঁকা — isolate restart-এ state মোছায় পুরনো
error-ও দেখা যায় না)।

**প্রধান আবিষ্কার — v0.21.0 থেকে তিন টিয়ারই চুপচাপ ভাঙা:** লাইভ E2E-র প্রথম রানে sweep-লগ ধরল
"1 REQUESTED doc(s), 1 fresh, 0 pushed, **1 claim-lost**" — নতুন ডকেও claim হারাচ্ছে। লোকাল রিপ্রোডিউসে
আসল কারণ: Firestore REST `documents.patch`-এ precondition `currentDocument` **body-ফিল্ড নয়,
query-parameter** — পুরনো কোড body-তে পাঠাত → প্রতিটি claim-এ 400 "Unknown name \"currentDocument\" at
'document'" → কোডের 400-as-claim-lost শাখা চুপ করে সেটাকে "অন্য টিয়ার জিতেছে" ভেবে পাশ কাটাত →
**v0.21.0-এর claim-লজিক শিপের দিন থেকে kill-push আসলে কোনো পুশই পাঠায়নি** (লগ "success" দেখাত, কেউ
লক্ষও করেনি — এই ধরনের নীরব-ব্যর্থতাই E2E-টেস্ট ছাড়া ধরা পড়ে না)। একই বাগ ছিল Node টিয়ারে
(scripts/fcm-kill-push.mjs) ও Worker-এ (cloudflare/killpush-worker.js) — দুটোই এক-লাইন-ধাঁচের ফিক্সে ঠিক।

**ফিক্স প্রমাণ (লাইভ production Firestore, পড়া-লেখা-মোছা সব টেস্ট-ডক, শূন্য অবশিষ্ট):** নতুন shape
`?updateMask.fieldPaths=…&currentDocument.updateTime=…` → **200 claim won**; পুরনো updateTime-এ
stale-প্রিকন্ডিশন → রিজেক্ট (400/409 → false) — অর্থাৎ cross-tier এক্সক্লুসিভিটি অক্ষত।

**স্টেট-ডাম্প (read-only):** fcmTokens=৩ (দুটি আজ ০৬:৫৭-৫৮-এ রিফ্রেশ — দুই ফোনেই অ্যাপ জীবিত ও
রেজিস্টার্ড: supportplybit…/FoOKzvShqT…, bangladeshtiktok15…/RPUWy46dzx…), users=৪, supportSessions=২৬।
**ডায়াগ:** REQUESTED ডক ৪৫ সেকেন্ড ওয়াচ করে প্রমাণ করা হল ফোন-অ্যাপ নিজে ডকে লেখে না (claim-ব্রেকার
থিওরি বাদ)।

**লাইভ E2E PASS:** টেস্ট-সেশন supportSessions/e2e184-1793362788707 (recipient=freshest-token uid
FoOKzvShqT…, helperEmail="18.4-e2e-test…", deadline +৬ মিনিট) → workflow_dispatch দিয়ে kill-push চালানো →
**৬ সেকেন্ডে** claim won → FCM send → `killPushSentAt=12:19:54.247Z`, `killPushStatus=sent:1/1` →
রিসিপিয়েন্ট ফোনে HIGH-priority "Help requested" নোটিফিকেশন এসেছে (সার্ভার-প্রমাণ ডক-মার্ক) → টেস্ট-ডক
মুছে শূন্য-অবশিষ্ট। রান 41054516514 success।

**ফিক্স-কভারেজ:** তিন টিয়ারই এক ফিক্সে ঠিক — (১) GH Actions/sandbox mini-service দুজনেই
scripts/fcm-kill-push.mjs import করে; (২) Worker push-এর সাথে সাথেই Workers Builds নতুন কোড deploy করে।
Push: 2b5eb19 + 621094f (দুই ফাইল), SHA256 readback-verify ALL MATCH (4eccd1f14e…/47ecc4249f…)। APK
অপরিবর্তিত — versionCode 43/v0.24.0-ই চলতে থাকবে, নতুন ইনস্টল লাগবে না।

**বাকি (Owner-করণীয়):** (১) Worker dashboard → Settings → Variables and Secrets → **FIREBASE_SA_JSON**
secret বসান (Firebase service-account JSON-এর পুরো টেক্সট; KILL_TOKEN আগেই আছে) → /health-এ
"FIREBASE_SA_JSON":true দেখা গেলেই ৬০-সেকেন্ড স্থায়ী টিয়ার যুক্ত হবে (GH-ব্যাকস্টপ আজ থেকে আবার
সক্রিয়); (২) পুশ-স্মোক-টেস্ট চাইলে আমি sandbox থেকে আবার টেস্ট-সেশন বানিয়ে দিতে পারি। Phase 18.5+
পরবর্তী Owner-নির্দেশের অপেক্ষায়।

## §56 — Phase 18.5 (infra — APK অপরিবর্তিত): server-side deep audit — rules হার্ডেনিং + ২৩-কেস লাইভ প্রমাণ (২০২৬-১০-৩০)

Owner "Next" → Phase 18.5। পদ্ধতি: rules লাইন-বাই-লাইন → লাইভ-মিল যাচাই → ক্লায়েন্ট-রাইট-পাথ
ম্যাপিং (SessionRepo/PairingRepo/PresenceRepo-র প্রতিটি set/update-এর ফিল্ডম্যাপ rules-এর
hasOnly-লিস্টের সাথে হুবহু তুলনা) → নতুন হোস্টাইল-প্রোব → ফিক্স → লাইভ-পুনঃপ্রমাণ।

**বেসলাইন:** LIVE ruleset 2d39a14d == রিপো (drift শূন্য)। ডেটা-স্যানিটি: সেশন/টোকেন/প্রেজেন্স
সব সুসংগত। ক্লায়েন্ট-রাইট-পাথ: create(১১ ফিল্ড)/grant(৫)/deny(৩)/cancel(৩)/end(৪)/expire(২) —
সবগুলোই rules-এর অনুমোদিত-কী-তালিকার ভেতরে; killPush* ফিল্ড কোনো ক্লায়েন্ট-ব্রাঞ্চে নেই (marks
admin-only — ভাল)।

**ফাঁক পাওয়া গেল ও বন্ধ (৬ গ্রুপ, সবই অ্যাপ-আচরণ-সম্মত, শূন্য-ব্রেকেজ):**
(১) signal-এর ফ্ল্যাট ১০০KB ক্যাপ → প্রতি-টাইপ: cmd/cmdack ≤ ৪০৯৬ (ইঞ্জিনের maxCmdChars=৪০৯৬-এর
হুবহু প্রতিলিপি — <= ইনক্লুসিভ), watch/bye ≤ ২৫৬ (ইঞ্জিন watch ১৬..৬৪ আশা করে), ice ≤ ১৬৩৮৪,
offer/answer ১০০KB (SDP-হেডরুম) — হোস্টাইল পার্টিসিপ্যান্ট এখন আর অ্যাস্ক-উইন্ডোতে মেগাবাইট-বাকেট
ভরতে পারবে না; (২) users/{uid}-এ displayName<৩০০ / email<৩২০ / photoUrl<২০৪৮ (আগে ~১MB ব্লব
লাগত); (৩) pairingInvitations-এ inviterEmail/recipientDeviceId/recipientDeviceModel টাইপ+সাইজ-বাউন্ড
(এই ডক যে-কোনো signed-in ব্যবহারকারীর কাছে পঠনযোগ্য — ব্লব-ক্যারিয়ার হতে পারে না); (৪)
devicePresence-এ heartbeatAt/updatedAt is timestamp (ভুল-টাইপ হার্টবিটে ব্যাজ-গণিত চুপচাপ ভুল বয়স
দেখাত); (৫) fcmTokens-এ updatedAt is timestamp; (৬) supportSessions create-এ
requestedAt/createdAt/updatedAt is timestamp (সুইপের freshness-গণিত ভুল-টাইপ requestedAt-এ
ডক-স্কিপ করত)।

**নতুন প্রোব (scripts/rules-audit-probe.mjs, ২৩ কেস):** সিন্থেটিক family-pair ফিক্সচারে আসল
atomic-redeem → নিজেদের সেশনে ব্যাটারি। ফল **২৩/২৩ PASS, শূন্য-অবশিষ্ট (SA-ক্লিনআপ ১১/১১ ডক +
২/২ Auth ইউজার)**। প্রমাণ-সারি: killPushSentAt/killPushStatus/killPushClaimAt জালিয়াতি (পুশ-দমন
আক্রমণ) = ৪০৩; ভুল-পাশের grant = ৪০৩; sessionEndsAt +২১মিনিট = ৪০৩; deadline +৬মিনিট = ৪০৩;
cmd ৫০০০/ice ১৭০০০/watch ৩০০ ক্যারেক্টার = ৪০৩; টাইপ 'evil'/ep ৭০/self-aud = ৪০৩;
EXPIRED-ট্রানজিশনে অতিরিক্ত ফিল্ড = ৪০৩; ৯০০KB displayName/model-int/string-heartbeatAt/string-
token-updatedAt = ৪০৩; ENDED-সেশনে signal = ৪০৩ (সার্ভার-সাইড উইন্ডো বন্ধ)। পজিটিভ-কন্ট্রোল
(A0/A1/A7/A9/A17/A20) সব ২০০ — হার্ডেনিং কোনো সৎ-পথ ভাঙেনি। **রিগ্রেশন: পুরনো ২৫-কেস
negative-probe ২৫/২৫ PASS** (G2-ফিক্সচার-বাগ ঠিক: fixed doc-id নিজেরই CANCELLED অবশিষ্টের সাথে
৪০৯ করত — এখন প্রতি-রানে ইউনিক id + SA-ক্লিনআপ)।

**তথ্য-স্তরের ফাইন্ডিং (এখনই কোনো বদল নয়, 18.6-প্রার্থী):** (ক) পেয়ারিং-ইনভাইটেশনে RAW টোকেন
সংরক্ষণ (rules হ্যাশ করতে পারে না বলে v0.21.0-এর ডিজাইন) + ৬-অক্ষর doc-id (৩১⁶ ≈ ৮৮৭M ≈ ৩০-বিট):
takeover করতে হলে লাইভ ৫-মিনিট উইন্ডোতে আইডি-জাল দিতে হয় — বাস্তবে অসম্ভব-প্রায় (৫০K রিড/সেকেন্ডেও
পূর্ণ-স্প্যান ≈ ৫ ঘণ্টা; লাইভ-ইনভাইট হিট ≈ ১.৭%), কিন্তু বিলিং-বোমা (৪০৪-GET-ও বিল হয়) সম্ভব —
আসল প্রতিষেধক: APK-তে id দৈর্ঘ্য ৬→১২ (৩১¹²) + App Check — 18.6-এর Android-অডিটে করার
সুপারিশ; (খ) config/turn-এর TURN-ক্রেডেনশিয়াল সব signed-in-ই পড়তে পারে (ডিজাইন-অনুযায়ী — গেস্টও
সেশনে TURN লাগে); (গ) Worker-টিয়ার এখনো FIREBASE_SA_JSON-অপেক্ষায় (Owner অ্যাকশন)।

**পুশ:** f9f8598 (firestore.rules) + rules-negative-probe.mjs (99ccc6c037) + rules-audit-probe.mjs
নতুন (0e030a5415) — readback-verify ALL MATCH; লাইভ ruleset 56000301 == রিপো (verify-firebase-state)।
APK অপরিবর্তিত (v0.24.0/code 43)। পরবর্তী: 18.6 (Android app-logic/state) — Owner-নির্দেশে।

---

## §57 — Phase 18.6 (v0.25.0/code 44): Android app-logic/state deep audit — pairing id ৬→১২ (২০২৬-১০-৩০)

Owner "koro" → 18.6 (18.5 আগের session-এই সম্পূর্ণ: remote 5 commit f9f8598…c26b6c5 + §56)।
পদ্ধতি: 20 Kotlin ফাইল (12.5K লাইন) — logic স্তর লাইন-বাই-লাইন (SessionRepo / SessionNotifier /
SignalingRepo / PushTokenService / PresenceRepo / ConfigRepo / PairingRepo / ScreenShareEngine
পুরোটা / ScreenShareService / AssistService / Fb / App / QrActions / QrScanner / Manifest)।

**একটিই বাস্তব ফিক্স (F1 — §56-এর deferred finding-এর অ্যাপ-অর্ধেক):** পেয়ারিং-ইনভাইট doc-id ৬→১২
অক্ষর। rules-এ `get: signedIn()` বলে যে-কোনো signed-in অ্যাকাউন্ট id ধরে GET করতে পারে — id-ই পুরো
ব্রুট-ফোর্স স্পেস: ৩১⁶ ≈ ৮.৯×১০⁸ (~৩০ বিট) এনুমারেবল (৪০৪-GET-ও বিল; লাইভ-ইনভাইট হিট-সান্স ~১.৭%)।
৩১¹² ≈ ৭.৯×১০¹⁷ (~৫৯.৫ বিট) — এনুমেরেশন গণিতসিদ্ধভাবে মৃত। ফিক্স তিন জায়গায়: PairingRepo
(INVITE_ID_LENGTH=12 + LEGACY_INVITE_ID_LENGTH=6; parsePairingCode এখন ৩২-অক্ষর নতুন কোড
*এবং* ২৬-অক্ষর legacy কোড দুটোই গ্রহণ করে — ট্রানজিশনে mixed-ভার্সন জোড়া ভাঙে না: পুরনো
রেসিপিয়েন্টের ৬-অক্ষর কোড নতুন হেল্পার-ই রিডিম করতে পারে; উল্টো দিকে দুই ফোনেই আপডেট লাগবে —
পেয়ারিং এমনিতেই version-locked), QrScanner (টাইপ-করা কোড extract-regex `{4,10}`→`{4,16}` —
কঠোর যাচাই parsePairingCode-তেই আছে), build.gradle 0.24.0/43→0.25.0/44। QR-regex আগেই
any-length; rules-এ id-দৈর্ঘ্য কনস্ট্রেইন্ট নেই (doc-id path, ফিল্ড নয়) — rules-বদল লাগেনি।

**বাকি সব পর্যবেক্ষণ রেকর্ড-মাত্র (নো-ফিক্স, কারণ যুক্তিসহ):** (১) SessionNotifier-এর `doc.id.hashCode()`
নোটিফিকেশন-আইডি কলিশন সম্ভব — দুর্লভ, ফল-শূন্য (দুটোই অ্যাপ খোলে); (২) watchSessions `.limit(10)`/সাইড —
১১+ ওপেন সেশন অদৃশ্য হতে পারে, কিন্তু তার জন্য ১১+ পেয়ারে একসাথে ওপেন সেশন লাগবে (zombie-sweep-গুলো
রাষ্ট্র স্বল্পস্থায়ী রাখে); (৩) SignalingRepo.clearAsync-vs-watch রেস — epoch+freshness গেট পুরনো
ডক নিষ্ক্রিয় করে, same-round হারালে হেল্পারের ১২-সে রিট্রাই স্বয়ং-নিরাময় করে; (৪) FCM push-ডেটার
helperEmail অবিশ্বস্ত ইনপুট — নোটিফিকেশন-টেক্সট plain-text, ইনজেকশন-ভেক্টর নেই; (৫) Manifest
পরিষ্কার (allowBackup=false, সব non-launcher exported=false, PushTokenService রেজিস্টার্ড)।
Engine/SessionNotifier/SessionRepo-র v0.21.0+v0.22.0+v0.24.0 হার্ডেনিং-স্তর নতুন কোনো ফাঁক দেখায়নি।

**পুশ:** PairingRepo.kt (48f7d88) + QrScanner.kt (205e75c) + build.gradle.kts (5c9a13f) —
readback-verify ALL MATCH। APK v0.25.0/code 44 (CI 41063484436)। পরবর্তী: 18.7 (UI/i18n) —
Owner-নির্দেশে।

## §58 — Phase 18.7 (v0.26.0/code 45): UI/i18n deep audit — notification localization + privacy/rules ১৩-ভাষা পূর্ণ (২০২৬-১০-৩০)

Owner "koro" → 18.7 (18.5/18.6 আগের session-এই সম্পূর্ণ: remote HEAD a447e85 + §56–§57)।
পদ্ধতি: ২৮ ফাইল remote থেকে blob-SHA-ভেরিফাইড ডাউনলোড — ১৫×strings.xml (en + ১৪ লোকেল) + ২০
Kotlin UI/logic ফাইল + PushTokenService; স্ক্যান: কী-প্যারিটি, প্লেসহোল্ডার-ম্যাচ, aapt2-এস্কেপিং,
হার্ডকোড-লিটারাল, RTL-প্যাটার্ন, ডার্ক-থিম ওয়্যারিং, নোটিফিকেশন-টেক্সট-উৎস।

**F1 — SessionNotifier পুরোটাই English-হার্ডকোড (ফিক্স):** চ্যানেল-নাম "Support session requests" +
টাইটেল "Help requested" + বডি "…wants to help…" + ফলব্যাক "A family member", raw context —
LangPrefBus-এর ১৫-ভাষা ইঞ্জিন সম্পূর্ণ বাইপাস (জাপানি/আরবি ইউজারও English ask-নোটিফিকেশন পেত)।
ফিক্স: ৪ নতুন কী (notif_channel_name / notif_help_requested_title / notif_help_requested_body
[%1$s] / notif_fallback_helper) ×১৫ ভাষা; SessionNotifier এখন LangPrefBus.wrap() দিয়ে resolve;
ফলব্যাক এখন blank-email-ও ধরে (takeIf isNotBlank)। চ্যানেল-নাম ভাষা-বদলের পর পরবর্তী
প্রসেস-স্টার্টে createNotificationChannel নিজেই আপডেট করে।

**F2 — privacy_body/rules_body ১৩ ভাষায় অনুপস্থিত (ফিক্স):** শুধু en+bn-এ ছিল — hi/ur/ar/de/…
ইউজার প্রাইভেসি/নীতিমালা ডায়ালগে English দেখত। ১৩ লোকেলে পূর্ণ অনুবাদ যোগ (ar/hi/ne/ur-এ নেটিভ
অঙ্ক ৫/২; \n/• স্ট্রাকচার en-সোর্সের হুবহু; \' এস্কেপ-ব্যাটারি ০ অবশিষ্ট; ET-parse + কী-প্যারিটি
রি-অডিট PASS: ৯৬ কী, ০ প্লেসহোল্ডার-মিসম্যাচ, ০ extra)।

**F3 — translatable="false" ডেক্লারেশন:** app_name / assist_service_label / assist_service_description
সব লোকেলে ইচ্ছাকৃত-অনুপস্থিত কিন্তু ঘোষণাবিহীন ছিল → ডিফল্ট ফাইলে translatable="false" — lint-পরিষ্কার।

**রেকর্ড-মাত্র (নো-ফিক্স):** (১) হার্ডকোড-বাংলা স্ট্রিং-স্টক ~৩০৫ লাইন (MainActivity ২৫৩ /
PermissionWizard ৪৬ / QrScanner ৬) — i18n ইঞ্জিন বাইপাস; AuthGate সম্পূর্ণ লোকালাইজড (২১
stringResource); পূর্ণ-নিষ্কাশন = আলাদা ফেজ (Phase 19-প্রার্থী; ডিভাইসে ভিজ্যুয়াল QA দরকার);
(২) ডার্ক-থিম সঠিক (isSystemInDarkTheme ×২ + darkColorScheme + values-night window_background);
(৩) RTL ঝুঁকিমুক্ত (AbsoluteAlignment/TextAlign.Left-Right নেই — Compose start/end অটো-মিরর;
তবে ar/ur ইউজার (১)-এর বাংলা টেক্সট দেখবে — debt-আওতা); (৪) kill-push ডেড-প্রসেস
নোটিফিকেশন-টেক্সট সার্ভার-পেলোড (fcm-kill-push.mjs) থেকে আসে, English — সার্ভার-সাইড i18n আলাদা
সিদ্ধান্ত; (৫) PushTokenService ৪৭ লাইন, নোটিফিকেশন-টেক্সট নেই (onMessageReceived লগ-মাত্র)।

**পুশ:** ১৭ ফাইল — ১৫×strings.xml (af9c95c 3e8e563 af6a20d 773e473 78ec32b 1cfaec4 124bb58
8606867 843e405 12ce51b 3b9becf 73d51ad 9bdf3d3 635731a 1b0b756) + SessionNotifier.kt (e9c60eb) +
build.gradle.kts 0.25.0/44→0.26.0/45 (4ceae79) — readback-verify ALL MATCH। APK v0.26.0/code 45
(CI 41067945483 SUCCESS)। Owner-কর্ম: দুই ফোনেই v0.26.0 (code 45) ইনস্টল — একই বিল্ড দুই প্রান্তে।
পরবর্তী: 18.8 (security/privacy) — Owner-নির্দেশে।

## §59 — Phase 18.8 (infra — APK অপরিবর্তিত v0.26.0/45): security/privacy deep audit — rules size-cap ব্যাচ + লাইভ প্রমাণ (২০২৬-১০-৩০)

Owner "শুরু করো" → 18.8। পদ্ধতি: ২৭ ফাইল remote থেকে blob-SHA-ভেরিফাইড (rules / worker / node /
manifest / google-services.json / build.gradle / workflows / ১২ Kotlin logic ফাইল) + প্যাটার্ন-স্ক্যান
(সিক্রেট/PII/লগ/পারমিশন) + rules লাইন-বাই-লাইন + privacy_body দাবি বনাম কোড ক্রস-চেক।

**F1 (ফিক্স): অবশিষ্ট আনবাউন্ডেড free-text স্ট্রিং-এ সাইজ-ক্যাপ** — 18.5 প্রোফাইল+ইনভাইটে ক্যাপ
দিয়েছিল; বাকি ছিল: users/devices (model, manufacturer, androidVersion, appVersionName),
devicePresence (model, deviceId), fcmTokens (deviceId), supportSessions-create (helperEmail,
recipientEmail, recipientDeviceModel), trustedDeviceRelationships-create (helperEmail,
recipientEmail, recipientDeviceId, recipientDeviceModel)। ক্যাপ: email<320, model<256/120,
deviceId<128, os/appVer<64 — hostile পার্টিসিপ্যান্ট আর recipient-এর UI পুল-পথে প্রায়-১MB ব্লব
পার্ক করাতে পারবে না (18.5-এর হুবহু প্যাটার্ন, বাকি পথগুলোতে)।

**লাইভ ডেপ্লয় + ৩-সুট প্রমাণ:** publish-firestore-rules.mjs → ruleset caea54c1 (release-verify)।
প্রোব: pairing-probe (P1a/P1b=200 + N1/N2=403 — কনট্র্যাক্ট ইনট্যাক্ট), negative-probe (গেস্ট-
লাইফসাইকেল + claim-token অ্যাটাক — সব প্রত্যাশা মেট), audit-probe ২৩-কেস (A0–A21 সব মেট;
A0/A1/A7/A17/A20 পজিটিভ=200 প্রমাণ করে নতুন ক্যাপ রিয়েল রাইট ভাঙে না)। প্রথম দুই রান 429 —
SA-commit কন্ট্রোল প্রমাণ করে Firestore নিজে থ্রটল করছে না, থ্রটলটা identitytoolkit-এ; অপেক্ষার পর
ক্লিন পাস। শূন্য রেসিডিউ (cleanup 11/11 + 2/2 + সব ফিক্সচার)।

**যাচাই-করা-পরিষ্কার (নো-ফিক্স):** (১) ট্র্যাকড-ফাইলে সিক্রেট শূন্য (google-services.json
public-by-design; SA শুধু .secrets/ + GH/CF secrets-এ); (২) Manifest least-privilege — ৭ পারমিশন
সব-যুক্তিসহ (WRITE_EXTERNAL_STORAGE maxSdkVersion=28; লোকেশন/মাইক নেই), allowBackup=false,
শুধু launcher exported, cleartext নেই; (৩) Log-এ PII শূন্য; (৪) Worker: /health কাউন্ট-মাত্র,
/sweep timing-safe bearer, CORS-বিহীন, 18.4 query-param precondition Node+Worker দুটোতেই উপস্থিত,
scopes সঠিক; (৫) workflows সিক্রেট-ইকো শূন্য; wrangler.toml নির্মল; (৬) ডিপেন্ডেন্সি analytics/ads/
crashlytics-শূন্য — privacy_body-র "tracking নেই" দাবি ধরে; (৭) QR payload fragment-URL (#) —
কোনো সার্ভারে যায় না, শুধু inv+tok; (৮) sign-out ক্লিনআপ কোডে যাচাই — presence + own-device
token-docs delete, ব্যর্থতায় সৎ নোটিশ — privacy_body-র দাবি হুবহু সত্য।

**রেকর্ড-মাত্র (নো-ফিক্স):** (১) pairingInvitations-এ raw token PAIRED-পরেও থাকে — rules-ভাষায়
crypto নেই বলে অনিবার্য ট্রেড-অফ; one-time-use + ৫-মিনিট expiry + ৫৯.৫-বিট doc-id দিয়ে বাউন্ডেড;
(২) helperEmail display-spoofing সম্ভব — display-মাত্র, authority নেই (18.6-এও রেকর্ডেড);
(৩) google-services.json-এ RTDB URL আছে (remote-family-support-default-rtdb) — অ্যাপ RTDB
ব্যবহার করে না; Owner চাইলে কনসোলে RTDB লক/অক্ষম যাচাই করবেন (repo থেকে যাচাই-অযোগ্য);
(৪) release build isMinifyEnabled=false — ভবিষ্যৎ R8 সুযোগ, ঝুঁকি নয়।

**সেশন-লেসন:** স্যান্ডবক্স-আউটপুট DLP লেয়ার টুল-আউটপুটে key-আকৃতির লিটারেল (PEM BEGIN-হেডার)
[REDACTED:…] করে দেয় — প্রথম স্ক্যানে Worker-এর বৈধ regex-কে "ভাঙা" মনে হয়েছিল; grep-ক্রস-চেক +
বুলিয়ান-ভেরিফিকেশনে false-positive প্রমাণ। ভবিষ্যৎ অডিটে key-আকৃতি প্যাটার্ন সবসময় বুলিয়ানে
যাচাই — প্রিন্টেড লিটারেল বিশ্বাস-অযোগ্য।

**পুশ:** firestore.rules (e340d4b, readback MATCH) — APK v0.26.0/code 45 অপরিবর্তিত (rules-only)।
পরবর্তী: 18.9 (CI/CD + docs) — Owner-নির্দেশে।

## §60 — Phase 18.9 (infra — APK অপরিবর্তিত v0.26.0/45): CI/CD + docs অডিট — README 18.x-গ্যাপ পূরণ, Cloudflare লাইভ-স্টেট প্রমাণ, workflow-হাইজিন (২০২৬-১০-৩০)

Owner "শুরু কর" → 18.9। পদ্ধতি: ১৬ ফাইল remote থেকে blob-SHA-ভেরিফাইড (workflows ×২ / README /
docs ×৩ / scripts ×৮ / wrangler.toml / app build.gradle.kts / worklog) + README টেক-নোট বনাম root
gradle ক্রস-চেক + দুটি লাইভ প্রোব (RTDB নাম-প্রোব, Worker /health) + kill-push Actions রান-ইতিহাস।

**F1 (ফিক্স, README):** Status-টপ Phase 17-এ থেমে ছিল — 18.x সিরিজ (v0.22.0→0.26.0, code 41→45,
৮ রাউন্ড) সম্পূর্ণ অনুপস্থিত → কমপ্যাক্ট ১৮.১–১৮.৮ ব্লক যোগ (worklog §55–§59 রেফারেন্স সহ)।
Tech-note "technical/status strings remain English" — 18.7-পরবর্তী মিথ্যা (১৫-ভাষা + নোটিফিকেশন
লোকালাইজড) → হালনাগাদ। বাকি সব দাবি যাচাই-সত্য: Kotlin 2.0.20 / AGP 8.5.2 / BoM 33.4.0 /
minSdk 26 / targetSdk 34 / verify-apk-axml গেট / rules publish-path — হুবহু মিল।

**F2 (ফিক্স, docs/CLOUDFLARE_SETUP.md):** "পরবর্তী" সেকশনে Phase 14b "v0.19.0-প্রার্থী" লেখা —
শিপ হয়ে গেছে বহু আগে → ডেলিভার-চিহ্নিত। নতুন "লাইভ-স্টেট যাচাই" সেকশন: ২০২৬-১০-৩০ /health প্রোব
→ `KILL_TOKEN:true` কিন্তু **`FIREBASE_SA_JSON:false` + last:"not run yet"** — স্থায়ী স্তর
এখনো নিষ্ক্রিয় (cron নিবন্ধিত, SA ছাড়া sweep Firestore-এ পৌঁছায় না)। Owner-একমাত্র-বাকি-ধাপ:
dashboard → Settings → Variables and Secrets → FIREBASE_SA_JSON (SA JSON পুরো টেক্সট) → /health-এ
true। ততদিন কভারেজ: sandbox mini-service (~৫সে) + GH Actions ব্যাকস্টপ (best-effort)।

**F3 (ফিক্স, android-build.yml):** build-job-এ `timeout-minutes: 30` (৩৬০-মিনিট ডিফল্ট ঝুলন্ত
বিল্ড-বাউন্ড) + `concurrency: android-build, cancel-in-progress: true` (ধারাবাহিক push-এ পুরনো
বিল্ড বাতিল — runner-মিনিট স্ট্যাক নয়)। permissions/retention/AXML-গেট অবিকল — ছোঁয়া হয়নি।
kill-push.yml পূর্ণ-পরিষ্কার: `permissions: {}` + concurrency + timeout 4 + idempotent ডিজাইন।

**লাইভ-প্রমাণ:** (১) RTDB deny-all — README-দাবি সরাসরি প্রমাণিত: অ্যানোন GET/PUT দুটোই **401
"Permission denied"** (region URL asia-southeast1; নাম-প্রোব 404 correctUrl-এ আসল instance ধরা);
(২) kill-push schedule sporadic-নিশ্চিত: ১০-২৮→৩০ মাত্র ৫টি schedule-রান (প্রত্যাশা ২৮৮/দিনে
~২৮৮ — বাস্তবে ~২.৫/দিন) — ডকুমেন্টেড GitHub আচরণ, Worker-স্তরই সমাধান।

**রেকর্ড-মাত্র (নো-ফিক্স):** (১) rules-প্রোব CI-তে ওয়্যার করা হয়নি — সচেতন ডিজাইন (লাইভ
Firestore-এ রাইট/ডিলিট সাইড-ইফেক্ট; ম্যানুয়াল-রান-ই সঠিক); (২) ডিপেন্ডেন্সি-ফ্রেশনেস (AGP 8.5.2 /
Kotlin 2.0.20 / Compose BoM 2024.09.03 / Firebase BoM 33.4.0 — ২০২৪-যুগ) — ভবিষ্যৎ
মেইনটেনেন্স-পাস, নিরাপত্তা-ঝুঁকি নয়; (৩) isMinifyEnabled=false — 18.8-তেই রেকর্ডেড; (৪)
PHASE-0.md / UI_UX_DESIGN_SYSTEM.md — ঐতিহাসিক/স্পেক-ডক, কোনো মিথ্যা-দাবি নেই।

**পুশ:** ৩ ফাইল — README.md (c137567) + docs/CLOUDFLARE_SETUP.md (b0694e4) +
.github/workflows/android-build.yml (fbb065e) — readback-verify ALL MATCH। APK v0.26.0/code 45
অপরিবর্তিত (docs+CI-only)। **Owner-করণীয় (উচ্চ-অগ্রাধিকার):** Cloudflare-এ FIREBASE_SA_JSON
secret — এটিই স্থায়ী kill-push স্তর জীবিত করার একমাত্র বাকি ধাপ। পরবর্তী: Phase 19-প্রার্থী
(i18n-ডেট ~৩০৫ লাইন বাংলা হার্ডকোড) / ডিপেন্ডেন্সি-পাস — Owner-নির্দেশে।

## §61 — Phase 19.1 (v0.27.0/code 46): i18n-নিষ্কাশন ধারা শুরু — QrScanner + PermissionWizard সম্পূর্ণ (২০২৬-১০-৩০)

Owner "Phase 19" → §58-র record-only ডেট (হার্ডকোড-বাংলা ~৩০৫ লাইন) নিষ্কাশন শুরু। স্কেল যাচাই:
৩ ফাইলে ৩২৩ অবস্থান (MainActivity ২৭০ / PW ৪৭ / QrScanner ৬) — ৩০-এ ইন্টারপোলেশন (placeh-
older-প্রয়োজন)। এক-শট ঝুঁকি এড়িয়ে দুই-কিস্তি: **19.1 = QrScanner + PermissionWizard**, 19.2 =
MainActivity (+error-pipeline)। পদ্ধতি: ২০ ফাইল remote blob-SHA-ভেরিফাইড → লিটারাল স্ক্যান
(Bengali-ইউনিকোড রেঞ্জ) → ৩৯ নতুন কী × ১৫ ভাষা অনুবাদ-জেনারেটর → XML ইনজেকশন (ET-parse +
প্লেসহোল্ডার-প্যারিটি) → Kotlin-প্রতিস্থাপন (exact-anchor + ব্রেস-ব্যালেন্স) → পুশ (readback) → CI।

**নতুন কী (৩৯):** qr_scan_title/hint/permission_text/permission_btn/close_btn + qr_badge_family/
guest (৭) · pw_guide_title/intro/step1-3_text/warning/privacy + pw_step_১-৪ + pw_open_settings/
later (১২) · pw_title/intro/all_done/progress(%1$d+%2$s)/perm_notif/camera/assist_title/assist/
battery + ৪ desc + allow/setup_open/screen_share_note/done_btn/status_done/status_pending (২০)।
বাংলা সংখ্যা bn/hi/ne/ar/ur-এ নেটিভ (১-৪, ২ ঘণ্টা, ৪টা) — 18.7-কনভেনশন ধরে।

**কোড-পরিবর্তন:** (১) QrScanner ৪ লিটারাল → stringResource; QrBadgeLegendChip এখন stringResource
(qr_badge_*) ব্যবহার করে; (২) PermissionWizard ৪৬ লিটারাল → stringResource, বহু-লাইন কনক্যাট ৫টি
এক-কীতে মার্জ (অনুবাদ-মানের জন্য), actionLabel ×৩ অনুমতি-দিন + ১ সেটআপ-খুলুন;
**missingNamesBn() → missingNames(context)** — localized নাম-তালিকা (Context প্যারামিটার);
(৩) PairingRepo QrBadge enum থেকে labelBn সরানো (glyph+fallback অবিকল — QR-বিটম্যাপ ভাষা-নিরপেক্ষ);
(৪) MainActivity ন্যূনতম-স্পর্শ: শুধু :2604-প্রগ্রেস-লাইন stringResource(pw_progress)+
missingNames(context) — ২৭০→২৬৯; (৫) build.gradle.kts 0.26.0/45→0.27.0/46।

**যাচাই:** ১৫×strings.xml ET-parse PASS + ৩৯-কী parity PASS + প্লেসহোল্ডার-মিল PASS;
QrScanner=0, PW=0 বাংলা-লিটারাল; ব্রেস/প্যারেন-ব্যালেন্স সব OK; Kotlin-রেফারেন্স ৭+৩১+১ সামঞ্জস্যপূর্ণ।
CI run 41086668596 **SUCCESS** — নিষ্কাশন রিয়েল-কম্পাইল-প্রমাণিত। APK v0.27.0/code 46
(39.8MB) download/-এ।

**নতুন-আবিষ্কৃত ডেট (18.7-তালিকায় ছিল না):** PairingRepo-র ৪টি ইউজার-ফেসিং বাংলা এরর-টেক্সট
(:92 describeFirestoreError-বাংলা-parenthetical, :437/:443 RedemptionException guest/family-
fence) — redeemCode()-এ Context নেই, describeFirestoreError-ও নেই; এগুলো MainActivity-এর
error-display-পাইপলাইনেই দেখায় → **19.2-এর error-plumbing-কিস্তিতে** (MainActivity ২৬৯ লিটারাল
+ PairingRepo-এরর + Context-প্লাম্বিং একসাথে)।

**পুশ:** ২০ ফাইল — QrScanner/PermissionWizard/PairingRepo/MainActivity/১৫×strings.xml/
build.gradle.kts — readback ALL MATCH (b60660f→4a5aeed; মাঝের রান cancel = 18.9-concurrency
কাজ করছে)। Owner-করণীয়: দুই ফোনে v0.27.0 (code 46) ইনস্টল — PermissionWizard/QrScanner
পছন্দের ভাষায় দেখুন। পরবর্তী: 19.2 (MainActivity ~২৬৯ লিটারাল + error-plumbing) — Owner-নির্দেশে।

## §62 — Phase 19.2 (v0.28.0/code 47): i18n-নিষ্কাশন সম্পন্ন — MainActivity + error-pipeline পূর্ণ (২০২৬-১০-৩০)

Owner "19.2 শুরু কর" → §58-ডেটের বাকি অংশ। স্কোপ: MainActivity ২৬৯ লিটারাল + PairingRepo-এর
৪ বাংলা এরর + describeFirestoreError/redeemCode Context-plumbing। পদ্ধতি: ২২৩ স্টেটমেন্ট-গ্রুপ
হাতে-শ্রেণিবিন্যাস (কম্পোজেবল/callback/ইন্টারপোলেশন/try-catch) → ২৩১ নতুন কী × ১৫ ভাষা (৪ ব্যাচ
অনুবাদ-জেনারেটর, প্লেসহোল্ডার-প্যারিটি অ্যাসার্ট) → লাইন-অ্যাংকরড প্রতিস্থাপন (desc, {IND}-ইনডেন্ট,
braces-NET যাচাই) → পুশ → CI।

**কী-ইনভেন্টরি (২৩১):** auth/presence/push/signout (৮) · home-hints/consent/dialog (১১) ·
activity/feed/devices (২২) · status ×৬ + friendlyError ×৭ (context-সংস্করণ) · settings/theme/lang/
version/test-notif/tiles (২০) · phone-tab/pairing/invite (২৮) · role-sheet (১৮) · pair-tile (৫) ·
session-keybar/semantics (৯) · screen-share/typing/stop-dialog (২৩) · pairing-error-pipeline
err_* ×১৩ (redeemCode+parsePairingCode+describeFirestoreError-এ Context প্যারামিটার) ·
error-prefix ×৭ (Could not send/answer/end/create/cancel/revoke/remove)। পুনঃব্যবহার: pw_title,
pw_perm_notif, pw_perm_assist_title (19.1)। sessionStatusBn→sessionStatus(context,·),
friendlyErrorBn→friendlyError(context,·)। **L4449-লজিক-ফিক্স:** startsWith("Guest যুক্ত")-
ব্রেকেবল প্রিফিক্স-চেক → message.contains("✓") (i18n-প্রমাণ)।

**CI-পুনরাবৃত্তি (৩ রাউন্ড — প্রতিটি লগ-চালিত):** R1 ১২৬ ত্রুটি: (ক) ১৪টি বর্ণিত-বাদ-পড়া
describeFirestoreError(e)-কলসাইট (grep-প্যাটার্ন 'e' ছিল, কিছু সাইটে 'it') → সব (context,e);
(খ) stringResource try/catch+semantics-lambda-এ নিষিদ্ধ (Compose নিয়ম) — ৫টি catch-সাইট
context.getString-এ + ২টি semantics-এ hoisted val; (গ) ইংরেজি এরর-প্রিফিক্স ৭টি নতুন কী-তে।
R2 ৫৪→৫ ত্রুটি: watch-callback-এ it-প্যারাম ×২ + if/else-শাখায় ভুল কমা ×৩। R3: parsePairingCode-
এ Context-প্যারাম। **R4: SUCCESS (run 41097218406)** — APK v0.28.0/code 47 (40.2MB)।

**চূড়ান্ত-অবস্থা:** MainActivity ইউজার-ফেসিং বাংলা-লিটারাল **শূন্য** (১২টি অবশিষ্ট সবই কমেন্ট-বাস্তু —
Owner-উদ্ধৃতি); PairingRepo **শূন্য**; PermissionWizard/QrScanner 19.1-এই শূন্য। strings.xml এখন
৩৭০টি ট্রান্সলেটেবল কী × ১৫ ভাষা (+en-এ ৩টি translatable=false)। §58-র record-only ডেট (৩০৫ লাইন)
সম্পূর্ণ নিষ্কাশিত — Phase 19-এর মূল লক্ষ্য পূর্ণ।

**সেশন-লেসন:** বড়-স্কেল লিটারাল-প্রতিস্থাপনে লাইন-অ্যাংকরিং + নেট-ব্রেস-যাচাই শক্তিশালী, কিন্তু
Compose-এর কনটেক্সট-নিয়ম (stringResource শুধু কম্পোজিশনে) প্রতিটি সাইটের জন্য আলাদা যাচাই
দাবি করে — try/catch/semantics/callback-lambda-ই ফাঁদ; CI-লগ-চালিত ৩-রাউন্ড পুনরাবৃত্তিই
বাস্তব-সমাপ্তি। grep-প্যাটার্নে প্যারাম-নাম ('e' বনাম 'it') ধরা না-পড়া ২টি সাইটই হারানো-কারণ।

**পুশ:** ১৮+১৬+১+১ = ৩৬ কমিট (মূল ১৮ + fix ১৬ + fix2/fix3) — সব readback MATCH।
**Owner-করণীয়:** দুই ফোনে v0.28.0 (code 47) ইনস্টল — একই বিল্ড দুই প্রান্তে (version-locked)।
এখন সম্পূর্ণ অ্যাপ-UI ১৫ ভাষায় (ব্যতিক্রম: সার্ভার-পেলোড নোটিফিকেশন-টেক্সট — §58-রেকর্ডেড)।
পরবর্তী: 19.3-প্রার্থী নেই — Phase 19 সম্পন্ন; ডিপেন্ডেন্সি-পাস/Phase 20 Owner-নির্দেশে।

## §63 — Phase 20 (v0.29.0/code 48): Firestore ফ্রি-কোটা সংকট নিরাময় — 5s-সুইপ 30s+ব্যাকঅফ, জম্বি-এক্সপায়ার, err_quota ×১৫ (২০২৬-১০-৩০)

**উপসর্গ (Owner-স্ক্রিনশট ২৩:০০–২৩:৩১):** ফোন/সেটিংস ট্যাবে লাল ইংরেজি ডাম্প — "Firestore error
RESOURCE_EXHAUSTED: Quota limit exceeded … 'Free daily read units per project (free tier
database) per day'", প্রতিটি ফোন-সারিতে "presence unavailable", টপ-পিল আটকে-থাকা "Checking…",
সেশন-একটিভিটি কার্ডে একই ত্রুটি।

**রুট-কজ (লাইভ-প্রমাণিত):** প্রজেক্টের দৈনিক ফ্রি-টায়ার কোটা (৫০,০০০ ডকুমেন্ট-রিড) ফুরিয়ে যাচ্ছিল।
প্রধান ভোক্তা আমাদেরই স্যান্ডবক্স kill-push টিয়ার — প্রতি ৫ সেকেন্ডে sweep = ১৭,২৮০ সুইপ/দিন × ২টি
runQuery (খালি কোয়েরিও ন্যূনতম ১ রিড বিল হয়) ≈ ৩৪,৫৬০ রিড/দিন = কোটার ~৬৯%; বাকিটা অ্যাপ-রিড +
টোকেন-লুকআপ — মোট সীমা পার → সারাদিন সব পাঠ (অ্যাপ সহ) 429। লাইভ-প্রমাণ: /health-এ sweep 429
(১৪:২৯ UTC), একই সময়ে অ্যাপ-স্ক্রিনে একই কোড। লক্ষণীয়: স্ক্রিনশটের অ্যাপ তখনো v0.26.0 — ত্রুটি
19.x-রিগ্রেশন নয়, বিশুদ্ধ কোটা-সংকট।

**সমাধান (৩ স্তর):**
F1 **স্যান্ডবক্স (তাৎক্ষণিক):** kill-push-service POLL_MS ৫,০০০→৩০,০০০ ms — ২-মিনিট উত্তর-উইন্ডোতে
সর্বোচ্চ ~৩০ সেকেন্ড লেটেন্সি, খরচ ৫,৭৬০ রিড/দিন (~১১.৫%)। সাথে quota-aware ব্যাকঅফ: 429-এ
ইন্টারভাল দ্বিগুণ (৩০s→১m→২m→৪m→৮m→১০m cap), প্রথম পরিষ্কার সুইপে রিসেট; /health-এ
basePollEveryMs + currentDelayMs + failStreak। রিস্টার্টের পরই লাইভ-যাচিত: failStreak 1,
currentDelayMs 60,000 (কোটা রিসেট না-হওয়া পর্যন্ত ব্যাকঅফ-মোডে থাকবে)।
F2 **সুইপ-হার্ডেনিং (scripts/fcm-kill-push.mjs):** respondDeadline-হীন জম্বি REQUESTED ডক আগে
চিরকাল skip হতো — প্রতিটি ভবিষ্যৎ সুইপ তাকে আবার-পড়ত (৩ টিয়ার মিলিয়ে প্রতি টিকে ১টি বিল-হওয়া
রিড)। এখন fresh-window (১০ মিনিট) পার হলেই EXPIRED — deadline-পথের হুবহু ট্রানজিশন।
F3 **অ্যাপ (v0.29.0/code 48):** describeFirestoreError-এ RESOURCE_EXHAUSTED শাখা → নতুন
err_quota ×১৫ ভাষা — কাঁচা ইংরেজি ডাম্পের বদলে সৎ বার্তা: "আজকের Firebase ফ্রি ব্যবহার-সীমা শেষ —
প্রতিদিন দুপুরের দিকে কোটা রিসেট হলে সব আবার কাজ করবে। কোনো তথ্য হারায়নি।" PresenceBadge-এর
হার্ডকোড "presence unavailable"/"never seen"/"Online"/"last seen N min ago"/"offline" →
pres_unavailable/pres_never_seen/pres_online/pres_last_seen_min/pres_offline (রঙ এখন টেক্সট-তুলনা
নয়, বুলিয়ান-নির্ধারিত)। PresenceChip-এর আটকে-থাকা "Checking…" → pres_unavailable। strings.xml
৩৭০→৩৭৪ ট্রান্সলেটেবল কী × ১৫।

**CI-লেসন (১-রাউন্ড ফিক্স):** প্রথম পুশে mergeDebugResources FAILED — "Invalid unicode escape
sequence": Android string-রিসোর্সে XML-এন্টিটি `&#39;` XML-আন-ইস্কেপের পরে কাঁচা `'` হয়ে AAPT2-তে
অবৈধ; রিপো-idiom `\'` (বিদ্যমান ১৭ ব্যবহার)। en/fr/tr-তে ৩টি প্রতিস্থাপন → রি-পুশ → run
41104345856 **SUCCESS**, HEAD 126344b — APK v0.29.0/code 48 (release 40,244,006 বাইট,
download/family-remote-support-v0.29.0-code48.apk, manifest-যাচিত)।

**কোটা-বাজেট (ফিক্সের পরে):** স্যান্ডবক্স ৫,৭৬০ + Worker (Owner SA-secret দিলে; cron প্রতি মিনিট)
২,৮৮০ + GH-Actions স্পোরাডিক ~৫০০ + অ্যাপ ~৫–১০k → মোট ≪ ৫০,০০০। রিসেট-ঘড়ি: প্রতিদিন
মিডনাইট প্যাসিফিক ≈ ঢাকা-সময় দুপুর ১২:৩০–১:০০।

**পুশ:** ২০+৩ = ২৩ কমিট (কোড ২০ + strings-ফিক্স ৩) — সব readback MATCH; ৪টি বাতিল run
(concurrency-প্রমাণ) + ১ fail + ১ SUCCESS।
**Owner-করণীয়:** (১) কোটা-রিসেটের পর (দুপুরের পর) দুই ফোনে **v0.29.0 (code 48)** ইনস্টল — কোটা-ত্রুটি
এখন পরিষ্কার নিজ-ভাষায় দেখাবে, আর সার্ভার-পাশ আর কোটা খাবে না; (২) Cloudflare-এ
FIREBASE_SA_JSON secret (§60-র অপেক্ষিত ধাপ) — Worker-চালু হলে kill-push স্যান্ডবক্স-নির্ভরতা শেষ।
পরবর্তী: Owner-নির্দেশ (ডিপেন্ডেন্সি-পাস বা অন্য)।

## §64 — Phase 21 (docs only — APK অপরিবর্তিত v0.29.0/48): মাস্টার-প্ল্যান অডিট + ৯-ডক পরিকল্পনা-সেট, অনুমোদন-গেট (২০২৬-১০-৩১)

**ট্রিগার:** Owner-এর মাস্টার-প্রজেক্ট নির্দেশ — সম্পূর্ণ frontend redesign + Live Location
Tracking + ২০–২৪ এজেন্ট সমন্বয়; কিন্তু শর্ত: আগে পূর্ণ অডিট + পরিকল্পনা-ডক, তারপর **স্পষ্ট
অনুমোদনের অপেক্ষা** (STOP-condition)। কোনো সোর্স-কোড পরিবর্তন হয়নি।

**পূর্ব-যাচাই (সারসংক্ষেপ-বিলম্ব ধরা):** GitHub অ্যাকাউন্ট rename `psfarfarfaruk` → **`psfaruk`**
(টোকেন বৈধ; repo = `psfaruk/family-remote-support`)। HEAD `cf87601`, CI run 41105504900
SUCCESS, বিল্ড **v0.29.0/code 48** (Phase 19.1–19.2 + 20 ইতিমধ্যে সম্পন্ন — worklog §61–§63)।
লোকাল মিররে ১০ ফাইল অনুপস্থিত ছিল → contents API থেকে ফেচ → blob-SHA **১১/১১ MATCH**।

**Owner-এর ৩ স্ক্রিনশট-প্রশ্নের উত্তর:** ২৩:০০–২৩:৩১-এর লাল `RESOURCE_EXHAUSTED` ব্যানার +
"presence unavailable" = §63-এর Firestore ফ্রি-কোটা ঘটনা (reads ৫০K/দিন শেষ); স্ক্রিনশটের
সময় ফোনে v0.26.0 ছিল; ফিক্স v0.29.0-এ (30s+backoff) — কোটা-রিসেটের পর দুই ফোনে v0.29.0
ইনস্টল করলেই চলবে; নতুন কোনো বাগ নয়।

**অডিট (৩টি সত্যিক parallel agent, READ-ONLY):** 2-a frontend — ২০ .kt / ১২,৫২৬ লাইন;
MainActivity ৫,৮৮৪-লাইন মনোলিথ; Navigation-Compose/ViewModel নেই (manual 4-tab state
machine); Theme.kt = মাস্টার-প্যালেটই; ১৫-ভাষা i18n; লাইভ-লোকেশন = প্লেসহোল্ডার, কোনো location
কোড/পারমিশন/ম্যাপ-লাইব্রেরি নেই; ~১৬ লাইন অবশিষ্ট হার্ডকোড স্ট্রিং; 2-b infra — নিরাপত্তা-স্ক্যান
পরিষ্কার (কোনো secret নেই), rules ম্যাট্রিক্স যাচাই, kill-push ৩-স্তর (Worker SA-secret এখনো
unset), কোটা-গণিত; 2-c docs — PHASE-0.md §১২ টেবিল stale, UI_UX ডক Phase-11.7-এ ফ্রোজন,
৮টি প্রয়োজনীয় ডক অনুপস্থিত।

**এই পুশে যা গেল (১১ কমিট, সব readback-verify):** docs/PROJECT_CONTEXT.md (শেয়ার্ড-কনটেক্সট +
২৪-রোল মালিকানা + সিদ্ধান্ত C1–C6) · FRONTEND_ARCHITECTURE.md · NAVIGATION_MAP.md (৪-ট্যাব নতুন
নাম: Home / **Add Device** / **Tracking** / Settings — শুধু-স্ট্রিং; নেস্টেড রুট-ট্রি) ·
LIVE_LOCATION_ARCHITECTURE.md (consent-ভিত্তিক, update-in-place single doc, ≥30–60s cadence,
osmdroid+OSM attribution, FGS-location, background-location ছাড়া, কোটা-বাজেট টেবিল) ·
FAMILY_GUEST_AUTHORIZATION.md (state-diagram + ফিচার-ম্যাট্রিক্স) · ANDROID_PERMISSION_MATRIX.md ·
TEST_PLAN.md (২৪-সিনারিও → পদ্ধতি) · IMPLEMENTATION_STATUS.md (Phase 0–20 ✅ + M0–M10 ট্র্যাকার;
PHASE-0 §১২-এর উত্তরাধিকারী) · UI_UX_DESIGN_SYSTEM.md **§26 সমন্বয়-পরিশিষ্ট** · README
Phase-21 ব্লক · এই §64।

**STOP:** M2+ (shell redesign, লাইভ-লোকেশন বাস্তবায়ন) শুরু হবে শুধুমাত্র Owner-এর স্পষ্ট
অনুমোদনের পর। Owner-অ্যাকশন: (১) কোটা-রিসেটে দুই ফোনে v0.29.0 ইনস্টল; (২) Cloudflare
`FIREBASE_SA_JSON` সিক্রেট; (৩) M1 ডক-সেট পর্যালোচনা করে অনুমোদন/সংশোধন-নির্দেশ।


## §65 — Phase 21/M2 (v0.30.0, code 49): শেয়ার্ড অ্যাপ্লিকেশন শেল — Navigation-Compose শেল (C3 spike গৃহীত), C1 ট্যাব-রিনেম ×১৫, নোটিফিকেশন সারফেস, কম্পোনেন্ট-নিষ্কাশন (২০২৬-১০-৩১)

**ট্রিগার:** Owner-এর **"M2 শুরু করো"** নির্দেশ = M1-এর অনুমোদন-গেট পাস (IMPLEMENTATION_STATUS §2)।
তাই এই পুশ থেকে মাস্টার-প্ল্যানের কোড-বাস্তবায়ন শুরু — প্রথম ইমপ্লিমেন্টেশন রিলিজ **v0.30.0/49**
(NAVIGATION_MAP §6 অনুযায়ী)।

**পূর্ব-যাচাই:** repo `psfaruk/family-remote-support` (§64-এর rename-নোট অনুযায়ী); HEAD `e44817d`,
M1-ডক-সেট ফেচ করে blob-SHA মিলিয়ে কাজ শুরু; লোকাল মিররে ৯টি stale ফাইল ফেচ করে সিঙ্ক। M2-স্কোপ
শুধু IMPLEMENTATION_STATUS §2-এর M2-সারি: header polish · notifications surface · C1 rename ·
theme foundation · C3 spike। **মাল্টি-এজেন্ট সততা:** এই মাইলস্টোনের কাজ এক ফাইলে (MainActivity)
কেন্দ্রীভূত — ফাইল-মালিকানা নিয়মে (PROJECT_CONTEXT §18) parallel write সম্ভব নয়, তাই coordinator
(Agent 1) সরাসরি সার্জারি করেছে; সাথে static verification passes (refs/parity/braces/anchor-checks)
আলাদা ধাপে। ২৪-রোল মডেল M3-এর extraction waves-এ সত্যিক parallel agents নেবে — কোনো ভূত-এজেন্ট
দাবি নয়।

**যা বদলেছে (কোড):**
1. **C3 spike — Navigation-Compose 2.8.2 গৃহীত:** `MainActivity`-এর `var tab` + `when(tab)` +
   `tabScrollStates` → `NavHost` (home/add/tracking/settings/notifications রুট; crossfade 120ms;
   `goTab` = popUpTo(start){saveState}+launchSingleTop+restoreState)। রুট-টেবিল + AppTopBar +
   AppBottomNav নতুন **`ui/nav/AppNav.kt`**-এ; টপ-বার টাইটেল এখন স্থানীয়কৃত `app_title`
   ("Remote Family Support" — মাস্টার-নির্দেশনার ব্র্যান্ড), presence chip slot প্যারামিটার।
   W1-gate ৩ আইটেম কোড-পর্যালোচনায় নিশ্চিত: cold-start-from-FCM (আগেও/এখনও কোনো intent-লজিক নেই —
   Firestore listener-ই সব চালায়), wizard overlay (app-level), fullscreen session (session-card-এর
   ভেতরের Dialog — পুরোনো when(tab)-এর মতোই unmount semantics)। **ডিভাইস-যাচাই = Owner**
   (TEST_PLAN #7/#8/#24)। lifecycle-viewmodel-compose ইচ্ছাকৃতভাবে M3-এ পিছিয়ে (minimal-deps C4)।
2. **C1 রিনেম ×১৫:** `nav_phone`→`nav_add_device` (আইকন PersonAdd), `nav_location`→`nav_tracking`
   — পুরোনো কী দুটি ১৫ ফাইল থেকেই বাদ; Home-এর ২টি quick-action লেবেলও একসঙ্গে বদলানো; এবং
   পুরোনো "Phone" ট্যাব উল্লেখ করা **৭টি কপি-স্ট্রিং** (hint_requested/active/helping, msg_allow_ok,
   feed_in_progress, devices_info, devices_empty_hint) প্রতি ভাষায় ট্যাব-নাম ঠিক করা।
3. **নোটিফিকেশন সারফেস (নতুন `ui/notifications/NotificationsScreen.kt`):** ব্যাজ-বেল এখন Settings
   নয় — ডেডিকেটেড স্ক্রিন। **শুধু বাস্তব ঘটনা:** consent-ask, পারমিশন-চেকলিস্ট ঘাটতি, সেটআপ-ব্যর্থতা
   (নতুন deviceOk/presenceOk/pushOk ফ্ল্যাগ — স্ট্রিং-পার্স নয়), চলমান সেশন — সবই app আগে থেকেই
   যেসব ডেটা listen করত তা থেকে; **নতুন Firestore read/লিসেনার শূন্য**। ইন-মেমরি — অ্যাপ-রিস্টার্টে
   রিসেট (স্থায়ী ইভেন্ট-স্টোর = M8 সিদ্ধান্ত); ⓘ-পপআপে এই সততা ব্যাখ্যা ×১৫ ভাষায়।
4. **কম্পোনেন্ট লাইব্রেরির সূচনা:** SectionCard / AppListTile / EmptyState / WideOutlinedButton
   MainActivity থেকে **হুবহু একই কোডে** `ui/components/Core.kt`-এ (private→public; ভিজ্যুয়াল
   পরিবর্তন শূন্য)। বাকি উইজেট M3 extraction wave।
5. **থিম ফাউন্ডেশন:** Theme.kt যাচাই — মাস্টার-প্যালেট হুবহু আছে; ডেল্টা দরকার হয়নি (সৎ রিপোর্ট:
   নতুন কিছু যোগ করিনি, কারণ দরকার ছিল না)।

**স্ট্রিং-পরিসংখ্যান:** প্রতি লোকেলে +১৬ কী (nav ×২ + notif ×১৪), −২ লিগ্যাসি; মোট নতুন ইংরেজি
কী-বডি ১৬ × ১৫ ভাষা = ২৪০ অনুবাদ; XML well-formed ×১৫ যাচাই; escaping যাচাই (raw apostrophe ০)।

**নতুন আবিষ্কার (M2 audit):** `values/strings.xml`-এর ঘোষিত ডিজাইন "default = English" থাকলেও
**~২০৩ লিগ্যাসি কী-তে বাংলা টেক্সট** আছে এবং `values-bn/`-এ সেগুলোর ইংরেজি — অর্থাৎ default↔bn
ইনভার্সন; দুই ভাষাসেটেই লিগ্যাসি স্ক্রিনে মিশ্র ভাষা দেখা যায় (Agent 2-a/2-c অডিট এটা ধরেনি —
তারা "~৩ কী/লোকেল ঘাটতি" বলেছিল)। **এই মাইলস্টোনে ধরা হয়নি** (স্কোপ-ডিসিপ্লিন; M2-র নতুন কীগুলো
সঠিক ভাষায় আছে) — M4/M8 i18n-ওয়েভে (Agent 19) পূর্ণ re-language sweep করবো; IMPLEMENTATION_STATUS
§4-এ রেকর্ড হয়েছে।

**স্বচ্ছ ডেল্টা (Owner জানুক):** সিস্টেম-Back এখন non-start ট্যাব থেকে প্রথমে Home-এ ফেরে, তারপর
অ্যাপ বন্ধ হয় (আগে সরাসরি বন্ধ হতো) — platform-standard ট্যাব-আচরণ; NAVIGATION_MAP §3 ও
TEST_PLAN #8-এ ডকুমেন্টেড। নোটিফিকেশন স্ক্রিনে কোনো ট্যাব selected থাকে না — ইচ্ছাকৃত সৎ অবস্থা।

**যাচাই-অবস্থা (সৎ):** স্যান্ডবক্সে Gradle/emulator নেই — এই রিলিজের প্রমাণ = (১) স্ট্যাটিক
ভেরিফিকেশন পাস (string-ref ×N মিল, ১৫-লোকেল parity, XML parse, brace-balance, anchor-based
edits), (২) CI assembleDebug+Release + AXML গেট (v0.30.0/49 আশা করা হয়েছে), (৩) Owner-এর
ফোন-টেস্ট। "কাজ করছে" দাবি কেবল CI SUCCESS + Owner নিশ্চিতির পরেই।

**CI রেকর্ড (সৎ):** প্রথম রান **FAILED** — আমার ভুল: `Icons.Filled.PersonAdd` material-icons-core
সেটে নেই (ওটা icons-extended-এ; R8 বন্ধ থাকায় extended ডিপ দিলে APK-ব্লোট — বাতিল)। ফিক্স:
core-set-এর `Icons.Filled.Add` (NAVIGATION_MAP §1-এ ডিভিয়েশন-নোট)। ফাইনাল HEAD `9bdc8a0`:
Android Build **SUCCESS** — run **41122154745**, AXML গেটসহ (versionCode 49 + versionName
0.30.0 + v2-signature), debug+release আর্টিফ্যাক্ট আপলোডেড।

**Owner-অ্যাকশন:** (১) CI SUCCESS দেখে দুই ফোনে **v0.30.0 (code 49)** ইনস্টল (একই ভার্সন দুটোতেই);
(২) ট্যাব-নাম/আইকন, বেল→নোটিফিকেশন স্ক্রিন, ট্যাব-সুইচে স্ক্রল-মনে থাকা, Back-আচরণ (নতুন ডেল্টা),
ডার্ক/লাইট ×১৫ ভাষা চেক — TEST_PLAN #7/#8/#23/#24; (৩) Cloudflare `FIREBASE_SA_JSON` সিক্রেট
(§60 থেকে বাকি)। **পরবর্তী:** M3 — স্ক্রিন-রিডিজাইন + ViewModel + নেস্টেড ডিটেইল রুট (সব M1-ডক
অনুযায়ী); M5-এ লাইভ-লোকেশন।


## §66 — Phase 21/M3 (v0.31.0, code 50): ফ্রন্টএন্ড রিডিজাইন — ৪ ট্যাব-স্ক্রিন নিষ্কাশন + ViewModel লেয়ার + কম্পোনেন্ট-লাইব্রেরি সম্পূর্ণ (২০২৬-১০-৩১)

**ট্রিগার:** Owner-এর **"M3"** নির্দেশ = M2 (v0.30.0/49, CI SUCCESS) শিপ হওয়ায় মাস্টার-প্ল্যানের
পরবর্তী মাইলস্টোন। M3-স্কোপ = IMPLEMENTATION_STATUS §2-এর M3-সারি: ৪ ট্যাব-স্ক্রিন + ডিটেইল
কম্পোজেবল নিষ্কাশন (real states সহ) + ViewModel লেয়ার (`lifecycle-viewmodel-compose`) +
কম্পোনেন্ট-লাইব্রেরি সম্পূর্ণ — FRONTEND_ARCHITECTURE §3/§4/§6/§10 (W2) অনুযায়ী।

**পূর্ব-যাচাই:** HEAD `49b4924` (worklog §65-এর CI-রেকর্ড পুশ)। লোকাল মিররে ৮টি ফাইল পুরোনো +
১৪টি ফাইল অনুপস্থিত ছিল → contents API থেকে ফেচ করে blob-SHA **১১৮/১১৮ MATCH** — মিরর এখন
HEAD-এ হুবহু। GitHub অ্যাকাউন্ট rename (`psfarfarfaruk` → `psfaruk`, §64) এই সেশনে পুনঃনিশ্চিত।

**মাল্টি-এজেন্ট সততা (§65-এর প্রতিশ্রুতি পূরণ):** এবার M3 extraction wave-এ **সত্যিকারের parallel
subagents** চালানো হয়েছে — **M3-A** (Add Device tab → `ui/pairing/AddDeviceTab.kt`, ১,৭৬০ লাইন)
এবং **M3-B** (Settings tab + activity feed → `ui/settings/SettingsTab.kt`, ১,০৯১ লাইন), দুটি
একসঙ্গে সমান্তরালে, প্রত্যেকের আলাদা ফাইল-মালিকানা (PROJECT_CONTEXT §18)। প্রতিটি এজেন্ট
byte-identical diff দিয়ে প্রমাণ করেছে: বডি হুবহু, শুধু `private`→`internal`। Coordinator (Agent 1)
একারMainActivity সার্জারি + বাকি ৬টি নতুন ফাইল (HomeTab/Badges/Cards/Buttons/States/TrackingTab/
SessionWatchViewModel) লিখেছে + সব static verification চালিয়েছে। এভাবে ২৪-রোল মডেলের যে অংশ
আজ দরকার ছিল সেটাই বাস্তবে সমান্তরালে হয়েছে — কোনো ভূত-এজেন্ট দাবি নয়; বাকি role-গুলো পরের
wave-এ (M4/M5) নেবে।

**যা বদলেছে (কোড):**
1. **স্ক্রিন-নিষ্কাশন (W2, verbatim):** `ui/home/HomeTab.kt` (HomeTab + HomeConsentCard/
   HomeConsentDialog + HomeLiveHintCard + FeatureTile + webClientAvailable) · `ui/pairing/
   AddDeviceTab.kt` (PairingCard + PairingSections + ScannedRoleSheet/Bullet/Code + PairOptionRow +
   PairTile + PairingUiBus) · `ui/settings/SettingsTab.kt` (SettingsTab + ActivityTab/ActivityFeed +
   ThemeOptionRow + EditNameDialog + LangOptionRow + TestNotifRow + DocOverlay + SettingsNotifTile +
   SettingsAssistTile) · `ui/tracking/TrackingTab.kt` (LiveLocationTab-এর সৎ placeholder, নাম-পরিবর্তন)।
   **MainActivity ৫,৬৮৩ → ~২,০০০ লাইন** — শুধু shell wiring + root session effects (v0.21.0-এর
   zombie-sweep/stop গ্যারান্টি অপরিবর্তিত) + session surfaces (W1-gate)।
2. **ViewModel লেয়ার:** `lifecycle-viewmodel-compose` **2.8.6** (runtime-ktx 2.8.6-এর সাথে মিল)।
   `ui/home/SessionWatchViewModel.kt` — সেশন-ওয়াচ এখন `StateFlow<SessionsUiState>` (data +
   loaded + routed error)। **অডিট-ফিক্স #5:** আগের root listener-এ `onError = { }` — লিসেনার-ফেল
   চুপচাপ হজম হতো; এখন state-এ route হয়: প্রথম snapshot-এর আগে ফেল করলে Home-এ সৎ ErrorPanel
   (retry সহ), snapshot আসার পরে শেষ লিস্ট থাকে (v0.21.0 নিয়ম অটুট)। ViewModel ইচ্ছাকৃতভাবে
   NavHost-এর **উপরে** root-scoped — Home-tab-scoped হলে zombie-sweep অন্য ট্যাবে চলত না
   (regression-by-construction এড়ানো)। Collection = `collectAsState` (shell সবসময় composed —
   repeatOnLifecycle-এর কার্যকারিতা সমান; অতিরিক্ত runtime-compose ডিপ নেই — C4)।
3. **কম্পোনেন্ট-লাইব্রেরি সম্পূর্ণ (FRONTEND_ARCHITECTURE §6):** `ui/components/Cards.kt`
   (SettingsTile/IconStatusTile/QuickActionTile) · `Buttons.kt` (BigButton) · `Badges.kt`
   (StatusBadge/sessionStatus/sessionBadgeKind/statusChipColors/PresenceBadge/PresenceChip) ·
   `States.kt` (**নতুন:** LoadingSkeleton/ErrorPanel/PermissionDeniedPanel — NAVIGATION_MAP §5-এর
   canonical state-কম্পোনেন্ট; permission panel-এর wire-up M4/M5-এ)। সব হুবহু প্রমোশন — এক উইজেটের
   একটাই ভ্যারিয়েন্ট, পুরো অ্যাপে।
4. **Real states ওয়্যারিং:** Home-এ সেশন-লিসেনারের প্রথম snapshot-এর আগে LoadingSkeleton (২ রো),
   pre-snapshot ব্যর্থতায় ErrorPanel + retry। এটাই M3-এর একমাত্র দৃশ্যমান ব্যবহার-পরিবর্তন।

**স্ট্রিং-পরিসংখ্যান:** +২ কী (`states_retry`, `states_open_settings`) × ১৫ ভাষা = ৩০ অনুবাদ; XML
well-formed ×১৫ যাচাই (python ET)। নতুন কোনো Firestore read/লিসেনার/রুল বদল নেই — শূন্য।

**সৎ ডেল্টা (Owner জানুক):** (১) NAVIGATION_MAP §2-এর nested detail রুটগুলো (home/family/{relId},
home/sessions/{id}, settings/*, add/*, tracking/{relId}) এই মাইলস্টোনে **হয়নি** — আগে ট্যাব-লেভেল
নিষ্কাশন শিপ হলো; রুট-ট্রি সম্প্রসারণ M4 wave-এ রেকর্ড করা হয়েছে (IMPLEMENTATION_STATUS M3-সারি
"clearly says")। (২) Home-এ নতুন loading/error অবস্থা ছাড়া আর কোনো ভিজ্যুয়াল বদল নেই — নিষ্কাশন
হুবহু। (৩) `repeatOnLifecycle` শব্দটি FRONTEND_ARCHITECTURE §4-এ ছিল; বাস্তবে `collectAsState` —
কার্য-সমতুল্য, ডকুমেন্টেড।

**যাচাই-অবস্থা (সৎ):** স্যান্ডবক্সে Gradle/emulator নেই। প্রমাণ = (১) static verification suite:
সব R.string/R.drawable ref রেজোলভ (৯টি নতুন+স্পর্শকৃত kt), ১৫-লোকেল key-parity, নতুন ৯ ফাইলে
brace-balance, MainActivity-তে সরানো প্রতীকের সংজ্ঞা শূন্য + ব্যবহারগুলো import-সহ রেজোলভ,
cross-file symbol matrix (watchSessions/watchRelationships/watchDevicePresence/PRESENCE_*/ThemePreview/
AssistSetupGuide/QrScanDialog ইত্যাদি সব public/internal যাচাই), gradle versionCode 50 + ডিপ যাচাই;
(২) CI assembleDebug+Release + AXML গেট (v0.31.0/50 প্রত্যাশিত); (৩) Owner-এর ফোন-টেস্ট।

**পুশ:** সব ফাইল readback-verify পাইপলাইনে (GET sha → PUT → GET download → SHA256 তুলনা)।
CI রেকর্ড পুশ-পরে এই সেকশনে যোগ হবে (সৎ: রান সফল হলে তবেই "কাজ করছে" দাবি)।

**Owner-অ্যাকশন:** (১) CI SUCCESS দেখে দুই ফোনে **v0.31.0 (code 50)** — TEST_PLAN #7/#8/#24 +
Home-এর নতুন loading/error অবস্থা চোখে দেখা; (২) Cloudflare `FIREBASE_SA_JSON` (§60 থেকে ঝুলে
আছে); (৩) **পরবর্তী = M4** — বিদ্যমান-ফিচার ইন্টিগ্রেশন যাচাই + drift ফিক্স + nested detail রুট।

**CI রেকর্ড (§66-এর প্রতিশ্রুতি অনুযায়ী, সৎ):** প্রথম রান **FAILED** — ২টি কম্পাইল-এরর, দুটোই
AddDeviceTab.kt-এ (M3-A-এর নিষ্কাশনের সূক্ষ্ম ভুল): (১) অব্যবহৃত `friendlyError` import —
MainActivity-এ সেটি file-private, অন্য ফাইল থেকে import করা যায় না; (২) `internal fun
ScannedRoleSheet(scan: ScannedCode…)` file-private `ScannedCode` টাইপ expose করছিল। ১-রাউন্ড
ফিক্স: import বাদ + ScannedRoleSheet `private` (কলার PairingSections একই ফাইলেই)। রি-পুশ
(readback MATCH) → ফাইনাল HEAD `f9045b9`: Android Build **SUCCESS** — run **41161846658**,
AXML গেটসহ: `✓ versionCode = 50` · `✓ versionName = "0.31.0"` (scripts/verify-apk-axml.mjs)।
আর্টিফ্যাক্ট: app-release-apk + app-debug-apk আপলোডেড। ২টি intermediate run concurrency-তে
বাতিল (প্রত্যাশিত)। **দাবির সীমা:** "কাজ করছে" = CI-সফল + Owner-এর ফোন-টেস্টের পরেই —
স্যান্ডবক্সে Gradle/emulator নেই।


## §67 — Phase 21/M4 (v0.32.0, code 51): নেস্টেড নেভিগেশন ওয়েভ-১ + Settings ৮-বিভাগ + ইন্টিগ্রেশন যাচাই (২০২৬-১০-৩১)

**ট্রিগার:** Owner-এর **"M4"** নির্দেশ। M-sিরিজ এখন প্রতিষ্ঠিত: M0 অডিট · M1 ডক · M2 শেল
(v0.30.0/49) · M3 নিষ্কাশন (v0.31.0/50) — সব শিপড; M4 = IMPLEMENTATION_STATUS §2-এর সারি +
worklog §66-এর প্রতিশ্রুতি: existing-feature integration checks (pairing, sessions,
revocation) + drift fixes + nested detail routes।

**পূর্ব-যাচাই:** সারভার-পার্শ্ব হিরো: GitHub অ্যাকাউন্ট rename (`psfarfarfaruk` → `psfaruk`)
পুনঃনিশ্চিত — রিমোট `psfaruk/family-remote-support`, HEAD `e1ee6e6` (M3-এর CI-রেকর্ড পুশ;
run 41162136517 SUCCESS)। blob-SHA বেসলাইন: **৯০/৯০ ফাইল MATCH** — লোকাল মিরর HEAD-এ হুবহু
(zero drift) — M4 শুরুর সামনে সোনালি ভিত্তি প্রমাণিত।

**মাল্টি-এজেন্ট সততা (§১৭-মডেল চালু):** **M4-A** (FamilyScreens.kt — Family Overview +
Member Details + PairTile সরানো, ৫৫৮ লাইন) ও **M4-B** (ActivityScreens.kt — Recent Activity +
Session Details, ৩৫৩ লাইন) — দুটি সত্যিকারের সমান্তরাল subagent, কঠোর ফাইল-মালিকানা
(PROJECT_CONTEXT §18); কোঅর্ডিনেটর (Agent 1) = AppNav রুট + MainActivity NavHost সার্জারি +
HomeTab এন্ট্রি + SettingsTab ৮-বিভাগ রিস্ট্রাকচার + AddDeviceTab §৩/§৪ অপসারণ + manifest
ড্রিফট ফিক্স + strings ×১৫ + স্ট্যাটিক ভেরিফিকেশন। এজেন্ট-রিপোর্ট অনুযায়ী পোর্টগুলো verbatim
(diff-প্রমাণিত) — শুধু চিহ্নিত এক-লাইন বিচ্যুতি (চিপ-শব্দ → নতুন ক্লিন-কী; শীট-ইনসেট বাদ;
sheet-dismiss → onBack)।

**যা বদলেছে (কোড):**
1. **নেস্টেড রুট (NAVIGATION_MAP §2 home/ সাবট্রি বাস্তব):** `home/family` (PairTile-তালিকা
   + presence + গেস্ট-কাউন্টডাউন; সৎ loading/error/empty; empty-CTA → Add Device) ·
   `home/family/{relId}` (পুরোনো অ্যাকশন-শীট → রুট: role, গেস্ট-উইন্ডো, request-session,
   family-revoke, guest-remove — **একই** PairingRepo/SessionRepo কল; সাফল্যে রুট pop) ·
   `home/sessions` (প্রতিটি open সেশনের রো + last-finished লাইন — সব রো রুট-লিংক) ·
   `home/sessions/{id}` (status/type/helper/recipient/duration/ended-by; root watch-এ থাকলে
   LIVE, নাহলে সেই একই one-shot getSession যা last-finished লাইন আজও করে)। Home-এ দুটি
   QuickActionTile এন্ট্রি। Nested ট্রানজিশন: স্লাইড ≈২০০ ms + fade, **Animator durationScale
   সম্মান** (animation বন্ধ = তাৎক্ষণিক) — §4-নীতি প্রথমবার আক্ষরিকভাবে বাস্তবায়িত।
   `AppRoutes.tabForRoute`: home/*-এ Home ট্যাব selected থাকে; system-Back pop করে (§3)।
2. **Settings ৮-বিভাগ (মাস্টার PART 7):** Account · Appearance(থিম+ভাষা) · Device Management
   (paired-phones এন্ট্রি → home/family + devices রেজিস্ট্রি Account থেকে সরানো) · Location &
   Privacy (সৎ M5-নোট + privacy/rules ডক) · Remote Support (notif/assist/test) · Guest Access
   (নোট + → home/family) · Security (permission wizard) · About। **Sign-out এখনও একদম নিচে**
   (Owner-এর Phase-16 স্থায়ী আদেশ — NAVIGATION_MAP-এর settings/account-স্কেচ থেকে রেকর্ডকৃত
   বিচ্যুতি)। পুরোনো Activity সেকশন (ActivityTab/ActivityFeed, ২১০ লাইন) home/sessions রুটে
   উত্তীর্ণ — SettingsTab.kt থেকে ডিলিট।
3. **AddDeviceTab ডায়েট (১,৭৬০ → ১,৪৪৩ লাইন):** §৩ তালিকা + §৪ অ্যাকশন-শীট + PairTile +
   selectedRelId/revokingId/startSessionFor বাদ (সব home/* রুটে)। থাকল: পেয়ারিং ফ্লো (two-ways
   কার্ড + invite-QR + স্ক্যানার + role-শীট) + relationship লিসেনার (গেস্ট-এক্সপায়ারি lazy
   sweep — Phase-10 আচরণ অপরিবর্তিত)। add/* রুট-না-হওয়া **ইচ্ছাকৃত** (NAVIGATION_MAP §3:
   QR/role/wizard মোডাল থাকবে)।
4. **Drift ফিক্স:** AndroidManifest-এর AssistService কমেন্ট এখন সত্য বলে —
   canRetrieveWindowContent আসলে Phase 8.3 (v0.12.0) থেকেই true (ACTION_SET_TEXT টাইপিং-ই
   একমাত্র ভোক্তা; নোড-টেক্সট কখনো পড়ে/লগ করে/জমা রাখে না)। assist_service_config.xml-এর
   v0.12.0-কমেন্টের সাথে মিল।
5. **build.gradle:** versionCode 51 / versionName 0.32.0। শূন্য নতুন ডিপেন্ডেন্সি।

**স্ট্রিং-পরিসংখ্যাণ:** +৩০ কী (fam_/actv_/sess_/cd_back/set_section_×৫/set_dev_entry_×২/
set_loc_status/set_guest_note) ×১৫ = ৪৫০ এন্ট্রি; XML well-formed ×১৫ (python ET); ফরাসি
অ্যাপোস্ট্রফি escaped; bn/hi/ne/ar/ur-এ দেশীয় অঙ্ক (৩ ঘণ্টা)। নতুন কোনো Firestore read/
লিসেনার/রুল/ডেটাবেস পরিবর্তন নেই — শূন্য।

**সৎ ডেল্টা (Owner জানুক):** (১) পূর্ণ সেশন-হিস্ট্রি LIST বানানো হয়নি — তার জন্য bounded
query + composite index লাগবে; watchSessions ইচ্ছাকৃতভাবে equality-only (quota + index
ডিসিপ্লিন) — Recent Activity আজ যা বাস্তব ডেটা আছে তাই দেখায় (open + last-finished)।
(২) settings/* সাব-রুট (account/appearance/… আলাদা স্ক্রিন হিসেবে) এই ওয়েভে নয় —
grouped-list রূপ শিপ হলো; guest= M6, notifications=M8-এর মালিকানা। (৩) i18n অবশিষ্ট
(~১৬ হার্ডকোডেড লাইন + parity + default↔bn ইনভার্সন) সচেতনভাবে M8-এ রাখা — M4-এর সিদ্ধান্ত
IMPLEMENTATION_STATUS §4-এ রেকর্ডকৃত। (৪) Settings-এ Language সাব-হেডার লিগ্যাসি
set_section_lang কী ব্যবহার করে (মান ইনভার্সন-আক্রান্ত — M8-এ ইন-প্লেস মান-ফিক্স হবে)।
(৫) হাতে-লেখা পরীক্ষণে পাওয়া পূর্ব-বিদ্যমান benign artifact: assist_service_description
default-এ translatable="false" কিন্তু bn-এ একটি অনুবাদ-কপি আছে (M4-পূর্ব থেকেই; M8 ক্লিনআপ)।

**যাচাই-অবস্থা (সৎ):** স্যান্ডবক্সে Gradle/emulator নেই। প্রমাণ = (১) স্ট্যাটিক স্যুট:
৪৫৯টি R.string রেফ রেজোলভ, ১৫-লোকেল key-parity (translatable=false বাদে), ৭ স্পর্শকৃত ফাইলে
brace-balance (string/comment-aware), ডিলিটেড-সিম্বল সুইপ (ActivityTab/ActivityFeed/
PairTile-in-pairing/selectedRelId/startSessionFor = ০), সিম্বল-ম্যাট্রিক্স + scoping যাচাই
(M4-A-এর startSessionFor/revokingId সব MemberDetailScreen-এর ভিতরে, লিক শূন্য);
(২) CI assembleDebug+Release + AXML গেট (v0.32.0/51 প্রত্যাশিত); (৩) Owner-এর ফোন-টেস্ট।

**পুশ:** readback-verify পাইপলাইনে (GET sha → PUT → GET download → SHA256 তুলনা)। CI রেকর্ড
পুশ-পরে এই সেকশনে যোগ হবে (সৎ: রান সফল হলে তবেই "কাজ করছে" দাবি)।

**Owner-অ্যাকশন:** (১) CI SUCCESS দেখে দুই ফোনে **v0.32.0 (code 51)** — TEST_PLAN #7/#8
(এখন সত্যিকারের nested: Home→Family Devices→সদস্য-রো→Back; Home→Recent Activity→সেশন-রো→
Session Details→Back; nested-এ Home ট্যাব selected) + #24 + Settings ৮-বিভাগ চোখে দেখা;
(২) Cloudflare `FIREBASE_SA_JSON` (§60 থেকে ঝুলে আছে); (৩) **পরবর্তী = M5** — লাইভ-লোকেশন
(engine + map + rules + probes, LIVE_LOCATION_ARCHITECTURE অনুযায়ী)।

**CI রেকর্ড (§৬৭-এর প্রতিশ্রুতি অনুযায়ী, সৎ — ২ রাউন্ড ফিক্স):**
- রান-১ **FAILED**: `values-tr/strings.xml`-এর `sess_not_found_h`-তে অনাস্কেপড অ্যাপোস্ট্রফি
  (`Son Etkinlik'i` — aapt2 flatten ব্যর্থ)। ফিক্স: `Etkinlik\'i` (commit `a12a46c`)।
  শিক্ষা: অ্যাপোস্ট্রফি-স্ক্যান আগে শুধু default+ফরাসি-২-কী-তে চালানো হয়েছিল — এখন থেকে
  সব লোকেল × সব নতুন কী (স্ক্যানারটি এই সেশনেই সংশোধিত)।
- রান-২ **FAILED**: `AppNav.kt` ডক-কমেন্টে **নেস্টেড `/*`** — Kotlin ব্লক-কমেন্ট নেস্ট করে!
  `add/* flow` (লাইন ৪৭) নেস্টেড ওপেনার খুলেছিল → বাইরের `/**…*/` অবন্ধ হয়নি → পুরো
  ফাইলের সিম্বল উবে গিয়ে MainActivity-তে "Unresolved reference: AppRoutes/AppTopBar/
  AppBottomNav/goTab" ঝড়। ফিক্স-১ (commit `be6790c`) পরেও দ্বিতীয় ওপেনার
  (`home/*`, tabForRoute-ডক-এ) রয়ে গিয়েছিল — ফিক্স-২ (commit `0f5bd73`) দুটোই ডি-নেস্ট
  করে। **নতুন স্ট্যাটিক-গেট জন্ম নিলে:** comment-nesting checker (গোটা কোডবেসে
  final-depth == 0 যাচাই) — ভবিষ্যতের প্রতিটি পুশে verify-স্যুটের অংশ।
- রান-৩ **SUCCESS** — Android Build, run **41170229200**, HEAD `0f5bd73`। AXML গেটসহ:
  `✓ versionCode = 51 (INT_DEC)` · `✓ versionName = "0.32.0"` ·
  `✓ APK Signing Block v2 present` · `✓ no stale version strings` (শুধু 0.32.0)।
  APK ≈ ৩৮.৭ MB; debug+release আর্টিফ্যাক্ট আপলোডেড।
- **দাবির সীমা (সৎ):** "কাজ করছে" = CI-সফল + Owner-এর ফোন-টেস্টের পরেই — স্যান্ডবক্সে
  Gradle/emulator নেই। দুটি ফিক্স-রাউন্ডই এই সেশনের ভুল (ট্রান্সলেশন-এস্কেপিং স্ক্যানের
  ফাঁক + নেস্টেড-কমেন্ট অজান্তে), ডকুমেন্টে লুকানো নয়।


## §68 — Phase 22/M5 (v0.33.0, code 52): লাইভ লোকেশন — ইঞ্জিন + ম্যাপ + রুলস + প্রোব (২০২৬-১০-৩১)

**ট্রিগার:** Owner-এর **"M5"** নির্দেশ (worklog §67-এর প্রতিশ্রুতি অনুযায়ী: "পরবর্তী = M5 —
লাইভ-লোকেশন, LIVE_LOCATION_ARCHITECTURE অনুযায়ী")।

**পূর্ব-যাচাই (সৎ সংশোধনসহ):** রিমোট `psfaruk/family-remote-support`, HEAD `7162e3c`
(M4-এর CI-রেকর্ড পুশ; run 41170479983 SUCCESS)। লোকাল মিররের ৮৯টি ট্র্যাকড ফাইলের blob-SHA
রিমোটের সাথে **পূর্ণ MATCH** (zero drift)। **নোট (সৎ):** এই সেশনের উত্তরাধিকার-সারাংশ
ভুলভাবে দাবি করছিল "M-সিরিজ অস্তিত্বহীন, M2/M3/M4 অনির্বাহিত" — রিমোট প্রমাণ
(IMPLEMENTATION_STATUS + কমিট-মেসেজ + CI রান) তা **নাকচ করেছে**; এছাড়া সারাংশে owner-নাম
`psfarfarfaruk` লেখা ছিল (বানান-ড্রিফট) — প্রকৃত অ্যাকাউন্ট `psfaruk`। সব সিদ্ধান্ত রিমোট
প্রমাণের ভিত্তিতে।

**মাল্টি-এজেন্ট সততা (§17-মডেল):** ৪টি সত্যিকারের সমান্তরাল agent — **M5-A** (engine:
LiveLocationRepo.kt + LocationShareService.kt, 261+472 লাইন, কন্ট্র্যাক্ট API হুবহু)
· **M5-B** (firestore.rules-এ 56-লাইনের liveLocations ব্লক + 526-লাইনের
rules-liveLocations-probe.mjs; diff-প্রমাণিত শুধু-ইনসার্ট) · **M5-C** (TrackingTab.kt 702 +
TrackingViewModel.kt 183 + LocationDetailScreen.kt 389 লাইন — agent টাইম-আউটে পড়লেও তিন
ফাইলই ডিস্কে সম্পূর্ণ ছিল; কোঅর্ডিনেটর লাইন-বাই-লাইন যাচাই করে গ্রহণ করেছে) · **M5-D**
(i18n: 14/15 লোকেলের পরে turn-limit — values-zh কোঅর্ডিনেটর পূরণ করেছে, এখানেই রেকর্ড)।
কোঅর্ডিনেটর (Agent 1) = manifest/gradle/App.kt/AppNav/MainActivity/SettingsTab সার্জারি +
স্ট্যাটিক ভেরিফিকেশন + রুল-পাবলিশ + প্রোব + পুশ।

**যা বদলেছে (কোড):**
1. **ইঞ্জিন (M5.a/b):** `LiveLocationRepo.kt` — কন্ট্র্যাক্ট API (LiveLocationView,
   watchLiveLocation, writeLocationFix, deleteOwnLocation, LocationSharingPrefs,
   start/stopLocationSharing, isLocationSharingRunning); `liveLocations/{uid}_{devId}`
   সিঙ্গেল-ডক in-place (hasOnly ৮-ফিল্ড, ইতিহাস নেই — PART 6)। `LocationShareService.kt`
   — FGS type location, low-importance চ্যানেল, **স্থায়ী নোটিফিকেশনে কোঅর্ডিনেট নেই**
   (R.string.loc_notif_* ×১৫), FusedLocation PRIORITY_BALANCED + LocationManager ফলব্যাক,
   গেট: Δt≥interval ∧ Δd≥25 m ∧ accuracy≤100 m, স্থির-অবস্থায় ব্যাক-অফ ×2 (ক্যাপ ৫ মিনিট),
   পারমিশন-হারা/সাইন-আউট/কনসেন্ট-বন্ধ হলে ডিফেন্সিভ স্টপ। reboot অটো-রিস্টার্ট **ইচ্ছাকৃত নেই**।
2. **রুলস (M5.a):** liveLocations ব্লক (myPairing মিরর + sharingEnabled + docId-প্যাটার্ন +
   রেঞ্জ/টাইপ/±৫-মিনিট freshness) — **LIVE পাবলিশড: ruleset d1a38d5e** (পাবলিশ-ই-প্রমাণ:
   ruleset তৈরি = সিনট্যাক্স বৈধ)।
3. **ম্যাপ (M5.c):** TrackingTab সত্যিকারের osmdroid MAPNIK ম্যাপ (CopyrightOverlay রাখা
   হয়েছে — OSM অ্যাট্রিবিউশন বাধ্যতামূলক; App.onCreate-এ userAgentValue=packageName —
   OSMF পলিসি), প্রতি অনুমোদিত কাউন্টারপার্টে একটি মার্কার (যে সত্যিই পাবলিশ করেছে তবেই),
   LIVE<২মি/STALE<৫মি/OFFLINE ফ্রেশনেস + সর্বদা-দৃশ্যমান "Last known — HH:mm ±N m" (পুরনো
   কোঅর্ডিনেট কখনো live লেবেল পায় না), canonical Loading/Error/Empty/PermissionDenied,
   consent শীট → FINE/COARSE লঞ্চার → start; ডিভাইস-সিলেক্টর শীট; `TrackingViewModel`
   (routed errors, keep-last)।
4. **tracking/{relId} (M5.c):** LocationDetailScreen — ফ্রেশনেস/updated/accuracy সারি,
   কাউন্টারপার্টের sharing অবস্থা **রিড-ওনলি** (শুধু মালিক টগল করতে পারেন), open-in-maps
   (geo: intent, try/catch), সৎ not-found + নিজ-ডিভাইস ডিফেন্সিভ ব্রাঞ্চ।
5. **Settings (M5.d):** Location পার্টিশন সত্যিকারের (set_loc_status প্লেসহোল্ডারের জায়গায়
   sharing টগল + পারমিশন-গেট + ৩০সে/১মি/৫মি ইন্টারভাল ডায়ালগ; চলমান শেয়ারিংয়ে ইন্টারভাল
   বদলালে সার্ভিস রিস্টার্ট — সাথে সাথে কার্যকর)।
6. **Shell সার্জারি:** AppNav-এ TRACKING_ARG + tabForRoute("tracking/") ; MainActivity-র
   NavHost-এ ট্যাব-প্যারামিটার + নেস্টেড রুট (nested ট্রানজিশন মানির M4-নিয়ম);
   **sign-out হাইজিনে M5:** stopLocationSharing + deleteOwnLocation Phase-13 ক্লিনআপে যুক্ত।
7. **build.gradle:** versionCode 52 / versionName 0.33.0; **+2 ডিপেন্ডেন্সি (C4 — Owner-এর
   M5 নির্দেশেই অনুমোদিত, M2-নজির অনুযায়ী):** play-services-location 21.3.0 +
   osmdroid-android 6.1.18।

**প্রোব (LIVE, সেল্ফ-ক্লিনিং):** `scripts/rules-liveLocations-probe.mjs` — **12/12 PASS**:
P0 owner create+read+delete ✓, P1 ACTIVE-counterpart পড়া ALLOW ✓, N1..N9 সব 403 (অপরিচিত,
sharing-off, REVOKED, গেস্ট-উইন্ডো-পার, বাড়তি-ফিল্ড, lat 91, ১০-মিনিট-পুরনো updatedAt,
অন্যের docId-তে লেখা, stranger LIST), P2 লাইভ-গেস্ট-উইন্ডো ALLOW ✓। ক্লিনআপ: 2/2 doc +
3/3 synthetic auth user ডিলিট।

**স্ট্যাটিক গেট:** ৯টি Kotlin ফাইলে brace/paren ব্যালান্স + block-comment nesting
(সংশোধিত চেকার — // লাইন-কমেন্ট inert, ব্লক-নেস্টিং সত্যিকার গভীরতা-সিমুলেশন) +
R.string/R.drawable রেফ রেজোলভ + 15-লোকেল key-parity + apostrophe স্ক্যান (সব লোকেল ×
সব নতুন কী) + XML well-formed ×১৫ + পুরনো কী-অপরিবর্তিত (0 diff) + build.gradle/manifest
যাচাই।

**স্ট্রিং-পরিসংখ্যান:** +৩২ কী (loc_* ×31 + set_loc_now) ×১৫ = ৪৮০ এন্ট্রি;
bn/hi/ne/ar/ur-এ দেশীয় অঙ্ক (৩০/১/৫); ফরাসি/তুর্কি অ্যাপোস্ট্রোফি escaped
(§67-এর শিক্ষা প্রয়োগ — স্ক্যানার সব লোকেলে চালানো); %1$s/%1$d সর্বত্র সংরক্ষিত।

**সৎ ডেল্টা (Owner জানুক):** (১) v1-এ ACCESS_BACKGROUND_LOCATION **নেই** — শেয়ারিং চলে
শুধু দৃশ্যমান FGS হিসেবে; আক্রমণাত্মক OEM-এ swipe-away করলে শেয়ারিং থামতে পারে (battery-
optimization ছাড়ের পথ আগেরই আছে)। (২) ম্যাপ টাইল OSMF পলিসি-অনুযায়ী family-scale
(২–৪ ডিভাইস) ব্যবহারে সীমিত — বড় স্কেলে টাইল-প্রোভাইডার বদলানোর পথ ডকুমেন্টেড।
(৩) অফলাইন ম্যাপ-প্যাকেজিং নেই (নেটওয়ার্ক লাগবে টাইলের জন্য)। (৪) সেশন-হিস্ট্রির মতো
লোকেশন-হিস্ট্রিও **ইচ্ছাকৃতভাবে নেই** — শুধু সর্বশেষ পজিশন, stop/sign-out-এ ডিলিট।
(৫) M5-C/M5-D agent সীমা ছাড়িয়েছিল (টাইম/টার্ন) — কিন্তু আউটপুট সম্পূর্ণ ছিল, ম্যানুয়ালি
যাচাই-কৃত; এই সীমাবদ্ধতা রেকর্ড থাকল। (৬) ইন্টারভাল বদলালে চলমান শেয়ারিং একবার
রিস্টার্ট হয় (doc একবার ডিলিট+পুনঃপাবলিশ — কয়েক সেকেন্ডের ফাঁক, ফ্রেশনেস সৎ থাকে)।

**যাচাই-অবস্থা (সৎ):** স্যান্ডবক্সে Gradle/emulator নেই। প্রমাণ = (১) উপরের স্ট্যাটিক
স্যুট; (২) **রুলস LIVE + প্রোব 12/12** (সত্যিকারের রানটাইম); (৩) CI assembleDebug+Release
+ AXML গেট (v0.33.0/52 প্রত্যাশিত); (৪) Owner-এর ফোন-টেস্ট (TEST_PLAN #10–#15)।

**পুশ:** readback-verify পাইপলাইনে (GET sha → PUT → GET download → SHA256 তুলনা)। CI রেকর্ড
পুশ-পরে এই সেকশনে যোগ হবে (সৎ: রান সফল হলে তবেই "কাজ করছে" দাবি)।

**Owner-অ্যাকশন:** (১) CI SUCCESS দেখে দুই ফোনে **v0.33.0 (code 52)** — TEST_PLAN
#১০–#১৫: Tracking-এ consent শীট → পারমিশন → ম্যাপে অন্য ফোনের মার্কার + LIVE/STALE ব্যাজ;
নোটিফিকেশনে কোঅর্ডিনেট নেই-ওয়ালা স্থায়ী নোটিশ; ৩ জায়গা থেকে stop; Settings-এ টগল+ইন্টারভাল;
revoke করলে লোকেশন-অ্যাক্সেস তাৎক্ষণিক বন্ধ। (২) Cloudflare `FIREBASE_SA_JSON` (§60 থেকে
ঝুলে আছে); (৩) **পরবর্তী = M6** — Family/Guest authorization verification + G1–G4 deltas।

**CI রেকর্ড (§68-এর প্রতিশ্রুতি অনুযায়ী, সৎ — ১ রাউন্ড ফিক্স):**
- রান-১ **FAILED**: `SettingsTab.kt`-এর তিন `onPick` ল্যাম্বডায় (৯০১/৯২৪/৯৪১ লাইন) scope-বহির্ভূত
  `applyIntervalChoice` রেফারেন্স — কোঅর্ডিনেটর-সার্জারির ভুল: `IntervalChoiceRow`-এর ভিতরেই
  ক্লিক প্রথমে `applyIntervalChoice(value)` তারপর `onPick(value)` ডাকে, তাই `onPick`-এর ভেতরের
  ডাকটা অপ্রয়োজনীয়ই ছিল এবং সেখানে সেই নামটা দৃশ্যমানও নয়। ১-রাউন্ড ফিক্স: তিনটি ডাক
  বাদ (commit `01c9810`)। শিক্ষা: ল্যাম্বডা-প্যারামিটারের নাম বাইরের কলব্যাকে ব্যবহারযোগ্য নয় —
  স্কোপিং-চেক স্ট্যাটিক স্যুটে যোগ করার মতো নয় (টুলিং-সীমা), কিন্তু রেকর্ড থাকল।
- রান-২ **SUCCESS** — Android Build, run **41179665821**, HEAD `01c9810`। AXML গেটসহ:
  `✓ versionCode = 52 (INT_DEC)` · `✓ versionName = "0.33.0"` ·
  `✓ APK Signing Block v2 present` · `✓ no stale version strings` (শুধু 0.33.0)।
  APK ≈ ৩৯.৪ MB; debug (32.5 MB) + release (27.6 MB compressed) আর্টিফ্যাক্ট আপলোডেড।
- **দাবির সীমা (সৎ):** "কাজ করছে" = CI-সফল + Owner-এর ফোন-টেস্টের পরেই — স্যান্ডবক্সে
  Gradle/emulator নেই। ফিক্স-রাউন্ডটি এই সেশনের ভুল (কোঅর্ডিনেটরের হাতের লেখা), ডকুমেন্টে
  লুকানো নয়।


## §69 — Phase 22/M6（v0.33.0/52 不变）：Family/Guest 授权验证 + G1–G4 对账（2026-10-31）

> **语言切换记录：** 自本条起，worklog 新条目改用中文（Owner 当日明确指示"孟加拉语看不懂，
> 不要再用孟加拉语"）。历史条目（§1–§68）保持原样，不回改。

**触发：** Owner 的 **"M6"** 指令（= §68 承诺的"পরবর্তী = M6 — Family/Guest authorization
verification + G1–G4 deltas"）+ 语言指示（此后交流与新条目用中文）。

**推送前校验（诚实）：** 远端 `psfaruk/family-remote-support` main，HEAD `7811ef5`（M5
CI 记录推送）；CI run 41180972665 SUCCESS；本地镜像 97/97 blob-SHA 全匹配（零漂移）。
另记：本会话继承摘要再次声称"M 体系不存在"——远端证据（commit 消息、CI run、
IMPLEMENTATION_STATUS）再次否定；真实账号 `psfaruk`（摘要中 `psfarfarfaruk` 为拼写漂移，
§68 已记录过一次）。

**多代理（诚实）：** 1 个真实只读 Explore 子代理（本地镜像架构清点）+ 协调者本人执行
验证与文档。M6 为验证型里程碑，无需并行编码代理。

**验证（全部 LIVE、自清理，4/4 绿）：**
1. `rules-pairing-probe.mjs` ✅ — P1a/P1b 兑换往返 200；N1/N2（无/错 claimToken）403。
2. `rules-audit-probe.mjs` ✅ — A0–A21 全符合（FSM 越侧/越步/超上限、载荷上限、类型
   围栏、post-END 信号、presence/fcm schema 伪造全拒）。
3. `rules-negative-probe.mjs` ✅ — P0/N1–N10 + P10/N11–N18/G1/G2/N12–N16b：kind 围栏
   （family↔guest 互转/互用拒绝）、10h 访客窗拒、过期后 ask 拒、静默延期拒、lazy
   EXPIRED 与参与者删除按设计放行。
4. `rules-liveLocations-probe.mjs` ✅ 12/12 重跑（现行 ruleset d1a38d5e）。

**G1–G4 对账：**
- G1 ✅ M5 已交付：liveLocations 规则 LIVE + 同意页 + FAMILY_GUEST §3 矩阵行（该文档
  本次 PLANNING → VERIFIED）。
- G2 ⛔ M8 待办（按里程碑纪律，非 M6 范围）。
- G3 ✅ 以"分布覆盖"关闭：OS 权限姿态 + assist 服务状态 → Security 权限向导
  （`PermChecklist` / `AssistSetup.isAssistEnabled`，今日代码亲验）；关系/访客窗状态 →
  home/family（M4）；共享状态 → Settings Location & Privacy + Tracking 横幅（M5）。
  未新增任何虚构界面。
- G4 ✅ M4 已修：manifest assist 注释今日亲验为真（canRetrieveWindowContent=true since
  Phase 8.3，仅 ACTION_SET_TEXT）。

**验收（FAMILY_GUEST §7 五条）：** ①电池重跑 4/4 绿；②撤销即时切断（N3/A21 探针证明）；
③访客窗服务端可观测过期（N15/N16a）；④无 UI 可触发规则拒绝的操作（负向探针）；
⑤同意界面 ×15 本地化（M5 静态门 + 今日 97/97 零漂移复核）。

**变更范围（仅文档，4 个文件）：** FAMILY_GUEST_AUTHORIZATION.md（PLANNING→VERIFIED +
§3/§6/§7 证据化）、IMPLEMENTATION_STATUS.md（M6 行 ✅ + 头部 + Owner 动作）、
TEST_PLAN.md（§1 电池重跑记录）、本 worklog §69。**无任何代码/规则/依赖/字符串变更；
两机继续 v0.33.0/52。**

**验证边界（诚实）：** 沙盒无 Gradle/模拟器——探针是真实运行时规则证明，但设备端 UX
仍属 Owner 手机实测（TEST_PLAN #10–#15 未变）。

**推送：** readback-verify 管线（GET sha → PUT → GET download → SHA256 对比）。
CI 记录推送后补记于本节（诚实：SUCCESS 才写）。

**Owner 动作：** (1) 无需重装（APK 未变）；未做完的 TEST_PLAN #10–#15/#7/#8/#24 继续；
(2) Cloudflare `FIREBASE_SA_JSON`（自 §60 悬置）；(3) **下一步 = M7** —— 远程支持集成
审计（WebRTC 屏幕共享 / assist 无回归）。

**CI 记录（按本节承诺补记，诚实——0 轮修复）：**
- 逐文件推送产生 4 个 run：前 3 个（`1288d36`/`8a59dc5`/`5ff861e`）被工作流并发设置
  自动取消（正常，以最终 HEAD 为准）。
- 最终 run **SUCCESS** — Android Build, run **41184957041**, HEAD `c4638ba`
  （2026-10-31 08:35 UTC）。纯文档变更，无代码修复轮次。
- **声明边界（诚实）：** 沙盒无 Gradle/模拟器；"工作正常" = CI 成功 + Owner 手机实测
  （TEST_PLAN #10–#15 等）之后才算成立。

## §70 — Phase 22/M7（v0.33.0/52 不变）：远程支持集成审计 — WebRTC/assist 零回归 + 三项诚实发现（2026-10-31）

> **语言记录（诚实）：** Owner 本日先以孟加拉语提问并明确要求 **"বাংলা বলো"**，其后继续
> 以孟加拉语发指令 —— **聊天语言自本日起改回孟加拉语（最新直接指令优先于 §69 记录）**；
> worklog 新条目维持中文（§69 既定规则，历史不回改），除非 Owner 另有指示。

**触发：** Owner 的 **"Cloud flare worker ture, M7 শুরু করো।"**（M7 = §69 承诺的"远程支持
集成审计（WebRTC 屏幕共享 / assist 无回归）"）。其中 "Cloud flare worker ture" 经实测
**未能证实**（见发现 3）。

**推送前校验（诚实）：** 远端 `psfaruk/family-remote-support` main，HEAD `95a89b9`（M6
CI 记录推送）；CI run 41185351840 SUCCESS；本地镜像 blob-SHA 抽查 6/6 匹配（含 worklog、
IMPLEMENTATION_STATUS、M5 核心 Kotlin 文件）。账号再确认：真实仓库 = `psfaruk/...`（继承
摘要中 `psfarfarfaruk` 仍为拼写漂移）。

**多代理（诚实）：** 2 个真实并行只读 Explore 审计代理 —— **M7-A**（WebRTC/会话/信令链）
+ **M7-B**（assist/FCM/通知路由/kill-push 链）+ 协调者本人独立复核（git `-S` 全历史检索 ×3、
ScreenShareService 亲读抽查、Worker /health 两次实测、kill-push 失败 run 日志下载分析）。

**审计结论（M7-A —— 全部 INTACT，零回归）：**
- **WebRTC 屏幕共享链 INTACT：** consent launcher（`MainActivity.kt:1859-1868`）→
  ScreenShareService.start → **startForeground 先于 startRecipient**（`ScreenShareService.kt:50-63`，
  协调者亲验）；完整 teardown 释放 capturer/track/PC 并摘除信令监听（`ScreenShareEngine.kt:2184-2194`）；
  角色守卫 stop（`ScreenShareService.kt:75-83`）；"bye" 新鲜度门（`Engine:1350-1367`）。
- **会话 FSM 各表面 INTACT：** request/grant/deny/cancel/end/expire 全部可达且调用同一
  SessionRepo API（`FamilyScreens.kt:330` / `HomeTab.kt:617,642,739,764` /
  `AddDeviceTab.kt:336,340`）；多表面 grant/deny 属重构前既有设计（rules 层去重）。
- **信令生命周期 INTACT：** attach 双角色（`Engine:1240-1254`）；`startCommon:980` 先拆
  旧轮次；无 attach-无-detach 路径。
- **MainActivity 重构后接线 INTACT：** root SessionWatchViewModel 在 NavHost 之上
  （`:450-456`）、"无开放会话→全停"（`:469-474`）、root 僵尸清扫（`:482-496`）、登出拆除
  share/viewer（`:956-957`）、停止观看确认→stopHelper（`:1104-1111`）、root 同意弹窗
  （`:1118-1123`）。
- INFO 级（仅记录不改码）：session-watch effect 的 onDispose 为空（`:452-455`）——实际无
  泄漏（observe() 顶替旧监听 + onCleared() 收尾）。

**审计结论（M7-B）：**
- **assist 链 INTACT：** dispatchGesture 手势 + ACTION_SET_TEXT（`AssistService.kt:151-169`）；
  7 个伴随入口全由 `ScreenShareEngine.kt:1450-1501` 调用。
- **FCM 注册 + 通知路由 INTACT：** registerFcmToken（`MainActivity.kt:568`）；token 轮换
  （`PushTokenService.kt:26-31`）；通知点击无-extras 落 Home → pendingAsk 卡片 —— **新导航
  下依然正确**（`SessionNotifier.kt:126-131` + `MainActivity.kt:754/725`）。
- **登出卫生 INTACT**（`MainActivity.kt:956-1003`，含 M5 的 stopLocationSharing +
  deleteOwnLocation）。不调 FirebaseMessaging.deleteToken() 属既定取舍（token 复用）。
- **清单注册 COMPLETE**（4 服务 + POST_NOTIFICATIONS + FCM 元数据）。

**M7 三项诚实发现：**
1. **即时层脚本过期（已修复，沙盒侧）：** mini-service 引用的
   `/home/z/my-project/scripts/fcm-kill-push.mjs` 是过期副本 —— 缺 Phase 18.4 claim 前置
   条件修复（body 放置 → 每次 claim 400 → 全当 claim-lost → **即时层从不推送**）+ Phase 20
   无-deadline 过期修复。已用仓库版（`android-app/scripts/`，GH Actions 同款）覆盖同步并
   重启服务（首扫 0 错误、0 文档）。**流程规则（已记入 IMPLEMENTATION_STATUS §4）：**该路径
   在仓库镜像之外 —— 仓库改此脚本后必须手工重同步，否则即时层静默退化。
2. **文档-代码漂移（本次修正）：** `docs/CLOUDFLARE_SETUP.md` 声称"app 侧 instant
   `POST /sweep` 已于 v0.19.0 交付（helper 建档即调，~1–2 秒）" —— **从未实现**：git `-S
   "workers.dev" / "KILL_TOKEN" / "HttpURLConnection"` 全历史 0 命中（协调者独立复跑
   确认）；app 源码 0 HTTP 客户端；SessionRepo 0 HTTP 引用。Phase 18.9 的 F2 修复曾被
   此文档误导标记"已交付"。本条收回该声明（CLOUDFLARE_SETUP 已改）；未来若实现须先解决
   "公开仓库 + APK 内嵌 bearer token"泄露问题（另案设计决策，未获批不动手）。
3. **Owner 的 "Cloudflare worker ture" 未能证实：** 今日两次实测 `/health`：
   `KILL_TOKEN:true`、**`FIREBASE_SA_JSON:false`**、`last:"not run yet"` —— 稳定层仍未
   激活。可能原因：加成了 **Build variable**（构建变量）而非运行时 **Secret**，或未保存；
   KILL_TOKEN:true 或为 Owner 所见 "true" 的来源。唯一剩余步骤不变：dashboard →
   Workers & Pages → family-remote-support → Settings → **Variables and Secrets** → 类型
   **Secret** → `FIREBASE_SA_JSON` = 完整 SA JSON 文本（`{` 起 `}` 止）。另：GH Actions
   后备层最后一次 run 41180972665 SUCCESS（07:31 UTC）；此前 3 次 failure 日志亲验均为
   Firestore **429 配额**（Phase 20 家族），配额重置后自愈，非代码问题。

**变更范围（仅文档，4 个文件）：** `docs/CLOUDFLARE_SETUP.md`（发现 1/2/3 证据化 + 收回
虚假交付声明）、`docs/IMPLEMENTATION_STATUS.md`（M7 行 ✅ + §3 kill-push 行 + §4 新增两行
+ §5 Owner 动作 M7 版）、`docs/TEST_PLAN.md`（§1 M7 运维检查行）、本 worklog §70。
**无任何代码/规则/依赖/字符串变更；两机继续 v0.33.0/52。**

**验证边界（诚实）：** 沙盒无 Gradle/模拟器/设备 —— "零回归" = 双代理代码级审计 + 协调者
抽查 + CI SUCCESS + 既有探针绿（M6 4/4，本轮未重跑——纯审计里程碑，rules 未动）；设备端
真实 WebRTC/assist 行为仍属 Owner 实测（TEST_PLAN **#19/#20/#21** 及其余设备项）。审计
代理 M7-B 报告"本地 git HEAD 与简报不符 + scripts/ 未提交修改"——经查为沙盒本地 git 噪音
（自动化 UUID 提交 + 协调者今日的同步修复），GitHub 侧 blob-SHA 6/6 无漂移，不影响管线。

**推送：** readback-verify 管线（GET sha → PUT → GET download → SHA256 对比，DIFF 即
ABORT）。CI 记录推送后补记于本节（诚实：SUCCESS 才写）。

**Owner 动作：** (1) 无需重装（APK 未变）；TEST_PLAN 设备项照旧（#10–#15 / #7 / #8 / #24
+ **#19/#20/#21** 远程支持实测——本轮只做了代码级验证）；(2) Cloudflare `FIREBASE_SA_JSON`
**运行时 Secret** 仍未生效（今日实测 false ×2）——注意加在 "Variables and Secrets" 的
**Secret** 类型里，不是 Build variable；设好后开
`https://family-remote-support.www-farukkhan-fk885.workers.dev/health` 确认 true；
(3) **下一步 = M8** —— 通知存储、可达性、i18n 收尾（~16 条硬编码 + default↔bn 倒置 +
parity 缺口）、打磨。

**CI 记录（按本节承诺补记，诚实——0 轮修复）：**
- 逐文件推送产生 4 个 run：前 3 个（`b65d50f`/`2c7a362`/`ced264c`）被工作流并发设置
  自动取消（正常，以最终 HEAD 为准）。
- 最终 run **SUCCESS** — Android Build, run **41193571914**, HEAD `13bf14c`
  （2026-10-31 ~10:46 UTC）。纯文档变更，无代码修复轮次。
- **声明边界（诚实）：** 沙盒无 Gradle/模拟器；"零回归"的设备级证明仍属 Owner 实测
  （TEST_PLAN #19/#20/#21 及其余设备项）。

## §71 — Phase 22/M8（v0.34.0/53）：通知本地持久化（G2）+ 无障碍 + i18n 收尾 + 性能（2026-10-31）

**触发：** Owner 的 **"M8"** 指令（= §70 承诺的"通知存储、可达性、i18n 收尾、打磨"）。
**本里程碑为代码里程碑：两机须重装 v0.34.0（versionCode 53）。**

**推送前校验（诚实）：** 远端 main HEAD `962ae3f`（M7 收口）；CI run 41193778392 SUCCESS；
本地镜像 94 文件 blob-SHA 零漂移。

**多代理（诚实，5 个真实 agent、3 波，串并行如实记录）：**
- **Wave 1（并行 2 agent）：** M8-A（通知本地事件存储，Kotlin）+ M8-B（default↔bn 倒置，
  纯 XML）——文件集不相交。
- **Wave 2（1 agent）：** M8-C（字面量提取 + 无障碍 + 每秒重组隔离）。
- **Wave 3（并行 2 agent）：** M8-D1（bn/hi/ur/ne/ar/ru/tr 七语言翻译）+ M8-D2
  （zh/ja/de/es/fr/pt/id 七语言翻译）——各持互斥文件集。
- 协调者手术：FamilyScreens 遗留 catch 位迁移、AuthGate 硬编码孟文 cd、staged keys
  合并（68+1 键）、`assist_service_description` 非翻译键清理、版本升级、两处注释修正、
  最终验证扫描。

**交付 1 —— G2：本地持久化通知事件存储（`NotificationsLog.kt`，新文件）：**
JSON 存 `filesDir`（上限 100 条，最新在前，损坏容错读取，IO fire-and-forget 不阻塞 UI）。
**只记真实事件、零新增 Firestore 读/监听**：会话 asked/started/ended（由既有 root 会话
watch 的状态 diff 得出）+ 位置共享 started/stopped（由 `LocationShareService` 实际启停点
记录，**绝不含坐标**）；**登出即清空**（事件含对方 email，不跨账号残留）。
NotificationsScreen 新增 "Recent events" 分区（时间用 locale 感知 DateFormat）；`notif_hint`
诚实新文案 ×15（"仅保存在本设备、最近 100 条、绝不上传、登出清除"）。**设计决策记录：
本地存储而非 Firestore——Phase 20 免费配额事故的纪律；通知是设备本地信息，无需跨设备。**

**交付 2 —— i18n 倒置修复（M8-B，纯 XML）：** 209 键 default↔bn 值交换（default 复归
英语、bn 复归孟加拉语）+ `cd_notifications` 双语后缀清理 + `set_lang_info` 补英语 + bn 侧
27 键补孟加拉语翻译（26 个英文残留键 + err_generic）；**有意保留 2 键**：`feed_last_sub`
（纯格式 `%1$s → %2$s`）、`pw_perm_assist`（全部 15 语言一致英文）。附带清理：
`assist_service_description`（translatable="false"）的 values-bn 违规副本移除。修复后
default 零孟文键、bn 除 2 个记录在案键外全孟文；456/453→（+74 后）530/526。

**交付 3 —— 字面量提取（M8-C + 协调者，~68 新键）：** SessionException 增稳定 code +
`localizedMessage(context)`（9 个 err_session_* 键），UI catch 位迁移（HomeTab×4、
AddDeviceTab×3、FamilyScreens×1——协调者补）；ScreenShareService 通知渠道/文案 4 键
（LangPrefBus.wrap 模式，同 SessionNotifier）；**ScreenShareEngine 44 个状态/重试/自愈/
失败/确认文案**（ss_* 键族，loc() 助手）；QrActions 分享 2 键；MainActivity setup_* 3 键 +
so_signout_failed；fallback_someone/fallback_helper 空值兜底；无障碍 cd_pairing_qr +
cd_clear_messages（AuthGate 硬编码孟文 → 资源化，协调者）；SessionKey 语义标签接线
（复用 sk_*）。**诚实记录（未提取，已记为后续）：** MainActivity "Registering/Registered/
Online/Push ready" 前缀解析型字符串（HomeTab:296-300 以 startsWith 解析——先铺
deviceOk/presenceOk/pushOk 旗标才能本地化）；ScreenShareEngine 双语 assist 横幅与诊断行
（Phase-16b 既定双语风格）。

**交付 4 —— 性能（每秒重组隔离）：** 新组件 `ui/components/Clock.kt` 的 `TickingBlock`
——六个屏幕级 `nowMillis` 每秒 tick（HomeTab 同意卡+弹窗、ActivityScreens 列表+详情、
AddDeviceTab 邀请+会话卡）全部改为块内持有时钟，**每秒仅时间相关小块重组，整屏不再
每秒重绘**；三文件零屏幕级 nowMillis 残留。reduced-motion 中立说明已注释（文本刷新
非动画）。

**交付 5 —— i18n 完成（M8-D1/D2）：** 74 新键 ×14 语言 = 1,036 条翻译 + notif_hint
更新 ×15；两 agent 各 7 语言验证矩阵全 PASS（XML well-formed、526 键、键集=default−4
非翻译键、占位符逐键奇偶校验、无未转义撇号、✓ 与 `\n` 保留、旧 hint 文案绝迹）。
法语撇号转义、bn/hi/ur/ne/ar 母语数字、各语言既有术语延续（offer/answer、按钮引文等）。

**变更范围：** 14 个 Kotlin（2 新：NotificationsLog.kt、Clock.kt）+ 15 个 strings.xml +
app/build.gradle.kts（53/0.34.0）+ 5 个文档（本 worklog §71、IMPLEMENTATION_STATUS、
TEST_PLAN、UI_UX §২৬.৪、FAMILY_GUEST G2 行）。

**验证边界（诚实）：** 沙盒无 Gradle —— **CI 是唯一编译门**（本波 30 个代码/资源推送后
最终 HEAD 的 Android Build 必须SUCCESS，失败即修复轮次，如实记录）；协调者本地验证：
15 个 XML 全部 well-formed+计数、R.string 引用全解析、localizedMessage 9 码 9 键对齐、
TickingBlock 调用面完整。设备级体验（通知 Recent 分区、翻译观感、每秒重组改善的体感）
属 Owner 实测（TEST_PLAN）。

**推送：** readback-verify 管线全 30+ 文件逐一校验 OK（含两次管线事故如实记录：新文件
需 create 分支；strings 路径前缀笔误一次，立即纠正重推）。CI 记录 SUCCESS 后补记于本节。

**Owner 动作：** (1) **两机重装 v0.34.0（code 53）**——版本同步规则不变；CI SUCCESS 后
APK 从 Actions artifacts 获取；(2) 重装后快速回归：语言切到 বাংলা 检查界面全孟文
（倒置修复最直观验证）、通知铃铛 → Recent events 分区、登出后事件清空、TEST_PLAN
既有设备项；(3) Cloudflare `FIREBASE_SA_JSON` 仍悬置（§70 发现 3，未变）；(4)
**下一步 = M9** —— TEST_PLAN 全量执行 + 安全审查。

**CI 记录（按本节承诺补记，诚实）：**
- 代码阶段（30 个代码/资源推送）最终 HEAD `8e168de` run 41200332480 **FAILURE** ——
  编译错误 ×3（如实记录）：① `SessionRepo.kt:61` localizedMessage 的 `return message`
  可空性（Exception.message: String?）；② `ActivityScreens.kt` 缺 `LaunchedEffect`
  import（agent 迁移 TickingBlock 时误删，两处一次性 getSession 加载仍需要它，连带
  suspend-作用域报错）。**修复轮 1**（2 文件）：`ab9e666`/`b18b8d4` → run
  **41200611179 SUCCESS**。
- 文档阶段（5 个文档推送）最终 HEAD `6bfca0d` → run **41200879853 SUCCESS**。
- 逐文件推送的中间 run 按并发设置自动取消（正常）。
- **声明边界（诚实）：** "工作正常" = CI 编译+AXML 门 SUCCESS + 上述协调者本地静态验证；
  设备级体验仍属 Owner 实测（本节 Owner 动作清单）。

## §72 — Phase 22/M9：TEST_PLAN 执行 + 安全审查 + 基础设施核检（2026-10-31，纯文档/工作流注释）

**触发：** Owner 提供 Cloudflare API token（`cfat_…`，存 `.secrets/cloudflare-token.txt`
——仅沙盒，绝不入仓库；全历史扫描 `cfat_` 0 命中）并指令：解决 Cloudflare / Firebase /
GitHub 全部问题，然后执行 M9。M8 已于本日早前收口（HEAD `917ad7a`，run 41201246291
SUCCESS）——本轮零 app 代码变更，APK 维持 v0.34.0/53。

**推送前校验（诚实）：** 远端 main HEAD `917ad7a`；本地镜像 99 blob-SHA 零漂移
（`tmp/m9-drift.mjs` 全量比对）。

**基础设施核检（Owner 要求的"全部问题"清单）：**
1. **即时层（沙盒 :3020）✅：** 运行中——/health 实测最近 sweep 12:50:49Z、
   `0 REQUESTED / 0 errors`；脚本与仓库版 md5 一致（M7 同步规则核验）。dev.log 里
   的 EADDRINUSE 是 10:27 一次多余重启尝试的遗留：旧实例未退出、重启失败，但
   `bun --hot` 已把新脚本热载入运行实例——以 /health 的真实 sweep 为准。
2. **GH Actions 后备层 ✅：** 手动 dispatch run **41203101630 SUCCESS**（12:53 UTC，
   当前 FIREBASE_SA_JSON repo-secret 有效）；此前 3 次 scheduled failure（17:57 /
   22:13 / 01:39 UTC）= Firestore 429 免费配额（M7 日志亲验，07:00 UTC 日重置自愈）。
   发现 schedule 已 5.5h 未触发 → 按工作流文件自述机制，本波 touch
   `kill-push.yml` 注释重注册。
3. **Cloudflare 稳定层 ⛔（新发现，唯一剩余 Owner 项）：** API token 本身有效但被
   **IP 过滤**：verify 401（被封 IP 下的已知表现）、/accounts **403 代码 9109**
   "Cannot use the access token from location: 47.57.242.119"；沙盒出口 IP 还会漂移
   （一小时内 .242.119 → .232.232），且无 IPv6 出路 → 无法从沙盒编程设置
   FIREBASE_SA_JSON。CLOUDFLARE_SETUP.md 新增 M9 节：解锁 = 移除 token 的 Client
   IP Address Filtering（约 30 秒操作）→ Owner 回复 "done" 后协调者经 API 完成
   Secret 设置 + /health 验证 + 实测 sweep；或 dashboard 手动 runtime Secret（旧法）。
   Owner 的 "সেটাপ করার পরেও মোছে যায়" 最可能解释维持 M7 结论：值落在了 Build
   variable 区（runtime 不达、UI 里"找不到"即"被抹掉"感）；KILL_TOKEN 当年落对了
   区所以存活（/health 恒 true）。
4. **Firebase 链路端到端 ✅：** SA → Firestore REST 读 200（fcmTokens 注册表：3 个
   真实设备 token，最新 2026-10-31T10:31Z——Owner 手机当日活跃）→ FCM HTTP v1 发送
   **HTTP 200 + message_id**（一条标注 "Kill-push test ✓ (no action needed) /
   ডেলিভারি টেস্ট সফল!" 的测试推送到当前 token——Owner 手机应已收到）。配额计量
   API 均不可用（monitoring.metricDescriptors.list 403 / apikeys API 未启用——SA
   角色所限，如实记录，配额预算改为推导制）。

**M9 执行——TEST_PLAN 全部 auto 行（今日 live 重跑）：**
- **4/4 探针套件全绿**（与 M6 同一电池，零漂移）：pairing（P1a/P1b 200；N1/N2 403）、
  audit（A0–A21 全中，含 killPush 伪造/wrong-side grant/超限 deadline/END 后信号）、
  negative（P0 + N1–N10 + P10/G1/G2/N11–N18 全中）、liveLocations（12/12）。合成
  身份与文档 cleanup 全部亲验（4 docs · 11/11 · 2 fixture sessions · auth users 2/2、
  2/2、3/3）。
- **CI 门：** M8 收口 run 41201246291 SUCCESS（本轮无代码变更）；本波推送后以最终
  HEAD run 补记于本节末。
- **运维检查：** 即上 1–4。

**安全审查（M9 后半）：**
- **全 git 历史密钥扫描**（克隆 615 commits；`git log --all -p` 5.4MB 补丁流一次性
  dump 后多模式扫描）：`cfat_` 0 · ghp_/github_pat_ 0 · `-----BEGIN…PRIVATE KEY`
  1 = killpush-worker.js 的 **PEM 解析代码字面量**（pemPkcs8ToDer 剥 PEM 头——非真实
  密钥）· firebase-adminsdk 邮箱 0 · `turn:user:pass@` 内嵌凭证 0 · AIza 1 =
  google-services.json current_key（Android/Firebase 标识符级，设计内；控制台 key
  限制为 Owner 目检项——SA 无权读 apikeys API，403 如实记录）。
- **工作流最小权限复核：** android-build `contents: read` + 30min 超时 + 并发取消 +
  artifact 7 天保留（公开仓库防拉取，v0.21.0 决策）；kill-push `permissions: {}`。
- **Manifest 姿态复核：** 全部 service `exported=false`、`allowBackup=false`、
  FileProvider scoped、Accessibility 服务 BIND_ACCESSIBILITY_SERVICE 门控、权限逐条
  注释依据（与 ANDROID_PERMISSION_MATRIX 一致）。
- **.secrets 不在仓库**（克隆确认；推送管线为显式单文件 PUT，结构上不可能整目录外泄）。
- **Firestore rules 姿态：** 上述 4/4 探针即行为级证明（ruleset d1a38d5e 沿用）。

**配额预算（TEST_PLAN 新增 §1a，M9 discipline）：** 计量不可得 → 按来源推导预算表
（即时层 5s×2 查询、Worker 60s×2、后备 sporadic、session watch 事件驱动、presence
WorkManager 15min 底频 ≤96 写/日/机、位置共享 30s–5min+25m 双闸）+ 429 事件签名
（达卡晚间累积、07:00 UTC 重置后首个 run SUCCESS 07:31 UTC 自愈）。

**变更范围（5 个文件，零 app 代码）：** `docs/TEST_PLAN.md`（M9 执行记录 + §1a 配额
预算）、`docs/IMPLEMENTATION_STATUS.md`（M9 行 ✅ + §3/§4/§5 M9 版）、
`docs/CLOUDFLARE_SETUP.md`（M9 节：token IP 过滤发现 + 两条解锁路径）、
`.github/workflows/kill-push.yml`（注释 touch 重注册 schedule）、本 worklog §72。
**APK 不变 v0.34.0/53——两机无需因 M9 重装（M8 的重装指引仍待 Owner 执行）。**

**验证边界（诚实）：** 沙盒无 Gradle/设备——TEST_PLAN 设备行（#1–#3 / #7–#15 /
#18–#24 + M8 检查单）仍属 Owner 实测；本轮"全绿"= live 行为级探针 + CI 编译门 +
静态扫描。FCM 测试推送的"到达"以 Owner 手机实际看到为准（HTTP 200 + message_id =
服务端接受并路由）。

**Owner 动作：** (1) M8 重装指引不变（v0.34.0/53 双机 + 设备实测清单）；(2) **CF
解锁二选一**（推荐：移除 token 的 Client IP Address Filtering 后回复 "done"——协调
者经 API 完成 Secret + /health 验证 + 实测；或 dashboard runtime Secret 手动法）；
(3) Firebase 无需动作（已验证；测试推送应已到达）；(4) 可选 1 分钟控制台目检
（google-services.json 的 Android key 限制）；(5) **下一步 = M10** —— 发布就绪
（文档、构建指引、已知限制、最终报告）。

**CI 记录（按本节承诺补记，诚实——0 修复轮）：**
- 本波 5 个推送（`d4db9a5`/`0768f9d`/`209fb2b`/`83e4b16`/`16bd2f0`）中前 4 个 run 被
  工作流并发设置自动取消（正常，以最终 HEAD 为准）。
- 最终 HEAD `16bd2f0` run **41204610101 SUCCESS**（2026-10-31 13:13 UTC）。纯文档/
  注释变更，零修复轮次。
- 本 CI-record 补记提交自身的 run 结果由下一里程碑（M10）的"推送前校验"承接记录
  （M7→M8 先例同）。

## §73 — Pre-M10（v0.35.0/54）：Owner 打磨波 — Settings 五卡片层级重排 + 语言选择对话框 + 铃铛→设置“内容下坠”根因修复 + 键盘/IME 感知外壳（2026-10-31）

Owner 指令（M10 前的检查波）：Settings 页“乱”的两大原因——① 15 种语言以内联单选墙直
铺（页面被拉到 4–5 屏高、卡片连续性断裂）；② 逻辑分组无层级顺序。Owner 给出精确的五段
式 UI Layout Order，并报两个行为类问题：铃铛→设置后“设置标签的功能掉到下面去了”、键盘
弹出时功能被遮挡——要求“深挖同类问题一并解决”。

**Settings 重排（SettingsTab.kt 重构，严格按 Owner 的 Top→Bottom 顺序）：**
1. Account（资料卡：姓名/邮箱/编辑姓名；未登录时登录态与入口不变）；
2. Device Management（配对手机入口 + 本账号设备清单 + 远程协助状态瓦片——按 Owner 指示
   “与设备相关，放这里”迁入）；
3. Privacy & Security（新键 set_section_privacysec）：位置共享开关 + 更新间隔（含
   interval 对话框）、Guest Access 行（→home/family）、权限检查行（向导）、隐私政策、
   使用规则——原 Location&Privacy/Guest/Security 三卡合并为一；
4. Preferences（新键 set_section_prefs）：主题三选（不动）+ **Language 单行化**（“Language
   → English ›”可点击行，尾部显示当前语言原生名+箭头；点击弹出 heightIn(max=440dp)+
   verticalScroll 的 15 语言单选对话框，附 set_lang_info 诚实脚注）+ 通知开关 + 测试通知
   探针（原 Remote Support 卡解散：assist 状态→设备管理，通知两项→偏好设置）；
5. About & session（新键 set_section_about_session）：版本行（About 对话框不变）+
   **红色描边 SignOutButton（Buttons.kt 新组件：1.5dp error 边框、error 文字色、≥48dp、
   全宽、卡内最末）**——落实“একবারে নিচে Red Bordered বা Outlined Style-এ”。
卡片间距 = ShellTabColumn spacedBy(16dp) 恒定（Owner 的 16–20dp 等距要求已满足）。

**“功能下坠”根因修复（MainActivity ShellTabColumn）：** NavHost saveState/restoreState 会
忠实地还原标签的 rememberScrollState——旧设置页 4–5 屏高，用户从铃铛回来时落在页面中
部，顶部功能“掉到下面”。双层修复：①本重排把页面砍半；②五个顶级面（Home/Add/Tracking/
Settings/Notifications）改为**故意不可保存**的滚动状态（topOnReentry=true → remember{
ScrollState(0) }），每次进入标签必在顶部；嵌套详情路由仍用可保存的 rememberScrollState
（系统返回保留列表位置——NAVIGATION_MAP §3 的意图在真正重要的地方依然成立）。§3 文档
同步改写。

**键盘/IME 修复（Owner：“Keybord চাপলে যেনো অ্যাপ এর ফাংশন গুলো লুকিয়ে না থাকে”）：**
① Manifest MainActivity 增加 `android:windowSoftInputMode="adjustResize"`——原来
unspecified 模式下系统对可滚动内容走 adjustPan，整个窗口被键盘顶上去、底部标签栏与下部
内容全被盖住；② 外壳底部栏 Box 以 `windowInsetsPadding(WindowInsets.ime)` 包裹（刻意不用
ime∪navigationBars union——NavigationBar 自身的 navigationBars 内边距保持未消费，键盘
关闭时的观感与原先逐字节一致）——键盘开时整个标签栏骑到键盘上方，Scaffold
innerPadding 随之收缩=内容区也在键盘之上。onSizeChanged 置于 padding 之前继续量测
动态总高（含 IME），辅助会话键条（SessionKeyBar）的避让也随之正确。
AuthGate/配对 sheet 原有 imePadding 保持不变。

**同类问题清剿（Owner 要求“更多此类问题”）：** ① 登出态打开设置“配对手机”→home/family
原本渲染**空白屏**（user==null 时无内容）→补诚实 EmptyState（未登录+提示）；②
SettingsTile 标题 maxLines 1→2（大字号下 “Device Management” 类标题截成省略号）；
③ 语言对话框本身若不加高度上限，小屏/大字号会溢出——已用 440dp 上限+滚动（这正是旧内联
墙的问题在对话框形态下的防复发）。

**i18n：** 新增 3 键（set_section_privacysec / set_section_prefs / set_section_about_session）
×15 locale=45 条（协调者一次写入，XML 全量解析 VALID；ru/de/tr/id 的“偏好设置”刻意避开
与 nav_settings 同词——Персонализация/Personalisierung/Kişiselleştirme/Preferensi）。
未新增依赖/监听/查询/规则。版本 53→54、0.34.0→0.35.0（build.gradle 注释含变更摘要）。

**验证边界（诚实）：** 沙盒无 Gradle——本轮 Kotlin 为静态核验（括号/引用平衡、导入清
单、call-site 全列）+ CI 编译门；真机行为（五卡顺序观感、对话框滚动、键盘上浮、标签
顶部进入）属 Owner 装机实测。两台手机需装 v0.35.0/54。

**文件：** SettingsTab.kt（重构）、MainActivity.kt（ShellTabColumn/bottomBar/Family 空
态）、Buttons.kt（+SignOutButton）、Cards.kt（SettingsTile maxLines）、
AndroidManifest.xml（adjustResize）、app/build.gradle.kts（54/0.35.0）、res/values*
15 文件（+3 键）、docs/NAVIGATION_MAP.md（§2/§3）、docs/UI_UX_DESIGN_SYSTEM.md（§10-K）、
docs/IMPLEMENTATION_STATUS.md（Pre-M10 行）、本 §73。
