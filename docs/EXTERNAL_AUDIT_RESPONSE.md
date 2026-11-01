# বাহ্যিক গভীর অডিট প্রতিবেদনের সোর্স-কোড যাচাই — প্রতিক্রিয়া (v0.45.0/65)

**তারিখ:** 2026-11-01 · **বেসলাইন:** HEAD `a5cbca0`, v0.45.0 / versionCode 65 · **CI:** run #859 SUCCESS (নভেম্বর ১, ২০২৬) · **লেখক:** সহায়ক এজেন্ট (সোর্স-কোড অডিট ওয়েভ)

---

## ০. এই প্রতিবেদন কী এবং কেন

Owner-এর হাতে আসা বাহ্যিক "Deep Technical Audit Report" নিজেই স্বীকার করেছে: *"psfaruk/family-remote-support রিপোজিটরির সোর্স ফাইল এই সেশনে অ্যাক্সেস করতে পারিনি… এটি এখনো প্রকৃত সোর্স-কোড অডিট নয়।"* — অর্থাৎ সেই রিপোর্টের প্রতিটি দাবি ছিল **সাধারণীকৃত সেরা-অনুশীলন পরামর্শ**, এই রিপোজিটরির প্রকৃত অবস্থার সাক্ষ্য নয়।

এই দস্তাবেজটি সেই ঘাটতিটুকু পূরণ করে: বাহ্যিক রিপোর্টের **প্রতিটি দাবি প্রকৃত সোর্স-কোডের বিপরীতে যাচাই** করা হয়েছে — ফাইল-ও-লাইন প্রমাণসহ। ফলাফল তিন শ্রেণিতে: ✅ ইতিমধ্যে বাস্তবায়িত · ⚠️ আংশিক/ভিন্ন-কিন্তু-সমতুল্য · ❌ সত্যিকারের ফাঁক।

**নিচের সব লাইন-নম্বর `a5cbca0`-এর বিপরীতে; CI সবুজ থাকায় এটি প্রকাশিত সত্য।**

---

## ১. নির্বাহী সারসংক্ষেপ — বাহ্যিক রিপোর্টের অগ্রাধিকার-তালিকার প্রকৃত অবস্থা

| বাহ্যিক রিপোর্টের অগ্রাধিকার | প্রকৃত অবস্থা | প্রমাণ (সংক্ষেপ) |
|---|---|---|
| Backend–Frontend Integration (High) | ✅ সম্পূর্ণ — ১২ ফিচারের পাইপলাইন বিদ্যমান | §২-এর ম্যাট্রিক্স |
| Notification System (High) | ✅ পাইপলাইন সম্পূর্ণ (v0.45.0 P1/P4/P8) · ❌ সেন্টার-UI-তে ৪টি ফাঁক | §৩ |
| Location Tracking UI (High) | ✅ সম্পূর্ণ (M5–M12, v0.33.0–v0.43.0) · ⚠️ ব্যাটারি নেই | §৪ |
| API ও Feature Pipeline (Verify) | ✅ যাচাই-সম্পন্ন — রিয়েলটাইম লিসেনার + ইভেন্ট-চালিত push | §২/§৩ |
| QR Pairing — Family/Guest (Verify) | ✅ নিয়ম-স্তরে প্রমাণিত (G1–G4 + ৩৩/৩৫-কেস প্রোব) | §৫ |
| UI / UX (Improve) | ✅ ৪-ট্যাব কাঠামো হুবহু বাহ্যিক পরিকল্পনার মতোই (M2–M4) | §৬ |
| Security & Permission (Critical) | ✅ §৮৫ নিরাপত্তা-পর্যালোচনা: ৯ হুমকি-শ্রেণিতে শূন্য বাইপাস | §৭ |

**মূল সিদ্ধান্ত:** বাহ্যিক রিপোর্টের কেন্দ্রীয় অভিযোগ — "Backend-এ ফাংশন আছে, Frontend-এ ব্যবহারযোগ্য ফিচার নেই" — **বাস্তব কোডে খারিজ**। পাওয়া গেছে মাত্র **২টি সত্যিকারের ফাঁক** (উভয়ই ছোট, লোকাল-অনলি, §৮-এ পরিকল্পনাসহ)।

---

## ২. Feature Integration Matrix — ১২ ফিচার × ৬ উপাদান (বাহ্যিক রিপোর্টের §২-এর সরাসরি উত্তর)

বাহ্যিক রিপোর্ট দাবি করেছিল: "প্রতিটি ফিচারের জন্য ছয়টি জিনিস থাকতে হবে।" নিচে সেই ছয়টি স্তম্ভই প্রকৃত কোড থেকে ভরা হলো।

| # | ফিচার | Backend Function | API/Listener | Frontend Service | UI Component | L/S/E স্টেট | টেস্ট |
|---|---|---|---|---|---|---|---|
| ১ | Device Pairing | `PairingRepo.createInvitation/redeem` — PENDING→PAIRED অ্যাটমিক লেনদেন | Firestore `pairingInvitations` + `watchInvitation` | `PairingRepo` (745 লাইন) | `AddDeviceTab` → `PairingSections`, QR জেনারেট/স্ক্যান, `ScannedRoleSheet` | লোডিং-স্কেলেটন + লাইভ স্ট্যাটাস (waiting→paired/cancelled/expired) + ব্যর্থতা-কার্ড | `rules-pairing-probe.mjs` + রুলসের `getAfter()` পুনঃযাচাই |
| ২ | Family Connection | `trustedDeviceRelationships` `kind=family`, স্থায়ী | রিলেশনশিপ লিসেনার (`watchRelationships`) | `PairingRepo` রিলেশনশিপ-পাথ | `FamilyScreens` → Family Overview, `MemberDetailScreen` | সত্যের লোডিং/নোট-ফাউন্ড/এরর স্টেট (ক্যাননিক্যাল) | `rules-audit-probe` + G1–G4 |
| ৩ | Guest Connection | `kind=guest`, `guestExpiresAt` ≤ ৩ ঘণ্টা সার্ভার-সীমাবদ্ধ | একই লিসেনার + কাউন্টডাউন | `PairingRepo` + রুলস `isGuestRel` | `FamilyScreens` — Family/Guest ব্যাজ + গেস্ট কাউন্টডাউন | উইন্ডো-শেষে সৎ "মেয়াদ শেষ" | §৬৯ G1–G4 সবুজ |
| ৪ | Live Location | `LiveLocationRepo` + `LocationShareService` (FGS) + হার্টবিট | `liveLocations` ডক লিসেনার (dual read path) | `LiveLocationRepo` (877 লাইন) | `TrackingTab` ম্যাপ + `LocationDetailScreen` | LIVE/STALE/OFFLINE + আপলোড স্টেট + পারমিশন-ডিনায়েড প্যানেল | `rules-liveLocations-probe.mjs` |
| ৫ | Location History | রিটেনশন + স্কোপ-সচেতন ইঞ্জিন | `locationHistoryV2` দিন-কুয়েরি | `HistoryScreen` VM | `HistoryScreen` (`tracking/{relId}/history`) | খালি/লোডিং/এরর + ডিলিট ওয়ার্কফ্লো | ৩৫/৩৫-কেস প্রোব (run-থ্রু রিয়েল রুলস) |
| ৬ | Device Status | `PresenceRepo` হার্টবিট + TTL | `devicePresence` লিসেনার | `PresenceRepo` (220 লাইন) | `PresenceBadge`/`PresenceChip` (রঙ বুলিয়ান-নির্ধারিত) | "unavailable"/"never seen"/"online"/"last seen N min" — সব লোকালাইজড | M9 টেস্ট-প্ল্যান সিনারিও |
| ৭ | Notification Events | `NotificationsLog` (লোকাল, ১০০-ইভেন্ট ক্যাপ) + `AskNotifier` ফ্যাক্টরি | শেলের সেশন-ওয়াচ থেকে ডিফ (শূন্য নতুন রিড) | `AskNotifier` (335 লাইন) | `NotificationsScreen` + সিস্টেম নোটিফিকেশন | খালি-স্টেট + অ্যাটেনশন সেকশন | (type,arg2) ১০-মিনিট ডিডুপ প্রোব |
| ৮ | Connection/Session Requests | `SessionRepo` — REQUESTED→ACTIVE/EXPIRED স্টেট মেশিন | ওপেন-সেশন ওয়াচ (root-স্কোপড VM) | `SessionWatchViewModel` | `HomeConsentCard` + **`HomeConsentDialog` (root-লেভেল সেন্টারড মোডাল, Allow/Deny)** | ২-মিনিট ডেডলাইন + টাইমআউট-সেলফ-নোটিফ | রুলস: শুধু অংশগ্রহণকারীই লিখতে পারে |
| ৯ | Permission Status | `readPermChecklist` + `locationAuthorizations` | লোকাল পোল (RESUMED-only, v0.45.0 P12) + অথরাইজেশন ডক | `PermissionWizard` + `LocationAuthzRepo` | উইজার্ড ৬-ধাপ + `PermissionDeniedPanel` + সেটিংস-টাইল | প্রতিটি ধাপে সৎ স্কিপ/গ্রান্ট/ডিনাই স্টেট | CI-তে ৪৪ ইউনিট টেস্ট |
| ১০ | Session Expiry | রুলস-স্তরে TTL (২-মিনিট ask / ১৫-মিনিট সেশন) + গেস্ট উইন্ডো | জম্বি-সুইপ LaunchedEffect (root) | `ServerClock` (±২ সেকেন্ড সহনশীলতা) | মেয়াদোত্তীর্ণ নোটিফিকেশন `setTimeoutAfter`-এ নিজেই সরে | লেজি-ক্লিনআপ + ম্যানুয়াল রিট্রাই | `rules-negative-probe.mjs` |
| ১১ | Device Removal | `revoke` → ওপেন সেশন সমাপ্ত (REQUESTED→EXPIRED / ACTIVE→ENDED) | রিলেশনশিপ ডিলিট/রিভোক | `PairingRepo` revoke-পাথ | `MemberDetailScreen` কনফার্মেশন ডায়ালগ | সরিয়ে ফেললে লাইভ-লোকেশন পড়ার পথ বন্ধ | §৮৭ P6 + রুলস |
| ১২ | Tracking Control | `LocationSharingPrefs` + FGS লাইফসাইকেল | প্রিফ-ফ্ল্যাগ + রিজিউম-রিফ্রেশ | `LocationShareService` | `OwnSharingCard` — ON/OFF ব্যানার + ৬-ধাপ `SharingSetupSheet` + ওয়ান-ট্যাপ Stop | আপলোড OK/PENDING/FAILED/UNAVAILABLE + স্কোপ-মেয়াদ লাইন | P3: অপ্রত্যাশিত বন্ধ হলে নোটিফিকেশন |

**সিদ্ধান্ত:** ১২/১২ ফিচারে ছয়-উপাদানের পাইপলাইন বিদ্যমান। বাহ্যিক রিপোর্টের "Architecture Gap" অভিযোগটি এই কোডবেসের বিরুদ্ধে প্রযোজ্য নয়।

---

## ৩. Notification System — ধারা-ধারি যাচাই

### ৩ক. In-App Popup — ✅ বিদ্যমান (v0.19.0 থেকেই)

বাহ্যিক রিপোর্টের উদাহরণ-পপআপ ("New Connection Request… Reject/Accept") এই অ্যাপে **সাপোর্ট-সেশন সম্মতি-জিজ্ঞাসার** রূপে বাস্তবায়িত:

- `HomeConsentDialog` (`HomeTab.kt:641`) — **root-লেভেল সেন্টারড মোডাল, যে-কোনো ট্যাবের উপরে**, Allow/Deny বাটনসহ; বাতিল করলে সিদ্ধান্ত হয় না — কার্ড হোমে থেকে যায় (`HomeTab.kt:137–140`-এর নকশা-টীকা)।
- `HomeConsentCard` (`HomeTab.kt:543`) — হোম ট্যাবের একদম উপরে, Allow/Deny শরীর এক জায়গায় (`SessionActions.rememberAnswerSession`, v0.37.0 ডিডুপ-ওয়েভ)।
- QR-পেয়ারিংয়ে "স্ক্যানের পর অনুমোদন-পপআপ" **ইচ্ছাকৃতভাবে নেই** — এই অ্যাপের মডেলে আমন্ত্রণকারী **QR জেনারেট করার মুহূর্তেই পূর্ব-অনুমোদন** দেন (এককালীন টোকেন, সার্ভার-সাইড ভ্যালিডেশন — Phase 4 নকশা); এটি নথিভুক্ত সুরক্ষা-সিদ্ধান্ত, ফাঁক নয়।

### ৩খ. System Push — ✅ সম্পূর্ণ পাইপলাইন (v0.45.0 P1-ওয়েভ)

বাহ্যিক রিপোর্টের ৬-ধাপ পাইপলাইনের প্রতিটি ধাপ প্রকৃত কোডে আছে:

| বাহ্যিক রিপোর্টের ধাপ | প্রকৃত বাস্তবায়ন |
|---|---|
| 01 Backend Event | Firestore সেশন-ডক রাইট (helper-এর ইউজার-অ্যাকশন) |
| 02 Notification Service | `SessionPush` → Worker `/notify` (Firebase **ID token**-এ অথেনটিকেটেড — APK-মধ্যস্থ কনস্ট্যান্ট কী নয়) |
| 03 FCM | Worker: টোকেন-ভেরিফাই + aud/iss/exp লোকাল রি-চেক + সেশন রি-রিড + claim-before-send + data-only HIGH |
| 04 Android Handler | `PushTokenService.onMessageReceived` (নিহত অ্যাপেও ডেলিভারি) — লিসেনার-পাথ ও FCM-পাথ **উভয়েই `AskNotifier`-এ ডেলিগেট, তাই byte-identical** |
| 05 Notification Router | `DeepLinks` বাস + `AskNotifier`-এর deep-link extras + Allow/Deny `SessionActionReceiver` + MainActivity-র রাউটার-ইফেক্ট (`MainActivity.kt:907–917`) |
| 06 Notification Center | `NotificationsLog.recordAt` ইভেন্টের প্রকৃত সময়ে + (type,arg2) ১০-মিনিট ডিডুপ |

**"একই Event দুইবার Popup" উদ্বেগ:** সমাধান বিদ্যমান — (ক) নোটিফিকেশন-ID স্থিতিশীল (kind, sessionId) তাই রি-পোস্ট জমে নয়; (খ) লগে (type,arg2) ১০-মিনিট ডিডুপ (`NotificationsLog.kt:218–233`)।

**"Location Update-এ Notification নয়" পরামর্শ:** অনুসৃত — লোকেশন-আপডেট কখনোই নোটিফিকেশন পাঠায় না; পাঠায় শুধু ইভেন্ট-ট্রানজিশন (ask/answer/sharing on-off/place alert/অপ্রত্যাশিত বন্ধ)।

### ৩গ. Notification Center — ⚠️ আছে, কিন্তু ৪টি সত্যিকারের ফাঁক

**যা আছে** (`NotificationsScreen.kt` + `NotificationsLog.kt`): ডেডিকেটেড সারফেস (bell → রুট), Attention সেকশন (পেন্ডিং ask / পারমিশন-ফাঁক / সেটআপ-সমস্যা), Live-sessions সেকশন, Recent-events সেকশন (লোকাল-পার্সিস্টেড, ১০০-ইভেন্ট ক্যাপ, সাইন-আউটে ওয়াইপ, ১৫ ভাষায়), খালি-স্টেট, ⓘ-ব্যাখ্যা।

**যা নেই (সত্যিকারের ফাঁক — §৮-এ পরিকল্পনা):**
1. ইভেন্টে পঠিত/অপঠিত (read/unread) স্টেট নেই → "Unread" ফিল্টার ও "Mark as Read / Mark All" অসম্ভব
2. ক্যাটাগরি-ফিল্টার ট্যাব নেই (All / Connection / Device / Location / Security)
3. Recent-events সারিতে **onClick নেই** — ট্যাপ করলে সংশ্লিষ্ট স্ক্রিনে যাওয়ার বাহ্যিক চাহিদা অপূরণ
4. ইউজার-মুখী "Clear History" বাটন নেই (wipes হয় শুধু সাইন-আউটে)

**গুরুত্বপূর্ণ প্রসঙ্গ:** বাহ্যিক রিপোর্টের ১২-ক্যাটাগরি পুশ-টেবিলের বেশিরভাগই (Help Request, Connection Accepted, Device Offline, Location Sharing Started/Stopped, Guest Session Expiring…) ইতিমধ্যে পাইপলাইনে রয়েছে বা ইচ্ছাকৃতভাবে নেই (লোকেশন-স্প্যাম)। উপরের ৪টি ফাঁকই একমাত্র বাস্তব অপূর্ণতা।

---

## ৪. Map & Live Location — যাচাই

### ৪ক. Tracking Screen-এর উপাদান — বাহ্যিক চাহিদা বনাম বাস্তবতা

| বাহ্যিক চাহিদা | অবস্থা | প্রমাণ |
|---|---|---|
| Location Marker | ✅ | `TrackingTab` মার্কার-ভিজ্যুয়াল + ডিভাইস-লেবেল |
| Current Location / Zoom / Accuracy Circle | ✅ | জুম-ইন/আউট অ্যানিমেশন + `AccuracyVisual` (১..১০০০ মি স্যানিটি-গেট) |
| Map Loading / Error State | ✅ | ডার্টি-ফ্ল্যাগ রিড্র + সৎ খালি/এরর সারফেস |
| 3 ধরনের ম্যাপ | ✅ (চাহিদার অতিরিক্ত) | 2D CARTO Voyager (দ্রুত CDN ডিফল্ট) / Esri স্যাটেলাইট / Esri টেরেইন — প্রতিস্থায়ী পছন্দ |
| Last Updated Timestamp | ✅ | `locationTimeString` + "last known" লেবেল |
| Device List (নাম/অনলাইন/শেয়ারিং/last seen) | ✅ | `deviceLabel` + `PresenceBadge` + ফ্রেশনেস-লাইন |
| Battery Status | ❌ **নেই** | `devicePresence`-এ ব্যাটারি-ফিল্ডই নেই (বাহ্যিক রিপোর্ট নিজেই ঐচ্ছিক বলেছে) |
| Location Info Card | ✅ | `LocationDetailScreen` (`tracking/{relId}`): last update, accuracy, staleness, sharing on/off, open-in-maps |

### ৪খ. অবস্থা-ভিত্তিক UI (৮-স্টেট টেবিলের ম্যাপিং)

| বাহ্যিক রিপোর্টের স্টেট | প্রকৃত বাস্তবায়ন |
|---|---|
| GPS Loading | লোডিং-স্কেলেটন + `loc_no_fix` সৎ খালি-স্টেট |
| Permission Denied | `PermissionDeniedPanel` + সিস্টেম-সেটিংসে হ্যান্ড-অফ |
| Approximate Location | নির্ভুলতা-বৃত্ত + মিটার-সংখ্যা প্রদর্শিত |
| GPS Disabled / Internet Offline | আপলোড-স্টেট FAILED/PENDING/UNAVAILABLE (ইঞ্জিন-রিপোর্টার, নীরব-যখন-ঠিক) |
| Location Stale | **STALE বাকেট (<১৫ মিনিট) + ড্যাশড বৃত্ত + ভিন্ন রং** — পুরোনো ডেটা কখনো "live" সাজে না |
| Sharing Stopped | ON/OFF ব্যানার + P3 অপ্রত্যাশিত-বন্ধ নোটিফিকেশন |
| Device Offline | OFFLINE বাকেট + প্রিসেন্স-ব্যাজ |

**"পুরোনো Location-কে নতুন হিসেবে দেখানো যাবে না" — এই অ্যাপের সবচেয়ে শক্ত নিয়মগুলোর একটি:** ফ্রেশনেস পাঠকের ঘড়িতে গণনা (LIVE <৫ মিনিট / STALE <১৫ / তারপর OFFLINE — `TrackingTab.kt:167–186`), সলিড-বনাম-ড্যাশড বৃত্ত, রঙ-ভাষা, এবং "last known" শব্দচয়ন।

### ৪গ. Location Pipeline
বাহ্যিক ৯-ধাপ ফ্লো (Consent→Permission→Service→Update→Authz→DB→Listener→UI→Marker) প্রকৃত আর্কিটেকচারের হুবহু সমতুল্য — `LIVE_LOCATION_ARCHITECTURE.md` §৩/§৪ + Phase ৮-এর `locationAuthorizations` dual-read-path + §৮৫-এর ৯-হুমকি শূন্য-বাইপাস পর্যালোচনা। Background-location অনুমতি ঐচ্ছিক রাখা হয়েছে (শুধু FGS sharing চালু করতে চাইলে) — Play-নীতি-সচেতন সিদ্ধান্ত।

---

## ৫. Family ও Guest — ৬ পরীক্ষার ফলাফল

| বাহ্যিক পরীক্ষা | ফলাফল | প্রমাণ |
|---|---|---|
| Family/Guest পারমিশন এক হয়ে যাচ্ছে? | ✅ না — `kind` রুলসে পরম | রুলস: guest≠family চিরস্থায়ী, ক্রস-অ্যাক্টিভেশন অসম্ভব (Phase 10) |
| Guest Expiry-তে Access বন্ধ? | ✅ হ্যাঁ | প্রতিটি সেশন-রাইট ও লোকেশন-রিডে `guestExpiresAt > request.time` (রুলস:৬২–৬৭) + UI কাউন্টডাউন |
| QR একাধিকবার? | ✅ অসম্ভব | এককালীন টোকেন, SHA-256 হ্যাশ, অ্যাটমিক রিডেম্পশন, `getAfter()` পুনঃযাচাই |
| Receiver Approval বাধ্যতামূলক? | ✅ সেশনে | REQUESTED→ACTIVE শুধু রিসিপিয়েন্ট গ্রান্টে; পেয়ারিং পূর্ব-অনুমোদিত-QR মডেল (নথিভুক্ত সিদ্ধান্ত) |
| Remove-এ পুরোনো Access বাতিল? | ✅ হ্যাঁ | revoke → ওপেন সেশন সমাপ্ত (§৮৭ P6) + liveLocations পড়ার পথ ACTIVE-রিলেশনশিপ-নির্ভর |
| কে কী দেখবে পরিষ্কার? | ✅ | `locationAuthorizations` স্কোপ (live/history আলাদা) + dual read path + UX-spec স্পষ্টীকরণ |

সব ছয়টিই `rules-*-probe.mjs` স্যুট + §৬৯ G1–G4 + §৮৫ নিরাপত্তা-পর্যালোচনায় প্রমাণিত।

---

## ৬. Frontend UI/UX — ৪-ট্যাব পরিকল্পনা বনাম প্রকৃত নেভিগেশন

বাহ্যিক রিপোর্টের প্রস্তাবিত ৪-ট্যাব কাঠামো **অক্ষরে-অক্ষরে ইতিমধ্যে বাস্তবায়িত** (`AppNav.kt:64–77`): Home · Add Device · Tracking · Settings — Notifications হেডার-বেল রুট হিসেবে ট্যাবের উপরে।

- **Home:** হিরো-ওয়েলকাম, ফিচার-গ্রিড, সম্মতি-কার্ড, কমপ্যাক্ট সেটআপ-স্ট্রিপ, Family Overview (`home/family`), Recent Activity (`home/sessions`) ✅
- **Add Device:** QR জেনারেট/স্ক্যান, Family/Guest নির্বাচন, রোল-প্রিভিউ শীট, পেন্ডিং আমন্ত্রণ-স্ট্যাটাস, সংযুক্ত ডিভাইস ✅
- **Tracking:** লাইভ ম্যাপ (৩ স্টাইল), মেম্বার-তালিকা, `OwnSharingCard`, হিস্ট্রি (`tracking/{relId}/history`), My Places (`tracking/places`) ✅
- **Setting:** ৫-কার্ড স্তরক্রম (§৭৩ পুনর্বিন্যাস), ভাষা-ডায়ালগ, নোটিফিকেশন-নিয়ন্ত্রণ + টেস্ট-প্রোব, ক্র্যাশ-রিপোর্ট, About ✅

**ডিজাইন-নীতিগুলোও বিদ্যমান:** কার্ড-ভিত্তিক লেআউট, `LoadingSkeleton`, `EmptyState`, `ErrorPanel(onRetry)`, বটম-শীট, কনফার্মেশন-ডায়ালগ, ডার্ক/লাইট (`values-night`), ১৫ ভাষা, সিনিয়র-প্রথম টাইপ-স্কেল (P10), স্থানীয় ডাকনাম (P13), দুই-স্তরের-সর্বোচ্চ নেভিগেশন।

---

## ৭. Backend & API অডিট (বাহ্যিক রিপোর্টের §৭ — যে-অংশটুকু পৌঁছেছিল)

| বাহ্যিক পরীক্ষা | ফলাফল | প্রমাণ |
|---|---|---|
| Authentication — expired token হ্যান্ডলিং | ✅ | Firestore SDK টোকেন স্বয়ংক্রিয়ভাবে রিফ্রেশ করে; `/notify`-তে short-lived Firebase ID token + exp/aud/iss লোকাল রি-চেক; সব Firestore-ত্রুটি `describeFirestoreError`-এ লোকালাইজড (RESOURCE_EXHAUSTED-সহ, v0.29.0) |
| Authorization — অন্যের ডেটা | ✅ | ডিফল্ট-ডিনাই রুলস; §৮৫: ৯ হুমকি-শ্রেণিতে **শূন্য বাইপাস**; negative-probe স্যুট সবুজ; প্রকাশিত রুলসেট রিপো-বাইট-সমান (লাইভ-ফায়ার রিডব্যাক) |

---

## ৮. সত্যিকারের ফাঁক (Genuine Gaps) — সম্পূর্ণ তালিকা ও প্রস্তাবিত ওয়েভ

**G1 — Notification Center আপগ্রেড (একমাত্র উল্লেখযোগ্য ফাঁক):**
লোকাল-অনলি পরিবর্তন (শূন্য নতুন Firestore রিড — §৭৫ কোটা-শৃঙ্খলার পূর্ণ সম্মতিতে):
1. `NotifEvent`-এ `readAt` ফিল্ড (লোকাল JSON-স্কিমা v2, পশ্চাদগামী-সামঞ্জস্য)
2. "Unread" ফিল্টার + Mark-as-Read (ট্যাপে স্বয়ংক্রিয়) + Mark-All
3. ক্যাটাগরি-চিপস: All / Connection / Location / Security (লোকাল ইভেন্ট-টাইপ থেকে ম্যাপড)
4. প্রতিটি সারিতে onClick → সংশ্লিষ্ট রুট (session_asked→home, sharing_*→tracking, place_alert→tracking/places — `DeepLinks`-এর রাউটার পুনঃব্যবহার)
5. "Clear History" বাটন (কনফার্মেশন-ডায়ালগসহ)
আনুমানিক পরিসর: `NotificationsLog.kt` + `NotificationsScreen.kt` + ~১২ নতুন স্ট্রিং-কী ×১৫ লোকেল + `NotifEvent`-স্কিমা মাইগ্রেশন। CI-গেট প্রয়োজন (কম্পাইল-যাচাই)।

**G2 — ব্যাটারি স্ট্যাটাস (ঐচ্ছিক, স্বল্প-অগ্রাধিকার):**
`devicePresence`-হার্টবিটে ঐচ্ছিক ব্যাটারি-ফিল্ড + রুলস-ফিল্ড-অনুমতি + প্রেসেন্স-ব্যাজে শতকরা-লাইন। নতুন ডেটা-শ্রেণি সংগ্রহ — Owner-সম্মতি-প্রয়োজন।

**স্বীকৃত-বিলম্বিত (ইতিমধ্যে নথিভুক্ত, §৮৭-এর "遗留"):** TURN টেম্পোরারি-ক্রেডেনশিয়াল, Google-Maps-ইঞ্জিন মাইগ্রেশন, বাংলা-প্রথম-ডিফল্ট সিদ্ধান্ত।

---

## ৯. সৎ ঘোষণা

- এই যাচাই **সম্পূর্ণরূপে স্ট্যাটিক সোর্স-পাঠ** — রিপোজিটরি লোকাল-মিররে (`a5cbca0`, remote-main-এর সঙ্গে ০/০ পার্থক্য) পরিচালিত; কোনো নতুন রানটাইম-টেস্ট চালানো হয়নি। রানটাইম-সত্যের গেট পূর্বের মতোই CI (#859 সবুজ) + Owner-এর দুই-ফোন ম্যানুয়াল চেকলিস্ট (RELEASE_NOTES §১২)।
- **পুশ-সক্ষমতা নেই এই সেশনে:** `.secrets/github-token.txt` স্যান্ডবক্স-রিসাইকেলে হারিয়েছে (§৫৮-পূর্ববর্তী ঘটনার পুনরাবৃত্তি)। এই ডকুমেন্ট + worklog §৮৮ লোকাল-কমিটে প্রস্তুত; Owner টোকেন দিলে ১-কমান্ডে push → CI-গেট।
- কোনো সোর্স-কোড পরিবর্তন করা হয়নি এই ওয়েভে — G1/G2 বাস্তবায়ন Owner-এর স্পষ্ট অনুমোদনের অপেক্ষায় (§৬৪-এর STOP-condition সংস্কৃতি)।
