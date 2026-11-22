/* ============================================================================
 * HavenCraft — Unified Ticket Chat, Support Composer, Realtime & Drawer Engine
 * Consumed by both support/index.html (support.js) and the global drawer.
 * ============================================================================ */
(function () {
    'use strict';

    const root = window.getRootPath ? window.getRootPath('assets/js/ticket-chat.js') : './';
    const MAX_RAW_IMAGE_BYTES = 10 * 1024 * 1024;
    const MAX_UPLOAD_BYTES = 2 * 1024 * 1024; // Mirrors api/contact.js 2MB limit

    function esc(str) {
        if (typeof window.escapeHtml === 'function') return window.escapeHtml(str);
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function notify(msg) {
        if (typeof window.showToast === 'function') {
            window.showToast(msg);
        }
    }

    let _cachedClient = null;
    function getTicketSupabaseClient() {
        if (_cachedClient) return _cachedClient;
        if (window.HavenCraftCore && typeof window.HavenCraftCore.getCore === 'function') {
            _cachedClient = window.HavenCraftCore.getCore();
            if (_cachedClient) return _cachedClient;
        }
        const cfg = window.HavenCraftCore?.getSupabaseConfig?.('core');
        if (cfg && typeof window.supabase !== 'undefined' && typeof window.supabase.createClient === 'function') {
            _cachedClient = window.supabase.createClient(cfg.url, cfg.key);
        }
        return _cachedClient;
    }

    function ensureSupabaseSdk() {
        if (typeof window.supabase !== 'undefined') return Promise.resolve(true);
        if (window._hvnSupabaseSdkPromise) return window._hvnSupabaseSdkPromise;

        window._hvnSupabaseSdkPromise = new Promise((resolve) => {
            const existing = document.querySelector('script[src*="@supabase/supabase-js"]');
            if (existing) {
                existing.addEventListener('load', () => resolve(true), { once: true });
                existing.addEventListener('error', () => resolve(false), { once: true });
                return;
            }
            const script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2';
            script.onload = () => resolve(true);
            script.onerror = () => resolve(false);
            document.head.appendChild(script);
        });
        return window._hvnSupabaseSdkPromise;
    }

    // ------------------------------------------------------------------------
    // 1. Support Composer Schema (Categories & Tailored Form Fields)
    // ------------------------------------------------------------------------
    window.HVN_SUPPORT_CATEGORIES = {
        store: {
            id: 'store',
            icon: 'fa-solid fa-credit-card',
            title: { bn: 'স্টোর ও র‍্যাংক', en: 'Store & Ranks' },
            desc: { bn: 'র‍্যাংক ক্রয়, পেমেন্ট ও সুবিধা', en: 'Purchases, perks & payments' },
            subtopics: [
                {
                    id: 'paid_not_received',
                    icon: 'fa-solid fa-receipt',
                    title: { bn: 'টাকা কেটেছে কিন্তু র‍্যাংক পাইনি', en: 'Paid but Rank/Item Not Received' },
                    desc: { bn: 'পেমেন্ট সম্পন্ন হয়েছে কিন্তু ইন-গেম সুবিধা যোগ হয়নি', en: 'Payment completed but rank/perk not credited' },
                    tip: { bn: 'bKash বা Nagad পেমেন্টে সাধারণত ৫-১৫ মিনিট সময় লাগতে পারে। সঠিক TrxID নিচে লিখুন।', en: 'bKash/Nagad verification usually takes 5-15 mins. Enter your exact TrxID below.' },
                    fields: ['ign', 'discordTag', 'email', 'paymentMethod', 'trxId', 'itemPurchased', 'details']
                },
                {
                    id: 'want_to_buy',
                    icon: 'fa-solid fa-cart-shopping',
                    title: { bn: 'র‍্যাংক বা আইটেম কিনতে চাই', en: 'Want to Buy a Rank or Item' },
                    desc: { bn: 'মূল্য তালিকা, অফার বা কেনার নিয়ম জানতে চাই', en: 'Inquire prices, available packages or payment methods' },
                    tip: { bn: 'আমাদের অফিসিয়াল স্টোর havencraft.pro/store/ থেকেও বিস্তারিত দেখতে পারেন।', en: 'You can also explore packages at havencraft.pro/store/' },
                    fields: ['ign', 'discordTag', 'email', 'itemInterested', 'paymentMethod', 'details']
                },
                {
                    id: 'purchase_issue',
                    icon: 'fa-solid fa-triangle-exclamation',
                    title: { bn: 'পেমেন্ট বা TrxID সমস্যা', en: 'Payment or Verification Issue' },
                    desc: { bn: 'ভুল TrxID, পেমেন্ট ফেইল্ড বা ট্রানজেকশন আটকে গেছে', en: 'Wrong TrxID, failed transaction or payment error' },
                    fields: ['ign', 'discordTag', 'email', 'paymentMethod', 'trxId', 'senderNumber', 'details']
                },
                {
                    id: 'other_store',
                    icon: 'fa-solid fa-circle-question',
                    title: { bn: 'অন্যান্য স্টোর জিজ্ঞাসা', en: 'Other Store-related Inquiry' },
                    desc: { bn: 'র‍্যাংক ট্রান্সফার বা সাধারণ পেমেন্ট প্রশ্ন', en: 'Perk transfer, invoice or general store questions' },
                    fields: ['ign', 'discordTag', 'email', 'details']
                }
            ]
        },
        gameplay: {
            id: 'gameplay',
            icon: 'fa-solid fa-compass',
            title: { bn: 'গেমপ্লে ও সাহায্য', en: 'Gameplay & Help' },
            desc: { bn: 'ক্লেইম, রুলস, কয়েন ও আবেদন', en: 'Claims, economy, rules & staff' },
            subtopics: [
                {
                    id: 'gameplay_issues',
                    icon: 'fa-solid fa-cube',
                    title: { bn: 'গেমপ্লে সমস্যা (Claim / Items / Money)', en: 'Gameplay Issues (Claims / Items / Money)' },
                    desc: { bn: 'ল্যান্ড ক্লেইম, ইন-গেম ব্যালেন্স, খোয়া যাওয়া আইটেম বা কমান্ড', en: 'Land claim, lost items, money, commands or perks' },
                    fields: ['ign', 'discordTag', 'email', 'serverMode', 'details']
                },
                {
                    id: 'rules_questions',
                    icon: 'fa-solid fa-circle-info',
                    title: { bn: 'সার্ভার নিয়ম ও খেলার নিয়ম', en: 'Questions & Server Rules' },
                    desc: { bn: 'সার্ভার রুলস, কীভাবে খেলবেন ও ফিচার গাইডলাইন', en: 'Server rules, commands and how to play' },
                    fields: ['ign', 'discordTag', 'email', 'details']
                },
                {
                    id: 'suggestions',
                    icon: 'fa-solid fa-lightbulb',
                    title: { bn: 'পরামর্শ ও ফিডব্যাক (Suggestions)', en: 'Suggestions & Feedback' },
                    desc: { bn: 'সার্ভারের উন্নয়নের জন্য নতুন আইডিয়া বা মতামত', en: 'Ideas to improve the server and community feedback' },
                    fields: ['ign', 'discordTag', 'email', 'details']
                },
                {
                    id: 'staff_apply',
                    icon: 'fa-solid fa-user-shield',
                    title: { bn: 'স্টাফ নিয়োগ ও আবেদন', en: 'Staff Application & Recruitment' },
                    desc: { bn: 'হেল্পার বা মডারেটর পদে আবেদন ও তথ্য', en: 'Helper, moderator applications & requirements' },
                    tip: { bn: 'স্টাফ আবেদনের জন্য সক্রিয় প্লেয়ার এবং ডিসকর্ড ভেরিফাইড হওয়া আবশ্যক।', en: 'Applicants must be active players and verified on Discord.' },
                    fields: ['ign', 'discordTag', 'email', 'age', 'details']
                }
            ]
        },
        technical: {
            id: 'technical',
            icon: 'fa-solid fa-server',
            title: { bn: 'কানেকশন ও বাগ', en: 'Technical & Bugs' },
            desc: { bn: 'কানেক্ট এরর, ল্যাগ ও গ্লিচ', en: "Can't join, lag, crash & bugs" },
            subtopics: [
                {
                    id: 'cannot_join',
                    icon: 'fa-solid fa-plug-circle-xmark',
                    title: { bn: 'গেমে ঢুকতে পারছি না (Can\'t Join)', en: 'Can\'t Join / Connection Error' },
                    desc: { bn: 'টাইমআউট, Whitelist, অথবা ডিসকানেক্ট সমস্যা', en: 'Connection timeout, outdated client or IP error' },
                    tip: { bn: 'সার্ভার IP: mc.havencraft.pro এবং Bedrock পোর্ট: 2025।', en: 'Server IP: mc.havencraft.pro and Bedrock Port: 2025.' },
                    fields: ['ign', 'discordTag', 'email', 'platform', 'errorMessage', 'details']
                },
                {
                    id: 'lag_ping_crash',
                    icon: 'fa-solid fa-gauge-high',
                    title: { bn: 'অতিরিক্ত ল্যাগ / পিং / ক্র্যাশ', en: 'Severe Lag / High Ping / Crash' },
                    desc: { bn: 'অস্বাভাবিক পিং, চাঙ্ক লোডিং সমস্যা বা গেম ক্র্যাশ', en: 'Unusual latency, packet loss or client crashes' },
                    fields: ['ign', 'discordTag', 'email', 'platform', 'isp', 'details']
                },
                {
                    id: 'bug_report',
                    icon: 'fa-solid fa-bug',
                    title: { bn: 'বাগ ও গ্লিচ রিপোর্ট', en: 'Bug Report & Exploits' },
                    desc: { bn: 'ডুপ্লিকেশন, প্লাগইন এরর বা ম্যাপের সমস্যা', en: 'Duplication glitch, broken plugin or map exploit' },
                    tip: { bn: 'জরুরি গ্লিচের বিবরণ পাবলিক চ্যাটে না জানিয়ে এখানে সিকিউরভাবে সাবমিট করুন।', en: 'Report game exploits privately here.' },
                    fields: ['ign', 'discordTag', 'email', 'platform', 'location', 'details']
                }
            ]
        },
        reports: {
            id: 'reports',
            icon: 'fa-solid fa-shield-halved',
            title: { bn: 'রিপোর্ট ও আপিল', en: 'Report & Appeal' },
            desc: { bn: 'হ্যাকার রিপোর্ট ও শাস্তি প্রত্যাহার', en: 'Rule breakers & ban appeals' },
            subtopics: [
                {
                    id: 'report_player',
                    icon: 'fa-solid fa-user-xmark',
                    title: { bn: 'প্লেয়ার রিপোর্ট (Report Player)', en: 'Report Player (Rule Breaker)' },
                    desc: { bn: 'হ্যাকিং, এক্স-রে, গালিগালাজ, স্ক্যাম বা নিয়ম ভঙ্গ', en: 'Cheating, griefing, toxicity, scamming or rule violation' },
                    tip: { bn: 'ভিডিও বা স্ক্রিনশট লিংক (Imgur/YouTube/Drive) দিলে দ্রুত ব্যবস্থা নেওয়া সম্ভব।', en: 'Adding a video or screenshot link helps staff review it faster.' },
                    fields: ['ign', 'discordTag', 'email', 'reportedPlayer', 'reportReason', 'dateTime', 'evidenceLink', 'details']
                },
                {
                    id: 'appeal_ban',
                    icon: 'fa-solid fa-scale-balanced',
                    title: { bn: 'ব্যান বা মিউট প্রত্যাহার আবেদন (Appeal)', en: 'Appeal Ban or Punishment' },
                    desc: { bn: 'ভুলবশত শাস্তি পেলে আনব্যান বা আনমিউটের জন্য আবেদন', en: 'Appeal unjustified mutes, kicks or bans' },
                    fields: ['ign', 'discordTag', 'email', 'punishmentReason', 'dateTime', 'appealReason']
                }
            ]
        },
        partnership: {
            id: 'partnership',
            icon: 'fa-solid fa-handshake',
            title: { bn: 'স্পন্সর ও মিডিয়া', en: 'Partnership & Media' },
            desc: { bn: 'ব্র্যান্ড স্পন্সর ও কনটেন্ট ক্রিয়েটর', en: 'Brand sponsors & creators' },
            subtopics: [
                {
                    id: 'sponsorship',
                    icon: 'fa-solid fa-bullhorn',
                    title: { bn: 'ব্র্যান্ড বা ইভেন্ট স্পন্সরশিপ', en: 'Brand / Event Sponsorship' },
                    desc: { bn: 'সার্ভার ইভেন্ট স্পন্সর, টুর্নামেন্ট বা কমার্শিয়াল অফার', en: 'Tournament sponsor, brand collaboration or commercial proposal' },
                    fields: ['orgName', 'contactPerson', 'discordTag', 'email', 'details']
                },
                {
                    id: 'media_rank',
                    icon: 'fa-solid fa-video',
                    title: { bn: 'মিডিয়া র‍্যাংক / কন্টেন্ট ক্রিয়েটর', en: 'Media Rank / Creator Partner' },
                    desc: { bn: 'ইউটিউব, ফেসবুক বা টিকটক ক্রিয়েটর পার্টনারশিপ', en: 'YouTube, Facebook or TikTok creator perks & media rank' },
                    fields: ['ign', 'channelLink', 'followersCount', 'discordTag', 'email', 'details']
                }
            ]
        }
    };

    window.HVN_SUPPORT_FIELDS = {
        ign: { type: 'text', required: true, label: { bn: 'আপনার Minecraft ইউজারনেম *', en: 'Your Minecraft Username (IGN) *' }, placeholder: { bn: 'যেমন: JU3L বা .BedrockName', en: 'e.g. JU3L or .BedrockName' } },
        email: { type: 'email', required: true, label: { bn: 'আপনার ইমেইল অ্যাড্রেস *', en: 'Your Email Address *' }, placeholder: { bn: 'yourname@gmail.com', en: 'yourname@gmail.com' } },
        paymentMethod: {
            type: 'select',
            required: true,
            label: { bn: 'পেমেন্ট মাধ্যম *', en: 'Payment Method *' },
            options: [
                { value: 'bKash', label: 'bKash (বিকাশ)' },
                { value: 'Nagad', label: 'Nagad (নগদ)' },
                { value: 'Rocket', label: 'Rocket (রকেট)' },
                { value: 'Card/Other', label: 'Bank Card / Other' }
            ]
        },
        trxId: { type: 'text', required: true, label: { bn: 'Transaction ID (TrxID) *', en: 'Transaction ID (TrxID) *' }, placeholder: { bn: 'যেমন: BLX92849182', en: 'e.g. BLX92849182' } },
        itemPurchased: { type: 'text', required: true, label: { bn: 'যে র‍্যাংক বা আইটেম কিনেছেন *', en: 'Purchased Rank / Item *' }, placeholder: { bn: 'যেমন: Prime Rank, Spawners', en: 'e.g. Prime Rank, Spawners' } },
        itemInterested: { type: 'text', required: true, label: { bn: 'যে র‍্যাংক বা প্যাকেজে আগ্রহী *', en: 'Interested Rank / Package *' }, placeholder: { bn: 'যেমন: Elite Rank বা Spawner বান্ডেল', en: 'e.g. Elite Rank or Spawner bundle' } },
        senderNumber: { type: 'text', required: false, label: { bn: 'প্রেরক নম্বর (Sender Number - শেষ ৪ ডিজিট)', en: 'Sender Number (Last 4 digits)' }, placeholder: { bn: 'যেমন: 017XXXX1234', en: 'e.g. 017XXXX1234' } },
        serverMode: {
            type: 'select',
            required: false,
            label: { bn: 'সার্ভার সেকশন / মোড', en: 'Server Section / Mode' },
            options: [
                { value: 'Survival SMP', label: 'Survival SMP' },
                { value: 'Lobby / Spawn', label: 'Lobby / Spawn' },
                { value: 'Other', label: 'Other' }
            ]
        },
        platform: {
            type: 'select',
            required: true,
            label: { bn: 'গেমের সংস্করণ / প্ল্যাটফর্ম *', en: 'Game Edition / Platform *' },
            options: [
                { value: 'Java Edition (PC)', label: 'Java Edition (PC / Mac)' },
                { value: 'Bedrock Edition (Mobile/MCPE)', label: 'Bedrock Mobile (Android / iOS)' },
                { value: 'Bedrock Edition (Windows 10/11)', label: 'Bedrock Windows PC' },
                { value: 'Bedrock Edition (Console/Switch/PS/Xbox)', label: 'Bedrock Console' },
                { value: 'Cracked Client (TLauncher/Pojav)', label: 'Cracked (TLauncher / PojavLauncher)' }
            ]
        },
        errorMessage: { type: 'text', required: false, label: { bn: 'স্ক্রিনে প্রদর্শিত এরর মেসেজ', en: 'Error Message on Screen' }, placeholder: { bn: 'যেমন: Connection timed out, Outdated client', en: 'e.g. Connection timed out, Outdated client' } },
        isp: { type: 'text', required: false, label: { bn: 'আপনার ইন্টারনেট বা সিম প্রোভাইডার', en: 'Your ISP / Network Operator' }, placeholder: { bn: 'যেমন: Grameenphone, Banglalink, Dot Internet', en: 'e.g. Grameenphone, WiFi ISP' } },
        location: { type: 'text', required: false, label: { bn: 'বাগ বা সমস্যার অবস্থান (Coordinates)', en: 'Location / Coordinates (X, Y, Z)' }, placeholder: { bn: 'যেমন: X: 120, Y: 64, Z: -450', en: 'e.g. X: 120, Y: 64, Z: -450' } },
        reportedPlayer: { type: 'text', required: true, label: { bn: 'অভিযুক্ত প্লেয়ারের নাম (Reported IGN) *', en: 'Reported Player Username (IGN) *' }, placeholder: { bn: 'অভিযুক্ত ব্যক্তির সঠিক ইন-গেম নাম', en: 'Exact in-game name of accused player' } },
        reportReason: { type: 'text', required: true, label: { bn: 'অভিযোগের কারণ / নিয়ম লঙ্ঘন *', en: 'Reason / Rule Violated *' }, placeholder: { bn: 'যেমন: Fly hack, X-Ray, Griefing, Scam, Abuse', en: 'e.g. Fly hack, X-Ray, Griefing, Scam, Abuse' } },
        dateTime: { type: 'text', required: true, label: { bn: 'ঘটনার তারিখ ও আনুমানিক সময় *', en: 'Date & Approximate Time *' }, placeholder: { bn: 'যেমন: 09 Oct 2026, রাত ৮:৩০ টা', en: 'e.g. 09 Oct 2026, 8:30 PM' } },
        evidenceLink: { type: 'text', required: false, label: { bn: 'প্রমাণ লিংক (Video / Screenshot Link)', en: 'Evidence Link (Video / Screenshot Link)' }, placeholder: { bn: 'YouTube, Imgur বা Google Drive লিংক', en: 'YouTube, Imgur or Google Drive link' } },
        punishmentReason: { type: 'text', required: true, label: { bn: 'ব্যানের স্ক্রিনে প্রদর্শিত কারণ *', en: 'Punishment Reason Shown on Screen *' }, placeholder: { bn: 'যেমন: Banned for X-Ray, Blacklisted', en: 'e.g. Banned for X-Ray, Blacklisted' } },
        appealReason: { type: 'textarea', required: true, label: { bn: 'কেন আপনার শাস্তি প্রত্যাহার করা উচিত? *', en: 'Why Should Your Punishment Be Revoked? *' }, placeholder: { bn: 'আপনার বক্তব্য সত্য ও সততার সাথে ব্যাখ্যা করুন...', en: 'Explain why this punishment should be lifted...' } },
        orgName: { type: 'text', required: true, label: { bn: 'প্রতিষ্ঠান বা ব্র্যান্ডের নাম *', en: 'Organization / Brand Name *' }, placeholder: { bn: 'যেমন: TechBD, GamerZone', en: 'e.g. Brand / Event Name' } },
        contactPerson: { type: 'text', required: true, label: { bn: 'যোগাযোগকারী ব্যক্তির নাম ও IGN *', en: 'Contact Person & In-game Name *' }, placeholder: { bn: 'নাম ও ইন-গেম ইউজারনেম', en: 'Full name & Minecraft IGN' } },
        channelLink: { type: 'text', required: true, label: { bn: 'ইউটিউব / ফেসবুক / টিকটক পেজ লিংক *', en: 'Channel / Page URL *' }, placeholder: { bn: 'https://youtube.com/@channel', en: 'https://youtube.com/@channel' } },
        followersCount: { type: 'text', required: true, label: { bn: 'সাবস্ক্রাইবার বা ফলোয়ার সংখ্যা *', en: 'Subscribers / Followers Count *' }, placeholder: { bn: 'যেমন: 5,000+ Subs', en: 'e.g. 5,000+ Subscribers' } },
        discordTag: { type: 'text', required: true, label: { bn: 'ডিসকর্ড ইউজারনেম (Discord Username) *', en: 'Discord Username *' }, placeholder: { bn: 'যেমন: notch বা player#1234', en: 'e.g. notch or player#1234' } },
        age: { type: 'text', required: false, label: { bn: 'আপনার বয়স (Age)', en: 'Your Age' }, placeholder: { bn: 'যেমন: 17', en: 'e.g. 17' } },
        details: { type: 'textarea', required: true, label: { bn: 'বিস্তারিত বিবরণ *', en: 'Detailed Description *' }, placeholder: { bn: 'কী সাহায্য দরকার বিস্তারিত বলুন...', en: 'Describe what happened in detail...' } }
    };

    // ------------------------------------------------------------------------
    // 2. Shared Message Formatting, Classification, Storage, Audio & Modals
    // ------------------------------------------------------------------------
    function playChime() {
        try {
            const Ctx = window.AudioContext || window.webkitAudioContext;
            if (!Ctx) return;
            const ctx = new Ctx();
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.type = 'sine';
            osc.frequency.setValueAtTime(587.33, ctx.currentTime);
            osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.15);
            gain.gain.setValueAtTime(0.08, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + 0.3);
        } catch (err) {
            console.warn('[TicketChat] Audio chime unavailable:', err?.message || err);
        }
    }

    function classifyMessageKind(msg) {
        if (!msg) return 'unknown';
        const text = typeof msg.message === 'string' ? msg.message : '';
        if (msg.sender_name === 'HavenCraft Feedback' || /^\s*⭐\s*Player Rating:/i.test(text)) {
            return 'rating';
        }
        if (
            msg.sender_name === 'HavenCraft System' ||
            /^(?:🔒\s*)?(?:This support ticket was closed|Ticket resolved and closed)/i.test(text) ||
            text.includes('closed by staff') ||
            text.includes('closed by user') ||
            text.includes('support ticket was closed')
        ) {
            return 'close';
        }
        if (msg.sender === 'system') return 'system';
        if (['player', 'user', 'customer', 'client'].includes(msg.sender)) return 'player';
        return 'staff';
    }

    function deduplicateMessages(list) {
        if (!Array.isArray(list)) return [];
        let seenClose = false;
        let seenRating = false;
        const seenIds = new Set();

        return list.filter(m => {
            if (!m) return false;
            if (m.id) {
                const key = String(m.id);
                if (seenIds.has(key)) return false;
                seenIds.add(key);
            }
            const kind = classifyMessageKind(m);
            if (kind === 'close') {
                if (seenClose) return false;
                seenClose = true;
            }
            if (kind === 'rating') {
                if (seenRating) return false;
                seenRating = true;
            }
            return true;
        });
    }

    function getActiveTicket({ allowClosed = false } = {}) {
        try {
            const raw = localStorage.getItem('hvn_active_ticket');
            if (!raw) return null;
            const parsed = JSON.parse(raw);
            if (!parsed || !parsed.ticket_code) return null;
            if (parsed.status === 'closed' && !allowClosed) {
                localStorage.removeItem('hvn_active_ticket');
                return null;
            }
            return parsed;
        } catch (err) {
            console.warn('[TicketChat] Failed to parse active ticket:', err?.message || err);
            return null;
        }
    }

    function saveActiveTicket(ticket) {
        if (!ticket) return;
        try {
            localStorage.setItem('hvn_active_ticket', JSON.stringify({
                id: ticket.id,
                ticket_code: ticket.ticket_code,
                sessionToken: ticket.sessionToken || '',
                user_name: ticket.user_name,
                user_email: ticket.user_email || '',
                subject: ticket.subject,
                status: ticket.status || 'open'
            }));
        } catch (err) {
            console.warn('[TicketChat] Failed to persist active ticket:', err?.message || err);
        }
    }

    function clearActiveTicket(ticketId) {
        try {
            if (ticketId) localStorage.removeItem(`hvn_chat_cache_${ticketId}`);
            localStorage.removeItem('hvn_active_ticket');
        } catch (err) {
            console.warn('[TicketChat] Failed to clear ticket storage:', err?.message || err);
        }
    }

    function readChatCache(ticketId) {
        if (!ticketId) return [];
        try {
            const raw = localStorage.getItem(`hvn_chat_cache_${ticketId}`);
            if (!raw) return [];
            const parsed = JSON.parse(raw);
            return deduplicateMessages(parsed);
        } catch {
            return [];
        }
    }

    function writeChatCache(ticketId, messages) {
        if (!ticketId || !Array.isArray(messages)) return [];
        const deduped = deduplicateMessages(messages);
        try {
            localStorage.setItem(`hvn_chat_cache_${ticketId}`, JSON.stringify(deduped));
        } catch (err) {
            console.warn('[TicketChat] Failed to write chat cache:', err?.message || err);
        }
        return deduped;
    }

    function appendChatCache(ticketId, msg) {
        if (!ticketId || !msg) return;
        const current = readChatCache(ticketId);
        current.push(msg);
        writeChatCache(ticketId, current);
    }

    function getSavedRating(ticketId, messages) {
        if (!ticketId) return null;
        try {
            const local = localStorage.getItem(`hvn_rated_${ticketId}`);
            if (local) return parseInt(local, 10) || 5;
        } catch { }
        if (Array.isArray(messages)) {
            const ratingMsg = messages.find(m => classifyMessageKind(m) === 'rating');
            if (ratingMsg && typeof ratingMsg.message === 'string') {
                const match = ratingMsg.message.match(/(\d+)\/5/);
                return match ? parseInt(match[1], 10) : 5;
            }
        }
        return null;
    }

    async function fetchTicketViaApi({ ticketCode, sessionToken = '', email = '' }) {
        const res = await fetch('/api/contact', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                action: 'get',
                ticketCode,
                sessionToken,
                email
            })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok && data.success && data.ticket) {
            if (data.sessionToken) data.ticket.sessionToken = data.sessionToken;
            else if (sessionToken) data.ticket.sessionToken = sessionToken;
        }
        return { ok: res.ok, status: res.status, data };
    }

    async function sendTicketReply({ ticket, message, senderName }) {
        const res = await fetch('/api/contact', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                action: 'reply',
                ticketId: ticket.id,
                sessionToken: ticket.sessionToken || '',
                message,
                senderName: senderName || ticket.user_name || 'Player'
            })
        });
        const data = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, data };
    }

    async function closeTicketOnServer({ ticket, rating, feedback }) {
        const payload = {
            action: 'close',
            ticketId: ticket.id,
            sessionToken: ticket.sessionToken || ''
        };
        if (rating) payload.rating = rating;
        if (feedback) payload.feedback = feedback;

        const res = await fetch('/api/contact', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, data };
    }

    function fileToBase64(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = reject;
            reader.readAsDataURL(file);
        });
    }

    function formatFileSize(bytes) {
        if (!bytes || bytes < 1024) return `${bytes || 0} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    function compressImageForUpload(file, maxDim = 1600, quality = 0.80) {
        return new Promise((resolve) => {
            if (!file || !file.type.startsWith('image/') || file.type === 'image/gif') {
                return resolve(file);
            }
            const reader = new FileReader();
            reader.onload = (e) => {
                const img = new Image();
                img.onload = () => {
                    let { width, height } = img;
                    if (width > maxDim || height > maxDim) {
                        if (width > height) {
                            height = Math.round((height * maxDim) / width);
                            width = maxDim;
                        } else {
                            width = Math.round((width * maxDim) / height);
                            height = maxDim;
                        }
                    }
                    const canvas = document.createElement('canvas');
                    canvas.width = width;
                    canvas.height = height;
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(img, 0, 0, width, height);
                    canvas.toBlob((blob) => {
                        if (blob && blob.size < file.size) {
                            const ext = blob.type === 'image/webp' ? '.webp' : '.jpg';
                            resolve(new File([blob], (file.name || 'screenshot').replace(/\.[^.]+$/, '') + ext, {
                                type: blob.type || 'image/webp'
                            }));
                        } else {
                            resolve(file);
                        }
                    }, 'image/webp', quality);
                };
                img.onerror = () => resolve(file);
                img.src = e.target.result;
            };
            reader.onerror = () => resolve(file);
            reader.readAsDataURL(file);
        });
    }

    async function stageImageAttachment(file, previewEls = {}) {
        if (!file || !file.type.startsWith('image/')) return null;
        if (file.size > MAX_RAW_IMAGE_BYTES) {
            notify('ছবিটি অনেক বড় (সর্বোচ্চ ১০MB)। অনুগ্রহ করে ছোট ছবি দিন।');
            return null;
        }
        const compressed = await compressImageForUpload(file);
        if (compressed.size > MAX_UPLOAD_BYTES) {
            notify('কম্প্রেস করার পরও ছবিটি 2MB-এর বেশি। দয়া করে ছোট স্ক্রিনশট দিন।');
            return null;
        }
        const { previewEl, thumbEl, nameEl, sizeEl } = previewEls;
        if (previewEl && thumbEl && nameEl && sizeEl) {
            if (thumbEl.dataset.blobUrl) {
                try { URL.revokeObjectURL(thumbEl.dataset.blobUrl); } catch { }
            }
            const objUrl = URL.createObjectURL(compressed);
            thumbEl.dataset.blobUrl = objUrl;
            thumbEl.src = objUrl;
            nameEl.textContent = file.name || 'screenshot.webp';
            sizeEl.textContent = formatFileSize(compressed.size);
            previewEl.style.display = 'flex';
        }
        return compressed;
    }

    function clearAttachmentPreview(previewEls = {}) {
        const { previewEl, thumbEl, fileInput } = previewEls;
        if (previewEl) previewEl.style.display = 'none';
        if (thumbEl) {
            if (thumbEl.dataset.blobUrl) {
                try { URL.revokeObjectURL(thumbEl.dataset.blobUrl); } catch { }
                delete thumbEl.dataset.blobUrl;
            }
            thumbEl.src = '';
        }
        if (fileInput) fileInput.value = '';
    }

    async function uploadTicketAttachment(blobOrFile, ticket) {
        const b64 = await fileToBase64(blobOrFile);
        const upRes = await fetch('/api/contact', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                action: 'upload',
                ticketId: ticket.id,
                sessionToken: ticket.sessionToken || '',
                fileBase64: b64
            })
        });
        const upData = await upRes.json().catch(() => ({}));
        if (!upRes.ok || !upData.success || !upData.url) {
            throw new Error(upData.error || 'Upload failed');
        }
        return upData.url;
    }

    function mountCsatWidget(containerEl, { ticket, existingRating = null, compact = false } = {}) {
        if (!containerEl || !ticket) return;
        const ticketId = ticket.id;
        const ratedKey = `hvn_rated_${ticketId}`;
        const saved = existingRating || getSavedRating(ticketId, []);

        const renderThanks = (stars) => {
            containerEl.innerHTML = `
                <div class="csat-rating-box" style="width: 100%; box-sizing: border-box; text-align: center; padding: ${compact ? '10px 12px' : '12px 14px'}; border-radius: 10px; background: rgba(20, 184, 166, 0.12); border: 1px solid rgba(20, 184, 166, 0.35);">
                    <div style="font-size: ${compact ? '0.78rem' : '0.82rem'}; color: #F06A4E; font-weight: 600; margin-bottom: 4px;">
                        <i class="fa-solid fa-lock" aria-hidden="true"></i> এই টিকিটটি সমাধান ও বন্ধ করা হয়েছে
                    </div>
                    <span style="color: #2DD4BF; font-weight: 600; font-size: ${compact ? '0.84rem' : '0.9rem'};">
                        <i class="fa-solid fa-circle-check" aria-hidden="true"></i> মতামত দেওয়ার জন্য ধন্যবাদ! (${stars}/5 Stars)
                    </span>
                </div>
            `;
        };

        if (saved) {
            renderThanks(saved);
            return;
        }

        containerEl.innerHTML = `
            <div class="csat-rating-box" style="width: 100%; box-sizing: border-box; text-align: center; padding: ${compact ? '10px 12px' : '12px 14px'}; border-radius: 10px; background: var(--surface-recessed); border: 1px solid var(--border);">
                <div style="font-size: ${compact ? '0.78rem' : '0.82rem'}; color: #F06A4E; font-weight: 600; margin-bottom: 4px;">
                    <i class="fa-solid fa-lock" aria-hidden="true"></i> এই টিকিটটি সমাধান ও বন্ধ করা হয়েছে
                </div>
                <div style="font-size: ${compact ? '0.75rem' : '0.84rem'}; color: var(--text-secondary); margin-bottom: 6px;">সার্ভিস কেমন ছিল? রেটিং দিন:</div>
                <div class="csat-stars" style="display: flex; justify-content: center; gap: 8px; font-size: ${compact ? '1.3rem' : '1.35rem'}; color: #F59E0B;">
                    ${[1, 2, 3, 4, 5].map(val => `<button type="button" class="csat-star-btn" data-val="${val}" aria-label="Rate ${val} out of 5 stars"><i class="fa-solid fa-star csat-star" aria-hidden="true"></i></button>`).join('')}
                </div>
            </div>
        `;

        const starBtns = containerEl.querySelectorAll('.csat-star-btn');
        let submitted = false;

        starBtns.forEach(btn => {
            btn.addEventListener('mouseenter', () => {
                if (submitted) return;
                const val = parseInt(btn.getAttribute('data-val'), 10);
                starBtns.forEach((s, idx) => {
                    s.style.opacity = idx < val ? '1' : '0.35';
                    s.style.transform = idx < val ? 'scale(1.15)' : 'scale(1)';
                });
            });

            btn.addEventListener('click', async () => {
                if (submitted) return;
                submitted = true;
                const val = parseInt(btn.getAttribute('data-val'), 10) || 5;
                try { localStorage.setItem(ratedKey, String(val)); } catch { }
                renderThanks(val);
                try {
                    await closeTicketOnServer({ ticket, rating: val });
                } catch (err) {
                    console.warn('[TicketChat] Failed to send rating:', err?.message || err);
                }
            });
        });
    }

    function setupChatInputBehaviors(textareaEl, { maxHeight = 120, onSubmit, onImagePaste, onResize } = {}) {
        if (!textareaEl) return;

        textareaEl.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                if (typeof onSubmit === 'function') onSubmit();
            }
        });

        textareaEl.addEventListener('paste', (e) => {
            const items = (e.clipboardData || e.originalEvent?.clipboardData)?.items;
            if (!items) return;
            for (const item of items) {
                if (item.type.indexOf('image') !== -1) {
                    const blob = item.getAsFile();
                    if (blob && typeof onImagePaste === 'function') {
                        onImagePaste(blob);
                        e.preventDefault();
                        break;
                    }
                }
            }
        });

        textareaEl.addEventListener('input', () => {
            textareaEl.style.height = 'auto';
            const nextH = Math.min(textareaEl.scrollHeight, maxHeight);
            textareaEl.style.height = `${Math.max(36, nextH)}px`;
            textareaEl.style.overflowY = textareaEl.scrollHeight > maxHeight ? 'auto' : 'hidden';
            if (typeof onResize === 'function') onResize(textareaEl.value);
        });
    }

    async function subscribeTicketRealtime(ticketId, channelKey, callbacks = {}) {
        const sdkOk = await ensureSupabaseSdk();
        const sb = sdkOk ? getTicketSupabaseClient() : null;
        if (!sb) return null;

        const channelName = `${channelKey}-${ticketId}`;
        const channel = sb.channel(channelName)
            .on('postgres_changes', {
                event: 'INSERT',
                schema: 'public',
                table: 'ticket_messages',
                filter: `ticket_id=eq.${ticketId}`
            }, (payload) => {
                const msg = payload.new;
                if (!msg || msg.sender === 'player') return;
                if (typeof callbacks.onRemoteMessage === 'function') {
                    callbacks.onRemoteMessage(msg);
                }
                if (classifyMessageKind(msg) === 'close' && typeof callbacks.onTicketClosed === 'function') {
                    callbacks.onTicketClosed(msg);
                }
            })
            .on('postgres_changes', {
                event: 'UPDATE',
                schema: 'public',
                table: 'tickets',
                filter: `id=eq.${ticketId}`
            }, (payload) => {
                if (payload.new && payload.new.status === 'closed' && typeof callbacks.onTicketClosed === 'function') {
                    callbacks.onTicketClosed(payload.new);
                }
            })
            .subscribe();

        return {
            unsubscribe() {
                try { sb.removeChannel(channel); } catch { }
            }
        };
    }

    // ------------------------------------------------------------------------
    // 3. Global Audio Player, Lightbox, Discord Markdown & Confirm Modal
    // ------------------------------------------------------------------------
    window.__hvnPlayingAudio = null;
    window.__hvnPlayingBtn = null;
    window.__hvnPlayingContainer = null;

    window.togglePlayVoice = function (btn) {
        if (!btn) return;
        const container = btn.closest('.voice-message-player');
        if (!container) return;
        const audioSrc = container.getAttribute('data-audio-src');
        const timeEl = container.querySelector('.voice-player-time');

        if (window.__hvnPlayingAudio && window.__hvnPlayingBtn === btn) {
            if (!window.__hvnPlayingAudio.paused) {
                window.__hvnPlayingAudio.pause();
                btn.innerHTML = '<i class="fa-solid fa-play"></i>';
                container.classList.remove('is-playing');
            } else {
                window.__hvnPlayingAudio.play().catch(e => console.warn('Play error:', e));
                btn.innerHTML = '<i class="fa-solid fa-pause"></i>';
                container.classList.add('is-playing');
            }
            return;
        }

        if (window.__hvnPlayingAudio) {
            window.__hvnPlayingAudio.pause();
            if (window.__hvnPlayingBtn) window.__hvnPlayingBtn.innerHTML = '<i class="fa-solid fa-play"></i>';
            if (window.__hvnPlayingContainer) window.__hvnPlayingContainer.classList.remove('is-playing');
        }

        const audio = new Audio(audioSrc);
        window.__hvnPlayingAudio = audio;
        window.__hvnPlayingBtn = btn;
        window.__hvnPlayingContainer = container;

        btn.innerHTML = '<i class="fa-solid fa-pause"></i>';
        container.classList.add('is-playing');

        const cleanupAudio = () => {
            btn.innerHTML = '<i class="fa-solid fa-play"></i>';
            container.classList.remove('is-playing');
            window.__hvnPlayingAudio = null;
            window.__hvnPlayingBtn = null;
            window.__hvnPlayingContainer = null;
        };

        audio.ontimeupdate = () => {
            if (!window.__hvnPlayingAudio || !timeEl) return;
            const cur = Math.floor(audio.currentTime || 0);
            timeEl.textContent = `${Math.floor(cur / 60)}:${String(cur % 60).padStart(2, '0')}`;
        };
        audio.onended = () => {
            if (timeEl) {
                const dur = Math.floor(audio.duration || 0);
                timeEl.textContent = `${Math.floor(dur / 60)}:${String(dur % 60).padStart(2, '0')}`;
            }
            cleanupAudio();
        };
        audio.onerror = cleanupAudio;
        audio.play().catch(cleanupAudio);
    };

    window.openLightbox = function (src) {
        if (!src) return;
        let modal = document.getElementById('chatLightboxModal');
        let img = document.getElementById('chatLightboxImg');

        if (!modal) {
            modal = document.createElement('div');
            modal.id = 'chatLightboxModal';
            modal.className = 'chat-lightbox-modal';
            modal.addEventListener('click', (e) => window.closeLightbox(e));
            modal.innerHTML = `
                <button type="button" class="chat-lightbox-close" aria-label="Close Lightbox">
                    <i class="fa-solid fa-xmark" aria-hidden="true"></i>
                </button>
                <img id="chatLightboxImg" src="" alt="Enlarged Screenshot" class="chat-lightbox-img" />
            `;
            document.body.appendChild(modal);
            modal.querySelector('.chat-lightbox-close')?.addEventListener('click', () => window.closeLightbox());
            img = document.getElementById('chatLightboxImg');
        }

        if (img) img.src = src;
        modal.classList.add('show');
    };

    window.closeLightbox = function (e) {
        if (e && e.target && e.target.id === 'chatLightboxImg') return;
        const modal = document.getElementById('chatLightboxModal');
        if (modal) modal.classList.remove('show');
    };

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            const modal = document.getElementById('chatLightboxModal');
            if (modal && modal.classList.contains('show')) modal.classList.remove('show');
        }
    });

    window.hvnFormatChatMessage = function (text) {
        if (!text) return '';

        const rawText = String(text)
            .replace(/\[(?:ESCALATE|HANDOVER|STAFF_PENDING_REMINDER)[^\]]*\]/gi, '')
            .replace(/@(Admin|Owner|Mod)\b/gi, '')
            .trim();

        const voiceMatch = rawText.match(/^\[voice:([\s\S]+?)(?::(\d+))?\]$/);
        if (voiceMatch) {
            const audioSrc = voiceMatch[1].trim();
            const durationSec = parseInt(voiceMatch[2] || '0', 10);
            const durStr = durationSec ? `${Math.floor(durationSec / 60)}:${String(durationSec % 60).padStart(2, '0')}` : '0:15';
            const barHeights = [10, 16, 24, 14, 8, 20, 26, 16, 11, 22, 18, 9, 15, 21, 13, 8, 17, 23];

            return `
                <div class="voice-message-player" data-audio-src="${esc(audioSrc)}">
                    <button type="button" class="voice-player-btn" onclick="window.togglePlayVoice(this)" title="Play voice message">
                        <i class="fa-solid fa-play" aria-hidden="true"></i>
                    </button>
                    <div class="voice-player-waveform">
                        ${barHeights.map(h => `<span class="wp-bar" style="height: ${h}px;"></span>`).join('')}
                    </div>
                    <span class="voice-player-time">${durStr}</span>
                </div>
            `;
        }

        const formatEmojis = (s) => s
            .replace(/&lt;(a)?:([a-zA-Z0-9_]{2,32}):(\d{15,22})&gt;/gi, (m, anim, name, id) => {
                const ext = anim ? 'gif' : 'png';
                return `<img class="chat-custom-emoji" src="https://cdn.discordapp.com/emojis/${id}.${ext}?size=48" alt=":${name}:" title=":${name}:" loading="lazy" />`;
            })
            .replace(/<(a)?:([a-zA-Z0-9_]{2,32}):(\d{15,22})>/gi, (m, anim, name, id) => {
                const ext = anim ? 'gif' : 'png';
                return `<img class="chat-custom-emoji" src="https://cdn.discordapp.com/emojis/${id}.${ext}?size=48" alt=":${name}:" title=":${name}:" loading="lazy" />`;
            });

        const imgTagMatch = text.match(/\[image:([\s\S]+?)\]/i);
        const directUrlMatch = text.match(/(https?:\/\/(?!cdn\.discordapp\.com\/emojis\/)[^\s<>"'\)]+?\.(?:png|jpe?g|gif|webp)(?:\?[^\s<>"'\)]*)?)/i);

        if (imgTagMatch || directUrlMatch) {
            const fullMatch = imgTagMatch ? imgTagMatch[0] : directUrlMatch[0];
            const rawSrc = (imgTagMatch ? imgTagMatch[1] : directUrlMatch[0]).trim();
            const isSafeImage = /^https?:\/\/[^\s"'<>]+/i.test(rawSrc) || /^data:image\/(?:png|jpe?g|gif|webp);base64,[a-z0-9+/=]+$/i.test(rawSrc);

            if (isSafeImage) {
                const caption = rawText.replace(fullMatch, '').trim();
                const captionHtml = caption
                    ? `<div class="chat-img-caption">${formatEmojis(esc(caption)).replace(/\n/g, '<br>')}</div>`
                    : '';
                const safeJsArg = JSON.stringify(rawSrc).replace(/"/g, '&quot;');

                return `
                    <button type="button" class="chat-media-card" onclick="window.openLightbox(${safeJsArg})" aria-label="Enlarge attachment image" style="background: transparent; padding: 0; width: 100%;">
                        <img src="${esc(rawSrc)}" class="chat-media-img" alt="Attachment" loading="lazy" />
                        <div class="chat-media-hover-overlay">
                            <i class="fa-solid fa-expand" aria-hidden="true"></i>
                        </div>
                    </button>
                    ${captionHtml}
                `;
            }
        }

        let res = esc(rawText)
            .replace(/```(?:([a-zA-Z0-9_+-]+)\n)?([\s\S]*?)```/g, (m, lang, code) => `<pre class="chat-code-block"><code>${code.trim()}</code></pre>`)
            .replace(/`([^`\n]+)`/g, '<code class="chat-inline-code">$1</code>')
            .replace(/\[([^\]]+)\]\((https?:\/\/[^\s<>"']+)\)/gi, '<a href="$2" target="_blank" rel="noopener noreferrer" class="chat-link">$1</a>')
            .replace(/(^|[\s(])(https?:\/\/[^\s<>"')]+)/gi, '$1<a href="$2" target="_blank" rel="noopener noreferrer" class="chat-link">$2</a>')
            .replace(/\*\*\*([^*]+)\*\*\*/g, '<strong><em>$1</em></strong>')
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/(^|[^*])\*([^*\n]+)\*([^*]|$)/g, '$1<em>$2</em>$3')
            .replace(/(^|[^_])_([^_\n]+)_([^_]|$)/g, '$1<em>$2</em>$3')
            .replace(/~~([^~]+)~~/g, '<del>$1</del>')
            .replace(/(?:^|\n)&gt; (.*?)(?=\n|$)/g, '\n<blockquote class="chat-quote">$1</blockquote>')
            .replace(/(?:^|\n)[ \t]*(\d+)\.[ \t]+(.*?)(?=\n|$)/g, '\n<div class="chat-list-item chat-num-item"><span class="chat-list-num">$1.</span> <span class="chat-list-text">$2</span></div>')
            .replace(/(?:^|\n)[ \t]*[-*•][ \t]+(.*?)(?=\n|$)/g, '\n<div class="chat-list-item"><span class="chat-list-bullet">•</span> <span class="chat-list-text">$1</span></div>');

        res = formatEmojis(res)
            .replace(/\n+(?=<div class="chat-list-item)/g, '')
            .replace(/(<\/div>)\n+/g, '$1')
            .replace(/(<\/pre>)\n+/g, '$1')
            .replace(/(<\/blockquote>)\n+/g, '$1')
            .replace(/\n/g, '<br>');

        return res.trim();
    };

    window.hvnShowConfirmModal = function ({ title, message, confirmText, cancelText, onConfirm }) {
        let modalOverlay = document.getElementById('hvnConfirmModalOverlay');
        if (!modalOverlay) {
            modalOverlay = document.createElement('div');
            modalOverlay.id = 'hvnConfirmModalOverlay';
            modalOverlay.className = 'hvn-custom-modal-overlay';
            modalOverlay.setAttribute('role', 'dialog');
            modalOverlay.setAttribute('aria-modal', 'true');
            modalOverlay.setAttribute('aria-labelledby', 'hvnModalTitle');
            modalOverlay.innerHTML = `
                <div class="hvn-custom-modal-box">
                    <div class="hvn-modal-icon-wrap">
                        <i class="fa-solid fa-lock" aria-hidden="true"></i>
                    </div>
                    <h3 class="hvn-modal-title" id="hvnModalTitle"></h3>
                    <p class="hvn-modal-desc" id="hvnModalDesc"></p>
                    <div class="hvn-modal-actions">
                        <button type="button" class="hvn-modal-btn hvn-modal-btn-cancel" id="hvnModalCancelBtn">বাতিল করুন</button>
                        <button type="button" class="hvn-modal-btn hvn-modal-btn-confirm" id="hvnModalConfirmBtn">হ্যাঁ, বন্ধ করুন</button>
                    </div>
                </div>
            `;
            document.body.appendChild(modalOverlay);
        }

        const titleEl = document.getElementById('hvnModalTitle');
        const descEl = document.getElementById('hvnModalDesc');
        const cancelBtn = document.getElementById('hvnModalCancelBtn');
        const confirmBtn = document.getElementById('hvnModalConfirmBtn');

        titleEl.textContent = title || 'টিকিট বন্ধ করতে চান?';
        descEl.textContent = message || 'এই সাপোর্ট টিকিটটি বন্ধ করতে চান? আপনি কি নিশ্চিত?';
        cancelBtn.textContent = cancelText || 'বাতিল করুন';
        confirmBtn.textContent = confirmText || 'হ্যাঁ, বন্ধ করুন';

        function handleKey(e) {
            if (e.key === 'Escape') closeModal();
        }

        function closeModal() {
            modalOverlay.classList.remove('active');
            cancelBtn.onclick = null;
            confirmBtn.onclick = null;
            modalOverlay.onclick = null;
            document.removeEventListener('keydown', handleKey);
        }

        cancelBtn.onclick = closeModal;
        modalOverlay.onclick = (e) => {
            if (e.target === modalOverlay) closeModal();
        };
        confirmBtn.onclick = () => {
            closeModal();
            if (typeof onConfirm === 'function') onConfirm();
        };

        document.addEventListener('keydown', handleKey);
        void modalOverlay.offsetWidth;
        requestAnimationFrame(() => {
            modalOverlay.classList.add('active');
            cancelBtn.focus();
        });
    };

    // ------------------------------------------------------------------------
    // 4. Unified Interactive Support Wizard (Desk & Drawer Composer)
    // ------------------------------------------------------------------------
    window.hvnMountSupportWizard = function (container, options = {}) {
        if (!container) return;
        const isWidget = !!options.isWidget;
        const prefix = options.prefix || './';
        const onTicketCreated = options.onTicketCreated || function () { };
        const prefixId = isWidget ? 'wgt' : 'desk';

        const cats = window.HVN_SUPPORT_CATEGORIES;
        const catKeys = Object.keys(cats);

        const SHORT_LABELS = {
            ign: { bn: 'IGN *', en: 'IGN *' },
            email: { bn: 'ইমেইল *', en: 'Email *' },
            discordTag: { bn: 'ডিসকর্ড *', en: 'Discord *' },
            paymentMethod: { bn: 'মাধ্যম *', en: 'Method *' },
            trxId: { bn: 'TrxID *', en: 'TrxID *' },
            itemPurchased: { bn: 'র‍্যাংক / আইটেম *', en: 'Rank / Item *' },
            itemInterested: { bn: 'প্যাকেজ *', en: 'Package *' },
            senderNumber: { bn: 'প্রেরক নম্বর', en: 'Sender No.' },
            serverMode: { bn: 'সার্ভার মোড', en: 'Server Mode' },
            platform: { bn: 'প্ল্যাটফর্ম *', en: 'Platform *' },
            errorMessage: { bn: 'এরর মেসেজ', en: 'Error Message' },
            isp: { bn: 'ইন্টারনেট / ISP', en: 'ISP / Network' },
            location: { bn: 'লোকেশন (X,Y,Z)', en: 'Coordinates' },
            reportedPlayer: { bn: 'অভিযুক্ত IGN *', en: 'Reported IGN *' },
            reportReason: { bn: 'কারণ *', en: 'Reason *' },
            dateTime: { bn: 'তারিখ ও সময় *', en: 'Date & Time *' },
            evidenceLink: { bn: 'প্রমাণ লিংক', en: 'Evidence URL' },
            punishmentReason: { bn: 'ব্যানের কারণ *', en: 'Ban Reason *' },
            appealReason: { bn: 'আপিলের বিবরণ *', en: 'Appeal Details *' },
            orgName: { bn: 'প্রতিষ্ঠান *', en: 'Brand / Org *' },
            contactPerson: { bn: 'নাম ও IGN *', en: 'Contact & IGN *' },
            channelLink: { bn: 'চ্যানেল লিংক *', en: 'Channel URL *' },
            followersCount: { bn: 'ফলোয়ার *', en: 'Followers *' },
            age: { bn: 'বয়স', en: 'Age' },
            details: { bn: 'বিস্তারিত বিবরণ *', en: 'Details *' }
        };

        let currentLang = localStorage.getItem('hvn_support_lang') || 'bn';
        let selectedCatKey = catKeys[0] || 'store';
        let selectedSubtopic = (cats[selectedCatKey] && cats[selectedCatKey].subtopics[0]) || null;
        let widgetStep = 1;
        let showOptionalFields = false;
        let turnstileWidgetId = null;
        let langSwapTimer = null;

        let initialIgn = '';
        try {
            initialIgn = (window.HavenCraftCore?.getCurrentUser?.())
                || localStorage.getItem('havencraft-gametag')
                || '';
        } catch { }

        const formValues = { ign: initialIgn };

        function getActiveFields() {
            return (selectedSubtopic && selectedSubtopic.fields) ? selectedSubtopic.fields : [];
        }

        function getFieldLabel(fid, def) {
            if (SHORT_LABELS[fid]) {
                return SHORT_LABELS[fid][currentLang];
            }
            return def.label[currentLang];
        }

        function cleanupTurnstile() {
            if (turnstileWidgetId !== null && window.turnstile && typeof window.turnstile.remove === 'function') {
                try { window.turnstile.remove(turnstileWidgetId); } catch { }
            }
            turnstileWidgetId = null;
        }

        function captureFormValues() {
            getActiveFields().forEach(fid => {
                const input = document.getElementById(`${prefixId}_field_${fid}`);
                if (input) formValues[fid] = input.value;
            });
        }

        function showComposerError(msg) {
            const alertEl = document.getElementById(`${prefixId}_form_error`);
            if (!alertEl) return;
            if (!msg) {
                alertEl.style.display = 'none';
                alertEl.innerHTML = '';
                return;
            }
            alertEl.innerHTML = `<i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i> <span>${esc(msg)}</span>`;
            alertEl.style.display = 'flex';
            alertEl.focus();
        }

        function updateLanguage(newLang) {
            const nextLang = newLang || 'bn';
            if (nextLang === currentLang && container.querySelector('.hvn-wiz-shell')) {
                if (typeof window.hvnSyncWidgetLangUI === 'function') {
                    window.hvnSyncWidgetLangUI(currentLang);
                }
                return;
            }

            currentLang = nextLang;
            try {
                if (newLang) localStorage.setItem('hvn_support_lang', newLang);
                else localStorage.removeItem('hvn_support_lang');
            } catch { }

            if (typeof window.hvnUpdateSupportPageLanguage === 'function') {
                window.hvnUpdateSupportPageLanguage(currentLang);
            }
            if (typeof window.hvnSyncWidgetLangUI === 'function') {
                window.hvnSyncWidgetLangUI(currentLang);
            }
            if (langSwapTimer) {
                clearTimeout(langSwapTimer);
                langSwapTimer = null;
            }
            const shell = container.querySelector('.hvn-wiz-shell');
            if (shell) {
                shell.classList.add('is-lang-fading');
                langSwapTimer = setTimeout(() => {
                    langSwapTimer = null;
                    renderWizardStep({ fadeIn: true });
                }, 115);
            } else {
                renderWizardStep();
            }
        }

        window[`${prefixId}SetLanguage`] = function (lang) {
            captureFormValues();
            updateLanguage(lang);
        };

        function buildCustomSelectHtml({ id, name, opts, selectedValue }) {
            const safeOpts = Array.isArray(opts) && opts.length ? opts : [{ value: '', label: '' }];
            const activeOpt = safeOpts.find(o => String(o.value) === String(selectedValue)) || safeOpts[0];

            const menuItemsHtml = safeOpts.map(opt => {
                const isSel = String(opt.value) === String(activeOpt.value);
                const iconHtml = opt.icon ? `<i class="${esc(opt.icon)}" aria-hidden="true"></i>` : '';
                return `
                    <button type="button" class="hvn-select-option ${isSel ? 'selected' : ''}" role="option" aria-selected="${isSel ? 'true' : 'false'}" data-value="${esc(opt.value)}" data-icon="${esc(opt.icon || '')}">
                        <span class="hvn-select-opt-main">
                            ${iconHtml}
                            <span class="hvn-select-opt-txt">${esc(opt.label)}</span>
                        </span>
                        <i class="fa-solid fa-check hvn-select-check" aria-hidden="true"></i>
                    </button>
                `;
            }).join('');

            const activeIconHtml = activeOpt.icon ? `<i class="${esc(activeOpt.icon)}" aria-hidden="true"></i>` : '';
            return `
                <div class="hvn-custom-select" data-select-id="${id}">
                    <input type="hidden" id="${id}" name="${name}" value="${esc(activeOpt.value)}" />
                    <button type="button" class="hvn-select-trigger" aria-haspopup="listbox" aria-expanded="false">
                        <span class="hvn-select-value">
                            ${activeIconHtml}
                            <span class="hvn-select-text">${esc(activeOpt.label)}</span>
                        </span>
                        <i class="fa-solid fa-chevron-down hvn-select-arrow" aria-hidden="true"></i>
                    </button>
                    <div class="hvn-select-menu" role="listbox">${menuItemsHtml}</div>
                </div>
            `;
        }

        function renderFieldControlHtml(fid, def, groupClass = 'form-group') {
            const currentVal = formValues[fid] || '';
            const labelText = esc(getFieldLabel(fid, def));
            const rawPlaceholder = def.placeholder ? def.placeholder[currentLang] : '';
            const placeholderText = esc(rawPlaceholder);

            if (def.type === 'select') {
                return `
                    <div class="${groupClass}">
                        <label>${labelText}</label>
                        ${buildCustomSelectHtml({
                            id: `${prefixId}_field_${fid}`,
                            name: fid,
                            opts: def.options,
                            selectedValue: currentVal
                        })}
                    </div>
                `;
            }
            if (def.type === 'textarea') {
                const enterNote = currentLang === 'bn'
                    ? `${rawPlaceholder} (পাঠাতে Enter চাপুন)`
                    : `${rawPlaceholder} (Press Enter to send)`;
                return `
                    <div class="${groupClass}">
                        <label for="${prefixId}_field_${fid}">${labelText}</label>
                        <textarea id="${prefixId}_field_${fid}" name="${fid}" rows="${isWidget ? '3' : '4'}" placeholder="${esc(enterNote)}" ${def.required ? 'required' : ''}>${esc(currentVal)}</textarea>
                    </div>
                `;
            }
            return `
                <div class="${groupClass}">
                    <label for="${prefixId}_field_${fid}">${labelText}</label>
                    <input type="${def.type}" id="${prefixId}_field_${fid}" name="${fid}" value="${esc(currentVal)}" placeholder="${placeholderText}" ${def.required ? 'required' : ''} />
                </div>
            `;
        }

        function bindGridControls(fieldsGrid) {
            fieldsGrid.querySelectorAll('.hvn-custom-select').forEach(selectWrap => {
                const trigger = selectWrap.querySelector('.hvn-select-trigger');
                const hiddenInput = selectWrap.querySelector('input[type="hidden"]');
                const valContainer = selectWrap.querySelector('.hvn-select-value');
                const optionsList = selectWrap.querySelectorAll('.hvn-select-option');

                if (trigger) {
                    trigger.onclick = (e) => {
                        e.stopPropagation();
                        const wasOpen = selectWrap.classList.contains('open');
                        document.querySelectorAll('.hvn-custom-select.open').forEach(other => {
                            other.classList.remove('open');
                            other.querySelector('.hvn-select-trigger')?.setAttribute('aria-expanded', 'false');
                        });
                        if (!wasOpen) {
                            selectWrap.classList.add('open');
                            trigger.setAttribute('aria-expanded', 'true');
                        }
                    };
                }

                optionsList.forEach(optBtn => {
                    optBtn.onclick = (e) => {
                        e.stopPropagation();
                        const newVal = optBtn.getAttribute('data-value') || '';
                        const newIcon = optBtn.getAttribute('data-icon') || '';
                        const newLabel = optBtn.querySelector('.hvn-select-opt-txt')?.textContent || '';

                        if (hiddenInput) hiddenInput.value = newVal;
                        if (valContainer) {
                            valContainer.innerHTML = `${newIcon ? `<i class="${esc(newIcon)}" aria-hidden="true"></i>` : ''}<span class="hvn-select-text">${esc(newLabel)}</span>`;
                        }
                        optionsList.forEach(o => {
                            const isThis = o === optBtn;
                            o.classList.toggle('selected', isThis);
                            o.setAttribute('aria-selected', isThis ? 'true' : 'false');
                        });
                        selectWrap.classList.remove('open');
                        trigger?.setAttribute('aria-expanded', 'false');
                        if (hiddenInput && typeof hiddenInput.onchange === 'function') hiddenInput.onchange();
                    };
                });
            });

            if (!window.__hvnCustomSelectDocBound) {
                window.__hvnCustomSelectDocBound = true;
                const closeAllSelects = () => {
                    document.querySelectorAll('.hvn-custom-select.open').forEach(openEl => {
                        openEl.classList.remove('open');
                        openEl.querySelector('.hvn-select-trigger')?.setAttribute('aria-expanded', 'false');
                    });
                };
                document.addEventListener('click', closeAllSelects);
                document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeAllSelects(); });
            }

            fieldsGrid.querySelectorAll('input[required], textarea[required]').forEach(inputEl => {
                inputEl.addEventListener('blur', () => {
                    const val = inputEl.value.trim();
                    const isEmailInvalid = inputEl.type === 'email' && val && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val);
                    const isIgnTooShort = inputEl.name === 'ign' && val && val.length < 2;
                    inputEl.classList.toggle('is-invalid', !val || isEmailInvalid || isIgnTooShort);
                });
                inputEl.addEventListener('input', () => {
                    if (inputEl.value.trim()) inputEl.classList.remove('is-invalid');
                });
            });
        }

        function bindFormSubmit(formEl, cat) {
            const resetSubmit = () => {
                const submitBtn = document.getElementById(`${prefixId}_submit_btn`);
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.innerHTML = `<i class="fa-solid fa-paper-plane" aria-hidden="true"></i> <span id="${prefixId}_submit_txt">${currentLang === 'bn' ? 'লাইভ চ্যাট শুরু করুন' : 'Start Live Chat'}</span>`;
                }
                if (window.turnstile && typeof window.turnstile.reset === 'function') {
                    try {
                        if (turnstileWidgetId !== null) window.turnstile.reset(turnstileWidgetId);
                        else window.turnstile.reset(`#${prefixId}_turnstile`);
                    } catch { }
                }
            };

            formEl.onsubmit = async (e) => {
                e.preventDefault();
                captureFormValues();
                showComposerError('');

                const isBn = currentLang === 'bn';
                let firstInvalidEl = null;

                getActiveFields().forEach(fid => {
                    const def = window.HVN_SUPPORT_FIELDS[fid];
                    const el = document.getElementById(`${prefixId}_field_${fid}`);
                    if (!def || !el) return;
                    const val = el.value.trim();
                    const isEmailInvalid = def.type === 'email' && val && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val);
                    const isIgnTooShort = fid === 'ign' && val && val.length < 2;
                    if ((def.required && !val) || isEmailInvalid || isIgnTooShort) {
                        el.classList.add('is-invalid');
                        if (!firstInvalidEl) firstInvalidEl = el;
                    } else {
                        el.classList.remove('is-invalid');
                    }
                });

                if (firstInvalidEl) {
                    showComposerError(isBn
                        ? 'অনুগ্রহ করে লাল চিহ্নিত আবশ্যক ঘরগুলো সঠিকভাবে পূরণ করুন।'
                        : 'Please fill out all highlighted required fields correctly.');
                    firstInvalidEl.focus();
                    return;
                }

                const turnstileInput = formEl.querySelector('[name="cf-turnstile-response"]');
                let captchaToken = turnstileInput ? turnstileInput.value : '';
                if (!captchaToken && window.turnstile && typeof window.turnstile.getResponse === 'function') {
                    try {
                        captchaToken = (turnstileWidgetId !== null ? window.turnstile.getResponse(turnstileWidgetId) : '')
                            || window.turnstile.getResponse(`#${prefixId}_turnstile`)
                            || window.turnstile.getResponse()
                            || '';
                    } catch { }
                }

                if (!captchaToken) {
                    showComposerError(isBn
                        ? 'অনুগ্রহ করে নিচের সিকিউরিটি চেক (Cloudflare Captcha) সম্পন্ন করুন।'
                        : 'Please complete the security verification (Captcha) below.');
                    return;
                }

                const submitBtn = document.getElementById(`${prefixId}_submit_btn`);
                if (submitBtn) {
                    submitBtn.disabled = true;
                    submitBtn.innerHTML = `<i class="fa-solid fa-circle-notch fa-spin" aria-hidden="true"></i> <span>${isBn ? 'শুরু হচ্ছে...' : 'Starting...'}</span>`;
                }

                const name = formValues.ign || formValues.contactPerson || 'Player';
                const email = formValues.email || '';
                const details = formValues.details || formValues.appealReason || '';
                const structuredLines = [
                    `📋 **Category:** ${cat.title.en} (${cat.title.bn})`,
                    `📌 **Topic:** ${selectedSubtopic.title.en} (${selectedSubtopic.title.bn})`
                ];

                getActiveFields().forEach(fid => {
                    if (['details', 'appealReason', 'botcheck'].includes(fid)) return;
                    const def = window.HVN_SUPPORT_FIELDS[fid];
                    const val = formValues[fid];
                    if (val && val.trim() && def) {
                        structuredLines.push(`• **${def.label.en.replace(' *', '')}:** \`${val.trim()}\``);
                    }
                });
                if (details) structuredLines.push(`\n📝 **Details:**\n${details}`);
                const assembledMessage = structuredLines.join('\n');

                try {
                    const res = await fetch('/api/contact', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            name,
                            email,
                            subject: `${cat.title.en} - ${selectedSubtopic.title.en}`,
                            message: assembledMessage,
                            discordTag: formValues.discordTag || undefined,
                            trxId: formValues.trxId || undefined,
                            paymentMethod: formValues.paymentMethod || undefined,
                            platform: formValues.platform || undefined,
                            captchaToken
                        })
                    });

                    const data = await res.json();
                    if (data.success && data.ticket) {
                        if (data.sessionToken) data.ticket.sessionToken = data.sessionToken;
                        const savedTicket = {
                            id: data.ticket.id,
                            ticket_code: data.ticket.ticket_code,
                            sessionToken: data.sessionToken || '',
                            user_name: data.ticket.user_name,
                            user_email: email,
                            subject: data.ticket.subject,
                            status: 'open'
                        };
                        saveActiveTicket(savedTicket);

                        const initialMsg = {
                            id: 'init-1',
                            ticket_id: data.ticket.id,
                            sender: 'player',
                            sender_name: data.ticket.user_name,
                            message: assembledMessage,
                            created_at: new Date().toISOString()
                        };
                        writeChatCache(data.ticket.id, [initialMsg]);
                        onTicketCreated(savedTicket, initialMsg);
                    } else {
                        showComposerError(data.error || (isBn ? 'টিকেট তৈরি করা যায়নি। আবার চেষ্টা করুন।' : 'Failed to start support ticket.'));
                        resetSubmit();
                    }
                } catch {
                    showComposerError(isBn ? 'নেটওয়ার্ক ত্রুটি। অনুগ্রহ করে আবার চেষ্টা করুন।' : 'Connection error. Please try again.');
                    resetSubmit();
                }
            };
        }

        function renderWizardStep(opts = {}) {
            const fadeIn = !!opts.fadeIn;
            const shellFadeClass = fadeIn ? ' is-lang-fading' : '';
            const listStaticClass = fadeIn ? ' is-static' : '';
            const revealIfFading = () => {
                if (!fadeIn) return;
                const nextShell = container.querySelector('.hvn-wiz-shell');
                if (!nextShell) return;
                requestAnimationFrame(() => {
                    requestAnimationFrame(() => {
                        nextShell.classList.remove('is-lang-fading');
                    });
                });
            };

            const isBn = currentLang === 'bn';
            const cat = cats[selectedCatKey] || cats[catKeys[0]];
            if (!selectedSubtopic || !cat.subtopics.includes(selectedSubtopic)) {
                selectedSubtopic = cat.subtopics[0];
            }

            cleanupTurnstile();

            const stepCounter = isBn
                ? `${['১', '২', '৩'][widgetStep - 1] || widgetStep}/৩`
                : `${widgetStep}/3`;

            const activeNavTitleHtml = widgetStep === 2
                ? `
                    <span class="hvn-wiz-nav-title">
                        <i class="${cat.icon}" aria-hidden="true"></i>
                        <strong>${esc(cat.title[currentLang])}</strong>
                    </span>
                `
                : `
                    <span class="hvn-wiz-nav-title">
                        <i class="${selectedSubtopic.icon}" aria-hidden="true"></i>
                        <strong>${esc(selectedSubtopic.title[currentLang])}</strong>
                    </span>
                `;

            const navHeaderHtml = widgetStep === 1
                ? `
                    <div class="hvn-wiz-nav">
                        <span class="hvn-wiz-greeting">${isBn ? 'কী নিয়ে সাহায্য দরকার?' : 'How can we help? Pick a topic'}</span>
                        <span class="hvn-wiz-step-pill">${stepCounter}</span>
                    </div>
                `
                : `
                    <div class="hvn-wiz-nav">
                        <div class="hvn-wiz-nav-heading">
                            <button type="button" class="hvn-wiz-back-btn" id="${prefixId}_back_btn" aria-label="${isBn ? 'পিছনে যান' : 'Back'}" title="${isBn ? 'পিছনে' : 'Back'}">
                                <i class="fa-solid fa-arrow-left" aria-hidden="true"></i>
                            </button>
                            ${activeNavTitleHtml}
                        </div>
                        <span class="hvn-wiz-step-pill">${stepCounter}</span>
                    </div>
                `;

            const footerHtml = isWidget
                ? `
                    <div class="composer-widget-footer">
                        <a href="${prefix}support/">
                            <i class="fa-solid fa-up-right-from-square" aria-hidden="true"></i>
                            <span>${isBn ? 'বিস্তারিত সাপোর্ট ডেস্কে যান' : 'Open full Support Desk'}</span>
                        </a>
                    </div>
                `
                : '';

            if (widgetStep === 1) {
                const cardsHtml = catKeys.map(ck => {
                    const c = cats[ck];
                    const isSel = ck === selectedCatKey;
                    return `
                        <button type="button" class="hvn-wiz-choice-card ${isSel ? 'is-selected' : ''}" data-cat="${ck}">
                            <span class="hvn-wiz-choice-icon"><i class="${c.icon}" aria-hidden="true"></i></span>
                            <span class="hvn-wiz-choice-copy">
                                <strong>${esc(c.title[currentLang])}</strong>
                                <span>${esc(c.desc[currentLang])}</span>
                            </span>
                            <i class="fa-solid fa-chevron-right hvn-wiz-choice-arrow" aria-hidden="true"></i>
                        </button>
                    `;
                }).join('');

                container.innerHTML = `
                    <div class="hvn-wiz-shell${shellFadeClass}">
                        ${navHeaderHtml}
                        <div class="hvn-wiz-choice-list${listStaticClass}" role="list">${cardsHtml}</div>
                        ${footerHtml}
                    </div>
                `;
                revealIfFading();

                container.querySelectorAll('.hvn-wiz-choice-card[data-cat]').forEach(btn => {
                    btn.onclick = () => {
                        const nextCat = btn.getAttribute('data-cat');
                        if (nextCat !== selectedCatKey) {
                            selectedCatKey = nextCat;
                            selectedSubtopic = cats[selectedCatKey].subtopics[0];
                        }
                        showOptionalFields = false;
                        widgetStep = 2;
                        renderWizardStep();
                    };
                });
                return;
            }

            if (widgetStep === 2) {
                const subCardsHtml = cat.subtopics.map((sub, idx) => {
                    const isSel = selectedSubtopic && sub.id === selectedSubtopic.id;
                    return `
                        <button type="button" class="hvn-wiz-choice-card ${isSel ? 'is-selected' : ''}" data-sub-idx="${idx}">
                            <span class="hvn-wiz-choice-icon"><i class="${sub.icon}" aria-hidden="true"></i></span>
                            <span class="hvn-wiz-choice-copy">
                                <strong>${esc(sub.title[currentLang])}</strong>
                                <span>${esc(sub.desc[currentLang])}</span>
                            </span>
                            <i class="fa-solid fa-chevron-right hvn-wiz-choice-arrow" aria-hidden="true"></i>
                        </button>
                    `;
                }).join('');

                container.innerHTML = `
                    <div class="hvn-wiz-shell${shellFadeClass}">
                        ${navHeaderHtml}
                        <div class="hvn-wiz-choice-list${listStaticClass}" role="list">${subCardsHtml}</div>
                    </div>
                `;
                revealIfFading();

                document.getElementById(`${prefixId}_back_btn`)?.addEventListener('click', () => {
                    widgetStep = 1;
                    renderWizardStep();
                });

                container.querySelectorAll('.hvn-wiz-choice-card[data-sub-idx]').forEach(btn => {
                    btn.onclick = () => {
                        const idx = parseInt(btn.getAttribute('data-sub-idx'), 10);
                        if (cat.subtopics[idx]) {
                            selectedSubtopic = cat.subtopics[idx];
                            showOptionalFields = false;
                            widgetStep = 3;
                            renderWizardStep();
                        }
                    };
                });
                return;
            }

            // Step 3: Clean compact details + Enter to create support ticket
            const requiredRegular = [];
            const optionalRegular = [];
            const fullTextareas = [];
            selectedSubtopic.fields.forEach(fid => {
                const def = window.HVN_SUPPORT_FIELDS[fid];
                if (!def) return;
                if (def.type === 'textarea') fullTextareas.push({ fid, def });
                else if (def.required) requiredRegular.push({ fid, def });
                else optionalRegular.push({ fid, def });
            });

            const hasTypedOptional = optionalRegular.some(({ fid }) => !!(formValues[fid] && String(formValues[fid]).trim()));
            const showOptsNow = showOptionalFields || hasTypedOptional;

            let reqGridHtml = '<div class="hvn-widget-row-2col">';
            requiredRegular.forEach(({ fid, def }, idx) => {
                const isLastOdd = (requiredRegular.length % 2 === 1) && (idx === requiredRegular.length - 1);
                reqGridHtml += renderFieldControlHtml(fid, def, isLastOdd ? 'form-group form-group-full' : 'form-group');
            });
            reqGridHtml += '</div>';

            let optSectionHtml = '';
            if (optionalRegular.length > 0) {
                if (showOptsNow) {
                    optSectionHtml = '<div class="hvn-widget-row-2col">';
                    optionalRegular.forEach(({ fid, def }, idx) => {
                        const isLastOdd = (optionalRegular.length % 2 === 1) && (idx === optionalRegular.length - 1);
                        optSectionHtml += renderFieldControlHtml(fid, def, isLastOdd ? 'form-group form-group-full' : 'form-group');
                    });
                    optSectionHtml += '</div>';
                } else {
                    optSectionHtml = `
                        <button type="button" class="hvn-wiz-opt-toggle" id="${prefixId}_opt_toggle">
                            <i class="fa-solid fa-plus" aria-hidden="true"></i>
                            <span>${isBn ? `ঐচ্ছিক তথ্য যোগ করুন (${optionalRegular.length})` : `Add optional details (${optionalRegular.length})`}</span>
                        </button>
                    `;
                }
            }

            const textareaHtml = fullTextareas.map(({ fid, def }) => renderFieldControlHtml(fid, def, 'form-group form-group-full')).join('');
            const tipHtml = selectedSubtopic.tip
                ? `<div class="hvn-wiz-tip"><i class="fa-solid fa-circle-info" aria-hidden="true"></i><span>${esc(selectedSubtopic.tip[currentLang])}</span></div>`
                : '';

            container.innerHTML = `
                <div class="hvn-wiz-shell${shellFadeClass}">
                    ${navHeaderHtml}
                    ${tipHtml}
                    <form id="${prefixId}_form" class="composer-smart-form ${isWidget ? 'composer-smart-form--widget' : 'composer-smart-form--desk'}" novalidate>
                        <input type="checkbox" name="botcheck" style="display: none !important;" tabindex="-1" autocomplete="off" />
                        <div id="${prefixId}_form_error" class="support-inline-alert" role="alert" tabindex="-1" style="display: none;"></div>
                        <div id="${prefixId}_fields_grid" class="support-form-grid">
                            ${reqGridHtml}
                            ${optSectionHtml}
                            ${textareaHtml}
                        </div>
                        <div class="composer-submit-bar">
                            <div class="turnstile-container"><div id="${prefixId}_turnstile"></div></div>
                            <button type="submit" class="form-submit-btn" id="${prefixId}_submit_btn">
                                <i class="fa-solid fa-paper-plane" aria-hidden="true"></i>
                                <span id="${prefixId}_submit_txt">${isBn ? 'লাইভ চ্যাট শুরু করুন' : 'Start Live Chat'}</span>
                            </button>
                        </div>
                    </form>
                </div>
            `;
            revealIfFading();

            const formEl = document.getElementById(`${prefixId}_form`);
            const fieldsGrid = document.getElementById(`${prefixId}_fields_grid`);
            if (fieldsGrid) bindGridControls(fieldsGrid);
            if (formEl) bindFormSubmit(formEl, cat);

            document.getElementById(`${prefixId}_back_btn`)?.addEventListener('click', () => {
                captureFormValues();
                widgetStep = 2;
                renderWizardStep();
            });

            document.getElementById(`${prefixId}_opt_toggle`)?.addEventListener('click', () => {
                captureFormValues();
                showOptionalFields = true;
                renderWizardStep();
            });

            fieldsGrid?.querySelectorAll('textarea').forEach(ta => {
                ta.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
                        e.preventDefault();
                        if (formEl) {
                            if (typeof formEl.requestSubmit === 'function') formEl.requestSubmit();
                            else formEl.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
                        }
                    }
                });
            });

            if (typeof window.mountTurnstileWidget === 'function') {
                window.mountTurnstileWidget(`${prefixId}_turnstile`, {
                    widgetId: null,
                    onReady: (id) => { turnstileWidgetId = id; }
                });
            }

            requestAnimationFrame(() => {
                const inputs = Array.from(fieldsGrid?.querySelectorAll('input[required], textarea[required]') || []);
                const firstEmpty = inputs.find(el => !el.value.trim());
                firstEmpty?.focus();
            });
        }

        renderWizardStep();
    };

    // ------------------------------------------------------------------------
    // 5. Public Shared Namespace for support.js & Floating Drawer
    // ------------------------------------------------------------------------
    window.HavenTicketChat = {
        playChime,
        classifyMessageKind,
        deduplicateMessages,
        getActiveTicket,
        saveActiveTicket,
        clearActiveTicket,
        readChatCache,
        writeChatCache,
        appendChatCache,
        getSavedRating,
        fetchTicketViaApi,
        sendTicketReply,
        closeTicketOnServer,
        fileToBase64,
        stageImageAttachment,
        clearAttachmentPreview,
        uploadTicketAttachment,
        mountCsatWidget,
        setupChatInputBehaviors,
        subscribeTicketRealtime
    };

    // ------------------------------------------------------------------------
    // 6. Floating Site-Wide Live Support Drawer (Hidden on /support/)
    // ------------------------------------------------------------------------
    function initFloatingSupportWidget() {
        if (window.location.pathname.includes('/support') || window.location.pathname.endsWith('/support/')) {
            return;
        }

        const widgetHtml = `
        <div class="hvn-chat-widget-root" id="hvnChatWidgetRoot" style="display: contents;">
            <div class="hvn-chat-bubble-label" id="hvnChatBubbleLabel">
                <i class="fa-solid fa-headset" aria-hidden="true"></i>
                <span>Live Support</span>
            </div>
            <button type="button" class="hvn-chat-bubble" id="hvnChatBubble" aria-label="Open HavenCraft Live Support Chat" title="HavenCraft Support">
                <i class="fa-solid fa-headset" id="hvnChatBubbleIcon"></i>
                <span class="hvn-chat-badge" id="hvnChatBadge">0</span>
            </button>
            <div class="hvn-chat-drawer" id="hvnChatDrawer" data-lenis-prevent aria-hidden="true" style="visibility: hidden; opacity: 0; pointer-events: none;">
                <div class="hvn-drawer-header">
                    <div class="hvn-drawer-handle"></div>
                    <div class="hvn-drawer-agent">
                        <div class="hvn-drawer-avatar-wrap">
                            <img src="${root}logo/icon.svg" alt="Live Support Desk" class="hvn-drawer-avatar" loading="lazy" />
                            <span class="hvn-drawer-online-dot"></span>
                        </div>
                        <div class="hvn-drawer-title">
                            <div class="hvn-drawer-heading">Live Support Desk</div>
                        </div>
                    </div>
                    <div class="hvn-drawer-actions">
                        <div class="lang-toggle-bar lang-toggle-bar--header" id="hvnDrawerLangBar" data-active="bn" role="group" aria-label="Language">
                            <span class="lang-toggle-glider" aria-hidden="true"></span>
                            <button type="button" class="lang-toggle-btn" id="hvnHeaderLangBn" data-lang="bn">বাংলা</button>
                            <button type="button" class="lang-toggle-btn" id="hvnHeaderLangEn" data-lang="en">EN</button>
                        </div>
                        <a href="${root}support/" class="hvn-drawer-btn" title="Open Full Support Desk" aria-label="Open Full Support Desk">
                            <i class="fa-solid fa-up-right-and-down-left-from-center" aria-hidden="true"></i>
                        </a>
                        <button type="button" class="hvn-drawer-btn" id="hvnChatCloseBtn" title="Close" aria-label="Close">
                            <i class="fa-solid fa-xmark" aria-hidden="true"></i>
                        </button>
                    </div>
                </div>
                <div class="hvn-drawer-body" id="hvnDrawerBody"></div>
            </div>
        </div>`;

        const wrapper = document.createElement('div');
        wrapper.innerHTML = widgetHtml;
        document.body.appendChild(wrapper.firstElementChild);

        const bubble = document.getElementById('hvnChatBubble');
        const drawer = document.getElementById('hvnChatDrawer');
        const badge = document.getElementById('hvnChatBadge');
        const label = document.getElementById('hvnChatBubbleLabel');
        const drawerLangBar = document.getElementById('hvnDrawerLangBar');
        const headerLangBn = document.getElementById('hvnHeaderLangBn');
        const headerLangEn = document.getElementById('hvnHeaderLangEn');
        const closeBtn = document.getElementById('hvnChatCloseBtn');
        const bodyEl = document.getElementById('hvnDrawerBody');

        let unreadCount = 0;
        let isDrawerOpen = false;
        let activeSubscription = null;
        let stagedAttachment = null;
        const renderedIds = new Set();

        function syncHeaderLangUI(langOverride) {
            let lang = langOverride;
            if (!lang) {
                try { lang = localStorage.getItem('hvn_support_lang') || 'bn'; } catch { lang = 'bn'; }
            }
            const isBn = lang !== 'en';
            drawerLangBar?.setAttribute('data-active', isBn ? 'bn' : 'en');
            headerLangBn?.classList.toggle('active', isBn);
            headerLangEn?.classList.toggle('active', !isBn);
            headerLangBn?.setAttribute('aria-pressed', isBn ? 'true' : 'false');
            headerLangEn?.setAttribute('aria-pressed', !isBn ? 'true' : 'false');
        }
        window.hvnSyncWidgetLangUI = syncHeaderLangUI;
        syncHeaderLangUI();

        function handleHeaderLangSwitch(nextLang) {
            syncHeaderLangUI(nextLang);
            if (typeof window.wgtSetLanguage === 'function' && bodyEl?.querySelector('.hvn-wiz-shell')) {
                window.wgtSetLanguage(nextLang);
                return;
            }
            try { localStorage.setItem('hvn_support_lang', nextLang); } catch { }
        }

        headerLangBn?.addEventListener('click', () => handleHeaderLangSwitch('bn'));
        headerLangEn?.addEventListener('click', () => handleHeaderLangSwitch('en'));

        function setBadge(count) {
            unreadCount = count;
            if (!badge) return;
            badge.textContent = count;
            badge.style.display = count > 0 ? 'flex' : 'none';
        }

        function toggleDrawer(open) {
            isDrawerOpen = typeof open === 'boolean' ? open : !isDrawerOpen;
            if (isDrawerOpen) {
                drawer.style.visibility = '';
                drawer.style.opacity = '';
                drawer.style.pointerEvents = '';
                drawer.classList.add('active');
                drawer.setAttribute('aria-hidden', 'false');
                bubble.classList.add('open');
                if (label) label.style.display = 'none';
                setBadge(0);
                syncHeaderLangUI();
                renderWidgetContent();
            } else {
                drawer.classList.remove('active');
                drawer.setAttribute('aria-hidden', 'true');
                bubble.classList.remove('open');
            }
        }

        bubble?.addEventListener('click', () => toggleDrawer());
        closeBtn?.addEventListener('click', () => toggleDrawer(false));

        if (label) {
            const onChatLabelScroll = () => {
                if (window.scrollY > 150) {
                    label.style.opacity = '0';
                    label.style.pointerEvents = 'none';
                    window.removeEventListener('scroll', onChatLabelScroll);
                }
            };
            window.addEventListener('scroll', onChatLabelScroll, { passive: true });
        }

        // Tuck floating trigger behind homepage connect card on small screens (WCAG 2.4.11 Focus Not Obscured).
        const connectCard = document.querySelector('.terminal-card');
        if (bubble && connectCard && 'IntersectionObserver' in window) {
            const smallScreen = window.matchMedia('(max-width: 991px)');
            let cardInZone = false;
            const syncTuck = () => {
                const tuck = smallScreen.matches && cardInZone && !isDrawerOpen;
                bubble.classList.toggle('is-tucked', tuck);
                if (label) label.classList.toggle('is-tucked', tuck);
            };
            new IntersectionObserver((entries) => {
                cardInZone = entries[0].isIntersecting;
                syncTuck();
            }, { rootMargin: '-82% 0px 0px 0px' }).observe(connectCard);
            smallScreen.addEventListener?.('change', syncTuck);
        }

        function appendWidgetMsg(box, msg) {
            if (!box || !msg) return;
            if (msg.id) {
                const key = String(msg.id);
                if (renderedIds.has(key)) return;
                renderedIds.add(key);
            }

            const kind = classifyMessageKind(msg);
            if (kind === 'close' || kind === 'rating') {
                const marker = kind === 'close' ? 'close' : 'rating';
                if (box.querySelector(`.chat-msg.system[data-sys-kind="${marker}"]`)) return;
            }

            const formattedText = window.hvnFormatChatMessage(msg.message);
            const div = document.createElement('div');

            if (kind === 'system' || kind === 'close' || kind === 'rating') {
                div.className = 'msg-system-pill chat-msg system';
                div.setAttribute('data-sys-kind', kind);
                div.innerHTML = `<i class="fa-solid fa-shield-halved" aria-hidden="true"></i> <span>${formattedText}</span>`;
            } else {
                const isPlayer = kind === 'player';
                const rawMsg = String(msg.message || '');
                const isVoice = rawMsg.trim().startsWith('[voice:');
                const isMedia = rawMsg.includes('[image:') || /\.(?:png|jpe?g|gif|webp)/i.test(rawMsg);
                div.className = `chat-msg ${isPlayer ? 'player' : 'staff'}${isVoice ? ' has-voice' : ''}${isMedia ? ' has-media' : ''}`;
                div.style.padding = '8px 12px';
                div.style.fontSize = '0.86rem';
                const timeStr = msg.created_at
                    ? new Date(msg.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                    : '';

                div.innerHTML = `
                    <div class="chat-sender ${!isPlayer ? 'staff-label' : ''}" style="font-size: 0.72rem;">
                        ${!isPlayer ? '<i class="fa-solid fa-shield-halved"></i> ' : '<i class="fa-solid fa-user"></i> '}${esc(msg.sender_name || 'Staff')}
                    </div>
                    <div>${formattedText}</div>
                    <div class="chat-time" style="font-size: 0.68rem;">${timeStr}</div>
                `;
            }

            box.appendChild(div);
            box.scrollTop = box.scrollHeight;
        }

        function getWidgetPreviewEls() {
            return {
                previewEl: document.getElementById('hvnWidgetAttachPreview'),
                thumbEl: document.getElementById('hvnWidgetAttachThumb'),
                nameEl: document.getElementById('hvnWidgetAttachName'),
                sizeEl: document.getElementById('hvnWidgetAttachSize'),
                fileInput: document.getElementById('hvnWidgetFileInput')
            };
        }

        function applyClosedUI(ticket, existingRating) {
            const stored = getActiveTicket({ allowClosed: true });
            if (stored && stored.id === ticket.id) {
                stored.status = 'closed';
                saveActiveTicket(stored);
            }

            const headerAction = document.getElementById('hvnWidgetHeaderAction');
            if (headerAction) {
                headerAction.innerHTML = `
                    <div style="display: flex; gap: 6px; align-items: center;">
                        <span class="btn btn-secondary" style="display: inline-flex; align-items: center; gap: 4px; padding: 4px 9px; font-size: 0.74rem; border-radius: var(--radius-xs); color: #F06A4E; border-color: rgba(240, 106, 78, 0.3); background: rgba(240, 106, 78, 0.12); cursor: default; font-weight: 600; line-height: 1;">
                            <i class="fa-solid fa-lock"></i> Closed
                        </span>
                        <button type="button" class="btn btn-secondary" id="hvnWidgetResetBtn" style="display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; font-size: 0.74rem; border-radius: var(--radius-xs); background: var(--surface-elevated); border: 1px solid var(--border); color: var(--text); cursor: pointer; line-height: 1;">
                            <i class="fa-solid fa-plus"></i> New
                        </button>
                    </div>
                `;
                document.getElementById('hvnWidgetResetBtn')?.addEventListener('click', resetWidgetToWizard);
            }

            document.getElementById('hvnWidgetBadge')?.classList.add('closed');

            const inputArea = document.getElementById('hvnWidgetInputArea');
            if (inputArea) {
                inputArea.style.display = 'flex';
                inputArea.style.padding = '6px 8px';
                mountCsatWidget(inputArea, { ticket, existingRating, compact: true });
            }
        }

        function resetWidgetToWizard() {
            const active = getActiveTicket({ allowClosed: true });
            clearActiveTicket(active?.id);
            stagedAttachment = null;
            if (activeSubscription) {
                activeSubscription.unsubscribe();
                activeSubscription = null;
            }
            renderWidgetContent();
        }

        async function connectRealtime(ticket) {
            if (activeSubscription) activeSubscription.unsubscribe();
            activeSubscription = await subscribeTicketRealtime(ticket.id, 'widget-room', {
                onRemoteMessage: (msg) => {
                    playChime();
                    if (!isDrawerOpen) setBadge(unreadCount + 1);
                    const box = document.getElementById('hvnWidgetMsgBox');
                    if (box) appendWidgetMsg(box, msg);
                    appendChatCache(ticket.id, msg);
                },
                onTicketClosed: () => {
                    applyClosedUI(ticket);
                }
            });
        }

        function renderWidgetContent() {
            const active = getActiveTicket();
            if (active) {
                renderWidgetChat(active);
            } else {
                window.hvnMountSupportWizard(bodyEl, {
                    isWidget: true,
                    prefix: root,
                    onTicketCreated: (ticket, initialMsg) => {
                        renderWidgetChat(ticket, [initialMsg]);
                    }
                });
            }
        }

        async function renderWidgetChat(ticket, initialMsgs) {
            const isClosed = ticket.status === 'closed';
            const cachedList = readChatCache(ticket.id);
            const seedList = cachedList.length > 0 ? cachedList : deduplicateMessages(initialMsgs || []);
            const existingRating = isClosed ? getSavedRating(ticket.id, seedList) : null;

            bodyEl.innerHTML = `
                <div style="display: flex; align-items: center; justify-content: space-between; padding-bottom: 8px; border-bottom: 1px solid var(--border); margin-bottom: 10px;">
                    <div>
                        <span class="ticket-badge ${isClosed ? 'closed' : ''}" id="hvnWidgetBadge">${esc(ticket.ticket_code)}</span>
                        <span style="font-size: 0.78rem; color: var(--text-secondary); margin-left: 6px;">${esc(ticket.subject || '')}</span>
                    </div>
                    <div id="hvnWidgetHeaderAction">
                        ${!isClosed ? `
                            <button type="button" class="btn btn-secondary" id="hvnWidgetCloseTicketBtn" style="display: inline-flex; align-items: center; gap: 5px; padding: 4px 10px; font-size: 0.76rem; border-radius: var(--radius-xs); color: #F06A4E; border-color: rgba(240, 106, 78, 0.35); background: rgba(240, 106, 78, 0.08); white-space: nowrap; cursor: pointer; font-weight: 600; line-height: 1;">
                                <i class="fa-solid fa-lock"></i> Close
                            </button>
                        ` : `
                            <div style="display: flex; gap: 6px; align-items: center;">
                                <span class="btn btn-secondary" style="display: inline-flex; align-items: center; gap: 4px; padding: 4px 9px; font-size: 0.74rem; border-radius: var(--radius-xs); color: #F06A4E; border-color: rgba(240, 106, 78, 0.3); background: rgba(240, 106, 78, 0.12); cursor: default; font-weight: 600; line-height: 1;">
                                    <i class="fa-solid fa-lock"></i> Closed
                                </span>
                                <button type="button" class="btn btn-secondary" id="hvnWidgetResetBtn" style="display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; font-size: 0.74rem; border-radius: var(--radius-xs); background: var(--surface-elevated); border: 1px solid var(--border); color: var(--text); cursor: pointer; line-height: 1;">
                                    <i class="fa-solid fa-plus"></i> New
                                </button>
                            </div>
                        `}
                    </div>
                </div>
                <div class="ticket-messages-box" id="hvnWidgetMsgBox" style="height: 310px; min-height: 250px; padding: 12px 10px; margin-bottom: 10px;"></div>
                <div class="ticket-input-area" id="hvnWidgetInputArea" style="flex-direction: column; align-items: stretch; padding: ${isClosed ? '6px 8px' : '6px 10px'};">
                    ${isClosed ? '' : `
                        <div class="ticket-attachment-preview" id="hvnWidgetAttachPreview" style="display: none;">
                            <div class="attach-thumb-wrap">
                                <img id="hvnWidgetAttachThumb" src="" alt="Screenshot" />
                                <button type="button" class="attach-remove-btn" id="hvnWidgetRemoveAttachBtn" title="মুছুন">
                                    <i class="fa-solid fa-xmark"></i>
                                </button>
                            </div>
                            <div class="attach-meta">
                                <span class="attach-filename" id="hvnWidgetAttachName">screenshot.webp</span>
                                <span class="attach-filesize" id="hvnWidgetAttachSize">0 KB</span>
                            </div>
                        </div>
                        <div class="ticket-input-row" style="display: flex; align-items: center; gap: 8px; width: 100%;">
                            <label class="ticket-attach-btn" id="hvnWidgetAttachBtn" title="স্ক্রিনশট বা ছবি সংযুক্ত করুন" aria-label="Attach screenshot">
                                <i class="fa-solid fa-paperclip"></i>
                                <input type="file" id="hvnWidgetFileInput" accept="image/png,image/jpeg,image/webp,image/gif" style="display: none;" />
                            </label>
                            <textarea id="hvnWidgetReplyInput" placeholder="স্টাফকে রিপ্লাই লিখুন... (Enter চাপুন)" rows="1" maxlength="2000" style="flex: 1; font-size: 0.9rem; min-height: 36px; max-height: 100px; resize: none; overflow-y: hidden; scrollbar-width: none; border: none; outline: none; background: transparent; color: var(--text); padding: 8px 10px; line-height: 1.4;"></textarea>
                            <button type="button" class="ticket-send-btn" id="hvnWidgetSendBtn" style="padding: 8px 14px; border-radius: 10px;" aria-label="Send Message">
                                <i class="fa-solid fa-paper-plane"></i>
                            </button>
                        </div>
                    `}
                </div>
            `;

            const box = document.getElementById('hvnWidgetMsgBox');
            const inputArea = document.getElementById('hvnWidgetInputArea');

            if (isClosed && inputArea) {
                mountCsatWidget(inputArea, { ticket, existingRating, compact: true });
                document.getElementById('hvnWidgetResetBtn')?.addEventListener('click', resetWidgetToWizard);
            }

            if (box && seedList.length > 0) {
                box.innerHTML = '';
                renderedIds.clear();
                seedList.forEach(m => appendWidgetMsg(box, m));
            }

            const sendReply = async () => {
                const current = getActiveTicket({ allowClosed: true }) || ticket;
                if (!current || current.status === 'closed') return;

                const inputEl = document.getElementById('hvnWidgetReplyInput');
                const sendBtn = document.getElementById('hvnWidgetSendBtn');
                const text = inputEl ? inputEl.value.trim() : '';
                if (!text && !stagedAttachment) return;

                let finalMessage = text;
                if (stagedAttachment) {
                    const pendingFile = stagedAttachment;
                    stagedAttachment = null;
                    clearAttachmentPreview(getWidgetPreviewEls());

                    if (sendBtn) {
                        sendBtn.disabled = true;
                        sendBtn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i>';
                    }
                    try {
                        const url = await uploadTicketAttachment(pendingFile, current);
                        finalMessage = finalMessage ? `${finalMessage}\n${url}` : url;
                    } catch (err) {
                        if (sendBtn) {
                            sendBtn.disabled = false;
                            sendBtn.innerHTML = '<i class="fa-solid fa-paper-plane"></i>';
                        }
                        notify(`স্ক্রিনশট আপলোড করতে সমস্যা হয়েছে: ${err.message || 'Error'}`);
                        return;
                    }
                }

                if (!finalMessage) return;

                if (inputEl) {
                    inputEl.value = '';
                    inputEl.style.height = 'auto';
                    inputEl.style.overflowY = 'hidden';
                }
                if (sendBtn) {
                    sendBtn.disabled = false;
                    sendBtn.innerHTML = '<i class="fa-solid fa-paper-plane"></i>';
                }

                const optimisticMsg = {
                    id: `opt-${Date.now()}`,
                    ticket_id: current.id,
                    sender: 'player',
                    sender_name: current.user_name || 'Player',
                    message: finalMessage,
                    created_at: new Date().toISOString()
                };
                if (box) appendWidgetMsg(box, optimisticMsg);
                appendChatCache(current.id, optimisticMsg);

                try {
                    const repRes = await sendTicketReply({ ticket: current, message: finalMessage });
                    if (!repRes.ok && repRes.data?.error?.toLowerCase().includes('closed')) {
                        applyClosedUI(current);
                        notify('এই টিকিটটি বন্ধ করা হয়েছে।');
                    }
                } catch (err) {
                    console.warn('[TicketChat] Widget reply failed:', err?.message || err);
                }
            };

            document.getElementById('hvnWidgetRemoveAttachBtn')?.addEventListener('click', () => {
                stagedAttachment = null;
                clearAttachmentPreview(getWidgetPreviewEls());
            });

            document.getElementById('hvnWidgetFileInput')?.addEventListener('change', async (e) => {
                const file = e.target.files?.[0];
                if (file) stagedAttachment = await stageImageAttachment(file, getWidgetPreviewEls());
            });

            document.getElementById('hvnWidgetSendBtn')?.addEventListener('click', sendReply);

            document.getElementById('hvnWidgetCloseTicketBtn')?.addEventListener('click', () => {
                const current = getActiveTicket({ allowClosed: true }) || ticket;
                if (!current || current.status === 'closed') return;

                window.hvnShowConfirmModal({
                    title: 'টিকিট বন্ধ করতে চান?',
                    message: 'এই টিকিটটি বন্ধ করলে আপনার লাইভ সাপোর্ট সেশন সমাপ্ত হবে। আপনি কি নিশ্চিত?',
                    confirmText: 'হ্যাঁ, টিকিট বন্ধ করুন',
                    cancelText: 'না, চালু রাখুন',
                    onConfirm: async () => {
                        applyClosedUI(current);
                        const closeMsg = {
                            id: `close-opt-${Date.now()}`,
                            ticket_id: current.id,
                            sender: 'system',
                            sender_name: 'HavenCraft System',
                            message: 'Ticket resolved and closed by user.',
                            created_at: new Date().toISOString()
                        };
                        if (box) appendWidgetMsg(box, closeMsg);
                        appendChatCache(current.id, closeMsg);
                        await closeTicketOnServer({ ticket: current }).catch(() => { });
                    }
                });
            });

            setupChatInputBehaviors(document.getElementById('hvnWidgetReplyInput'), {
                maxHeight: 100,
                onSubmit: sendReply,
                onImagePaste: async (blob) => {
                    stagedAttachment = await stageImageAttachment(blob, getWidgetPreviewEls());
                }
            });

            // Synchronize complete authenticated transcript via /api/contact (never anon RLS partial reads).
            try {
                const apiRes = await fetchTicketViaApi({
                    ticketCode: ticket.ticket_code,
                    sessionToken: ticket.sessionToken || '',
                    email: ticket.user_email || ''
                });
                if (apiRes.ok && apiRes.data.success && apiRes.data.ticket) {
                    const verifiedTicket = apiRes.data.ticket;
                    saveActiveTicket(verifiedTicket);
                    const fullMsgs = writeChatCache(verifiedTicket.id, apiRes.data.messages || []);
                    if (box && fullMsgs.length > 0) {
                        box.innerHTML = '';
                        renderedIds.clear();
                        fullMsgs.forEach(m => appendWidgetMsg(box, m));
                    }
                    if (verifiedTicket.status === 'closed' || fullMsgs.some(m => classifyMessageKind(m) === 'close')) {
                        applyClosedUI(verifiedTicket, getSavedRating(verifiedTicket.id, fullMsgs));
                    }
                }
            } catch (err) {
                console.warn('[TicketChat] Widget sync warning:', err?.message || err);
            }

            connectRealtime(ticket);
        }

        const active = getActiveTicket();
        if (active) {
            connectRealtime(active);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initFloatingSupportWidget);
    } else {
        initFloatingSupportWidget();
    }
})();
