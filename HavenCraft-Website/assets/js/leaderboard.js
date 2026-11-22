/* ============================================
   HavenCraft — Leaderboard Module (Home Page)
   ============================================ */

(function () {
    'use strict';

    const core = window.HavenLeaderboardCore;
    if (!core) return;

    const TOP_N = 5;
    const CATEGORY_KEYS = ['playtime', 'kills', 'deaths', 'money', 'shards'];

    let supabase = null;
    let currentCategory = 'playtime';
    const categoryCache = {};

    const DOM = {
        tabs: document.getElementById('lbTabs'),
        list: document.getElementById('lbList'),
        loading: document.getElementById('lbLoading'),
        error: document.getElementById('lbError')
    };

    const escapeHtml = (text) => (typeof window.escapeHtml === 'function' ? window.escapeHtml(text) : String(text || ''));

    const refresher = core.createAutoRefresher(async () => {
        const data = await fetchCategory(currentCategory);
        if (data !== null) {
            renderPlayers(data, currentCategory, false);
        }
        CATEGORY_KEYS.forEach((cat) => {
            if (cat !== currentCategory) fetchCategory(cat);
        });
    });

    async function fetchCategory(category) {
        if (!supabase) return null;
        const config = core.CATEGORIES[category];
        if (!config) return null;

        try {
            const { data, error } = await supabase
                .from('player_stats')
                .select(`username, ${config.column}`)
                .order(config.column, { ascending: false })
                .order('username', { ascending: true })
                .limit(TOP_N);

            if (error) throw error;
            if (Array.isArray(data)) {
                categoryCache[category] = data;
            }
            return data;
        } catch (err) {
            console.error(`[Leaderboard] Error fetching ${category}:`, err);
            return null;
        }
    }

    function prefetchOtherCategories(excludeCategory) {
        CATEGORY_KEYS.forEach((cat) => {
            if (cat !== excludeCategory && !categoryCache[cat]) {
                fetchCategory(cat);
            }
        });
    }

    function renderPlayers(players, category, animate = true) {
        if (!DOM.list) return;
        DOM.list.classList.remove('lb-animate-in');

        if (!players || players.length === 0) {
            DOM.list.style.display = 'none';
            return;
        }

        const root = window.getRootPath ? window.getRootPath('assets/js/leaderboard.js') : './';
        const config = core.CATEGORIES[category];
        let html = '';

        players.forEach((player, i) => {
            const rank = i + 1;
            const username = player.username;
            const rawVal = player[config.column] || 0;
            const { value, tooltip } = core.formatCategoryValue(category, rawVal, false);

            let rowClass = 'lb-row';
            let rankClass = 'lb-rank';
            let rankContent = `#${rank}`;

            if (rank === 1) {
                rowClass += ' lb-row-top';
                rankClass += ' lb-rank-gold';
                rankContent = '<i class="fa-solid fa-crown"></i>';
            }

            html += `
                <div class="${rowClass}" style="animation-delay: ${i * 0.05}s;">
                    <div class="${rankClass}">${rankContent}</div>
                    ${core.renderAvatar(username, 40, 'lb-avatar')}
                    <a href="${core.buildProfileUrl(root, username)}" class="lb-username hover-link">${escapeHtml(username)}</a>
                    <div class="lb-value" title="${escapeHtml(tooltip)}">${escapeHtml(value)}</div>
                </div>
            `;
        });

        DOM.list.innerHTML = html;
        DOM.list.style.display = 'flex';
        if (DOM.error) DOM.error.style.display = 'none';
        if (DOM.loading) DOM.loading.style.display = 'none';

        if (animate) {
            void DOM.list.offsetWidth;
            DOM.list.classList.add('lb-animate-in');
        }
    }

    function showLoading() {
        if (DOM.loading) DOM.loading.style.display = 'flex';
        if (DOM.list) DOM.list.style.display = 'none';
        if (DOM.error) DOM.error.style.display = 'none';
    }

    function showError() {
        if (DOM.loading) DOM.loading.style.display = 'none';
        if (DOM.list) DOM.list.style.display = 'none';
        if (DOM.error) DOM.error.style.display = 'flex';
    }

    async function switchCategory(category) {
        if (currentCategory === category || !core.CATEGORIES[category]) return;
        currentCategory = category;

        if (DOM.tabs) {
            DOM.tabs.querySelectorAll('.lb-tab').forEach((tab) => {
                const isSelected = tab.dataset.category === category;
                tab.classList.toggle('active', isSelected);
                tab.setAttribute('aria-selected', isSelected ? 'true' : 'false');
            });
        }

        refresher.stop();

        if (categoryCache[currentCategory]) {
            renderPlayers(categoryCache[currentCategory], currentCategory, true);
            refresher.start();
            return;
        }

        if (!DOM.list || !DOM.list.children.length) {
            showLoading();
        }

        const data = await fetchCategory(currentCategory);
        if (currentCategory !== category) return;
        if (data !== null) {
            renderPlayers(data, currentCategory);
        } else if (!DOM.list || !DOM.list.children.length) {
            showError();
        }

        refresher.start();
    }

    async function retry() {
        supabase = supabase || await core.waitForClient();
        showLoading();
        const data = await fetchCategory(currentCategory);
        if (data !== null) {
            renderPlayers(data, currentCategory);
            refresher.start();
        } else {
            showError();
        }
    }

    async function boot() {
        showLoading();
        supabase = await core.waitForClient();
        if (!supabase) {
            showError();
            return;
        }

        const data = await fetchCategory(currentCategory);
        if (data !== null) {
            renderPlayers(data, currentCategory);
            prefetchOtherCategories(currentCategory);
        } else {
            showError();
        }

        refresher.start();
    }

    function init() {
        if (!DOM.tabs || !DOM.list) return;

        DOM.tabs.querySelectorAll('.lb-tab').forEach((tab) => {
            tab.addEventListener('click', () => {
                switchCategory(tab.dataset.category);
            });
        });

        refresher.bindVisibility();
        boot();
    }

    window.HavenLeaderboard = { retry };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
