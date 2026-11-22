# HavenCraft Admin Dashboard — UI ও Functionality Improvement Plan

## যা verified হয়েছে
পুরনো audit-এর বেশিরভাগ bug ইতিমধ্যে fixed (XSS escaping, spawner logic, RLS, timezone, gallery filter, shop icon, finance sorting, permission masking)। বাকি কাজ + নতুন ২টা feature (Discord Alerts, Finance Charts) + UI polish + code quality — সবই এই plan-এ আছে।

---

## Part 1: Bug Fix — Fake Telemetry আসল data দিয়ে replace

**1a. Dashboard TPS gauge সবসময় 20.0 দেখায় (`index.html:893`)** — mcsrvstat/Pterodactyl কোথাও থেকেই TPS পাওয়া যায় না, তাই এটা সবসময় ভুয়া। Gauge-টা **"CPU Utilization" gauge** বানাব যা `/api/server-status` (Pterodactyl resources) থেকে আসল CPU % নেবে — `utils.js`-এর existing cached helper `hvnFetchServerStatus()` ব্যবহার করে (extra request হবে না)। Offline হলে 0% + OFFLINE label।

**1b. `api/server-status.js:144-151`** — `minecraft_ping` fallback-এ বানানো CPU 24.5% / RAM 4.2GB দেখায়। Null + `telemetry:'basic'` flag return করব যাতে UI "—" দেখায়, আর state/players (যা আসলেই পাওয়া যায়) থাকবে।

## Part 2: নতুন Feature — Finance Analytics Charts (`finance/index.html`)

Finance page-এ নতুন **"Analytics" section** (ledger-এর উপরে), existing data flow (`server_sales`, `store_orders`, `server_expenses` — এই৩টা page-এ already load হয়) থেকে render:

1. **Revenue vs Expenses grouped bar chart** — last 6/12 months toggle (dashboard-এর `crm-bar` pattern-এর মতো custom CSS bar, কোনো নতুন CDN library নয় — CSP-safe এবং theme-consistent)। প্রতি মাসের নিচে net profit/loss দেখাবে।
2. **Expense Category Donut chart** — ৬টা category (Server Hosting, Domain & Web, Plugins & Dev, Marketing, Events, Maintenance) theme-এর ৬টা accent রঙে SVG donut + legend (amount + percentage)।
3. **Period summary strip** — নির্বাচিত সময়ের Total Revenue, Total Expenses, Net Profit, Profit Margin।

Month key হিসাবে existing `hvnFormatLocalMonthKey()` (BD timezone-safe) ব্যবহার হবে।

## Part 3: নতুন Feature — Discord Webhook Alerts

**Architecture:** নতুন order/ticket/staff application **public website** থেকে আসে, admin dashboard থেকে নয় — তাই client-side trigger কাজ করবে না। Supabase-এ `pg_net` extension দিয়ে **database trigger** সবচেয়ে নির্ভরযোগ্য পথ।

**3a. নতুন SQL migration `discord_alerts.sql`:**
- `CREATE EXTENSION IF NOT EXISTS pg_net;`
- নতুন `app_secrets` table (owner-only RLS) — webhook URL ও toggle flags (`orders`, `tickets`, `staff_apps`) এখানে থাকবে, কারণ webhook URL একটা secret আর `site_settings` সব স্টাফ পড়তে পারে
- Trigger: `INSERT ON store_orders` → order/player/item/price/payment method সহ Discord embed
- Trigger: `INSERT ON tickets` → ticket code/player/subject সহ embed
- Trigger: `INSERT ON staff_profiles` (pending হলে) → applicant এর embed
- ৩টা owner-only RPC: `set_discord_alert_config()`, `get_discord_alert_config()` (masked URL return করে), `send_discord_test_alert()`
- Embed content `jsonb_build_object` দিয়ে বানানো — injection-safe

**3b. Settings page UI (`settings/index.html`):**
- নতুন "Discord Alert Notifications" card — **শুধু Owner-কে দেখাবে** (existing owner-check pattern অনুসরণে)
- Webhook URL input (masked), ৩টা toggle switch, Save button, **"Send Test Alert"** button
- সব admin-site call Supabase RPC দিয়ে যাবে — CSP change লাগবে না

**3c. Note:** SQL-টা আমি file হিসেবে দেব; Supabase SQL Editor-এ আপনাকে manually run করতে হবে (আমার কাছে DB credential নেই)। Discord webhook URL Discord channel settings থেকে নিতে হবে।

## Part 4: UI Polish

- **Skeleton loaders:** Dashboard + finance-এর stat card number ও table-এর "Loading... spinner" text-এর বদলে shimmer skeleton block (নতুন `.hvn-skeleton` CSS) — অনেক বেশি professional লাগবে
- CSS version bump `?v=3.3` (cache-bust)

## Part 5: Code Quality

- **৫টা page-এ duplicate `logStaffAction`** (tickets, settings, gallery, shop, orders ~100 লাইন) → `utils.js`-এর existing `hvnLogAudit()`-এর thin wrapper-এ রূপান্তর
- **Unused `assets/js/auth-guard.js` delete** (কোনো page reference করে না — verified)
- **`vercel.json` asset caching:** `/assets/` এর জন্য `max-age=31536000, immutable` (files already `?v=` versioned, তাই safe)

## Execution Order
1. Part 1 (bug fixes — ছোট, আগে করা ভালো)
2. Part 4 (skeleton CSS — পরের feature-গুলোও এটা ব্যবহার করবে)
3. Part 5 (code quality)
4. Part 2 (finance charts)
5. Part 3 (Discord alerts + settings UI + SQL)

## Testing
- Local static server খুলে প্রতিটা page-এর console error ও rendering check করব (login redirect বাদে যা দেখা যায়)
- SQL/DB-dependent অংশের instruction দেব — আপনি Supabase-এ apply করবেন
- Edit-এর পর প্রতিটা পরিবর্তনের জায়গাগুলোয় existing pattern (hvnToast, hvnEscapeHtml, permission gates) follow করা হবে যাতে নতুন কোনো নিরাপত্তা গ্যাপ না তৈরি হয়