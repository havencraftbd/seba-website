/* ============================================
   HavenCraft — Shared Leaderboard Primitives
   Categories, Formatters, Supabase Access, Refresh Loop
   ============================================ */

window.HavenLeaderboardCore = (function () {
    'use strict';

    const DEFAULT_REFRESH_MS = 60000;

    function compact(num) {
        if (typeof window.formatCompact === 'function') {
            return window.formatCompact(num);
        }
        const val = Number(num);
        if (Number.isNaN(val)) return '0';
        return val.toLocaleString('en-US');
    }

    function escapeText(text) {
        return typeof window.escapeHtml === 'function' ? window.escapeHtml(text) : String(text || '');
    }

    function formatHours(seconds) {
        return (Number(seconds || 0) / 3600).toFixed(1);
    }

    function formatInteger(val) {
        return Number(val || 0).toLocaleString('en-US');
    }

    const CATEGORIES = {
        playtime: {
            column: 'playtime_sec',
            label: 'Playtime',
            summaryId: 'topSummaryPlaytime',
            format: (val) => `${formatHours(val)}h`,
            tooltip: (val) => `${formatHours(val)} hours`
        },
        kills: {
            column: 'kills',
            label: 'Kills',
            summaryId: 'topSummaryKills',
            format: (val, useCompact) => (useCompact ? compact(val) : formatInteger(val)),
            tooltip: (val) => formatInteger(val)
        },
        deaths: {
            column: 'deaths',
            label: 'Deaths',
            summaryId: 'topSummaryDeaths',
            format: (val, useCompact) => (useCompact ? compact(val) : formatInteger(val)),
            tooltip: (val) => formatInteger(val)
        },
        money: {
            column: 'money',
            label: 'Money',
            summaryId: 'topSummaryMoney',
            format: (val) => `$${compact(val)}`,
            tooltip: (val) => `$${formatInteger(val)}`
        },
        shards: {
            column: 'shards',
            label: 'Shards',
            summaryId: 'topSummaryShards',
            format: (val) => compact(val),
            tooltip: (val) => `${formatInteger(val)} Shards`
        },
        block_place: {
            column: 'block_place',
            label: 'Blocks Placed',
            summaryId: 'topSummaryBlockPlace',
            format: (val) => compact(val),
            tooltip: (val) => formatInteger(val)
        },
        block_break: {
            column: 'block_break',
            label: 'Blocks Broken',
            summaryId: 'topSummaryBlockBreak',
            format: (val) => compact(val),
            tooltip: (val) => formatInteger(val)
        },
        mobs_killed: {
            column: 'mobs_killed',
            label: 'Mobs Killed',
            summaryId: 'topSummaryMobsKilled',
            format: (val) => compact(val),
            tooltip: (val) => formatInteger(val)
        }
    };

    function getClient() {
        if (window.supabaseLeaderboard) return window.supabaseLeaderboard;
        if (window.HavenCraftCore && typeof window.HavenCraftCore.getLeaderboard === 'function') {
            return window.HavenCraftCore.getLeaderboard();
        }
        return null;
    }

    function waitForClient() {
        const existing = getClient();
        if (existing) return Promise.resolve(existing);

        return new Promise((resolve) => {
            let settled = false;
            const finish = () => {
                if (settled) return;
                settled = true;
                resolve(getClient());
            };

            const sdkScript = document.querySelector('script[src*="supabase-js"]');
            if (sdkScript) {
                sdkScript.addEventListener('load', finish, { once: true });
                sdkScript.addEventListener('error', finish, { once: true });
            }
            if (document.readyState !== 'complete') {
                window.addEventListener('load', finish, { once: true });
            } else {
                queueMicrotask(finish);
            }
        });
    }

    function formatCategoryValue(category, rawVal, useCompact = false) {
        const config = CATEGORIES[category];
        if (!config) {
            return { value: String(rawVal || 0), tooltip: String(rawVal || 0) };
        }
        return {
            value: config.format(rawVal, useCompact),
            tooltip: config.tooltip(rawVal)
        };
    }

    function buildProfileUrl(rootPath, username) {
        return `${rootPath}profile/?player=${encodeURIComponent(username)}`;
    }

    function renderAvatar(username, size, className, lazy = false) {
        if (typeof window.renderAvatarImgHtml === 'function') {
            return window.renderAvatarImgHtml(username, { size, className, lazy });
        }
        return `<img src="https://mc-heads.net/avatar/MHF_Steve/${size}" alt="${escapeText(username)}" class="${escapeText(className)}" />`;
    }

    function createAutoRefresher(onTick, intervalMs = DEFAULT_REFRESH_MS) {
        let timer = null;

        function stop() {
            if (timer) {
                clearInterval(timer);
                timer = null;
            }
        }

        function start() {
            stop();
            if (document.hidden) return;
            timer = setInterval(() => {
                if (!document.hidden) onTick();
            }, intervalMs);
        }

        function bindVisibility() {
            document.addEventListener('visibilitychange', () => {
                if (document.hidden) {
                    stop();
                } else {
                    start();
                }
            });
        }

        return { start, stop, bindVisibility };
    }

    return {
        CATEGORIES,
        getClient,
        waitForClient,
        formatCategoryValue,
        buildProfileUrl,
        renderAvatar,
        createAutoRefresher
    };
})();
