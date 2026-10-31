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
