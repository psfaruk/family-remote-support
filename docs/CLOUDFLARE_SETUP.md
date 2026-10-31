# Cloudflare Workers সেটআপ — kill-push স্থায়ী স্তর (Phase 14 / 14.1)

**লক্ষ্য:** Owner-এর Cloudflare ফ্রি অ্যাকাউন্টে একটি Worker চালু করা — যেটি ২৪/৭
প্রতি মিনিটে Firestore-এর `supportSessions` দেখে **অ্যাপ-প্রসেস মরে গেলেও**
সাহায্যের অনুরোধ (REQUESTED) ফোনে পুশ করে দেবে। এটি ফ্রি প্ল্যানেই চলে —
কার্ড লাগে না, টাকা লাগে না (Owner-সিদ্ধান্ত: ফ্রি লিমিট পার হলে তবেই পেইড ভাবা হবে)।

**নিরাপত্তা (Owner-সিদ্ধান্ত):** শুরুতেই সাধারণ টোকেন অথেন্টিকেশন —
`POST /sweep` শুধু `KILL_TOKEN` জানলে চলবে। পরে দরকার হলে আরও কড়া করা যাবে।

---

## পদ্ধতি A — Git-connected "Workers Builds" (বর্তমান পদ্ধতি)

Owner Cloudflare dashboard-এ GitHub repo যুক্ত করেছেন (deploy command:
`npx wrangler deploy`) — প্রতিটি push-এ Cloudflare নিজেই build করে deploy করে।

### আগের build কেন fail করেছিল (রুট-কজ)

লগে ছিল:
`✘ [ERROR] Could not detect a directory containing static files (e.g. html, css and js) for the project`

কারণ: repo root-এ **`wrangler.toml` ছিল না**। config ছাড়া wrangler বুঝতে পারেনি
কী deploy করবে, তাই static-ফাইল-Worker ধরে চেষ্টা করেছে আর static file খুঁজে
পায়নি। কোড বা সিনট্যাক্সের কোনো দোষ ছিল না।

### ফিক্স — wrangler.toml (repo root, push করা হয়েছে)

```toml
name = "family-remote-support"
main = "cloudflare/killpush-worker.js"
compatibility_date = "2025-10-01"

[triggers]
crons = ["* * * * *"]

[observability]
enabled = true
```

- `name` — Worker-এর নাম। **আপনার Workers Builds প্রজেক্টের নামের সাথে হুবহু
  মিলতে হবে।** আপনার build-এর নাম ভিন্ন হলে (যেমন `frs-killpush`) আমাকে জানান —
  এই `name` বদলে দেব; না-মিললে build সফল হলেও কোড ভিন্ন Worker-এ চলে যেতে পারে।
- `main` — আসল কোড ফাইল: `cloudflare/killpush-worker.js` (repo-তেই আছে)।
- `[triggers]` — cron এখান থেকেই সেট হয়: **dashboard-এ আলাদা করে Cron Trigger
  যোগ করতে হবে না।**
- `[observability]` — sweep-এর লগ dashboard-এর **Logs** ট্যাবে দেখা যাবে।

### এখন যা হবে (ধাপে ধাপে)

1. এই push-এর পরেই Workers Builds নিজে থেকে নতুন build চালাবে — সফল হলে
   কোড Worker-এ চলে যাবে, cron-ও যুক্ত হবে।
2. **Secrets (একবারই):** Worker → **Settings → Variables and Secrets → Add** — দুবার:

   | Type | Name | Value |
   |------|------|-------|
   | Secret | `FIREBASE_SA_JSON` | Firebase service-account JSON-এর পুরো টেক্সট (Owner-এর সংরক্ষিত কপি — `{` দিয়ে শুরু `}` দিয়ে শেষ) |
   | Secret | `KILL_TOKEN` | ৪০-অক্ষরের টোকেন (চ্যাটে পাঠানো হয়েছে) |

   Secrets deploy-এ মুছে যায় না — একবার দিলেই পরের সব deploy-এ থেকে যায়।
3. **পরীক্ষা:** ব্রাউজারে
   `https://family-remote-support.<আপনার-সাবডোমেইন>.workers.dev/health`
   খুলুন → `"secretsPresent":{"FIREBASE_SA_JSON":true,"KILL_TOKEN":true}`
   দেখা গেলেই সেটআপ সঠিক।
4. আসল sweep-পরীক্ষা (`POST /sweep` + Bearer KILL_TOKEN) আমি sandbox থেকে
   করব — /health-এর প্রমাণ চ্যাটে পাঠালেই।

### Firestore database id — লাইভ-পরীক্ষায় প্রমাণিত (কোড বদলানো হয়নি)

নির্দেশনা ছিল শর্তসাপেক্ষ: "database এর নাম (default) **হলে** কোডে
`/databases/default/` কে `/databases/(default)/` করে দিন"। তাই SA দিয়ে লাইভ
পরীক্ষা (read-only, tmp/dbid-test.mjs) করা হলো:

| পরীক্ষা | ফলাফল |
|---|---|
| `GET /databases/default` | **200** — canonical name `projects/remote-family-support/databases/default`, FIRESTORE_NATIVE |
| `GET /databases/(default)` | **404** — "database '(default)' does not exist" |
| `runQuery /databases/default/documents:runQuery` (Worker-এর হুবহু কল-shape) | **200** — rows OK |
| doc GET `config/quality` via `default` | **200** |
| doc GET `config/quality` via `(default)` | **404** |

**উপসংহার: এই প্রজেক্টের database-এর আসল নামই `default` — কোডে
`/databases/default/` হুবহু সঠিক, তাই কোডে শূন্য পরিবর্তন।** উল্টো
`(default)` করা হলে প্রতিটি sweep 404-এ ভেঙে যেত। (একই `/databases/default/`
path দিয়েই GitHub Actions স্তর বহুদিন সফলভাবে kill-push পাঠাচ্ছে — সম্মত প্রমাণ।)

---

## পদ্ধতি B — হাতে-কোড-পেস্ট (বিকল্প; Git-connection বাদ দিলে)

1. Workers & Pages → **Create Worker** → নাম দিন `frs-killpush` → **Deploy** →
   **Edit code**।
2. GitHub repo → `cloudflare/killpush-worker.js` → **Copy raw file** →
   এডিটরে পুরনো কোড মুছে পেস্ট → **Deploy**।
3. Settings → **Variables and Secrets** → উপরের ২টি Secret → Save।
4. Settings → **Trigger Events** → Cron Trigger `* * * * *` → Save।
5. একই `/health` পরীক্ষা।

> পদ্ধতি A ব্যবহার করলে পদ্ধতি B আর দরকার নেই — **একটি Worker-ই যথেষ্ট**;
> দুটি চালু থাকলে একটি মুছে দিন (বিল-শূন্য, তবে বিভ্রান্তি কমাতে পরিষ্কার রাখা ভালো)।

---

## এটি কী দেয়

| স্তর | লেটেন্সি | স্থায়িত্ব |
|------|----------|-----------|
| স্যান্ডবক্স mini-service (আগের স্তর) | ~৫ সেকেন্ড | স্যান্ডবক্স চলার সময় |
| **Cloudflare Worker (স্থায়ী স্তর)** | ~৬০ সেকেন্ড | **স্থায়ী — ২৪/৭, ফ্রি** |
| GitHub Actions cron (ব্যাকস্টপ) | ~৫ মিনিট | ব্যাকস্টপ |

অনুরোধের উত্তর-সময়সীমা ২ মিনিট — তাই প্রতি-মিনিট Worker স্তরটি সবসময়
জানালার ভেতরে পুশ পৌঁছে দেয়। একই sweep-লজিক (gate, idempotent mark,
dead-token cleanup) Node বাস্তবায়নের হুবহু প্রতিলিপি — দুই স্তর কখনো
আলাদা আচরণ করবে না।

## ফ্রি-লিমিট (কেন টাকা লাগবে না)

- Workers ফ্রি: **১,০০,০০০ request/দিন** — আমাদের ব্যবহার: প্রতি মিনিটে ১ cron
  (১,৪৪০/দিন) + ম্যানুয়াল/অ্যাপ sweep — লিমিটের ~২%-এরও কম।
- CPU: প্রতি invocation ১০ ms — sweep-এর সব কাজ I/O (অপেক্ষা), CPU নয়।
- Cron trigger: ফ্রি প্ল্যানে ৫টি পর্যন্ত — আমরা ১টি ব্যবহার করছি।
- Workers Logs (observability): ফ্রিতে দিনে ২,০০,০০০ ইভেন্ট পর্যন্ত।

লিমিট পার হলে (দূর-ভবিষ্যতে, বহু পরিবার যুক্ত হলে) তখনই $5/মাস Workers Paid —
Owner-সিদ্ধান্ত অনুযায়ী ততদিন অপেক্ষা।

## পরবর্তী-তালিকার আপডেট (Phase 18.9 অডিট, ২০২৬-১০-৩০)

- **অ্যাপ-পাশের instant কল — ❌ সংশোধন (M7 অডিট, ২০২৬-১০-৩১):** আগে এখানে লেখা ছিল
  "Phase 14b, v0.19.0-এ শিপ হয়েছে (helper ফোন সেশন-doc তৈরি করেই `POST /sweep` ডাকে,
  পুশ ~১–২ সেকেন্ডে)"। **এটি কখনোই সত্য ছিল না** — অ্যাপ কোডে কোনোদিন কোনো HTTP
  ক্লায়েন্ট ছিল না (পুরো git ইতিহাসে `workers.dev`/`KILL_TOKEN`/`HttpURLConnection`
  `-S` অনুসন্ধানে ০ ফল; `SessionRepo.kt`-তে ০ HTTP রেফারেন্স)। বিস্তারিত নিচের
  **M7 অডিট সংশোধন** সেকশনে। instant স্তর = স্যান্ডবক্স mini-service (~৫ সেকেন্ড)।
- টোকেন rotate: প্রয়োজন হলে নতুন টোকেন জেনারেট করে Secret আপডেট করলেই হবে।

### লাইভ-স্টেট যাচাই (Phase 18.9, ২০২৬-১০-৩০)

`GET /health` প্রোব ফল: `KILL_TOKEN:true`, কিন্তু **`FIREBASE_SA_JSON:false`** এবং
`last:"not run yet"` — অর্থাৎ **স্থায়ী স্তরটি এখনো সক্রিয় হয়নি**: cron নিবন্ধিত আছে,
কিন্তু SA secret ছাড়া sweep Firestore-এ পৌঁছাতে পারে না। এটিই একমাত্র বাকি ধাপ —
উপরে **পদ্ধতি A → ধাপ ২** (Secrets) শেষ করুন, তারপর /health আবার দেখুন;
`FIREBASE_SA_JSON:true` এলেই স্থায়ী স্তর জীবন্ত। ততদিন কভারেজ: স্যান্ডবক্স
mini-service (~৫ সেকেন্ড) + GitHub Actions ব্যাকস্টপ (best-effort — ২০২৬-১০-২৮→৩০
পর্যবেক্ষণে ৪৮ ঘণ্টায় মাত্র ৫টি schedule-রান)।

### M7 অডিট সংশোধন + লাইভ-স্টেট (২০২৬-১০-৩১)

**লাইভ-স্টেট (দুইবার প্রোব, M7):** `KILL_TOKEN:true`, কিন্তু **`FIREBASE_SA_JSON:false`**
এবং `last:"not run yet"` — Phase 18.9-এর পর অপরিবর্তিত। Owner জানিয়েছেন "worker true" —
লাইভ এন্ডপয়েন্ট এটি নিশ্চিত করেনি। সম্ভাব্য কারণ: মানটি **Build variable** হিসেবে যোগ
হয়েছে (রানটাইম Secret নয় — Workers Builds-এর সেটিংসে দুই জায়গায় ভেরিয়েবল থাকে:
build-time ও runtime), অথবা সেভ সম্পূর্ণ হয়নি। ঠিক করার ধাপ: Workers & Pages →
**family-remote-support** → **Settings → Variables and Secrets** → Add → ধরন অবশ্যই
**Secret** (Build variable নয়) → নাম `FIREBASE_SA_JSON`, মান = SA JSON-এর পুরো টেক্সট
(`{` দিয়ে শুরু `}` দিয়ে শেষ)। সেভ করলেই কার্যকর — নতুন deploy লাগে না। তারপর যাচাই:
`https://family-remote-support.www-farukkhan-fk885.workers.dev/health` —
`FIREBASE_SA_JSON:true` দেখলেই স্থায়ী স্তর জীবন্ত (cron `* * * * *` আগেই নিবন্ধিত)।

**স্যান্ডবক্স instant স্তরের M7 ঘটনা (ঠিক করা হয়েছে):** mini-service যে স্ক্রিপ্ট চালায়
(`/home/z/my-project/scripts/fcm-kill-push.mjs`) সেটি পুরনো কপি ছিল — Phase 18.4
claim-ফিক্স (query-param precondition) ও Phase 20 expiry-ফিক্স ছাড়া। ফল: প্রতিটি claim
400 → "claim-lost" হিসেবে গোনা → instant স্তর কখনোই push করত না (Phase 18.4-এর ব্যাগের
স্যান্ডবক্স-কপি সংস্করণ)। M7-তে রিপো কপি (`android-app/scripts/fcm-kill-push.mjs`,
GitHub Actions যেটি চালায়) দিয়ে সিঙ্ক করে সার্ভিস রিস্টার্ট করা হয়েছে — প্রথম sweep:
০ ত্রুটি। **প্রক্রিয়া-নিয়ম (রেকর্ডকৃত):** এই স্যান্ডবক্স কপি রিপো-মিররের বাইরে — রিপোতে
স্ক্রিপ্ট বদলালে স্যান্ডবক্স কপিও আবার সিঙ্ক করতে হবে, না হলে instant স্তর নীরবে পুরনো
হয়ে যায়।

**GitHub Actions ব্যাকস্টপ (M7 যাচাই):** শেষ run SUCCESS (41180972665, ২০২৬-১০-৩১
07:31 UTC); আগের ৩টি failure-এর লগ ডাউনলোড করে যাচাইকৃত — কারণ Firestore **429**
(ফ্রি-কোটা, Phase 20 পরিবারের), কোটা রিসেটে নিজেই সেরে যায়, কোডে দোষ নেই।

**তিন-স্তরের সৎ অবস্থা (M7 পরে):**

| স্তর | অবস্থা |
|------|--------|
| স্যান্ডবক্স mini-service (~৫সে) | ✅ চলমান — M7-এ স্ক্রিপ্ট সিঙ্ক+রিস্টার্ট |
| Cloudflare Worker স্থায়ী (~৬০সে) | ⛔ `FIREBASE_SA_JSON` এখনো false (Owner-ধাপ) |
| GitHub Actions (~৫মি) | ✅ শেষ run SUCCESS (429-failure জানা-সীমা) |

---

## M9 সনাক্তকরণ — API টোকেনটি IP-filtered (২০২৬-১০-৩১)

M9-এ Owner একটি Cloudflare API টোকেন পাঠিয়েছেন যাতে সহকারী নিজেই Secret-টি বসাতে
পারে। টোকেনটি sandbox-এ `.secrets/cloudflare-token.txt`-এ সংরক্ষিত (অ্যাপের কাজ
শেষ হওয়া পর্যন্ত) — **রিপোতে কখনোই নয়** (সম্পূর্ণ git ইতিহাস স্ক্যানে `cfat_`
০ হিট)। লাইভ পরীক্ষার ফল:

| পরীক্ষা | ফল |
|---|---|
| `GET /user/tokens/verify` | **401 "Invalid API Token"** — অনুমোদিত-নয় IP থেকে verify ঠিক এইরকমই উত্তর দেয় |
| `GET /accounts` | **403 কোড 9109** — `"Cannot use the access token from location: 47.57.242.119"` |
| IPv6 পথ | নেই (sandbox-এ IPv6 egress নেই) |
| egress IP | **drift করে** — এক ঘণ্টার মধ্যে `47.57.242.119` → `47.57.232.232` |

**অর্থ:** টোকেনটি সঠিক ও সক্রিয়, কিন্তু তাতে **Client IP Address Filtering** চালু
(সম্ভবত তৈরির সময় Owner-এর নিজের IP বসানো ছিল) — অন্য যেকোনো IP থেকে (sandbox বা
GitHub Actions) ব্যবহার করা যায় না। এই কারণেই dashboard-এর বাইরে কোনো পথ খোলা
ছিল না।

### আনলক ধাপ — দুই ভাবে (একটিই যথেষ্ট)

1. **(সুপারিশকৃত — একবারই, ~৩০ সেকেন্ড):** dash.cloudflare.com → ডান-উপরের আইকন →
   **My Profile → API Tokens** → টোকেনটির ডানে **⋯ → Edit** → **Client IP Address
   Filtering** ঘরের IP-গুলো **মুছে খালি** করুন → Continue to Summary → **Save**।
   এরপর চ্যাটে শুধু বলুন **"done"** — সহকারী তখন API দিয়েই `FIREBASE_SA_JSON`
   Secret বসাবে, `/health`-এ `FIREBASE_SA_JSON:true` প্রমাণ দেখবে, এবং একটি লাইভ
   sweep-টেস্ট চালিয়ে স্থায়ী স্তর চালু নিশ্চিত করবে। (টোকেনটি scoped — এই কাজেই
   যথেষ্ট।)
2. (বিকল্প — আগের ধাপই): Workers & Pages → **family-remote-support** → Settings →
   **Variables and Secrets** → Add → ধরন অবশ্যই **Secret** (Build variable নয়) →
   `FIREBASE_SA_JSON` = SA JSON-এর পুরো টেক্সট (`{` দিয়ে শুরু `}` দিয়ে শেষ) →
   সেভ। তারপর
   `https://family-remote-support.www-farukkhan-fk885.workers.dev/health`-এ
   `FIREBASE_SA_JSON:true` দেখলেই স্থায়ী স্তর জীবন্ত।

### "সেটাপ করার পরেও মোছে যায়" — M9 ব্যাখ্যা

Secret runtime-এ একবার বসলে deploy দিয়ে মুছে যায় না। "মোছা"-র মতো আচরণের সবচেয়ে
সম্ভাব্য কারণ আগের বিশ্লেষণই: মানটি **Build variable** অংশে (Workers Builds-এর
আলাদা সেটিংস পেজ) বসেছিল — সেখানে বসলে Worker-এর runtime-এ পৌঁছায় না,
`/health`-এ false দেখায়, আর পরে Variables-অংশে খুঁজলে আর পাওয়া যায় না ("মুছে
গেছে" মনে হয়)। KILL_TOKEN একবার ঠিক জায়গায় (runtime Secret) বসেছিল বলেই সেটি
টিকে আছে (`/health`-এ `true`)।

### তিন-স্তরের M9-অবস্থা (২০২৬-১০-৩১)

| স্তর | অবস্থা (M9) |
|------|--------|
| স্যান্ডবক্স instant (~৫সে) | ✅ ৫ সেকেন্ড পর পর sweep, ০ ত্রুটি; স্ক্রিপ্ট repo-র সাথে md5-সম |
| Cloudflare স্থায়ী (~৬০সে) | ⛔ টোকেনের IP-filter খোলা পর্যন্ত অপেক্ষমাণ (উপরের ধাপ ১/২) |
| GitHub Actions (~৫মি) | ✅ ম্যানুয়াল dispatch run **41203101630 SUCCESS**; schedule পুনঃনিবন্ধিত (workflow কমেন্ট touch) |

## M10 সমাধান — টোকেন আনলকড, Secret বসানো, স্থায়ী স্তর জীবন্ত (২০২৬-১০-৩১)

Owner টোকেনটি **Edit করে সব পারমিশন এলাউ করে দিয়েছেন** — M9-এর IP-filter ব্লক আর
নেই। সমন্বয়কারী এরপর API দিয়েই পুরো কাজটি শেষ করেছে (প্রতিটি ধাপের প্রমাণসহ):

| ধাপ | ফলাফল (লাইভ প্রমাণ) |
|---|---|
| টোকেন যাচাই | `GET /accounts` **HTTP 200** — আগের-সেই ব্লকড egress IP `47.57.242.119` থেকেই; IP-filter চলে গেছে (`/user/tokens/verify` 401 ও `/user/accounts` 404 দেখালেও টোকেন কার্যকর — ঐ দুটি রুটের নিজস্ব সীমাবদ্ধতা, `/accounts`-ই সঠিক পথ) |
| Account শনাক্তকরণ | `0ad9f9d438b20ae86afec8ec3347e600` (Www.farukkhan.fk885@gmail.com) |
| Worker তালিকা | `family-remote-support` — শেষ wrangler deploy ১৪:২৮ UTC (Workers Builds, সর্বশেষ push-এর সাথে মিলে) |
| **Secret বসানো** | REST secrets API (`PUT …/workers/scripts/family-remote-support/secrets`, `type:"secret_text"`) → **HTTP 201** — এটাই runtime Secret, Build variable নয় |
| Bindings যাচাই | Script settings-এ `FIREBASE_SA_JSON` + `KILL_TOKEN` দুটোই `secret_text` — deploy-এ টিকে যায় (KILL_TOKEN Phase-১৪ থেকে যে প্রমাণিত পথেই বেঁচে আছে, সে পথেই) |
| `/health` | **`FIREBASE_SA_JSON:true`** ✓ |
| Cron জীবন্ত প্রমাণ | GraphQL `workersInvocationsAdaptive`: প্রতি মিনিটে ঠিক একটি success invocation (নির্দিষ্ট সেকেন্ড-অফসেটে), আর **প্রতি sweep-এ `subreq=2`** = OAuth টোকেন কল + Firestore query — মানে SA JSON পার্স/সাইন/গৃহীত হচ্ছে (OAuth ব্যর্থ হলে Firestore কল পর্যন্ত যেতই না) |
| বর্তমান সীমা (স্বাভাবিক) | Firestore ফ্রি-কোটা উইন্ডোতে (ঢাকা-সন্ধ্যা) query-leg 429 দেয় — অন্য দুই স্তরও একই 429-এ থাকে; **১৩:০০ (ঢাকা) রিসেটের পর প্রথম sweep থেকেই সব স্বয়ংক্রিয়ভাবে ঠিক** |
| Deploy-সার্ভাইভাল | এই M10 push-ই Workers Builds-কে নতুন deploy ট্রিগার করে — push-পরবর্তী পুনঃযাচাইয়ে Secret টিকে থাকা নিশ্চিত করা হয়েছে (worklog §74-এ CI-record-এর পাশেই) |

**উপসংহার: তিন-স্তরের kill-push প্রথমবারের মতো একসাথে চালু** — sandbox instant
(~৫সে) · Cloudflare Worker (~৬০সে, স্থায়ী) · GitHub Actions (~৫মি, ব্যাকস্টপ)।
Owner-এর আর কোনো Cloudflare কাজ নেই; ভবিষ্যতে Secret বদলাতে হলে একই REST পথ
ব্যবহারযোগ্য (`docs/RELEASE_NOTES.md` §10 runbook)।

### তিন-স্তরের M10-অবস্থা (২০২৬-১০-৩১)

| স্তর | অবস্থা (M10) |
|------|--------|
| স্যান্ডবক্স instant (~৫সে) | ✅ চলমান (কোটা-উইন্ডোতে 429 — পরিচিত, ১৩:০০ ঢাকায় স্বয়ংক্রিয় রিকভারি) |
| Cloudflare স্থায়ী (~৬০সে) | ✅ **Secret বসানো + cron প্রমাণিত — বন্ধ হওয়া একমাত্র বাকি আইটেম বন্ধ** |
| GitHub Actions (~৫মি) | ✅ M9-এ dispatch-যাচাইকৃত; schedule নিবন্ধিত |

## কোটা-নির্দেশ (২০২৬-১০-৩১, M10-পরবর্তী) — সব অটো-পোলিং স্তর বন্ধ

Owner নির্দেশ: *"শুধু মাত্র যখন ইউজার চাইবে তখন রিড রাইট হবে, বাকি সময় অটো আপডেট
বন্ধ থাকবে; স্যান্ডবক্স থেকে কোনও রিকোয়েস্ট যাবে না, শুধু অ্যাপ থেকে যাবে।"*
সন্ধ্যার "API লিমিট শেষ" (RESOURCE_EXHAUSTED) বার্তার **আসল কারণ হিসাব**:

| স্তর | অটো-রিড (দৈনিক) | কোটার অংশ |
|---|---|---|
| স্যান্ডবক্স kill-push (৫ সেকেন্ড × **দুই ইনস্ট্যান্স** — লাইভে ধরা পড়েছে) | ২×২×১৭,২৮০ ≈ **৬৯,০০০** | >১০০% |
| Cloudflare Worker (প্রতি মিনিট) | ১,৪৪০×২ = ২,৮৮০ | ৫.৮% |
| GitHub Actions (৫ মিনিট) | ২৮৮×২ = ৫৭৬ | ১.২% |
| **অ্যাপ নিজে** (ইউজার-চালিত + ইভেন্ট-ড্রিভেন লিসেনার; অডিট-প্রমাণিত) | ~২৫০–৪০০ | <১% |

ফ্রি কোটা ৫০,০০০ রিড/দিন — অটো-স্তরগুলোই দুপুরের মধ্যে শেষ করে দিচ্ছিল।
**নতুন ভঙ্গি (এই নির্দেশ থেকে):**

| স্তর | নতুন অবস্থা |
|---|---|
| স্যান্ডবক্স | ⛔ **সম্পূর্ণ বন্ধ** — দুই zombie ইনস্ট্যান্সই kill করা হয়েছে; :3020 বন্ধ; স্যান্ডবক্স থেকে Firebase-এ **শূন্য** রিকোয়েস্ট (প্রোব/স্ক্রিপ্ট শুধু চাহিবা-মাত্র হাতে চালানো হবে) |
| Cloudflare Worker | ⛔ **DORMANT** — cron ট্রিগার সরানো (`wrangler.toml`-এ `crons = []`, প্রতিটা deploy-এ খালিই থাকবে; API + deploy দুই পথেই যাচাইকৃত `schedules: []`); Secrets অক্ষত, `POST /sweep` (KILL_TOKEN) চাহিবা-মাত্র ডায়াগনস্টিকের জন্য বাকি আছে |
| GitHub Actions | ⛔ schedule নিষ্ক্রিয় (workflow_dispatch-only — Actions পেজ থেকে হাতে চালানো যাবে) |
| **অ্যাপ** | ✅ **একমাত্র Firebase ক্লায়েন্ট** — v0.36.0/55: presence হার্টবিট foreground-only (খোলা + চালু-থাকাকালে প্রতি ৫ মিনিট + বন্ধ করার মুহূর্তে একবার); অ্যাপ বন্ধ থাকলে Firebase-এ **শূন্য** ট্রাফিক; সব রিড ইউজার-অ্যাকশন বা ইভেন্ট-ড্রিভেন লিসেনার (কিছু না ঘটলে ০ রিড) |

**সৎ ট্রেড-অফ (জেনে রাখুন):** অ্যাপের প্রসেস মৃত থাকলে support-ask-এর ফোন-রিং আর
আসবে না (ওই রিং দিতেই সার্ভার-পোলিং লাগত)। অ্যাপ খোলা/ট্রে-তে থাকলে লিসেনার
সাথে সাথে নোটিফিকেশন দেখাবে; খোলার পর pending ask দেখা যাবে। কোটা নিয়ে আর ভাবনা
নেই — দিনে কয়েকশ রিডও লাগবে না এখন।

**পুনরায় চালু (re-arm) — ৩০ সেকেন্ডের কাজ, যেকোনো সময়:**
- Worker: `wrangler.toml`-এ `crons = ["* * * * *"]` করে push → পরের deploy-ই প্রতি-মিনিট sweep ফিরিয়ে আনবে (Secrets তো আছেই)।
- Actions: `kill-push.yml`-এ schedule-ব্লক uncomment করে push।
- স্যান্ডবক্স: `mini-services/kill-push-service`-এ `nohup bun run dev` (ইচ্ছা করলে)।
