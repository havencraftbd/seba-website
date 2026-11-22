/* ============================================
   HavenCraft — Full Leaderboard Logic
   ============================================ */

(function () {
    'use strict';

    const core = window.HavenLeaderboardCore;
    if (!core) return;

    const ITEMS_PER_PAGE = 20;
    const SUMMARY_CATEGORIES = ['playtime', 'money', 'shards', 'mobs_killed', 'kills', 'deaths', 'block_place', 'block_break'];

    let supabase = null;
    let currentCategory = 'playtime';
    let currentPage = 1;
    let totalItems = 0;
    let searchQuery = '';
    let searchTimeout = null;
    let activeFetchId = 0;

    const DOM = {
        tabs: document.getElementById('fullLbTabs'),
        list: document.getElementById('flbList'),
        loading: document.getElementById('flbLoading'),
        empty: document.getElementById('flbEmpty'),
        error: document.getElementById('flbError'),
        dynamicHeading: document.getElementById('lbDynamicHeading'),
        searchInput: document.getElementById('lbSearch'),
        clearSearchBtn: document.getElementById('clearSearchBtn'),
        btnPrev: document.getElementById('btnPrev'),
        btnNext: document.getElementById('btnNext'),
        lblCurrentPage: document.getElementById('currentPage'),
        lblTotalPages: document.getElementById('totalPages'),
        lblTotalPlayers: document.getElementById('totalPlayersBadge')
    };

    const escapeHtml = (text) => (typeof window.escapeHtml === 'function' ? window.escapeHtml(text) : String(text || ''));
    const escapeSql = (text) => (typeof window.escapeSqlWildcards === 'function'
        ? window.escapeSqlWildcards(text)
        : String(text || '').replace(/[%_\\]/g, '\\$&'));

    const refresher = core.createAutoRefresher(() => {
        if (currentPage === 1 && !searchQuery) {
            fetchData();
            fetchTopSummaries();
        }
    });

    async function fetchData() {
        if (!supabase) return;

        const fetchId = ++activeFetchId;
        showLoading();

        const config = core.CATEGORIES[currentCategory];
        const startIdx = (currentPage - 1) * ITEMS_PER_PAGE;
        const endIdx = startIdx + ITEMS_PER_PAGE - 1;

        try {
            let query = supabase
                .from('player_stats')
                .select(`username, ${config.column}`, { count: 'exact' });

            if (searchQuery) {
                query = query.ilike('username', `%${escapeSql(searchQuery)}%`);
            }

            query = query.order(config.column, { ascending: false }).order('username', { ascending: true });

            const { data, count, error } = await query.range(startIdx, endIdx);
            if (fetchId !== activeFetchId) return;
            if (error) throw error;

            totalItems = count || 0;
            updatePagination();
            renderData(data);
        } catch (err) {
            if (fetchId !== activeFetchId) return;
            console.error('[Leaderboard] Fetch error:', err);
            showError();
        }
    }

    function showLoading() {
        let skeletons = '';
        for (let i = 0; i < 5; i++) {
            skeletons += `
                <div class="flb-skeleton-row" style="animation-delay: ${i * 0.1}s">
                    <div class="skel-block skel-rank"></div>
                    <div class="skel-player">
                        <div class="skel-block skel-avatar"></div>
                        <div class="skel-block skel-name"></div>
                    </div>
                    <div class="skel-block skel-val"></div>
                </div>
            `;
        }
        DOM.loading.innerHTML = skeletons;
        DOM.loading.style.display = 'block';
        DOM.list.style.display = 'none';
        DOM.empty.style.display = 'none';
        DOM.error.style.display = 'none';
    }

    function renderData(data) {
        DOM.loading.style.display = 'none';

        if (!data || data.length === 0) {
            DOM.list.style.display = 'none';
            DOM.empty.style.display = 'flex';
            return;
        }

        const config = core.CATEGORIES[currentCategory];
        const offset = (currentPage - 1) * ITEMS_PER_PAGE;
        let html = '';

        data.forEach((player, i) => {
            const rank = offset + i + 1;
            const username = player.username;
            const rawVal = player[config.column] || 0;
            const { value, tooltip } = core.formatCategoryValue(currentCategory, rawVal, true);

            let rankClass = '';
            let rankContent = `#${rank}`;

            if (currentPage === 1 && !searchQuery) {
                if (rank === 1) { rankClass = 'flb-row-top1'; rankContent = '<i class="fa-solid fa-crown"></i>'; }
                else if (rank === 2) { rankClass = 'flb-row-top2'; rankContent = '<i class="fa-solid fa-medal"></i>'; }
                else if (rank === 3) { rankClass = 'flb-row-top3'; rankContent = '<i class="fa-solid fa-award"></i>'; }
            }

            html += `
                <div class="flb-row ${rankClass}" style="animation-delay: ${i * 0.03}s;">
                    <div class="flb-rank-badge">${rankContent}</div>
                    <div class="flb-player-col">
                        ${core.renderAvatar(username, 40, 'flb-avatar', true)}
                        <a href="${core.buildProfileUrl('../', username)}" class="flb-username hover-link">${escapeHtml(username)}</a>
                    </div>
                    <div class="flb-value" title="${escapeHtml(tooltip)}">${escapeHtml(value)}</div>
                </div>
            `;
        });

        DOM.list.innerHTML = html;
        DOM.list.style.display = 'flex';
    }

    function showError() {
        DOM.loading.style.display = 'none';
        DOM.list.style.display = 'none';
        DOM.empty.style.display = 'none';
        DOM.error.style.display = 'flex';
    }

    async function retry() {
        supabase = supabase || await core.waitForClient();
        fetchData();
        fetchTopSummaries();
    }

    function updatePagination() {
        const totalPages = Math.max(1, Math.ceil(totalItems / ITEMS_PER_PAGE));

        if (currentPage > totalPages) {
            currentPage = totalPages;
            fetchData();
            return;
        }

        DOM.lblCurrentPage.textContent = currentPage;
        DOM.lblTotalPages.textContent = totalPages;
        DOM.lblTotalPlayers.textContent = totalItems.toLocaleString();

        DOM.btnPrev.disabled = currentPage <= 1;
        DOM.btnNext.disabled = currentPage >= totalPages;
    }

    function setCategory(cat) {
        if (currentCategory === cat || !core.CATEGORIES[cat]) return;
        currentCategory = cat;
        currentPage = 1;

        DOM.tabs.querySelectorAll('.lb-tab').forEach((tab) => {
            const isActive = tab.dataset.category === cat;
            tab.classList.toggle('active', isActive);
            tab.setAttribute('aria-selected', isActive ? 'true' : 'false');
            if (isActive) {
                tab.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
            }
        });

        DOM.dynamicHeading.textContent = core.CATEGORIES[cat].label;

        fetchData();
        refresher.start();
    }

    function handleSearch(e) {
        const val = e.target.value.trim();
        DOM.clearSearchBtn.style.display = val ? 'block' : 'none';

        if (searchTimeout) clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            searchQuery = val;
            currentPage = 1;
            fetchData();
            refresher.start();
        }, 500);
    }

    function clearSearch() {
        DOM.searchInput.value = '';
        DOM.clearSearchBtn.style.display = 'none';
        searchQuery = '';
        currentPage = 1;
        fetchData();
        refresher.start();
    }

    function changePage(delta) {
        currentPage += delta;
        fetchData();
        refresher.start();
    }

    async function fetchTopSummaries() {
        if (!supabase) return;

        await Promise.all(SUMMARY_CATEGORIES.map(async (cat, idx) => {
            const config = core.CATEGORIES[cat];
            if (!config) return;

            try {
                const { data, error } = await supabase
                    .from('player_stats')
                    .select(`username, ${config.column}`)
                    .order(config.column, { ascending: false })
                    .limit(1);

                const el = document.getElementById(config.summaryId);
                if (!el) return;

                const card = el.closest('.top-summary-card');
                if (card && !card.classList.contains('loaded')) {
                    card.style.animationDelay = `${idx * 0.08}s`;
                    card.classList.add('loaded');
                }

                if (error || !data || data.length === 0) {
                    el.innerHTML = '<span class="top-summary-dash">—</span>';
                    return;
                }

                const player = data[0];
                const rawVal = player[config.column] || 0;
                const { value, tooltip } = core.formatCategoryValue(cat, rawVal, true);

                el.innerHTML = `
                    <div class="top-summary-content">
                        ${core.renderAvatar(player.username, 48, 'top-summary-avatar')}
                        <a href="${core.buildProfileUrl('../', player.username)}" class="top-summary-name hover-link">${escapeHtml(player.username)}</a>
                        <div class="top-summary-value" title="${escapeHtml(tooltip)}">${escapeHtml(value)}</div>
                    </div>
                `;
            } catch (err) {
                console.error(`[Leaderboard Summary] Error fetching ${cat}:`, err);
            }
        }));
    }

    async function init() {
        supabase = await core.waitForClient();
        if (!supabase) {
            showError();
            return;
        }

        DOM.tabs.querySelectorAll('.lb-tab').forEach((tab) => {
            tab.addEventListener('click', () => setCategory(tab.dataset.category));
        });

        document.querySelectorAll('.top-summary-card').forEach((card) => {
            card.style.cursor = 'pointer';
            card.addEventListener('click', (e) => {
                if (e.target.closest('.top-summary-name')) return;
                const cat = card.dataset.cat;
                if (cat) {
                    setCategory(cat);
                    const wrapper = document.querySelector('.full-lb-wrapper');
                    if (wrapper) {
                        wrapper.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    }
                }
            });
        });

        DOM.searchInput.addEventListener('input', handleSearch);
        DOM.clearSearchBtn.addEventListener('click', clearSearch);

        DOM.btnPrev.addEventListener('click', () => changePage(-1));
        DOM.btnNext.addEventListener('click', () => changePage(1));

        fetchData();
        fetchTopSummaries();
        refresher.bindVisibility();
        refresher.start();
    }

    window.HavenFullLeaderboard = { retry };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
