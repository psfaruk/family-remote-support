# Cloudflare Workers সেটআপ — kill-push স্থায়ী স্তর (Phase 14)

**লক্ষ্য:** Owner-এর Cloudflare ফ্রি অ্যাকাউন্টে একটি Worker চালু করা — যেটি ২৪/৭
প্রতি মিনিটে Firestore-এর `supportSessions` দেখে **অ্যাপ-প্রসেস মরে গেলেও**
সাহায্যের অনুরোধ (REQUESTED) ফোনে পুশ করে দেবে। এটি ফ্রি প্ল্যানেই চলে —
কার্ড লাগে না, টাকা লাগে না (Owner-সিদ্ধান্ত: ফ্রি লিমিট পার হলে তবেই পেইড ভাবা হবে)।

**নিরাপত্তা (Owner-সিদ্ধান্ত):** শুরুতেই সাধারণ টোকেন অথেন্টিকেশন —
`POST /sweep` শুধু `KILL_TOKEN` জানলে চলবে। পরে দরকার হলে আরও কড়া করা যাবে।

---

## ধাপ ১ — Worker তৈরি

1. Cloudflare Dashboard-এর বাম মেনু থেকে **Workers & Pages** খুলুন।
2. **Create** → **Create Worker** চাপুন।
3. নাম দিন: `frs-killpush` (ছোট হাতের অক্ষর, ড্যাশ ছাড়া) → **Deploy**।
   (এখন hello-world কোড ডিপ্লয় হবে — সমস্যা নেই।)
4. Deploy হয়ে গেলে **Edit code** চাপুন।
5. এডিটরের বাম পাশে থাকা ডিফল্ট কোড **সব মুছে দিন** (Ctrl+A → Delete)।
6. নিচের ফাইলের পুরো কোড কপি করে পেস্ট করুন:
   GitHub repo → **`cloudflare/killpush-worker.js`** → ফাইলটি খুলে উপরের
   ডান কোণায় **Copy raw file** বোতাম।
7. ডান-উপরের **Deploy** চাপুন।

## ধাপ ২ — Secrets যোগ করা (২টি)

Worker পেজ → **Settings** → **Variables and Secrets** → **Add** — দুবার:

| Type | Name | Value |
|------|------|-------|
| Secret | `FIREBASE_SA_JSON` | Firebase service-account JSON-এর পুরো টেক্সট (Owner-এর সংরক্ষিত কপি — `{` দিয়ে শুরু `}` দিয়ে শেষ) |
| Secret | `KILL_TOKEN` | Assistant-এর দেওয়া ৪০-অক্ষরের টোকেন (চ্যাটে পাঠানো হয়েছে) |

প্রতিটির পরে **Deploy/Save** চাপতে ভুলবেন না।

> নোট: SA JSON-এ `client_email`, `private_key`, `project_id` থাকতে হবে —
> Firebase Console → Project settings → Service accounts → যেখান থেকে আগে
> key নেওয়া হয়েছিল, সেটিই।

## ধাপ ৩ — Cron Trigger (প্রতি মিনিটে)

Worker পেজ → **Settings** → **Trigger Events** (Cron Triggers) → **Add Cron Trigger**:

```
* * * * *
```

(৫টি তারকা — মানে প্রতি মিনিটে। ফ্রি প্ল্যানে cron trigger ফ্রি।)
Save করুন।

## ধাপ ৪ — পরীক্ষা

1. ফোন/কম্পিউটারের ব্রাউজারে খুলুন:
   `https://frs-killpush.<আপনার-সাবডোমেইন>.workers.dev/health`
2. দেখতে হবে: `{"ok":true,"service":"frs-killpush",...,"secretsPresent":{"FIREBASE_SA_JSON":true,"KILL_TOKEN":true},...}`
   — `secretsPresent` দুটোই `true` হলে সেটআপ সঠিক।
3. আসল sweep পরীক্ষা (অ্যাসিস্ট্যান্ট স্যান্ডবক্স থেকে করবে): `POST /sweep` +
   `Authorization: Bearer <KILL_TOKEN>` → `{"ok":true,"summary":{...}}`।

## এটি কী দেয়

| স্তর | লেটেন্সি | স্থায়িত্ব |
|------|----------|-----------|
| স্যান্ডবক্স mini-service (আগের স্তর) | ~৫ সেকেন্ড | স্যান্ডবক্স চলার সময় |
| **Cloudflare Worker (নতুন, Phase 14)** | ~৬০ সেকেন্ড | **স্থায়ী — ২৪/৭, ফ্রি** |
| GitHub Actions cron (আগের ব্যাকস্টপ) | ~৫ মিনিট | ব্যাকস্টপ |

অনুরোধের উত্তর-সময়সীমা ২ মিনিট — তাই প্রতি-মিনিট Worker স্তরটি সবসময়
জানালার ভেতরে পুশ পৌঁছে দেয়। একই sweep-লজিক (gate, idempotent mark,
dead-token cleanup) Node বাস্তবায়নের হুবহু প্রতিলিপি — দুই স্তর কখনো
আলাদা আচরণ করবে না।

## ফ্রি-লিমিট (কেন টাকা লাগবে না)

- Workers ফ্রি: **১,০০,০০০ request/দিন** — আমাদের ব্যবহার: প্রতি মিনিটে ১ cron
  (১,৪৪০/দিন) + ম্যানুয়াল/অ্যাপ sweep — লিমিটের ~২%-এরও কম।
- CPU: প্রতি invocation ১০ ms — sweep-এর সব কাজ I/O (অপেক্ষা), CPU নয়।
- Cron trigger: ফ্রি প্ল্যানে ৫টি পর্যন্ত — আমরা ১টি ব্যবহার করছি।

লিমিট পার হলে (দূর-ভবিষ্যতে, বহু পরিবার যুক্ত হলে) তখনই $5/মাস Workers Paid —
Owner-সিদ্ধান্ত অনুযায়ী ততদিন অপেক্ষা।

## পরবর্তী (Phase 14-পরবর্তী)

- **অ্যাপ-পাশের instant কল (v0.19.0):** helper ফোন সেশন-doc তৈরি করেই
  `POST /sweep` ডাকবে (fire-and-forget) → পুশ ~১-২ সেকেন্ডে।
- টোকেন rotate: প্রয়োজন হলে নতুন টোকেন জেনারেট করে Secret আপডেট করলেই হবে।
