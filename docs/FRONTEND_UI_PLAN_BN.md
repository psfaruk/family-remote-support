# FRONTEND_UI_PLAN_BN — গভীর UI পরিকল্পনা (ব্যাকএন্ড-প্রমাণিত)

> **Status:** PLANNING — Owner-অনুমোদনের অপেক্ষায়। কোনো কোড বদলানো হয়নি।
> **Baseline:** v0.48.0/68 · 2026-11 · **Sources:** সব কথা `docs/` (NAVIGATION_MAP, UI_UX_DESIGN_SYSTEM, LOCATION_UX_SPECIFICATION, PROJECT_CONTEXT, FRONTEND_ARCHITECTURE) + ব্যাকএন্ড সোর্স (SessionRepo, PairingRepo, PresenceRepo, LiveLocationRepo, NotifCenter, NotificationsLog, EventBannerBus, PermissionWizard, AssistService, QrActions) পড়ে প্রমাণিত।
> **প্রেক্ষাপট:** MainActivity.kt (~৫,৯০০ লাইন) + `ui/` ফোল্ডার রিপোতে অনুপস্থিত (কখনো commit হয়নি) — এই ডকুমেন্ট সেই frontend-এর পূর্ণ পুনর্নির্মাণ-ব্লুপ্রিন্ট।

---

## পর্ব ১ — ডেটা-চুক্তি (UI যা রেন্ডার করবে)

### 1.1 মূল মডেল ও টাইম-কনস্ট্যান্ট (কোড-প্রমাণিত)

| মডেল | UI-প্রাসঙ্গিক ফিল্ড | টাইমলাইন |
|---|---|---|
| `SessionView` | status, helperEmail, recipientEmail, recipientDeviceModel, respondDeadlineMillis, sessionEndsAtMillis, endedBy, kind | ask **২ মি** ডেডলাইন → ACTIVE **১৫ মি** TTL (`SESSION_ACTIVE_TTL_MILLIS`) |
| `InvitationView` | status (PENDING/PAIRED/CANCELLED), expiresAt, kind | QR **৫ মি** TTL (`INVITE_TTL_MILLIS`) |
| `RelationshipView` | counterpartEmail, status (ACTIVE/REVOKED/EXPIRED), kind, guestExpiresAt, `isUsable` | গেস্ট **২ ঘ** ডিফল্ট (rules-সীমা **৩ ঘ**) |
| `PresenceView` | heartbeatAtMillis | online উইন্ডো **২০ মি**, last-seen **১ ঘ** পর্যন্ত |
| `LiveLocationView` | lat, lng, accuracyMeters, sharingEnabled, updatedAtMillis, speedMetersPerSecond | LIVE **<২ মি**, STALE **<৫ মি**, তারপর OFFLINE |
| `NotifEvent` | type (১৮ প্রকার), at, arg (নিকনেম), arg2 (sessionId/placeId), readAt | স্থানীয় JSONL — Firestore-খরচ শূন্য |
| `EventBanner` | type, arg, route, atMillis | টপ-ব্যানার, অটো-ডিসমিস, `session_asked` বাদ (কনসেন্ট-ডায়ালগ-ই হিরো) |

### 1.2 সেশন FSM (UI-র প্রতিটা স্ক্রিন এর একটা স্টেট)

```
IDLE ──► REQUESTED ──(২মি পার)──► EXPIRED
             ││└─(recipient প্রত্যাখ্যান)──► DENIED
             │└──(helper ফিরিয়ে নেয়)────► CANCELLED
             └───(recipient অনুমতি)──► ACTIVE ──(১৫মি/End)──► ENDED
                                          └─(গেস্ট-উইন্ডো আগে শেষ)──► EXPIRED
zombie-cleanup: রিবুটের পরে পুরোনো REQUESTED/ACTIVE নিজে থেকেই মৃত (ensureNoBusySession)
```

### 1.3 পেয়ারিং FSM ও লোকেশন FSM

```
পেয়ারিং: IDLE → PENDING(QR, ৫মি) → PAIRED ✓(icon-pop 200ms) | CANCELLED | EXPIRED(→"আবার তৈরি" হাইলাইট)
লোকেশন:  OFF → [৬-ধাপ wizard] → LIVE → OFF
          LIVE → PAUSED → LIVE|OFF ; PERMISSION_LOST → OFF + notif_ev_sharing_perm_lost
```

---

## পর্ব ২ — স্ক্রিন-বাই-স্ক্রিন ব্লুপ্রিন্ট

### 2.1 Shell
- AppTopBar 64dp: অ্যাপ-নাম + presence চিপ + 🔔(unread ব্যাজ) → notifications রুট
- EventBanner: টপ-বারের নিচে, ১-টাই, অটো-ডিসমিস, [দেখুন] → notifRouteFor
- AppBottomNav 80dp: হোম / ডিভাইস যোগ(➕) / ট্র্যাকিং(📍) / সেটিংস — crossfade ≤150ms, nested slide ≈200ms
- টপ-লেভেল ট্যাবে প্রতি-প্রবেশে scroll-top; nested-এ saved scroll
- কনটেন্ট: padding-H 16dp, SectionCard-গ্যাপ 16dp

### 2.2 হোম (উপর→নিচ)
1. **ConsentCard/ডায়ালগ** (REQUESTED থাকলে) — primaryContainer, অনুরোধকারী+ইমেইল+টার্গেট-ডিভাইস, ২-মি কাউন্টডাউন, [অনুমতি দিন·52dp][বাতিল·48dp]
2. **LiveHintCard** (ACTIVE) — "স্ক্রিন শেয়ার চালু · শেষ হবে HH:MM", [সেশনে ফিরুন][শেষ করুন]
3. অভ্যর্থনা (time-aware) + সাইন-ইন-লাইন
4. ২×২ IconStatusTile গ্রিড: সেটআপ x/৪ · যুক্ত ফোন n · শেয়ার ON/OFF · নিরাপত্তা (<360dp → 1×4)
5. QuickActionTile ×২: সাহায্য নিন / সাহায্য দিন(→home/family)
6. যুক্ত ফোন top-৩ + [সব দেখুন] — AppListTile: অবতার, presence(🟢/সর্বশেষ X মি আগে/○), KindChip(FAMILY-অ্যাম্বার/GUEST+⏳কাউন্টডাউন)
7. সেশন একটিভিটি (চলমান+শেষ, StatusBadge)

**Nested:** `home/family`, `home/family/{relId}` (action sheet: সেশন-চাওয়া/family-revoke/guest-remove + confirm), `home/sessions`, `home/sessions/{id}`

### 2.3 ডিভাইস যোগ
- Tab-root: FAMILY/GUEST featureCard (140dp-min) + "কোড স্ক্যান করে যুক্ত হন" + ব্যাখ্যা-কার্ড ⓘ
- PairSheet: QrContainer (সাদা তল, QR 200dp, StatusBadge, ৫-মি কাউন্টডাউন, ≤৩০স → warning), ১২-অক্ষর কোড `INVITE-TOKEN` mono + কপি + share(PNG+টেক্সট), বাতিল
- QrScanDialog: CameraX-প্রিভিউ স্টাইল ফ্রেম + ম্যানুয়াল কোড (imePadding) + ক্যামেরা-পারমিশন নেই → PermissionDeniedPanel
- রিডিম → সফল ✓-পপ → role-preview শিট → পারমিশন-উইজার্ড শর্টকাট

### 2.4 ট্র্যাকিং
1. **OwnSharingCard** — ৭-স্টেট (LIVE/PAUSED/PERMISSION_REQUIRED/OFFLINE/LAST_LOCATION/UNAVAILABLE/STOPPED) + upload-লাইন (up_ok নীরব / up_pending / up_failed / up_unavailable) + ইন্টারভাল (৩০স/১মি/৫মি) + [শেয়ার শুরু] → ৬-ধাপ wizard
2. MemberLocationCard-তালিকা — freshness ●/◐/○ (আইকন+রঙ+লেখা), "সর্বন্ধীন ৯:১৫ · ±২৫ মিটার", [বিস্তারিত][ইতিহাস(*শুধু historyAccess)]
3. ম্যাপ — accuracy-circle, stale=dashed, zoom +/− 48dp(cd), ডিভাইস-সিলেক্টর শিট
4. [আমার জায়গা ও অ্যালার্ট] → places

**SharingSetupSheet (৬-ধাপ):** ①ব্যাখ্যা ②প্রাপক(কেউ-না→Next disabled) ③স্কোপ(একবার/১ঘ/৮ঘ/মধ্যরাত/বন্ধ-না-হওয়া-পর্যন্ত[ডিফল্ট]/ইতিহাসসহ) ④রিটেনশন(না/২৪ঘ/৭দি/৩০দি — শুধু ইতিহাসসহ) ⑤পারমিশন+ব্যাটারি-সৎ-নোট ⑥read-back+[শেয়ার শুরু]। ব্যাক=আগের ধাপ; বাতিল=কিছুই বদলায়নি।

**Nested:** `tracking/{relId}` সেকশন A-E · `tracking/{relId}/history` (date-chips, gap>১০মি dashed, summary: পয়েন্ট/দূরত্ব/কভারেজ%) · `tracking/places` (নাম+রেডিয়াস ১০০/২০০/৫০০মি, arrival/departure টগল, cooldown ≥৫মি)

### 2.5 সেটিংস (৫ পার্টিশন, ২৩ রো)
১ Account(প্রোফাইল, ডাকনাম-এডিট) · ২ Device Mgmt(যুক্ত ফোন→home/family, এই অ্যাকাউন্টের ফোন, রিমোট-অ্যাসিস্ট স্ট্যাটাস) · ৩ Privacy & Security(শেয়ার+ইন্টারভাল, গেস্ট-অ্যাক্সেস, পারমিশন-চেক x/৪, প্রাইভেসি, নিয়ম) · ৪ Preferences(থিম, ভাষা→৪৪০dp-cap ডায়ালগ, নোটিফিকেশন, বন্ধ-অ্যাপের-অ্যালার্ট) · ৫ About & session(ভার্সন, **লাল-আউটলাইন সাইন-আউট** — একেবারে শেষ)

### 2.6 নোটিফিকেশন সেন্টার
ফিল্টার-চিপ: সব/অপঠিত(n)/সংযোগ/লোকেশন/নিরাপত্তা। ১৮ ইভেন্ট-টাইপ → ৩ ক্যাটাগরি (NotifCategoryOf ম্যাপ)। ট্যাপ → readAt + notifRouteFor-ডিপ-লিংক। [সব পড়া-হিসেবে]।

### 2.7 AuthGate + SessionViewer
- AuthGate: hero-shield(emerald) + ট্যাগলাইন + Google BigButton + ইমেইল/পাসওয়ার্ড + রিসেট; busy-স্পিনার; FriendlyErrorCard
- Viewer: কালো VideoRenderer + LIVE-ব্যাজ + ১৫-মি কাউন্টডাউন + টাচ-অ্যাসিস্ট-লেবেল(স্ক্রিন-শেয়ার থেকে আলাদা) + Back/Home/Recents ইনজেকশন + [End Session·সবসময়-দৃশ্যমান]

---

## পর্ব ৩ — কম্পোনেন্ট API
`SectionCard(title,icon,infoText?,trailing?)` · `AppListTile(avatar,title,subtitle?,chip?,onClick?,trailing?)` · `SettingsTile(icon,title,desc?,trailing?,danger?)` · `MemberLocationCard` · `BigButton(≥52dp,loading)` · `WideOutlinedButton(≥48dp,danger)` · `QuickActionTile(88dp)` · `IconStatusTile` · `StatusBadge(৬-স্টেট: রঙ+আইকন+লেখা)` · `FreshnessBadge(●/◐/○)` · `KindChip(FAMILY/GUEST)` · `GuestCountdownChip(<১৫মি উজ্জ্বল)` · `QrContainer(সাদা তল)` · `EmptyState` · `LoadingSkeleton(৩)` · `ErrorPanel(FriendlyErrorCard-সহ)` · `PermissionDeniedPanel`

**নিষেধ:** একই কাজে দ্বিতীয় কম্পোনেন্ট নয় · স্ক্রিনে হার্ডকোড-রঙ নয় (শুধু colorScheme.*) · নির্দিষ্ট-উচ্চতা নয় (বাংলা-wrap) · রঙ-একা-স্ট্যাটাস নয়

## পর্ব ৪ — ডিজাইন টোকেন (v0.48 emerald)
**Light:** primary #047857 / primaryContainer #D1FAE5 / background #F6F9F7 / surface #FFF / onSurface #1A2420 / onSurfaceVariant #5D6B64 / outlineVariant #DFE7E2 / secondary(Family) #8F4C00/#FFDCC2 / tertiary(Presence) #0F766E / error #C83C45 / warning #C78313
**Dark:** background #0D1512 / surface #141D19 / surfaceContainerHigh #1C2721 / onSurface #EDF3EF / onSurfaceVariant #A9B7AF / primary #6EE7B7 / primaryContainer #065F46 / secondaryContainer #5A3A1E / tertiary #5EEAD4 / error #FF8A93 / outlineVariant #26302B / warning #E8A83E
**Type (SeniorTypography):** titleLg 22/28B · titleMd 18/26SB · titleSm 16/24SB · bodyLg 17/25 · bodyMd 16/23 · bodySm 14/20 · labelLg 15/20SB · labelMd 13/18M — সব sp, ফন্ট-স্কেল 1.3× টেস্ট
**Spacing:** 4/8/12/16/20/24/32 · **Shape:** control 10 / card 16 / featureCard 20 / dialog 22 · **Touch ≥48dp** · QR 200dp সাদা তল · ভাষা-ডায়ালগ heightIn(max=440dp)

## পর্ব ৫ — টাইমার-স্পেসিফিকেশন
| টাইমার | UI | টিক | শেষে |
|---|---|---|---|
| Consent ২মি | ConsentCard-চিপ | ১স | কার্ড ফেইড, listener EXPIRED দেখায় |
| Session TTL ১৫মি | Viewer-হেডার+LiveHint | ১স | ENDED + banner |
| QR TTL ৫মি | PairSheet-চিপ, ≤৩০স warning | ১স | EXPIRED-ব্যাজ + [আবার তৈরি] |
| Guest ২ঘ | KindChip | ৩০স | EXPIRED + guest_expired |
| Presence | রো-সাবটেক্সট | listener | ◐→○ |

## পর্ব ৬ — এজ-কেস (১২ বাধ্যতামূলক)
১ প্রসেস-ডেথ-রিস্টোর (SavedStateHandle) · ২ FCM cold-start deep-link (singleTop+onNewIntent) · ৩ নেটওয়ার্ক-হারা → "পুনঃসংযোগ…"(মিথ্যা-শেষ নয়) · ৪ quota → err_quota ErrorPanel · ৫ zombie-sweep UI-প্রতিফলন · ৬ গেস্ট-উইন্ডো < সেশন → EXPIRED · ৭ revoked ডিটেইল → সৎ fam_not_found · ৮ ডার্কেও QR সাদা তল · ৯ 1.3× বাংলা wrap · ১০ <360dp গ্রিড-ভাঙা · ১১ ভাষা-বদলে ট্যাব-সংরক্ষণ · ১২ ডেমো-ব্যানার (Phase-4 শুধু)

## পর্ব ৭ — ইমপ্লিমেন্টেশন ওয়েভ (প্রতি ওয়েভ-শেষে installable-APK গেট)
| ওয়েভ | স্কোপ | গেট |
|---|---|---|
| W1 | Theme.kt + Shell + AppNav + ৪-ফাঁপা-ট্যাব | ট্যাব-সুইচ+ব্যাক+ডার্ক |
| W2 | AuthGate + HomeTab | consent-flow ড্রাই-রান |
| W3 | PairingFlow | ৫-মি TTL লাইভ |
| W4 | Consent+Viewer+LiveHint | দুই-ফোন E2E |
| W5 | Tracking+Wizard | freshness ×৩ |
| W6 | SettingsTab | ×15 লোকেল |
| W7 | NotifCenter+Banner+deep-links | ১৮-ইভেন্ট ম্যাট্রিক্স |
| W8 | Places+History+Movement | geofence |

**আনুমানিক:** ~৮,৫০০ লাইন Kotlin · ফাইল-লেআউট FRONTEND_ARCHITECTURE.md §৩-অনুযায়ী (`ui/nav`, `ui/components`, `ui/home`…)

---

*ইন্টারঅ্যাক্টিভ HTML প্রোটোটাইপ (এই প্ল্যানের ১:১ ভিজ্যুয়াল) Next.js স্যান্ডবক্সে নির্মিত — Owner ক্লিক-করে প্রতিটা স্টেট/টাইমার/ফ্লো যাচাই করতে পারবেন; Kotlin রি-বিল্ড সেটার ১:১ অনুসরণ করবে।*
