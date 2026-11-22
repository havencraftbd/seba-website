/* ============================================
   HavenCraft — Utility JavaScript
   API, Copy IP, Animations, Player Modal
   ============================================ */

const API_URL = '/api/status';
const REFRESH_INTERVAL = 30000;
const MINOTAR_AVATAR_BASE = 'https://minotar.net/avatar/';
const MC_HEAD_URL = 'https://mc-heads.net/avatar/';

function getFallbackApis() {
    const ip = window.SERVER_CONFIG?.ip || 'mc.havencraft.pro';
    const bedrockPort = window.SERVER_CONFIG?.bedrockPort || 2025;
    return [
        `https://api.mcstatus.io/v2/status/java/${ip}`,
        `https://api.mcsrvstat.us/3/${ip}`,
        `https://api.mcstatus.io/v2/status/bedrock/${ip}:${bedrockPort}`,
        `https://api.mcsrvstat.us/bedrock/3/${ip}:${bedrockPort}`
    ];
}

// Throttle network-failure toasts to one per context every 60s
window._hcErrorShown = {};
function showNetworkError(context, err) {
    const now = Date.now();
    if (window._hcErrorShown[context] && now - window._hcErrorShown[context] < 60000) return;
    window._hcErrorShown[context] = now;

    const messages = {
        'server-status': 'Could not reach the server. Check your connection.',
        'leaderboard': 'Leaderboard data unavailable. We\'ll retry shortly.',
        'search': 'Player search failed. Please try again.',
        'gallery': 'Gallery could not be loaded right now.',
        'profile': 'Profile data failed to load. Please refresh.',
        'vote': 'Vote system temporarily unavailable.',
        'default': 'Something went wrong. Please try again later.'
    };

    const msg = messages[context] || messages['default'];
    if (typeof showToast === 'function') {
        showToast(msg);
    }
    console.warn(`[HavenCraft:${context}]`, err);
}

window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason;
    if (reason instanceof TypeError && reason.message && reason.message.includes('fetch')) {
        showNetworkError('default', reason);
    }
});

window.getRootPath = function (scriptName) {
    let root = './';
    const scripts = document.getElementsByTagName('script');
    for (const s of scripts) {
        const rawSrc = s.getAttribute('src');
        if (!rawSrc) continue;
        const cleanSrc = rawSrc.split('?')[0];
        if (scriptName && cleanSrc.includes(scriptName)) {
            root = cleanSrc.replace(scriptName, '');
            return root === '' ? './' : root;
        }
        if (cleanSrc.includes('assets/js/')) {
            return cleanSrc.split('assets/js/')[0] || './';
        }
    }
    const pathParts = window.location.pathname.split('/').filter(Boolean);
    if (pathParts.length > 0 && !pathParts[pathParts.length - 1].includes('.')) {
        return '../';
    }
    return root === '' ? './' : root;
};

let _lastPlayerList = [];
let _lastPlayerOnlineCount = 0;
let _lastPlayerMaxCount = 0;

function escapeHtml(text) {
    if (!text) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
window.escapeHtml = escapeHtml;

function escapeSqlWildcards(text) {
    return String(text || '').replace(/[%_\\]/g, '\\$&');
}
window.escapeSqlWildcards = escapeSqlWildcards;

function formatCompact(num) {
    const val = Number(num);
    if (isNaN(val)) return '0';
    if (val >= 1e9) return (val / 1e9).toFixed(1).replace(/\.0$/, '') + 'B';
    if (val >= 1e6) return (val / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';
    if (val >= 1e4) return (val / 1e3).toFixed(1).replace(/\.0$/, '') + 'K';
    return val.toLocaleString('en-US');
}
window.formatCompact = formatCompact;

function getPlayerAvatarUrls(username, size = 40) {
    const raw = String(username || 'MHF_Steve');
    const cleanName = raw.replace(/^\.+/, '') || 'MHF_Steve';
    const encoded = encodeURIComponent(cleanName);
    return {
        raw,
        cleanName,
        primary: `${MINOTAR_AVATAR_BASE}${encoded}/${size}.png`,
        fallback: `${MC_HEAD_URL}${encoded}/${size}`,
        steve: `${MC_HEAD_URL}MHF_Steve/${size}`
    };
}
window.getPlayerAvatarUrls = getPlayerAvatarUrls;

function renderAvatarImgHtml(username, opts = {}) {
    const size = Number(opts.size) || 40;
    const urls = getPlayerAvatarUrls(username, size);
    const alt = escapeHtml(opts.alt ?? urls.raw);
    const cls = opts.className ? ` class="${escapeHtml(opts.className)}"` : '';
    const lazy = opts.lazy ? ' loading="lazy"' : '';
    const dims = opts.dimensions ? ` width="${size}" height="${size}"` : '';
    return `<img src="${urls.primary}" alt="${alt}"${cls}${dims}${lazy} onerror="if(!this.dataset.triedMc){this.dataset.triedMc='1';this.src='${urls.fallback}';}else{this.onerror=null;this.src='${urls.steve}';}" />`;
}
window.renderAvatarImgHtml = renderAvatarImgHtml;

let _turnstileWaiters = null;
function ensureTurnstileReady() {
    if (window.turnstile && typeof window.turnstile.render === 'function') {
        return Promise.resolve(true);
    }
    if (_turnstileWaiters) return _turnstileWaiters;

    _turnstileWaiters = new Promise((resolve) => {
        const done = (ok) => {
            _turnstileWaiters = null;
            resolve(ok);
        };

        let script = document.querySelector('script[src*="challenges.cloudflare.com/turnstile"]');
        if (!script) {
            script = document.createElement('script');
            script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
            script.async = true;
            script.defer = true;
            document.head.appendChild(script);
        }

        const startWait = Date.now();
        const check = () => {
            if (window.turnstile && typeof window.turnstile.render === 'function') {
                return done(true);
            }
            if (Date.now() - startWait > 5000) {
                return done(false);
            }
            setTimeout(check, 100);
        };

        script.addEventListener('load', () => check(), { once: true });
        script.addEventListener('error', () => done(false), { once: true });
        check();
    });

    return _turnstileWaiters;
}

window.mountTurnstileWidget = async function (containerOrId, opts = {}) {
    const container = typeof containerOrId === 'string'
        ? document.getElementById(containerOrId)
        : containerOrId;
    if (!container) return null;

    if (window.location.hostname === '127.0.0.1') {
        container.innerHTML = '<span class="turnstile-local-note">Turnstile Security Check (active on havencraft.pro)</span>';
        if (typeof opts.onReady === 'function') opts.onReady(null);
        return null;
    }

    const ready = await ensureTurnstileReady();
    if (!ready || !window.turnstile || typeof window.turnstile.render !== 'function') {
        return null;
    }

    if (opts.widgetId !== undefined && opts.widgetId !== null && container.hasChildNodes()) {
        try {
            window.turnstile.reset(opts.widgetId);
            if (typeof opts.onReady === 'function') opts.onReady(opts.widgetId);
            return opts.widgetId;
        } catch (_) {
            // Stale widget container; re-render cleanly below
        }
    }

    const siteKey = opts.siteKey || window.SERVER_CONFIG?.turnstileSiteKey || '0x4AAAAAAFwXQNw_tYM5tZ8P';
    container.innerHTML = '';
    try {
        const id = window.turnstile.render(container, {
            sitekey: siteKey,
            theme: opts.theme || 'dark',
            'error-callback': (code) => {
                console.warn('[Turnstile Notice]:', code);
                return true;
            }
        });
        if (typeof opts.onReady === 'function') opts.onReady(id);
        return id;
    } catch (err) {
        console.warn('[Turnstile Render Error]:', err);
        return null;
    }
};

const getHomeDOM = () => ({
    playerCount: document.getElementById('playerCount'),
    playerMax: document.getElementById('playerMax'),
    playerCountLarge: document.getElementById('playerCountLarge'),
    statusBadge: document.getElementById('statusBadge'),
    statusText: document.getElementById('statusText'),
    statPlayers: document.getElementById('statPlayers'),
    statStatus: document.getElementById('statStatus'),
    statusIndicator: document.getElementById('statusIndicator')
});

/* ============================================
   SERVER API
   ============================================ */
function calibrateServerClock(response, json) {
    if (json && typeof json.server_time === 'number' && !isNaN(json.server_time)) {
        window._serverClockOffset = json.server_time - Date.now();
        return;
    }
    const serverDateHeader = response?.headers?.get?.('date');
    if (serverDateHeader) {
        const headerMs = new Date(serverDateHeader).getTime();
        if (!isNaN(headerMs)) {
            window._serverClockOffset = headerMs - Date.now();
        }
    }
}

let _clockSyncPromise = null;
window.syncServerClock = async function () {
    if (typeof window._serverClockOffset === 'number' && !isNaN(window._serverClockOffset)) {
        return window._serverClockOffset;
    }
    if (_clockSyncPromise) return _clockSyncPromise;

    _clockSyncPromise = (async () => {
        try {
            const liveIp = window.SERVER_CONFIG?.ip || 'mc.havencraft.pro';
            const livePort = window.SERVER_CONFIG?.bedrockPort || window.SERVER_CONFIG?.port || 2566;
            const statusUrl = `${API_URL}?ip=${encodeURIComponent(liveIp)}&port=${encodeURIComponent(livePort)}`;
            const response = await fetch(statusUrl, { cache: 'no-store' });
            if (response.ok) {
                const json = await response.json().catch(() => null);
                calibrateServerClock(response, json);
            }
        } catch (_) {
            // Silently fall back to client clock when offline or in static preview
        } finally {
            _clockSyncPromise = null;
        }
        return window._serverClockOffset || 0;
    })();

    return _clockSyncPromise;
};

async function fetchServerStatus() {
    try {
        let data = null;

        // 1. Try internal edge proxy first (passing live Supabase IP & Port if customized)
        try {
            const liveIp = window.SERVER_CONFIG?.ip || 'mc.havencraft.pro';
            const livePort = window.SERVER_CONFIG?.bedrockPort || window.SERVER_CONFIG?.port || 2566;
            const statusUrl = `${API_URL}?ip=${encodeURIComponent(liveIp)}&port=${encodeURIComponent(livePort)}`;
            const response = await fetch(statusUrl);
            if (response.ok) {
                const json = await response.json();
                if (json && json.online) data = json;
                calibrateServerClock(response, json);
            }
        } catch (proxyError) {
            // Fall through to direct providers
        }

        // 2. Multi-source fallback loop if proxy is offline or failed
        if (!data || !data.online) {
            for (const endpoint of getFallbackApis()) {
                try {
                    const response = await fetch(endpoint);
                    if (response.ok) {
                        const json = await response.json();
                        if (json && json.online) {
                            data = {
                                online: true,
                                players: {
                                    online: json.players?.online ?? 0,
                                    max: json.players?.max ?? 100,
                                    list: Array.isArray(json.players?.list)
                                        ? json.players.list
                                            .map(p => (typeof p === 'string' ? p : (p?.name_clean || p?.name || p?.username || p?.name_raw || '')))
                                            .filter(Boolean)
                                        : []
                                }
                            };
                            break;
                        }
                    }
                } catch (err) {
                    // Try next provider
                }
            }
        }

        if (data && data.online) {
            const online = data.players?.online || 0;
            const max = data.players?.max || 0;

            updateUniquePlayersCount(data);
            updateUI(true, online, max, data.players?.list);
        } else {
            updateUniquePlayersCount(data);
            updateUI(false, 0, 0);
        }
    } catch (error) {
        showNetworkError('server-status', error);
        updateUniquePlayersCount(data);
        updateUI(false, 0, 0);
    }
}

function updateUniquePlayersCount(data) {
    const uniqueEl = document.getElementById('uniquePlayers');
    if (!uniqueEl) return;

    const client = window.supabaseLeaderboard || window.HavenCraftCore?.getLeaderboard?.();
    const applyFallback = () => {
        if (data?.players?.unique) {
            uniqueEl.textContent = data.players.unique + '+';
        } else if (!uniqueEl.textContent.trim()) {
            uniqueEl.textContent = '1200+';
        }
    };

    if (!client) {
        applyFallback();
        return;
    }

    client.from('player_stats').select('*', { count: 'exact', head: true })
        .then(({ count, error }) => {
            if (!error && count) {
                uniqueEl.textContent = count + '+';
            } else {
                applyFallback();
            }
        })
        .catch(() => {
            applyFallback();
        });
}

function updateUI(isOnline, online, max, playerListData = []) {
    _lastPlayerList = playerListData; // cache for openPlayerModal
    _lastPlayerOnlineCount = online; // cache for openPlayerModal
    _lastPlayerMaxCount = max; // cache for openPlayerModal
    const DOM = getHomeDOM();
    if (!DOM.playerCount) return;

    if (isOnline) {
        DOM.playerCount.textContent = online;
        if (DOM.playerMax) DOM.playerMax.textContent = max;
        if (DOM.playerCountLarge) {
            DOM.playerCountLarge.innerHTML = `${online} <span style="opacity:0.6">/ ${max}</span>`;
        }

        if (DOM.statusBadge) {
            DOM.statusBadge.classList.remove('offline');
        }
        if (DOM.statusText) DOM.statusText.textContent = 'Server Online';

        if (DOM.statPlayers) {
            DOM.statPlayers.textContent = `${online}`;
        }

        if (DOM.statStatus) {
            DOM.statStatus.innerHTML = '<span class="status-indicator status-indicator-lg"><span class="dot"></span> Online</span>';
        }
        if (DOM.statusIndicator) DOM.statusIndicator.className = 'status-dot';

        const rosterTextEl = document.getElementById('heroRosterText');
        const avatarStackEl = document.getElementById('heroAvatarStack');
        if (rosterTextEl) {
            if (online > 0) {
                rosterTextEl.innerHTML = `<strong>${online} ${online === 1 ? 'player' : 'players'}</strong> online now • Click to inspect`;
            } else {
                rosterTextEl.innerHTML = `Server Online • <strong>mc.havencraft.pro</strong> ready`;
            }
        }
        if (avatarStackEl && Array.isArray(playerListData) && playerListData.length > 0) {
            const sample = playerListData.slice(0, 4);
            const headsHtml = sample.map(p => {
                const rawName = typeof p === 'string' ? p : (p?.name_clean || p?.name || p?.username || p?.name_raw || 'MHF_Steve');
                return renderAvatarImgHtml(rawName, { size: 32, className: 'hero-stack-head' });
            }).join('');
            if (headsHtml) {
                avatarStackEl.innerHTML = headsHtml;
                avatarStackEl.dataset.livePopulated = 'true';
            }
        }
    } else {
        DOM.playerCount.textContent = '—';
        if (DOM.playerMax) DOM.playerMax.textContent = '—';
        if (DOM.playerCountLarge) {
            DOM.playerCountLarge.innerHTML = `<span style="font-size:1.4rem">Server Offline</span>`;
        }
        if (DOM.statusBadge) {
            DOM.statusBadge.classList.add('offline');
        }
        if (DOM.statusText) DOM.statusText.textContent = 'Server Offline';

        if (DOM.statPlayers) DOM.statPlayers.textContent = '—';
        if (DOM.statStatus) {
            DOM.statStatus.innerHTML = '<span class="status-indicator status-indicator-lg offline"><span class="dot"></span> Offline</span>';
        }
        if (DOM.statusIndicator) DOM.statusIndicator.className = 'status-dot offline';

        const rosterTextEl = document.getElementById('heroRosterText');
        if (rosterTextEl) {
            rosterTextEl.textContent = 'Server currently offline or restarting';
        }
    }
}

// Server status polling with Page Visibility optimization (pauses when tab is hidden)
let _statusPollInterval = null;

function startStatusPolling() {
    if (_statusPollInterval) return;
    fetchServerStatus();
    _statusPollInterval = setInterval(fetchServerStatus, REFRESH_INTERVAL);
}

function stopStatusPolling() {
    if (_statusPollInterval) {
        clearInterval(_statusPollInterval);
        _statusPollInterval = null;
    }
}

if (document.getElementById('playerCount') || document.getElementById('uniquePlayers')) {
    startStatusPolling();

    // Pause polling when tab is minimized or hidden, resume instantly when user returns
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            stopStatusPolling();
        } else {
            startStatusPolling();
        }
    });
}

/* ============================================
   COPY IP
   ============================================ */
function fallbackCopyText(text) {
    try {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.setAttribute('readonly', '');
        textarea.style.position = 'fixed';
        textarea.style.left = '-9999px';
        textarea.style.top = '0';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        const ok = document.execCommand('copy');
        document.body.removeChild(textarea);
        return ok;
    } catch (_) {
        return false;
    }
}

async function copyText(text) {
    const value = String(text ?? '');
    if (navigator.clipboard && navigator.clipboard.writeText) {
        try {
            await navigator.clipboard.writeText(value);
            return true;
        } catch (_) {
            return fallbackCopyText(value);
        }
    }
    return fallbackCopyText(value);
}
window.copyText = copyText;

function copyIP(element) {
    const liveIp = window.SERVER_CONFIG?.ip || 'mc.havencraft.pro';
    copyText(liveIp).then((ok) => {
        if (!ok) {
            showToast('Failed to copy IP');
            return;
        }
        showToast('Copied!', { icon: 'fa-solid fa-circle-check' });

        if (!element) return;
        element.classList.add('copied');

        const bounceTarget = element.querySelector('.copy-ip-btn') || element;
        bounceTarget.classList.remove('hc-bounce');
        void bounceTarget.offsetWidth;
        bounceTarget.classList.add('hc-bounce');

        const textEl = element.querySelector('.copy-btn-text');
        if (textEl) {
            if (!textEl.dataset.originalText) textEl.dataset.originalText = textEl.textContent;
            textEl.textContent = 'Copied!';
        }

        const copyIcon = element.querySelector('.fa-copy, .fa-regular.fa-copy, .fa-solid.fa-copy, .copy-icon');
        if (copyIcon) {
            if (!copyIcon.dataset.originalIcon) copyIcon.dataset.originalIcon = copyIcon.className;
            copyIcon.className = 'fa-solid fa-check';
        }

        clearTimeout(element._hcCopyTimer);
        element._hcCopyTimer = setTimeout(() => {
            element.classList.remove('copied');
            bounceTarget.classList.remove('hc-bounce');

            if (textEl && textEl.dataset.originalText) {
                textEl.textContent = textEl.dataset.originalText;
                delete textEl.dataset.originalText;
            }
            if (copyIcon && copyIcon.dataset.originalIcon) {
                copyIcon.className = copyIcon.dataset.originalIcon;
                delete copyIcon.dataset.originalIcon;
            }
        }, 2000);
    });
}
window.copyIP = copyIP;

function copyServerIP(ip, element, port) {
    if (typeof element === 'string' && port === undefined) {
        port = element;
        element = null;
    }
    if (window.getSelection) {
        try { window.getSelection().removeAllRanges(); } catch (_) {}
    }

    const liveIp = window.SERVER_CONFIG?.ip || ip || 'mc.havencraft.pro';
    let livePort = null;
    let isBedrock = false;
    if (port) {
        if (port === 'bedrock' || String(port) === String(window.SERVER_CONFIG?.bedrockPort || '2025')) {
            isBedrock = true;
            livePort = window.SERVER_CONFIG?.bedrockPort || '2025';
        } else if (port === 'java' || String(port) === '2566') {
            livePort = window.SERVER_CONFIG?.javaPort || window.SERVER_CONFIG?.port || '2566';
        } else {
            livePort = window.SERVER_CONFIG?.port || port;
        }
    }
    const fullAddress = livePort ? `${liveIp}:${livePort}` : liveIp;

    copyText(fullAddress).then((ok) => {
        if (!ok) {
            showToast('Failed to copy');
            return;
        }
        showToast('Copied!', { icon: 'fa-solid fa-circle-check' });

        if (element && element.classList) {
            element.classList.add('copied');
            const badge = element.querySelector('.copy-badge');
            const copyIcon = element.querySelector('.fa-copy, .fa-regular.fa-copy, .fa-solid.fa-copy, .copy-icon');

            if (badge) {
                if (!badge.dataset.originalHtml) badge.dataset.originalHtml = badge.innerHTML;
                badge.innerHTML = '<i class="fa-solid fa-check"></i> <span>Copied!</span>';
                badge.classList.add('copied-success');
            }
            if (copyIcon) {
                if (!copyIcon.dataset.originalIcon) copyIcon.dataset.originalIcon = copyIcon.className;
                copyIcon.className = 'fa-solid fa-check';
            }

            clearTimeout(element._hcCopyTimer);
            element._hcCopyTimer = setTimeout(() => {
                element.classList.remove('copied');
                if (badge && badge.dataset.originalHtml) {
                    badge.innerHTML = badge.dataset.originalHtml;
                    badge.classList.remove('copied-success');
                    delete badge.dataset.originalHtml;
                }
                if (copyIcon && copyIcon.dataset.originalIcon) {
                    copyIcon.className = copyIcon.dataset.originalIcon;
                    delete copyIcon.dataset.originalIcon;
                }
            }, 2000);
        }
    });
}
window.copyServerIP = copyServerIP;

function showToast(message, options) {
    let toast = document.getElementById('toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'toast';
        toast.className = 'toast';
        document.body.appendChild(toast);
    }
    if (options && options.icon) {
        toast.textContent = '';
        const iconEl = document.createElement('i');
        iconEl.className = options.icon;
        iconEl.setAttribute('aria-hidden', 'true');
        const textEl = document.createElement('span');
        textEl.textContent = message;
        toast.appendChild(iconEl);
        toast.appendChild(textEl);
    } else {
        toast.textContent = message;
    }
    toast.classList.add('show');
    clearTimeout(toast.timeoutId);
    toast.timeoutId = setTimeout(() => {
        toast.classList.remove('show');
    }, 2500);
}
window.showToast = showToast;

/* ============================================
   SMART MAILTO & EMAIL COPY HANDLER
   Works on all desktop & mobile browsers:
   - Attempts to launch mail client (default browser action)
   - Automatically copies email address to clipboard
   - Shows feedback toast: "✓ Email copied: support@havencraft.pro"
   - Visual checkmark feedback on the clicked button
   ============================================ */
function initGlobalMailtoHandler() {
    document.addEventListener('click', (e) => {
        const mailLink = e.target.closest('a[href^="mailto:"]');
        if (!mailLink) return;

        const href = mailLink.getAttribute('href') || '';
        const emailMatch = href.match(/^mailto:([^?&]+)/i);
        if (!emailMatch || !emailMatch[1]) return;

        const email = decodeURIComponent(emailMatch[1]).trim();
        if (!email) return;

        copyText(email);
        showToast(`✓ Email copied: ${email}`);

        const icon = mailLink.querySelector('.fa-solid, .fa-regular, .fa-brands, i');
        if (icon && !mailLink._hcOriginalIcon) {
            mailLink._hcOriginalIcon = icon.className;
            icon.className = 'fa-solid fa-check';
            setTimeout(() => {
                if (mailLink._hcOriginalIcon) {
                    icon.className = mailLink._hcOriginalIcon;
                    delete mailLink._hcOriginalIcon;
                }
            }, 2000);
        }
    }, { capture: true });
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initGlobalMailtoHandler);
} else {
    initGlobalMailtoHandler();
}

/* ============================================
   PLAYER MODAL (focus trapping included)
   ============================================ */
document.addEventListener('DOMContentLoaded', () => {
    const closeBtn = document.getElementById('closePlayerModal');
    const modalOverlay = document.getElementById('playerModal');
    let lastFocusedElement = null;

    function openModal() {
        lastFocusedElement = document.activeElement;
        modalOverlay.classList.add('show');
        document.body.classList.add('modal-open');
        if (closeBtn) closeBtn.focus();
    }

    function closeModal() {
        modalOverlay.classList.remove('show');
        document.body.classList.remove('modal-open');
        if (lastFocusedElement) lastFocusedElement.focus();
    }

    function trapFocus(e) {
        if (!modalOverlay.classList.contains('show')) return;
        const focusable = modalOverlay.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
            e.preventDefault();
            last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
            e.preventDefault();
            first.focus();
        }
    }

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (modalOverlay) {
        modalOverlay.addEventListener('click', (e) => {
            if (e.target === modalOverlay) closeModal();
        });
        modalOverlay.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') closeModal();
            if (e.key === 'Tab') trapFocus(e);
        });
    }

    window.showPlayerModal = function (players, onlineCount = 0, maxCount = 0) {
        const rootPath = window.getRootPath ? window.getRootPath('assets/js/utils.js') : './';

        const modalTitle = document.getElementById('playerModalTitle');
        if (modalTitle) {
            modalTitle.innerHTML = `Online Players <span class="modal-badge">${onlineCount} / ${maxCount}</span>`;
        }

        let html = '';

        const validPlayers = Array.isArray(players)
            ? players
                .map(p => (typeof p === 'string' ? p : (p?.name_clean || p?.name || p?.username || p?.name_raw || '')))
                .filter(Boolean)
            : [];

        // Grid Container
        html += `<div class="player-grid" id="playerModalGrid">`;
        if (validPlayers.length > 0) {
            validPlayers.forEach(playerName => {
                const safeName = escapeHtml(playerName);
                const profileUrl = rootPath + 'profile/?player=' + encodeURIComponent(playerName);
                const imgHtml = renderAvatarImgHtml(playerName, { size: 40, lazy: true });
                html += `
                <div class="player-grid-item" data-username="${safeName}">
                    ${imgHtml}
                    <div class="player-info">
                        <span class="player-name">${safeName}</span>
                        <a href="${profileUrl}" class="player-view-link">View Profile <i class="fa-solid fa-arrow-right"></i></a>
                    </div>
                </div>`;
            });
        } else if (onlineCount > 0) {
            html += `
            <div style="text-align: center; width: 100%; grid-column: span 2; padding: 25px 15px; color: var(--text-secondary);">
                <i class="fa-solid fa-gamepad" style="font-size: 2rem; color: var(--primary); margin-bottom: 10px; display: inline-block;"></i>
                <p style="margin: 0; font-size: 1rem; font-weight: 600; color: var(--text);"><strong>${onlineCount} players</strong> are currently playing on HavenCraft!</p>
                <p style="font-size: 0.85rem; color: var(--text-muted); margin-top: 8px;">Player names are protected by server privacy settings. Join now to meet them in-game!</p>
            </div>`;
        } else {
            html += `<p style="color: var(--text-muted); text-align: center; width: 100%; grid-column: span 2; padding: 20px 0;">No players currently online. Be the first to join!</p>`;
        }
        html += `</div>`;

        const playerListEl = document.getElementById('playerList');
        if (playerListEl) {
            playerListEl.innerHTML = html;
        }

        openModal();
    };

    // Global wrapper for HTML onclick="openPlayerModal()" in index.html
    window.openPlayerModal = function () {
        if (window.showPlayerModal) {
            window.showPlayerModal(_lastPlayerList, _lastPlayerOnlineCount, _lastPlayerMaxCount);
        }
    };
});
