# PHASE 0 — পরিবেশ মূল্যায়ন ও সম্ভাব্যতা (Environment & Feasibility)

**Project:** Remote Family Support & Device Assistance Platform
**Status:** Phase 0 — পরিকল্পনা সম্পন্ন। কোনো কোড এখনো তৈরি, কম্পাইল বা টেস্ট করা হয়নি।
**আপডেট:** Owner-এর উত্তর অনুযায়ী Section 9, 14, 16 আপডেটেড — **শূন্য-খরচ নীতি**, UI ডিফল্ট **ইংরেজি**, ওয়ার্কফ্লো **Codespaces** (চূড়ান্ত)।

---

## 1. প্রজেক্ট এক নজরে

একটি Android-to-Android রিমোট সহায়তা অ্যাপ, যেখানে বিশ্বস্ত পরিবারের সদস্যরা দূর থেকে একে অপরের ফোনের সমস্যা সমাধানে সাহায্য করতে পারবেন।

- Helper (সাহায্যকারী) ফোন থেকে Receiver (সহায়তা-গ্রহীতা) ফোনের স্ক্রিন দেখা যাবে — **শুধুমাত্র গ্রহীতার স্পষ্ট অনুমতিতে**।
- Android প্ল্যাটফর্ম যা অনুমতি দেয় না (যেমন: সব ফোনে সম্পূর্ণ রিমোট কন্ট্রোল), সে বিষয়ে অ্যাপ **সৎ থাকবে** — বিকল্প হিসেবে Guided Support (ধাপে ধাপে নির্দেশনা) থাকবে।
- ব্যবহারকারী প্রযুক্তিতে অনভিজ্ঞ হতে পারে — তাই UI খুব সহজ, বাংলা ও ইংরেজি দুই ভাষায়।

---

## 2. তিনটি আলাদা স্তম্ভ (সবচেয়ে গুরুত্বপূর্ণ ডিজাইন সিদ্ধান্ত)

আপনার পরামর্শ অনুযায়ী তিনটি জিনিস **পুরোপুরি আলাদা** রাখা হবে:

| স্তম্ভ | কী | কোথায় থাকবে | একে অপরের সঙ্গে সম্পর্ক |
|---|---|---|---|
| **1. Permanent Pairing** | দুটি বিশ্বস্ত ডিভাইসের দীর্ঘমেয়াদি রেজিস্ট্রেশন রেকর্ড | Firestore: `trustedDeviceRelationships` | এটি শুধু একটি "রেকর্ড" — কোনো চলমান সংযোগ নয় |
| **2. Persistent Connectivity** | ডিভাইস এখন অনলাইন কিনা তার Presence (হার্টবিট + FCM) | Firestore: `devicePresence` + FCM token | Presence **মানেই Pairing নয়**; presence-এর TTL থাকবে (stale হবে) |
| **3. Remote Support Session** | প্রতি সেশনের আলাদা অনুমতি, সম্মতি ও MediaProjection/WebRTC | Firestore: `sessions` + সিগন্যালিং সার্ভার | পেয়ার থাকলেও **প্রতিটি সেশনে আলাদা অনুমতি লাগবে** |

**মূল নীতি:** পেয়ার করা = বিশ্বস্ত ডিভাইস হিসেবে নিবন্ধিত। কিন্তু পেয়ারিং **কখনোই** স্ক্রিন দেখা বা কন্ট্রোলের অটো-অনুমতি নয়।

### ডিভাইস স্টেট (এই ৭টি অবস্থা সবসময় আলাদা করে দেখানো হবে)

1. **Registered** — অ্যাকাউন্টে যুক্ত
2. **Trusted/Paired** — বিশ্বস্ত ডিভাইস হিসেবে যুক্ত
3. **Online** — সাম্প্রতিক ভেরিফাইড হার্টবিট আছে (নইলে অফলাইন দেখাবে)
4. **Request Pending** — সহায়তার অনুরোধ অপেক্ষমাণ
5. **Active Session** — এখন সেশন চলছে
6. **Disconnected** — সেশন শেষ/বিচ্ছিন্ন
7. **Revoked** — সম্পর্ক বাতিল; পুরনো QR/token/সেশন আর কাজ করবে না

---

## 3. প্রস্তাবিত আর্কিটেকচার (সহজ ভাষায়)

তিনটি অংশ: **Android অ্যাপ** (Kotlin + Jetpack Compose), **Backend** (Node.js + TypeScript সিগন্যালিং সার্ভার), **Firebase** (Auth, Firestore, FCM)। স্ক্রিন শেয়ারের ভিডিও স্ট্রিম **সরাসরি দুই ফোনের মধ্যে** WebRTC দিয়ে যায় (দরকার হলে TURN রিলে হয়ে); সার্ভার শুধু "পরিচয় ও অনুমতি" যাচাই করে এবং connection তৈরির খবর (signaling) আদান-প্রদান করে।

```
                     ┌─────────────────────────────────┐
                     │         Firebase Cloud          │
                     │  • Auth (Google Sign-In)        │
                     │  • Firestore (ডেটা + Rules)     │
                     │  • FCM (পুশ নোটিফিকেশন)         │
                     └───────────────▲─────────────────┘
                                     │ Admin SDK
                                     │ (শুধু বিশ্বস্ত backend)
 ┌──────────────┐   HTTPS + WSS     │
 │ Helper ফোন   │◄────────────────► │ ┌──────────────────────┐
 │ (Android app)│                   │ │ Signaling Server     │
 │ Compose UI   │                   │ │ Node.js + TypeScript │
 │ WebRTC peer  │                   │ │ • ID token যাচাই     │
 └──────┬───────┘                   │ │ • Invitation যাচাই   │
        │                           │ │   (atomic, 1 বার)    │
        │  WebRTC media (DTLS-সুরক্ষিত) │ • Session state এনফোর্স │
        │  P2P হলে সরাসরি,          │ │ • TURN credential    │
        │  NAT-এ আটকে গেলে TURN হয়ে │ │   (short-lived HMAC) │
        │                           │ └──────────────────────┘
 ┌──────┴───────┐                   │
 │ Receiver ফোন │◄──── WebRTC ──────┘ (ভিডিও সরাসরি helper-এর কাছে যায়)
 │ MediaProjection│
 │ + সম্মতি UI  │
 └──────────────┘
```

**কেন এই আর্কিটেকচার:**
- Authorization **শুধু সার্ভারে** এনফোর্স করা হবে — ক্লায়েন্টের কথায় বিশ্বাস নয় (least privilege)।
- Firestore Security Rules + সার্ভার-সাইড চেক দুটোই থাকবে (defense in depth)।
- WebRTC peer-to-peer হলে সার্ভারের ব্যান্ডউইথ খরচ কম; TURN শুধু দরকারি হলে।

---

## 4. দুইটি কানেকশন মোড

| বিষয় | Mode A: Family | Mode B: Guest |
|---|---|---|
| উদ্দেশ্য | পরিবারের স্থায়ী বিশ্বস্ত ডিভাইস | সাময়িক, বাইরের সহায়তা |
| পেয়ারিং | QR/পেয়ারিং কোড, একবার — পরে আর স্ক্যান লাগবে না | QR/short-lived ইনভাইটেশন কোড, প্রতিবার |
| মেয়াদ | স্থায়ী (যতক্ষণ না revoke করা হয়) | সার্ভার-এনফোর্সড নীতি অনুযায়ী মেয়াদোত্তীর্ণ |
| নতুন সেশনের অনুমতি | প্রতিবার গ্রহীতার আলাদা অনুমোদন | প্রতিবার গ্রহীতার আলাদা অনুমোদন |
| Guest → Family | **স্বয়ংক্রিয়ভাবে কখনো নয়** — আলাদা পেয়ারিং প্রক্রিয়া লাগবে | — |
| UI-তে স্পষ্ট পার্থক্য | "Family" ব্যাজ | "Guest (সাময়িক)" ব্যাজ |

---

## 5. QR পেয়ারিং নিরাপত্তা নির্বচন

### QR কোডে যা থাকবে (শুধু এটুকু)
```
https://<app-domain>/pair#inv=<invitationId>&tok=<256-bit random one-time token>
```

### QR কোডে যা **কখনো** থাকবে না
- পাসওয়ার্ড, Firebase ID token, refresh token, প্রাইভেট কী
- দীর্ঘমেয়াদি কোনো credential
- ব্যক্তিগত তথ্য

### সার্ভার-এনফোর্সড নীতি (configurable security policy)
- ইনভাইটেশন মেয়াদ: **৫ মিনিট** (পরিবার) / Guest-এর জন্য আলাদা নীতি
- **একবারই** redeem হবে — Firestore transaction দিয়ে **atomic redemption** (দুটি একসঙ্গে request এলে একটিই সফল হবে)
- সার্ভারে টোকেনের **SHA-256 hash** থাকবে, কাঁচা টোকেন নয়
- Rate limit: ইনভাইটেশন তৈরি ও redeem — উভয়তে
- মেয়াদ/বাতিল হলে পুনঃব্যবহার **প্রত্যাখ্যাত**

### Pairing state machine
```
PENDING → CLAIMED → APPROVAL_REQUIRED → PAIRED
   │          │
   ▼          ▼
EXPIRED    REJECTED / CANCELLED / REVOKED
```
ক্লায়েন্ট লোকাল ভ্যারিয়েবল বদলে নিজেকে "paired" দেখাতে **পারবে না** — অবস্থা বদল শুধু বিশ্বস্ত সার্ভার করবে।

---

## 6. সেশন লাইফসাইকেল ও সম্মতি (Consent)

```
IDLE → REQUEST_CREATED → REQUEST_DELIVERED → AWAITING_OWNER_APPROVAL
     → OWNER_APPROVED → ANDROID_PERMISSION_REQUIRED → CONNECTING
     → ACTIVE → ENDING → ENDED
```
ব্যর্থতার অবস্থা: REJECTED, EXPIRED, CANCELLED, NETWORK_FAILED, AUTHENTICATION_FAILED, PERMISSION_DENIED, UNSUPPORTED, REVOKED

**প্রতিটি সংবেদনশীল সেশনের আগে:**
1. অনুরোধকারীর পরিচয় ও অনুমতি সার্ভারে যাচাই
2. সঠিক ডিভাইসকেই টার্গেট করা হয়েছে কিনা নিশ্চিত
3. গ্রহীতার ফোনে স্পষ্ট ব্যাখ্যা (কী দেখা যাবে, কী হবে)
4. গ্রহীতার **স্পষ্ট অনুমোদন** (পুশ নোটিফিকেশনের বাটন চেপেও কনসেন্ট বাইপাস হবে না)
5. Android সিস্টেম পারমিশন (MediaProjection ডায়ালগ ইত্যাদি)
6. অনুমোদিত capability-ই শুধু চালু
7. সেশন চলাকালীন স্পষ্ট সূচক (স্ক্রিন শেয়ার চলছে)
8. সবার কাছেই সহজ **End Session**
9. সেশন শেষে authorization **অবৈধ** হয়ে যাবে

---

## 7. ডেভেলপমেন্ট পরিবেশ মূল্যায়ন (শুধু Android ফোন থাকলে)

ধরে নিচ্ছি: আপনার কাছে **কোনো কম্পিউটার নেই**, শুধু একটি Android ফোন। প্রতিটি অপশনের সৎ মূল্যায়ন:

| অপশন | কীভাবে | সুবিধা | সীমাবদ্ধতা | রায় |
|---|---|---|---|---|
| **A. GitHub Codespaces + GitHub Actions** | ফোনের Chrome ব্রাউজারে ক্লাউড VS Code; বিল্ড ক্লাউডে GitHub Actions-এ; APK artifact ডাউনলোড করে ফোনে ইনস্টল | আসল Gradle বিল্ড, ফোন গরম/স্লো হয় না, public repo-তে Actions ফ্রি | বড় স্ক্রিন-ইডিটিং একটু কষ্টকর; ইন্টারনেট লাগবে | ✅ **প্রধান ওয়ার্কফ্লো (সুপারিশকৃত)** |
| **B. AndroidIDE** (ফোনেই IDE) | ফোনে Gradle + JDK 17 চলে, Kotlin লেখা যায় | অফলাইনে ছোট এডিট সম্ভব | Compose-সহ বড় বিল্ড খুব ধীর, ফোন গরম, টুলিং অসম্পূর্ণ | ⚠️ ফলব্যাক হিসেবেই শুধু |
| **C. Termux + Acode** | Termux-এ OpenJDK+Gradle, Acode-এ এডিট | সম্পূর্ণ ফোনেই | সবচেয়ে ধীর, সবচেয়ে ঝামেলাপূর্ণ | ❌ সুপারিশ নয় |
| **D. Cloud Build service** (Codemagic ইত্যাদি) | ক্লাউডে CI বিল্ড | ভালো ফ্রি কোটা | Codespaces-এর চেয়ে অতিরিক্ত জটিলতা | ℹ️ পরে দরকার হলে |

### সুপারিশকৃত দৈনন্দিন ওয়ার্কফ্লো (Option A)
1. ফোনে **GitHub** অ্যাপ/ওয়েবে repo — Codespaces খুলে কোড এডিট (মোবাইল VS Code)
2. `git push` করলে **GitHub Actions** স্বয়ংক্রিয়ভাবে `assembleDebug` চালাবে
3. Actions-এর artifact থেকে **debug APK ডাউনলোড**
4. ফোনে APK ইনস্টল → টেস্ট
5. ব্যাকএন্ড পরিবর্তন হলে সেটিও Actions/deploy workflow দিয়ে হোস্টে যাবে
6. সব ফলাফল `docs/`-এ লেখা হবে — কোন টেস্ট চলল, কোনটা পাস/ফেল/অটেস্টেড

**সততার নোট:** ফোনের ছোট স্ক্রিনে বড় রিফ্যাক্টর করা কষ্টকর; ফোনের স্টোরেজে দুটি ডিভাইসের APK রাখার জায়গা লাগবে। এগুলো গ্রহণযোগ্য ট্রেড-অফ।

---

## 8. প্রয়োজনীয় অ্যাকাউন্ট ও টুল (ফোনেই তৈরি করা যাবে)

| # | অ্যাকাউন্ট/টুল | কাজ | খরচ |
|---|---|---|---|
| 1 | Google অ্যাকাউন্ট | ফোনে আছেই | ফ্রি |
| 2 | GitHub অ্যাকাউন্ট | কোড, Codespaces, Actions, APK artifact | ফ্রি (public repo; private-এ 2000 CI min/মাস) |
| 3 | Firebase প্রজেক্ট | Auth, Firestore, FCM | Spark tier ফ্রি |
| 4 | Backend হোস্ট (Render/Fly.io) | Signaling সার্ভার | ফ্রি tier দিয়ে শুরু |
| 5 | STUN | Google ফ্রি STUN | ফ্রি |
| 6 | TURN (পরে, Phase 7-এ) | কঠোর NAT ভাঙা | ফ্রি টিয়ার বা ~$3–5/মাস |
| 7 | (ঐচ্ছিক) Metered.ca / Xirsys | Managed TURN | ফ্রি কোটা সীমিত |

---

## 9. এক্সটার্নাল সেবা ও খরচ (Free vs Paid)

| সেবা | ফ্রি টিয়ার | ফ্রি-র বাইরে কখন লাগবে | আনুমানিক খরচ |
|---|---|---|---|
| Firebase Auth (Google Sign-In) | প্রায় আনলিমিটেড | — | 0 |
| Firestore | 50k read / 20k write per day, 1GB | ভারী টেস্টিং বা বড় ইউজারবেস | Blaze প্ল্যান, ব্যবহারভেদে ~$1–25/মাস |
| FCM | আনলিমিটেড পুশ | — | 0 |
| Cloud Functions | **Blaze প্ল্যান লাগবে** (ফ্রি কোটা সহ) | সার্ভারলেস রুট বেছে নিলে | ছোট স্কেলে ~0–5/মাস |
| Signaling হোস্ট (Render/Fly.io) | ফ্রি tier (Render-এ idle হলে cold-start) | সবসময় জাগ্রত সার্ভার | $0 → $5–7/মাস |
| TURN রিলে | Metered.ca ফ্রি 50GB/মাস | CGNAT/কঠোর NAT-এ P2P ব্যর্থ হলে | ~$3–10/মাস (ব্যান্ডউইথভেদে) |

**শূন্য-খরচ নীতি (Owner-এর সিদ্ধান্ত):** কোনো খরচ হবে না — সব ফ্রি টিয়ার। Cloud Functions (Blaze প্ল্যান লাগে) বাদ — সার্ভার-কাজ সব signaling সার্ভারই করবে (Render ফ্রি টিয়ার: idle হলে ঘুমায়, জাগতে ~৩০–৫০ সেকেন্ড — ডেভ/পরিবার স্কেলে গ্রহণযোগ্য)। TURN: Metered.ca ফ্রি কোটা (50GB/মাস)। ভবিষ্যতে খরচ-জনিত প্রয়োজন হলে **আগে Owner-এর অনুমতি** নেওয়া হবে।

---

## 10. Android প্ল্যাটফর্ম সীমাবদ্ধতা (সৎ ঘোষণা)

1. **প্রতি সেশনে MediaProjection কনসেন্ট** — স্ক্রিন ক্যাপচারের জন্য প্রতিবার সিস্টেম ডায়ালগে ইউজারকেই অনুমতি দিতে হবে; Android 14+ এ foreground service type `mediaProjection` বাধ্যতামূলক; এক অনুমতি চিরকালের জন্য পুনর্ব্যবহারযোগ্য ধরা যাবে না; বন্ধ/বাতিল হলে গোপনে আবার চালু করা যাবে না।
2. **সম্পূর্ণ রিমোট কন্ট্রোল পাবলিক API-তে নেই** — AccessibilityService-এর সীমিত ব্যবহার ছাড়া ইনপুট ইনজেকশন সম্ভব নয়, আর Play policy-তে তা কঠোর নিয়ন্ত্রিত। তাই মূল অভিজ্ঞতা হবে **স্ক্রিন শেয়ার + Guided Support**; AccessibilityService-এর নির্দিষ্ট permitted action (যেমন সেটিংস স্ক্রিন খুলে দেওয়া) Phase 8-এ পলিসি-সামঞ্জস্য যাচাই করে যোগ হবে।
3. **লক স্ক্রিন বাইপাস অসম্ভব** — এবং আমরা চেষ্টাও করব না।
4. **ব্যাকগ্রাউন্ড সীমাবদ্ধতা** — Doze/App Standby ও vendor battery killer (Xiaomi/Realme ইত্যাদি) হার্টবিট থামাতে পারে; তাই Presence **best-effort**: FCM high-priority পুশ + চালু থাকলে WebSocket; stale হলে UI-তে সৎ "Offline"।
5. **পুশ নোটিফিকেশন নিশ্চিত নয়** — বিলম্ব/ব্যর্থ হতে পারে; সার্ভার টাইমআউট ও অবস্থা দেখাবে; নোটিফিকেশনের অ্যাকশনে কনসেন্ট বাইপাস নয়।
6. **Play Store পলিসি** — প্রাথমিক সংস্করণ সাইডলোড (APK) হিসেবে বিতরণ হবে; Play-তে ছাড়ার আগে পলিসি রিভিউ ও ঘোষণাপত্র লাগবে (Phase 13)।

---

## 11. প্রাথমিক রিপোজিটরি কাঠামো

```
remote-family-support/
├── android-app/                        # Kotlin + Jetpack Compose + Material 3
│   ├── app/
│   │   └── src/main/java/com/familysupport/app/
│   │       ├── ui/                     # Compose screens (home, pairing, session…)
│   │       │   ├── home/
│   │       │   ├── pairing/
│   │       │   ├── devices/
│   │       │   └── session/
│   │       ├── domain/                 # Use cases: pairing, session auth, presence
│   │       ├── data/                   # Repositories: Firestore, API, FCM
│   │       ├── platform/               # MediaProjection service, FGS, notifications
│   │       ├── webrtc/                 # Peer connection factory, signaling client
│   │       ├── core/                   # Result types, error mapping, i18n (bn/en)
│   │       └── di/                     # Dependency injection
│   └── build.gradle.kts
├── backend/                            # Node.js + TypeScript
│   └── src/
│       ├── api/                        # REST: invitations, sessions, devices
│       ├── signaling/                  # Authenticated WebSocket (WSS)
│       ├── auth/                       # Firebase ID token verification
│       ├── turn/                       # Short-lived TURN credential (HMAC)
│       └── db/                         # Firestore access + transactions
├── docs/                               # PHASE-0.md, প্রতি Phase-র রেকর্ড, থ্রেট মডেল
├── .github/workflows/
│   ├── android-build.yml               # PR/push → assembleDebug → APK artifact
│   └── backend-deploy.yml
└── README.md
```

---

## 12. ধাপে ধাপে বাস্তবায়ন পরিকল্পনা (Phase 0–13)

| Phase | কাজ | মোবাইল-ওয়ার্কফ্লো নোট |
|---|---|---|
| **0** ✅ | পরিবেশ মূল্যায়ন ও পরিকল্পনা (এই ডকুমেন্ট) | সম্পন্ন |
| **1** | Android প্রজেক্ট কাঠামো, Compose + Material 3, Navigation, থিম, বাংলা/ইংরেজি, ড্যাশবোর্ড (mock data, স্পষ্ট লেবেলসহ) | Actions-এ প্রথম debug APK |
| **2** | Firebase Auth + Google Sign-In, লগআউট, পার্সিসটেন্স, সার্ভারে token যাচাই | SHA-1 ফিঙ্গারপ্রিন্ট সেটআপ কনসোল থেকেই |
| **3** | ডেটা মডেল + Firestore Security Rules (least privilege) + Emulator Suite টেস্ট | Rules ফাইল repo-তে |
| **4** | সুরক্ষিত QR পেয়ারিং: ইনভাইটেশন, মেয়াদ, atomic redemption, অনুমোদন/প্রত্যাখ্যান, revoke | QR জেনারেট/স্ক্যান (CameraX + ML Kit) |
| **5** | ডিভাইস তালিকা, presence (হার্টবিট+TTL), FCM, reconnect, revoke | ব্যাটারি-অপ্টিমাইজেশন গাইড অ্যাপেই |
| **6** | সিগন্যালিং সার্ভার: সেশন তৈরি, WSS auth, SDP/ICE, state enforcement, rate limit | Render/Fly.io deploy |
| **7** | WebRTC স্ক্রিন শেয়ার: MediaProjection + FGS + STUN/TURN + cleanup | দুই ফোনে প্রথম বাস্তব টেস্ট |
| **8** | Permitted remote interaction যাচাই + capability matrix + Guided Support | পলিসি-সামঞ্জস্য পরীক্ষা |
| **9** | সিকিউরিটি হার্ডেনিং: negative tests, replay প্রতিরোধ, secure logging | থ্রেট মডেল আপডেট |
| **10** | Guest মোড সম্পূর্ণ: মেয়াদ, session-scoped auth, guest≠family | — |
| **11** | UI/UX পলিশ: অ্যাক্সেসিবিলিটি, অনুবাদ, empty/error states | §35 — প্রথম ধাপ v0.13.0 (৪-ট্যাব শেল) সম্পন্ন |
| **12** | QA: ইউনিট/ইন্টিগ্রেশন/ব্যাকএন্ড/E2E (দুই ফোনে) | টেস্ট রেকর্ড docs-এ |
| **13** | বিল্ড/রিলিজ প্রস্তুতি: signing, deploy নির্দেশনা, privacy doc | সাইডলোড APK বিতরণ |

---

## 13. প্রধান ঝুঁকি (Risk Register)

| # | ঝুঁকি | প্রভাব | প্রশমন |
|---|---|---|---|
| R1 | কঠোর NAT/CGNAT-এ WebRTC P2P ব্যর্থ (বাংলাদেশের মোবাইল নেটওয়ার্কে সাধারণ) | স্ক্রিন শেয়ার কানেক্ট না হওয়া | TURN রিলে (short-lived credential); সংযোগ সাফল্য বাস্তবে মেপে রেকর্ড |
| R2 | ফোনেই একক ডেভেলপার — বড় এডিট কষ্টকর | গতি কম | ছোট ছোট কমিট, Codespaces, প্রয়োজনে AndroidIDE ফলব্যাক |
| R3 | Google Sign-In সেটআপ (SHA-1) ভুল হলে লগইন ফেল | Phase 2 ব্লক | ডকুমেন্টেড ধাপে ধাপে কনসোল সেটআপ; ফেল করলে সুনির্দিষ্ট ত্রুটি-চেকলিস্ট |
| R4 | Firestore ফ্রি কোটা টেস্টিংয়ে শেষ | ডেভ থেমে যায় | রিড/রাইট বাজেট, ক্যাশিং, প্রয়োজনে Blaze |
| R5 | একটি ফোনেই টেস্টিং | E2E দুই-ফোন ফ্লো যাচাই সীমিত | পরিবারের সদস্যের ফোনে (সম্মতিসহ) টেস্ট; Firebase Test Lab সাহায্যকারী |
| R6 | Vendor battery killer পুশ/হার্টবিট থামায় | "অনলাইন" ভুল দেখানোর ঝুঁকি | TTL-ভিত্তিক stale presence, সৎ UI, ব্যাটারি-এক্সেম্পশন গাইড |
| R7 | AccessibilityService নিয়ে Play policy | রিজেকশন ঝুঁকি | প্রাথমিকভাবে সাইডলোড; policy-সামঞ্জস্য ছাড়া ফিচার চালু নয় |
| R8 | একক ডেভেলপারের রক্ষণাবেক্ষণ বোঝা | জটিলতা বাড়লে থেমে যায় | সরল স্তরযুক্ত আর্কিটেকচার, অতিরিক্ত ডিপেন্ডেন্সি নয় |

---

## 14. সিদ্ধান্তের রেকর্ড (Owner-এর উত্তরসহ — চূড়ান্ত)

| # | সিদ্ধান্ত | Owner-এর উত্তর | অবস্থা |
|---|---|---|---|
| D1 | ওয়ার্কফ্লো | **Codespaces প্রধান** (Owner-এর GitHub রেডি আছে); AndroidIDE শুধু ব্যাকআপ | ✅ চূড়ান্ত |
| D2 | দ্বিতীয় টেস্ট ফোন | এখনই প্রয়োজন নেই; Phase 7-এর আগে পরিবারের সদস্যের ফোন (সম্মতিসহ) হলেই হবে | ⏳ স্থগিত |
| D3 | খরচ / Blaze | **কোনো খরচ নয়** — সব ফ্রি টিয়ার; Cloud Functions বাদ; signaling Render ফ্রি টিয়ারে; TURN ফ্রি টিয়ার | ✅ চূড়ান্ত |
| D4 | UI ডিফল্ট ভাষা | **ইংরেজি** ডিফল্ট; অ্যাপের ভেতরে বাংলা বিকল্প | ✅ চূড়ান্ত |
| D5 | বিতরণ | **সাইডলোড APK** — একমাত্র ফ্রি উপায়; Play Store একেবারে ঐচ্ছিক/পরবর্তী | ✅ চূড়ান্ত |

**শূন্য-খরচ নীতির প্রভাব (ডিজাইন সিদ্ধান্ত):**
- Cloud Functions ব্যবহার হবে না (Blaze লাগে) — সব trusted-server কাজ signaling সার্ভারেই থাকবে
- Render ফ্রি টিয়ারে idle সার্ভার ঘুমায় — জাগতে ~৩০–৫০ সেকেন্ড লাগতে পারে (পরিবার-স্কেলে গ্রহণযোগ্য; UI-তে সৎভাবে দেখানো হবে)
- Codespaces ফ্রি কোটা (মোটামুটি মাসে ~৬০ ঘণ্টা, 2-core) + public repo-তে GitHub Actions ফ্রি
- যেকোনো খরচ-জনিত প্রয়োজনে আগে Owner-এর অনুমতি নেওয়া হবে — নিজে থেকে কিছু কেনা হবে না
- Firebase প্রজেক্ট এখনো খোলা হয়নি — ফোনে খোলার ধাপ Section 16-এ

---

## 15. Phase 0 চেকলিস্ট (গ্রহণযোগ্যতা মানদণ্ড)

- [x] উপলব্ধ ডেভেলপমেন্ট পরিবেশ মূল্যায়ন (Android-only অনুমান)
- [x] Android-only ওয়ার্কফ্লো চিহ্নিত + সীমাবদ্ধতার সৎ ব্যাখ্যা
- [x] Android ও backend আর্কিটেকচার ডকুমেন্টেড (টেক্সট ডায়াগ্রামসহ)
- [x] Family mode ও Guest mode স্পষ্টভাবে আলাদা
- [x] Persistent trusted-device pairing ≠ active access — আলাদা করা হয়েছে (৩ স্তম্ভ)
- [x] QR নিরাপত্তা নির্বচন (কনটেন্ট, মেয়াদ, atomic redemption, hash, rate limit)
- [x] স্ক্রিন শেয়ার/রিমোট ইন্টারঅ্যাকশনের Android সীমাবদ্ধতা চিহ্নিত
- [x] বাহ্যিক সেবা ও খরচ তালিকাভুক্ত (free vs paid)
- [x] প্রাথমিক ফোল্ডার ও প্যাকেজ কাঠামো প্রস্তাবিত
- [x] বাস্তবসম্মত ধাপক্রম (Phase 1–13) তৈরি
- [x] প্রধান ঝুঁকি ও অমীমাংসিত সিদ্ধান্ত ডকুমেন্টেড

**→ Phase 0 সম্পন্ন হওয়ার যোগ্য।**

---

## 16. পরবর্তী সুনির্দিষ্ট পদক্ষেপ (Owner-এর জন্য — আপডেটেড)

**Owner রেডি: GitHub অ্যাকাউন্ট আছে। একমাত্র বাকি কাজ: Firebase প্রজেক্ট খোলা।**

1. **Firebase প্রজেক্ট খুলুন** (ফোনের Chrome ব্রাউজারে):
   - যান: `console.firebase.google.com` → Google অ্যাকাউন্টে লগইন
   - "Create a project" / "Add project" চাপুন
   - নাম দিন: **family-support** → Continue
   - Google Analytics: **Disable** রাখুন (দরকার নেই, সহজ রাখতে) → Create project
   - ব্যাস — আর কিছু করতে হবে না; বাকি সেটআপ Phase 2-তে আমি ধাপে ধাপে বলব
2. চ্যাটে শুধু লিখুন **"হয়েছে"**
3. তারপর **Phase 1** শুরু করব: Android প্রজেক্ট কঙ্কাল + GitHub Actions workflow → ফল: **আপনার ফোনে ইনস্টলযোগ্য প্রথম debug APK** (Phase 1-এ Firebase লাগবে না)

---

## 17. সততার ঘোষণা (Verification Status)

- এই Phase 0-তে **কোনো কোড লেখা, কম্পাইল, বিল্ড, ডিপ্লয় বা টেস্ট করা হয়নি**।
- উপরের সব মূল্যায়ন বর্তমান Android/Firebase ডকুমেন্টেশনের সাধারণ জ্ঞানের ভিত্তিতে; Phase 1-2-তে প্রতিটি সিদ্ধান্ত বাস্তবে যাচাই করে রেকর্ড করা হবে।
- কোনো টেস্ট ফলাফল এখনো নেই; তাই কোনো ফলাফল দাবি করা হয়নি।

---

## 18. Phase 1 সম্পূর্ণ রেকর্ড + টোকেন-সহায়তা নোট (২০২৫-১০-২৬)

### 18.1 যা ঘটেছে (সিদ্ধান্ত ও কাজের রেকর্ড)

- Owner-এর চূড়ান্ত সিদ্ধান্ত: **Codespaces ওয়ার্কফ্লো ✓**, **সাইডলোড APK ✓**; দ্বিতীয় টেস্ট ফোন → Phase 7 পর্যন্ত স্থগিত।
- Owner এককালীন ব্যবহারের জন্য একটি GitHub classic PAT চ্যাটে প্রদান করেন এবং অনুমতি দেন এটি দিয়ে সেটআপ সম্পন্ন করতে; বিল্ড শেষে টোকেন নিজে ডিলিট করার প্রতিশ্রুতি দেন।

### 18.2 টোকেন ব্যবহারের নোট (গুরুত্বপূর্ণ)

- **টোকেনটি কোথাও স্থায়ীভাবে সংরক্ষণ করা হয়নি** — কোনো ফাইল/রিপো/পেজ/কোডে নেই। শুধু সাময়িকভাবে শেল-সেশন ও `/tmp`-তে ব্যবহৃত হয়েছে; কাজ শেষে `/tmp`-এর সব ট্রেস (টোকেন ফাইল, অস্থায়ী git clone-এর ভিতরের remote URL) মুছে ফেলা হয়েছে।
- টোকেনের স্কোপ ছিল অত্যন্ত ব্যাপক ("সকল পারমিশন") — এই কারণেই **কাজ শেষ হওয়া মাত্রই ডিলিট করা বাধ্যতামূলক**। Owner-কে অনুস্মারক দেওয়া হয়েছে: GitHub → Settings → Developer settings → Personal access tokens → টোকেনটি **Delete**।
- ভবিষ্যতে টোকেন দরকার হলে: **সংক্ষিপ্ত মেয়াদি + ন্যূনতম স্কোপ** (শুধু `repo` + `workflow`, যদি সম্ভব হয় fine-grained) ব্যবহার করা উচিত; চ্যাটে টোকেন পাঠানো ঝুঁকিপূর্ণ — সম্ভব হলে এড়ানো ভালো।
- তৈরি করা রিপো **public** — এতে কোনো গোপন তথ্য/কী/টোকেন নেই (যাচাইকৃত); ভবিষ্যতে গোপন তথ্য GitHub **Secrets**-এ যাবে।

### 18.3 Phase 1 বাস্তব যাচাইয়ের ফল (এখন আর দাবি নয় — প্রমাণ আছে)

- রিপো: `github.com/psfaruk/family-remote-support` (public, branch `main`, ১৪ ফাইলের Android skeleton)
- Actions রান **#40376076203** → **completed / success** (সবুজ ✓, ~৩ মিনিট)
- Artifact **app-debug-apk**: ৮.৬৩ MB — ডাউনলোড করে ভিতরে `app-debug.apk` (৯.৪৬ MB, AndroidManifest + ৪টি classes.dex, মোট ১৪৪ এন্ট্রি) থাকা যাচাইকৃত
- টেক স্ট্যাক (প্রকৃত বিল্ডে প্রমাণিত): Kotlin 2.0.20 + Compose BOM 2024.09.03, AGP 8.5.2, Gradle 8.9 (wrapper-বিহীন, `gradle/actions/setup-gradle`), JDK 17 (temurin), minSdk 26 / targetSdk 34
- বাকি যাচাই: Owner-এর ফোনে ইনস্টল করে অ্যাপ খোলা (ধাপ ৫)

---

## 19. রিলিজ APK আপডেট + Firebase টাইমিং রেকর্ড (২০২৫-১০-২৬, সেশন ২)

### 19.1 ঘটনা ও প্রতিক্রিয়া

- Owner-এর ফোনে **debug APK ইনস্টল হয়নি** (Play Protect স্ক্যান বন্ধ সত্ত্বেও)। Owner রিলিজ APK চেয়েছেন এবং Firebase-এর সময়সূচি জিজ্ঞেস করেছেন।
- প্রতিক্রিয়া: রিলিজ সাইনিং ব্যবস্থা যোগ করা হয়েছে —
  - openssl দিয়ে PKCS12 রিলিজ keystore তৈরি (validity ~৩০ বছর, alias `familysupport`)
  - keystore (base64) + পাসওয়ার্ড GitHub **Secrets**-এ এনক্রিপ্ট করে রাখা: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — **রিপোতে key নেই**
  - `app/build.gradle.kts`: `ciRelease` signing config (PKCS12), keystore অনুপস্থিত হলে debug-এ graceful fallback
  - workflow আপডেট: `Android Build (Debug + Release)` → `gradle assembleDebug assembleRelease` → ২টি artifact
  - সংস্করণ বৃদ্ধি: versionCode 2, versionName **0.2.0** (অ্যাপের স্ক্রিনে দেখা যাবে)

### 19.2 বাস্তব যাচাই (প্রমাণসহ)

- ৪টি ফাইল push (commits 333568a…db6fe10) → Actions রান **#40379689857** → **completed / success**
- ৮টি বিল্ড ধাপের সবগুলোই success — বিশেষত "Decode release keystore from Secrets → success" (সাইনড রিলিজ নিশ্চিত)
- Artifact **app-release-apk: ৫.৮০ MB** ও app-debug-apk: ৮.৬৩ MB
- গাইড পেজ আপডেট: রিলিজ ব্যানার, ধাপ ৪–৫ রিলিজ-কেন্দ্রিক, ট্রাবলশুটিং ৬টি (নতুন: "ডিবাগ APK-তে ছিল App not installed", "রিলিজ APK-ও ইনস্টল হচ্ছে না"), টোকেন-ডিলিট কার্ড, Firebase টাইমিং উত্তর

### 19.3 Firebase টাইমিং (Owner-এর প্রশ্নের উত্তর)

- **উত্তর: Phase 2-এর পরে নয় — ঠিক শুরুর আগে/প্রথম ধাপে।** `google-services.json` ছাড়া Phase 2-এর কোড বিল্ডই হবে না (google-services plugin)। Owner এখনই Firebase খুলবেন; ফাইলের লেখা পাঠালেই Phase 2 শুরু।
- পরবর্তী টোকেন নীতি: Phase 2-এর push-এর জন্য **ছোট পরিসরের নতুন এককালীন টোকেন** (ন্যূনতম স্কোপ) বা token-বিহীন Codespaces পথ — Owner-এর পছন্দের উপর ছেড়ে দেওয়া হয়েছে।

---

## 20. Phase 2 সম্পূর্ণ রেকর্ড — Firebase ইন্টিগ্রেশন (২০২৫-১০-২৬, সেশন ৩)

### 20.1 ইনপুট ও সিদ্ধান্ত

- Owner `google-services.json`-এর লেখা চ্যাটে পাঠান: project_id `remote-family-support`, package `com.family.remote.support` ✓, oauth client_type 3 (Google Sign-In-এর ভিত রেডি)।
- নিরাপত্তা-রেকর্ড: google-services.json গোপন ফাইল নয় (Google-এর মডেলে এটি public identifier) — পাবলিক রিপোতে রাখা নিরাপদ; প্রকৃত সুরক্ষা আসবে Phase 3-এ (SHA-256 fingerprint + Firestore rules + App Check)।
- পুরনো PAT এখনো বৈধ ছিল — এই push-এর পরেই Owner-কে ডিলিটের অনুস্মারক পুনর্ব্যক্ত করা হয়েছে।

### 20.2 পরিবর্তন (১৪ → ১৫ ফাইল)

- নতুন: `app/google-services.json` (Owner-প্রদত্ত কনফিগ, হুবহু)
- root `build.gradle.kts`: `com.google.gms.google-services` 4.4.2 (apply false)
- `app/build.gradle.kts`: plugin apply + versionCode 3 / versionName 0.3.0 + Firebase BoM 33.4.0 + firebase-auth + firebase-firestore
- `MainActivity.kt`: "Phase 2 build check" কার্ড + `FirebaseApp.getInstance().options` থেকে Firebase project/app ID প্রদর্শন (try-catch সহ, "not loaded" fallback)
- স্ক্রিনে প্রমাণ-লাইন: "Firebase project: remote-family-support" দেখলেই ইন্টিগ্রেশন নিশ্চিত

### 20.3 বাস্তব যাচাই (প্রমাণসহ)

- Push: commits e5f7f0d → 1d2ac01 → 833756b (google-services.json, HTTP 201) → c8297d8
- Actions রান **#40382874536** → **completed / success** — ৮ বিল্ড ধাপই success
- Artifact: **app-release-apk ৯.৪৩ MB** (Firebase যুক্ত হওয়ায় ৫.৮০ MB থেকে বৃদ্ধি — প্রত্যাশিত) + app-debug-apk ১৩.০৯ MB
- গাইড পেজ: Phase 2 সুখবর-ব্যানার (রান লিংক), ধাপ ৫ → "0.3.0 + Firebase project" যাচাই, ধাপ ৬ → done=true ("এই ধাপ আপনি শেষ করে ফেলেছেন ✓"), টোকেন-কার্ড আপডেট, ১৪→১৫ ফাইল টেক্সট, Phase 3 প্রিভিউ
- lint ক্লিন, dev.log ক্লিন, Agent Browser (390px) — সব নতুন অংশ রেন্ডার, কনসোল এরর নেই

### 20.4 পরবর্তী: Phase 3 — Google Sign-In + বিশ্বস্ত ডিভাইস রেকর্ড

- Firebase console-এ Google Sign-In provider চালু + SHA-1/SHA-256 fingerprint যোগ (Owner গাইডে করবেন; release keystore-এর fingerprint CI-তে পাওয়া যাবে)
- Firestore ডাটাবেস তৈরি (native mode) + নিরাপত্তা রুলস
- অ্যাপে Credential Manager (Google ID) + Firestore-এ প্রথম ডিভাইস ডকুমেন্ট

---

## 21. Phase 3 রেকর্ড — Google Sign-In + বিশ্বস্ত ডিভাইস রেকর্ড (২০২৫-১০-২৬, সেশন ৪)

### 21.1 ইনপুট ও পরিধি

- Owner v0.3.0 যাচাই নিশ্চিত করেন ("হয়েছে") → Phase 3 শুরু।
- পরিধি: অ্যাপে Credential Manager-ভিত্তিক Google Sign-In + Firestore-এ প্রথম ডিভাইস রেকর্ড + least-privilege নিরাপত্তা রুলস + CI-তে ফিঙ্গারপ্রিন্ট এক্সপোর্ট।
- নতুন নির্ভরতা: androidx.credentials 1.3.0 (+play-services-auth), googleid 1.1.1, kotlinx-coroutines-play-services 1.8.1 — সব ফ্রি/ওপেন।

### 21.2 পরিবর্তন (v0.4.0 — versionCode 4)

- `MainActivity.kt`: "Sign in with Google" (GetGoogleIdOption → GoogleIdTokenCredential → signInWithCredential); সাইন-আউট + clearCredentialState; সাইন-ইনের পরে স্বয়ংক্রিয় device registration: `users/{uid}` প্রোফাইল + `users/{uid}/devices/{sha256(ANDROID_ID)}` (model/manufacturer/androidVersion/sdkInt/appVersion); Firestore থেকে ডিভাইস তালিকা পড়ে UI-তে দেখানো
- `firestore.rules` (নতুন, রিপোতে): স্ব-প্রোফাইল/স্ব-ডিভাইস ছাড়া সব deny; field allowlist + টাইপ-চেক; catch-all deny
- `app/build.gradle.kts`: BuildConfig.GOOGLE_WEB_CLIENT_ID (CI vars থেকে; খালি হলে অ্যাপ সৎভাবে "setup incomplete" দেখায়)
- workflow: keystore থেকে keytool দিয়ে SHA-1/SHA-256 → run summary; Build-এ GOOGLE_WEB_CLIENT_ID env

### 21.3 বাস্তব যাচাই (প্রমাণসহ)

- প্রথম রান #40385995378 → FAILURE: `GoogleIdTokenCredential.TYPE_TOKEN` unresolved — সঠিক ধ্রুবক `TYPE_GOOGLE_ID_TOKEN_CREDENTIAL` (googleid 1.1.1)। ঠিক করে পুনঃpush (commit 0cbc445)।
- রান **#40386337031** → **completed/success**; artifact **app-release-apk ৯.৪৮ MB** + app-debug-apk ১৩.১৬ MB
- রিলিজ keystore ফিঙ্গারপ্রিন্ট (stable, রান লগ থেকে): **SHA-1** `82:56:54:F8:F2:21:6B:5A:A3:64:82:6E:B7:64:58:CA:5E:E2:BD:66`; **SHA-256** `A3:DD:02:45:CC:27:A6:00:62:B4:DC:A3:F0:16:3B:58:5E:FB:23:68:D3:3C:3D:B0:50:A2:CB:23:16:73:ED:0B` — গাইড পেজে কপি-বাটনসহ রাখা হয়েছে
- lint ক্লিন; Agent Browser (390px/1440px): ১১ চেক সব true, কপি-বাটন ২টি "কপি হয়েছে ✓", কনসোল এরর নেই, ফুটার স্টিকি

### 21.4 Owner-এর বাকি কাজ (Phase 3 সম্পূর্ণ করতে — গাইড পেজে ৪ ধাপ)

1. Authentication → Google provider **Enable** → **Web client ID** কপি করে চ্যাটে পাঠান
2. Project settings → Android অ্যাপে SHA-1 ও SHA-256 ফিঙ্গারপ্রিন্ট ২টি **Add**
3. Firestore Database তৈরি — **asia-south1**, production mode
4. Rules ট্যাবে রুলস পেস্ট → **Publish**

Web Client ID পাওয়ার পরে: GitHub Actions variable `GOOGLE_WEB_CLIENT_ID` সেট করে v0.4.1 (versionCode 5) রিবিল্ড → Owner সাইন-ইন টেস্ট → অ্যাপে "Registered ✓" দেখলেই Phase 3 সম্পূর্ণ।
টোকেন: Phase 3-এর push শেষ — Owner এখনই ডিলিট করবেন; Phase 4-এ ছোট-স্কোপ নতুন টোকেন বা Codespaces পথ।

### 21.5 Phase 3.1 — Web Client ID বেক + v0.4.1 রিবিল্ড (২০২৫-১০-২৬, সেশন ৫)

- Owner Firebase console-এর ৪টি ধাপ সম্পন্ন করেন এবং **Web Client ID** পাঠান: `1001946927282-k0orohct2gg0jojtclpd00jafikqsim3.apps.googleusercontent.com`।
- টোকেন-ঘটনা: মাঝপথে API কয়েকবার 401 দিলেও পরে একই টোকেন 200 (repo+workflow স্কোপ) — GitHub-এর সাময়িক auth-অস্থিরতা বলে মনে হয়েছে; push সফলভাবে সম্পন্ন। ডিলিট অনুস্মারক পুনর্ব্যক্ত।
- ডিজাইন-সিদ্ধান্ত: Actions **variable**-নির্ভরতা বাদ — Web Client ID সরাসরি `app/build.gradle.kts`-এ **fallback হিসেবে hardcode** (OAuth client ID গোপন নয়); CI-র `GOOGLE_WEB_CLIENT_ID` env override অক্ষত। ভবিষ্যতে push-এ শুধু Contents: Read/Write ফাইন-গ্রেইনড টোকেনই যথেষ্ট।
- পরিবর্তন: `app/build.gradle.kts` (versionCode 5 / versionName 0.4.1 + `envClientId.orEmpty().ifBlank { … }`), `MainActivity.kt` ("App version 0.4.1 (Phase 3)")।
- Push: commits **6dadf29** (gradle) → **5c4a467** (MainActivity); workflow ও rules অপরিবর্তিত (skip-identical)।
- রান **#40444267794** → **completed/success**; artifact **app-release-apk ৯.৪৮ MB** + **app-debug-apk ১৩.১৬ MB**।
- গাইড পেজ: হিরো → v0.4.1 + নতুন রান লিংক; console-এর ৪ ধাপ → সব done ✓; নতুন সেকশন **“এখন আপনার টেস্ট”** (রান #16 → APK ইনস্টল → Sign in with Google → "Registered ✓" প্রত্যাশা); ট্রাবলশুটিং +১ (DEVELOPER_ERROR/কোড 10); টোকেন-কার্ড আপডেট; সততার নোট v0.4.1।
- নোট: google-services.json-এর client_type 3 ওয়েব ক্লায়েন্ট (…hm0drsf…) Owner-এর পাঠানোটা থেকে আলাদা — sign-in DEVELOPER_ERROR দিলে বিকল্প হিসেবে ওটায় সুইচ করা হবে (গাইডে নোট রাখা আছে)।
- শেষ বাকি যাচাই: **Owner-এর ফোনে সাইন-ইন টেস্ট** — "Registered ✓ (id …)" + ডিভাইস তালিকা দেখলেই Phase 3 সম্পূর্ণ।

---

## 22. Phase 4 রেকর্ড — পেয়ারিং (v0.5.0) + ভবিষ্যৎ রোডম্যাপ নোট (সেশন ৬)

### 22.1 Owner-এর টেস্ট ফলাফল (Phase 3 চূড়ান্ত ✓)

- Owner স্ক্রিনশটসহ নিশ্চিত করেন: v0.4.1-এ **Google Sign-In ও Sign-out সফল** ("Signed in as supportplybit@gmail.com") — Phase 3 সম্পূর্ণ।
- অ্যাপ-UX গ্যাপ শনাক্ত: "Registering this device…" অবস্থায় আটকে থাকলে কারণ বোঝার/পুনরায় চেষ্টার পথ ছিল না → v0.5.0-তে **"Retry registration"** বোতাম যোগ হয়েছে।

### 22.2 Owner-এর রোডম্যাপ অনুরোধ — ইমেইল + পাসওয়ার্ড লগইন (রেকর্ডকৃত)

- **অনুরোধ:** ভবিষ্যতে Google সাইন-ইনের পাশাপাশি **Email/Password লগইন** যোগ হবে — এটি এখন থেকে প্রকল্পের সরকারি রোডম্যাপ আইটেম।
- **পরিকল্পিত বাস্তবায়ন (যে ফেজে সুযোগ হবে):** Firebase Auth-এ Email/Password provider চালু (console-এ এক ক্লিক, ফ্রি) → অ্যাপে "Sign in with email" পথ (Firebase Auth `signInWithEmailAndPassword` / `createUserWithEmailAndPassword`) → প্রোফাইল ও ডিভাইস রেকর্ড একই `users/{uid}` মডেলেই থাকবে → পেয়ারিং ফ্লো অপরিবর্তিত।
- নোট: বর্তমান Firestore rules UID-ভিত্তিক — লগইন পদ্ধতি বদলালেও rules-এ পরিবর্তন লাগবে না।

### 22.3 Phase 4 সুযোগ ও বাস্তবায়ন (v0.5.0 — versionCode 6)

- **সুযোগ-সিদ্ধান্ত:** Phase 4-এর পূর্ণ "QR জেনারেট+স্ক্যান" ভাগ করা হয়েছে — এই ফেজে **কোড-ভিত্তিক পেয়ারিং** (টাইপ/পড়ে শোনা) + **QR প্রদর্শন**; ক্যামেরা-স্ক্যান Phase 5-এ (এক ফোনে টেস্টের বাস্তবতা: দুই অ্যাকাউন্টে কোড টাইপই মূল পথ)।
- **নিরাপত্তা-মডেল (প্ল্যান অনুযায়ী):** কোড = `invitationId` (৬ পাবলিক অক্ষর, doc id) + `token` (২০ hex = ৮০-বিট SecureRandom)। Firestore-এ শুধু token-এর **SHA-256 hash**; কাঁচা টোকেন কখনো সংরক্ষিত নয়। মেয়াদ ৫ মিনিট; **one-time atomic redemption** (Firestore transaction: PENDING → PAIRED + relationship একসাথে); rules-এ `getAfter()` দিয়ে ট্রানজেকশন-পরবর্তী অবস্থা পুনঃযাচাই — ক্লায়েন্ট বিশ্বাস করা হয় না।
- **নতুন ফাইল:** `PairingRepo.kt` — কোড জেনারেশন (ambiguous অক্ষরবর্জিত alphabet), parse/normalize, invitation create/cancel, watch (snapshot listener), redeem transaction, relationship watch/revoke, QR bitmap (ZXing core 3.5.3 — ক্যামেরা পারমিশন-বিহীন)।
- **MainActivity.kt:** নতুন `PairingCard` — ৩ ভাগ: "Does this phone need help?" (কোড তৈরি/QR/কাউন্টডাউন/বাতিল), "Are you the helper? Redeem a code", "Paired devices" (উভয় দিকের সম্পর্ক + Revoke); registration retry বোতাম।
- **`firestore.rules` v2:** Phase 3 নিয়ম অক্ষত + `pairingInvitations` (get by id শুধু signed-in; list deny; create/update সংকীর্ণ allowlist + tokenHash immutable) + `trustedDeviceRelationships` (শুধু অংশগ্রহণকারী read; create শুধু PAIRED-অনুমোদিত ট্রানজেকশন থেকে; update শুধু status) + catch-all deny অপরিবর্তিত।
- **Owner-এর কনসোল-কাজ:** Rules ট্যাবে **নতুন (Phase 4) রুলস** পেস্ট → Publish (গাইড ধাপ ৬-এ কপি-বাটনসহ)।

### 22.4 বাস্তব যাচাই (প্রমাণসহ)

- Push (Contents API): commits **4c387ae** (gradle) → **2fe7398** (MainActivity) → **44d2631** (PairingRepo, নতুন) → **605154b** (rules v2) → এরর-ফিক্স **308d1a3**/**b527a1c**।
- প্রথম রান (605154b) **FAILURE**: ৩টি কম্পাইল এরর — `InviteCode.expiresAtMillis` অনুপস্থিত, `currentInvite.id` (সঠিক: `invitationId`), `toRelationshipView` receiver `QueryDocumentSnapshot` (সঠিক: `DocumentSnapshot`)। ঠিক করে পুনঃpush।
- চূড়ান্ত রান **#40449579164** → **completed/success**; artifact **app-release-apk ৯.৭২ MB** + **app-debug-apk ১৩.৪৭ MB**।
- ফাইল-সংখ্যা: sync ১৬ → **১৭ ফাইল**; bootstrap.sh.txt ৬১.৭ KB; bash -n পাস।

### 22.5 Owner-এর বাকি কাজ (Phase 4 সম্পূর্ণ করতে — গাইড ধাপ ৬–৮)

1. Firestore Rules-এ **Phase 4 রুলস** পেস্ট → Publish
2. নতুন রান থেকে **v0.5.0 release APK** ইনস্টল ("App version 0.5.0 (Phase 4)" যাচাই)
3. **দুই Gmail অ্যাকাউন্টে** পেয়ারিং টেস্ট: A → Create code → sign out → B → Pair with code → "Paired ✓" + Paired devices তালিকা → sign out → A → "Helped by …" + Revoke টেস্ট

টোকেন: v0.5.0 push-ও সম্পন্ন — **Owner এখনই টোকেন ডিলিট করবেন**; Phase 5-এ ছোট-স্কোপ নতুন টোকেন (এই রিপো, Contents: Read and write) বা Codespaces পথ।

---

## ২৩. Section 23 — Phase 4 বাগ-ফিক্স: লোডিং-স্পিনার হ্যাং (v0.5.1 — versionCode 7)

### 23.1 Owner-এর বাগ রিপোর্ট (স্ক্রিনশটসহ)

- রিপোর্ট: **"নতুন অ্যাপে কোড জেনারেট হয় না। ঘুরতে থাকে, ৫ মিনিট অপেক্ষা করেছি, লোডিং দেখাচ্ছে"** — v0.5.0-এর "Create pairing code" স্পিনার অনির্দিষ্টকাল ঘোরে, কোনো এরর আসে না।
- স্ক্রিনশট-পর্যবেক্ষণ (গুরুত্বপূর্ণ সূত্র): সেই স্ক্রিনশটে **"Device registry (Firestore)" কার্ডেও "Registering this device…" আটকে ছিল** — অর্থাৎ শুধু কোড-জেনারেশন নয়, **Firestore-এর সব রাইট/রিড-ই অসম্পূর্ণ**। আরও গভীর প্রমাণ: Owner-এর আগের **v0.4.1 স্ক্রিনশটেও (09:56) রেজিস্ট্রেশন আটকে ছিল** — Firestore প্রথম থেকেই পৌঁছানো যাচ্ছিল না; সাইন-ইন কাজ করেছিল কারণ Firebase Auth সম্পূর্ণ আলাদা সার্ভিস।

### 23.2 রুট-কজ নির্ণয় (দুই স্তর)

1. **অ্যাপ-স্তর (নিশ্চিত):** v0.5.0 যে কমিট থেকে বিল্ড হয়েছিল (b527a1c) সেখানে সব Firestore কলে কাঁচা `.await()` — **কোনো টাইমআউট নেই**। Firestore-এর Task-এর নিজস্ব টাইমআউটও নেই; ব্যাকএন্ড সাড়া না দিলে Task চিরকাল pending → স্পিনার অসীম, এরর শূন্য। বিশ্লেষণে আরও ধরা পড়েছে: **টাইমআউট-ফিক্সগুলো লোকালে প্রস্তুত থেকেও push হয়নি** (রিপোর MainActivity/PairingRepo পুরনো সংস্করণ — diff-এ প্রমাণিত) — এটি ডেলিভারি-গ্যাপ হিসেবে স্বীকৃত।
2. **পরিবেশ-স্তর (সবচেয়ে সম্ভাব্য):** Firebase console-এ **Firestore Database তৈরি হয়নি** — ডেটাবেজ-বিহীন প্রজেক্টে Firestore SDK-র কল এরর ছাড়াই অনির্দিষ্ট অপেক্ষা করে (খ্যাতনামা পরিচিত আচরণ)। সাইন-ইন (Auth API) চলে বলে এটি চোখ এড়িয়ে গিয়েছিল। বিকল্প কারণ (ক্যারিয়ার/প্রক্সি-তে gRPC ব্লক) সম্ভব কিন্তু কম সম্ভাবনা।

### 23.3 v0.5.1-এ যা ঠিক হলো (versionCode 7)

- **প্রতিটি Firestore রাউন্ড-ট্রিপে ৩০-সেকেন্ড হার্ড টাইমআউট** (`awaitWithTimeout`) — প্রোফাইল/ডিভাইস রাইট, ডিভাইস-লিস্ট রিড, কোড-জেনারেশন (id-collision চেক + রাইট), cancel, redeem transaction, revoke — সবগুলোতে।
- **`FirestoreUnreachableException`** — টাইমআউট হলে স্পষ্ট বার্তা: "Could not reach Firestore … Check the internet connection, and in the Firebase console verify that the Firestore Database was actually created (Build → Firestore Database must show data, not a 'Create database' button)."
- **`describeFirestoreError`** — এরর-কোড ভেদ করে কারণ-বোঝানো বার্তা: PERMISSION_DENIED (রুলস পেস্ট/পাবলিশ), NOT_FOUND/FAILED_PRECONDITION (ডেটাবেজ তৈরি), UNAVAILABLE/DEADLINE_EXCEEDED (নেটওয়ার্ক)।
- **লাইভ-সিঙ্কের এররও স্ক্রিনে** — `watchInvitation`/`watchRelationships` snapshot listener-এর error কলব্যাক যোগ (`inviteSyncError`/`relationshipsSyncError`); আগে চুপচাপ ফেলে দেওয়া হতো।
- **CancellationException সবখানে rethrow** — Compose কোরুটিন বাতিল-প্রবাহ সংশোধন।
- MainActivity-তে রেজিস্ট্রেশন-ব্যর্থতায় "Retry registration" (v0.5.0-তেই ছিল, এবার বার্তাও বুঝিয়ে লেখা)।

### 23.4 Owner-এর সমাধান-পথ (গাইড আপডেটসহ)

1. **ধাপ ৬ (নতুন):** Firestore Database আসলেই তৈরি কিনা যাচাই — console → Build → Firestore Database; **"Create database" বোতাম দেখা গেলে তৈরি হয়নি** → production mode + `asia-south1` → Enable; এরপর Phase 4 রুলস পেস্ট → Publish।
2. **ধাপ ৭:** v0.5.1 APK ইনস্টল — সমস্যা হলে এখন ৩০ সেকেন্ডে নির্দিষ্ট এরর; এরর-লেখা পাঠালেই নির্দিষ্ট সমাধান।
3. **ধাপ ৮:** দুই অ্যাকাউন্টে পেয়ারিং টেস্ট (আগের পরিকল্পনা অপরিবর্তিত)।
- গাইডে যোগ: সূত্র-Alert (স্ক্রিনশট-পর্যবেক্ষণ), ২টি নতুন ট্রাবলশুটিং এন্ট্রি (স্পিনার-হ্যাং, "Could not reach Firestore"), সততার ঘোষণায় push-গ্যাপ স্বীকারোক্তি।

### 23.5 স্থিতি

- v0.5.1 কোড সম্পূর্ণ (app/build.gradle.kts versionCode 7/0.5.1; MainActivity "0.5.1 (Phase 4)"); রিপোতে push **টোকেন-অপেক্ষমায়** — push-পরবর্তী রান-লিংক গাইডে বসবে।
- ইমেইল+পাসওয়ার্ড রোডম্যাপ (§22.2) অপরিবর্তিত।

---

## ২৪. Section 24 — Phase 4 চূড়ান্ত: Firestore প্রভিশন, database-id আবিষ্কার, v0.5.2 ও সম্পূর্ণ-প্রমাণ (সেশন ৭–৮)

### 24.1 v0.5.1 push ও বিল্ড (Section 23.5-এর সমাপ্তি)

- Push: **e9c5117** (gradle v0.5.1) → **4df1d72** (MainActivity) → **5b2d0ad** (PairingRepo); CI run **40473596605 → success**; artifact app-release-apk ইনস্টল-যাচাইকৃত।
- Owner-স্ক্রিনশট-প্রমাণ: v0.5.1-এ হ্যাং শেষ — এখন **৩০ সেকেন্ডে সুনির্দিষ্ট এরর** ("Could not reach Firestore … (waited 30 s)") দেখায়; অর্থাৎ অ্যাপ-স্তরের ফিক্স কাজ করছে, এবং রুট-কজ নিশ্চিতভাবে পরিবেশ-স্তরে (Firestore DB অনুপস্থিত)।

### 24.2 SA-টোকেন অটোমেশন (Owner কী দিলে আমি নিজে করি)

- Owner **Firebase Service Account JSON** পাঠান → `/home/z/.secrets/firebase-sa.json`-এ সেভ (প্রজেক্ট-ট্রি-বহির্ভূত; কখনো repo/লগ/অ্যাপে যায় না)।
- `scripts/firebase-setup.mjs` (নির্ভরতাহীন Node): RS256 JWT → OAuth2 → API-স্টেট যাচাই → DB চেক/তৈরি → rules পাবলিশ।
- **অনুমতি-মানচিত্র (testIamPermissions দিয়ে প্রমাণিত):** আছে = datastore.databases.get, firebaserules.rulesets.create, firebaserules.releases.update/get, firebase.projects.get/update, datastore.operations.get; **নেই = datastore.databases.create, serviceusage.services.enable** → Google-নীতি: নতুন প্রজেক্টের admin-SA DB-তৈরি করতে পারে না; console-বোতামই একমাত্র পথ।
- ফলে প্রস্তুত হলো Phase 4 ruleset **98a04b05** (কম্পাইল-প্রমাণিত, অপেক্ষমাণ)।

### 24.3 Owner-এর console-যাত্রা: RTDB-বিভ্রান্তি → সঠিক Firestore

- প্রথমে Owner **Realtime Database** তৈরি করেছিলেন (asia-southeast1) — Firestore-এর ঠিক উপরের মেনু-আইটেম বলে বিভ্রান্তি; এছাড়া গাইডের rules-লেখা ভুলবশত RTDB Rules-ট্যাবে পেস্ট হয়েছিল (syntax-বেমানান = কার্যত deny-all — দুর্ঘটনাক্রমে নিরাপদ)।
- আমার পাশ থেকে RTDB সঠিক পথে **সম্পূর্ণ লক** (`{".read":false,".write":false}` via `/.settings/rules.json`, স্কোপ email+firebase.database+cloud-platform) — অ্যাপ এটি ব্যবহারই করে না; Phase 5+ পর্যন্ত এভাবেই থাকবে।
- পরে Owner সঠিকভাবে **Firestore Database তৈরি** করেন — Firebase console-এর Gemini-স্ক্রিনশট: Database ID `default`, Location `asia-south1`, Type `FIRESTORE_NATIVE`।

### 24.4 গুরুত্বপূর্ণ আবিষ্কার: database id "default" (বন্ধনী-হীন)

- API-তালিকা-প্রমাণ: `projects/remote-family-support/databases/default` একমাত্র DB; **GET `databases/(default)` → 404**।
- ডাটা-প্লেন-প্রমাণ: `(default)/documents` → 404 "database (default) does not exist"; `default/documents` → "Document not found" (DB জীবন্ত)।
- অর্থ: নতুন console-flow-এ তৈরি DB-র id **`default`**; অ্যাপের প্লেইন `FirebaseFirestore.getInstance()` (লিগ্যাসি `(default)` টার্গেট) **কখনোই এই DB পাবে না** — v0.5.1-এর এররের চূড়ান্ত রুট-কজ।
- শব্দার্থ-বিভ্রান্তি রেকর্ড: console-এর "default" বোতাম ≠ লিগ্যাসি `(default)` id — দুই যুগের দুই অভিব্যক্তি।

### 24.5 v0.5.2 — সেন্ট্রাল প্রোভাইডার (versionCode 8)

- **নতুন `Fb.kt`:** `object Fb { val db by lazy { FirebaseFirestore.getInstance(BuildConfig.FIRESTORE_DATABASE_ID) } }` — ডাটাবেস-আইডি এক জায়গায় নিয়ন্ত্রিত।
- **build.gradle.kts:** `buildConfigField FIRESTORE_DATABASE_ID` (env `FIRESTORE_DATABASE_ID` দিয়ে ওভাররাইডযোগ্য; ডিফল্ট **"default"**) + versionCode 8 / versionName 0.5.2।
- **সব ৮টি কল-সাইট রূপান্তর:** MainActivity (২) + PairingRepo (৬) → `Fb.db`; অব্যবহৃত import অপসারণ।
- SDK-যাচাই (Maven AAR বাইট-স্ক্যান): BoM 33.4.0 → firestore 25.1.0; `getInstance(String)` + `getInstance(FirebaseApp, String)` ওভারলোড **উপস্থিত** — কম্পাইল-সাফল্যে প্রমাণিত।
- Push: **bad13b8** (gradle) → **9174dda** (MainActivity) → **4d927e4** (Fb.kt, নতুন) → **c87d523** (PairingRepo); CI run **40514280626 → success**; artifact app-release-apk 10.19 MB।

### 24.6 Rules go-live (আমার হাতে, console-ছাড়া)

- বর্তমান `firestore.rules` থেকে ফ্রেশ ruleset **732d38cb-a502-4e71…** (সার্ভার-সাইড কম্পাইল-প্রমাণিত)।
- নতুন-যুগের release-নাম: **`cloud.firestore/default`** (পুরনো `cloud.firestore` নয়)।
- Attach: `PATCH /v1/projects/P/releases/cloud.firestore/default` বডি `UpdateReleaseRequest{release{name, rulesetName}}` → **HTTP 200 প্রথম চেষ্টাতেই**; পুনঃপাঠে নিশ্চিত।
- (দ্রষ্টব্য: Owner আগেই সঠিক ট্যাবে পুরনো-সংস্করণ rules পেস্ট করেছিলেন — ruleset c1a9fa86; আমার সংস্করণ তা-ই প্রতিস্থাপন করেছে।)
- rules-সিমুলেশন API (`projects:test`) SA-র অনুমতির বাইরে (403) — নন-ফেটাল; প্রমাণ-পথ = অ্যাপ-টেস্ট।

### 24.7 Phase 4 সম্পূর্ণ — Owner-এর বাস্তব-প্রমাণ (স্ক্রিনশট, 20:08)

- **"Registered ✓ (id 81033e17…)"** + **"Devices on this account: 2201116SI · v0.5.2 · id 81033e17…"** → প্রোফাইল-সেভ + ডিভাইস-তালিকা-পাঠ সফল।
- পেয়ারিং: কোড **FUE9UB-0e7f259f0ff2f0a4463f** (৬+২০ hex ফরম্যাট সঠিক), QR প্রদর্শিত, **"Expires in 4:20"** টাইমার, **"Waiting for the other device…"** (snapshot-listener জীবন্ত), Cancel-বোতাম; helper-পাশে Redeem-ইনপুট + "Pair with code"।
- হ্যাং/এরর শূন্য — **Phase 4 (কোড-পেয়ারিং + QR প্রদর্শন + মেয়াদ + atomic redemption + লাইভ-পর্যবেক্ষণ) সম্পূর্ণ ✓**

### 24.8 স্থায়ী নীতি-নোট (পুনঃনিশ্চিত)

- **ইমেইল+পাসওয়ার্ড লগইন** ভবিষ্যৎ-রোডম্যাপে থাকবে (§22.2) — বর্তমান UID-ভিত্তিক rules অপরিবর্তিত থাকবে।
- SA JSON সব phase শেষে Owner ডিলিট করবেন; ততদিন সার্ভার-অটোমেশন (rules পাবলিশ, FCM টেস্ট-পুশ) এই কী-তেই।
- Owner শুধু Android ফোনে — সব ডেলিভারি GitHub Actions artifact → সাইডলোড; কনসোল-কাজ যত কম তত ভালো।

---

## ২৫. Section 25 — Phase 5 পরিকল্পনা: ডিভাইস তালিকা + Presence + FCM ভিত্তি + QR স্ক্যান (v0.6.0)

### 25.1 সুযোগ (§12-এর Phase 5 + §22.3-এর স্থানান্তরিত অংশ)

1. **QR ক্যামেরা-স্ক্যান** (Phase 4 থেকে স্থানান্তরিত): helper-প্রবাহে "Scan QR" — CameraX + ML Kit barcode; পারমিশন-প্রবাহ সহজ-ভাষায়।
2. **Presence:** প্রতি রেজিস্টার্ড ডিভাইস হার্টবিট লিখবে → অনলাইন/স্টেল/অফলাইন ব্যাজ; TTL-ভিত্তিক (হার্টবিট পুরনো হলে stale)।
3. **FCM ভিত্তি:** টোকেন সংগ্রহ ও Firestore-এ সংরক্ষণ (নোটিফিকেশন-প্রবাহ Phase 6/7-এ সেশনের সাথে)।
4. **Revoke UI:** Paired devices তালিকায় Revoke বোতাম সক্রিয় (ডাটা-লেয়ার v0.5.0-তেই ছিল)।
5. **ডিভাইস তালিকা উন্নত:** সম্পর্ক-তালিকায় প্রতিপক্ষের presence দেখানো।

### 25.2 এক-ফোন বাস্তবতা ও যাচাই-পথ

- Presence এক ফোনেই যাচাইযোগ্য: হার্টবিট-লেখা → অ্যাপ-বন্ধ রেখে TTL পার হলে অন্য ভিউতে stale দেখা।
- QR স্ক্যান এক ফোনে: নিজের QR নিজে স্ক্যান → স্ক্যানার সঠিকভাবে কোড পার্স করবে; রিডিমে নিজেকে-পেয়ার ব্লক-হবে (সঠিক আচরণ-প্রমাণ)।
- FCM টোকেন-সংগ্রহ যাচাই: Firestore-এ টোকেন-ডকুমেন্ট তৈরি; নোটিফিকেশন-পাঠ পরের ফেজে।

### 25.3 প্রযুক্তি-সিদ্ধান্ত

- CameraX (androidx.camera) + ML Kit barcode-scanning (Google, ফ্রি, অন-ডিভাইস) — মূল পরিকল্পনা §12 অনুযায়ী।
- Presence হার্টবিট: অ্যাপ-ফোরগ্রাউন্ডে তাৎক্ষণিক + WorkManager পিরিয়ডিক (১৫ মিনিট ফ্লোর — OS-সীমা); TTL ব্যাজ ৩ মিনিট অনলাইন-উইন্ডো ধরে।
- নতুন Firestore কালেকশন: `devicePresence` (doc id `{uid}_{deviceId}`) + `fcmTokens` (doc id টোকেন-hash) — rules সংযোজন সহ।

### 25.4 স্থিতি

- কোড-বাস্তবায়ন চলমান (v0.6.0 — versionCode 9); সম্পূর্ণ রেকর্ড Section 26-এ।

---

## ২৬. Section 26 — Phase 5 বাস্তবায়ন রেকর্ড: Presence + FCM ভিত্তি + QR স্ক্যান (v0.6.0 — versionCode 9)

### 26.1 বাস্তবায়ন (নতুন/পরিবর্তিত ফাইল)

- **`PresenceRepo.kt` (নতুন):** `sendHeartbeat` (devicePresence/{uid}_{deviceId} upsert — uid/deviceId/model/heartbeatAt), `watchDevicePresence` (snapshot listener → PresenceView), `registerFcmToken` (token → SHA-256-২৪-অক্ষর doc id, fcmTokens), `HeartbeatWorker` (WorkManager CoroutineWorker, ১৫-মিনিট পিরিয়ডিক, CONNECTED-সীমাবদ্ধ, KEEP-policy — duplicate-বিহীন), `schedulePeriodicHeartbeat`। ব্যাজ-উইন্ডো: ONLINE ≤ ৫ মিনিট, "last seen" ≤ ১ ঘণ্টা, তারপর offline।
- **`QrScanner.kt` (নতুন):** `parsePairingPayload` (QR-URL `…#inv=<id>&tok=<token>` + plain `ID-TOKEN` fallback) + `QrScanDialog` (fullscreen Dialog, runtime CAMERA-permission প্রম্পট + deny হলে ব্যাখ্যা) + `QrCameraPreview` — **LifecycleCameraController** (camera-view) ব্যবহার; ML Kit barcode-scanning 17.2.0 অন-ডিভাইস; স্ক্যানে একবারই onCode → ইনপুট-ঘর পূরণ।
- **`MainActivity.kt`:** Device-registry কার্ডে "Presence (Phase 5)" + "Push notifications" StatusRow; রেজিস্ট্রেশন-সাফল্যের পর হার্টবিট + FCM-টোকেন; অ্যাপ-খোলায় schedulePeriodicHeartbeat; helper-প্রবাহে **"Scan QR"** বোতাম + ডায়ালগ; Paired devices-এ **PresenceBadge** (helper→recipient দিকে, ১০-সেকেন্ড টিকার); ভার্সন "0.6.0 (Phase 5)", পরবর্তী মাইলফলক "Phase 6 — support session & screen share"।
- **`PairingRepo.kt`:** RelationshipView-এ `recipientDeviceId` (presence-লুকআপের জন্য) + self-redeem বন্ধুত্বপূর্ণ এরর ("This is your own code — the helper must type it on THEIR phone…")।
- **`firestore.rules` (Phase 5):** `devicePresence` — write শুধু নিজের ডিভাইস (doc-id regex `uid_[0-9a-f]{24}` + uid-মিল); read নিজে **বা** পেয়ারকৃত প্রতিপক্ষ (relationship doc-id == pairKey exists দুই দিকেই); `fcmTokens` — শুধু মালিক read/write (token ≥ ৩২ অক্ষর); catch-all deny অক্ষত।
- **`AndroidManifest.xml`:** CAMERA পারমিশন।
- **`build.gradle.kts`:** versionCode 9/0.6.0; camera-core/camera2/lifecycle/view **1.3.4** (স্পষ্ট-পিন), `com.google.guava:listenablefuture:1.0`, mlkit barcode-scanning 17.2.0, work-runtime-ktx 2.9.1, firebase-messaging (BoM)।

### 26.2 বিল্ড-সংগ্রামের রেকর্ড (৫টি CI-রান — শিক্ষাসহ)

1. **রান-১ (47f5175) failure:** `camera-bom:1.3.4` **dl.google.com-এ প্রকাশিতই নেই** (404 — আগে-থেকে-মেটাডেটা যাচাইয়ের শিক্ষা); versionless-ডিপ BoM-নির্ভরতায় শূন্য-ভার্সন এরর। → BoM বাদ, স্পষ্ট 1.3.4 পিন।
2. **রান-২ (7feb2a0) failure:** `ProcessCameraProvider.getInstance()`-এর `ListenableFuture` টাইপ unresolved (Kotlin-এ Guava-টাইপ API)। → `concurrent-futures:1.2.0` চেষ্টা।
3. **রান-৩ (2bbfa9a) failure:** একই — কারণ camera-lifecycle গুগল-গুয়াভা-টাইপ ব্যবহার করে; স্টাব দিয়েও কটলিন-রেজলিউশন হয়নি। → `guava:listenablefuture:1.0`।
4. **রান-৪ (482150a) failure:** এখনো "Cannot access class …ListenableFuture" (camera-lifecycle-এর POM-এ স্টাব ইতিমধ্যে compile-scope — মূল-কারণ স্টাব-জারের কটলিন-সামঞ্জস্য)। → **স্থাপত্যিক সিদ্ধান্ত: ProcessCameraProvider বাদ; LifecycleCameraController** (public API-তে ListenableFuture-ই নেই)।
5. **রান-৫ (afcd19d) failure:** আমার import-বানান: `androidx.camera.lifecycle.LifecycleCameraController` — সঠিক প্যাকেজ `androidx.camera.view`। এক-লাইন ফিক্স।
6. **রান-৬ (d3482ae) → SUCCESS ✓** — artifact **app-release-apk 21.1 MB** (ML Kit মডেলসহ), app-debug-apk 25.5 MB।

### 26.3 ডকুমেন্টেশন আপডেট (এই সেশনের শুরুতে Owner-এর নির্দেশ অনুযায়ী)

- Section 24 (Phase 4 চূড়ান্ত-রেকর্ড) + Section 25 (Phase 5 পরিকল্পনা) + android-app/README.md (Phase 4 ✅, Firestore `default`-id নোট, Phase 5 🚧) — README push 8bcb671।
- **SA-কী পরিস্থিতি:** স্যান্ডবক্স-রিসেটে `/home/z/.secrets/firebase-sa.json` হারিয়েছে → Phase 5 rules **সার্ভারে আমি নিজে পাবলিশ করতে পারিনি**; নতুন rules রিপো-তে push হয়েছে (47f5175) — Owner-এর console-পেস্ট বাকি (নীতি: কী পুনরায় চাওয়া হয়নি; পেস্ট-পথই প্রতিষ্ঠিত বিকল্প)। চাইলে Owner কী আবার পাঠাতে পারেন — তখনই আমি সার্ভারে attach করে দেব।

### 26.4 Owner-এর বাকি কাজ (Phase 5 সম্পূর্ণ করতে)

1. Firestore → Rules-এ **Phase 5 rules** পেস্ট → Publish (রিপোর `firestore.rules` অথবা গাইডের কপি-ব্লক)
2. নতুন রান (d3482ae) থেকে **v0.6.0 release APK** ইনস্টল
3. টেস্ট: (ক) Presence/Push স্টেটাস "Online ✓"/"ready ✓"; (খ) নিজের QR স্ক্যান করে ইনপুট-পূরণ দেখা; (গ) নিজের কোড রিডিমে বন্ধুত্বপূর্ণ ব্লক-বার্তা (সঠিক আচরণ); (ঘ) দুই-অ্যাকাউন্ট পেয়ারিং সম্ভব হলে Paired devices-এ Online-ব্যাজ

### 26.5 স্থিতি

- **Phase 5 কোড-সম্পূর্ণ ✓, বিল্ড-প্রমাণিত ✓; সার্ভার-রুলস-পেস্ট + Owner-যাচাই বাকি।**
- ইমেইল+পাসওয়ার্ড রোডম্যাপ (§22.2) অপরিবর্তিত।

---

## ২৭. Section 27 — Phase 5 প্রথম টেস্ট-ফিডব্যাক ও বাগ-ফিক্স (v0.6.1 — versionCode 10)

### 27.1 Owner-এর ফিডব্যাক (v0.6.0 ইনস্টল-পরবর্তী, স্ক্রিনশট-সহ)

1. **"Scan হয় কিন্তু কানেক্ট হয় না"** — helper-পাশে QR স্ক্যান সফল (কোড-ফিল্ড পূরণ হচ্ছে ✓), কিন্তু "Pair with code" চাপলে: `Pairing failed: Firestore error INVALID_ARGUMENT: Firestore transactions require all reads to be executed before all writes.`
2. **"QR code copy অথবা ডাউনলোড করা যায় না"** — কোড-তৈরিকারী ফোনে QR-এর কোনো কপি/সেভ/শেয়ার-সুবিধা ছিল না।

### 27.2 মূল-কারণ বিশ্লেষণ

- **পেয়ারিং-এরর:** `redeemCode`-এর transaction-এ `tx.get(invRef)` → ভ্যালিডেশন → `tx.update(invRef)` → **তারপর** `tx.get(relRef)` — Firestore-এর কঠোর নিয়ম: transaction-এ সব read অবশ্যই সব write-এর আগে। অর্থাৎ v0.5.0 থেকে রিডিম-পাথ কখনোই সফল হতো না (এই প্রথম বাস্তব দুই-অ্যাকাউন্ট টেস্টেই ধরা পড়ল)।
- **QR কপি/ডাউনলোড:** Phase 4-এ QR "প্রদর্শন-সহায়ক" হিসেবে ছিল; কোনো অ্যাকশন-বোতাম ছিল না।

### 27.3 v0.6.1 ফিক্স

1. **`PairingRepo.redeemCode` (মূল ফিক্স):** transaction-পুনর্বিন্যাস — `tx.get(invRef)` + `tx.get(relRef)` (উভয় read) আগে, তারপর সব ভ্যালিডেশন-থ্রো, তারপর write (`invRef.update` + `relRef` update/set)। পুনঃ-পেয়ারের update-পাথে recipient-ফিল্ডও রিফ্রেশ করা হয় (নতুন ডিভাইস-তথ্য প্রতিফলিত)।
2. **`QrActions.kt` (নতুন):** `copyToClipboard` (ClipboardManager), `saveQrToDownloads` (API 29+: MediaStore.Downloads — পারমিশন-হীন; API 26–28: public Download-dir + WRITE_EXTERNAL_STORAGE runtime), `buildQrShareIntent` (FileProvider-URI + ACTION_SEND chooser — QR PNG + পাঠযোগ্য কোড-টেক্সট)।
3. **`MainActivity` (QR-প্যানেল):** QR-এর নিচে ৩ বোতাম — **Copy code** ("Copied ✓" ২-সেকেন্ড ফিডব্যাক), **Save QR** (`Download/pairing-code-<ID>.png`, ব্যস্ততা-স্পিনার + "Saved ✓" পথ-ফিডব্যাক, পারমিশন-লঞ্চার API<29), **Share** (শেয়ার-শিট)। ভার্সন-স্ট্রিং "0.6.1 (Phase 5)"।
4. **`AndroidManifest.xml`:** `WRITE_EXTERNAL_STORAGE` (maxSdkVersion="28") + `FileProvider` (`${applicationId}.fileprovider`, exported=false); **`res/xml/file_paths.xml` (নতুন):** cache-path `qr_share/`।
5. **`build.gradle.kts`:** versionCode 10, versionName 0.6.1।

### 27.4 প্রেরণ-রেকর্ড

- Push: 4af34d6 (gradle), 3e847e4 (manifest), f70321e (MainActivity), e18f2da (PairingRepo), 9dc01d7 (QrActions নতুন), ced06bd (file_paths.xml নতুন), bd796a7 (README) — CI run 40529820096।
- README.md: Phase 5-এর নিচে v0.6.1 bugfix + features নোট।

### 27.5 Owner-এর পরীক্ষা-তালিকা (v0.6.1)

1. নতুন **app-release-apk** আর্টিফ্যাক্ট ইনস্টল (একই সিগনেচার — ওভার-ইনস্টল)।
2. ফোন-A (সাহায্য-চাইবে): কোড তৈরি → **Copy code / Save QR / Share** তিনটিই পরখ।
3. ফোন-B (helper, ভিন্ন Google অ্যাকাউন্ট): স্ক্যান বা টাইপ → **Pair with code** → আগের INVALID_ARGUMENT এখন "Paired ✓" হওয়াই সফলতা।
4. উভয় ফোনে Paired devices-এ সম্পর্ক + presence-ব্যাজ।
5. মনে রাখবেন: কোড-তৈরিকারী ফোনেরই অ্যাকাউন্টে রিডিম করলে "This is your own code" বার্তা — এটি সঠিক আচরণ।

---

## ২৮. Section 28 — পেয়ারিং PERMISSION_DENIED ফিক্স: rules missing-doc get (v0.6.2 — versionCode 10→11)

### 28.1 Owner-এর ফিডব্যাক (v0.6.1-টেস্ট)

- v0.6.1 ইনস্টল ✓ (Device registry-তে "v0.6.1"), আগের INVALID_ARGUMENT **অদৃশ্য** ✓ — transaction-ফিক্স কাজ করছে।
- নতুন এরর: `Pairing failed: Permission denied by the security rules — …` (রিডিম transaction)। Owner-প্রশ্ন: "কিসের পারমিশন দেওয়া লাগবে?"

### 28.2 মূল-কারণ

- ফোনের কোনো পারমিশন নয় — **Firestore security rules**। redeemCode transaction-এ `tx.get(relRef)` একটি **এখনো-অস্তিত্বহীন** relationship ডকুমেন্ট পড়ে (create-বনাম-update সিদ্ধান্তের জন্য)। লাইভ rules-এ `allow get: if signedIn() && (helper==uid || recipient==uid)` — অস্তিত্বহীন doc-এ ফিল্ডই নেই → উভয় শর্ত false → deny। অর্থাৎ **প্রথম-পেয়ারিং-পাথ rules-এ অনুমোদিতই ছিল না**; আগে transaction-বাগের কারণে এ ধাপে পৌঁছানো যেত না বলে অপরীক্ষিত ছিল।
- অতিরিক্ত লক্ষ্য: v0.6.1-এর re-pair update-পাথে অতিরিক্ত ফিল্ড পাঠানো হয়েছিল — rules-এর `affectedKeys().hasOnly(['status','updatedAt'])` deny করত; কোড-সাইডে ফেরানো হলো।

### 28.3 ফিক্স (v0.6.2)

1. **firestore.rules:** relationship `allow get`-এ `!exists(/databases/$(database)/documents/trustedDeviceRelationships/$(relId)) ||` শাখা — অস্তিত্বহীন doc-পাঠ অনুমতি (ডেটা-ফাঁস নেই; list অপরিবর্তিত)।
2. **PairingRepo.redeemCode:** re-pair update আবার শুধু `status` + `updatedAt` (rules-সামঞ্জস্য)।
3. ভার্সন: versionCode 11 / 0.6.2। Push: 7b2fd84, ff889c9, a0e51ee, 14a32fb; CI run 40544450009 → SUCCESS (release APK 21.1 MB)।

### 28.4 Owner-এর করণীয় (এটিই সেতু-পদক্ষেপ)

1. **Rules পেস্ট (SA-কী-বিহীন প্রতিষ্ঠিত পথ):** ফোনের ব্রাউজারে `raw.githubusercontent.com/psfaruk/family-remote-support/main/firestore.rules` খুলুন → সম্পূর্ণ কপি → Firebase console → remote-family-support → Build → Firestore Database → **Rules** → সম্পূর্ণ পেস্ট → **Publish**।
2. নতুন রানের **app-release-apk (v0.6.2)** ইনস্টল।
3. টেস্ট: ফোন-A কোড তৈরি → ফোন-B (ভিন্ন অ্যাকাউন্ট) স্ক্যান/টাইপ → **Pair with code** → "Paired ✓" + উভয় ফোনে Paired devices-তালিকা।
4. Presence/Push-স্টেটাসও এবার "Online ✓"/"ready ✓" হওয়ার কথা (Phase 5 rules-এ devicePresence+fcmTokens অনুমোদিত)।

### 28.5 স্থিতি

- **কোড-পাশ সম্পূর্ণ ✓ (v0.6.2, CI-প্রমাণিত); rules-পেস্ট + Owner-যাচাই বাকি।** পেয়ারিং-পাথের তিনটি প্রতিবন্ধক (transaction ক্রম, missing-doc get, re-pair ফিল্ড) এখন অপসারিত।
- ইমেইল+পাসওয়ার্ড রোডম্যাপ (§22.2) অপরিবর্তিত।

---

## ২৯. Section 29 — Phase 6: Support session — সম্মতি-ভিত্তিক সাহায্য-সেশন (v0.7.0 — versionCode 11→12)

### 29.1 ট্রিগার

- Owner নিশ্চিত করলেন: **পেয়ারিং সফল** ("কানেক্ট হয়েছে") — v0.6.2 + rules-পেস্টে `22101317C · ACTIVE · Online`; Revoke-টেস্টও হয়েছে (REVOKED দেখা গেছে)। Phase 4+5 এখন সম্পূর্ণ ভেরিফায়েড।
- পরবর্তী মাইলফলক: **Phase 6 — support session**। মূল-রোডম্যাপে Phase 6 ছিল সিগন্যালিং-সার্ভার; বাস্তব-অবস্থায় (ফোন-অনলি Owner, শূন্য-খরচ, বাহ্যিক হোস্ট নেই) প্রথমে **সেশন-সম্মতি মেশিন** ডেলিভারি করা হলো — যা ছিল স্তম্ভ-৩-এর ভিত্তি ("পেয়ার থাকলেও প্রতিটি সেশনে আলাদা অনুমতি")। সিগন্যালিং + WebRTC স্ক্রিন-শেয়ার পরবর্তী ফেজে।

### 29.2 ডিজাইন — সেশন-লাইফসাইকেল

```
helper: "Start session" ──▶ REQUESTED (respondDeadline = এখন + ২ মিনিট)
                               │
        recipient-এর ফোনে লাইভ কার্ড + (ব্যাকগ্রাউন্ডে) নোটিফিকেশন
                               │
   ┌───────────────┬───────────┼────────────────┬───────────────┐
   ▼               ▼           ▼                ▼               ▼
 ACTIVE         DENIED     CANCELLED        EXPIRED      (কোনো উত্তর নেই)
 (grantedAt +      │           │            (lazy cleanup)      │
  sessionEndsAt =  │           │                                │
  এখন + ১৫ মিনিট)  │           │                                │
   │               │           │                                │
   ▼               ▼           ▼                                ▼
 ENDED (যে-কেউ, অথবা "timeout" — ১৫ মিনিট পার হলে যে-কোনো ক্লায়েন্ট বন্ধ করে)
```

- **এক-জোড়ায় একবারে একটিই খোলা সেশন** (REQUESTED বা ACTIVE) — `requestSession` transaction আগেরটা খুঁজে আটকায়।
- **কোনো সেশন-ডকইউমেন্ট নিজে থেকে কোনো ডেটা-অ্যাক্সেস দেয় না** — ভবিষ্যৎ capability (স্ক্রিন-শেয়ার ইত্যাদি) কখনোই ACTIVE সেশনের রেফারেন্স ছাড়া হবে না।

### 29.3 কোড-উপাদান (সব নতুন)

1. **`SessionRepo.kt`:** `SessionView`; `requestSession` (transaction: open-session query-read → write; সব read আগে — v0.6.1-শিক্ষা), `grantSession` (recipient-only, deadline + grace, ACTIVE + sessionEndsAt), `denySession`, `cancelSession`, `endSession(byTimeout)`, `expireSession` (best-effort lazy), `watchSessions` (উভয়-রোল listener, newest-first, limit 8)।
2. **`App.kt` (নতুন Application):** started-activity কাউন্ট — অ্যাপ দৃশ্যমান কিনা নোটিফায়ার জানে।
3. **`SessionNotifier.kt`:** recipient-পাশে `recipientUid==me && status==REQUESTED` listener; অ্যাপ ব্যাকগ্রাউন্ডে থাকলে (process জীবিত) high-importance নোটিফিকেশন; চ্যানেল `support_session_requests`; Android 13+ POST_NOTIFICATIONS runtime-পারমিশন (সেশন-সেকশনে "Enable notifications" বোতাম)। **সীমা:** process মারা গেলে ডেলিভারি বন্ধ — kill-এর পরেও পুশ Phase 7-এর সার্ভার (FCM HTTP v1 + Phase 5-এর `fcmTokens`)।
4. **`MainActivity.kt`:** Paired devices-রোতে helper-এর **"Start session (asks permission)"** বোতাম (খোলা সেশন থাকলে নিষ্ক্রিয়); **Section D — Phase 6:** প্রাপকের ত্রিভুজ-রঙা সম্মতি-কার্ড (Allow/Deny + কাউন্টডাউন), helper-এর waiting + Cancel request, ACTIVE-কার্ড (কে কাকে সাহায্য করছে + অটো-শেষের কাউন্টডাউন + End session), `Last session: …` হিস্ট্রি-লাইন; `mmss()` কাউন্টডাউন-হেল্পার; ভার্সন "0.7.0 (Phase 6)", পরবর্তী মাইলফলক "Phase 7"।
5. **`AndroidManifest.xml`:** `android:name=".App"` + `POST_NOTIFICATIONS`।
6. **`firestore.rules` — `supportSessions`:** get/list = শুধু participant; create = শুধু ACTIVE relationship-এর helper, `relId == helperUid_recipientUid`, deadline ≤ ৫ মিনিট; update = নির্দিষ্ট ৫টি ট্রানজিশনই কেবল (উপরের ডায়াগ্রাম), `affectedKeys().hasOnly(...)` প্রতিটিতে; ACTIVE-এ `sessionEndsAt ≤ request.time + 20 min`; delete = কখনোই না। ক্যাচ-অল deny অপরিবর্তিত।
7. **`build.gradle.kts`:** versionCode 12 / 0.7.0।

### 29.4 প্রেরণ-রেকর্ড ও CI

- push script-এ SessionRepo.kt, App.kt, SessionNotifier.kt যোগ; কমিট-মেসেজ "v0.7.0: Phase 6 — support session consent flow…"। CI ফলাফল Task 19-b-তে।

### 29.5 Owner-এর করণীয় (দুই ধাপ)

1. **নতুন rules পেস্ট (আবশ্যক, v0.6.2-এর মতোই):** ফোনের ব্রাউজারে `raw.githubusercontent.com/psfaruk/family-remote-support/main/firestore.rules` খুলুন → সম্পূর্ণ কপি → Firebase console → remote-family-support → Firestore Database → Rules → সম্পূর্ণ পেস্ট → **Publish**। (না করলে "Start session" PERMISSION_DENIED দেবে।)
2. নতুন রানের **app-release-apk (v0.7.0)** দুই ফোনেই ইনস্টল।

### 29.6 Owner-এর পরীক্ষা-তালিকা (v0.7.0)

1. উভয় ফোনে অ্যাপ খোলা + সাইন-ইন (পেয়ার ভাঙলে আগে আবার পেয়ার করুন)।
2. **Recipient-ফোনে** সেশন-সেকশনে **Enable notifications** → "Allow" (একবারই)।
3. **Helper-ফোনে** Paired devices-এ **Start session (asks permission)** চাপুন → "Asking … to allow" + 2:00 কাউন্টডাউন।
4. **Recipient-ফোনে** ত্রিভুজ-রঙা কার্ড: "…wants to help with this phone" → **Allow** → দুই ফোনেই ACTIVE-কার্ড + 15:00 কাউন্টডাউন।
5. **End session** (helper) → দুই ফোনেই "Last session: ended (ended by …)"।
6. আবার Start → এবার recipient **Deny** → "Last session: denied"।
7. বোনাস: recipient-অ্যাপ ব্যাকগ্রাউন্ডে নিয়ে helper Start করলে নোটিফিকেশন আসার কথা (process জীবিত থাকলে)।

### 29.7 স্থিতি ও সততা

- **কোড + rules সম্পূর্ণ (CI-প্রমাণিত বিল্ড); বাস্তব দুই-ফোন টেস্ট বাকি।** সেশনটি এখনো "অনুমতি + সময়সীমা" মাত্র — এর ভিতরে কোনো রিমোট-কন্ট্রোল/স্ক্রিন-শেয়ার নেই (Phase 7: সিগন্যালিং সার্ভার + WebRTC)।
- ইমেইল+পাসওয়ার্ড রোডম্যাপ (§22.2) অপরিবর্তিত; সব ফেজ শেষে Owner SA-JSON ডিলিট করবেন।

### 29.8 v0.7.1 সংশোধনী (Owner টেস্টে ধরা পড়া দুটি গভীর সমস্যা)

Owner-এর প্রথম "Start session" ট্যাপে `PERMISSION_DENIED` — কারণ-বিশ্লেষণে দুই স্তরের শিক্ষা:

1. **Firestore rules "queries are not filters":** security rules-এ `list` অনুমোদন `resource.data.helperUid == request.auth.uid || recipientUid == …` শর্তে দেওয়া; কিন্তু `requestSession`-এর duplicate-check query শুধু `relId == X` দিয়ে খুঁজছিল — rules ইঞ্জিন query-র ফলাফলে নিজের শর্ত **প্রমাণ** করতে পারে না (relId থেকে helperUid বের করা যায় না) → সঠিক rules থাকলেও PERMISSION_DENIED। **ফিক্স:** duplicate-check query-তে `whereEqualTo("helperUid", user.uid)` যোগ (relId সমান মানেই helperUid সমান — অর্থ অপরিবর্তিত, কিন্তু এখন rules-প্রমাণযোগ্য)।
2. **Composite index-পরিহার:** `watchSessions`-এ `orderBy(requestedAt DESC)` + equality query-তে **composite index** লাগত (না থাকলে FAILED_PRECONDITION), আর index বানানোও console-কাজ। **ফিক্স:** live watch এখন equality-only (`helperUid/recipientUid == me` + `status in [REQUESTED, ACTIVE]`, কোনো orderBy নেই) — single-field automatic index-এই চলে, rules-ও প্রমাণযোগ্য, আর ফলাফল-সেট **সম্পূর্ণ** (যে-কোনো খোলা সেশন মিস হওয়া অসম্ভব — সম্মতি-কার্ডের জন্য এটাই দরকার)। শেষ-হওয়া সেশনের "Last session:" লাইন এখন open→closed ট্রানজিশনে একবার `getSession()` এনে মনে রাখে (app-restart-এ মুছে যায় — তথ্যগত লাইন মাত্র)।
3. `describeFirestoreError`-এর PERMISSION_DENIED টেক্সট এখন Phase 6 rules-পেস্ট নির্দেশ করে। ভার্সন 0.7.1 / versionCode 13।
4. **Owner-এর করণীয় অপরিবর্তিত:** (ক) §29.5-এর rules-পেস্ট+Publish (এটি না করা পর্যন্ত যেকোনো ভার্সনেই Start session ব্যর্থ হবে), (খ) v0.7.1 APK দুই ফোনে ইনস্টল, (গ) §29.6 টেস্ট-তালিকা।

## 30. Phase 7 (v0.8.0) — লাইভ স্ক্রিন-শেয়ার (view-only) + সার্ভারহীন সিগন্যালিং

### 30.1 Owner প্রদত্ত সামর্থ্য

- Owner Firebase **service-account key** প্রদান করেছেন → `/home/z/.secrets/firebase-sa.json` (chmod 600, কখনো কমিট হয় না)।
- **এরপর থেকে Firebase-এর সব কাজ অটোমেশন নিজে করে ও ভেরিফাই করে** — console-পেস্ট-পথ অবসর।
- SA দিয়ে যাচাই: লাইভ rules == repo rules (Owner-এর Phase 6 পেস্ট নিখুঁত ছিল), supportSessions-এ ENDED + EXPIRED ট্রানজিশন-প্রমাণ, fcmTokens ২টি, devicePresence ২টি।
- SA দিয়ে Phase 7 rules **নিজেই Publish** (release `cloud.firestore/default` → ruleset; শেখা: v1 PATCH-এর বডি = `UpdateReleaseRequest {release:{name,rulesetName}, updateMask:"ruleset_name"}` — সরাসরি Release বডি নয়!)।

### 30.2 ডিজাইন — কোনো সিগন্যালিং সার্ভার নেই (শূন্য-খরচ)

- **Firestore-ই সিগন্যালিং**: `supportSessions/{sessionId}/signal/{autoId}` — `{aud (পাঠানোর লক্ষ্য-পাঠক), from, type: offer|answer|ice|watch|bye, payload (JSON SDP/ICE), createdAt}`।
- প্রতিটি পাশ শুধু **নিজের ঠিকানায়** আসা ডক পড়ে (`where("aud","==",me)`) — rules-provable list; create-এ rules প্যারেন্ট-সেশন get করে ACTIVE + participant + "শুধু অন্য-পক্ষকে" ঠিকানা যাচাই করে; payload < 100KB; delete = participant।
- **watch-first নেগোসিয়েশন** (offer/answer race-শূন্য): helper "watch" পাঠায় → recipient তারপর fresh offer দেয় → helper answer → দুই-দিক ICE trickle (remote-desc-এর আগে এলে বাফার)। helper আবার "Watch" চাপলে নতুন watch → নতুন offer — re-watch সহজ।

### 30.3 কোড-উপাদান

1. **`SignalingRepo.kt`:** send / watch / clearAsync (best-effort পুরনো-ডক মোছা)।
2. **`ScreenShareEngine.kt` (singleton):** PeerConnectionFactory (stream-webrtc-android 1.3.8, org.webrtc API), EglBase শেয়ার্ড; recipient = ScreenCapturerAndroid (স্ক্রিন-অনুপাতে ≤1280px, 15fps, even-dimension) + VideoSource(screencast=true); helper = recv + SurfaceViewRenderer-এ VideoTrack; StateFlow `state` (IDLE→PREPARING→WAITING_PEER→CONNECTING→LIVE/ENDED) + `info` লাইন; "bye"-এ উভয়-পাশ টিয়ারডাউন; main-thread-ড্রিভেন WebRTC।
3. **`ScreenShareService.kt`:** `mediaProjection` foreground service; Android 14 নিয়ম মেনে startForeground(type) **আগে**, তারপর ইঞ্জিন শুরু; LOW-importance নোটিফিকেশন।
4. **`MainActivity` — ScreenSharePanel (ACTIVE কার্ডের ভেতরে):** recipient = "Share screen with helper" (সিস্টেম consent-লঞ্চার) / "Stop sharing"; helper = "Watch screen (view only)" + 220dp রেন্ডারার / "Stop watching"; সেশন-শেষে LaunchedEffect সব বন্ধ করে।
5. **Manifest:** FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PROJECTION + service(exported=false, mediaProjection type)।
6. **Gradle:** io.getstream:stream-webrtc-android:1.3.8; abiFilters armeabi-v7a+arm64-v8a (x86 বাদ → APK ছোট); versionCode 14 / 0.8.0।

### 30.4 সততা-ঘোষণা (সীমা)

- **View-only** — রিমোট টাচ-কন্ট্রোল এই ফেজে নেই (Phase 8 প্রার্থী)।
- **STUN মাত্র (Google public)**, TURN নেই — একই Wi-Fi/সাধারণ NAT-এ কাজ করার কথা; কড়া symmetric-NAT (কিছু মোবাইল-ডেটা) ব্যর্থ হতে পারে → ভবিষ্যতে ছোট TURN (Cloud Run/metered)।
- স্ক্রিন দেখতে helper-অ্যাপ খোলা থাকতে হয় (process-liveness); kill-হওয়া অ্যাপে পুশ-নোটিফিকেশন Phase 8-এ FCM HTTP v1 (SA এখন আছে) দিয়ে হবে।
- প্রতি পাশের সিগন্যাল-ডক ছোট ও সেশন-স্কোপড; শুরু/শেষে best-effort মোছা হয়।

### 30.5 Owner-এর পরীক্ষা-তালিকা (v0.8.0)

1. দুই ফোনে v0.8.0 APK (CI artifact `app-release-apk`)।
2. helper: Start session → recipient: **Allow** (আগের মতোই)।
3. **recipient:** ACTIVE কার্ডে **Share screen with helper** → সিস্টেম ডায়ালগে **Start now / এখনই শুরু করুন** → নোটিফিকেশন "Screen sharing active"।
4. **helper:** একই কার্ডে **Watch screen (view only)** → কিছুক্ষণে recipient-এর স্ক্রিন ভিডিওতে দেখা যাবে + "Live"।
5. recipient-এর স্ক্রিনে অ্যাপ ঘোরানো/মেনু খোলা → helper-এর ভিডিওতে তা-ই দেখা যাচ্ছে কিনা।
6. helper **Stop watching** → recipient-এর নোটিফিকেশন/শেয়ার বন্ধ। (বিকল্প: recipient Stop sharing → helper "stopped sharing" দেখবে।)
7. **End session** → সব কিছু স্বয়ংক্রিয়ভাবে বন্ধ; "Last session: ended"।

### 30.6 v0.8.1 সংশোধনী — "স্ক্রিন কালো আসে" (Owner-স্ক্রিনশট-নির্ণয়)

Owner-এর রিপোর্ট: বাটন/সেশন-প্রবাহ ঠিক, কিন্তু helper-ফোনে ভিডিও **কালো**; একবার "Connection failed", বারবার "Answer sent — connecting video…" আটকে থাকা; recipient-এ "Connected — streaming screen…" (যেটা আসলে আশাবাদী লেখা ছিল, PC-সত্য নয়)। রোগনির্ণয়ে তিনটি আসল বাগ + একটি পরিবেশ-ঝুঁকি:

1. **Stale-signal বিষক্রিয়া (মূল কারণ):** আগের রাউন্ডের offer/ice ডক Firestore-এ থেকে যেত; নতুন helper-রাউন্ডের listener-এর initial snapshot সেগুলো **আগেই** পড়ত (clearAsync পরে মুছলেও দেরি) → helper মৃত negotiation-এ answer পাঠাত → "Answer sent" চিরকাল + কালো। আর recipient-এর `!remoteDescriptionSet` ফ্ল্যাগ পুরনো রাউন্ডেই true থেকে যাওয়ায় নতুন answer-ও সে ফেলে দিত। **ফিক্স:** প্রতিটি রাউন্ডে র‍্যান্ডম **epoch (`ep`)** টোকেন — helper "watch"-এ পাঠায়, recipient offer/ice-এ সেটাই ফেরত দেয়, helper answer/ice-এ নিজের epoch দেয়; পাঠক শুধু বর্তমান রাউন্ডের epoch মেলে এমন সিগন্যাল প্রয়োগ করে — পুরনো ডক স্বয়ংক্রিয়ভাবে অদৃশ্য। rules-এ `ep` ফিল্ড (string ≤64) আবশ্যক করা হয়েছে এবং SA দিয়ে Publish+verify সম্পন্ন (ruleset c601e0c0…)।
2. **Re-watch-এ নতুন ICE candidate আসত না:** recipient একই PC-তে IceRestart ছাড়া পুনরায় offer বানালে ICE আবার gather করে না → নতুন helper কোনো candidate-ই পেত না। **ফিক্স:** প্রতিটি "watch"-এ `IceRestart=true` কনস্ট্রেইন্ট → নতুন ufrag/pwd + সম্পূর্ণ নতুন candidate।
3. **Stale-callback দূষণ:** বন্ধ হওয়া PC-র দেরিতে আসা FAILED callback নতুন রাউন্ডের state নষ্ট করত ("Connection failed" ভুল মুহূর্তে)। **ফিক্স:** প্রতিটি observer-callback নিজের PC-র পরিচয় যাচাই করে (`mine()` guard)।
4. **সততা + নির্ণয়-সামর্থ্য:** helper-এ CONNECTED এখন দেখায় ("Connection established…"), প্রথম decoded ফ্রেমে "Live — receiving the screen ✓", ১২ সেকেন্ড ফ্রেম-শূন্য হলে স্পষ্ট সতর্কবার্তা; recipient-এর মিথ্যা "Connected — streaming…" বাদ, বাস্তব অবস্থা + **মিনি সেলফ-প্রিভিউ** ("Preview — this is what the helper sees") যাতে recipient নিজেই দেখে ক্যাপচার ফ্রেম দিচ্ছে কি না। helper-এর "Stop watching" এখন **bye পাঠায় না** — recipient শেয়ার চালু রাখে, helper এক-ট্যাপে আবার দেখতে পারে।
5. **মিডিয়া-পথ মজবুতকরণ:** দুই পাশে SDP-তে **VP8 অগ্রাধিকার** (সফটওয়্যার কোডেক — সব ফোনে একরকম আচরণ, ভাঙা H.264 হার্ডওয়্যার এড়াতে) + Google STUN-এর পরে **ফ্রি কমিউনিটি TURN** (openrelay.metered.ca — public credential; না চললে ICE নিজেই STUN-এ ফিরে যায়) — কড়া NAT/মোবাইল-ডেটার ঝুঁকি কমাতে।

ভার্সন 0.8.1 / versionCode 15। **উভয় ফোনে v0.8.1** ইনস্টল আবশ্যক (rules-এ `ep` আবশ্যকতার কারণে v0.8.0-এর সিগন্যাল লেখা বন্ধ হবে — এটাই ইচ্ছাকৃত)।

### 30.7 Owner-এর পরীক্ষা-তালিকা (v0.8.1)

1. দুই ফোনে v0.8.1 APK (CI artifact `app-release-apk`) — **দুটোতেই**।
2. helper: Start session → recipient: **Allow**।
3. **recipient:** Share screen → সিস্টেম consent → **Start now** → "Screen capture ready — waiting…" + প্রিভিউ-বক্সে নিজের স্ক্রিনের ঝলমলানি দেখা যাচ্ছে কি না (ক্যাপচার-প্রমাণ)।
4. **helper:** Watch screen → ধাপে ধাপে: "Asked…" → "Video channel ready…" → "Answer sent…" → "Connection established…" → **"Live — receiving the screen ✓"** + recipient-এর স্ক্রিন ভিডিওতে।
5. helper **Stop watching** → recipient শেয়ার চালু থাকে ("waiting…" লেখা) → helper **Watch screen** আবার → দ্বিতীয়বারও সরাসরি লাইভ (এটাই v0.8.1-এর মূল পরীক্ষা — আগে এখানেই কালো হতো)।
6. কালো হলে কী দেখবেন: "Connection failed…" মানে দুই ফোন একে অপরে পৌঁছাতে পারেনি — দুটোকে **একই Wi-Fi** (বা একটির hotspot-এ অন্যটি) রেখে আবার চেষ্টা করুন; "Connected, but no video is arriving" মানে লাইন আছে কিন্তু ফ্রেম নেই — Stop → Watch আবার।
7. ফলাফল + স্ক্রিনশট জানান; সব ঠিক থাকলে Phase 8-এর আলোচনায় যাব (FCM kill-push / TURN / assisted control)।

### 30.8 v0.8.2 সংশোধনী — প্রকৃত কালো-স্ক্রিনের মূলোৎপাটন (Owner-স্ক্রিনশট-নির্ণয়)

v0.8.1-পরবর্তী Owner-স্ক্রিনশট (১২:৫৬–১২:৫৭) রহস্যটা **প্রমাণই করে দিয়েছিল**: helper-এ "Live — receiving the screen ✓" — অথচ ছবি কালো; recipient-এ "Live — the helper can see this screen." — অথচ সেলফ-প্রিভিউও কালো।

**নির্ণয় (কম্পোজ-লাইফসাইকেল বাগ):** `ScreenSharePanel`-এ renderer attach হতো `AndroidView`-এর **factory**-তে, তারপর সাথে সাথে `rendererView = this` স্টেট-রাইট হতো। সেই রাইট `DisposableEffect(rendererView)`-কে **রিস্টার্ট** করত → রিস্টার্টের নিয়ম অনুযায়ী আগে পুরনো effect-এর `onDispose` চলে → সেখানে `detachRenderer()` **factory-র সদ্যকৃত attach-টাই মুছে দিত**, আর নতুন effect-বডিতে attach ছিলই না। ফলাফল: ইঞ্জিন ফ্রেম decode করছিল (frameCounter গুনছিল → watchdog "Live ✓" দেখাচ্ছিল), PeerConnection CONNECTED — কিন্তু **উভয় সারফেসে কোনো sink-ই নেই → স্থায়ী কালো**। recipient-প্রিভিউতেও হুবহু একই প্যাটার্ন। এই এক বাগই দুই ফোনের কালো-ছবির সম্পূর্ণ ব্যাখ্যা — v0.8.1-এর সিগন্যালিং-ফিক্সগুলো সঠিকই ছিল (connection+frames প্রমাণিত), কিন্তু রেন্ডার-পাশটা রয়ে গিয়েছিল।

**ফিক্স (তিন স্তরে):**
1. attach এখন **effect-বডিতে** (`DisposableEffect(rendererView) { attach(view); onDispose { detach; release } }`) — key স্থির হওয়ার পরেই attach, তাই "dispose-after-attach" অসম্ভব;
2. `AndroidView(update = …)` প্রতিটি recomposition-এ attach **পুনঃপ্রতিষ্ঠা** করে (ইঞ্জিনে remove+add — idempotent) — যে-কোনো অদ্ভুত recomposition-পথেও সারফেস সংযুক্ত থাকবে;
3. নতুন "watch" রাউন্ড গ্রহণের সময় `remoteDescriptionSet=false` + `pendingIce.clear()` — re-watch-এর answer-পূর্ব ICE candidate আর পুরনো remote description-এর বিরুদ্ধে প্রয়োগ হয়ে হারিয়ে যেত না।

সঙ্গীয় পরিষ্কার: helper-এর স্টিকি "Request sent — waiting…" লাইন এখন সেশন ACTIVE হলে স্বয়ংক্রিয়ভাবে মুছে যায় (আগে live-কার্ডের নিচে বাসি লাইন দেখা যেত — স্ক্রিনশটে ধরা ছিল)।

ভার্সন 0.8.2 / versionCode 16। পরীক্ষা §30.7-এর ধাপ ৩–৭ই — এবার প্রিভিউ-বক্স ও helper-ভিডিও দুটোতেই সত্যিকারের ছবি আসার কথা; "Live — receiving the screen ✓" **এখন থেকে কালো-ছবির সাথে কখনোই দেখা যাবে না** (ওই লেখা আসামাত্র ফ্রেম রেন্ডারও হচ্ছে)।

### 30.9 v0.8.3 সংশোধনী — সেলফ-হিলিং সংযোগ + বড় ছবি (Owner-স্ক্রিনশট-নির্ণয়)

v0.8.2-পরবর্তী Owner-স্ক্রিনশট (১৪:১১–১৪:১৩) + ক্যামেরা-ছবি: helper-এ কালো বক্স + "Answer sent — connecting video…" আটকে, কোথাও "Connection failed — the two phones could not link up"; recipient-এও "Connection failed — Tap Stop sharing, then share again"; আর প্রশ্ন — "স্ক্রিন এত ছোট কেন?"

**নির্ণয় ১ — ফ্রি TURN মৃত (মূল কারণ):** বিল্ড-বক্স থেকে raw STUN/TURN প্রোব (node dgram): Google STUN সাড়া দেয় (Binding Success, mapped 8.212.10.159) — কিন্তু **openrelay.metered.ca UDP-তে পিনপুন নেই, TCP 80/443 connection refused**; নতুন হোস্ট standard.relay.metered.ca 401 challenge দেয় কিন্তু পুরনো public ক্রেডেনশিয়াল (openrelayproject) আর গ্রহণ করে না (authenticated allocate → 401); ExpressTurn-এর জনপ্রিয় কম্বোগুলোও অকার্যকর। অর্থাৎ v0.8.1-এ যে relay-fallback ছিল সেটি কাগজে-কলমে নেই — **দুটি ভিন্ন ক্যারিয়ার-নেটওয়ার্ক (CGNAT) STUN-only-তে সাধারণত যুক্ত হতে পারে না**; একই Wi-Fi/হটস্পটে হয় (হোস্ট-ক্যান্ডিডেট) — তাই কখনো কাজ করে, কখনো "Connection failed"।

**নির্ণয় ২ — ব্যর্থ রাউন্ডের পর দুই ফোন পরস্পর-অন্ধ:** recipient-এর "watch"-হ্যান্ডলার শুধু WAITING_PEER/CONNECTING/LIVE স্টেটে কাজ করত — FAILED→ENDED হওয়ার পর এলে সেটি **নীরবে বাতিল** হতো; helper তখন "Asked the recipient's phone…" লাইনে চিরকাল অপেক্ষা করত। একমাত্র পথ ছিল recipient-এ "Stop sharing" → re-consent — যেটা অন্য ফোনের মানুষকে বোঝানো কঠিন।

**ফিক্স (v0.8.3):**
1. **রানটাইম TURN-config:** ইঞ্জিন প্রতি সেশনের শুরুতে `config/turn` Firestore ডক পড়ে (app-চালিত লেখা নিষিদ্ধ; লেখা কেবল Owner-এর SA — `scripts/set-turn-config.mjs`) এবং সেগুলোকে ICE-তে merge করে। ফ্রি metered.ca TURN (২০GB/মাস) ক্রেডেনশিয়াল পেলে SA দিয়ে ডকে বসালেই নতুন APK ছাড়া ক্রস-নেটওয়ার্ক চালু। খালি ডক ⇒ STUN-only। সাথে `GATHER_CONTINUALLY` + Cloudflare STUN।
2. **সেলফ-হিলিং watch:** recipient এখন capture জীবিত থাকলে **যেকোনো স্টেটে** watch গ্রহণ করে; FAILED-এ শুধু PeerConnection rebuild হয় (ক্যাপচার/ট্র্যাক/লিসেনার অক্ষত), স্টেট ফেরে WAITING_PEER-এ — "Stop sharing"+re-consent আর লাগে না; নতুন PC-তে track re-add করা হয়।
3. **helper অটো-রিট্রাই:** watch-পাঠানোর ১২ সে-এ offer না এলে / offer-এর ১২ সে-এ answer না এলে / answer-এর ১৪ সে-এ ফ্রেম শূন্য হলে / হার্ড FAILED-এ — স্বয়ংক্রিয় নতুন রাউন্ড (নতুন epoch + PC + watch), সর্বোচ্চ ৩ বার (সাফল্যে বাজেট রিফিল); শেষ হলেই কেবল হাতে-করা পরামর্শ। এক-রাউন্ডে-এক-সিদ্ধান্ত গার্ড (retryRoundTag)।
4. **নেটওয়ার্ক সেলফ-ডায়াগনসিস:** গ্যাদারিং শেষে উভয় ফোনে "Network check — direct: N · via STUN: M · via relay: K" — স্ক্রিনশটেই বোঝা যায় relay পাওয়া গেছে কি না (K>0)।
5. **বড় ছবি:** helper-এর ভিডিও এখন আসল ফ্রেম-অনুপাতে (decoded frame size → aspect) fillMaxWidth, সর্বোচ্চ 480dp, **ট্যাপ করলে ফুলস্ক্রিন দর্শক** (কালো, edge-to-edge); LIVE অবস্থায় helper-এর স্ক্রিন জ্বলে থাকে (FLAG_KEEP_SCREEN_ON); recipient প্রিভিউ 120→160dp।

ভার্সন 0.8.3 / versionCode 17। Owner-করণীয়: দুই ফোনে v0.8.3 → পরীক্ষা §30.7। একই Wi-Fi/হটস্পটে ১০০% কাজ করার কথা; ভিন্ন নেটওয়ার্কে চাইলে metered.ca ফ্রি TURN অ্যাকাউন্ট খুলে ক্রেডেনশিয়াল Owner পাঠাবেন → SA দিয়ে `config/turn`-এ বসানো হবে (নতুন APK লাগবে না)।

## 31. Phase 8 (v0.9.0, versionCode 18) — Remote Assist (সহায়তা-নিয়ন্ত্রণ)

### 31.1 কী এলো
Phase 7-এ helper শুধু **দেখতে** পেত; Phase 8-এ helper **ট্যাপও করতে পারে** — পরিবারের সাহায্যের মূল উদ্দেশ্য এবার পূর্ণ হলো।

- **Helper-এর ফোনে:** ভিডিওর নিচে নতুন **"Touch control" সুইচ**। চালু করলে ছবিতে ট্যাপ মানে গ্রহীতার ফোনে ট্যাপ (inline ও full-screen — দুই জায়গাতেই)। সাথে **◀ Back / ⌂ Home / ▦ Recents** তিনটি বাটন।
- **গ্রহীতার ফোনে:** শেয়ার-কার্ডে "Remote assist is ON/OFF" সত্য-স্থিতি + একবারই সেটআপের "Turn on Remote assist" বাটন (সিস্টেম Settings → Accessibility খুলে দেয়)।
- **প্রতি কমান্ডের প্রতিউত্তর:** helper-এর UI বলবে "Tap delivered ✓" নয়তো "Remote assist is switched off there" — নিঃশব্দ-ব্যর্থতা নেই।

### 31.2 কীভাবে কাজ করে (স্থাপত্য)
1. **AssistService.kt** (নতুন) — একটি AccessibilityService: `canPerformGestures=true`, কিন্তু **`canRetrieveWindowContent=false`** — সেবাটি পর্দার কিছুই **পড়ে না**, শুধু ট্যাপ বসাতে পারে (চোখ নয়, আঙুল)। Back/Home/Recents গ্লোবাল-অ্যাকশনও করতে পারে।
2. **কমান্ড-চ্যানেল:** ভিডিও-সিগন্যালিং-এর সেই নিরাপদ Firestore কাতারেই `cmd`/`cmdack` টাইপ (rules-এ যোগ করা হয়েছে) — শুধু ACTIVE সেশনের অন্য অংশগ্রহণকারী লিখতে পারে (server-enforced), প্রতিটি কমান্ডে **বর্তমান epoch** থাকতেই হবে (পুরনো রাউন্ডের বাসি কমান্ড নিষ্ক্রিয়)। সেশন/ক্যাপচার শেষ হলেই ইঞ্জিন কমান্ড প্রয়োগ বন্ধ করে দেয়।
3. **সমন্বয়-গণিত:** helper-এর letterbox-করা ভিডিওতে ট্যাপের অবস্থান → বাস্তব ফ্রেমের (0..1) normalized স্থানাঙ্ক → গ্রহীতার আসল ডিসপ্লের পিক্সেলে রূপান্তর → `dispatchGesture` দিয়ে ~60ms ট্যাপ। রেজোলিউশন-নিরপেক্ষ।

### 31.3 বিশ্বাস-মডেল (গোপনীয়তা)
- গ্রহীতা **নিজে একবার** সেবাটি চালু করবেন (Android নিজেই সতর্ক-স্ক্রিন দেখায়); যেকোনো মুহূর্ত বন্ধ করলেই helper আবার view-only।
- সেবাটি পর্দা পড়তে পারে না — পাসওয়ার্ড/মেসেজ/কিছুই নয়; শুধু ট্যাপ ঢোকাতে পারে।
- সব কমান্ড ACTIVE সেশন + epoch গেটের পেছনে — ভিডিওর মতোই নিরাপত্তা-শিকল।

### 31.4 Owner-করণীয় (পরীক্ষা §31.5)
1. **দুই ফোনেই v0.9.0 (versionCode 18) ইনস্টল** — পুরনো সংস্করণের উপরেই বসবে (সাইন-সামঞ্জস্য)।
2. গ্রহীতার ফোনে: সেশন শুরু করে "Share screen" চালু → কার্ডে **"Turn on Remote assist"** চাপ → Settings-এ "Family Remote Support assist" খুঁজে **On** করুন → অ্যাপে ফিরে আসুন (কার্ড নিজেই "Remote assist is ON" দেখাবে)।
3. Helper-এর ফোনে: "Watch screen" → লাইভ হলে **Touch control ON** → ছবিতে যেকোনো জায়গায় ট্যাপ করুন → গ্রহীতার ফোনে সেই জায়গায় ট্যাপ হবে এবং helper-এর ফোনে "Tap delivered ✓" আসবে।
4. নেভ-বাটন পরীক্ষা: **Home** চাপলে গ্রহীতার ফোন হোম-স্ক্রিনে যাবে; **Back** পেছনে যাবে; **Recents** সাম্প্রতিক-অ্যাপ খুলবে।
5. বিপরীত-যাচাই: গ্রহীতার ফোনে assist **বন্ধ** রেখে helper ট্যাপ করলে "The recipient's phone cannot tap — Remote assist is switched off there." দেখানোর কথা।

### 31.5 পরবর্তী (Phase 8 ধারাবাহিক)
- Phase 8.2 (স্থগিত): FCM kill-push — অ্যাপ মারা গেলেও সেশন-অনুরোধ পৌঁছানো।
- Phase 8.3 (প্রার্থী): সোয়াইপ/লং-প্রেস/টাইপিং; রিলে-চললে কম-বিটরেট মোড।
- Oracle Always-Free TURN (Owner চালু করলেই): probe → `config/turn` → ভিন্ন-নেটওয়ার্ক নিশ্চিত-সংযোগ।
- UI/UX polish: Phase 8 স্থিতিশীল হওয়ার পর আলাদা phase-এ (বাংলা লেখা, রঙ, সাজ)।

## 32. Phase 9 (v0.9.4, versionCode 22) — পারিবারিক UI polish (T0–T3)

### 32.1 কী এলো
রিলে-টেস্ট সফল হওয়ায় §31.5-এ প্রতিশ্রুত "বাংলা লেখা, রঙ, সাজ" ধাপটি এলো — চারটি ট্রান্চে, লজিক-ঝুঁকি ছাড়া:
- **T0 থিম:** উষ্ণ amber/brown Material 3 লাইট-স্কিম + বয়স্ক-বান্ধব টাইপ-স্কেল (body 14→16sp, small 12→14sp, বোল্ড টাইটেল); ইচ্ছাকৃত FIXED-light (পূর্বানুমেয়, উচ্চ-কনট্রাস্ট) — পুরোটা একক MaterialTheme র‍্যাপ থেকে।
- **T1 সাজ:** SectionCard (একরূপ কার্ড-প্যাডিং/টাইটেল), BigButton (প্রাইমারি ≥52dp), WideOutlinedButton (সেকেন্ডারি ≥48dp); পেয়ারিং-কার্ড Account-এর নিচে উঠেছে; dev-রো → collapsed "About · অ্যাপ সম্পর্কে"; "Phase 2 build check" কার্ড অপসারিত।
- **T2 বাংলা:** পরিবার-মুখী সব লেখা বাংলা-প্রথম দ্বিভাষিক; phase-উপসর্গ সরানো; পেয়ারিং কোড headline-আকারে; কনসেন্ট-কার্ড বাংলায় + Allow/Deny বড় জোড়া-বাটন।
- **T3 টাচ-ওভারলে:** রিমোট-টাচ চালু হলে ভিডিওতে উষ্ণ রিম + "REMOTE TOUCH ON · টাচ চালু" ব্যাজ (inline+fullscreen); fullscreen-close এখন সত্যিকার ≥48dp বাটন; নেভ-বাটন ≥48dp; গ্রহীতার assist-কার্ড bold-বাংলা + প্রমিনেন্ট সেটআপ-বাটন (v0.9.3-ফরেনসিকে মিস-হওয়া ধাপ)।

### 32.2 Owner-করণীয়
1. দুই ফোনে v0.9.4 (versionCode 22) ইনস্টল — v0.9.3-এর সব ফিক্স এতেও আছে, তাই টাচ+কোয়ালিটি টেস্ট এই বিল্ডেই করলেও চলে।
2. টাচ-টেস্ট: গ্রহীতার ফোনে শেয়ার-কার্ডে "Turn on Remote assist · রিমোট টাচ চালু করুন" → Android Settings → Accessibility → "Family Remote Support assist" On → helper-এ Touch control ON → ছবিতে রিম+ব্যাজ দেখা যাবে, ট্যাপে "Tap delivered ✓" আসবে।
3. কোয়ালিটি: same-Wi-Fi বনাম রিলে-পাথ তুলনা করুন — রিলে সবসময় কিছুটা ধীর; ফল অনুযায়ী পেইড-রিলে সিদ্ধান্ত নিন।

### 32.3 পরবর্তী
- Phase 8.2 (স্থগিত — সার্ভার-সিদ্ধান্ত দরকার): FCM kill-push (Cloud Functions/Blaze বনাম Oracle-VM-স্ক্রিপ্ট বনাম স্থগিত)।
- Phase 8.3 (প্রার্থী): সোয়াইপ/লং-প্রেস/টাইপিং; কম-বিটরেট মোড।
- Oracle Always-Free TURN; টোকেন-rotate (SA/PAT/metered) — ফোন-টেস্ট সফলের পরে।

## 33. Phase 9 (v0.10.0, versionCode 28) — সিকিউরিটি হার্ডেনিং (২০২৫-১০-২৮)

### 33.1 কেন এবার
§12-এর রোডম্যাপ অনুযায়ী Phase 8-এর ধারাবাহিক (8.2 kill-push, 8.3 সোয়াইপ/লং-প্রেস/কম-বিটরেট) শেষ হওয়ায় পরের ধাপ ছিল
**"সিকিউরিটি হার্ডেনিং: negative tests, replay প্রতিরোধ, secure logging — থ্রেট মডেল আপডেট"**। প্রবেশ-বিন্দু:
SA (Admin) দিয়ে কখনোই rules *প্রমাণ* করা যায় না — Admin পথ rules বাইপাস করে। তাই সাধারণ-ব্যবহারকারীর
পরিচয়ে লাইভ rules-এর বিরুদ্ধে শত্রু-পথগুলো নিজেই গুলি করে দেখাই হলো এই ফেজের মূল কাজ।

### 33.2 Runtime negative-probe (সর্ববৃহৎ ডেলিভারেবল — `scripts/rules-negative-probe.mjs`)
- **পদ্ধতি:** SA-র প্রাইভেট কী দিয়ে RS256 custom token (uid = `rules-probe`) মিন্ট → Identity Toolkit
  `signInWithCustomToken` এক্সচেঞ্জ → **আসল ১-ঘণ্টার ID token** → Firestore REST-এ Bearer হিসেবে
  (অর্থাৎ প্রোব সাধারণ সাইন-ইন করা অ্যাপের মতোই rules-এর শিকার)। SA কেবল রিকন-এ (আসল uid/session/rel-id
  বের করতে) ও ক্লিনআপে ব্যবহৃত।
- **ফল — ১২/১২ প্রত্যাশামতো (লাইভ প্রোডাকশন, ২ বার চালানো):**
  - P0 পজিটিভ-কন্ট্রোল: নিজের devicePresence ডক create+delete → 200 ALLOWED (অথ-পথ কাজ করছে)
  - N1 অন্যের users/{uid} পড়া → 403 · N2 অন্যের sessions তালিকা → 403 · N3 invitations এনুমারেশন → 403
  - N4 config/quality লেখা → 403 · N5 relationship ছাড়া session তৈরি → 403 · N6 আসল সেশনে signal/cmd ঢোকানো → 403
  - N7a ছোট টোকেন (8) → 403 · N7b বিশাল টোকেন (5000) → 403 · N8 অন্যের fcmTokens পড়া → 403
  - N9 আসল সেশন UVjG2ojpKLLU3HPEYlQg মিউটেট → 403 · N10 relationship DELETE → 403
- **শূন্য-অবশিষ্ট:** synthetic Auth user `accounts:delete` (HTTP 200), throwaway presence ডক সঙ্গে সঙ্গে মুছে,
  পরের অডিটে probe-doc = 0, fcmTokens = ৩ আসল (১৪২ অক্ষর), REQUESTED = 0।
- এই স্ক্রিপ্ট এখন পুনর্ব্যবহারযোগ্য: ভবিষ্যতে rules বদলালেই একবার `node scripts/rules-negative-probe.mjs` —
  exit 1 মানে rules-drift।

### 33.3 ইনজেক্টর-সীমানায় replay/abuse guard (engine)
- **Replay:** Firestore `cmd` এখন সার্ভার-`createdAt` বয়স পরীক্ষা করে (SignalingRepo.watch নতুন করে
  timestamp প্যাস করে) — **>১৫ সেকেন্ড পুরনো কমান্ড অচল**; epoch-গেটের সাথে দ্বিতীয় স্বাধীন বেড়া।
- **Payload sanity:** উভয় ট্রান্সপোর্টে ≤ 4KB; স্থানাঙ্ক finite হতে হবে ও 0..1-এ clamp (engine-বাউন্ডারিতে একবার,
  AssistService-এ আবার — দুটো স্বাধীন বেড়া); dur clamp (swipe 120–3000ms, long 400–2000ms)।
- **Flood:** স্লাইডিং ১ সেকেন্ডে >২৫ কমান্ড → drop (মানুষের আঙুল এত দ্রুত নয়); teardown-এ কাউন্টার রিসেট।
- **সততা:** প্রত্যাখ্যাত কমান্ডও এখন সৎ ack পায় (`bad-payload`/`rate`/`assist-off`) — helper-এর UI কখনো মিথ্যা বলবে না।
  p2p চ্যানেল per-round-by-construction (epoch-এর ডাল নেই), তাই সেখানে size+sanity যথেষ্ট।

### 33.4 rules টাইটেনিং + লাইভ ডিপ্লয়
- `fcmTokens`: uid/deviceId/token **type-check** + token size 32–4096 (আগে শুধু নিম্নসীমা ছিল — 10KB টোকেন-স্ট্রিংও
  ঢুকে যেত)। আসল ৩ টোকেন (১৪২ অক্ষর) নতুন সীমায় অক্ষত।
- `publish-firestore-rules.mjs`-এ SA-পাথ Phase-8.2-কনভেনশনে ফিক্স (env → home → project .secrets); নতুন ruleset
  **fc6d8ec0-5f81-46b6-a194-5531a145600a** লাইভ + readback-verify ✓ (৩৬১ লাইন)।

### 33.5 Secure-logging অডিট (শূন্য-পরিবর্তন, শুধু প্রমাণ)
- সব সোর্স জুড়ে grep: **`android.util.Log` = ০ কল** — অ্যাপ কোনো সিস্টেম-লগেই কিছু লেখে না। একমাত্র ডায়াগ
  ইন-অ্যাপ ১৪-লাইন রিং-বাফার (ScreenShareEngine.diagLog), যা ডিভাইস ছাড়ে না এবং টোকেন/ইমেইল কখনো ধারণ করে না
  (কমান্ড-প্রুফ লাইনে শুধু শতকরা-স্থানাঙ্ক)। pairing টোকেন SHA-256-হ্যাশড (80-bit SecureRandom), FCM টোকেন
  owner-private — এই তিনটাই লগ-সারফেস নিশ্চিত।

### 33.6 থ্রেট-মডেল ডেল্টা (§13 রিস্ক-রেজিস্টারের পরিপূরক)
| হুমকি | আগের অবস্থা | এখন |
|---|---|---|
| ভুল/দুর্বল rules-সংস্করণ লাইভ থেকে যাওয়া | কেবল চোখে-পড়া রিভিউ | runtime probe ১২-পথ প্রমাণ + পুনর্চালনযোগ্য |
| একই রাউন্ডে বাসি `cmd` পুনঃপ্রদান | epoch-only | epoch + ১৫সে freshness |
| বিশাল/ম্যালফর্মড কমান্ড-পেলোড | rules-সীমা 100KB | 4KB দুই ট্রান্সপোর্টে + finite/clamp |
| জেসচার-ফ্লাড (গ্রহীতার ফোন অস্বচ্ছল) | সীমাহীন | ২৫/সে ক্যাপ |
| টোকেন-রেজিস্ট্রি অপব্যবহার | শুধু ≥32 | + ≤4096 + টাইপ-চেক |
| লগ-ফাঁস | (ডিজাইনে নেই) | grep-প্রমাণিত শূন্য Log-কল |

### 33.7 বাস্তবায়ন-নোট
- সংশ্লিষ্ট ফাইল: SignalingRepo.kt (createdAt প্যাস), ScreenShareEngine.kt (guard-ব্লক, sane/saneDur,
  sendAssistAck রিফ্যাক্টর, p2p size-cap, teardown রিসেট), firestore.rules, build.gradle.kts (28/0.10.0)।
- probe-নির্মাণে ধরা পড়া বাগ: এই প্রজেক্টের DB-আইডি `(default)` নয় `default`; runQuery রেসপন্স অ্যারে-মোড়ানো —
  দুটোই এখন স্ক্রিপ্টে মন্তব্যসহ নথিভুক্ত।
- CI: push → Android Build ×৩ কিউ (প্রতি কমিটে); চূড়ান্ত head 2269d83।
- Owner-করণীয়: দুই ফোনে v0.10.0 ইনস্টল; আচরণে কোনো পরিবর্তন দেখা যাবে না (সব guard অদৃশ্য) — স্বাভাবিক
  ট্যাপ/সোয়াইপ/লং-প্রেস আগের মতোই চলবে।

### 33.8 পরবর্তী
- Phase 10 (রোডম্যাপ): Guest মোড — মেয়াদ, session-scoped auth, guest≠family।
- ব্যাকলগ (অপরিবর্তিত): টাইপিং (canRetrieveWindowContent=true Owner-সিদ্ধান্ত), kill-push-এর স্থায়ী-ইনস্ট্যান্ট
  স্তর (Cloudflare Workers/Blaze), রিলে পেইড-সিদ্ধান্ত, টোকেন-rotate, Oracle TURN।

## 34. Phase 8.3-সমাপ্তি (v0.12.0, versionCode 30) — টাইপিং + Data saver (২০২৫-১০-২৯)

বই-রাখার নোট: v0.11.0 (versionCode 29, Phase 10 — Guest মোড) এই ডকে আলাদা সেকশন পায়নি;
তার পূর্ণ নকশা-ব্যাখ্যা ও প্রমাণ worklog Task 41-এ আছে (ruleset 5f1e6b43…, probe ২৩/২৩,
CI 40826687092, APK AXML code-29-verified)।

### 34.1 কেন এবার
§31.5-এ Phase 8.3 দুই কিস্তিতে ভাগ হয়েছিল — v0.9.9 (code 27)-এ সোয়াইপ + লং-প্রেস গিয়েছিল;
টাইপিং স্থগিত ছিল কারণ IME-টেক্সট-বসানো (ACTION_SET_TEXT) দিয়ে যেতে হলে
canRetrieveWindowContent=true লাগে — সেটা ছিল Owner-সিদ্ধান্ত (Play-policy প্রশ্নসহ)।
Owner এই সেশনে "হ্যাঁ শুরু করো" বলায় স্থগিত দুই অংশ এলো: (১) টাইপিং, (২) কম-বিটরেট
Data saver মোড।

### 34.2 টাইপিং — কীভাবে কাজ করে
1. **Helper পাশে**: Touch control সেকশনে নতুন "⌨ Type text · লেখা টাইপ করুন" বোতাম →
   সৎ দ্বিভাষিক ডায়ালগ (৫০০-অক্ষর ক্যাপ; স্পষ্ট লেখা: লেখাটি ঘরের **আগের লেখা বদলে বসবে**,
   আগে রিমোট-টাচে ঘরে ট্যাপ করতে হবে) → `kind:"text"` কমান্ড — একই epoch-gated cmd চ্যানেল
   (p2p DataChannel আগে, Firestore ফলব্যাক), JSONObject-নির্মিত payload তাই উদ্ধৃতি/নতুন-লাইন/ইমোজি
   সবই নিরাপদ।
2. **Device পাশে**: `AssistService.typeTextStatus()` — ফোকাসড editable ঘর খোঁজে:
   আগে rootInActiveWindow, না পেলে **প্রতিটি retrievable window** (application আগে) — কারণ
   রিমোট কিবোর্ড খোলা থাকলে IME নিজেই "active window" হতে পারে আর IME-তে editable ঘর থাকে না।
   Traversal **400-node বাজেটে বাউন্ডেড DFS** — বিশাল tree-তেও কমান্ড ack-latency বাজেটের ভেতরে থাকে।
   পাওয়া গেলে `ACTION_SET_TEXT`; না গেলে সৎ "no-field"।
3. **চার-রকম সৎ ফল**: "ok" | "assist-off" | "no-field" | "failed" — প্রতিটা helper-এর
   ব্যানারে নিজের বার্তা নিয়ে নামে: "Text delivered ✓ · লেখা বসেছে" / "No focused text field
   — আগে ঘরে ট্যাপ করুন…" / "ওদের ফোন লেখা নিতে রাজি হয়নি (ঘর সুরক্ষিত)" / assist-off ব্যানার।
   টেক্সট-ব্যর্থতা গ্রহীতার "dropped taps" অ্যালার্ম গোনে না — অ্যালার্মের ভাষা সত্য থাকে।
4. **গোপনীয়তা-ইনভ্যারিয়েন্ট**: ডায়াগ লগে টেক্সটের দৈর্ঘ্য যায়, কনটেন্ট কখনো নয়।

### 34.3 বিশ্বাস-মডেল হালনাগাদ (যেখানে ব্যবহারকারী তাকান সেখানেই সৎ)
- `canRetrieveWindowContent` এখন **true** — কিন্তু **একটাই consumer**: ফোকাসড ঘর-খোঁজা।
  সেই খোঁজা capability-ফ্ল্যাগ (isEditable/isFocused) পড়ে, node-টেক্সট কখনো পড়ে না;
  কোডে এমন কোনো API নেই যা স্ক্রিন-কনটেন্ট বাইরে বের করতে পারে; কিছু লগ হয় না, জমা থাকে না।
- কপি আপডেট হয়েছে চার জায়গায় — res/xml/assist_service_config.xml কমেন্ট, সিস্টেম
  service-description (ইংরেজি), গ্রহীতার assist-কার্ড, সেটআপ-গাইড (বাংলা): "টাইপ করার জন্য
  সার্ভিস ফোকাসড লেখার-ঘরটি খুঁজে পায়, কিন্তু অ্যাপ স্ক্রিনের কোনো লেখা পড়ে না, জমা রাখে না,
  পাঠায় না"।
- Play-policy নোট: AccessibilityService API-র ব্যবহার-ঘোষণায় এখন "performs gestures AND
  places text into the focused field at the user's direction" — সেবা বন্ধ করলেই সব বন্ধ,
  আগের মতোই।

### 34.4 Data saver (helper-টগল, কম-বিটরেট মোড)
- হেল্পারের ভিডিও-প্যানেলে "Data saver · ছবি হালকা" সুইচ → `kind:"qcap"` কমান্ড।
- ON ⇒ সেন্ডার-বাজেট পুনঃপ্রয়োগ, হার্ড **৬০০ kbps** সিলিং (রিলে-অটোক্যাপ ১৫০০-র নিচে;
  পথ-নিরপেক্ষ — ডিরেক্ট সেশনেও হেল্পার চাইলে ক্যাপ বসবে, মিটার্ড ডেটা দুই প্রান্তেই মিটার্ড)।
  OFF ⇒ স্বাভাবিক বাজেট (রিলে-পথে ১৫০০ ক্যাপ ফেরে)। মধ্য-সেশনে প্রয়োগ — ক্যাপচার রিস্টার্ট নেই।
- গণিত: `capK = min(relayMaxKbps, dataSaverKbps)` — দুই ক্যাপ-উৎসের ন্যূনতম জয়ী; দুটোই
  রাউন্ড-শেষে রিসেট। একই সৎ-ack: "Data saver applied ✓"।
- ডায়াগ-প্রমাণ: "bitrate 1200k–600k · … · capped (data-saver)" / "data saver ON — 600 kbps cap applied"।

### 34.5 Owner-করণীয় (পরীক্ষা)
1. দুই ফোনে **v0.12.0 (versionCode 30)** ইনস্টল (পুরনোর উপরেই বসবে)।
2. **টাইপিং-সুখ**: হেল্পার: Touch control ON → গ্রহীতার ফোনে যেকোনো সার্চ/মেসেজ-ঘরে
   রিমোট-ট্যাপ (কিবোর্ড খুলবে) → "⌨ Type text" → কিছু লিখে Send → ঘরে লেখা বসবে +
   "Text delivered ✓ · লেখা বসেছে"।
3. **no-field সততা**: ঘরে ট্যাপ না করেই Type text → "No focused text field… আগে ঘরে ট্যাপ করুন"
   বার্তা — এটাই সঠিক আচরণ।
4. **Data saver**: লাইভ ভিডিওতে ON → ছবি একটু ঝাপসা + ডায়াগ-লগে "capped (data-saver)";
   OFF → আগের তীক্ষ্ণতা ফেরে। রিলে-পথে ON করলে ডেটা-খরচ দৃশ্যমানভাবে কমবে।
5. **মিশ্র-সংস্করণ সততা**: গ্রহীতার ফোনে পুরনো অ্যাপ (v0.11.x) থাকলে text/qcap কমান্ডের উত্তর
   আসবে "unknown-kind" — helper জানবে কিছু হয়নি (নিঃশব্দ-ব্যর্থতা নেই); দুই ফোনেই নতুন
   ভার্সন দিয়েই পূর্ণ সুবিধা।

### 34.6 পরবর্তী
- ব্যাকলগ (অপরিবর্তিত): kill-push-এর স্থায়ী-ইনস্ট্যান্ট স্তর (Cloudflare Workers/Blaze —
  Owner-সিদ্ধান্ত), রিলে পেইড-সিদ্ধান্ত, টোকেন-rotate, Oracle TURN, Phase 11 UI/UX polish।
- Owner-ফোন-টেস্ট সফল হলে পরের ধাপ হবে §34.6-র প্রথম ব্যাকলগ-আইটেম।

---

## 35. Phase 11 (v0.13.0, versionCode 31) — UI/UX পলিশ: ৪-ট্যাব নেভিগেশন শেল (২০২৫-১০-২৯)

Owner-নির্দেশ (স্ক্রিনশট v0.12.0-এর ভাঙা লেআউট দেখিয়ে): **"বটম এরিয়াতে ৪টি বাটন — হোম, সেটিংস
ও অন্যান্য; উপরে নোটিফিকেশন আইকন + একটিভিটি।"** অর্থাৎ এতদিনের এক-লম্বা স্ক্রল-কলাম ভেঙে
আসল মোবাইল-অ্যাপ গঠনে যাওয়া।

### 35.1 সমস্যা (স্ক্রিনশট-প্রমাণিত)
1. সব সেকশন এক স্ক্রলে — সিনিয়র ব্যবহারকারীর জন্য খোঁজা কঠিন, গুরুত্বপূর্ণ কার্ড নিচে হারিয়ে যায়।
2. `StatusRow` = SpaceBetween + দুই unweighted Text → লম্বা মানে লেবেল-মান জুড়ে যায়
   ("Package com.family…", "অনলাইনOnline ✓") বা এক-অক্ষর-প্রতি-লাইন উল্লম্ব হয়ে যায়।
3. যুক্ত-ফোন রো = Column(weight) + পাশে Revoke pill → কলাম চেপে যায়, "last seen" উল্লম্ব
   অক্ষরে পরিণত হয়, Start-session বোতাম ডিম্বাকৃতি হয়।
4. ডিভাইস-মডেল + PresenceBadge এক unweighted Row-তে — সংকীর্ণ জায়গায় ব্যাজ পিষ্ট হয়।

### 35.2 নতুন গঠন
- **TopAppBar**: অ্যাপ-নাম + প্রেজেন্স-চিপ (সবুজ ডট = অনলাইন) + **bell IconButton**
  (REQUESTED অনুরোধ থাকলে badge-dot; চাপলে একটিভিটি ট্যাবে)।
- **NavigationBar — ৪ ট্যাব (≥48dp, বাংলা লেবেল, icons-core আইকন)**:
  | ট্যাব | আইকন | কনটেন্ট |
  |---|---|---|
  | হোম | Home | শুভেচ্ছা+ভার্সন, **গ্রহীতা-সম্মতি কার্ড (সবার উপরে — REQUESTED হলেই)**, পারমিশন কার্ড+উইজার্ড, এই-ফোন স্ট্যাটাস, দ্রুত-অ্যাকশন |
  | ফোন | Phone | PairingCard পূর্ণ: কোড-তৈরি/স্ক্যান/Redeem + যুক্ত-ফোন + সেশন-অ্যাকশন |
  | একটিভিটি | Notifications | সেশন-ফিড (লাইভ/শেষ-সেশন সৎ-সারসংক্ষেপ) + যুক্ত-ফোনের প্রেজেন্স তালিকা |
  | সেটিংস | Settings | অ্যাকাউন্ট, About (পারমিশন কার্ড হোম ট্যাবেই — এক জায়গায়) |
- PermissionWizard ডায়ালগ HomeScreen-লেভেলে থাকবে (ট্যাব-বদলে বন্ধ হবে না)।
- গ্রহীতার Allow/Deny এখন **হোম-ট্যাবের উপরেই** — FCM নোটিফিকেশনে চেপে অ্যাপ খুললে
  ডিফল্ট ট্যাবেই সম্মতি কার্ড চোখের সামনে।

### 35.3 সততা-সিদ্ধান্ত
- Activity ট্যাব শুধু প্রমাণিত ডেটা দেখায়: watchSessions (OPEN-সেট) + watchRelationships;
  বন্ধ-সেশনের এক-শট `getSession` ফেচ। কোনো বানানো history নেই — ভবিষ্যতে আসবে।
- bell-badge নিজস্ব হালকা watchSessions-লিসেনার (badge-এর জন্য); Activity ট্যাব নিজের
  লিসেনার চালায় — দুটোই rules-বাউন্ড, ক্যাপড-কোয়েরি, খরচ নগণ্য।
- ডিজাইন-ভাষা অপরিবর্তিত: FamilyWarm রঙ, SeniorTypography, দ্বিভাষিক লেখা।

### 35.4 Owner-করণীয় (পরীক্ষা)
1. দুই ফোনে **v0.13.0 (versionCode 31)** ইনস্টল।
2. নিচে ৪ ট্যাব দেখা যাচ্ছে? উপরে বেল-আইকন + সবুজ অনলাইন-চিপ?
3. গ্রহীতার ফোনে হেল্পার কোড দিলে → অনুরোধ এলে **হোম-ট্যাবের উপরেই Allow/Deny কার্ড**?
4. যুক্ত-ফোন তালিকায় আর উল্লম্ব-অক্ষর/ডিম্ব-বোতাম নেই তো? Revoke এখন পূর্ণ-প্রস্থ নিচে?
5. সেটিংসে Sign out / পারমিশন চেক / About বিস্তারিত সব আগের মতো কাজ করছে?

## 36. Phase 11.5 (v0.14.0, versionCode 32) — কম-টেক্সট, কম্পোনেন্ট-ভিত্তিক UI (২০২৫-১০-২৯)

### 36.1 কেন (Owner-নির্দেশ, স্ক্রিনশট-প্রমাণিত)

Owner ৬টি স্ক্রিনশট পাঠিয়ে জানালেন: "App এর মধ্যে এত কিছু লেখা কেনো" — প্রতিটি ট্যাবে
বড় বড় ব্যাখ্যা-প্যারাগ্রাফ, ফিচারগুলো আইকন-চিহ্নিত গোছানো রো-তে নেই। চাহিদা:
Chat-style **List Tile** (avatar + নাম + সাবটেক্সট + ব্যাজ), **FAB**, **Toggle Switch**,
**Description sub-text**, **পপআপ/dropdown** — কোনটা কোথায় হবে ডিজাইনার-সিদ্ধান্ত।

### 36.2 নতুন রিইউজেবল কম্পোনেন্ট (MainActivity.kt)

| কম্পোনেন্ট | কোথায় | কেন |
|---|---|---|
| `SectionCard(title, icon, infoText?)` | সব ট্যাব | আইকন-শিরোনাম; **বড় ব্যাখ্যা ⓘ-পপআপে** (AlertDialog) — কার্ডের ভেতরে শুধু কাজের জিনিস |
| `AppListTile` (avatar/initials + title + subtitle + chip + chevron) | যুক্ত-ফোন, ডিভাইস-তালিকা, একটিভিটি, অ্যাকাউন্ট | Chat-item-স্টাইল রো — প্রোফাইল-চিহ্ন + নাম + সাব-টেক্সট + ব্যাজ |
| `SettingsTile(icon, title, description, trailing)` | সেটিংস | অপশনের নিচে **ছোট বিবরণ** + ট্রেইলিং কন্ট্রোল (সুইচ/বোতাম/শেভরন) |
| `IconStatusTile` ২×২ গ্রিড | হোম | পারমিশন/এই-ফোন/অনলাইন/নোটিফিকেশন — এক নজরে; ট্যাপ = অ্যাকশন |
| `QuickActionTile` | হোম | দ্রুত-অ্যাকশন ২টি (ফোন যুক্ত / একটিভিটি) |
| `EmptyState(icon, title, hint, action?)` | খালি তালিকা | বিশাল ব্যাখ্যার বদলে আইকন + এক লাইনে পথনির্দেশ |
| `ExtendedFloatingActionButton "যুক্ত করুন"` | ফোন-ট্যাব | পেয়ারিং **নিচ-শিট** খোলে |
| `ModalBottomSheet` ×২ | ফোন-ট্যাব | (১) পেয়ারিং: Family/Guest বড় অপশন + কোড-লেখা + স্ক্যান; (২) যুক্ত-ফোন অ্যাকশন (সেশন শুরু/Revoke/Remove) |
| `DropdownMenu ⋮` | কোড-কার্ড | কপি / QR সেভ / শেয়ার |

### 36.3 ট্যাব-প্রতি পরিবর্তন

- **হোম**: দুই-লাইন ট্যাগলাইনের বদলে এক লাইন; ২×২ স্ট্যাটাস-গ্রিড; ব্যর্থতাগুলো একটাই
  কম্প্যাক্ট এরর-কার্ডে (টাইলগুলো ছোট-পরিষ্কার); ডিভাইস-তালিকা List Tile; ভার্সন নিচে ছোট।
- **ফোন**: সেশন-কার্ড কম্প্যাক্ট (আইকন + শিরোনাম + কাউন্টডাউন চিপ); চলমান কোড-কার্ডে
  dropdown; যুক্ত ফোন = List Tile (Family/Guest+বাকি-সময় ব্যাজ, presence নিচে), ট্যাপে
  অ্যাকশন-শিট; FAB → পেয়ারিং-শিট; নোটিফিকেশন-পারমিশন জিজ্ঞাসা সেটিংসে সরেছে।
- **একটিভিটি**: লাইভ সেশন / শেষ সেশন / যুক্ত ফোন — প্রতিটি আইকন-টাইল; খালি অবস্থায়
  EmptyState; ব্যাখ্যা ⓘ-তে।
- **সেটিংস**: অ্যাকাউন্ট List Tile + সাইন-আউট (ExitToApp আইকন); **নোটিফিকেশন = আসল
  টগল সুইচ** (অবস্থা সিস্টেম থেকে ১.৫সে-পোল; চালু = রানটাইম পারমিশন, বন্ধ = সিস্টেম
  সেটিংসে পাঠায় — সৎ); Remote assist স্ট্যাটাস-টাইল + গাইড; About ডায়ালগে টেকনিক্যাল
  বিবরণ।

### 36.4 সততা-সিদ্ধান্ত

কোনো নতুন ফেক-সেটিং নেই — টগল দুটির অবস্থা আসল সিস্টেম-স্টেট থেকে পড়া হয়; বানানো
history আগের মতোই নেই; সব Firestore-লজিক/রুলস অপরিবর্তিত; সেশন-অনুমতি প্রতিবার গ্রহীতার
কাছেই যায়। ব্যাখ্যা-টেক্সট মোছা হয়নি — ⓘ-পপআপে সরানো হয়েছে।

### 36.5 Owner-করণীয় (পরীক্ষা)

1. দুই ফোনে **v0.14.0 (versionCode 32)** ইনস্টল।
2. হোম-ট্যাবে ২×২ টাইল-গ্রিড + দ্রুত-অ্যাকশন দেখা যাচ্ছে? ⓘ চাপলে ব্যাখ্যা-পপআপ?
3. ফোন-ট্যাবে ডানদিকে **"যুক্ত করুন" FAB** → নিচ-শিটে Family/Guest → কোড-কার্ডে ⋮ dropdown?
4. যুক্ত-ফোন রো ট্যাপ করলে অ্যাকশন-শিট (সেশন শুরু/Revoke/Remove)?
5. সেটিংসে নোটিফিকেশন **সুইচ** টগল করলে পারমিশন-জিজ্ঞাসা/সিস্টেম-সেটিংস খোলে?

---

## 37. Phase 11.6 (v0.15.0, versionCode 33) — Design System & Dark Mode ✅ বাস্তবায়িত (Owner-অনুমোদনে)

Owner-এর উন্নত UI/UX-ইন্টিগ্রেশন নির্দেশ (৫-কনসেপ্ট রেফারেন্স-ছবি সহ) অনুযায়ী Phase 11-এর
সমন্বিত সম্প্রসারণ। প্রথমে পরিকল্পনা প্রকাশিত হয় (docs/UI_UX_DESIGN_SYSTEM.md v1.0), Owner
"**11.6 শুরু করো deeply**" বলে §24-এর ৪টি সুপারিশ অনুমোদন করেন — তারপর সম্পূর্ণ বাস্তবায়ন।

### 37.1 পূর্ণ স্পেসিফিকেশন
- **docs/UI_UX_DESIGN_SYSTEM.md** (v1.1, বাস্তবায়ন-অবস্থাসহ) — ২৪-সেকশন ডিজাইন-সিস্টেম ডক।

### 37.2 বাস্তবায়িত সারসংক্ষেপ (v0.15.0)
- **ui/Theme.kt (নতুন)** = একমাত্র ডিজাইন-উৎস: LightColors (নীল #246BFD) + DarkColors
  (পূর্ণ ডার্ক স্কিম, AA-নীল #6C9BFF) + SeniorTypography + AppShapes + Spacing +
  ThemePreview + AppWarningColor + FamilyRemoteSupportTheme + ThemePrefBus।
- **আসল চেহারা-সেটিং**: Settings-এ সিস্টেম/লাইট/ডার্ক (SharedPreferences-persist;
  onCreate-এ sync-load — প্রথম ফ্রেমেই সঠিক থিম, ফ্ল্যাশ নেই)।
- **StatusBadge** (রঙ+আইকন+লেখা): সম্মতি-কার্ড (এখন primaryContainer + mergeDescendants
  semantics), একটিভিটি-ফিড চিপ, QR ইনভাইট-অবস্থা।
- **QR পলিশ**: 200dp সাদা-তলে (দুই থিমেই স্ক্যানযোগ্য), শেষ-৩০-সেকেন্ডে warning-রঙ।
- **Guest চিপ** <১৫ মিনিটে warning-রঙ; **friendlyErrorBn** (নেটওয়ার্ক/অনুমতি = মানুষ-ভাষা)।
- **উইজার্ড**: LinearProgressIndicator + ধাপ-বৃত্ত (done→✓) + সাফল্য-রো।
- **হোম সাইন-ইন-আমন্ত্রণ-কার্ড** (signed-out-এ) — শেয়ার্ড startSignIn।
- **অ্যাক্সেসিবিলিটি/রেসপন্সিভ**: ⓘ 36→48dp, প্রতি-ট্যাব স্ক্রল-অবস্থা, পেয়ারিং-শিটে
  imePadding, থিম-সচেতন window-background (day+night), ২টি ভেক্টর ইলাস্ট্রেশন।
- **সততা অটুট**: কোনো ফেক-ডেটা/ফেক-সেটিং নেই; FamilyWarmColors-এর অ্যাম্বার secondary-
  অ্যাকসেন্টে সংরক্ষিত; সব Firestore-লজিক/রুলস/FCM/WebRTC অপরিবর্তিত।

### 37.3 যাচাই-প্রমাণ
- string/comment-aware brace-lexer: braces/parens/brackets 0/0/0 (৩ ফাইল); ডুপ্লিকেট-
  import/ফাংকন-নাম শূন্য; MainActivity-তে Color(0x…) লিটারেল **শূন্য**।
- push: ৯ PUT (gradle a6c1af7 → MainActivity 74b7ebc → PW 06dbe72 → Theme 4cde7aa →
  colors d480cc6 → themes 3af3203 → night 724fe7f → devices f80946c → activity 50cb44c);
  readback-verify **9/9 MATCH** (sha256)।
- CI: ফাইনাল রান (head 50cb44c) → SUCCESS; APK-AXML: versionCode INT_DEC 33 ✓,
  versionName "0.15.0" ✓, stale-স্ট্রিং-প্রোব শূন্য ✓, Signing Block v2 ✓।
- APK সংরক্ষিত: download/family-remote-support-v0.15.0-code33.apk।

### 37.4 Owner-করণীয় (§22-চেকলিস্ট)
দুই ফোনে **v0.15.0 (code 33)** ইনস্টল করে: (১) সিস্টেম-ডার্ক-মোডে অ্যাপ ডার্ক + সেটিংসে
তিন অপশন কাজ করছে? (২) দুই থিমেই হোম/ফোন/একটিভিটি/সেটিংস/QR পড়া যায়? (৩) সেশন-অনুরোধে
নীল সম্মতি-কার্ড + ব্যাজ? (৪) QR বড়-সাদা-তলে স্ক্যান হয়? (৫) পুরনো সব ফ্লো অপরিবর্তিত?
রিপোর্ট দিলে Phase 11 বন্ধ হবে; Phase 12 শুধু পরবর্তী স্পষ্ট নির্দেশে।

## §38 — Phase 11.7 (v0.16.0 / versionCode 34): AuthGate — আলাদা লগইন/সাইন-আপ পেজ

**উৎপত্তি:** Owner-এর সরাসরি নির্দেশ — (ক) Phase 0→11 পূর্ণাঙ্গ অডিট (backend-frontend
জুক্তি-সংগতি), (খ) "লগিং পেজ টি আলাদা হবে — নতুন ইউজার হিসাবে লগিং ও সাইন আপ পেজ থাকবে",
(গ) "প্রত্যেকটি ডিজাইন আমার দেওয়া অ্যাপ প্রম্পট এর মত করে দেন" → UI_UX_DESIGN_SYSTEM.md
§24-সিদ্ধান্ত-৪ সুপারসিডেড, §25-এ AuthGate-স্পেসিফিকেশন।

### 38.1 অডিট-সারসংক্ষেপ (৩-সমান্তরাল তদন্ত)
- **ফেজ-ম্যাট্রিক্স:** Phase 0→11.6 সব শিপড-প্রমাণিত (push+CI+APK-AXML); সতর্কতা — Phase 7-এর
  ফরমাল-ক্লোজ নেই (প্রমাণ আপোষ্য: Task 32/34), Phase 8-টাচ-কন্ট্রোলের Owner-ফোন-সফলতা রেকর্ডে
  নেই, Phase 10-এর ডক-সেকশন ছিল না, Phase 11-এর ক্লোজ §37.4-অপেক্ষায়।
- **কোড↔rules:** ৭-কালেকশন মিল; সব অ্যাক্সেস Fb.db (database-id "default") হুবহু; সিগন্যাল ৭-টাইপ
  rules-whitelistে; FCM channel_id = `support_session_requests` manifest↔sender মিল;
  Manifest↔কোড নির্দোষ।
- **চিহ্নিত ফাঁক:** FirebaseMessagingService নেই (নোটিফিকেশন-মেসেজ ডিজাইন — সচেতন), সাইন-আউটে
  devicePresence/fcmTokens মুছে না, App Check প্রতিশ্রুত-কিন্তু-অনুপস্থিত, verify-apk-axml CI-তে
  নেই (Owner-লোকাল গেট), kill-push instant-tier sandbox-বাঁধা (Owner-সিদ্ধান্ত-বাকি)।

### 38.2 বাস্তবায়ন (v0.16.0)
- Firebase Email/Password প্রদানকারী চালু (identitytoolkit Admin API, PATCH → GET যাচাই
  `enabled:true, passwordRequired:true`); রোডম্যাপ §22.2-এর পূর্ণতা; **rules অপরিবর্তিত**।
- **ui/AuthGate.kt** (নতুন): পূর্ণ-স্ক্রিন গেট — হিরো ic_auth_hero + ট্যাগলাইন + আস্থা-চিপ +
  লগইন/সাইন-আপ পিল-টগল + নাম/ইমেইল/পাসওয়ার্ড/নিশ্চিতকরণ (inline-ত্রুটি, দেখুন-লুকান) +
  পাসওয়ার্ড-রিসেট + Google-বিকল্প + ট্রাস্ট-ফুটার; সব টোকেন-ভিত্তিক (ডার্ক-মোড-প্রস্তুত)।
- **MainActivity.kt:** `signInWithEmailPassword`/`signUpWithEmailPassword`; AuthGate-বাইন্ডিং
  (early-return গেট); authMessage/authNotice স্টেট; friendlyErrorBn-এ ৬ Auth-ম্যাপ; উইজার্ড
  অটো-ওপেন সাইন-ইন-গেটেড; ডেড হোম-সাইন-ইন-কার্ড অপসারণ; Settings/Pairing কপি আপডেট।
- build.gradle.kts: versionCode **34**, versionName **"0.16.0"**; পুশ-তালিকায় AuthGate.kt +
  ic_auth_hero.xml যোগ।
- যাচাই: lexer net-0 (mismatch-প্যাটার্ন HEAD-এও বিদ্যমান — false-positive), ডুপ্লিকেট-import শূন্য,
  XML valid; পুশ readback-verify, CI, APK-AXML (code 34) — নিচের প্রকাশনা-রেকর্ড।

### 38.3 Owner-করণীয়
নতুন ফোনে অ্যাপ খুলে: (১) লগইন/সাইন-আপ পেজ দেখা যায়? (২) ইমেইল-সাইন-আপ → ডিভাইস-রেজিস্ট্রেশন →
৪-ট্যাব; (৩) ভুল পাসওয়ার্ডে বাংলা-ত্রুটি বার্তা? (৪) রিসেট-লিংক ইমেইলে আসে? (৫) Google-বোতামও
কাজ করে? (৬) সাইন-আউটে আবার লগইন-পেজ?

## §39 — Phase 12 (v0.17.0 / versionCode 35): রোল-টাইপড QR ও স্ক্যান→সেটআপ (Owner-প্রশ্নের উত্তর)

**উৎপত্তি:** Owner-এর সরাসরি প্রশ্ন — "দুইটি মোড (Family/Guest) ইউজার কিভাবে কন্ট্রোল করবে?
গেস্ট এসে ফ্যামিলিতে ঢুকে গেলে বা অপরিচিত বাইরের লোক বিপদে ফেললে তো হলো না। Family ও Guest-এর
QR জেনারেট ও স্ক্যানারে পরিবর্তন চাই, রোল-সেটআপ চাই, QR-এর মাঝখানে ছোট প্রতীক চাই, OWNER
স্ক্যান করার পর যেনো অটোমেটিক Guest/Family সেটআপে নিয়ে যেতে পারে।"

### 39.1 অডিট-ফলাফদ (স্ক্যান-পথের ৪টি ফাঁক, নিরাপত্তা-বেড়া অক্ষত)
- **QR-পে-লোডে রোল নেই:** পুরনো QR-এ শুধু inv+tok — স্ক্যানকারী ফোন redeem-এর *আগে* জানতই না
  এটি Family নাকি Guest কোড।
- **redeem-আগে রোল-প্রিভিউ নেই:** স্ক্যান → টেক্সট-ফিল্ড → এক বোতামে যুক্তি — কোনো নিশ্চিতকরণ ধাপ নেই।
- **QR-এ কোনো পরিচয় নেই:** দুই ধরনের কোড দেখতে হুবহু এক (কালো-সাদা, প্রতীকহীন)।
- **স্ক্যান-পরবর্তী পথ ম্যানুয়াল:** স্ক্যানের পরে নিজে ফিরে এসে বোতাম চাপতে হয় — অটোমেটিক সেটআপ নেই।
- **অক্ষত বেড়া (কোনো rules-পরিবর্তন লাগেনি):** kind সার্ভার-ডকে ফ্রোজেন, guest≠family লেনদেনে+rules-এ
  নিখুঁত, guest-উইন্ডো server-bounded (≤৩ঘ), invitation PENDING+৫মিনিট+tokenHash একবারই।

### 39.2 বাস্তবায়ন (v0.17.0) — সব ক্লায়েন্ট-সাইড, rules অপরিবর্তিত
- **PairingRepo.kt:** (ক) `InviteCode.kind` + QR-পে-লোডে রোল-মার্কার `&k=fam`/`&k=gst` (cosmetic —
  চূড়ান্ত রোল সবসময় লেনদেনের ভিতরে Firestore-থেকেই; ভুয়া QR রোল বদলাতে পারে না); (খ) `QrBadge`
  enum — QR-এর **মাঝখানে সাদা rounded-প্লেট + প্রতীক: 👪 Family / ⏳ Guest** (hasGlyph-fallback "F"/"G",
  বর্ডার #E2E8F0); (গ) `qrBitmap` এখন **ErrorCorrectionLevel.H** (~৩০% ক্ষতি-পুনরুদ্ধার — প্লেট-আচ্ছাদিত
  মডিউলও পড়া যায়), সাইজ ৫৬০।
- **QrScanner.kt:** (ক) `parsePairingKindHint` — স্ক্যান থেকে রোল-মার্কার পড়ে; (খ) স্ক্যানার UI বাংলায় —
  **ভিউফাইন্ডারের মাঝে rounded রেটিকল** (মাঝ স্বচ্ছ — কোডের প্রতীক দেখা যায়) + নিচে দুটি লেজেন্ড-চিপ
  "👪 Family · স্থায়ী / ⏳ Guest · ২ ঘণ্টা" (রঙ+প্রতীক+শব্দ — শুধু রঙ নয়); (গ) `onCode` এখন (code,
  kindHint) দেয়।
- **MainActivity.kt:** (ক) **ScannedRoleSheet** — স্ক্যান হলেই খোলে: PREVIEW (কোডটি কী রোল দেবে —
  রঙিন কার্ড + ৩-বুলেট অনুমতি-তালিকা; পুরনো কোডে "যাচাই করা হবে") → CONFIRM (না চাপলে কিছুই লেখা
  হয় না) → **AUTO-SETUP** (redeem চলে, শিট ঘুরে SUCCESS — প্রকৃত রোল-ব্যাজ + "এখন যা পারবেন" +
  "ঠিক আছে"); (খ) redeem-লজিক এক `doRedeem`-এ রিফ্যাক্টর — টাইপড-কোড ও স্ক্যান-শিট দুইটিই এক পথে;
  (গ) লাইভ-কোড কার্ড রোল-ব্র্যান্ডেড (আইকন+টিন্ট রোল-রঙ; QR-এ প্রতীক) + PAIRED-লাইনে রোল-চিপ
  ("… যুক্ত হয়েছে ✓ — Guest · ২ ঘণ্টা / Family · স্থায়ী"); (ঘ) যুক্ত-ফোন অ্যাকশন-শিটে রোল-সারসংক্ষেপ
  ("রোল: GUEST — সাময়িক / FAMILY — স্থায়ী বিশ্বস্ত ফোন"); (ঙ) পেয়ারিং-শিটে QR-প্রতীক-লেজেন্ড লাইন।
- **রোল-রঙ (ডিজাইন-সিস্টেম §4):** Family = tertiary সবুজ (স্থায়ী/বিশ্বস্ত), Guest = warning অ্যাম্বার
  (সাময়িক) — দুই থিমেই AA।
- build.gradle.kts: versionCode **35**, versionName **"0.17.0"**।
- **Owner-প্রশ্নের উত্তর (কন্ট্রোল):** নতুন ফোন যুক্ত করার সময়ই রোল ঠিক হয় — FAB → "Family কোড"
  (স্থায়ী, শুধু পরিবার) বা "Guest কোড" (২ ঘণ্টা, বাইরের লোক; মেয়াদ শেষে নিজেই মুছে যায়)। কোডের
  মাঝের প্রতীকই রোল চেনায়; স্ক্যানকারীকে স্ক্যানের পরেই রোল-প্রিভিউ+নিশ্চিতকরণ দেখে যেতে হয়; Guest
  কখনো Family হতে পারে না (সার্ভার-ফ্রোজেন) — বিপদের পথ বন্ধ।
- যাচাই: string/comment-aware brace-lexer ৩ ফাইল 0/0/0, ডুপ্লিকেট-import শূন্য; পুশ readback-verify,
  CI, APK-AXML (code 35) — নিচের প্রকাশনা-রেকর্ড।

### 39.3 Owner-করণীয়
(১) Owner-ফোনে Guest কোড বানান → QR-এর মাঝে ⏳ দেখা যায়? (২) পারিবারিক ফোনে স্ক্যান → রোল-প্রিভিউ
শিট আসে? বাতিল চাপলে কিছু না হয়? (৩) নিশ্চিত চাপলে সাকসেস-ব্যাজে রোল দেখায়? (৪) Family কোডের
QR-এ 👪 আছে? (৫) পুরনো (v≤0.16) কোড স্ক্যানে "কোড যাচাই করা হবে" নিরপেক্ষ প্রিভিউ আসে?

### 39.4 প্রকাশনা-রেকর্ড (v0.17.0)
- পুশ: Contents-API per-file — মধ্যবর্তী রান fail (জ্ঞাত আর্টিফ্যাক্ট: QrScanner-MainActivity
  onCode-সিগনেচার ক্রস-নির্ভরতা per-file কমিটে), চূড়ান্ত গাছ **07f1574** CI **SUCCESS**
  (run 40868598783)।
- নির্মাণকালে ধরা-পড়া-শেখা বাগ: `apply{}`-ব্লকে `textSize = textSize` বাইরের `val textSize`-কেই
  shadow করত ("'val' cannot be reassigned" ×২) — লোকাল rename `glyphSize` (07f1574)।
- APK verify (artifact 12894822120): versionCode **INT_DEC 35** ✓, versionName **"0.17.0"** ✓
  (স্টেল 0.9.9/0.10.0 স্ট্রিং অনুপস্থিত), APK Signing Block v2 ✓ ("APK Sig Block 42")।
- download/family-remote-support-v0.17.0-code35.apk (39.5 MB) সংরক্ষিত।

## §40 — Phase 13 (v0.18.0 / versionCode 36): সাইন-আউট হাইজিন + CI রিলিজ-গেট

**উৎপত্তি:** Owner: "এর পরের কাজ কি? করো" — Phase 12 পর্যন্ত সব অনুরোধ পূর্ণ হওয়ায় অডিটের
চিহ্নিত ফাঁকগুলো (§38.1) থেকে Owner-সিদ্ধান্ত-ছাড়া দুটি actionable আইটেম বেছে নেওয়া হলো:
(১) সাইন-আউটে `devicePresence`/`fcmTokens` মুছে না — ফোন সাইন-আউটের পরেও পরিবারের চোখে
"Online" থাকে ও সেশন-অনুরোধের kill-push পেতে থাকে; (২) `verify-apk-axml` CI-তে নেই —
সংস্করণ-যাচাই ছিল কেবল ম্যানুয়াল লোকাল গেট।

### 40.1 ডিজাইন-সিদ্ধান্ত
- **rules অপরিবর্তিত:** দুই কালেকশনেই `delete: uid == mine` ও (fcmTokens) `list: uid == mine`
  আগে থেকেই আছে — ক্লিনআপ কেবল নিজের ডক মুছে; ১২/১২ নেগেটিভ-প্রোব অক্ষত।
- **fcmTokens ডক-আইডি = raw টোকেনের SHA-256-হ্যাশের প্রথম ২৪ hex** — টোকেন ছাড়া আইডি বের
  করা যায় না; তাই `whereEqualTo("uid", mine)` → deviceId-মিল ডকগুলো ডিলিট।
- **best-effort:** নেটওয়ার্ক-ব্যর্থতা কখনো সাইন-আউট আটকায় না — প্রতিটি ধাপ স্বাধীনভাবে ডিগ্রেড,
  ফল লগইন-স্ক্রিনে সৎ নোটিশে (authNotice)।
- **WorkManager ওয়ার্ক ইচ্ছাকৃতভাবে বাতিল হয়নি:** HeartbeatWorker সাইন-আউটে no-op
  (currentUser-null → Result.success()); KEEP-policy-এ পুনঃসাইন-ইনে নিজেই চালু — বাতিল করলে
  রিগ্রেশন (রি-সাইন-ইনে হার্টবিট বন্ধ) আসত, কারণ শিডিউলার-কলটি LaunchedEffect(Unit)-এ একবারই চলে।
- **CI-গেট drift-প্রমাণ:** স্ক্রিপ্ট expectations সরাসরি `app/build.gradle.kts` থেকে পড়ে
  (repo-root ও sandbox — দুই লেআউট ফলব্যাক), তাই workflow-যাচাই ও gradle কখনো ভিন্ন হতে পারে না।

### 40.2 বাস্তবায়ন
- **PresenceRepo.kt:** `deleteOwnPresenceAndToken(uid, deviceId): Boolean` — presence-ডক ডিলিট +
  নিজের টোকেন-তালিকা → deviceId-মিল ডক ডিলিট; CancellationException পুনঃনিক্ষেপ, বাকি সব
  স্বাধীন ডিগ্রেড; সততার জন্য Boolean রিটার্ন।
- **MainActivity.kt (onSignOut):** signOut()-এর **আগে** ক্লিনআপ (rules-এর signedIn-শর্ত);
  সাফল্যে authNotice: "সাইন-আউট সম্পন্ন — এই ফোনের উপস্থিতি ও পুশ-টোকেন সার্ভার থেকে মুছে
  ফেলা হয়েছে।"; ব্যর্থতায় সৎ নোটিশ (নেটওয়ার্ক সমস্যা + পরে সাইন-ইন–সাইন-আউটে পরিষ্কার হবে);
  presence/push স্ট্যাটাস "Signed out" — পুরনো "Online ✓" লেখা থেকে যাওয়ার অসততা বন্ধ।
- **scripts/verify-apk-axml.mjs (নতুন, zero-dep):** APK → AndroidManifest.xml (binary AXML)
  পার্স → versionCode INT_DEC + versionName মিল; `--stale` তালিকার পুরনো-সংস্করণ-স্ট্রিং
  অনুপস্থিতি; APK Signing Block v2 ("APK Sig Block 42", scheme-id 0x7109871a); EOCD পেছন-স্ক্যান
  (zip-comment-safe)। যাচাই: বাস্তব v0.17.0 APK → PASS (exit 0); --code 999 → FAIL (exit 1)।
- **android-build.yml:** build-এর পরে "Verify release APK (AXML versionCode + versionName +
  v2 signature)" ধাপ — `set -o pipefail`, ফলাফল `$GITHUB_STEP_SUMMARY`-তে; যেকোনো ✗-এ
  বিল্ড ব্যর্থ।
- **README:** Status-এ নিয়ে আসা ১১.৬→১৩ ফেজ-এন্ট্রি + Build-এ গেট-রেকর্ড; worklog-এ
  Task 41–44 ব্যাকফিল (convention-পুনরুদ্ধার, কোড-পরিবর্তন নেই) + Task 45।
- build.gradle.kts: versionCode **36**, versionName **"0.18.0"**; push-তালিকায়
  scripts/verify-apk-axml.mjs যোগ।

### 40.3 যাচাই
- brace-lexer: PresenceRepo 0; MainActivity HEAD-এর হুবহু জানা false-positive প্যাটার্নে
  (নতুন mismatch শূন্য); verify-স্ক্রিপ্ট `node --check` PASS; ডুপ্লিকেট-import শূন্য।
- verify-স্ক্রিপ্ট পজিটিভ+নেগেটিভ টেস্ট স্থানীয়ভাবে PASS।
- নিরাপত্তা: নতুন অথ/পথ শূন্য; সেশন/QR/WebRTC/FCM-প্রবাহে শূন্য পরিবর্তন।

### 40.4 Owner-করণীয় (যাচাই-চিহ্ন)
দুই ফোনে **v0.18.0 (code 36)** ইনস্টল করে: (১) Settings → সাইন-আউট চাপুন → লগইন-পেজে
"সাইন-আউট সম্পন্ন — উপস্থিতি ও পুশ-টোকেন মুছে ফেলা হয়েছে" নোটিশ দেখা যায়? (২) সাইন-আউটের
পরে অন্য ফোনে এই ডিভাইস "Online" না দেখিয়ে সঙ্গে সঙ্গে শেষ-দেখা-অবস্থায় যায়? (৩) সাইন-আউট-
করা ফোনে (অ্যাপ বন্ধ করে) হেল্পার সেশন-অনুরোধ পাঠালে আর push আসে না? (৪) আবার সাইন-ইনে সব
কিছু আগের মতো কাজ করে (presence, push, হার্টবিট)? — GitHub-এ "Android Build" রানের summary-তে
এখন "Release-APK verification (AXML gate)" টেবিল দেখা যাবে।

### 40.5 প্রকাশনা-রেকর্ড (v0.18.0)
- পুশ (Contents-API per-file, ONLY-ফিল্টার নতুন সুবিধা): verify-apk-axml.mjs **9cb2a00 (201,
  নতুন)**, workflow e9c5f90, gradle 8e90b1a, PresenceRepo fd448eb, MainActivity 4467b60 —
  readback-verify **5/5 MATCH** (sha256)।
- **CI রান 40872756514 (head fd448eb) → SUCCESS, প্রথম চেষ্টাতেই** — নতুন ধাপ
  "Verify release APK (AXML versionCode + versionName + v2 signature)" ⇒ success; CI-লগ-প্রমাণ:
  `expectations: code=36 name="0.18.0"` · `✓ versionCode = 36 (INT_DEC)` ·
  `✓ versionName = "0.18.0"` · `✓ APK Signing Block v2 present ("APK Sig Block 42")` ·
  `VERIFY: PASS` (ফলাফল run-summary-তেও গেটে যায়)।
- আর্টিফ্যাক্ট (12896309192) নেমে **স্থানীয়ভাবে** একই স্ক্রিপ্টে পুনঃযাচাই — স্টেল-প্রোব
  0.17.0/0.16.0/0.15.0 অনুপস্থিতসহ **VERIFY: PASS**; APK সংরক্ষিত
  download/family-remote-support-v0.18.0-code36.apk (39.5 MB)।
- ডক-পুশ (README + §40 + worklog Task 41–45) চূড়ান্ত গাছে — পরবর্তী CI রানও গেটসহ SUCCESS
  (নিচে সংযোজন হলে সেই রান-আইডি)।
