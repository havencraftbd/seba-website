# 🎮 HavenCraft SMP — Official Codebase & Developer Guide

<p align="center">
  <img src="logo/logo.png" alt="HavenCraft Logo" width="120" style="border-radius: 50%;" />
</p>

<p align="center">
  <b>The Official Modern Web Platform for the HavenCraft Minecraft Network</b><br>
  High-performance static Single-Page-App experience • Crossplay Java & Bedrock • Supabase Multi-DB Integration • Vercel Serverless Security Layer
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21%2B-brightgreen?logo=minecraft" alt="Minecraft 1.21+">
  <img src="https://img.shields.io/badge/Crossplay-Java%20%26%20Bedrock-blue" alt="Crossplay">
  <img src="https://img.shields.io/badge/Backend-Supabase%20Cloud-3ECF8E?logo=supabase" alt="Supabase">
  <img src="https://img.shields.io/badge/Serverless-Vercel%20Edge%20API-black?logo=vercel" alt="Vercel">
  <img src="https://img.shields.io/badge/PWA-Ready%20%26%20Offline%20Cache-9cf" alt="PWA Ready">
  <img src="https://img.shields.io/badge/Vanilla%20JS-Zero%20Framework%20Overhead-yellow?logo=javascript" alt="Vanilla JS">
</p>

---

## 📖 Table of Contents
1. [Project Overview](#-project-overview)
2. [Codebase Directory Tree](#-codebase-directory-tree)
3. [Core Architecture & State Management](#-core-architecture--state-management)
4. [Serverless API Layer (`/api/*`)](#-serverless-api-layer-api)
5. [In-Game Store Engine (`/store`)](#-in-game-store-engine-store)
6. [Dynamic Component Injection](#-dynamic-component-injection)
7. [Player Avatars & Bedrock Support](#-player-avatars--bedrock-support)
8. [Design System & Styling Guide](#-design-system--styling-guide)
9. [PWA Service Worker & Cache Invalidation](#-pwa-service-worker--cache-invalidation)
10. [Build & Asset Bundling](#-build--asset-bundling)
11. [Environment Variables Reference](#-environment-variables-reference)
12. [Developer Protocols & Guidelines for AI Agents](#-developer-protocols--guidelines-for-ai-agents)

---

## 🌟 Project Overview

**HavenCraft SMP** is a premier Bangladeshi Minecraft community server featuring survival gameplay, custom economy, server ranks, player leaderboards, and seamless crossplay between **Minecraft: Java Edition** and **Minecraft: Bedrock Edition**.

This repository powers the official website:
- **Lightning Fast Performance:** Zero heavy front-end frameworks (React/Vue/Angular). Built with modern Vanilla JS (ES6+), optimized CSS, and native browser APIs.
- **Micro-Services & Serverless:** Vercel Serverless Functions (`/api/*`) manage order verification, contact submissions, server pinging, and gallery moderation with rate limiting and honeypot protection.
- **Triple Supabase Micro-Databases:** Decoupled database instances for **Leaderboards**, **Player Votes**, and **Community Gallery**.
- **Progressive Web App (PWA):** Instantly installable on mobile and desktop with offline asset caching.

---

## 🗺️ Codebase Directory Tree

```yaml
HavenCraft-Website-main/
├── index.html                   # Homepage (Hero, Live Ping, Quick Nav, Leaderboard Preview, CTA)
├── sw.js                        # PWA Service Worker (Handles offline caching & version invalidation)
├── manifest.json                # Web App Manifest for mobile/desktop PWA installation
├── 404.html                     # Custom 404 error page (with redirection fallback)
├── robots.txt                   # Search Engine crawler indexing rules
├── sitemap.xml                  # Website XML sitemap for SEO
├── vercel.json                  # Vercel deployment routes, rewrites, CSP, and caching headers
├── package.json                 # Project npm scripts & Terser dev dependency
├── build.js                     # Asset bundler & CSS minifier script (dist/ generator)
│
├── api/                         # Vercel Serverless Backend Endpoints
│   ├── status.js                # Live Java & Bedrock server ping (mcstatus.io + mcsrvstat.us failover)
│   ├── order.js                 # Store purchase processor (rate-limiting, TrxID validation, Discord embed)
│   ├── contact.js               # Contact form handler (honeypot, anti-spam, Web3Forms integration)
│   ├── submit-gallery.js        # Staff gallery upload gateway (rate-limiting, brute-force lockout, passcode check)
│   └── 404.js                   # JSON fallback handler for invalid API requests
│
├── store/                       # HavenCraft In-Game Store
│   ├── index.html               # Store front-end (Ranks, Spawners, Coins, Keys, BDT/USD currency toggle)
│   └── config.js                # SINGLE SOURCE OF TRUTH: Prices, perks, bkash/nagad numbers, season settings
│
├── vote/                        # Modular Voting System
│   ├── index.html               # Voting page front-end
│   ├── config.js                # Centralized voting site list, cooldowns, and external URLs
│   ├── vote.js                  # Vote claim handlers & Supabase logging
│   ├── cooldown.js              # Client-side localStorage vote cooldown tracker
│   ├── validation.js            # Minecraft username validation (Java & Bedrock formats)
│   ├── ui.js                    # Dynamic toast notifications, countdown timers & grid builder
│   └── supabase.js              # Real-time vote history database connection
│
├── profile/                     # Player Profiles & Stats
│   ├── index.html               # Dedicated profile layout (skin visualizer, stats & badges)
│   └── ranks.js                 # Server ranks mapping & visual rank formulas
│
├── leaderboard/                 # High-Scores & Rankings
│   └── index.html               # Dedicated full server rankings page (Playtime, Balance, Shards, Kills)
│
├── gallery/                     # Community Media Gallery
│   ├── index.html               # Public screenshot viewer & lightbox modal
│   └── upload.html              # Staff upload portal (passcode gated via serverless API)
│
├── about/                       # About Us page (index.html)
├── commands/                    # In-game Command Wiki & Server Guide (index.html)
├── contact/                     # Player Support & Contact Form page (index.html)
├── discord/                     # Discord redirection & community guidelines page (index.html)
├── features/                    # Feature showcase & gameplay guides (index.html)
├── staff/                       # Staff directory, hierarchy & skin visualizers (index.html)
├── rules/                       # Server rules & player guidelines (index.html)
├── terms/                       # Terms of Service & Store policy (index.html)
├── privacy/                     # Privacy Policy (index.html)
│
├── assets/                      # Shared Static Assets
│   ├── css/
│   │   ├── style.css            # PRIMARY STYLESHEET (Design System tokens, glassmorphism, base styles)
│   │   ├── pages.css            # Styles for secondary informational subpages
│   │   ├── animations.css       # Keyframes, glow pulses, float cards & hover transitions
│   │   ├── store.css            # Dedicated store layouts, checkout modals, payment guides
│   │   ├── vote.css             # Vote grid, cooldown badges & platform cards
│   │   ├── profile.css          # Player profile grids, 3D skin renderers & stat counters
│   │   ├── leaderboard.css      # Ranking tables, podium cards & search filtering
│   │   ├── commands.css         # Command wiki accordion & syntax tags
│   │   ├── discord.css          # Discord invite page styling & widget cards
│   │   └── support.css          # Dedicated 24/7 Support Desk styles, ticket wizard & live chatroom
│   │
│   └── js/
│       ├── core.js              # CENTRAL STATE: Exposes window.HavenCraftCore & window.SERVER_CONFIG
│       ├── utils.js             # General utilities (Toast notifications, copy IP, error boundaries)
│       ├── navbar.js            # Dynamic Navbar: Builds header, mobile drawer, gametag login & search
│       ├── components.js        # Dynamic Footer & Support Drawer (hvnMountSupportWizard)
│       ├── home.js              # Homepage scripts (SW register, live ping trigger)
│       ├── store.js             # Store logic: Cart, currency converter (BDT/USD), payment verification modal
│       ├── staff.js             # Staff directory builder with real-time Mojang/Bedrock skin fallback
│       ├── support.js           # 24/7 Support Desk & Live Chat engine (Supabase Realtime + Discord bridge)
│       ├── contact.js           # Contact form AJAX submission & support alias
│       ├── profile.js           # URL query param profile fetcher (`?player=username`) from Supabase
│       ├── leaderboard.js       # Homepage mini-leaderboard preview
│       ├── full-leaderboard.js  # Dedicated ranking page (pagination, search, sorting)
│       ├── gallery.js           # Gallery grid loader & Supabase media viewer
│       ├── upload.js            # Upload form client script (communicates with /api/submit-gallery)
│       └── animations.js        # Page scroll reveals & intersection observers
│
├── scripts/                     # Maintenance & Migration Tooling
│   └── migrate-webp.js          # Sharp-based CLI tool to convert heavy Supabase gallery images to WebP
│
├── docs/                        # Internal Reference Documents
│   └── server-listing-data.md   # Server listing descriptions, tags, and promotional copy
│
├── images/                      # Gameplay screenshots & promotional banners (.webp optimized)
├── logo/                        # Branding assets, favicons & server icons
└── dist/                        # Minified production output (generated by build.js)
```

---

## ⚙️ Core Architecture & State Management

HavenCraft is engineered as a static-first multi-page architecture that achieves the speed and dynamic state sharing of a Single-Page App without framework bloat.

### 1. Central Core State (`assets/js/core.js`)

The `window.HavenCraftCore` global object acts as the primary brain of the frontend:

* **Global Server Configuration:**
  ```javascript
  window.SERVER_CONFIG = {
      ip: 'mc.havencraft.pro',
      port: 2566,
      javaPort: 2566,
      version: '1.21+',
      crossplay: 'Java & Bedrock'
  };
  ```
  Updating this single object automatically updates the IP and port displayed across every page, copy buttons, and fallback pingers.

* **Multi-Instance Supabase Architecture:**
  Maintains three independent Supabase client connections with client-safe `anon` keys:
  * `window.supabaseLeaderboard`: Reads player statistics (Playtime, Kills, Deaths, Money, Shards).
  * `window.supabaseVote`: Tracks player vote logs, claims, and daily stats.
  * `window.supabaseGallery`: Queries approved player screenshots and timestamps.

* **Gametag Session & Authentication:**
  * Stores logged-in player gametag in `localStorage` (`havencraft-gametag`).
  * `HavenCraftCore.login(gametag)` and `HavenCraftCore.logout()`.
  * Dispatches custom DOM events `hc_login` and `hc_logout` to notify the Navbar, Profile view, and Vote pages to update instantly.

---

## 🛡️ Serverless API Layer (`/api/*`)

All privileged operations, external APIs, and secret credentials are kept strictly on the server-side via Vercel Serverless Functions:

| Endpoint | Method | Key Features & Responsibilities |
|---|---|---|
| `/api/status` | `GET` | Fetches real-time server health. Cascades through `mcstatus.io` and `mcsrvstat.us` with a 4.5s timeout. Cleans MOTD formatting and normalizes online player counts. |
| `/api/order` | `POST` | Processes in-game rank/spawner orders. Includes **in-memory IP rate limiting** (5 req/min), anti-bot honeypot check, TrxID validation, generates order IDs (`HC-XXXX`), and sends formatted Discord embeds with admin console commands (`/lp user <player> parent set <rank>`). |
| `/api/contact` | `POST` | Handles player contact inquiries. Enforces IP rate limiting (3 req/min), sanitizes input lengths, and forwards messages to Web3Forms using `WEB3FORMS_ACCESS_KEY`. |
| `/api/submit-gallery` | `POST` | Authenticates staff gallery submissions. Enforces **brute-force defense** (5 failed attempts locks IP for 5 minutes), verifies staff passcodes from server environment variables, validates URLs against the official Supabase storage bucket, and writes to the database. |
| `/api/404` | `ANY` | Clean JSON 404 response handler for non-existent API routes. |

---

## 🛒 In-Game Store Engine (`/store`)

The HavenCraft store is built for zero-maintenance seasonal updates:

### Configuration Single Source of Truth (`store/config.js`)
All store items, pricing, ranks, spawners, and bKash/Nagad phone numbers reside in `window.STORE_CONFIG`. **Never hardcode items in `store/index.html`!**
* **Season Details:** Easily change season name and badge (e.g., `Season 6`).
* **Payment Numbers:** Change bKash/Nagad personal Send Money numbers in one place.
* **Dual Currency Support:** Toggle smoothly between Bangladeshi Taka (`BDT ৳`) and US Dollars (`USD $`).
* **Ranks (Prime, Legend, Elite):** Configurable perks, badge colors, free spawner allocations, and LuckPerms commands.
* **Spawners & Items:** Configured by category, custom icons, stock descriptions, and tiered pricing.

---

## 🧩 Dynamic Component Injection

To eliminate repetitive HTML editing across 15+ subdirectories, layout components are generated dynamically at runtime:

### 1. Global Navbar (`assets/js/navbar.js`)
Injected directly into `<div id="navbar-placeholder"></div>`:
* Auto-detects the active page route (`window.location.pathname`) to highlight the current navigation link.
* Integrated mobile drawer menu with touch-friendly navigation.
* Real-time global player search bar with auto-complete from Supabase database.
* Gametag login modal with persistent MC-Heads skin visualizer.

### 2. Global Footer (`assets/js/components.js`)
Injected directly into `<div id="footer-placeholder"></div>`:
* Standardized links, discord invite button, server IP copy shortcut, dynamic copyright year, and legal policy links.

---

## 👤 Player Avatars & Bedrock Support

HavenCraft is a crossplay network. Both Java Edition and Bedrock Edition (GeyserMC) usernames are supported:

### 1. Username Regex Validation
```javascript
// Validates Java (3-16 chars) and Bedrock (.Prefix up to 24 chars)
function isValidMinecraftUsername(name) {
    return /^[a-zA-Z0-9_.]{3,24}$/.test(name);
}
```

### 2. Avatar Service Integration
* **Primary Avatar Provider:** `https://mc-heads.net/avatar/{username}/{size}`
* **Bedrock Dot Prefix Handling:** For avatar generation, leading dots (`.`) are stripped before requesting the image provider:
  ```javascript
  const cleanName = username.startsWith('.') ? username.substring(1) : username;
  const avatarUrl = `https://mc-heads.net/avatar/${encodeURIComponent(cleanName)}/64`;
  ```
* **Fallback Handling:** Always attach an `onerror` handler falling back to Steve (`https://mc-heads.net/avatar/MHF_Steve/64`).
* **Staff Custom Skins:** In `assets/js/staff.js`, staff members can specify custom Mojang texture URLs (`textures.minecraft.net`) or direct PNG skins for cracked/Bedrock accounts.

---

## 🎨 Design System & Styling Guide

The styling system lives in `assets/css/style.css` and utilizes a refined glassmorphic aesthetic with dark-space tones.

### Color Tokens (`:root` / `[data-theme="dark"]`)

| Variable | Value | Description |
|---|---|---|
| `--primary` | `#00c896` | Vibrant emerald green (buttons, highlights, active states) |
| `--secondary` | `#008f6b` | Dark emerald green (shadow glows, active gradient stops) |
| `--accent` | `#ff8a00` | Vibrant amber/orange (trophies, rank badges, alerts) |
| `--bg` | `#0e1018` | Deep layout background (space obsidian) |
| `--surface` | `rgba(30, 34, 48, 0.72)` | Translucent card surface with `backdrop-filter: blur(12px)` |
| `--surface-hover` | `rgba(40, 46, 64, 0.85)` | Elevated hover state for interactive cards |
| `--border` | `rgba(255, 255, 255, 0.06)` | Subtle card outline border |
| `--border-strong`| `rgba(255, 255, 255, 0.12)` | Focused card border |
| `--text` | `#f1f3f9` | Primary text color |
| `--text-muted` | `#9096a9` | Secondary/descriptive text color |

> **Style Rule:** Do not introduce arbitrary HEX values. Always use CSS custom properties to maintain visual consistency across all pages.

---

## ⚡ PWA Service Worker & Cache Invalidation

The website registers `sw.js` to enable offline resilience and lightning-fast repeated loads.

### ⚠️ Critical Cache Protocol
When you update any CSS file, JS file, or core image, you **must increment the cache version** at the top of `sw.js`:

```javascript
// sw.js (Line 1)
const CACHE_NAME = 'havencraft-cache-v92'; // Increment version here (e.g., v91 -> v92)
```

* **Why this matters:** Browsers aggressively cache PWA assets. Incrementing `CACHE_NAME` immediately purges old caches on active devices and delivers fresh code.
* **Safe Caching Rule:** `sw.js` is configured to **never cache** `/api/*` endpoints, external Supabase queries, or WebSocket connections.

---

## 📦 Build & Asset Bundling

HavenCraft includes a zero-dependency build engine with optional Terser optimization.

### Scripts
In [package.json](file:///package.json):
```bash
# Run one-time bundle & minification
npm run bundle

# Watch files and auto-bundle on change
npm run watch
```

### What `build.js` Does:
1. **CSS Minification:** Strips comments, removes unnecessary whitespace, and generates minified stylesheets in `dist/`.
2. **JS Bundling:** Bundles core scripts (`core.js`, `utils.js`, `navbar.js`, `components.js`, `leaderboard.js`, `home.js`) in dependency order.
3. **Terser AST Compression:** Compresses JavaScript safely while keeping global variables intact (`mangle: false`).

---

## 🔑 Environment Variables Reference

Configure these environment variables in your **Vercel Project Dashboard** (or local `.env` file):

| Variable | Required | Purpose |
|---|---|---|
| `SERVER_IP` | Optional | Server IP to ping in `/api/status` (Default: `mc.havencraft.pro`) |
| `BEDROCK_PORT` | Optional | Bedrock port to ping in `/api/status` (Default: `2566`) |
| `STAFF_PASSCODES_JSON` | Required | JSON string mapping moderator names to secret passcodes for gallery uploads |
| `DISCORD_STORE_WEBHOOK_URL`| Required | Discord Webhook URL where store orders are dispatched |
| `WEB3FORMS_ACCESS_KEY` | Required | Web3Forms access key for the contact form in `/api/contact` |
| `GALLERY_SUPABASE_URL` | Optional | Supabase Project URL for gallery storage |
| `GALLERY_SUPABASE_SERVICE_KEY` | Optional | Supabase Service Role Key for `scripts/migrate-webp.js` |

---

## 🚨 Developer Protocols & Guidelines for AI Agents

All human developers and AI assistants working on HavenCraft must adhere to these rules:

1. **Never Hardcode Store Data:** Modify [store/config.js](file:///store/config.js) exclusively for ranks, prices, spawners, or payment numbers.
2. **Never Duplicate Navbars/Footers:** Always use `<div id="navbar-placeholder"></div>` and `<div id="footer-placeholder"></div>` on new pages.
3. **Prevent XSS Exploits:** Never inject player usernames with `.innerHTML`. Always use `.textContent` or safe DOM node creation:
   ```javascript
   // Safe:
   const nameEl = document.createElement('span');
   nameEl.textContent = player.username;
   ```
4. **Preserve Service Worker Sync:** If you edit cached styles or scripts, immediately increment `CACHE_NAME` in [sw.js](file:///sw.js).
5. **Respect Rate Limits:** Do not bypass serverless rate limiting checks on endpoints (`/api/order`, `/api/contact`, `/api/submit-gallery`).
6. **Support Bedrock Users:** Keep in mind that Bedrock player names can contain dots (`.`) and be up to 24 characters long.

---

<p align="center">
  <b>HavenCraft SMP © 2024–2026. Built with ❤️ for Minecraft players across the globe.</b><br>
  IP: <code>mc.havencraft.pro:2566</code> • Discord: <a href="https://dc.havencraft.pro">dc.havencraft.pro</a>
</p>
