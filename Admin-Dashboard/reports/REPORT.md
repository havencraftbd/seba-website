# 🛡️ HavenCraft SMP Admin Dashboard — Security Audit & Defensive Vulnerability Assessment Report

**প্রজেক্ট:** HavenCraft SMP Admin Website (`Admin-Website`)  
**অডিটের ধরন:** Full-Stack Defensive Security Audit (Frontend, Vercel Serverless API, Supabase RLS & Storage)  
**তারিখ:** নভেম্বর ২০২৬  

---

## 📌 সারসংক্ষেপ (Executive Summary)

HavenCraft SMP Admin Dashboard প্রজেক্টটির ফ্রন্টএন্ড, সার্ভারলেস ব্যাকএন্ড (`/api/*`) এবং Supabase ডাটাবেজ স্কিমা (`supabase_schema.sql`) পুঙ্খানুপুঙ্খভাবে বিশ্লেষণ করা হয়েছে। প্রজেক্টটিতে ইতিমধ্যেই বেশ কিছু **শক্তিশালী সিকিউরিটি প্র্যাকটিস** বিদ্যমান রয়েছে:

- ✅ **Server-Side Token Verification:** ক্লায়েন্ট ও সার্ভার উভয় জায়গায় `auth.getUser(token)` ব্যবহার করে সরাসরি Supabase Auth সার্ভার থেকে JWT টোকেন ভেরিফাই করা হয়েছে (`assets/js/auth.js` ও `/api/*.js`)।
- ✅ **Immutable Client Profile State:** ব্রাউজারের মেমোরিতে `Object.freeze()` এবং `Object.defineProperty(..., { writable: false, configurable: false })` ব্যবহার করে `window.havenStaffProfile` ট্যাম্পার-প্রুফ করা হয়েছে (`assets/js/auth.js#L101-L119`)।
- ✅ **Privilege Escalation Guard Trigger:** ডাটাবেস লেভেলে `guard_staff_profile_changes()` ট্রিগার ব্যবহার করে অননুমোদিত রোল ও পারমিশন পরিবর্তন আটকানোর ব্যবস্থা রাখা হয়েছে (`supabase_schema.sql#L421-L482`)।
- ✅ **HTML Escaping Utility:** অধিকাংশ ডাইনামিক টেমপ্লেটে `window.hvnEscapeHtml()` ব্যবহার করে XSS প্রতিরোধের চেষ্টা করা হয়েছে (`assets/js/utils.js#L18-L27`)।

তবে ফ্রন্টএন্ডের সাথে ডাটাবেজ RLS এবং API-এর **Trust Boundary**-র কিছু অমিলের কারণে বেশ কয়েকটি **Critical**, **High** এবং **Medium** পর্যায়ের ঝুঁকি পাওয়া গেছে, যেগুলোর বিস্তারিত বিবরণ ও ডিফেন্সিভ সমাধান নিচে তুলে ধরা হলো।

---

## ১. Authentication & Authorization (সেশন, লোকাল স্টোরেজ ও Broken Access Control)

### 🔴 ১.১ [CRITICAL] View-Only (`read_only` / `<module>_edit: false`) পারমিশন বাইপাস (ডাটাবেস RLS বনাম ফ্রন্টএন্ড অমিল)
- **অবস্থান:**
  - `assets/js/auth.js` (Lines 131–264)
  - `supabase_schema.sql` (Lines 353–395, 621–680, 975–1186)
  - `api/gallery-upload.js` (Lines 94–97)
- **সমস্যার মূল কারণ (Root Cause):**
  ফ্রন্টএন্ডে `installSupabaseWriteGuard(client)` নামে একটি জাভাস্ক্রিপ্ট প্রক্সি বসানো হয়েছে, যা ইউজারের `read_only: true` বা `<mod>_edit: false` থাকলে ব্রাউজার UI থেকে `.insert()`, `.update()`, `.delete()` কল ব্লক করে।
  কিন্তু ডাটাবেসের `supabase_schema.sql` (Lines 621–680)-এ `public.hvn_can_edit(mod_key)` ফাংশন তৈরি করা থাকলেও, **Section 5-এর কোনো RLS পলিসিতেই সেটি ব্যবহার করা হয়নি!**
  সব `INSERT`, `UPDATE`, `DELETE` এবং `FOR ALL` পলিসিতে এখনো পুরনো `public.has_haven_perm('<mod>')` ব্যবহার করা হচ্ছে, যা শুধুমাত্র মডিউলের **View ফ্ল্যাগ** (`v_perms ->> perm_key`) চেক করে এবং `read_only` বা `<mod>_edit` ফ্ল্যাগ সম্পূর্ণ উপেক্ষা করে।
- **প্রভাবিত রিসোর্সসমূহ:**
  1. **ডাটাবেস টেবিল:** `store_orders` (L993–999), `server_sales` (L1014–1021), `server_expenses` (L1036–1043), `tickets` (L1057–1063), `ticket_messages` (L1072–1079), `shop_items` (L1094–1096), `gallery` (L1115–1121), `site_settings` (L1162–1169), এবং `staff_members` (L1183–1186)।
  2. **ডাটাবেস ট্রিগার:** `guard_staff_profile_changes()` (L431)-এও `has_haven_perm('users')` চেক করা হয়েছে, `hvn_can_edit('users')` নয়।
  3. **সার্ভারলেস API:** `api/gallery-upload.js` (L94–97)-এ শুধু `profile.permissions.gallery === true` চেক করা হয়েছে; `gallery_edit === false` বা `read_only === true` চেক করা হয়নি।
- **ঝুঁকি (Impact):** কোনো স্টাফকে "View-Only" পারমিশন দেওয়া হলেও সে সরাসরি Supabase REST API বা `/api/gallery-upload`-এ নিজের বৈধ JWT টোকেন পাঠিয়ে অর্ডার ডিলিট, ফিন্যান্স লেজার পরিবর্তন, শপ আইটেম এডিট বা সাইট সেটিংস পরিবর্তন করতে পারবে।

---

### 🟠 ১.২ [HIGH] `/api/server-command.js`-এ অর্ডার ভেরিফিকেশন ছাড়াই `/give` কমান্ড ও ব্ল্যাকলিস্ট সীমাবদ্ধতা
- **অবস্থান:** `api/server-command.js` (Lines 62–66, 144–165)
- **সমস্যার মূল কারণ (Root Cause):**
  1. যাদের কনসোল পারমিশন নেই কিন্তু `orders_edit` পারমিশন আছে, তাদের জন্য `SAFE_DELIVERY_PATTERNS`-এ `/^give\s+[a-zA-Z0-9_.]+\s+[a-zA-Z0-9_:]+\s+\d{1,3}$/i` অনুমতি দেওয়া হয়েছে। এখানে ডাটাবেসে আদৌ কোনো `pending` অর্ডার আছে কি না বা আইটেমটি বৈধ শপ আইটেম কি না তা চেক করা হয় না।
  2. যাদের `console_edit` আছে, তাদের জন্য `OWNER_ONLY_COMMANDS` ব্ল্যাকলিস্টে মাত্র ১৫টি কমান্ড আছে (`op`, `stop`, `lp` ইত্যাদি)। কিন্তু মাইনক্রাফট ও প্লাগইনের বিকল্প কমান্ড (যেমন `gamerule`, `fill`, `setblock`, `ban`, `pardon`, `whitelist`, `essentials:sudo`, `cmi`, অথবা কমান্ডের ভেতর নিউলাইন `\n`) ব্লক করা হয়নি।
- **ঝুঁকি (Impact):** শুধুমাত্র `orders` পারমিশন থাকা যেকোনো মডারেটর সরাসরি `/api/server-command`-এ POST রিকোয়েস্ট পাঠিয়ে যেকোনো প্লেয়ারকে যেকোনো ইন-গেম আইটেম (যেমন `bedrock`, `command_block`, `netherite_block 999`) দিতে পারবে।

---

### 🟠 ১.৩ [HIGH] Pre-Invited Staff Email লিংকিং ও Profile ID Overwrite ঝুঁকি
- **অবস্থান:**
  - `assets/js/auth.js` (Lines 442–457)
  - `supabase_schema.sql` (Lines 421–482, 975–976)
- **সমস্যার মূল কারণ (Root Cause):**
  লগইনের সময় ইউজারের `id` দিয়ে প্রোফাইল না পাওয়া গেলে ফ্রন্টএন্ড কোড `.eq('email', userEmail)` দিয়ে খুঁজে ক্লায়েন্ট সাইড থেকেই `.update({ id: verifiedUser.id }).eq('email', userEmail)` চালায়। এদিকে ডাটাবেসের `guard_staff_profile_changes()` ট্রিগারে `OLD.id IS DISTINCT FROM NEW.id` ব্লক করা নেই এবং `staff_update_policy` যেকোনো `users` পারমিশনধারী স্টাফকে প্রোফাইল রো-তে `UPDATE` চালানোর অনুমতি দেয়।
- **ঝুঁকি (Impact):** প্রাইমারি কী (`id`) ক্লায়েন্ট থেকে আপডেট করার সুযোগ থাকা ঝুঁকিপূর্ণ; এটি শুধুমাত্র সার্ভার-সাইড `SECURITY DEFINER` ট্রিগার (`handle_new_staff_user`) বা `/api/staff-create.js`-এর মাধ্যমে হ্যান্ডেল হওয়া উচিত।

---

### 🟡 ১.৪ [MEDIUM] `/api/server-status.js`-এ Fail-Open অথরাইজেশন চেক
- **অবস্থান:** `api/server-status.js` (Lines 49, 64–76)
- **সমস্যার মূল কারণ (Root Cause):**
  স্টাফের স্ট্যাটাস (`active` কি না) যাচাই করার শর্তটি লেখা হয়েছে `if (!isOwner && serviceKey) { ... }`। অথচ ৪৯ নম্বর লাইনে টোকেন যাচাই করা হচ্ছে `serviceKey || process.env.SUPABASE_ANON_KEY` দিয়ে।
- **ঝুঁকি (Impact):** যদি কখনো Vercel-এ `SUPABASE_SERVICE_KEY` এনভায়রনমেন্ট ভেরিয়েবলটি মিসিং থাকে, তবে ৪৯ নম্বর লাইন পাস করবে কিন্তু ৬৪ নম্বর লাইনের পুরো RBAC চেক স্কিপ হয়ে যাবে (**Fail-Open**)। ফলে যেকোনো `Pending` বা `Suspended` ইউজার সার্ভারের স্ট্যাটাস দেখতে পাবে।

---

### 🟡 ১.৫ [MEDIUM] ব্রাউজারের `localStorage`-এ Session Token সংরক্ষণ
- **অবস্থান:** `assets/js/auth.js` (Lines 510–526)
- **সমস্যার মূল কারণ (Root Cause):**
  Supabase JS SDK ডিফল্টভাবে ব্রাউজারের `localStorage`-এ (`sb-nvcnowvasqcsygtbwktw-auth-token`) অ্যাক্সেস ও রিফ্রেশ টোকেন সংরক্ষণ করে।
- **ঝুঁকি (Impact):** স্ট্যাটিক/SPA আর্কিটেকচারে `localStorage` ব্যবহার স্বাভাবিক হলেও, সাইটে যদি একটিও XSS দুর্বলতা থাকে তবে অ্যাটাকার সরাসরি `localStorage` থেকে JWT টোকেন চুরি করতে পারবে। এজন্য নিচে উল্লেখিত DOM XSS এবং CSP ফিক্স করা অপরিহার্য।

---

## ২. Client-Side Vulnerabilities (DOM-based XSS, ফাইল আপলোড ও স্যানিটাইজেশন)

### 🟠 ২.১ [HIGH] `shop/index.html`-এ আন-স্যানিটাইজড DOM XSS সিঙ্ক
- **অবস্থান:** `shop/index.html` (Lines 1998–2009)
- **সমস্যার মূল কারণ (Root Cause):**
  প্রজেক্টের প্রায় সব পেইজে `window.hvnEscapeHtml()` ব্যবহার করা হলেও, `shop/index.html`-এ স্পনারের আইকন ও রঙের ভ্যালু কোনো স্যানিটাইজেশন ছাড়াই সরাসরি `innerHTML`-এ বসানো হয়েছে:
  ```javascript
  if (sp.icon && sp.icon.startsWith('bi-') && ...) {
    iconHtml = `<i class="bi ${sp.icon}" style="color: ${sp.iconColor || 'var(--info)'}; font-size: 1.15rem;"></i>`;
  } else if (sp.icon && !sp.icon.startsWith('bi-')) {
    iconHtml = `<span style="font-size: 1.15rem; line-height: 1;">${sp.icon}</span>`;
  }
  ```
- **ঝুঁকি (Impact):** `shop_items` টেবিলের `icon` বা `icon_color` কলামে (অথবা `localStorage`-এর `hvn_shop_items`-এ) যদি কোনো HTML/JS পেলোড থাকে, তবে `/shop/` পেইজ ওপেন করলেই Owner-সহ সব স্টাফের ব্রাউজারে জাভাস্ক্রিপ্ট রান করবে (Stored DOM XSS)।

---

### 🟠 ২.২ [HIGH] `/api/gallery-upload.js` ও Supabase Storage-এ আর্বিট্রারি ফাইল টাইপ আপলোড (Stored XSS)
- **অবস্থান:**
  - `api/gallery-upload.js` (Lines 149–164, 184–194)
  - `supabase_schema.sql` (Lines 1192–1226)
- **সমস্যার মূল কারণ (Root Cause):**
  `api/gallery-upload.js`-এ ইউজারের পাঠানো `fileName` থেকে সরাসরি এক্সটেনশন নেওয়া হচ্ছে (`const ext = fileName.split('.').pop() || 'webp'`) এবং `fileType`-কে সরাসরি `Content-Type` হেডারে বসানো হচ্ছে। কোনো এক্সটেনশন বা MIME-type হোয়াইটলিস্ট নেই। একইভাবে `supabase_schema.sql`-এ `gallery` বাকেটেও `allowed_mime_types` নির্ধারণ করা হয়নি।
- **ঝুঁকি (Impact):** গ্যালারি অ্যাক্সেস থাকা কোনো অ্যাকাউন্ট থেকে `.svg` (`image/svg+xml`) বা `.html` (`text/html`) ফাইলের ভেতর জাভাস্ক্রিপ্ট ঢুকিয়ে আপলোড করলে তা পাবলিক Supabase ডোমেইনে হোস্ট হবে এবং Stored XSS তৈরি করবে।

---

### 🟡 ২.৩ [MEDIUM] দুর্বল Content-Security-Policy (CSP) ও CDN স্ক্রিপ্টে SRI না থাকা
- **অবস্থান:**
  - `vercel.json` (Lines 60–61)
  - সকল `.html` ফাইলের `<script src="https://cdn.jsdelivr.net/...">` ট্যাগ
- **সমস্যার মূল কারণ (Root Cause):**
  1. `vercel.json`-এ `script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net;` কনফিগার করা আছে। `'unsafe-inline'` এবং `'unsafe-eval'` চালু থাকায় ব্রাউজারের CSP কোনো ইনলাইন XSS পেলোড আটকাতে পারবে না।
  2. সব HTML পেইজে `@supabase/supabase-js@2` এবং `index.html`-এ `chart.js` লোড করা হয়েছে কোনো **Subresource Integrity (`integrity="sha384-..."`)** এবং নির্দিষ্ট প্যাচ ভার্সন পিনিং ছাড়াই।

---

### 🟡 ২.৪ [LOW/MEDIUM] PostgREST `.or()` ফিল্টার ইনজেকশন ঝুঁকি
- **অবস্থান:**
  - `profile/index.html` (Lines 574, 990)
  - `users/index.html` (Line 2326)
- **সমস্যার মূল কারণ (Root Cause):**
  `profile/index.html`-এ `query.or(\`staff_name.eq."${cleanIgn}",staff_name.eq."${cleanName}"\`)` এবং `users/index.html`-এ `.or(\`email.ilike.${email},minecraft_ign.ilike.${ign}\`)` ব্যবহার করা হয়েছে। শুধু `[,"']` রিমুভ করা হলেও প্যারেনথেসিস `()` বা ডট দিয়ে PostgREST ফিল্টার সিনট্যাক্স ম্যানিপুলেট করা সম্ভব।

---

## ৩. API & Data Exposure (নেটওয়ার্ক ট্যাব ও ডাটাবেজ ফিল্ড লিক)

### 🟠 ৩.১ [HIGH] নেটওয়ার্ক ট্যাবে সব স্টাফের ব্যক্তিগত ইমেইল ও পারমিশন লিক (`staff_profiles` RLS)
- **অবস্থান:**
  - `supabase_schema.sql` (Lines 969–970)
  - `users/index.html` (Lines 2450–2470)
- **সমস্যার মূল কারণ (Root Cause):**
  `users/index.html`-এ জাভাস্ক্রিপ্ট দিয়ে চেক করা হয়েছে যেন শুধুমাত্র Primary Owner অন্যদের ইমেইল ও পারমিশন দেখতে পায় (`const canSeeSensitive = isViewerOwner || ...`)।
  **কিন্তু** এই ফিল্টারিংটি হচ্ছে ব্রাউজারে ডেটা চলে আসার পর! ডাটাবেসের RLS পলিসি `staff_read_self_or_active`-এ লেখা আছে:
  ```sql
  CREATE POLICY "staff_read_self_or_active" ON public.staff_profiles
  FOR SELECT USING (auth.uid() = id OR public.has_haven_perm('any'));
  ```
- **ঝুঁকি (Impact):** যেকোনো সাধারণ স্টাফ (এমনকি যার `users` পেইজের পারমিশনও নেই) ব্রাউজারের **Network Tab** খুললে বা কনসোলে `supabaseClient.from('staff_profiles').select('*')` চালালে Owner-সহ **সব স্টাফের ব্যক্তিগত ইমেইল অ্যাড্রেস, UUID, এবং কার কী পারমিশন আছে তার সম্পূর্ণ JSON** দেখতে পাবে।

---

### 🟡 ৩.২ [MEDIUM] ড্যাশবোর্ডে `SELECT *` কোয়েরির কারণে কাস্টমারদের পেমেন্ট ডেটা অতিরিক্ত এক্সপোজার
- **অবস্থান:** `index.html` (Lines 1131–1136)
- **সমস্যার মূল কারণ (Root Cause):**
  `loadDashboardData()` ফাংশনে শুধুমাত্র সামারি কার্ড ও চার্ট দেখানোর জন্য `store_orders`, `server_sales`, `server_expenses`, এবং `tickets` টেবিলের ওপর কোনো কলাম ফিল্টার বা রো লিমিট ছাড়াই `.select('*')` চালানো হচ্ছে।
- **ঝুঁকি (Impact):** ড্যাশবোর্ড ভিজিট করলেই নেটওয়ার্ক ট্যাবে অতীতের সব অর্ডারের কাস্টমারদের ব্যক্তিগত বিকাশ/নগদ নাম্বার (`sender_number`), `trx_id` এবং ডিসকর্ড আইডি পুরোপুরি ডাউনলোড হয়ে যায়।

---

### 🟡 ৩.৩ [MEDIUM] পাবলিক `gallery` বাকেটে প্রাইভেট সাপোর্ট টিকেটের ভয়েস ও স্ক্রিনশট সংরক্ষণ
- **অবস্থান:** `tickets/index.html` (Lines 2553–2556, 3683–3688)
- **সমস্যার মূল কারণ (Root Cause):**
  সাপোর্ট টিকেটের ভয়েস মেসেজ ও স্ক্রিনশট পাবলিক `gallery` বাকেটের ভেতর (`tickets/voice_...`, `tickets/img_...`) আপলোড করা হচ্ছে।
- **ঝুঁকি (Impact):**
  1. `gallery` বাকেটটি সম্পূর্ণ পাবলিক হওয়ায় টিকেটের সংবেদনশীল স্ক্রিনশট (যেমন পেমেন্ট রিসিট) বা ভয়েস মেসেজ পাবলিকলি অ্যাক্সেসযোগ্য হয়ে থাকে।
  2. যে স্টাফের `tickets` পারমিশন আছে কিন্তু `gallery` পারমিশন নেই, তার ক্ষেত্রে স্টোরেজ আপলোডটি RLS-এ ব্লক হয়ে বিশাল Base64 স্ট্রিং সরাসরি ডাটাবেসের `ticket_messages` কলামে সেভ হচ্ছে।

---

### 🔵 ৩.৪ [LOW] ক্লায়েন্ট কনফিগে প্রক্সি এন্ডপয়েন্ট এক্সপোজার
- **অবস্থান:** `vercel.json` (Lines 60–61)
- **সমস্যার মূল কারণ (Root Cause):**
  CSP `connect-src`-এ `https://qbit-proxy.rajuel55.workers.dev` যুক্ত করা আছে। যেহেতু ব্রাউজার কখনোই সরাসরি এই Cloudflare Worker-এ রিকোয়েস্ট পাঠায় না (শুধুমাত্র Vercel-এর `/api/server-*` ব্যাকএন্ড থেকে কল হয়), তাই এটি ক্লায়েন্ট হেডারে প্রকাশ করার প্রয়োজন নেই।

---

## ৪. CORS, CSRF ও Public Endpoint Abuse

### 🟠 ৪.১ [HIGH] আন-অথেন্টিকেটেড পাবলিক `INSERT` পলিসি, Discord Webhook Spam এবং টিকেট মেসেজ IDOR
- **অবস্থান:** `supabase_schema.sql` (Lines 758–795, 988–989, 1051–1052, 1072–1073)
- **সমস্যার মূল কারণ (Root Cause):**
  1. `orders_insert_public` এবং `tickets_insert_public`-এ `WITH CHECK (true)` দেওয়া আছে। আবার `store_orders` ও `tickets` টেবিলে ইনসার্ট হলেই ট্রিগার `notify_discord_new_order()` ও `notify_discord_new_ticket()` সরাসরি ডিসকর্ড ওয়েবহুকে `net.http_post` কল করে।
  2. `ticket_msgs_insert` পলিসিতে `WITH CHECK (sender = 'player')` দেওয়া আছে। এখানে রিকোয়েস্টকারী আসলেই ওই `ticket_id`-এর মালিক কি না তা যাচাই করা হয় না।
- **ঝুঁকি (Impact):**
  - যে কেউ পাবলিক Supabase Anon Key ব্যবহার করে একটি সাধারণ স্ক্রিপ্টের মাধ্যমে সেকেন্ডে শত শত ভুয়া অর্ডার বা টিকেট ইনসার্ট করে ডাটাবেস এবং স্টাফদের Discord চ্যানেল স্প্যাম/ফ্লাড করে দিতে পারে।
  - যেহেতু `tickets.id` একটি ক্রমিক সংখ্যা (`BIGINT` 1, 2, 3...), তাই যে কেউ `ticket_id` অনুমান করে অন্য যেকোনো প্লেয়ারের ওপেন টিকেটে `'player'` সেজে মেসেজ ইনজেক্ট করতে পারবে (**IDOR**)।

---

### 🟡 ৪.২ [MEDIUM] প্রোডাকশনে Localhost CORS এবং HSTS হেডার না থাকা
- **অবস্থান:**
  - `api/*.js` (যেমন `api/server-command.js#L26`)
  - `vercel.json` (Lines 54–66)
- **সমস্যার মূল কারণ (Root Cause):**
  - সব `/api/*.js` ফাইলে `/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/` অরিজিনকে প্রোডাকশনেও অনুমতি দেওয়া হয়েছে।
  - **CSRF স্ট্যাটাস:** `/api/*` এন্ডপয়েন্টগুলো কুকির বদলে `Authorization: Bearer <token>` হেডার ব্যবহার করে, যা ব্রাউজার স্বয়ংক্রিয়ভাবে ক্রস-সাইট রিকোয়েস্টে পাঠায় না—ফলে প্রথাগত CSRF থেকে আপনার API নিরাপদ।
  - তবে `vercel.json`-এ `Strict-Transport-Security` (HSTS) হেডার যুক্ত করা হয়নি।

---

## ✅ বিস্তারিত সিকিউরিটি চেকলিস্ট (Actionable Security Checklist)

- [x] **P0 (Critical):** `supabase_schema.sql`-এর সব `INSERT`/`UPDATE`/`DELETE`/`FOR ALL` RLS পলিসিতে `public.has_haven_perm('<mod>')`-এর বদলে `public.hvn_can_edit('<mod>')` ব্যবহার করুন (লাইভ ডাটাবেসেও কার্যকর করা হয়েছে)।
- [x] **P0 (Critical):** `api/gallery-upload.js`-এ `profile.permissions.read_only === true` এবং `profile.permissions.gallery_edit === false` চেক যুক্ত করুন।
- [x] **P1 (High):** `staff_profiles` টেবিলের রোল/পারমিশন পরিবর্তন `hvn_can_edit('users')` ও র‍্যাঙ্ক হায়ারার্কিতে লক করুন এবং পেন্ডিং/সাসপেন্ডেড অ্যাকাউন্টের জন্য অন্য স্টাফদের প্রোফাইল দেখা ব্লক করুন।
- [x] **P1 (High):** `api/gallery-upload.js` এবং Supabase `gallery` বাকেটে শুধুমাত্র `['image/webp', 'image/png', 'image/jpeg', 'image/gif']` MIME-type ও এক্সটেনশন হোয়াইটলিস্ট করুন (`.svg` এবং `.html` ব্লক করুন)।
- [x] **P1 (High):** `shop/index.html` (Lines 1998–2009)-এ `sp.icon` এবং `sp.iconColor` রেন্ডার করার আগে `escapeHtml()` ও রেজেক্স ভ্যালিডেশন যুক্ত করুন।
- [x] **P1 (High):** `api/server-command.js`-এ `SAFE_DELIVERY_PATTERNS` থেকে অবাধ `/give` কমান্ড সরিয়ে শুধুমাত্র অনুমোদিত স্টোর র‍্যাঙ্ক ও স্পনার কমান্ড রাখুন, বিপজ্জনক কমান্ডগুলো ব্লকলিস্ট করুন এবং নিউলাইন (`\n`, `\r`) ব্লক করুন।
- [x] **P1 (High):** টিকেট মেসেজে স্টাফ/সিস্টেম রিপ্লাই ও ডিলিট পলিসিতে `public.hvn_can_edit('tickets')` বাধ্যতামূলক করুন।
- [x] **P2 (Medium):** `api/server-status.js`-এ `SUPABASE_SERVICE_KEY` না থাকলে `500` এরর রিটার্ন করুন (Fail-Closed)।
- [x] **P2 (Medium):** `guard_staff_profile_changes()` ট্রিগারে `IF NEW.id IS DISTINCT FROM OLD.id` ব্লক করুন এবং `has_haven_perm('users')`-কে `hvn_can_edit('users')`-এ রূপান্তর করুন।
- [x] **P2 (Medium):** `index.html`-এর ড্যাশবোর্ডে `.select('*')`-এর বদলে শুধুমাত্র প্রয়োজনীয় কলামগুলো সিলেক্ট করুন যাতে কাস্টমারদের `sender_number` ও `trx_id` অপ্রয়োজনে এক্সপোজ না হয়।
- [x] **P2 (Medium):** `vercel.json`-এ `Strict-Transport-Security` হেডার যুক্ত করুন, `script-src` থেকে `'unsafe-eval'` ও `connect-src` থেকে `qbit-proxy` সরান এবং প্রোডাকশনে `localhost` CORS বন্ধ করুন।

---

## 🛠️ ডিফেন্সিভ সমাধান ও Best Practices (Code & SQL Patches)

### ১. Supabase RLS ও Trigger ফিক্স (`supabase_schema.sql`)

```sql
-- ১. প্রোফাইল গার্ড ট্রিগারে ID পরিবর্তন ব্লক এবং hvn_can_edit('users') নিশ্চিত করা
CREATE OR REPLACE FUNCTION public.guard_staff_profile_changes()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RETURN NEW;
  END IF;

  -- প্রাইমারি কী (id) কখনোই ক্লায়েন্ট আপডেট করতে পারবে না
  IF NEW.id IS DISTINCT FROM OLD.id THEN
    RAISE EXCEPTION 'Security Violation: Modifying staff profile UUID is forbidden.';
  END IF;

  -- শুধুমাত্র Owner অথবা যাদের users_edit পারমিশন আছে তারাই রোল/পারমিশন বদলাতে পারবে
  IF NOT public.is_haven_owner() AND NOT public.hvn_can_edit('users') THEN
    IF NEW.role IS DISTINCT FROM OLD.role
       OR NEW.status IS DISTINCT FROM OLD.status
       OR NEW.is_active IS DISTINCT FROM OLD.is_active
       OR NEW.permissions IS DISTINCT FROM OLD.permissions
       OR NEW.email IS DISTINCT FROM OLD.email THEN
      RAISE EXCEPTION 'Security Violation: You are only allowed to update your own display name, IGN, and Discord tag.';
    END IF;
  END IF;

  IF NOT public.is_haven_owner() THEN
    IF LOWER(TRIM(COALESCE(OLD.email, ''))) = 'rajuel55@gmail.com' OR OLD.role = 'Owner' THEN
      RAISE EXCEPTION 'Security Violation: The Primary Owner account cannot be modified by other staff.';
    END IF;
    IF NEW.role = 'Owner' OR (NEW.permissions IS NOT NULL AND COALESCE((NEW.permissions ->> 'users')::boolean, false) = true) THEN
      RAISE EXCEPTION 'Security Violation: Only the Primary Owner can grant Owner role or Staff Management permissions.';
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

-- ২. রাইট (INSERT/UPDATE/DELETE) পলিসিতে has_haven_perm এর বদলে hvn_can_edit ব্যবহার করা
DROP POLICY IF EXISTS "orders_update_staff" ON public.store_orders;
CREATE POLICY "orders_update_staff" ON public.store_orders
FOR UPDATE USING (public.hvn_can_edit('orders')) WITH CHECK (public.hvn_can_edit('orders'));

DROP POLICY IF EXISTS "orders_delete_staff" ON public.store_orders;
CREATE POLICY "orders_delete_staff" ON public.store_orders
FOR DELETE USING (public.hvn_can_edit('orders'));

DROP POLICY IF EXISTS "sales_write_staff" ON public.server_sales;
CREATE POLICY "sales_write_staff" ON public.server_sales
FOR ALL USING (public.hvn_can_edit('finance')) WITH CHECK (public.hvn_can_edit('finance'));

DROP POLICY IF EXISTS "expenses_write_staff" ON public.server_expenses;
CREATE POLICY "expenses_write_staff" ON public.server_expenses
FOR ALL USING (public.hvn_can_edit('finance')) WITH CHECK (public.hvn_can_edit('finance'));

DROP POLICY IF EXISTS "tickets_update_staff" ON public.tickets;
CREATE POLICY "tickets_update_staff" ON public.tickets
FOR UPDATE USING (public.hvn_can_edit('tickets')) WITH CHECK (public.hvn_can_edit('tickets'));

DROP POLICY IF EXISTS "tickets_delete_staff" ON public.tickets;
CREATE POLICY "tickets_delete_staff" ON public.tickets
FOR DELETE USING (public.hvn_can_edit('tickets'));

DROP POLICY IF EXISTS "shop_modify_staff" ON public.shop_items;
CREATE POLICY "shop_modify_staff" ON public.shop_items
FOR ALL USING (public.hvn_can_edit('shop')) WITH CHECK (public.hvn_can_edit('shop'));

DROP POLICY IF EXISTS "gallery_update_staff" ON public.gallery;
CREATE POLICY "gallery_update_staff" ON public.gallery
FOR UPDATE USING (public.hvn_can_edit('gallery')) WITH CHECK (public.hvn_can_edit('gallery'));

DROP POLICY IF EXISTS "gallery_delete_staff" ON public.gallery;
CREATE POLICY "gallery_delete_staff" ON public.gallery
FOR DELETE USING (public.hvn_can_edit('gallery'));

DROP POLICY IF EXISTS "settings_write_staff" ON public.site_settings;
CREATE POLICY "settings_write_staff" ON public.site_settings
FOR ALL USING (public.hvn_can_edit('settings')) WITH CHECK (public.hvn_can_edit('settings'));

DROP POLICY IF EXISTS "staff_members_modify_admin" ON public.staff_members;
CREATE POLICY "staff_members_modify_admin" ON public.staff_members
FOR ALL USING (public.hvn_can_edit('users')) WITH CHECK (public.hvn_can_edit('users'));

-- ৩. গ্যালারি স্টোরেজ বাকেটে শুধুমাত্র নিরাপদ ইমেজ MIME টাইপ ও এডিট পারমিশন নিশ্চিত করা
UPDATE storage.buckets
SET allowed_mime_types = ARRAY['image/webp', 'image/png', 'image/jpeg', 'image/gif'],
    file_size_limit = 5242880
WHERE id = 'gallery';

DROP POLICY IF EXISTS "gallery_Auth_Upload" ON storage.objects;
CREATE POLICY "gallery_Auth_Upload" ON storage.objects
FOR INSERT TO authenticated
WITH CHECK (bucket_id = 'gallery' AND (public.hvn_can_edit('gallery') OR public.hvn_can_edit('tickets')));

DROP POLICY IF EXISTS "gallery_Auth_Delete" ON storage.objects;
CREATE POLICY "gallery_Auth_Delete" ON storage.objects
FOR DELETE TO authenticated
USING (bucket_id = 'gallery' AND public.hvn_can_edit('gallery'));
```

---

### ২. `/api/gallery-upload.js`-এ View-Only চেক ও Strict File Whitelist

```javascript
// ১. View-Only ও gallery_edit চেক যুক্ত করুন (লাইন ৯৪-এর পরিবর্তে):
const perms = profile.permissions || {};
const hasGalleryEdit = Boolean(
  isOwner || (
    isActive &&
    perms.read_only !== true &&
    perms.gallery === true &&
    perms.gallery_edit !== false
  )
);
if (!hasGalleryEdit) {
  return res.status(403).json({
    error: 'Forbidden: You have View-Only access and cannot upload or modify gallery images.'
  });
}

// ২. ফাইল এক্সটেনশন ও MIME-Type হোয়াইটলিস্ট করুন (লাইন ১৪৯-এর পরিবর্তে):
const ALLOWED_MIME_TO_EXT = {
  'image/webp': 'webp',
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/gif': 'gif'
};
const safeMime = ALLOWED_MIME_TO_EXT[fileType] ? fileType : 'image/webp';
const ext = ALLOWED_MIME_TO_EXT[safeMime];
```

---

### ৩. `/api/server-status.js`-এ Fail-Closed অথরাইজেশন চেক

```javascript
if (!isOwner) {
  if (!serviceKey) {
    return res.status(500).json({
      error: 'Server configuration error: SUPABASE_SERVICE_KEY is required for RBAC verification.'
    });
  }
  const profileResp = await fetch(
    `${supabaseUrl}/rest/v1/staff_profiles?id=eq.${encodeURIComponent(authUser.id)}&select=role,status,is_active`,
    { headers: { 'apikey': serviceKey, 'Authorization': `Bearer ${serviceKey}` } }
  );
  const profiles = profileResp.ok ? await profileResp.json() : [];
  const profile = profiles[0];
  const isActive = profile && profile.status === 'active' && profile.is_active === true && profile.role !== 'Pending';
  if (!isActive) {
    return res.status(403).json({ error: 'Forbidden: Active staff privileges required.' });
  }
}
```

---

### ৪. `shop/index.html`-এ DOM XSS স্যানিটাইজেশন (Lines 1998–2009)

```javascript
const safeIcon = escapeHtml(sp.icon || 'bi-box-seam');
const safeIconColor = /^[#a-zA-Z0-9(),.\s%-]+$/.test(String(sp.iconColor || ''))
  ? escapeHtml(sp.iconColor)
  : 'var(--info)';

if (sp.icon && sp.icon.startsWith('bi-') && /^bi-[a-z0-9-]+$/i.test(sp.icon) && sp.icon !== 'bi-skull-fill' && sp.icon !== 'bi-skull') {
  iconHtml = `<i class="bi ${safeIcon}" style="color: ${safeIconColor}; font-size: 1.15rem;"></i>`;
} else if (sp.icon && !sp.icon.startsWith('bi-')) {
  iconHtml = `<span style="font-size: 1.15rem; line-height: 1;">${safeIcon}</span>`;
} else if (sp.name && sp.name.toLowerCase().includes('wither skeleton')) {
  iconHtml = `<span style="font-size: 1.15rem; line-height: 1;" title="Wither Skeleton Spawner">☠️</span>`;
} else if (sp.name && sp.name.toLowerCase().includes('skeleton')) {
  iconHtml = `<span style="font-size: 1.15rem; line-height: 1;" title="Skeleton Spawner">💀</span>`;
} else {
  iconHtml = `<i class="bi bi-box-seam" style="color: ${safeIconColor}; font-size: 1.15rem;"></i>`;
}
```

---

### ৫. `vercel.json`-এ সিকিউরিটি হেডার শক্তিশালীকরণ

```json
{
  "key": "Strict-Transport-Security",
  "value": "max-age=63072000; includeSubDomains; preload"
},
{
  "key": "Content-Security-Policy",
  "value": "default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net; font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net data:; img-src 'self' data: blob: https:; media-src 'self' data: blob: https://nvcnowvasqcsygtbwktw.supabase.co; connect-src 'self' https://nvcnowvasqcsygtbwktw.supabase.co wss://nvcnowvasqcsygtbwktw.supabase.co https://api.mineskin.org https://cdn.jsdelivr.net; frame-ancestors 'none'; base-uri 'self'; form-action 'self';"
}
```
*(নোট: `script-src` থেকে `'unsafe-eval'` এবং `connect-src` থেকে `https://qbit-proxy.rajuel55.workers.dev` সরানো হয়েছে।)*

