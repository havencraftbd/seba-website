/**
 * HavenCraft — Vote Cooldown Engine
 * 
 * Maps known database site names and service names to canonical site IDs.
 * Uses pre-computed alias maps for O(1) lookups and supports server clock calibration.
 */

const VOTE_SITE_ALIASES = {
    'havencraft': [
        'havencraft',
        'topminecraftservers',
        'topminecraftservers.org',
        'tms'
    ],
    'minecraft-mp': [
        'minecraft-mp',
        'minecraft-mp.com',
        'minecraftmp'
    ],
    'planetminecraft': [
        'planetminecraft',
        'planetminecraft.com',
        'planet-minecraft',
        'pmc'
    ],
    'mclist': [
        'mclist',
        'mclist.io',
        'mclistio'
    ],
    'minecraftservers': [
        'minecraftservers',
        'minecraftservers.org',
        'minecraft-serverlist-com',
        'minecraftservers.page',
        'mcsl'
    ],
    'topg': [
        'topg',
        'topg.org'
    ],
    'minecraftkrant': [
        'minecraftkrant',
        'minecraftkrant.nl'
    ],
    'minecraftiplist': [
        'minecraftiplist',
        'minecraftiplist.com'
    ]
};

const NORMALIZED_ALIAS_MAP = (function () {
    const map = new Map();
    const SUFFIXES = ['org', 'com', 'nl', 'io', 'net', 'page'];

    for (const [siteId, aliases] of Object.entries(VOTE_SITE_ALIASES)) {
        for (const alias of aliases) {
            const clean = alias.toLowerCase().replace(/[^a-z0-9]/g, '');
            map.set(clean, siteId);
            for (const sfx of SUFFIXES) {
                if (!clean.endsWith(sfx)) {
                    map.set(clean + sfx, siteId);
                }
            }
        }
    }
    return map;
})();

function getSiteIdForDbName(dbName) {
    if (!dbName) return null;
    const clean = String(dbName).toLowerCase().replace(/[^a-z0-9]/g, '');
    return NORMALIZED_ALIAS_MAP.get(clean) || null;
}

/**
 * Calculates current vote cooldowns for a list of voting sites
 * @param {Object} votesBySite - Map of { siteName: lastVoteTimestamp }
 * @param {Array} sites - List of site config objects
 * @param {number} [serverTimeNow] - Optional calibrated server timestamp to prevent client clock skew
 * @returns {Array} - Array of cooldown status objects
 */
function calculateSiteCooldowns(votesBySite, sites, serverTimeNow) {
    // Calibrate with server time offset if available to prevent client clock tampering/skew
    const now = (typeof serverTimeNow === 'number' && !isNaN(serverTimeNow))
        ? serverTimeNow
        : (typeof window !== 'undefined' && window._serverClockOffset
            ? Date.now() + window._serverClockOffset
            : Date.now());

    // Map DB votes to siteId with maximum timestamp in O(N)
    const latestVotePerSiteId = new Map();

    if (votesBySite && typeof votesBySite === 'object') {
        for (const [dbSite, timeStr] of Object.entries(votesBySite)) {
            if (!dbSite || dbSite === 'null' || !timeStr) continue;

            const voteMs = new Date(timeStr).getTime();
            if (isNaN(voteMs)) continue;

            const siteId = getSiteIdForDbName(dbSite);
            if (siteId) {
                const currentLatest = latestVotePerSiteId.get(siteId) || 0;
                if (voteMs > currentLatest) {
                    latestVotePerSiteId.set(siteId, voteMs);
                }
            }
        }
    }

    const siteList = Array.isArray(sites) ? sites : [];
    const results = [];

    for (const site of siteList) {
        const siteId = (site.id || '').toLowerCase().trim();
        const lastVoteTimeMs = latestVotePerSiteId.get(siteId) || null;

        const cooldownHours = Number(site.cooldown_hours) || 24;
        const cooldownMs = cooldownHours * 60 * 60 * 1000;

        let state = 'available';
        let remainingMs = 0;
        let lastVoteAt = null;

        if (lastVoteTimeMs) {
            lastVoteAt = new Date(lastVoteTimeMs).toISOString();
            const timeSinceVote = now - lastVoteTimeMs;
            remainingMs = cooldownMs - timeSinceVote;

            if (remainingMs > 0) {
                state = 'cooldown';
            } else {
                state = 'available';
                remainingMs = 0;
            }
        }

        results.push({
            site,
            state,
            remainingMs: Math.max(0, Math.floor(remainingMs)),
            lastVoteAt
        });
    }

    return results;
}

/**
 * Formats milliseconds into human-readable HH:MM:SS countdown
 * @param {number} ms 
 * @returns {string} e.g. "05:24:12"
 */
function formatCountdown(ms) {
    if (ms <= 0) return '00:00:00';

    const totalSeconds = Math.floor(ms / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

// Expose globally for browser usage
if (typeof window !== 'undefined') {
    window.calculateSiteCooldowns = calculateSiteCooldowns;
    window.formatCountdown = formatCountdown;
    window.getSiteIdForDbName = getSiteIdForDbName;
}
