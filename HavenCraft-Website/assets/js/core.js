window.SERVER_CONFIG = {
    ip: 'mc.havencraft.pro',
    port: '2566',
    javaPort: '2566',
    bedrockPort: '2025',
    version: '1.21+',
    crossplay: 'Java & Bedrock',
    discordUrl: 'https://dc.havencraft.pro',
    seasonName: 'Season 6',
    seasonBadge: 'Season 6 Active',
    seasonDesc: '',
    turnstileSiteKey: '0x4AAAAAAFwXQNw_tYM5tZ8P',
    gaMeasurementId: 'G-3ES6YSB8Y4'
};

window.HavenCraftCore = (function () {
    'use strict';

    const SUPABASE_CONFIGS = {
        leaderboard: {
            url: 'https://ixombndandsttatszhqy.supabase.co',
            key: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml4b21ibmRhbmRzdHRhdHN6aHF5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYzNTkwODIsImV4cCI6MjA5MTkzNTA4Mn0.Xx1oAZg1h68mp9RwaxCFD73k7qIcZAupTDBXaJ3wPhs'
        },
        vote: {
            url: 'https://wlhrnlnocpmtmpgwbirg.supabase.co',
            key: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndsaHJubG5vY3BtdG1wZ3diaXJnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYyNTUxNTcsImV4cCI6MjA5MTgzMTE1N30.3IXbS_ybUbYWLOzJv_4wpjLpsnrOvKxhCVI4_KQhwFc'
        },
        core: {
            url: 'https://nvcnowvasqcsygtbwktw.supabase.co',
            key: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im52Y25vd3Zhc3Fjc3lndGJ3a3R3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE4OTc0MDYsImV4cCI6MjEwNzQ3MzQwNn0.-5z7uOIcr3DBAuBLm7xtiirPqFuVGl_rExTG1rdU61k'
        }
    };
    SUPABASE_CONFIGS.gallery = SUPABASE_CONFIGS.core;

    window.SUPABASE_TICKET_URL = SUPABASE_CONFIGS.core.url;
    window.SUPABASE_TICKET_KEY = SUPABASE_CONFIGS.core.key;

    function initSupabase() {
        if (typeof window.supabase === 'undefined' || typeof window.supabase.createClient !== 'function') {
            return false;
        }
        if (!window.supabaseLeaderboard) {
            window.supabaseLeaderboard = window.supabase.createClient(SUPABASE_CONFIGS.leaderboard.url, SUPABASE_CONFIGS.leaderboard.key);
        }
        if (!window.supabaseVote) {
            window.supabaseVote = window.supabase.createClient(SUPABASE_CONFIGS.vote.url, SUPABASE_CONFIGS.vote.key, {
                realtime: { params: { eventsPerSecond: 2 } }
            });
        }
        if (!window.supabaseCore) {
            window.supabaseCore = window.supabase.createClient(SUPABASE_CONFIGS.core.url, SUPABASE_CONFIGS.core.key);
        }
        if (!window.supabaseGallery) {
            window.supabaseGallery = window.supabaseCore;
        }
        return true;
    }

    function escapeBannerAttr(text) {
        if (typeof window.escapeHtml === 'function') return window.escapeHtml(text);
        return String(text || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function formatBannerMessage(text) {
        if (!text) return '';
        return escapeBannerAttr(text).replace(
            /(https?:\/\/[^\s<]+)/g,
            '<a href="$1" target="_blank" rel="noopener noreferrer" class="hvn-banner-link">$1</a>'
        );
    }

    function handleAnnouncementBanner(announcement) {
        if (!document.body) {
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', () => handleAnnouncementBanner(announcement), { once: true });
            }
            return;
        }

        const BANNER_ID = 'hvnAnnouncementBanner';
        const existingBanner = document.getElementById(BANNER_ID);

        const isEnabled = announcement && announcement.enabled === true && typeof announcement.text === 'string' && announcement.text.trim().length > 0;

        if (!isEnabled) {
            document.body.classList.remove('has-announcement-banner');
            document.documentElement.classList.remove('has-announcement-banner');
            if (existingBanner) existingBanner.remove();
            return;
        }

        const rawText = announcement.text.trim();
        let type = announcement.type || 'news';
        if (type === 'info') type = 'news';
        if (type === 'notice') type = 'warning';
        if (type === 'alert') type = 'danger';

        const badgeLabels = {
            news: 'NEWS',
            update: 'UPDATE',
            event: 'EVENT',
            sale: 'SALE',
            warning: 'NOTICE',
            danger: 'ALERT'
        };

        const badgeLabel = badgeLabels[type] || badgeLabels.news;

        try {
            if (sessionStorage.getItem('hvn_announcement_dismissed') === rawText) {
                document.body.classList.remove('has-announcement-banner');
                document.documentElement.classList.remove('has-announcement-banner');
                if (existingBanner) existingBanner.remove();
                return;
            }
        } catch (err) {
            console.warn('[HavenCraftCore] Announcement session read skipped:', err);
        }

        // If banner is already mounted with the exact same text and type, do not rebuild (prevents animation restart)
        if (existingBanner && existingBanner.dataset.text === rawText && existingBanner.classList.contains(`type-${type}`)) {
            document.body.classList.add('has-announcement-banner');
            document.documentElement.classList.add('has-announcement-banner');
            return;
        }

        let banner = existingBanner;
        if (!banner) {
            banner = document.createElement('aside');
            banner.id = BANNER_ID;
            banner.setAttribute('role', 'region');
            banner.setAttribute('aria-label', 'Server Announcement');

            const placeholder = document.getElementById('navbar-placeholder');
            if (placeholder && placeholder.parentNode) {
                placeholder.parentNode.insertBefore(banner, placeholder);
            } else if (document.body.firstChild) {
                document.body.insertBefore(banner, document.body.firstChild);
            } else {
                document.body.appendChild(banner);
            }
        }

        banner.className = `hvn-announcement-banner type-${type}`;
        banner.dataset.text = rawText;

        const safeFormatted = formatBannerMessage(rawText);

        banner.innerHTML = `
            <div class="hvn-banner-inner">
                <span class="hvn-banner-badge">
                    <span class="hvn-banner-badge-label">${badgeLabel}</span>
                </span>
                <div class="hvn-banner-stream">
                    <span class="hvn-banner-msg">${safeFormatted}</span>
                </div>
                <button type="button" class="hvn-banner-close" aria-label="Dismiss Announcement" title="Dismiss for this session">
                    <i class="fa-solid fa-xmark" aria-hidden="true"></i>
                </button>
            </div>
        `;

        const closeBtn = banner.querySelector('.hvn-banner-close');
        if (closeBtn) {
            closeBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                banner.classList.add('is-dismissed');
                document.body.classList.remove('has-announcement-banner');
                document.documentElement.classList.remove('has-announcement-banner');
                try {
                    sessionStorage.setItem('hvn_announcement_dismissed', rawText);
                } catch (err) {
                    console.warn('[HavenCraftCore] Announcement dismiss save skipped:', err);
                }
                setTimeout(() => {
                    if (banner && banner.parentNode) banner.remove();
                }, 260);
            });
        }

        document.body.classList.add('has-announcement-banner');
        document.documentElement.classList.add('has-announcement-banner');
    }

    function applySiteSettings(settings) {
        if (!settings || typeof settings !== 'object') return;

        // 1. Server Connectivity / Links
        const conn = settings.server_connectivity || settings.server_info;
        if (conn) {
            if (conn.serverIp) window.SERVER_CONFIG.ip = conn.serverIp;
            if (conn.port || conn.javaPort) {
                window.SERVER_CONFIG.port = String(conn.port || conn.javaPort);
                window.SERVER_CONFIG.javaPort = String(conn.javaPort || conn.port);
            }
            if (conn.bedrockPort) window.SERVER_CONFIG.bedrockPort = String(conn.bedrockPort);
            if (conn.discordUrl) window.SERVER_CONFIG.discordUrl = conn.discordUrl;
        }

        // 2. Season & Hero Branding
        const season = settings.season;
        if (season) {
            if (season.name) window.SERVER_CONFIG.seasonName = season.name;
            if (season.badge) window.SERVER_CONFIG.seasonBadge = season.badge;
            if (season.description) window.SERVER_CONFIG.seasonDesc = season.description;

            if (window.STORE_CONFIG) {
                if (!window.STORE_CONFIG.season) window.STORE_CONFIG.season = {};
                if (season.name) window.STORE_CONFIG.season.name = season.name;
                if (season.badge) window.STORE_CONFIG.season.badgeText = season.badge;
                if (season.description) window.STORE_CONFIG.season.description = season.description;
            }
        }

        // 3. Payment Accounts (bKash & Nagad)
        const payment = settings.payment_numbers;
        if (payment) {
            if (window.STORE_CONFIG) {
                if (!window.STORE_CONFIG.payment) window.STORE_CONFIG.payment = {};
                if (payment.bkash) window.STORE_CONFIG.payment.bkashNumber = payment.bkash;
                if (payment.nagad) window.STORE_CONFIG.payment.nagadNumber = payment.nagad;
                if (payment.bkash || payment.nagad) window.STORE_CONFIG.payment.number = payment.bkash || payment.nagad;
                if (payment.type) window.STORE_CONFIG.payment.notice = payment.type;
            }
        }

        // 4. Global Announcement Marquee Banner (Controlled via Admin Dashboard -> Supabase site_settings)
        handleAnnouncementBanner(settings.announcement);

        // Re-apply to DOM elements
        if (typeof window.applyServerConfigToDOM === 'function') {
            window.applyServerConfigToDOM();
        }

        // Fire event for listeners (e.g. store.js)
        window.dispatchEvent(new CustomEvent('hvn_settings_updated', { detail: settings }));
    }

    async function syncSiteSettings() {
        let cached = null;
        try {
            cached = localStorage.getItem('hvn_site_settings');
            if (cached) {
                applySiteSettings(JSON.parse(cached));
            }
        } catch (err) {
            console.warn('[HavenCraftCore] Cached settings read skipped:', err);
        }

        try {
            const { url, key } = SUPABASE_CONFIGS.core;
            const res = await fetch(`${url}/rest/v1/site_settings?select=key,value`, {
                headers: {
                    'apikey': key,
                    'Authorization': `Bearer ${key}`
                }
            });
            if (!res.ok) return;
            const data = await res.json();
            if (!Array.isArray(data)) return;

            const map = {};
            data.forEach(item => {
                if (item && item.key) {
                    map[item.key] = item.value;
                }
            });

            const freshJson = JSON.stringify(map);
            if (freshJson === cached) return;

            try {
                localStorage.setItem('hvn_site_settings', freshJson);
            } catch (err) {
                console.warn('[HavenCraftCore] Settings cache write skipped:', err);
            }

            applySiteSettings(map);
        } catch (err) {
            console.warn('[HavenCraftCore] Live settings sync fallback:', err);
        }
    }

    let currentUser = null;

    function initUserSession() {
        try {
            const gametag = localStorage.getItem('havencraft-gametag');
            if (gametag) {
                currentUser = gametag;
                window.currentUser = gametag;
            }
        } catch (err) {
            console.warn('[HavenCraftCore] Gametag session read skipped:', err);
        }
    }

    function getCurrentUser() {
        return currentUser;
    }

    function login(gametag) {
        if (!gametag || gametag.length < 3 || gametag.length > 24) {
            return { success: false, error: 'Invalid username' };
        }
        try {
            localStorage.setItem('havencraft-gametag', gametag);
        } catch (err) {
            console.warn('[HavenCraftCore] Gametag save skipped:', err);
        }
        currentUser = gametag;
        window.currentUser = gametag;
        window.dispatchEvent(new CustomEvent('hc_login', { detail: { gametag } }));
        return { success: true };
    }

    function logout() {
        try {
            localStorage.removeItem('havencraft-gametag');
        } catch (err) {
            console.warn('[HavenCraftCore] Gametag clear skipped:', err);
        }
        currentUser = null;
        window.currentUser = null;
        window.dispatchEvent(new CustomEvent('hc_logout'));
    }

    function getClient(globalKey) {
        if (!window[globalKey]) {
            initSupabase();
        }
        return window[globalKey] || null;
    }

    function getLeaderboard() {
        return getClient('supabaseLeaderboard');
    }

    function getVote() {
        return getClient('supabaseVote');
    }

    function getGallery() {
        return getClient('supabaseGallery');
    }

    function getCore() {
        return getClient('supabaseCore');
    }

    function getSupabaseConfig(section = 'core') {
        const cfg = SUPABASE_CONFIGS[section];
        return cfg ? { url: cfg.url, key: cfg.key } : null;
    }

    function getVoteSites() {
        if (Array.isArray(window.VOTE_SITES) && window.VOTE_SITES.length > 0) {
            return window.VOTE_SITES;
        }
        return [];
    }

    function init() {
        if (!initSupabase()) {
            document.addEventListener('DOMContentLoaded', initSupabase, { once: true });
        }
        initUserSession();
        syncSiteSettings();
    }

    init();

    // Auto-refresh live settings when returning to tab
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            syncSiteSettings();
        }
    });

    return {
        initSupabase,
        login,
        logout,
        getCurrentUser,
        getLeaderboard,
        getVote,
        getGallery,
        getCore,
        getSupabaseConfig,
        getVoteSites,
        getServerConfig: () => window.SERVER_CONFIG,
        syncSiteSettings,
        applySiteSettings,
        isReady: () => !!(getLeaderboard() && getVote())
    };
})();
