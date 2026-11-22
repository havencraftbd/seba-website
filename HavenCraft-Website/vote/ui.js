/**
 * HavenCraft — Vote UI & Realtime Updates
 */

window.HavenVote = window.HavenVote || {
    currentUsername: null,
    currentSiteCooldowns: [],
    countdownInterval: null
};

function clearCountdown() {
    if (window.HavenVote.countdownInterval) {
        clearInterval(window.HavenVote.countdownInterval);
        window.HavenVote.countdownInterval = null;
    }
}

function timeAgo(isoString) {
    if (!isoString) return 'Never';
    const nowMs = Date.now() + (window._serverClockOffset || 0);
    const diff = nowMs - new Date(isoString).getTime();
    const m = Math.floor(diff / 60000);
    if (m < 1) return 'Just now';
    if (m < 60) return `${m}m ago`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}h ago`;
    return `${Math.floor(h / 24)}d ago`;
}

function getBaseVoteSites() {
    if (window.HavenCraftCore && typeof window.HavenCraftCore.getVoteSites === 'function') {
        const coreSites = window.HavenCraftCore.getVoteSites();
        if (coreSites.length > 0) return coreSites;
    }
    return Array.isArray(window.VOTE_SITES) ? window.VOTE_SITES : [];
}

function renderPlayerAvatar(username, size) {
    if (typeof window.renderAvatarImgHtml === 'function') {
        return window.renderAvatarImgHtml(username, { size, lazy: true });
    }
    const clean = String(username || 'MHF_Steve').replace(/^\.+/, '') || 'MHF_Steve';
    const safeAlt = sanitizeText(username);
    return `<img src="https://mc-heads.net/avatar/${encodeURIComponent(clean)}/${size}" alt="${safeAlt}" loading="lazy" />`;
}

function renderVoteSites(siteCooldowns) {
    const grid = document.getElementById('voteSitesGrid');
    if (!grid) return;

    const state = window.HavenVote;
    const baseSites = getBaseVoteSites();
    const sites = (siteCooldowns && siteCooldowns.length > 0)
        ? siteCooldowns
        : baseSites.map(site => ({ site, state: 'available', remainingMs: 0, lastVoteAt: null }));

    let html = '';

    sites.forEach((item) => {
        const site = item.site || item;
        const status = item.state || 'available';
        const remainingMs = item.remainingMs || 0;
        const lastVoteAt = item.lastVoteAt || null;

        const siteUrl = (typeof window.getVoteUrl === 'function')
            ? window.getVoteUrl(site, state.currentUsername)
            : (site.url || '#');

        const safeSiteNameAttr = encodeURIComponent(site.name || '');
        const siteIdAttr = encodeURIComponent(site.id || '');
        const cooldownHours = Number(site.cooldown_hours) || 24;

        let btnHTML = `<a href="${sanitizeText(siteUrl)}" class="btn-vote" target="_blank" rel="noopener noreferrer" data-site-id="${siteIdAttr}" data-site-name="${safeSiteNameAttr}" data-cooldown-hours="${cooldownHours}"><i class="fa-solid fa-external-link-alt"></i> Vote Now</a>`;
        let statusClass = 'available';
        let statusText = 'Ready to vote';

        if (state.currentUsername) {
            if (status === 'cooldown' && remainingMs > 0) {
                statusClass = 'cooldown';
                statusText = 'Cooldown active';
                btnHTML = `<a href="${sanitizeText(siteUrl)}" class="btn-vote cooldown-active" target="_blank" rel="noopener noreferrer" data-site-id="${siteIdAttr}" data-site-name="${safeSiteNameAttr}" data-cooldown-hours="${cooldownHours}" data-remaining="${remainingMs}"><i class="fa-solid fa-hourglass-half"></i> <span class="countdown-display">${formatCountdown(remainingMs)}</span></a>`;
            } else {
                statusText = lastVoteAt ? 'Ready to vote again' : 'Ready to vote';
            }
        }

        html += `
            <div class="vote-site-card" id="card-${sanitizeText(site.id)}">
                <div class="vote-site-icon">
                    <i class="fa-solid ${sanitizeText(site.icon)}"></i>
                </div>
                <div class="vote-site-name">${sanitizeText(site.name)}</div>
                <div class="vote-site-desc">${sanitizeText(site.desc)}</div>
                <div class="vote-site-status ${statusClass}">
                    <span class="status-dot-small"></span>
                    ${statusText}
                </div>
                ${btnHTML}
            </div>
        `;
    });

    grid.innerHTML = html;
    startCountdownTimers();
}

function startCountdownTimers() {
    clearCountdown();

    const buttons = document.querySelectorAll('.btn-vote.cooldown-active[data-remaining]');
    if (buttons.length === 0) return;

    window.HavenVote.countdownInterval = setInterval(() => {
        let hasRemaining = false;

        buttons.forEach((btn) => {
            let remaining = parseInt(btn.dataset.remaining, 10);
            if (remaining > 0) {
                remaining -= 1000;
                btn.dataset.remaining = remaining;

                const display = btn.querySelector('.countdown-display');
                if (display) {
                    display.textContent = formatCountdown(remaining);
                }

                if (remaining > 0) {
                    hasRemaining = true;
                } else {
                    btn.classList.remove('cooldown-active');
                    btn.innerHTML = '<i class="fa-solid fa-external-link-alt"></i> Vote Now';
                    const card = btn.closest('.vote-site-card');
                    const statusEl = card?.querySelector('.vote-site-status');
                    if (statusEl) {
                        statusEl.className = 'vote-site-status available';
                        statusEl.innerHTML = '<span class="status-dot-small"></span> Ready to vote again';
                    }
                }
            }
        });

        if (!hasRemaining) {
            clearCountdown();
            if (window.HavenVote.currentUsername) {
                loadUserVoteStatus(window.HavenVote.currentUsername);
            }
        }
    }, 1000);
}

async function loadUserVoteStatus(username) {
    username = (username || '').trim();
    if (!isValidUsername(username)) {
        showStatusError('Invalid username. Use 3-24 characters (letters, numbers, underscore, dots).');
        return;
    }

    window.HavenVote.currentUsername = username;
    showStatusLoading(username);

    try {
        const [votesResult, stats] = await Promise.all([
            fetchUserVotes(username),
            fetchPlayerStats(username),
            typeof window.syncServerClock === 'function' ? window.syncServerClock() : Promise.resolve()
        ]);

        if (votesResult.error) {
            showStatusError('Could not load vote data. Sites are still available below.');
            renderVoteSites([]);
            return;
        }

        const sites = getBaseVoteSites();
        const siteCooldowns = (typeof calculateSiteCooldowns === 'function')
            ? calculateSiteCooldowns(votesResult.data, sites)
            : [];

        window.HavenVote.currentSiteCooldowns = siteCooldowns;

        renderVoteSites(siteCooldowns);
        renderPlayerStats(username, stats);

        if (typeof scheduleVoteReminder === 'function') {
            scheduleVoteReminder(siteCooldowns);
        }
    } catch (err) {
        console.error('[Vote UI] Error loading user vote status:', err);
        showStatusError('An error occurred while loading vote status. Please try again.');
        renderVoteSites([]);
    }
}

function renderPlayerStats(username, stats) {
    const panel = document.getElementById('statusNotification');
    if (!panel) return;

    if (!stats) {
        panel.style.display = 'flex';
        panel.innerHTML = `
            <div class="player-stat-empty">
                <h3 class="player-stat-name">${sanitizeText(username)}</h3>
                <p>No vote history found yet. Start voting on the sites below.</p>
            </div>`;
        return;
    }

    panel.style.display = 'flex';
    panel.innerHTML = `
        <div class="player-stat-wrapper">
            <div class="player-stat-identity">
                <h3 class="player-stat-name">${sanitizeText(username)}</h3>
                <span class="player-stat-dot" aria-hidden="true">•</span>
                <span class="player-stat-last">Last voted: <strong>${timeAgo(stats.last_vote_at)}</strong></span>
            </div>

            <div class="player-stat-metrics">
                <div class="metric-item">
                    <div class="metric-val">${Number(stats.daily_votes || 0).toLocaleString()}</div>
                    <div class="metric-lbl">Daily</div>
                </div>
                <div class="metric-item">
                    <div class="metric-val">${Number(stats.weekly_votes || 0).toLocaleString()}</div>
                    <div class="metric-lbl">Weekly</div>
                </div>
                <div class="metric-item">
                    <div class="metric-val">${Number(stats.monthly_votes || 0).toLocaleString()}</div>
                    <div class="metric-lbl">Monthly</div>
                </div>
                <div class="metric-item highlight">
                    <div class="metric-val">${Number(stats.total_votes || 0).toLocaleString()}</div>
                    <div class="metric-lbl">All-time</div>
                </div>
            </div>
        </div>
    `;
}

function showStatusLoading(username) {
    const panel = document.getElementById('statusNotification');
    if (!panel) return;

    panel.style.display = 'flex';
    panel.innerHTML = `
        <div class="player-stat-state">
            <div class="status-icon"><i class="fa-solid fa-spinner fa-spin"></i></div>
            <div class="status-content">
                <h3>Looking up <em>${sanitizeText(username)}</em>…</h3>
            </div>
        </div>`;
}

function showStatusError(message) {
    const panel = document.getElementById('statusNotification');
    if (!panel) return;

    panel.style.display = 'flex';
    panel.innerHTML = `
        <div class="player-stat-state">
            <div class="status-icon error"><i class="fa-solid fa-circle-exclamation"></i></div>
            <div class="status-content">
                <h3 class="error-title">Unable to load vote status</h3>
                <p>${sanitizeText(message)}</p>
            </div>
        </div>`;
}

function renderLoadingSkeleton(rows = 5) {
    return Array.from({ length: rows }, (_, i) => `
        <div class="leaderboard-item" style="opacity:${1 - i * 0.15}">
            <div class="rank-badge default">${i + 1}</div>
            <div class="player-avatar">
                <div class="loading-skeleton" style="width:48px;height:48px;border-radius:4px;background:rgba(255,255,255,0.1)"></div>
            </div>
            <div class="player-info" style="display:flex; flex-direction:column; justify-content:center;">
                <div class="loading-skeleton" style="width:${100 + i * 10}px;height:14px;border-radius:6px;margin-bottom:6px;background:rgba(255,255,255,0.1)"></div>
                <div class="loading-skeleton" style="width:70px;height:10px;border-radius:6px;background:rgba(255,255,255,0.05)"></div>
            </div>
            <div class="vote-count" style="display:flex; justify-content:flex-end;">
                <div class="loading-skeleton" style="width:40px;height:24px;border-radius:6px;background:rgba(255,255,255,0.1)"></div>
            </div>
        </div>`).join('');
}

function renderEmptyState(icon, title, subtitle) {
    return `
        <div class="empty-state" style="grid-column:1/-1; text-align:center; padding: 40px 0;">
            <i class="fa-solid ${icon}" style="font-size:3rem; margin-bottom:16px; opacity:0.5;"></i>
            <h3>${sanitizeText(title)}</h3>
            <p style="opacity:0.7;">${sanitizeText(subtitle)}</p>
        </div>`;
}

function renderLeaderboardItem(player, rank) {
    const rankClass = rank <= 3 ? `rank-${rank}` : '';
    const badgeClass = rank <= 3 ? '' : 'default';
    const titles = { 1: 'Champion Voter', 2: 'Elite Voter', 3: 'Pro Voter' };
    const title = titles[rank] || 'Ranked Voter';
    const safeUser = sanitizeText(player.username);

    return `
        <div class="leaderboard-item ${rankClass}">
            <div class="rank-badge ${badgeClass}">${rank}</div>
            <div class="player-avatar">
                ${renderPlayerAvatar(player.username, 48)}
            </div>
            <div class="player-info">
                <a href="../profile/?player=${encodeURIComponent(player.username)}" class="player-name hover-link">${safeUser}</a>
                <div class="player-rank-title">${title}</div>
            </div>
            <div class="vote-count">
                <div class="number">${Number(player.vote_count).toLocaleString()}</div>
                <div class="label">Votes</div>
            </div>
        </div>`;
}

async function loadLeaderboard(period = 'monthly') {
    const container = document.getElementById('leaderboard-list');
    if (!container) return;

    container.innerHTML = renderLoadingSkeleton(5);

    try {
        const data = await fetchLeaderboard(period);

        if (!data || data.length === 0) {
            container.innerHTML = renderEmptyState(
                'fa-ghost',
                'No votes yet',
                `Be the first on the ${period} board!`
            );
            return;
        }

        container.innerHTML = data.map((player, i) => renderLeaderboardItem(player, i + 1)).join('');
    } catch (err) {
        console.error('[Vote Leaderboard] Error loading board:', err);
        container.innerHTML = renderEmptyState(
            'fa-triangle-exclamation',
            'Unable to load leaderboard',
            'Please check your connection and try again.'
        );
    }
}

async function loadWallOfFame(period = 'alltime') {
    const showcase = document.getElementById('wall-of-fame-list');
    if (!showcase) return;

    showcase.innerHTML = '<div class="empty-state" style="grid-column:1/-1"><i class="fa-solid fa-spinner fa-spin" style="font-size: 2rem;"></i><p>Loading…</p></div>';

    try {
        const data = await fetchWallOfFame(period);

        if (!data || data.length === 0) {
            showcase.innerHTML = `<div class="empty-state" style="grid-column:1/-1">
                <i class="fa-solid fa-ghost"></i>
                <h3>No Legends Yet</h3>
                <p>Vote to become the first champion!</p></div>`;
            return;
        }

        const badges = [
            { cls: 'champion', label: 'Champion', cardCls: 'legendary' },
            { cls: 'legend', label: 'Legend', cardCls: '' },
            { cls: 'supporter', label: 'Supporter', cardCls: '' }
        ];

        showcase.innerHTML = data.map((player, i) => {
            const safeUser = sanitizeText(player.username);
            const b = badges[i] || badges[2];
            return `
                <div class="fame-card ${b.cardCls}">
                    <div class="fame-avatar">
                        ${renderPlayerAvatar(player.username, 80)}
                    </div>
                    <a href="../profile/?player=${encodeURIComponent(player.username)}" class="fame-name hover-link">${safeUser}</a>
                    <div class="fame-badge ${b.cls}">${b.label}</div>
                    <div class="fame-votes"><strong>${Number(player.vote_count).toLocaleString()}</strong> Total Votes</div>
                </div>`;
        }).join('');
    } catch (err) {
        console.error('[Wall of Fame] Error loading legends:', err);
        showcase.innerHTML = `<div class="empty-state" style="grid-column:1/-1">
            <i class="fa-solid fa-triangle-exclamation" style="font-size: 2rem; margin-bottom: 12px; opacity: 0.6;"></i>
            <h3>Unable to load legends</h3>
            <p style="opacity: 0.7;">Please check your connection and try again.</p></div>`;
    }
}

function setupRealtime() {
    if (!window.supabaseVote) return;
    try {
        window.supabaseVote
            .channel('votes-realtime')
            .on(
                'postgres_changes',
                { event: 'INSERT', schema: 'public', table: 'votes' },
                (payload) => {
                    if (typeof clearVoteCache === 'function') {
                        clearVoteCache();
                    }
                    const incomingUser = (payload.new?.username || '').trim().toLowerCase();
                    const activeName = window.HavenVote.currentUsername || '';
                    const activeUser = activeName.trim().toLowerCase();

                    if (activeUser && incomingUser === activeUser) {
                        loadUserVoteStatus(activeName);
                    }
                    const activeLbTab = document.querySelector('.leaderboard-tab[data-lb-tab].active');
                    if (activeLbTab) {
                        loadLeaderboard(activeLbTab.dataset.lbTab || 'monthly');
                    }
                }
            )
            .subscribe();
    } catch (e) {
        console.warn('[Supabase Realtime] Setup error:', e);
    }
}

document.addEventListener('click', (e) => {
    const btn = e.target.closest('.btn-vote');
    if (btn && btn.dataset.siteId) {
        const siteId = decodeURIComponent(btn.dataset.siteId);
        const siteName = decodeURIComponent(btn.dataset.siteName || '');
        const cooldown = Number(btn.dataset.cooldownHours) || 24;
        if (typeof window.handleVoteClick === 'function') {
            window.handleVoteClick(siteId, siteName, cooldown);
        }
    }
});
